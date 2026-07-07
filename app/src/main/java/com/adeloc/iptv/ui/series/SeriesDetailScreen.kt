package com.adeloc.iptv.ui.series

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.adeloc.iptv.data.model.Episode
import com.adeloc.iptv.ui.vod.ResumePlaybackDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    viewModel: SeriesDetailViewModel,
    onPlayEpisode: (String, String, Long) -> Unit,
    onBack: () -> Unit
) {
    val detail = viewModel.seriesDetail
    val playlist = viewModel.playlist
    val error = viewModel.error
    var selectedSeason by remember { mutableStateOf<String?>(null) }
    var episodeToResume by remember { mutableStateOf<Pair<Episode, String>?>(null) }
    
    val seasons = detail?.episodes?.keys?.sortedBy { it.toIntOrNull() ?: 0 } ?: emptyList()
    
    LaunchedEffect(seasons) {
        if (selectedSeason == null && seasons.isNotEmpty()) {
            selectedSeason = seasons.first()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.info?.name ?: "Series Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF121212))
        ) {
            if (viewModel.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.Cyan)
                }
            } else if (error != null) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Oops!",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { onBack() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                    ) {
                        Text("Go Back")
                    }
                }
            } else if (detail != null) {
                // Use a single LazyColumn for everything to ensure landscape scrolling works
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    // Header Section (Poster + Info)
                    item {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
                            ) {
                                AsyncImage(
                                    model = detail.info?.cover,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color(0xFF121212))
                                        ))
                                )
                            }

                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = detail.info?.name ?: "",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = detail.info?.plot ?: "",
                                    fontSize = 14.sp,
                                    color = Color.LightGray,
                                    maxLines = 10 // Increased maxLines for scrollable content
                                )
                            }
                        }
                    }

                    // Season Selector Section
                    if (seasons.isNotEmpty()) {
                        item {
                            ScrollableTabRow(
                                selectedTabIndex = seasons.indexOf(selectedSeason).coerceAtLeast(0),
                                containerColor = Color.Transparent,
                                edgePadding = 16.dp,
                                divider = {}
                            ) {
                                seasons.forEach { season ->
                                    Tab(
                                        selected = selectedSeason == season,
                                        onClick = { selectedSeason = season },
                                        text = { Text("Season $season") }
                                    )
                                }
                            }
                        }
                    }

                    // Episodes List Section
                    val episodes = detail.episodes?.get(selectedSeason) ?: emptyList()
                    items(episodes) { episode ->
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            EpisodeItem(
                                episode = episode,
                                onClick = {
                                    playlist?.let { p ->
                                        val baseUrl = p.serverUrl.removeSuffix("/")
                                        val username = p.username ?: ""
                                        val password = p.password ?: ""
                                        val ext = episode.containerExtension ?: "mp4"
                                        val streamUrl = "${baseUrl}/series/${username}/${password}/${episode.id}.${ext}"
                                        
                                        val resumePos = viewModel.episodeProgressMap[streamUrl] ?: 0L

                                        if (resumePos > 0) {
                                            episodeToResume = Pair(episode, streamUrl)
                                        } else {
                                            val episodeTitle = "${detail.info?.name} - S${selectedSeason}E${episode.episodeNum}: ${episode.title}"
                                            viewModel.seriesDbId?.let { dbId ->
                                                viewModel.updateSeriesLastWatched(dbId)
                                            }
                                            onPlayEpisode(streamUrl, episodeTitle, 0L)
                                        }
                                    }
                                }
                            )
                        }
                    }
                    
                    // Extra spacing at the bottom
                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }

    episodeToResume?.let { (episode, streamUrl) ->
        val resumePos = viewModel.episodeProgressMap[streamUrl] ?: 0L
        val episodeTitle = "${detail?.info?.name} - S${selectedSeason}E${episode.episodeNum}: ${episode.title}"
        ResumePlaybackDialog(
            resumePosition = resumePos,
            onResume = {
                viewModel.seriesDbId?.let { dbId ->
                    viewModel.updateSeriesLastWatched(dbId)
                }
                onPlayEpisode(streamUrl, episodeTitle, resumePos)
                episodeToResume = null
            },
            onStartOver = {
                viewModel.seriesDbId?.let { dbId ->
                    viewModel.updateSeriesLastWatched(dbId)
                }
                onPlayEpisode(streamUrl, episodeTitle, 0L)
                episodeToResume = null
            },
            onDismiss = { episodeToResume = null }
        )
    }
}

@Composable
fun EpisodeItem(episode: Episode, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .height(80.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(120.dp).fillMaxHeight()) {
                AsyncImage(
                    model = episode.info?.movieImage,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.Center).size(32.dp),
                    tint = Color.White.copy(alpha = 0.8f)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${episode.episodeNum}. ${episode.title ?: "Episode"}",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = episode.info?.plot ?: "",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    maxLines = 2
                )
            }
        }
    }
}
