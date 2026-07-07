package com.adeloc.iptv.ui.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.adeloc.iptv.data.local.AppDatabase
import com.adeloc.iptv.data.local.dao.PlaylistDao
import com.adeloc.iptv.data.local.datastore.UserPreferences
import com.adeloc.iptv.data.local.entity.PlaylistProfile
import com.adeloc.iptv.data.local.entity.StreamCategory
import com.adeloc.iptv.data.repository.XtreamRepository
import com.adeloc.iptv.data.repository.XtreamSyncRepository
import com.adeloc.iptv.worker.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val playlistDao: PlaylistDao,
    private val userPreferences: UserPreferences,
    private val syncRepository: XtreamSyncRepository
) : ViewModel() {

    private val xtreamRepository = XtreamRepository()

    // Detached scope for long running sync tasks that shouldn't be cancelled on navigation
    private val detachedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val playlists: StateFlow<List<PlaylistProfile>> = playlistDao.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activePlaylistId: StateFlow<Int> = userPreferences.activePlaylistIdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1)

    val syncInterval: StateFlow<Int> = userPreferences.syncIntervalFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    val parentalControlEnabled: StateFlow<Boolean> = userPreferences.parentalControlEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val parentalControlPin: StateFlow<String> = userPreferences.parentalControlPinFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "0000")

    var isSyncing by mutableStateOf(false)
        private set

    val syncProgressMessage: StateFlow<String> = syncRepository.syncMessage

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getCategories(type: String): Flow<List<StreamCategory>> {
        return activePlaylistId.flatMapLatest { id ->
            if (id == -1) flowOf(emptyList())
            else playlistDao.getAllCategoriesByPlaylist(id, type)
        }
    }

    fun toggleCategoryVisibility(category: StreamCategory) {
        viewModelScope.launch {
            playlistDao.updateCategory(category.copy(isHidden = !category.isHidden))
        }
    }

    fun onPlaylistSelect(playlistId: Int, onSuccess: () -> Unit = {}) {
        isSyncing = true
        
        // Launch in detached scope to survive navigation
        detachedScope.launch {
            try {
                // 1. Update active status in DB and Prefs
                playlistDao.activatePlaylist(playlistId)
                userPreferences.saveActivePlaylistId(playlistId)
                
                // 2. Fetch the fresh playlist directly from DAO to avoid race conditions
                val freshPlaylist = playlistDao.getPlaylistById(playlistId)
                
                if (freshPlaylist != null) {
                    // 3. Sync new content (Repository handles clearing old data)
                    syncRepository.syncXtreamPlaylist(freshPlaylist.id)
                }
            } catch (e: Exception) {
                // Handle or log error
            } finally {
                withContext(Dispatchers.Main) {
                    isSyncing = false
                    onSuccess()
                }
            }
        }
    }

    private fun formatExpirationDateForPreSync(expElement: com.google.gson.JsonElement?): String {
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

    fun addPlaylist(name: String, url: String, user: String, pass: String) {
        viewModelScope.launch {
            var rawExpiry = "Unlimited"
            
            try {
                val loginResponse = xtreamRepository.login(url, user, pass)
                if (loginResponse.isSuccess) {
                    val expElement = loginResponse.getOrNull()?.userInfo?.exp_date
                    rawExpiry = formatExpirationDateForPreSync(expElement)
                }
            } catch (e: Exception) {}

            val newPlaylist = PlaylistProfile(
                name = name,
                serverUrl = url,
                username = user,
                password = pass,
                type = "xtream",
                isActive = false,
                expDate = rawExpiry
            )
            playlistDao.insertPlaylist(newPlaylist)
        }
    }

    fun deletePlaylist(playlist: PlaylistProfile, onNoPlaylistsLeft: () -> Unit) {
        viewModelScope.launch {
            val wasActive = activePlaylistId.value == playlist.id
            playlistDao.deletePlaylist(playlist)
            
            val remaining = playlistDao.getAllPlaylistsSync()
            
            if (remaining.isEmpty()) {
                userPreferences.saveActivePlaylistId(-1)
                withContext(Dispatchers.Main) {
                    onNoPlaylistsLeft()
                }
            } else if (wasActive) {
                val nextActive = remaining.first()
                onPlaylistSelect(nextActive.id)
            }
        }
    }

    fun updateSyncInterval(context: Context, days: Int) {
        viewModelScope.launch {
            userPreferences.saveSyncInterval(days)
            SyncWorker.scheduleSync(context, days)
        }
    }

    fun setParentalControlEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setParentalControlEnabled(enabled)
        }
    }

    fun saveParentalControlPin(pin: String) {
        viewModelScope.launch {
            userPreferences.saveParentalControlPin(pin)
        }
    }

    fun removeParentalControl() {
        viewModelScope.launch {
            userPreferences.clearParentalControl()
        }
    }

    fun syncNow() {
        isSyncing = true
        detachedScope.launch {
            try {
                val activeIdFromPrefs = activePlaylistId.value
                val activePlaylist = if (activeIdFromPrefs != -1) {
                    playlistDao.getPlaylistById(activeIdFromPrefs)
                } else null

                if (activePlaylist != null) {
                    syncRepository.syncXtreamPlaylist(activePlaylist.id)
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isSyncing = false
                }
            }
        }
    }

    class Factory(private val database: AppDatabase, private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SettingsViewModel(
                    database.playlistDao(),
                    UserPreferences.getInstance(context),
                    XtreamSyncRepository(database.playlistDao(), database.epgDao(), database.streamDao())
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
