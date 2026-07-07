package com.adeloc.iptv.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "epg_programs",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistProfile::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId"), Index("channelId")]
)
data class EpgProgram(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val playlistId: Int,      // Foreign key to the provider
    val channelId: String,    // The channel ID from the XMLTV file (e.g., "BBC1.uk")
    val startTime: String,    // Start time in format like "20240721000000 +0000"
    val stopTime: String,
    val title: String,
    val desc: String? = null
)
