package com.adeloc.iptv.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stream_categories",
    indices = [Index("playlistId")]
)
data class StreamCategory(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val categoryId: String, // ID from the provider (e.g., "10")
    val playlistId: Int,    // FK to PlaylistProfile (relaxed)
    val categoryName: String,
    val type: String, // "live", "vod", or "series"
    val isHidden: Boolean = false
)
