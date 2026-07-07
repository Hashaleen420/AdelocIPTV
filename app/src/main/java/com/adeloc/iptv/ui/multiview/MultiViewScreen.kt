package com.adeloc.iptv.ui.multiview

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.adeloc.iptv.data.local.AppDatabase
import com.adeloc.iptv.data.local.datastore.UserPreferences
import com.adeloc.iptv.data.local.entity.LiveChannel
import com.adeloc.iptv.data.local.entity.StreamCategory
import com.adeloc.iptv.ui.tv.LiveTvViewModel
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun MultiViewScreen() {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val database = remember { AppDatabase.getInstance(context) }
    val userPrefs = remember { UserPreferences.getInstance(context) }
    val activePlaylistId by userPrefs.activePlaylistIdFlow.collectAsState(initial = -1)

    // Force Landscape orientation and immersive mode for Multi-View
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            
            fun hideBars() {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }

            hideBars()

            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    hideBars()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)

            onDispose {
                activity.requestedOrientation = originalOrientation
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        } else {
            onDispose {
                activity?.requestedOrientation = originalOrientation
            }
        }
    }

    // State management for Multi-View
    val selectedChannels = remember { mutableStateListOf<LiveChannel?>(null, null, null, null) }
    var activeAudioIndex by remember { mutableIntStateOf(0) }
    var fullscreenQuadrantIndex by remember { mutableStateOf<Int?>(null) }
    
    // Picker State
    var showChannelPicker by remember { mutableStateOf(false) }
    var targetingQuadrant by remember { mutableIntStateOf(-1) }

    // Use ViewModel if playlist is active
    val vm: LiveTvViewModel? = if (activePlaylistId != -1) {
        viewModel(factory = LiveTvViewModel.Factory(database, activePlaylistId))
    } else null

    val allChannels by (vm?.allChannels ?: flowOf(emptyList()))
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val categories by (vm?.categories ?: flowOf(emptyList()))
        .collectAsStateWithLifecycle(initialValue = emptyList())

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        if (fullscreenQuadrantIndex != null) {
            val index = fullscreenQuadrantIndex!!
            selectedChannels[index]?.let { channel ->
                QuadPlayerView(
                    channel = channel,
                    hasAudio = true,
                    onClick = { },
                    onRemove = {
                        selectedChannels[index] = null
                        fullscreenQuadrantIndex = null
                    },
                    onExpand = { fullscreenQuadrantIndex = null } // acts as minimize
                )
            } ?: run {
                fullscreenQuadrantIndex = null
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Row 1
                Row(modifier = Modifier.weight(1f)) {
                    MultiViewQuadrant(
                        index = 0,
                        channel = selectedChannels[0],
                        isActive = activeAudioIndex == 0,
                        onFocus = { activeAudioIndex = 0 },
                        onAddClick = { 
                            targetingQuadrant = 0
                            showChannelPicker = true 
                        },
                        onRemove = { selectedChannels[0] = null },
                        onExpand = { 
                            fullscreenQuadrantIndex = 0
                            activeAudioIndex = 0
                        },
                        modifier = Modifier.weight(1f)
                    )
                    MultiViewQuadrant(
                        index = 1,
                        channel = selectedChannels[1],
                        isActive = activeAudioIndex == 1,
                        onFocus = { activeAudioIndex = 1 },
                        onAddClick = { 
                            targetingQuadrant = 1
                            showChannelPicker = true 
                        },
                        onRemove = { selectedChannels[1] = null },
                        onExpand = { 
                            fullscreenQuadrantIndex = 1
                            activeAudioIndex = 1
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Row 2
                Row(modifier = Modifier.weight(1f)) {
                    MultiViewQuadrant(
                        index = 2,
                        channel = selectedChannels[2],
                        isActive = activeAudioIndex == 2,
                        onFocus = { activeAudioIndex = 2 },
                        onAddClick = { 
                            targetingQuadrant = 2
                            showChannelPicker = true 
                        },
                        onRemove = { selectedChannels[2] = null },
                        onExpand = { 
                            fullscreenQuadrantIndex = 2
                            activeAudioIndex = 2
                        },
                        modifier = Modifier.weight(1f)
                    )
                    MultiViewQuadrant(
                        index = 3,
                        channel = selectedChannels[3],
                        isActive = activeAudioIndex == 3,
                        onFocus = { activeAudioIndex = 3 },
                        onAddClick = { 
                            targetingQuadrant = 3
                            showChannelPicker = true 
                        },
                        onRemove = { selectedChannels[3] = null },
                        onExpand = { 
                            fullscreenQuadrantIndex = 3
                            activeAudioIndex = 3
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (showChannelPicker) {
        ChannelPickerDialog(
            channels = allChannels,
            categories = categories,
            onChannelSelected = { channel ->
                if (targetingQuadrant in 0..3) {
                    selectedChannels[targetingQuadrant] = channel
                }
                showChannelPicker = false
            },
            onDismiss = { showChannelPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun ChannelPickerDialog(
    channels: List<LiveChannel>,
    categories: List<StreamCategory>,
    onChannelSelected: (LiveChannel) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedPickerCategory by remember { mutableStateOf<StreamCategory?>(null) }
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(
    skipPartiallyExpanded = true, 
    confirmValueChange = { it != androidx.compose.material3.SheetValue.Hidden }
)

    val filteredChannels = remember(searchQuery, selectedPickerCategory, channels) {
        channels.filter { channel ->
            val matchesSearch = channel.name.contains(searchQuery, ignoreCase = true)
            val matchesCategory = when (selectedPickerCategory?.categoryId) {
                null -> true
                "-1" -> channel.isFavorite
                "-2" -> channel.lastWatched > 0L
                else -> channel.categoryId == selectedPickerCategory?.categoryId
            }
            matchesSearch && matchesCategory
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E1E1E),
        contentColor = Color.White,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select Channel",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxHeight(0.9f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        placeholder = { Text("Search channels...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                }

                item {
                    // Category Selection
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedPickerCategory == null,
                                onClick = { selectedPickerCategory = null },
                                label = { Text("All") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.White,
                                    labelColor = Color.Gray
                                )
                            )
                        }
                        items(categories) { category ->
                            FilterChip(
                                selected = selectedPickerCategory == category,
                                onClick = { selectedPickerCategory = category },
                                label = { Text(category.categoryName) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.White,
                                    labelColor = Color.Gray
                                )
                            )
                        }
                    }
                }

                items(filteredChannels, key = { it.streamId }) { channel ->
                    ChannelPickerItem(
                        channel = channel,
                        onClick = { onChannelSelected(channel) }
                    )
                }
            }
        }
    }
}

@Composable
fun ChannelPickerItem(
    channel: LiveChannel,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF2C2C2C))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = channel.logoUrl,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black),
            contentScale = ContentScale.Fit
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Text(
                text = "ID: ${channel.streamId}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun MultiViewQuadrant(
    index: Int,
    channel: LiveChannel?,
    isActive: Boolean,
    onFocus: () -> Unit,
    onAddClick: () -> Unit,
    onRemove: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .border(
                width = if (isActive) 2.dp else 0.5.dp,
                color = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f)
            )
            .clickable { onFocus() },
        contentAlignment = Alignment.Center
    ) {
        if (channel == null) {
            // Empty slot: Add Button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                IconButton(
                    onClick = onAddClick,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Channel")
                }
                Text(
                    text = "Add Channel",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.LightGray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        } else {
            QuadPlayerView(
                channel = channel,
                hasAudio = isActive,
                onClick = onFocus,
                onRemove = onRemove,
                onExpand = onExpand
            )
        }
        
        // Active Audio Indicator (Small overlay)
        if (isActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.extraSmall)
                )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun QuadPlayerView(
    channel: LiveChannel,
    hasAudio: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onExpand: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isBuffering by remember { mutableStateOf(true) }

    // Instantiate a clean instance of ExoPlayer tailored for Multi-View (low buffer overhead)
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                false // CRITICAL: This 'false' disables automatic audio focus handling!
            )
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(2500, 10000, 1000, 1500)
                    .build()
            )
            .build().apply {
                playWhenReady = true
            }
    }

    // Explicitly release player when the composable is removed
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // CRITICAL FOR AUDIO: Explicitly manage audio focus
    LaunchedEffect(hasAudio) {
        exoPlayer.volume = if (hasAudio) 1f else 0f
    }

    // Media loading
    LaunchedEffect(channel.streamUrl) {
        exoPlayer.setMediaItem(MediaItem.fromUri(channel.streamUrl))
        exoPlayer.prepare()
    }

    // Lifecycle management to pause/resume with app lifecycle
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> exoPlayer.play()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Grid Selection UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onClick() }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    // Force useController = false to completely strip out native UI elements
                    useController = false
                    controllerAutoShow = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Loading Spinner
        AnimatedVisibility(
            visible = isBuffering,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
        ) {
            IconButton(onClick = onExpand) {
                Icon(
                    imageVector = Icons.Default.Fullscreen, 
                    contentDescription = "Expand", 
                    tint = Color.White
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Close, 
                    contentDescription = "Close", 
                    tint = Color.White
                )
            }
        }
    }
}

// Helper to provide empty flow when VM is null
private fun <T> flowOf(value: T): kotlinx.coroutines.flow.Flow<T> = kotlinx.coroutines.flow.flowOf(value)