package com.adeloc.iptv.ui.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.adeloc.iptv.data.local.datastore.UserPreferences
import com.adeloc.iptv.data.local.entity.StreamCategory
import com.adeloc.iptv.data.local.entity.StreamEntity
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@Composable
fun VodDashboard(
    viewModel: VodViewModel,
    title: String,
    onStreamClick: (StreamEntity, Long) -> Unit
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val streams by viewModel.streams.collectAsStateWithLifecycle()
    val isSyncing = viewModel.isSyncing
    val selectedCategory = viewModel.selectedCategory

    val context = LocalContext.current
    val userPrefs = remember { UserPreferences.getInstance(context) }
    val parentalEnabled by userPrefs.parentalControlEnabledFlow.collectAsState(initial = false)
    val savedPin by userPrefs.parentalControlPinFlow.collectAsState(initial = "0000")

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var streamToManage by remember { mutableStateOf<StreamEntity?>(null) }
    var categoryToUnlock by remember { mutableStateOf<StreamCategory?>(null) }
    var streamToResume by remember { mutableStateOf<StreamEntity?>(null) }

    val premiumAccent = Color(0xFF2979FF)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF121212))
            ) {
                // Categories List (Left Pane)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.25f)
                        .background(Color(0xFF1A1A1A))
                ) {
                    items(categories) { category ->
                        VodCategoryItem(
                            category = category,
                            isSelected = category.categoryId == selectedCategory?.categoryId,
                            onSelect = {
                                val isSensitive = category.categoryName.contains("adult", true) ||
                                        category.categoryName.contains("xxx", true) ||
                                        category.categoryName.contains("18+", true)

                                if (isSensitive && parentalEnabled) {
                                    categoryToUnlock = category
                                } else {
                                    viewModel.selectCategory(category)
                                }
                            },
                            accentColor = premiumAccent
                        )
                    }
                }

                // Content Grid (Right Pane)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.75f)
                        .padding(8.dp)
                ) {
                    if (streams.isEmpty() && !isSyncing) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("No Content Found", color = Color.Gray)
                            if (selectedCategory?.categoryId != "-1" && selectedCategory?.categoryId != "-2") {
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { viewModel.forceSync() },
                                    colors = ButtonDefaults.buttonColors(containerColor = premiumAccent)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Force Sync Data", color = Color.White)
                                }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 120.dp),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(streams) { stream ->
                                VodStreamItem(
                                    stream = stream,
                                    onClick = {
                                        if (stream.resumePosition > 0 && stream.duration > 0 && stream.resumePosition < stream.duration * 0.95) {
                                            streamToResume = stream
                                        } else {
                                            onStreamClick(stream, 0L)
                                        }
                                    },
                                    onLongClick = { streamToManage = it }
                                )
                            }
                        }
                    }
                }
            }

            if (isSyncing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = premiumAccent)
                }
            }
        }
    }

    if (categoryToUnlock != null) {
        var pinInput by remember { mutableStateOf("") }
        var error by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { categoryToUnlock = null },
            title = { Text("Parental Lock") },
            text = {
                Column {
                    Text("Enter PIN to unlock '${categoryToUnlock?.categoryName}'", color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 4) pinInput = it },
                        label = { Text("PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        isError = error,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (error) {
                        Text("Incorrect PIN", color = Color.Red, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinInput == savedPin) {
                            viewModel.selectCategory(categoryToUnlock!!)
                            categoryToUnlock = null
                        } else {
                            error = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = premiumAccent)
                ) {
                    Text("Unlock", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToUnlock = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    streamToResume?.let { stream ->
        ResumePlaybackDialog(
            stream = stream,
            onResume = {
                onStreamClick(stream, stream.resumePosition)
                streamToResume = null
            },
            onStartOver = {
                onStreamClick(stream, 0L)
                streamToResume = null
            },
            onDismiss = { streamToResume = null }
        )
    }

    streamToManage?.let { stream ->
        AlertDialog(
            onDismissRequest = { streamToManage = null },
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = "Stream Options",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = stream.name,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
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
                        onClick = {
                            stream.streamId?.let {
                                viewModel.toggleFavorite(it)
                                val msg = if (stream.isFavorite) "Removed from Favourites" else "Added to Favourites"
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                            streamToManage = null
                        }
                    ) {
                        Text(
                            text = if (stream.isFavorite) "Remove from Favourites" else "Add to Favourites",
                            textAlign = TextAlign.Center
                        )
                    }

                    if (stream.lastWatched > 0L) {
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                stream.streamId?.let {
                                    viewModel.removeFromRecents(it)
                                    scope.launch { snackbarHostState.showSnackbar("Removed from Recently Watched") }
                                }
                                streamToManage = null
                            }
                        ) {
                            Text(
                                text = "Remove from Recents",
                                color = Color.Red,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { streamToManage = null }
                    ) {
                        Text(
                            text = "Cancel",
                            textAlign = TextAlign.Center
                        )
                    }
                }
            },
            dismissButton = null
        )
    }
}

@Composable
fun VodCategoryItem(category: StreamCategory, isSelected: Boolean, onSelect: () -> Unit, accentColor: Color) {
    Surface(
        onClick = onSelect,
        color = if (isSelected) accentColor.copy(alpha = 0.15f) else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = category.categoryName,
            color = if (isSelected) accentColor else Color.LightGray,
            modifier = Modifier.padding(16.dp),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp
        )
    }
}

@Composable
fun VodStreamItem(
    stream: StreamEntity,
    onClick: () -> Unit,
    onLongClick: (StreamEntity) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .pointerInput(stream) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick(stream) }
                )
            },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF252525))
    ) {
        Column {
            AsyncImage(
                model = stream.logoUrl.takeIf { !it.isNullOrEmpty() } ?: "",
                contentDescription = stream.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentScale = ContentScale.Crop
            )
            Text(
                text = stream.name,
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 2,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            )
        }
    }
}

@Composable
fun ResumePlaybackDialog(
    stream: StreamEntity,
    onResume: () -> Unit,
    onStartOver: () -> Unit,
    onDismiss: () -> Unit
) {
    val resumePositionMillis = stream.resumePosition
    val hours = TimeUnit.MILLISECONDS.toHours(resumePositionMillis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(resumePositionMillis) % TimeUnit.HOURS.toMinutes(1)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(resumePositionMillis) % TimeUnit.MINUTES.toSeconds(1)

    val timeString = if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "Resume Playback?",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = "You were watching at $timeString. Would you like to resume?",
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
                Button(
                    onClick = onResume,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Resume")
                }
                OutlinedButton(
                    onClick = onStartOver,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Over")
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        },
        dismissButton = null
    )
}