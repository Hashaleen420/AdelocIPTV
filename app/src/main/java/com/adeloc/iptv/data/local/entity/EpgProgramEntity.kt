package com.adeloc.iptv.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "epg_programs_new",
    indices = [Index("playlistId"), Index("channelId"), Index("startTime"), Index("endTime")]
)
data class EpgProgramEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val playlistId: Int,
    val channelId: String,
    val title: String,
    val description: String?,
    val startTime: Long, // Unix timestamp in milliseconds
    val endTime: Long    // Unix timestamp in milliseconds
)
