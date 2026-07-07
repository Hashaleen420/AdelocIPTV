package com.adeloc.iptv.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "streams",
    indices = [Index("playlistId"), Index("categoryId")]
)
data class StreamEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val playlistId: Int,
    val categoryId: String?,
    val streamId: String?,
    val name: String,
    val logoUrl: String?,
    val groupTitle: String?,
    val url: String,
    val isLive: Boolean,
    val streamType: StreamType,
    val resumePosition: Long = 0L,
    val duration: Long = 0L,
    val lastWatched: Long = 0L,
    val isFavorite: Boolean = false
)

enum class StreamType {
    LIVE, VOD, SERIES
}

fun guessStreamType(url: String): StreamType {
    val lowercaseUrl = url.lowercase()
    return when {
        lowercaseUrl.contains("/movie/") || 
        lowercaseUrl.endsWith(".mp4") || 
        lowercaseUrl.endsWith(".mkv") || 
        lowercaseUrl.endsWith(".avi") -> StreamType.VOD
        
        lowercaseUrl.contains("/series/") -> StreamType.SERIES
        
        else -> StreamType.LIVE
    }
}
