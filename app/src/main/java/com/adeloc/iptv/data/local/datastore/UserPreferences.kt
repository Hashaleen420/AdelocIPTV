package com.adeloc.iptv.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class UserPreferences private constructor(private val context: Context) {

    private object PreferencesKeys {
        val SYNC_INTERVAL_DAYS = intPreferencesKey("sync_interval_days")
        val ACTIVE_PLAYLIST_ID = intPreferencesKey("active_playlist_id")
        val PARENTAL_CONTROL_ENABLED = booleanPreferencesKey("parental_control_enabled")
        val PARENTAL_CONTROL_PIN = stringPreferencesKey("parental_control_pin")
    }

    val syncIntervalFlow: Flow<Int> = context.dataStore.data
        .catch { exception -> if (exception is IOException) emit(emptyPreferences()) else throw exception }
        .map { preferences -> preferences[PreferencesKeys.SYNC_INTERVAL_DAYS] ?: 1 }

    suspend fun saveSyncInterval(days: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SYNC_INTERVAL_DAYS] = days
        }
    }

    val activePlaylistIdFlow: Flow<Int> = context.dataStore.data
        .catch { exception -> if (exception is IOException) emit(emptyPreferences()) else throw exception }
        .map { preferences -> preferences[PreferencesKeys.ACTIVE_PLAYLIST_ID] ?: -1 }

    suspend fun saveActivePlaylistId(playlistId: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACTIVE_PLAYLIST_ID] = playlistId
        }
    }

    val parentalControlEnabledFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception -> if (exception is IOException) emit(emptyPreferences()) else throw exception }
        .map { preferences -> preferences[PreferencesKeys.PARENTAL_CONTROL_ENABLED] ?: false }

    suspend fun setParentalControlEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PARENTAL_CONTROL_ENABLED] = enabled
        }
    }

    val parentalControlPinFlow: Flow<String> = context.dataStore.data
        .catch { exception -> if (exception is IOException) emit(emptyPreferences()) else throw exception }
        .map { preferences -> preferences[PreferencesKeys.PARENTAL_CONTROL_PIN] ?: "0000" }

    suspend fun saveParentalControlPin(pin: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PARENTAL_CONTROL_PIN] = pin
        }
    }

    suspend fun clearParentalControl() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.PARENTAL_CONTROL_PIN)
            preferences[PreferencesKeys.PARENTAL_CONTROL_ENABLED] = false
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: UserPreferences? = null

        fun getInstance(context: Context): UserPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
