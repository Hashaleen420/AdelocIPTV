package com.adeloc.iptv.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.adeloc.iptv.data.local.dao.PlaylistDao
import com.adeloc.iptv.data.local.dao.StreamDao
import com.adeloc.iptv.data.local.dao.RecentDao
import com.adeloc.iptv.data.local.entity.RecentEntity
import com.adeloc.iptv.data.local.entity.StreamEntity
import com.adeloc.iptv.data.local.entity.StreamType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerViewModel(
    private val streamDao: StreamDao,
    private val playlistDao: PlaylistDao,
    private val recentDao: RecentDao
) : ViewModel() {

    fun saveProgress(url: String, position: Long, duration: Long, seriesApiId: String?, playlistId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            if (playlistId == -1) return@launch

            // 1. Update the streams table (which works perfectly for Movies/VOD where URL is the primary identifier)
            streamDao.saveProgress(url, position, duration, System.currentTimeMillis())
            
            // 2. See if this URL matches a recognized database stream (e.g. a Movie)
            val stream = streamDao.getStreamByUrl(url)
            
            if (stream != null) {
                // It's a Movie (or a recognized DB stream)
                val xtreamId = stream.streamId
                if (!xtreamId.isNullOrEmpty()) {
                    val existing = recentDao.getRecentByStreamId(playlistId, xtreamId)
                    if (existing != null) {
                        recentDao.updateProgress(playlistId, xtreamId, position, duration, System.currentTimeMillis())
                    } else {
                        recentDao.insertRecent(
                            RecentEntity(
                                playlistId = playlistId,
                                streamId = xtreamId,
                                streamType = stream.streamType, // E.g., StreamType.VOD
                                lastWatched = System.currentTimeMillis(),
                                resumePosition = position,
                                duration = duration
                            )
                        )
                    }
                }
            } else if (!seriesApiId.isNullOrEmpty()) {
                // It's a Series Episode (since individual episodes aren't saved in the streams table by URL)
                
                // A) Save Episode specific progress (using its URL as the streamId)
                val existingEp = recentDao.getRecentByStreamId(playlistId, url)
                if (existingEp != null) {
                    recentDao.updateProgress(playlistId, url, position, duration, System.currentTimeMillis())
                } else {
                    recentDao.insertRecent(
                        RecentEntity(
                            playlistId = playlistId,
                            streamId = url,
                            streamType = StreamType.SERIES,
                            lastWatched = System.currentTimeMillis(),
                            resumePosition = position,
                            duration = duration
                        )
                    )
                }
                
                // B) Add the Parent Series to "Recently Watched" using the seriesApiId
                val existingSeries = recentDao.getRecentByStreamId(playlistId, seriesApiId)
                if (existingSeries != null) {
                    // Update lastWatched only, leaving its resumePosition alone since it's just a folder
                    recentDao.updateProgress(playlistId, seriesApiId, existingSeries.resumePosition, existingSeries.duration, System.currentTimeMillis())
                } else {
                    recentDao.insertRecent(
                        RecentEntity(
                            playlistId = playlistId,
                            streamId = seriesApiId,
                            streamType = StreamType.SERIES,
                            lastWatched = System.currentTimeMillis(),
                            resumePosition = 0L,
                            duration = 0L
                        )
                    )
                }
            }
        }
    }

    suspend fun getStreamByUrl(url: String): StreamEntity? {
        return streamDao.getStreamByUrl(url)
    }

    fun updateLastWatched(streamId: String, type: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            if (streamId.isEmpty()) return@launch
            
            when (type?.uppercase()) {
                "VOD", "SERIES" -> streamDao.updateLastWatched(streamId, System.currentTimeMillis())
                else -> {
                    // Fallback for LIVE or unknown
                }
            }
        }
    }

    suspend fun getSeriesIdByApiId(apiId: String, playlistId: Int): Int? {
        return withContext(Dispatchers.IO) {
            streamDao.getSeriesIdByApiId(apiId, playlistId)
        }
    }

    fun resetProgress(playlistId: Int, url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (playlistId == -1) return@launch
            streamDao.saveProgress(url, 0, 0, System.currentTimeMillis())
            recentDao.updateProgress(playlistId, url, 0, 0, System.currentTimeMillis())
        }
    }

    class Factory(
        private val streamDao: StreamDao,
        private val playlistDao: PlaylistDao,
        private val recentDao: RecentDao
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PlayerViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return PlayerViewModel(streamDao, playlistDao, recentDao) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
