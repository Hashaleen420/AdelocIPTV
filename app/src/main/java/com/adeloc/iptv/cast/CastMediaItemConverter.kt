package com.adeloc.iptv.cast

import androidx.annotation.OptIn
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata as CastMediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.common.images.WebImage

@OptIn(UnstableApi::class)
class CastMediaItemConverter : MediaItemConverter {
    private val defaultConverter = DefaultMediaItemConverter()

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem {
        return defaultConverter.toMediaItem(mediaQueueItem)
    }

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val url = mediaItem.localConfiguration?.uri.toString()
        
        // Check if the content is Live TV based on URL or metadata
        val isLive = url.contains("/live/") || 
                     mediaItem.localConfiguration?.mimeType == "application/x-mpegURL" ||
                     url.contains(".m3u8")

        val streamType = if (isLive) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED
        val mimeType = if (isLive) "application/x-mpegURL" else (mediaItem.localConfiguration?.mimeType ?: "video/mp4")

        val metadata = mediaItem.mediaMetadata
        val castMetadata = CastMediaMetadata(
            if (isLive) CastMediaMetadata.MEDIA_TYPE_TV_SHOW 
            else CastMediaMetadata.MEDIA_TYPE_MOVIE
        )
        
        metadata.title?.let { 
            castMetadata.putString(CastMediaMetadata.KEY_TITLE, it.toString()) 
        }
        metadata.artworkUri?.let { 
            castMetadata.addImage(WebImage(it)) 
        }

        val mediaInfo = MediaInfo.Builder(url)
            .setStreamType(streamType)
            .setContentType(mimeType)
            .setMetadata(castMetadata)
            .build()
            
        return MediaQueueItem.Builder(mediaInfo).build()
    }
}
