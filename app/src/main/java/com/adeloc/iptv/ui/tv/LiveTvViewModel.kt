package com.adeloc.iptv.ui.tv

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.adeloc.iptv.data.local.AppDatabase
import com.adeloc.iptv.data.local.dao.EpgDao
import com.adeloc.iptv.data.local.dao.PlaylistDao
import com.adeloc.iptv.data.local.entity.*
import com.adeloc.iptv.data.repository.XtreamSyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LiveTvViewModel(
    private val playlistDao: PlaylistDao,
    private val epgDao: EpgDao,
    private val initialPlaylistId: Int,
    private val database: AppDatabase
) : ViewModel() {

    private val syncRepository = XtreamSyncRepository(playlistDao, epgDao, database.streamDao())
    private val favoriteDao = database.favoriteDao()
    private val recentDao = database.recentDao()

    var isSyncing by mutableStateOf(false)
        private set

    // Observe active playlist via Flow for reactive UI updates
    val activePlaylistId: StateFlow<Int?> = playlistDao.observeActivePlaylist()
        .filterNotNull()
        .map { it.id }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val categories: StateFlow<List<StreamCategory>> = activePlaylistId
        .filterNotNull()
        .flatMapLatest { id ->
            playlistDao.getCategoriesByPlaylist(id, "live").map { list ->
                val favCat = StreamCategory(categoryId = "-1", playlistId = id, categoryName = "Favourites", type = "live")
                val recentCat = StreamCategory(categoryId = "-2", playlistId = id, categoryName = "Recently Watched", type = "live")
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

    var selectedChannel by mutableStateOf<LiveChannel?>(null)
        private set

    @OptIn(ExperimentalCoroutinesApi::class)
    val allChannels: StateFlow<List<LiveChannel>> = activePlaylistId
        .filterNotNull()
        .flatMapLatest { id ->
            playlistDao.getAllChannelsByPlaylist(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val channels: StateFlow<List<LiveChannel>> = combine(activePlaylistId, snapshotFlow { selectedCategory }) { id, category ->
        id to category
    }
        .filter { (id, category) -> id != null && category != null }
        .flatMapLatest { (id, category) ->
            val pId = id!!
            val cat = category!!
            
            val baseChannelsFlow = when (cat.categoryId) {
                "-1" -> {
                    // Fetch all channels for playlist and filter by favorites
                    playlistDao.getAllChannelsByPlaylist(pId)
                }
                "-2" -> {
                    // Fetch all channels for playlist and filter by recents
                    playlistDao.getAllChannelsByPlaylist(pId)
                }
                else -> playlistDao.getChannelsByCategory(pId, cat.categoryId)
            }

            combine(
                baseChannelsFlow,
                favoriteDao.getFavoriteStreamIds(pId, StreamType.LIVE),
                recentDao.getRecentlyWatched(pId, StreamType.LIVE)
            ) { baseList, favIds, recentList ->
                val favSet = favIds.toSet()
                val recentMap = recentList.associateBy { it.streamId }
                
                val filteredList = when (cat.categoryId) {
                    "-1" -> baseList.filter { favSet.contains(it.streamId) }
                    "-2" -> {
                        baseList.filter { recentMap.containsKey(it.streamId) }
                            .sortedByDescending { recentMap[it.streamId]?.lastWatched ?: 0L }
                    }
                    else -> baseList
                }

                filteredList.map { channel ->
                    channel.copy(
                        isFavorite = favSet.contains(channel.streamId),
                        lastWatched = recentMap[channel.streamId]?.lastWatched ?: 0L
                    )
                }
            }
        }
        .onEach { list ->
            if (selectedChannel == null && list.isNotEmpty()) {
                selectedChannel = list.first()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val epgPrograms: StateFlow<List<EpgProgramEntity>> = combine(activePlaylistId, snapshotFlow { selectedChannel }) { id, channel ->
        id to channel
    }
        .filter { (id, channel) -> id != null && channel != null }
        .flatMapLatest { (id, channel) ->
            epgDao.getEpgForChannel(id!!, channel!!.streamId, System.currentTimeMillis())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectCategory(category: StreamCategory) {
        selectedCategory = category
        selectedChannel = null
    }

    fun selectChannel(channel: LiveChannel) {
        selectedChannel = channel
        val id = activePlaylistId.value
        if (id != null) {
            viewModelScope.launch {
                syncRepository.syncEpgForChannel(id, channel.streamId)
            }
        }
    }

    fun updateLastWatched(channel: LiveChannel) {
        viewModelScope.launch(Dispatchers.IO) {
            val pId = activePlaylistId.value ?: return@launch
            recentDao.insertRecent(
                RecentEntity(
                    playlistId = pId,
                    streamId = channel.streamId,
                    streamType = StreamType.LIVE,
                    lastWatched = System.currentTimeMillis()
                )
            )
        }
    }

    fun removeFromRecents(streamId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val pId = activePlaylistId.value ?: return@launch
            recentDao.removeRecent(pId, streamId)
        }
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
                        streamType = StreamType.LIVE
                    )
                )
            }
        }
    }

    fun forceSync() {
        viewModelScope.launch {
            val targetId = activePlaylistId.value ?: return@launch
            isSyncing = true
            try {
                syncRepository.syncXtreamPlaylist(targetId)
            } catch (e: Exception) {
                Log.e("IPTV_DEBUG", "Force Sync failed", e)
            } finally {
                isSyncing = false
            }
        }
    }

    class Factory(private val database: AppDatabase, private val playlistId: Int) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LiveTvViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return LiveTvViewModel(
                    database.playlistDao(),
                    database.epgDao(),
                    playlistId,
                    database
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
