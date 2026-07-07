package com.adeloc.iptv.ui.vod

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.adeloc.iptv.data.local.AppDatabase
import com.adeloc.iptv.data.local.dao.PlaylistDao
import com.adeloc.iptv.data.local.dao.StreamDao
import com.adeloc.iptv.data.local.entity.*
import com.adeloc.iptv.data.repository.XtreamSyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class VodViewModel(
    private val playlistDao: PlaylistDao,
    private val streamDao: StreamDao,
    private val initialPlaylistId: Int,
    private val type: String, // "vod" or "series"
    private val database: AppDatabase
) : ViewModel() {

    private val syncRepository = XtreamSyncRepository(playlistDao, database.epgDao(), streamDao)
    private val favoriteDao = database.favoriteDao()
    private val recentDao = database.recentDao()

    var isSyncing by mutableStateOf(false)
        private set

    val activePlaylistId: StateFlow<Int?> = playlistDao.observeActivePlaylist()
        .filterNotNull()
        .map { it.id }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val categories: StateFlow<List<StreamCategory>> = activePlaylistId
        .filterNotNull()
        .flatMapLatest { id ->
            playlistDao.getCategoriesByPlaylist(id, type).map { list ->
                val favCat = StreamCategory(categoryId = "-1", playlistId = id, categoryName = "Favourites", type = type)
                val recentCat = StreamCategory(categoryId = "-2", playlistId = id, categoryName = "Recently Watched", type = type)
                listOf(favCat, recentCat) + list
            }
        }
        .onEach { list ->
            if (selectedCategory == null && list.isNotEmpty()) {
                selectedCategory = list.first()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var selectedCategory by mutableStateOf<StreamCategory?>(null)
        private set

    @OptIn(ExperimentalCoroutinesApi::class)
    val streams: StateFlow<List<StreamEntity>> = combine(
        activePlaylistId.filterNotNull(),
        snapshotFlow { selectedCategory }.filterNotNull()
    ) { pId, category ->
        pId to category
    }.flatMapLatest { (pId, category) ->
        val targetType = getTargetType()
        val favoritesFlow = favoriteDao.getFavoriteStreamIds(pId, targetType)

        if (category.categoryId == "-2") {
            // "Recently Watched" directly from the streams table
            val recentStreamsFlow = streamDao.getRecentlyWatched(pId, targetType)
            combine(recentStreamsFlow, favoritesFlow) { streams, favIds ->
                val favSet = favIds.toSet()
                streams.map { stream ->
                    stream.copy(isFavorite = favSet.contains(stream.streamId))
                }
            }
        } else {
            // Original logic for all other categories
            val baseStreamsFlow = when (category.categoryId) {
                "-1" -> favoriteDao.getFavoriteStreams(pId, targetType)
                else -> streamDao.getStreamsByCategory(pId, category.categoryId, targetType)
            }

            combine(baseStreamsFlow, favoritesFlow) { streams, favIds ->
                val favSet = favIds.toSet()
                streams.map { stream ->
                    stream.copy(
                        isFavorite = favSet.contains(stream.streamId)
                    )
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    private fun getTargetType(): StreamType = if (type == "vod") StreamType.VOD else StreamType.SERIES

    fun selectCategory(category: StreamCategory) {
        selectedCategory = category
    }

    fun toggleFavorite(streamId: String) {
        viewModelScope.launch {
            val pId = activePlaylistId.value ?: return@launch
            val isFav = favoriteDao.isFavorite(pId, streamId).first()
            if (isFav) {
                favoriteDao.removeFavorite(pId, streamId)
            } else {
                favoriteDao.insertFavorite(
                    FavoriteEntity(
                        playlistId = pId,
                        streamId = streamId,
                        streamType = getTargetType()
                    )
                )
            }
        }
    }

    fun updateProgress(streamId: String, position: Long, duration: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val pId = activePlaylistId.value ?: return@launch
            recentDao.insertRecent(
                RecentEntity(
                    playlistId = pId,
                    streamId = streamId,
                    streamType = getTargetType(),
                    lastWatched = System.currentTimeMillis(),
                    resumePosition = position,
                    duration = duration
                )
            )
        }
    }

    fun removeFromRecents(streamId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val pId = activePlaylistId.value ?: return@launch
            // Also reset it in the streams table so it doesn't show up again!
            streamDao.updateLastWatched(streamId, 0L)
            recentDao.removeRecent(pId, streamId)
        }
    }

    fun forceSync() {
        viewModelScope.launch {
            val targetId = activePlaylistId.value ?: return@launch
            isSyncing = true
            try {
                syncRepository.syncXtreamPlaylist(targetId)
            } catch (e: Exception) {
                Log.e("IPTV_DEBUG", "Force Sync failed for $type", e)
            } finally {
                isSyncing = false
            }
        }
    }

    class Factory(
        private val database: AppDatabase,
        private val playlistId: Int,
        private val type: String
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(VodViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return VodViewModel(
                    database.playlistDao(),
                    database.streamDao(),
                    playlistId,
                    type,
                    database
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
