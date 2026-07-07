package com.adeloc.iptv.ui.series

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.adeloc.iptv.data.local.AppDatabase
import com.adeloc.iptv.data.local.entity.PlaylistProfile
import com.adeloc.iptv.data.model.XtreamSeriesDetail
import com.adeloc.iptv.data.repository.XtreamRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SeriesDetailViewModel(
    private val database: AppDatabase,
    private val seriesId: String
) : ViewModel() {

    private val repository = XtreamRepository()
    
    var seriesDetail by mutableStateOf<XtreamSeriesDetail?>(null)
        private set
        
    var playlist by mutableStateOf<PlaylistProfile?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set
        
    var error by mutableStateOf<String?>(null)
        private set

    var seriesDbId by mutableStateOf<Int?>(null)
        private set

    // Maps episode streamUrl to resume position
    var episodeProgressMap by mutableStateOf<Map<String, Long>>(emptyMap())
        private set

    init {
        loadDetail()
    }

    private fun loadDetail() {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                isLoading = true
                error = null
            }
            try {
                // Fetch the latest playlist directly from the DAO
                val activePlaylist = database.playlistDao().getLatestPlaylist().firstOrNull()
                
                if (activePlaylist != null && activePlaylist.id != 0) {
                    withContext(Dispatchers.Main) {
                        playlist = activePlaylist
                    }
                    
                    // Look up DB ID for tracking
                    val dbId = database.streamDao().getSeriesIdByApiId(seriesId, activePlaylist.id)
                    withContext(Dispatchers.Main) {
                        seriesDbId = dbId
                    }

                    val result = repository.getSeriesDetail(
                        activePlaylist.serverUrl,
                        activePlaylist.username ?: "",
                        activePlaylist.password ?: "",
                        seriesId
                    )
                    
                    withContext(Dispatchers.Main) {
                        if (result.isSuccess) {
                            seriesDetail = result.getOrNull()
                            if (seriesDetail == null) {
                                error = "No series information found"
                            } else {
                                loadEpisodeProgress()
                            }
                        } else {
                            val exception = result.exceptionOrNull()
                            error = exception?.message ?: "Failed to fetch series details"
                            Log.e("IPTV_DEBUG", "Series fetch failed for ID: $seriesId", exception)
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        error = "No active playlist found"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error = "An unexpected error occurred: ${e.message}"
                }
                Log.e("IPTV_DEBUG", "Series fetch failed", e)
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    private fun loadEpisodeProgress() {
        viewModelScope.launch(Dispatchers.IO) {
            val p = playlist ?: return@launch
            val baseUrl = p.serverUrl.removeSuffix("/")
            val username = p.username ?: ""
            val password = p.password ?: ""
            val urls = mutableListOf<String>()
            
            seriesDetail?.episodes?.values?.flatten()?.forEach { episode ->
                val ext = episode.containerExtension ?: "mp4"
                val url = "${baseUrl}/series/${username}/${password}/${episode.id}.${ext}"
                urls.add(url)
            }

            val recentDao = database.recentDao()
            val progressMap = mutableMapOf<String, Long>()
            val pId = p.id
            
            urls.forEach { url ->
                val recent = recentDao.getRecentByStreamId(pId, url)
                if (recent != null && recent.resumePosition > 0 && recent.duration > 0 && recent.resumePosition < recent.duration * 0.95) {
                    progressMap[url] = recent.resumePosition
                }
            }
            
            withContext(Dispatchers.Main) {
                episodeProgressMap = progressMap
            }
        }
    }

    fun updateSeriesLastWatched(seriesId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            database.streamDao().updateLastWatched(seriesId, System.currentTimeMillis())
        }
    }

    class Factory(
        private val database: AppDatabase,
        private val seriesId: String
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SeriesDetailViewModel(database, seriesId) as T
        }
    }
}
