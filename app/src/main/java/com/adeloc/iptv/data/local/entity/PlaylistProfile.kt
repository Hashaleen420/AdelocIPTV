package com.adeloc.iptv.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlist_profiles")
data class PlaylistProfile(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val serverUrl: String,
    val username: String? = null,
    val password: String? = null,
    val type: String, // "m3u" or "xtream"
    val isActive: Boolean = false,
    val expDate: String? = null
)
