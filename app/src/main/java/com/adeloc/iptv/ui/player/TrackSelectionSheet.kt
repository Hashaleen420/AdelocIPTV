@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.adeloc.iptv.ui.player

import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.launch
import java.util.Locale

fun formatTrackName(format: Format, index: Int): String {
    val lang = format.language?.lowercase()
    if (lang.isNullOrBlank() || lang == "und" || lang == "vof") {
        return "Audio Track ${index + 1}"
    }
    return try {
        Locale.Builder().setLanguage(lang).build().displayLanguage.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    } catch (e: Exception) {
        "Audio Track ${index + 1}"
    }
}

@OptIn(UnstableApi::class)
@Composable
fun TrackSelectionSheet(
    player: Player,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val coroutineScope = rememberCoroutineScope()

    var availableAudioTracks by remember { mutableStateOf<List<Tracks.Group>>(emptyList()) }
    var availableSubtitleTracks by remember { mutableStateOf<List<Tracks.Group>>(emptyList()) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                availableAudioTracks = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                availableSubtitleTracks = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
            }
        }
        player.addListener(listener)
        // Initial population
        listener.onTracksChanged(player.currentTracks)
        onDispose { player.removeListener(listener) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E1E1E)
    ) {
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            // Section 1: Audio Tracks
            item {
                Text(
                    "Audio Tracks",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            availableAudioTracks.forEach { group ->
                for (trackIndex in 0 until group.length) {
                    val format = group.mediaTrackGroup.getFormat(trackIndex)
                    val isSelected = group.isTrackSelected(trackIndex)
                    item {
                        TrackItem(
                            label = formatTrackName(format, trackIndex),
                            isSelected = isSelected,
                            onClick = {
                                player.trackSelectionParameters = player.trackSelectionParameters
                                    .buildUpon()
                                    .setOverrideForType(
                                        TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
                                    )
                                    .build()
                                coroutineScope.launch {
                                    sheetState.hide()
                                    onDismiss()
                                }
                            }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            // Section 2: Subtitles
            item {
                Text(
                    "Subtitles",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            item {
                val isOffSelected = availableSubtitleTracks.all { group -> !group.isSelected }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = true) {
                            player.trackSelectionParameters = player.trackSelectionParameters
                                .buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                .build()
                            coroutineScope.launch {
                                sheetState.hide()
                                onDismiss()
                            }
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "None",
                        color = if (isOffSelected) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    if (isOffSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            availableSubtitleTracks.forEach { group ->
                for (trackIndex in 0 until group.length) {
                    val format = group.mediaTrackGroup.getFormat(trackIndex)
                    val isSelected = group.isTrackSelected(trackIndex)
                    val label = format.label ?: format.language ?: "Subtitle ${trackIndex + 1}"
                    item {
                        TrackItem(
                            label = label,
                            isSelected = isSelected,
                            onClick = {
                                player.trackSelectionParameters = player.trackSelectionParameters
                                    .buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                    .setOverrideForType(
                                        TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
                                    )
                                    .build()
                                coroutineScope.launch {
                                    sheetState.hide()
                                    onDismiss()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackItem(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
            modifier = Modifier.weight(1f)
        )
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
