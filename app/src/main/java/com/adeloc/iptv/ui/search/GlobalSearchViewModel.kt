package com.adeloc.iptv.ui.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.adeloc.iptv.data.local.AppDatabase
import com.adeloc.iptv.data.local.datastore.UserPreferences
import com.adeloc.iptv.data.local.entity.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class GlobalSearchViewModel(
    private val database: AppDatabase,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val favoriteDao = database.favoriteDao()
    private val recentDao = database.recentDao()

    private val activePlaylist = database.playlistDao().observeActivePlaylist()
        .map { it?.id }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val parentalControlEnabled: StateFlow<Boolean> = userPreferences.parentalControlEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val parentalControlPin: StateFlow<String> = userPreferences.parentalControlPinFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "0000")

    @OptIn(ExperimentalCoroutinesApi::class)
    val liveChannels: StateFlow<List<LiveChannel>> = combine(activePlaylist, _searchQuery) { playlistId, query ->
        playlistId to query
    }.flatMapLatest { (playlistId, query) ->
        if (playlistId == null || query.trim().length < 2) {
            flowOf(emptyList())
        } else {
            combine(
                database.playlistDao().searchChannelsFlow(playlistId, query.trim()),
                favoriteDao.getFavoriteStreamIds(playlistId, StreamType.LIVE)
            ) { channels, favIds ->
                val favSet = favIds.toSet()
                channels.map { it.copy(isFavorite = favSet.contains(it.streamId)) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val movies: StateFlow<List<StreamEntity>> = combine(activePlaylist, _searchQuery) { playlistId, query ->
        playlistId to query
    }.flatMapLatest { (playlistId, query) ->
        if (playlistId == null || query.trim().length < 2) {
            flowOf(emptyList())
        } else {
            combine(
                database.streamDao().searchStreams(playlistId, StreamType.VOD, query.trim()),
                favoriteDao.getFavoriteStreamIds(playlistId, StreamType.VOD)
            ) { streams, favIds ->
                val favSet = favIds.toSet()
                streams.map { it.copy(isFavorite = favSet.contains(it.streamId)) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val series: StateFlow<List<StreamEntity>> = combine(activePlaylist, _searchQuery) { playlistId, query ->
        playlistId to query
    }.flatMapLatest { (playlistId, query) ->
        if (playlistId == null || query.trim().length < 2) {
            flowOf(emptyList())
        } else {
            combine(
                database.streamDao().searchStreams(playlistId, StreamType.SERIES, query.trim()),
                favoriteDao.getFavoriteStreamIds(playlistId, StreamType.SERIES)
            ) { streams, favIds ->
                val favSet = favIds.toSet()
                streams.map { it.copy(isFavorite = favSet.contains(it.streamId)) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun toggleFavorite(streamId: String, streamType: StreamType) {
        viewModelScope.launch {
            val pId = activePlaylist.value ?: return@launch
            val isFav = favoriteDao.isFavorite(pId, streamId).first()
            if (isFav) {
                favoriteDao.removeFavorite(pId, streamId)
            } else {
                favoriteDao.insertFavorite(
                    FavoriteEntity(
                        playlistId = pId,
                        streamId = streamId,
                        streamType = streamType
                    )
                )
            }
        }
    }

    class Factory(private val database: AppDatabase, private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(GlobalSearchViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return GlobalSearchViewModel(database, UserPreferences.getInstance(context)) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
