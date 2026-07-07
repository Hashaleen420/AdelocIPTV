package com.adeloc.iptv.ui.tvguide

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.adeloc.iptv.data.local.AppDatabase
import com.adeloc.iptv.data.local.dao.EpgDao
import com.adeloc.iptv.data.local.dao.PlaylistDao
import com.adeloc.iptv.data.local.entity.EpgProgramEntity
import com.adeloc.iptv.data.local.entity.LiveChannel
import com.adeloc.iptv.data.local.entity.StreamCategory
import com.adeloc.iptv.data.repository.XtreamSyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Type aliases for clarity
typealias LiveCategoryEntity = StreamCategory
typealias LiveChannelEntity = LiveChannel

class TvGuideViewModel(
    private val playlistDao: PlaylistDao,
    private val epgDao: EpgDao,
    private val activePlaylistId: Int,
    database: AppDatabase
) : ViewModel() {

    private val syncRepository = XtreamSyncRepository(playlistDao, epgDao, database.streamDao())

    val categories: StateFlow<List<LiveCategoryEntity>> =
        playlistDao.getCategoriesByPlaylist(activePlaylistId, "live")
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    var selectedCategory by mutableStateOf<LiveCategoryEntity?>(null)
        private set

    val categoryEpgData = MutableStateFlow<Map<LiveChannelEntity, List<EpgProgramEntity>>>(emptyMap())
    
    // To keep track of which categories we've already synced to avoid redundant API calls
    private val syncedCategories = mutableSetOf<String>()

    fun selectCategory(category: LiveCategoryEntity?) {
        if (selectedCategory != category) {
            selectedCategory = category
            category?.let {
                loadEpgForCategory(it)
            } ?: viewModelScope.launch { categoryEpgData.emit(emptyMap()) }
        }
    }

    fun loadEpgForCategory(category: LiveCategoryEntity) {
        selectedCategory = category
        loadEpgForCategory(category.categoryId)
    }

    private fun loadEpgForCategory(categoryId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            categoryEpgData.value = emptyMap()
            val channels = playlistDao.getChannelsByCategory(activePlaylistId, categoryId).first()
            val tempMap = mutableMapOf<LiveChannelEntity, List<EpgProgramEntity>>()
            val channelsMissingEpg = mutableListOf<LiveChannelEntity>()

            channels.forEach { channel ->
                var programs = epgDao.getProgramsForChannel(activePlaylistId, channel.streamId)

                if (programs.isEmpty() && !channel.epgChannelId.isNullOrBlank()) {
                    programs = epgDao.getProgramsForChannel(activePlaylistId, channel.epgChannelId.trim())
                }

                if (programs.isEmpty()) {
                    programs = epgDao.getEpgForChannelByName(activePlaylistId, channel.name.trim())
                }
                
                if (programs.isEmpty()) {
                    channelsMissingEpg.add(channel)
                }
                
                tempMap[channel] = programs
            }
            
            // Emit the initial local data
            categoryEpgData.value = tempMap
            
            // Fetch missing EPGs from API in the background if we haven't synced this category yet
            if (channelsMissingEpg.isNotEmpty() && !syncedCategories.contains(categoryId)) {
                syncedCategories.add(categoryId)
                
                channelsMissingEpg.forEach { channel ->
                    try {
                        syncRepository.syncEpgForChannel(activePlaylistId, channel.streamId)
                        
                        // Fetch the newly inserted programs from DB
                        var newPrograms = epgDao.getProgramsForChannel(activePlaylistId, channel.streamId)
                        if (newPrograms.isEmpty() && !channel.epgChannelId.isNullOrBlank()) {
                            newPrograms = epgDao.getProgramsForChannel(activePlaylistId, channel.epgChannelId.trim())
                        }
                        if (newPrograms.isEmpty()) {
                            newPrograms = epgDao.getEpgForChannelByName(activePlaylistId, channel.name.trim())
                        }
                        
                        if (newPrograms.isNotEmpty()) {
                            // Update the UI map incrementally
                            val updatedMap = categoryEpgData.value.toMutableMap()
                            updatedMap[channel] = newPrograms
                            categoryEpgData.value = updatedMap
                        }
                    } catch (e: Exception) {
                        // Ignore errors for individual channels so we continue with the next
                    }
                }
            }
        }
    }

    class Factory(
        private val database: AppDatabase,
        private val activePlaylistId: Int
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TvGuideViewModel::class.java)) {
                return TvGuideViewModel(
                    database.playlistDao(),
                    database.epgDao(),
                    activePlaylistId,
                    database
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
