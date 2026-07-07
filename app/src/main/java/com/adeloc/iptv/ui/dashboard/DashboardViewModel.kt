package com.adeloc.iptv.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.adeloc.iptv.data.local.dao.PlaylistDao
import com.adeloc.iptv.data.local.entity.PlaylistProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

class DashboardViewModel(private val playlistDao: PlaylistDao) : ViewModel() {

    val playlists: StateFlow<List<PlaylistProfile>> = playlistDao.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activePlaylist = playlistDao.observeActivePlaylist().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _playlistId = MutableStateFlow<Int?>(null)
    
    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedPlaylist: StateFlow<PlaylistProfile?> = _playlistId
        .flatMapLatest { id ->
            if (id != null) playlistDao.observePlaylistById(id)
            else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun loadPlaylist(playlistId: Int) {
        _playlistId.value = playlistId
    }

    fun onPlaylistSelected(playlist: PlaylistProfile) {
        _playlistId.value = playlist.id
    }

    class Factory(private val playlistDao: PlaylistDao) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return DashboardViewModel(playlistDao) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
