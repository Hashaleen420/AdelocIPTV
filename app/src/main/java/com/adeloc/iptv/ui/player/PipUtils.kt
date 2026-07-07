package com.adeloc.iptv.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.util.Rational
import androidx.media3.common.Player
import androidx.media3.common.VideoSize

fun enterPipMode(context: Context, player: Player) {
    val activity = context.findActivity() ?: return
    val videoSize = player.videoSize
    
    val aspect = if (videoSize != VideoSize.UNKNOWN) {
        Rational(videoSize.width, videoSize.height)
    } else {
        Rational(16, 9)
    }
    
    val pipParams = PictureInPictureParams.Builder()
        .setAspectRatio(aspect)
        .build()
    
    activity.enterPictureInPictureMode(pipParams)
}

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
