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
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.lang.reflect.Type

class XtreamSyncRepository(
    private val playlistDao: PlaylistDao,
    private val epgDao: EpgDao,
    private val streamDao: StreamDao
) {
    private val _syncMessage = MutableStateFlow("")
    val syncMessage: StateFlow<String> = _syncMessage.asStateFlow()

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
        
        return Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(XtreamApiService::class.java)
    }

    suspend fun syncXtreamPlaylist(playlistId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d("IPTV_DEBUG", "Starting sync for playlist $playlistId")
            _syncMessage.value = "Starting sync..."
            
            val playlist = playlistDao.getPlaylistById(playlistId)
                ?: return@withContext Result.failure<Unit>(Exception("Playlist $playlistId not found"))
                    .also { Log.e("IPTV_DEBUG", "Sync failed: Playlist $playlistId not found in DB") }

            // 0. Wipe Old Data First
            _syncMessage.value = "Clearing old data..."
            playlistDao.deleteCategoriesByPlaylist(playlistId)
            playlistDao.deleteChannelsByPlaylist(playlistId)
            streamDao.deleteStreamsByPlaylist(playlistId)

            val apiService = createApiService(playlist.serverUrl)
            val username = playlist.username ?: ""
            val password = playlist.password ?: ""

            // 1. Sync Categories
            _syncMessage.value = "Downloading Categories..."
            Log.d("IPTV_DEBUG", "Syncing Categories...")
            val liveCats = fetchAndParseList(apiService.getLiveCategories(username, password), XtreamCategory::class.java)
                .map { it.toEntity(playlistId, "live") }
            val vodCats = fetchAndParseList(apiService.getVodCategories(username, password), XtreamCategory::class.java)
                .map { it.toEntity(playlistId, "vod") }
            val seriesCats = fetchAndParseList(apiService.getSeriesCategories(username, password), XtreamCategory::class.java)
                .map { it.toEntity(playlistId, "series") }

            val allCats = (liveCats + vodCats + seriesCats).toMutableList()
            if (liveCats.isNotEmpty() && liveCats.none { it.categoryName.lowercase().contains("all") }) {
                allCats.add(StreamCategory(categoryId = "all_live", playlistId = playlistId, categoryName = "All Channels", type = "live"))
            }
            if (vodCats.isNotEmpty() && vodCats.none { it.categoryName.lowercase().contains("all") }) {
                allCats.add(StreamCategory(categoryId = "all_vod", playlistId = playlistId, categoryName = "All Movies", type = "vod"))
            }
            if (seriesCats.isNotEmpty() && seriesCats.none { it.categoryName.lowercase().contains("all") }) {
                allCats.add(StreamCategory(categoryId = "all_series", playlistId = playlistId, categoryName = "All Series", type = "series"))
            }

            playlistDao.insertCategories(allCats)
            Log.d("IPTV_DEBUG", "Stored ${allCats.size} categories")

            // Create category map for lookup
            val dbCats = playlistDao.getCategoriesByPlaylistSync(playlistId, "live") + 
                         playlistDao.getCategoriesByPlaylistSync(playlistId, "vod") + 
                         playlistDao.getCategoriesByPlaylistSync(playlistId, "series")
            val catMap = dbCats.associateBy { "${it.type}_${it.categoryId}" }

            // 2. Sync Live Streams
            _syncMessage.value = "Downloading Live Channels..."
            Log.d("IPTV_DEBUG", "Syncing Live Streams...")
            val liveList = fetchAndParseList(apiService.getLiveStreams(username, password), XtreamStream::class.java)
            val defaultLiveCatDbId = catMap["live_all_live"]?.id ?: 0
            val channels = liveList.map { s ->
                val categoryId = s.categoryId ?: "all_live"
                val catDbId = catMap["live_$categoryId"]?.id ?: defaultLiveCatDbId
                val sId = s.streamId?.toString() ?: ""
                LiveChannel(
                    streamId = sId,
                    playlistId = playlistId,
                    categoryDbId = catDbId,
                    categoryId = categoryId,
                    name = s.name ?: "Unknown",
                    streamUrl = "${playlist.serverUrl}/live/$username/$password/${sId}.${s.containerExtension ?: "ts"}",
                    logoUrl = s.streamIcon ?: s.cover ?: ""
                )
            }
            playlistDao.insertChannels(channels)
            Log.d("IPTV_DEBUG", "Stored ${channels.size} channels")

            // 3. Sync VOD Streams
            _syncMessage.value = "Downloading Movies..."
            Log.d("IPTV_DEBUG", "Syncing VOD Streams...")
            val vodList = fetchAndParseList(apiService.getVodStreams(username, password), XtreamStream::class.java)
            val vodEntities = vodList.map { s ->
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
            }
            streamDao.insertStreams(vodEntities)
            Log.d("IPTV_DEBUG", "Stored ${vodEntities.size} movies")

            // 4. Sync Series
            _syncMessage.value = "Downloading Series..."
            Log.d("IPTV_DEBUG", "Syncing Series...")
            val seriesList = fetchAndParseList(apiService.getSeries(username, password), XtreamStream::class.java)
            val seriesEntities = seriesList.map { s ->
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
            }
            streamDao.insertStreams(seriesEntities)
            Log.d("IPTV_DEBUG", "Stored ${seriesEntities.size} series")

            _syncMessage.value = "Sync complete!"
            Log.d("IPTV_DEBUG", "Sync Completed Successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            _syncMessage.value = "Sync failed: ${e.message}"
            Log.e("IPTV_DEBUG", "Sync failed: ${e.message}", e)
            Result.failure<Unit>(e)
        }
    }

    private fun <T : Any> fetchAndParseList(json: JsonElement, clazz: Class<T>): List<T> {
        val list = mutableListOf<T>()
        val gson = Gson()
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

    private fun XtreamCategory.toEntity(playlistId: Int, type: String) = StreamCategory(
        categoryId = (this.categoryId ?: "0").toString(),
        playlistId = playlistId,
        categoryName = this.categoryName ?: "Unknown",
        type = type
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
            if (!entities.isNullOrEmpty()) epgDao.insertPrograms(entities)
        } catch (e: Exception) {}
    }
}
