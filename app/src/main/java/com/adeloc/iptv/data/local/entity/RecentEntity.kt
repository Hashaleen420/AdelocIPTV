package com.adeloc.iptv.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recents",
    indices = [Index(value = ["playlistId", "streamId"], unique = true)]
)
data class RecentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val playlistId: Int,
    val streamId: String, // Provider's stream ID
    val streamType: StreamType,
    val lastWatched: Long,
    val resumePosition: Long = 0L,
    val duration: Long = 0L
)
