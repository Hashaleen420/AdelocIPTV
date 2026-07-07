package com.adeloc.iptv.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adeloc.iptv.data.local.entity.PlaylistProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    playlistId: Int,
    viewModel: DashboardViewModel,
    onNavigate: (String) -> Unit
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    // Observe the currently selected playlist to get instant DB updates
    val playlist by viewModel.selectedPlaylist.collectAsStateWithLifecycle()

    var showPlaylistDialog by remember { mutableStateOf(false) }

    LaunchedEffect(playlistId) {
        viewModel.loadPlaylist(playlistId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Adeloc IPTV")
                        playlist?.let { p ->
                            Text(
                                text = "Expires: ${p.expDate ?: "Unlimited"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigate("search") }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { showPlaylistDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                modifier = Modifier.statusBarsPadding(),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF121212), Color(0xFF1E1E1E))
                    )
                )
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = playlist?.name ?: "Select Playlist",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Main Grid
                val menuItems = listOf(
                    DashboardItem("Live TV", Icons.Default.Tv, Color(0xFF2979FF), "live"),
                    DashboardItem("Movies", Icons.Default.Movie, Color(0xFF2196F3), "movies"),
                    DashboardItem("Series", Icons.Default.VideoLibrary, Color(0xFF4CAF50), "series"),
                    DashboardItem("Catch Up", Icons.Default.History, Color(0xFFFF9800), "catchup")
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(menuItems) { item ->
                        DashboardCard(item) {
                            onNavigate(item.route)
                        }
                    }
                }
            }

            if (showPlaylistDialog) {
                PlaylistSelectionDialog(
                    playlists = playlists,
                    onDismiss = { showPlaylistDialog = false },
                    onSelect = { 
                        viewModel.onPlaylistSelected(it)
                        showPlaylistDialog = false
                    }
                )
            }
        }
    }
}

data class DashboardItem(
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val route: String
)

@Composable
fun DashboardCard(item: DashboardItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.2f)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF252525))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                modifier = Modifier.size(48.dp),
                tint = item.color
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
    }
}

@Composable
fun PlaylistSelectionDialog(
    playlists: List<PlaylistProfile>,
    onDismiss: () -> Unit,
    onSelect: (PlaylistProfile) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Switch Playlist") },
        text = {
            Column {
                playlists.forEach { playlist ->
                    Text(
                        text = playlist.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(playlist) }
                            .padding(16.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
