package com.adeloc.iptv.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object PipState {
    var isVideoPlaying by mutableStateOf(false)
}
