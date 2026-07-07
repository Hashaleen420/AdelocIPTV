package com.adeloc.iptv.data.repository

import android.util.Log
import android.util.Base64
import com.adeloc.iptv.data.local.dao.EpgDao
import com.adeloc.iptv.data.local.dao.PlaylistDao
import com.adeloc.iptv.data.local.dao.StreamDao
import com.adeloc.iptv.data.local.entity.*
import com.adeloc.iptv.data.model.XtreamCategory
import com.adeloc.iptv.data.model.XtreamStream
import com.adeloc.iptv.data.remote.XtreamApiService
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class XtreamSyncRepository(
    private val playlistDao: PlaylistDao,
    private val epgDao: EpgDao,
    private val streamDao: StreamDao
) {
    private val _syncMessage = MutableStateFlow("")
    val syncMessage: StateFlow<String> = _syncMessage.asStateFlow()
    private val gson = Gson()

    private fun decodeBase64(encoded: String?): String {
        if (encoded.isNullOrEmpty()) return ""
        return try {
            String(Base64.decode(encoded, Base64.DEFAULT)).trim()
        } catch (e: Exception) {
            encoded
        }
    }

    private fun createApiService(baseUrl: String): XtreamApiService {
        var url = baseUrl.trim()
        if (!url.startsWith("http")) url = "http://$url"
        url = url.replace("/player_api.php", "")
        if (!url.endsWith("/")) url = "$url/"

        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
        
        return Retrofit.Builder()
            .baseUrl(url)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(XtreamApiService::class.java)
    }

    private fun formatExpirationDate(expElement: JsonElement?): String {
        if (expElement == null || expElement.isJsonNull) return "Unlimited"
        
        val rawExp = try {
            if (expElement.isJsonPrimitive) expElement.asString else expElement.toString().replace("\"", "")
        } catch (e: Exception) {
            ""
        }.trim()

        if (rawExp.isEmpty() || rawExp.equals("null", ignoreCase = true) || rawExp == "0") {
            return "Unlimited"
        }
        
        return try {
            val timestamp = rawExp.toLong()
            val ms = if (timestamp < 100000000000L) timestamp * 1000L else timestamp
            val date = java.util.Date(ms)
            java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(date)
        } catch (e: NumberFormatException) {
            rawExp
        }
    }

    suspend fun syncXtreamPlaylist(playlistId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d("IPTV_DEBUG", "Starting sync for playlist $playlistId")
            _syncMessage.value = "Starting sync..."
            
            val playlist = playlistDao.getPlaylistById(playlistId)
                ?: return@withContext Result.failure<Unit>(Exception("Playlist $playlistId not found"))
                    .also { Log.e("IPTV_DEBUG", "Sync failed: Playlist $playlistId not found in DB") }

            val apiService = createApiService(playlist.serverUrl)
            val username = playlist.username ?: ""
            val password = playlist.password ?: ""

            // 0. Fetch latest user_info and update exp_date
            try {
                _syncMessage.value = "Updating account info..."
                val loginResponse = apiService.login(username, password)
                val updatedExp = formatExpirationDate(loginResponse.userInfo?.exp_date)
                playlistDao.updatePlaylistExpiry(playlistId, updatedExp)
                Log.d("IPTV_DEBUG", "Updated expDate to: $updatedExp")
            } catch (e: Exception) {
                Log.e("IPTV_DEBUG", "Failed to update expiry date during sync", e)
            }

            // 1. Wipe Old Data First (BUT NOT FAVORITES OR RECENTS)
            _syncMessage.value = "Clearing old data..."
            
            // PRESERVE HIDDEN STATE: Capture hidden category IDs before wiping
            val existingCats = (playlistDao.getCategoriesByPlaylistSync(playlistId, "live") +
                               playlistDao.getCategoriesByPlaylistSync(playlistId, "vod") +
                               playlistDao.getCategoriesByPlaylistSync(playlistId, "series"))
            val hiddenIds = existingCats.filter { it.isHidden }.map { it.categoryId }.toSet()

            playlistDao.deleteCategoriesByPlaylist(playlistId)
            playlistDao.deleteChannelsByPlaylist(playlistId)
            streamDao.deleteStreamsByPlaylist(playlistId)

            // 2. Sync Categories
            _syncMessage.value = "Downloading Categories..."
            Log.d("IPTV_DEBUG", "Syncing Categories...")
            val liveCats = fetchAndParseList(apiService.getLiveCategories(username, password), XtreamCategory::class.java)
                .map { it.toEntity(playlistId, "live", hiddenIds.contains(it.categoryId ?: "0")) }
            val vodCats = fetchAndParseList(apiService.getVodCategories(username, password), XtreamCategory::class.java)
                .map { it.toEntity(playlistId, "vod", hiddenIds.contains(it.categoryId ?: "0")) }
            val seriesCats = fetchAndParseList(apiService.getSeriesCategories(username, password), XtreamCategory::class.java)
                .map { it.toEntity(playlistId, "series", hiddenIds.contains(it.categoryId ?: "0")) }

            val allCats = (liveCats + vodCats + seriesCats).toMutableList()
            if (liveCats.isNotEmpty() && liveCats.none { it.categoryName.lowercase().contains("all") }) {
                allCats.add(StreamCategory(categoryId = "all_live", playlistId = playlistId, categoryName = "All Channels", type = "live", isHidden = hiddenIds.contains("all_live")))
            }
            if (vodCats.isNotEmpty() && vodCats.none { it.categoryName.lowercase().contains("all") }) {
                allCats.add(StreamCategory(categoryId = "all_vod", playlistId = playlistId, categoryName = "All Movies", type = "vod", isHidden = hiddenIds.contains("all_vod")))
            }
            if (seriesCats.isNotEmpty() && seriesCats.none { it.categoryName.lowercase().contains("all") }) {
                allCats.add(StreamCategory(categoryId = "all_series", playlistId = playlistId, categoryName = "All Series", type = "series", isHidden = hiddenIds.contains("all_series")))
            }

            allCats.chunked(900).forEach { chunk ->
                playlistDao.insertCategories(chunk)
            }

            // Create category map for lookup
            val dbCats = playlistDao.getCategoriesByPlaylistSync(playlistId, "live") + 
                         playlistDao.getCategoriesByPlaylistSync(playlistId, "vod") + 
                         playlistDao.getCategoriesByPlaylistSync(playlistId, "series")
            val catMap = dbCats.associateBy { "${it.type}_${it.categoryId}" }

            // 3. Sync Live Streams (Streaming)
            _syncMessage.value = "Downloading Live Channels..."
            Log.d("IPTV_DEBUG", "Syncing Live Streams (Streaming)...")
            val liveResponse = apiService.getLiveStreams(username, password)
            val defaultLiveCatDbId = catMap["live_all_live"]?.id ?: 0
            streamAndParse(liveResponse, mapper = { s ->
                val categoryId = s.categoryId ?: "all_live"
                val catDbId = catMap["live_$categoryId"]?.id ?: defaultLiveCatDbId
                val sId = s.streamId?.toString() ?: ""
                LiveChannel(
                    streamId = sId,
                    epgChannelId = s.epgChannelId,
                    playlistId = playlistId,
                    categoryDbId = catDbId,
                    categoryId = categoryId,
                    name = s.name ?: "Unknown",
                    streamUrl = "${playlist.serverUrl}/live/$username/$password/${sId}.${s.containerExtension ?: "ts"}",
                    logoUrl = s.streamIcon ?: s.cover ?: ""
                )
            }, onChunkReady = { chunk ->
                playlistDao.insertChannels(chunk)
            })

            // 4. Sync VOD Streams (Streaming)
            _syncMessage.value = "Downloading Movies..."
            Log.d("IPTV_DEBUG", "Syncing VOD Streams (Streaming)...")
            val vodResponse = apiService.getVodStreams(username, password)
            streamAndParse(vodResponse, mapper = { s ->
                val sId = s.streamId?.toString() ?: ""
                StreamEntity(
                    playlistId = playlistId,
                    categoryId = s.categoryId ?: "all_vod",
                    streamId = sId,
                    name = s.name ?: "Unknown",
                    logoUrl = s.streamIcon ?: s.cover ?: "",
                    groupTitle = null,
                    url = "${playlist.serverUrl}/movie/$username/$password/${sId}.${s.containerExtension ?: "mp4"}",
                    isLive = false,
                    streamType = StreamType.VOD
                )
            }, onChunkReady = { chunk ->
                streamDao.insertStreams(chunk)
            })

            // 5. Sync Series (Streaming)
            _syncMessage.value = "Downloading Series..."
            Log.d("IPTV_DEBUG", "Syncing Series (Streaming)...")
            val seriesResponse = apiService.getSeries(username, password)
            streamAndParse(seriesResponse, mapper = { s ->
                val sId = (s.seriesId ?: s.streamId)?.toString() ?: ""
                StreamEntity(
                    playlistId = playlistId,
                    categoryId = s.categoryId ?: "all_series",
                    streamId = sId,
                    name = s.name ?: "Unknown",
                    logoUrl = s.cover ?: s.streamIcon ?: s.thumbnail ?: "",
                    groupTitle = null,
                    url = "${playlist.serverUrl}/series/$username/$password/${sId}.mp4",
                    isLive = false,
                    streamType = StreamType.SERIES
                )
            }, onChunkReady = { chunk ->
                streamDao.insertStreams(chunk)
            })

            _syncMessage.value = "Sync complete!"
            Log.d("IPTV_DEBUG", "Sync Completed Successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            _syncMessage.value = "Sync failed: ${e.message}"
            Log.e("IPTV_DEBUG", "Sync failed: ${e.message}", e)
            Result.failure<Unit>(e)
        }
    }

    private suspend fun <R> streamAndParse(
        responseBody: ResponseBody,
        mapper: (XtreamStream) -> R,
        onChunkReady: suspend (List<R>) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            responseBody.use { body ->
                val reader = JsonReader(body.charStream())
                if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                    reader.beginArray()
                    val chunk = mutableListOf<R>()
                    while (reader.hasNext()) {
                        try {
                            val item: XtreamStream = gson.fromJson(reader, XtreamStream::class.java)
                            chunk.add(mapper(item))
                            if (chunk.size >= 1000) {
                                onChunkReady(chunk.toList())
                                chunk.clear()
                            }
                        } catch (e: Exception) {
                            Log.e("IPTV_DEBUG", "Error parsing item: ${e.message}")
                            reader.skipValue()
                        }
                    }
                    if (chunk.isNotEmpty()) {
                        onChunkReady(chunk)
                    }
                    reader.endArray()
                } else {
                    Log.e("IPTV_DEBUG", "Expected BEGIN_ARRAY but got ${reader.peek()}")
                }
            }
        } catch (e: Exception) {
            Log.e("IPTV_DEBUG", "Streaming parse error: ${e.message}", e)
            throw e
        }
    }

    private fun <T : Any> fetchAndParseList(json: JsonElement, clazz: Class<T>): List<T> {
        val list = mutableListOf<T>()
        try {
            if (json.isJsonArray) {
                val array = json.asJsonArray
                for (element in array) {
                    try {
                        val item = gson.fromJson(element, clazz)
                        if (item != null) list.add(item)
                    } catch (e: Exception) {}
                }
            } else if (json.isJsonObject) {
                val obj = json.asJsonObject
                for (entry in obj.entrySet()) {
                    try {
                        val item = gson.fromJson(entry.value, clazz)
                        if (item != null) list.add(item)
                    } catch (e: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.e("IPTV_DEBUG", "Parse error: ${e.message}")
        }
        return list
    }

    private fun XtreamCategory.toEntity(playlistId: Int, type: String, isHidden: Boolean = false) = StreamCategory(
        categoryId = (this.categoryId ?: "0").toString(),
        playlistId = playlistId,
        categoryName = this.categoryName ?: "Unknown",
        type = type,
        isHidden = isHidden
    )

    suspend fun syncEpgForChannel(playlistId: Int, streamId: String) = withContext(Dispatchers.IO) {
        try {
            val playlist = playlistDao.getPlaylistById(playlistId) ?: return@withContext
            val apiService = createApiService(playlist.serverUrl)
            val epgResponse = apiService.getShortEpg(playlist.username ?: "", playlist.password ?: "", streamId = streamId)
            val entities = epgResponse.epgListings?.map { listing ->
                EpgProgramEntity(
                    playlistId = playlistId,
                    channelId = streamId,
                    title = decodeBase64(listing.title ?: "No Title"),
                    description = decodeBase64(listing.description),
                    startTime = listing.startTimestamp?.toLongOrNull()?.times(1000) ?: 0L,
                    endTime = listing.stopTimestamp?.toLongOrNull()?.times(1000) ?: 0L
                )
            }
            if (!entities.isNullOrEmpty()) {
                entities.chunked(900).forEach { chunk ->
                    epgDao.insertPrograms(chunk)
                }
            }
        } catch (e: Exception) {}
    }
}
