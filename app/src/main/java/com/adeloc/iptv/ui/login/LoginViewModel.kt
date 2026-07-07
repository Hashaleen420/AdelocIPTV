package com.adeloc.iptv.ui.login

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.adeloc.iptv.data.local.AppDatabase
import com.adeloc.iptv.data.local.datastore.UserPreferences
import com.adeloc.iptv.data.local.entity.PlaylistProfile
import com.adeloc.iptv.data.repository.XtreamRepository
import com.adeloc.iptv.data.repository.XtreamSyncRepository
import com.google.gson.JsonElement
import kotlinx.coroutines.*
import retrofit2.HttpException
import java.text.SimpleDateFormat
import java.util.*

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = XtreamRepository()
    private val database = AppDatabase.getInstance(application)
    private val userPrefs = UserPreferences.getInstance(application)
    private val syncRepository = XtreamSyncRepository(database.playlistDao(), database.epgDao(), database.streamDao())
    
    private val externalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Xtream Fields
    var playlistName by mutableStateOf("")
    var url by mutableStateOf("")
    var username by mutableStateOf("")
    var password by mutableStateOf("")

    var isLoading by mutableStateOf(false)
    var loadingMessage by mutableStateOf("")
    var loginResult by mutableStateOf<Result<Int>?>(null)
    var errorMessage by mutableStateOf<String?>(null)

    fun onLoginClick(onSuccess: (Int) -> Unit) {
        externalScope.launch {
            if (playlistName.isBlank() || url.isBlank() || username.isBlank() || password.isBlank()) {
                withContext(Dispatchers.Main) {
                    errorMessage = "Please fill all fields"
                    loginResult = Result.failure(Exception("Please fill all fields"))
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                isLoading = true
                loadingMessage = "Authenticating..."
            }
            
            try {
                // 1. Authenticate with server
                val loginResult = repository.login(url, username, password)
                if (loginResult.isSuccess) {
                    val loginResponse = loginResult.getOrNull()
                    
                    val expElement = loginResponse?.userInfo?.exp_date
                    val formattedExp = if (expElement == null || expElement.isJsonNull) {
                        "Unlimited"
                    } else {
                        val rawExp = try {
                            if (expElement.isJsonPrimitive) expElement.asString else expElement.toString().replace("\"", "")
                        } catch (e: Exception) {
                            ""
                        }.trim()

                        if (rawExp.isEmpty() || rawExp.equals("null", ignoreCase = true) || rawExp == "0") {
                            "Unlimited"
                        } else {
                            try {
                                val timestamp = rawExp.toLong()
                                // Handle if it's seconds or milliseconds
                                val ms = if (timestamp < 100000000000L) timestamp * 1000L else timestamp
                                val date = java.util.Date(ms)
                                java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(date)
                            } catch (e: NumberFormatException) {
                                rawExp
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        loadingMessage = "Saving profile..."
                    }
                    
                    // 2. Deactivate others and insert new active playlist
                    database.playlistDao().deactivateAllPlaylists()
                    
                    val playlist = PlaylistProfile(
                        name = playlistName,
                        serverUrl = url,
                        username = username,
                        password = password,
                        type = "xtream",
                        isActive = true, // Mark as active immediately
                        expDate = formattedExp
                    )
                    val playlistId = database.playlistDao().insertPlaylist(playlist).toInt()
                    Log.d("IPTV_DEBUG", "Playlist inserted with ID: $playlistId and isActive=true")

                    // 3. Sync content BEFORE navigating
                    val messageJob = launch {
                        syncRepository.syncMessage.collect { msg ->
                            withContext(Dispatchers.Main) {
                                loadingMessage = msg
                            }
                        }
                    }

                    val syncResult = syncRepository.syncXtreamPlaylist(playlistId)
                    messageJob.cancel()
                    
                    if (syncResult.isSuccess) {
                        Log.d("IPTV_DEBUG", "Login and Sync successful, Playlist ID: $playlistId")
                        userPrefs.saveActivePlaylistId(playlistId)

                        withContext(Dispatchers.Main) {
                            this@LoginViewModel.loginResult = Result.success(playlistId)
                            // 4. Finally navigate only after sync is complete
                            onSuccess(playlistId)
                        }
                    } else {
                        val error = syncResult.exceptionOrNull() ?: Exception("Sync failed")
                        Log.e("IPTV_DEBUG", "Sync failed during login: ${error.message}")
                        withContext(Dispatchers.Main) {
                            errorMessage = "Sync failed: ${error.message}"
                            this@LoginViewModel.loginResult = Result.failure(error)
                        }
                    }
                } else {
                    val exception = loginResult.exceptionOrNull()
                    val message = when (exception) {
                        is HttpException -> {
                            when (exception.code()) {
                                401 -> "Invalid credentials (401)"
                                403 -> "Access forbidden (403)"
                                else -> "Server error: ${exception.code()}"
                            }
                        }
                        else -> exception?.message ?: "Login failed"
                    }
                    withContext(Dispatchers.Main) {
                        errorMessage = message
                        this@LoginViewModel.loginResult = Result.failure(Exception(message))
                    }
                }
            } catch (e: Exception) {
                Log.e("IPTV_DEBUG", "Login process error", e)
                withContext(Dispatchers.Main) {
                    errorMessage = e.message ?: "An unexpected error occurred"
                    this@LoginViewModel.loginResult = Result.failure(e)
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    loadingMessage = ""
                }
            }
        }
    }
}
