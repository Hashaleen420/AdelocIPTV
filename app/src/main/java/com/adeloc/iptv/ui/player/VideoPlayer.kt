package com.adeloc.iptv.ui.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import androidx.mediarouter.app.MediaRouteButton
import coil.compose.AsyncImage
import com.adeloc.iptv.cast.CastMediaItemConverter
import com.adeloc.iptv.data.local.AppDatabase
import com.adeloc.iptv.data.local.entity.LiveChannel
import com.adeloc.iptv.util.ChannelLogoMatcher
import com.adeloc.iptv.util.PipState
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier,
    title: String = "Stream",
    posterUrl: String = "",
    resumePosition: Long = 0L,
    streamId: String = "",
    streamType: String? = null,
    onProgressUpdate: (Long, Long) -> Unit,
    onControllerVisibilityChanged: (Boolean) -> Unit = {},
    enablePip: Boolean = false,
    isControllerVisible: Boolean = true,
    isFullscreen: Boolean = false,
    isActivePage: Boolean = true,
    onNextChannel: (() -> Unit)? = null,
    onPreviousChannel: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    showCastButton: Boolean = false,
    bottomContent: (@Composable () -> Unit)? = null,
    showChannelSidebar: Boolean = false,
    onChannelSidebarToggle: (Boolean) -> Unit = {},
    channels: List<LiveChannel> = emptyList(),
    selectedChannel: LiveChannel? = null,
    onChannelClick: (LiveChannel) -> Unit = {},
    categoryName: String = ""
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val database = remember { AppDatabase.getInstance(context) }
    val playerViewModel: PlayerViewModel = viewModel(factory = PlayerViewModel.Factory(database.streamDao(), database.playlistDao(), database.recentDao()))

    val isLiveTv = streamType?.trim()?.equals("live", ignoreCase = true) == true
    var showTrackSelection by remember { mutableStateOf(false) }

    // Internal player state
    var isPlaying by remember { mutableStateOf(false) }
    var interactionCount by remember { mutableIntStateOf(0) }

    // Internal visibility state to support both controlled and uncontrolled usage
    var isControlsVisible by remember { mutableStateOf(isControllerVisible) }

    // Double-tap seek hints
    var showForward10 by remember { mutableStateOf(false) }
    var showBackward10 by remember { mutableStateOf(false) }

    // System levels and indicator states
    var systemVolume by remember { mutableFloatStateOf(0.5f) }
    var systemBrightness by remember { mutableFloatStateOf(0.5f) }
    var showVolumeIndicator by remember { mutableStateOf(false) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }

    val premiumAccent = Color(0xFF2979FF)

    // Optimization: Keep callbacks updated
    val currentOnControllerVisibilityChanged by rememberUpdatedState(onControllerVisibilityChanged)

    fun syncSystemLevels() {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curr = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        systemVolume = if (max > 0) (curr.toFloat() / max.toFloat()).coerceIn(0f, 1f) else 0f

        val activity = context as? Activity
        val lp = activity?.window?.attributes
        systemBrightness = if (lp != null && lp.screenBrightness >= 0) lp.screenBrightness.coerceIn(0f, 1f) else 0.5f
    }

    LaunchedEffect(Unit) { syncSystemLevels() }

    // Sync internal state with prop (for Live TV peeking)
    LaunchedEffect(isControllerVisible) {
        isControlsVisible = isControllerVisible
    }

    // Bulletproof Auto-Hide Timer
    LaunchedEffect(isControlsVisible, interactionCount) {
        if (isControlsVisible) {
            delay(4000L)
            isControlsVisible = false
            currentOnControllerVisibilityChanged(false)
        }
    }

    LaunchedEffect(showForward10) { if (showForward10) { delay(800); showForward10 = false } }
    LaunchedEffect(showBackward10) { if (showBackward10) { delay(800); showBackward10 = false } }

    val exoPlayer = remember {
        val trackSelector = DefaultTrackSelector(context)
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, DefaultHttpDataSource.Factory().setUserAgent("VLC/3.0.0"))))
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(1000, 8000, 1000, 1000).build())
            .build().apply {
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("EXO_TRACK_CRASH", "Player crashed: ${error.errorCodeName} - ${error.message}")
                        Log.e("EXO_TRACK_CRASH", "Caused by: ${error.cause?.message}")
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val state = when (playbackState) {
                            Player.STATE_BUFFERING -> "BUFFERING"
                            Player.STATE_READY -> "READY"
                            Player.STATE_ENDED -> "ENDED"
                            Player.STATE_IDLE -> "IDLE"
                            else -> "UNKNOWN"
                        }
                        Log.d("EXO_TRACK_STATE", "State changed to: $state")
                    }
                })
            }
    }

    var currentPlayer by remember { mutableStateOf<Player>(exoPlayer) }

    // Master Continuous Progress Tracker (fixes issue where tracking stopped if HUD hid)
    LaunchedEffect(currentPlayer, isLiveTv) {
        while (isActive) {
            val playing = currentPlayer.isPlaying
            val ready = currentPlayer.playbackState == Player.STATE_READY
            
            if (!isLiveTv && (playing || ready)) {
                val pos = currentPlayer.currentPosition
                val dur = currentPlayer.duration
                if (dur > 0) {
                    onProgressUpdate(pos, dur)
                }
            }
            delay(1000)
        }
    }

    DisposableEffect(currentPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                PipState.isVideoPlaying = playing
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY || state == Player.STATE_BUFFERING) {
                    PipState.isVideoPlaying = currentPlayer.isPlaying
                }
            }
        }
        currentPlayer.addListener(listener)
        isPlaying = currentPlayer.isPlaying
        PipState.isVideoPlaying = isPlaying
        onDispose { currentPlayer.removeListener(listener) }
    }

    LaunchedEffect(isPlaying) {
        PipState.isVideoPlaying = isPlaying
    }

    val castContext = remember { try { CastContext.getSharedInstance(context) } catch (_: Exception) { null } }
    DisposableEffect(castContext) {
        val castPlayer = castContext?.let { CastPlayer(it, CastMediaItemConverter()) }
        val listener = object : SessionAvailabilityListener {
            override fun onCastSessionAvailable() {
                castPlayer?.let { player ->
                    coroutineScope.launch {
                        val currentPos = exoPlayer.currentPosition
                        exoPlayer.stop()
                        val mediaItem = MediaItem.Builder().setUri(videoUrl).build()
                        player.setMediaItem(mediaItem, currentPos); player.prepare(); player.play()
                        currentPlayer = player
                    }
                }
            }
            override fun onCastSessionUnavailable() {
                coroutineScope.launch {
                    val currentPos = currentPlayer.currentPosition
                    currentPlayer.stop()
                    val mediaItem = MediaItem.Builder().setUri(videoUrl).build()
                    exoPlayer.setMediaItem(mediaItem, currentPos); exoPlayer.prepare(); exoPlayer.play()
                    currentPlayer = exoPlayer
                }
            }
        }
        castPlayer?.setSessionAvailabilityListener(listener)
        onDispose { castPlayer?.setSessionAvailabilityListener(null); castPlayer?.release() }
    }

    LaunchedEffect(videoUrl, isActivePage) {
        if (isActivePage) {
            currentPlayer.stop()
            currentPlayer.clearMediaItems()
            val mediaItem = MediaItem.Builder().setUri(videoUrl).setMediaMetadata(MediaMetadata.Builder().setTitle(title).build()).build()
            currentPlayer.setMediaItem(mediaItem)
            if (resumePosition > 0L && currentPlayer == exoPlayer) currentPlayer.seekTo(resumePosition)
            currentPlayer.prepare()
            currentPlayer.play()
            if (streamId.isNotEmpty()) playerViewModel.updateLastWatched(streamId, streamType)
        } else {
            currentPlayer.pause()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                val activity = context as? Activity
                if (currentPlayer == exoPlayer && activity?.isInPictureInPictureMode == false) {
                    // Only pause if not playing - allows system PiP swipe-up to work
                    if (!isPlaying) currentPlayer.pause()
                }
            } else if (event == Lifecycle.Event.ON_RESUME && currentPlayer == exoPlayer) {
                if (isActivePage) currentPlayer.play()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
            PipState.isVideoPlaying = false
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        
        // 1. Android Player Surface (Lowest layer) isolated with remember
        val playerView = remember {
            PlayerView(context).apply {
                useController = false
                controllerAutoShow = false
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
        }

        DisposableEffect(currentPlayer) {
            playerView.player = currentPlayer
            onDispose { 
                // Do not release playerView.player here to prevent flicker during fast switches, 
                // it is managed by currentPlayer state changes.
            }
        }

        AndroidView(
            factory = { playerView },
            modifier = Modifier.fillMaxSize()
        )

        // 3. Side Indicators (Independent Fading)
        if (isFullscreen) {
            AnimatedVisibility(
                visible = showBrightnessIndicator,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                SideIndicator(Alignment.CenterStart, Icons.Default.Brightness7, systemBrightness, premiumAccent)
            }
            AnimatedVisibility(
                visible = showVolumeIndicator,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                SideIndicator(Alignment.CenterEnd, Icons.AutoMirrored.Filled.VolumeUp, systemVolume, premiumAccent)
            }
        }

        // 2. Master Gesture Overlay (Handles swipes and taps on empty space)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isLiveTv) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            // Execute the Seek (onDoubleTap)
                            if (offset.y < size.height - 150f) {
                                interactionCount++
                                val isLeft = offset.x < size.width / 2
                                if (isLeft) {
                                    // Left Half: backward 10s
                                    currentPlayer.seekTo(maxOf(0L, currentPlayer.currentPosition - 10000L))
                                    showBackward10 = true
                                } else {
                                    // Right Half: forward 10s
                                    currentPlayer.seekTo(minOf(currentPlayer.duration, currentPlayer.currentPosition + 10000L))
                                    showForward10 = true
                                }
                            }
                        },
                        onTap = { offset ->
                            // Restore Single Tap (onTap): Toggle player controls
                            if (offset.y < size.height - 150f) {
                                if (showChannelSidebar) {
                                    onChannelSidebarToggle(false)
                                } else {
                                    interactionCount++
                                    isControlsVisible = !isControlsVisible
                                    currentOnControllerVisibilityChanged(isControlsVisible)
                                    if (!isControlsVisible) syncSystemLevels()
                                }
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            // Initial pass ensures we see the event before HUD buttons.
                            val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Initial)
                            val width = size.width
                            val height = size.height

                            // Protect bottom edge for system gestures (PiP / Home) - 150px zone
                            if (down.position.y > height - 150f) continue

                            var dragTriggered = false
                            var wasSwipeHandled = false
                            var totalDeltaY = 0f
                            var totalDeltaX = 0f
                            val slop = viewConfiguration.touchSlop

                            // Swipe zones: left 35% for brightness, right 35% for volume
                            val isLeftZone = down.position.x < width * 0.35f
                            val isRightZone = down.position.x > width * 0.65f
                            val canSwipe = isFullscreen && (isLeftZone || isRightZone)

                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                val change = event.changes.find { it.id == down.id } ?: break

                                if (change.pressed) {
                                    val dY = (change.position.y - change.previousPosition.y).absoluteValue
                                    val dX = (change.position.x - change.previousPosition.x).absoluteValue
                                    totalDeltaY += dY
                                    totalDeltaX += dX

                                    if (!dragTriggered && (totalDeltaY > slop || totalDeltaX > slop)) {
                                        dragTriggered = true
                                        // Handle swipe if vertical AND in side zones
                                        if (canSwipe && totalDeltaY > totalDeltaX) {
                                            wasSwipeHandled = true
                                            syncSystemLevels()
                                            // Show indicators precisely when swipe starts
                                            if (isLeftZone) showBrightnessIndicator = true
                                            else showVolumeIndicator = true
                                        }
                                    }

                                    if (wasSwipeHandled) {
                                        interactionCount++
                                        change.consume()
                                        val delta = (change.position.y - change.previousPosition.y) / height
                                        if (isLeftZone) {
                                            systemBrightness = (systemBrightness - delta).coerceIn(0f, 1f)
                                            (context as? Activity)?.let { activity ->
                                                val lp = activity.window.attributes
                                                lp.screenBrightness = systemBrightness.coerceIn(0.01f, 1.0f)
                                                activity.window.attributes = lp
                                            }
                                        } else {
                                            systemVolume = (systemVolume - delta).coerceIn(0f, 1f)
                                            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (systemVolume * max).toInt(), 0)
                                        }
                                    }

                                    // If a real drag occurred but it wasn't a handled swipe, break so Slider or Pager can handle it.
                                    if (dragTriggered && !wasSwipeHandled) break

                                } else {
                                    break
                                }
                            }
                            // End of drag gesture lifecycle: Hide indicators immediately
                            showBrightnessIndicator = false
                            showVolumeIndicator = false
                        }
                    }
                }
        ) {
            // 2.5 Double-tap Seek Hints
            Box(modifier = Modifier.fillMaxSize()) {
                // Left half for backward hint
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .fillMaxHeight()
                        .align(Alignment.CenterStart),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedVisibility(
                        visible = showBackward10,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Replay10, null, tint = Color.White, modifier = Modifier.size(48.dp))
                            Text("-10s", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
                // Right half for forward hint
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .fillMaxHeight()
                        .align(Alignment.CenterEnd),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedVisibility(
                        visible = showForward10,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Forward10, null, tint = Color.White, modifier = Modifier.size(48.dp))
                            Text("+10s", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }

            // 4. Custom HUD Layer (Highest z-index)
            AnimatedVisibility(
                visible = isControlsVisible,
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                ) {
                    // Integrated Top Bar: Includes Back, Title, Cast, and Settings in ONE Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onBack != null) {
                            IconButton(onClick = {
                                interactionCount++
                                onBack()
                            }) {
                                Icon(
                                    imageVector = if (isFullscreen && isLiveTv) Icons.Default.FullscreenExit else Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        // Action Buttons Row (Unified spacing to prevent overlap)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (showCastButton) {
                                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                    AndroidView(
                                        factory = { ctx ->
                                            val themedCtx = ContextThemeWrapper(ctx, androidx.appcompat.R.style.Theme_AppCompat_NoActionBar)
                                            MediaRouteButton(themedCtx).apply {
                                                CastButtonFactory.setUpMediaRouteButton(context, this)
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                            }

                            if (isLiveTv && isFullscreen) {
                                IconButton(onClick = {
                                    interactionCount++
                                    onChannelSidebarToggle(true)
                                }) {
                                    Icon(Icons.Default.Menu, "Channels", tint = Color.White, modifier = Modifier.size(28.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                            }

                            IconButton(onClick = {
                                interactionCount++
                                showTrackSelection = true
                            }) {
                                Icon(Icons.Default.Settings, "Tracks", tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                        }
                    }

                    // Center Play/Pause Toggle
                    Box(modifier = Modifier.align(Alignment.Center).fillMaxWidth()) {
                        Surface(
                            onClick = {
                                interactionCount++
                                if (isPlaying) currentPlayer.pause() else currentPlayer.play()
                            },
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.4f),
                            modifier = Modifier.align(Alignment.Center).size(85.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(68.dp)
                                )
                            }
                        }
                    }

                    // Channel Controls (Right Side)
                    if (isLiveTv && !(context as? Activity)?.isInPictureInPictureMode!! && !showChannelSidebar) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = if (isFullscreen) 24.dp else 8.dp),
                            verticalArrangement = Arrangement.spacedBy(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            FilledIconButton(onClick = {
                                interactionCount++
                                onPreviousChannel?.invoke()
                            }, modifier = Modifier.size(32.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.6f), contentColor = Color.White)) { Icon(Icons.Default.KeyboardArrowUp, "Prev", modifier = Modifier.size(20.dp)) }
                            FilledIconButton(onClick = {
                                interactionCount++
                                onNextChannel?.invoke()
                            }, modifier = Modifier.size(32.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.6f), contentColor = Color.White)) { Icon(Icons.Default.KeyboardArrowDown, "Next", modifier = Modifier.size(20.dp)) }
                        }
                    }

                    // Integrated Bottom Slot for Mini-EPG or Seekbar
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                    ) {
                        if (bottomContent != null) {
                            bottomContent()
                        } else {
                            VideoPlayerSeeker(
                                player = currentPlayer,
                                isLiveTv = isLiveTv,
                                onInteraction = { interactionCount++ },
                                accentColor = premiumAccent
                            )
                        }
                    }
                }
            }
        }

        // 5. Channel Sidebar Drawer
        AnimatedVisibility(
            visible = showChannelSidebar,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.fillMaxHeight().fillMaxWidth(0.35f).align(Alignment.CenterEnd)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Text(
                        text = categoryName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(channels) { channel ->
                            val isSelected = channel.id == selectedChannel?.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        interactionCount++
                                        onChannelClick(channel)
                                        onChannelSidebarToggle(false)
                                    }
                                    .background(if (isSelected) premiumAccent.copy(alpha = 0.2f) else Color.Transparent)
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val logoUrl = channel.logoUrl.takeIf { !it.isNullOrBlank() }
                                    ?: ChannelLogoMatcher.getFallbackLogoUrl(channel.name)

                                AsyncImage(
                                    model = logoUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = channel.name,
                                    color = if (isSelected) premiumAccent else Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showTrackSelection) {
            TrackSelectionSheet(
                player = currentPlayer, 
                onDismiss = { showTrackSelection = false }
            )
        }
    }
}

@Composable
fun VideoPlayerSeeker(
    player: Player,
    isLiveTv: Boolean,
    onInteraction: () -> Unit,
    accentColor: Color
) {
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isDraggingSlider by remember { mutableStateOf(false) }

    LaunchedEffect(player) {
        while (isActive) {
            if (!isDraggingSlider) {
                position = player.currentPosition
                duration = player.duration
            }
            delay(1000)
        }
    }

    if (duration > 0 && !isLiveTv) {
        Column(
            modifier = Modifier
                .padding(bottom = 32.dp, start = 24.dp, end = 24.dp)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(position), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(formatTime(duration), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = position.toFloat(),
                onValueChange = {
                    onInteraction()
                    isDraggingSlider = true
                    position = it.toLong()
                },
                onValueChangeFinished = {
                    onInteraction()
                    player.seekTo(position)
                    isDraggingSlider = false
                },
                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor, inactiveTrackColor = Color.Gray.copy(alpha = 0.5f))
            )
        }
    }
}

@Composable
private fun BoxScope.SideIndicator(alignment: Alignment, icon: androidx.compose.ui.graphics.vector.ImageVector, value: Float, accentColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(alignment).padding(horizontal = 8.dp).width(44.dp).height(240.dp).clip(RoundedCornerShape(22.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        Box(modifier = Modifier.width(4.dp).weight(1f).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(value.coerceIn(0f, 1f))
                    .align(Alignment.BottomCenter)
                    .background(brush = Brush.verticalGradient(colors = listOf(accentColor, Color(0xFF00BFA5))))
            )
        }
        Text("${(value * 100).toInt()}%", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
