package com.adeloc.iptv.ui.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.adeloc.iptv.data.local.entity.LiveChannel
import com.adeloc.iptv.util.ChannelLogoMatcher

@Composable
fun TvChannelGrid(
    channels: List<LiveChannel>,
    onChannelClick: (LiveChannel) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
    ) {
        items(channels) { channel ->
            TvChannelCard(
                channel = channel,
                onClick = { onChannelClick(channel) }
            )
        }
    }
}

@Composable
fun TvChannelCard(
    channel: LiveChannel,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.1f else 1.0f)
    
    val logoUrl = channel.logoUrl.takeIf { !it.isNullOrBlank() }
        ?: ChannelLogoMatcher.getFallbackLogoUrl(channel.name)

    Surface(
        onClick = onClick,
        modifier = Modifier
            .aspectRatio(16f / 9f)
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused },
        shape = RoundedCornerShape(8.dp),
        border = if (isFocused) BorderStroke(2.dp, Color.White) else null,
        color = Color(0xFF252525)
    ) {
        Box(contentAlignment = Alignment.Center) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(logoUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = channel.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            
            if (isFocused || logoUrl.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
