package com.adeloc.iptv.ui.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.adeloc.iptv.data.local.entity.StreamEntity
import java.util.Locale

@Composable
fun ContinueWatchingRow(
    streams: List<StreamEntity>,
    onStreamClick: (StreamEntity) -> Unit
) {
    if (streams.isEmpty()) return

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "Continue Watching",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(streams) { stream ->
                ContinueWatchingItem(stream = stream, onClick = { onStreamClick(stream) })
            }
        }
    }
}

@Composable
fun ContinueWatchingItem(stream: StreamEntity, onClick: () -> Unit) {
    val progress = if (stream.duration > 0) stream.resumePosition.toFloat() / stream.duration.toFloat() else 0f

    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable { onClick() }
    ) {
        Box {
            AsyncImage(
                model = stream.logoUrl,
                contentDescription = stream.name,
                modifier = Modifier
                    .height(240.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray),
                contentScale = ContentScale.Crop
            )
            
            // Progress Bar Overlay
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(4.dp)
                    .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)),
                color = Color.Red,
                trackColor = Color.Gray.copy(alpha = 0.5f),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stream.name,
            color = Color.White,
            maxLines = 1,
            fontSize = 14.sp
        )
    }
}

@Composable
fun ResumePlaybackDialog(
    resumePosition: Long,
    onResume: () -> Unit,
    onStartOver: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = { 
            Text(
                text = "Resume Playback",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            ) 
        },
        text = {
            Text(
                text = "You were watching at ${formatMillis(resumePosition)}.\nWould you like to resume where you left off?",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onResume
                ) {
                    Text(text = "Resume", textAlign = TextAlign.Center)
                }

                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onStartOver
                ) {
                    Text(text = "Start Over", textAlign = TextAlign.Center)
                }

                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDismiss
                ) {
                    Text(text = "Cancel", textAlign = TextAlign.Center, color = Color.Red)
                }
            }
        },
        dismissButton = null
    )
}

fun formatMillis(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%dh %02dm", hours, minutes)
    } else {
        String.format(Locale.getDefault(), "%dm", minutes)
    }
}
