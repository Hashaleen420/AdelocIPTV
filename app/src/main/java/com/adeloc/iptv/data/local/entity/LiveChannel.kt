package com.adeloc.iptv.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "live_channels",
    indices = [Index("playlistId"), Index("categoryDbId")]
)
data class LiveChannel(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val streamId: String,    // ID from provider
    val epgChannelId: String?, // ID for EPG mapping
    val playlistId: Int,    // FK to PlaylistProfile (relaxed)
    val categoryDbId: Int,  // FK to StreamCategory internal ID (relaxed)
    val categoryId: String,  // Provider category ID
    val name: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val isFavorite: Boolean = false,
    val lastWatched: Long = 0L
)
