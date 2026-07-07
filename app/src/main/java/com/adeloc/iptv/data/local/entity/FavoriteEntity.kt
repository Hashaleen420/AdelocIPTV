package com.adeloc.iptv.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "favorites",
    indices = [Index(value = ["playlistId", "streamId"], unique = true)]
)
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val playlistId: Int,
    val streamId: String, // Provider's stream ID
    val streamType: StreamType
)
