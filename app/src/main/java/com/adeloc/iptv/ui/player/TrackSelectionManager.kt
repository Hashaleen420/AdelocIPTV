package com.adeloc.iptv.ui.player

import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
class TrackSelectionManager(private val player: Player) {

    var audioTracks by mutableStateOf<List<TrackInfo>>(emptyList())
        private set
    var subtitleTracks by mutableStateOf<List<TrackInfo>>(emptyList())
        private set

    fun updateTracks() {
        val tracks = player.currentTracks
        audioTracks = fetchTracks(tracks, C.TRACK_TYPE_AUDIO)
        subtitleTracks = fetchTracks(tracks, C.TRACK_TYPE_TEXT)
    }

    private fun fetchTracks(tracks: Tracks, trackType: Int): List<TrackInfo> {
        val isDisabled = player.trackSelectionParameters.disabledTrackTypes.contains(trackType)
        val trackList = mutableListOf<TrackInfo>()
        tracks.groups.forEach { group ->
            if (group.type == trackType) {
                for (i in 0 until group.length) {
                    if (group.isTrackSupported(i)) {
                        val format = group.getTrackFormat(i)
                        trackList.add(
                            TrackInfo(
                                group = group.mediaTrackGroup,
                                trackIndex = i,
                                label = format.label ?: format.language ?: "Unknown",
                                isSelected = !isDisabled && group.isTrackSelected(i)
                            )
                        )
                    }
                }
            }
        }
        return trackList
    }

    fun selectTrack(trackInfo: TrackInfo) {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(TrackSelectionOverride(trackInfo.group, trackInfo.trackIndex))
            .build()
        updateTracks()
    }

    fun selectSubtitleTrack(trackInfo: TrackInfo) {
        // Re-enable for Specific Languages: Ensure the ignored flags are reset to 0
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setIgnoredTextSelectionFlags(0) // Reset flags!
            .setOverrideForType(TrackSelectionOverride(trackInfo.group, trackInfo.trackIndex))
            .build()
        updateTracks()
    }

    fun disableSubtitles() {
        // Aggressive 'None' Logic: Disable text tracks AND ignore all default/forced flags
        // This ensures ExoPlayer doesn't auto-select forced tracks when we want them OFF
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_FORCED)
            .build()
        updateTracks()
    }

    fun isSubtitlesDisabled(): Boolean {
        return player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
    }
}

@OptIn(UnstableApi::class)
data class TrackInfo(
    val group: TrackGroup,
    val trackIndex: Int,
    val label: String,
    val isSelected: Boolean
)
