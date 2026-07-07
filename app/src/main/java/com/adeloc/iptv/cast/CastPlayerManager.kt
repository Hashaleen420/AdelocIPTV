package com.adeloc.iptv.cast

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener

@OptIn(UnstableApi::class)
class CastPlayerManager(
    context: Context,
    private val localPlayer: ExoPlayer
) : SessionAvailabilityListener, SessionManagerListener<CastSession> {

    private val castContext = CastContext.getSharedInstance(context)
    private val castPlayer = CastPlayer(castContext, CastMediaItemConverter())
    
    var isCasting by mutableStateOf(false)
        private set
    
    var castDeviceName by mutableStateOf("")
        private set

    init {
        castPlayer.setSessionAvailabilityListener(this)
        castContext.sessionManager.addSessionManagerListener(this, CastSession::class.java)
    }

    fun playMedia(mediaItem: MediaItem) {
        if (isCasting) {
            castPlayer.setMediaItem(mediaItem)
            castPlayer.prepare()
            castPlayer.play()
        } else {
            localPlayer.setMediaItem(mediaItem)
            localPlayer.prepare()
            localPlayer.play()
        }
    }

    // SessionAvailabilityListener
    override fun onCastSessionAvailable() {
        val currentMediaItem = localPlayer.currentMediaItem
        val currentPosition = localPlayer.currentPosition
        
        localPlayer.pause()
        
        if (currentMediaItem != null) {
            castPlayer.setMediaItem(currentMediaItem, currentPosition)
            castPlayer.prepare()
            castPlayer.play()
        }
        
        isCasting = true
        castDeviceName = castContext.sessionManager.currentCastSession?.castDevice?.friendlyName ?: "Chromecast"
    }

    override fun onCastSessionUnavailable() {
        val currentPosition = castPlayer.currentPosition
        val currentMediaItem = castPlayer.currentMediaItem
        
        castPlayer.stop()
        
        if (currentMediaItem != null) {
            localPlayer.setMediaItem(currentMediaItem, currentPosition)
            localPlayer.prepare()
            localPlayer.play()
        }
        
        isCasting = false
        castDeviceName = ""
    }

    // SessionManagerListener
    override fun onSessionStarting(session: CastSession) {}
    override fun onSessionStarted(session: CastSession, sessionId: String) {
        // onCastSessionAvailable is usually called by the CastPlayer's listener
    }
    override fun onSessionStartFailed(session: CastSession, error: Int) {}
    override fun onSessionEnding(session: CastSession) {}
    override fun onSessionEnded(session: CastSession, error: Int) {
        onCastSessionUnavailable()
    }
    override fun onSessionResuming(session: CastSession, sessionId: String) {}
    override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {}
    override fun onSessionResumeFailed(session: CastSession, error: Int) {}
    override fun onSessionSuspended(session: CastSession, reason: Int) {}

    fun release() {
        castContext.sessionManager.removeSessionManagerListener(this, CastSession::class.java)
        castPlayer.release()
    }
}
