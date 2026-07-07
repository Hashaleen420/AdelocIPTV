package com.adeloc.iptv.ui.tv

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.adeloc.iptv.data.local.datastore.UserPreferences
import com.adeloc.iptv.data.local.entity.LiveChannel
import com.adeloc.iptv.data.local.entity.StreamCategory
import com.adeloc.iptv.ui.player.VideoPlayer
import com.adeloc.iptv.util.ChannelLogoMatcher
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTvDashboard(
    viewModel: LiveTvViewModel,
    isInPipMode: Boolean = false,
    onFullScreenClick: (String) -> Unit
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val epgPrograms by viewModel.epgPrograms.collectAsStateWithLifecycle()
    val isSyncing = viewModel.isSyncing
    
    val selectedCategory = viewModel.selectedCategory
    val selectedChannel = viewModel.selectedChannel

    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var showChannelSidebar by rememberSaveable { mutableStateOf(false) }
    
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val userPrefs = remember { UserPreferences.getInstance(context) }
    val parentalEnabled by userPrefs.parentalControlEnabledFlow.collectAsState(initial = false)
    val savedPin by userPrefs.parentalControlPinFlow.collectAsState(initial = "0000")

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var channelToManage by remember { mutableStateOf<LiveChannel?>(null) }
    var categoryToUnlock by remember { mutableStateOf<StreamCategory?>(null) }

    val premiumAccent = Color(0xFF2979FF)

    // Strongly enforce immersive mode in the Live TV section
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window ?: return@DisposableEffect onDispose {}
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
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Do not show bars on dispose to maintain global immersive state
        }
    }

    BackHandler(isFullscreen) {
        if (showChannelSidebar) {
            showChannelSidebar = false
        } else {
            isFullscreen = false
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0) // Ignore default insets to prevent status bar space reservation
    ) { padding ->
        val contentPadding = if (isFullscreen) PaddingValues(0.dp) else padding
        
        Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            if (isFullscreen) {
                if (channels.isNotEmpty()) {
                    val initialPageIndex = remember {
                        channels.indexOfFirst { it.streamId == selectedChannel?.streamId }.coerceAtLeast(0)
                    }
                    val pagerState = rememberPagerState(initialPage = initialPageIndex) { channels.size }

                    LaunchedEffect(pagerState.currentPage) {
                        val channel = channels[pagerState.currentPage]
                        viewModel.selectChannel(channel)
                        viewModel.updateLastWatched(channel)
                    }

                    VerticalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        key = { channels[it].streamId }
                    ) { page ->
                        val channel = channels[page]
                        val isActivePage = pagerState.currentPage == page
                        var controlsVisible by remember { mutableStateOf(true) }

                        // Temporary Peek on Channel Change
                        LaunchedEffect(pagerState.currentPage) {
                            if (pagerState.currentPage == page) controlsVisible = true
                        }

                        VideoPlayer(
                            videoUrl = channel.streamUrl,
                            title = channel.name,
                            isActivePage = isActivePage,
                            onProgressUpdate = { _, _ -> },
                            onControllerVisibilityChanged = { controlsVisible = it },
                            isControllerVisible = controlsVisible,
                            modifier = Modifier.fillMaxSize(),
                            enablePip = true,
                            isFullscreen = true,
                            streamType = "live",
                            onNextChannel = {
                                scope.launch {
                                    if (pagerState.currentPage < channels.size - 1) {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                }
                            },
                            onPreviousChannel = {
                                scope.launch {
                                    if (pagerState.currentPage > 0) {
                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                    }
                                }
                            },
                            onBack = { isFullscreen = false },
                            showCastButton = !isInPipMode,
                            showChannelSidebar = showChannelSidebar,
                            onChannelSidebarToggle = { showChannelSidebar = it },
                            channels = channels,
                            selectedChannel = selectedChannel,
                            categoryName = selectedCategory?.categoryName ?: "Channels",
                            onChannelClick = { clickedChannel ->
                                scope.launch {
                                    val index = channels.indexOfFirst { it.streamId == clickedChannel.streamId }
                                    if (index != -1) {
                                        pagerState.scrollToPage(index)
                                    }
                                }
                            },
                            bottomContent = {
                                // Integrated Mini EPG Overlay passed to VideoPlayer HUD
                                if (!isInPipMode) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f), Color.Black)
                                                )
                                            )
                                            .padding(horizontal = 24.dp, vertical = 24.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            val logoUrl = channel.logoUrl.takeIf { !it.isNullOrBlank() }
                                                ?: ChannelLogoMatcher.getFallbackLogoUrl(channel.name)
                                            
                                            AsyncImage(
                                                model = logoUrl,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(64.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color.White.copy(alpha = 0.1f))
                                                    .padding(4.dp),
                                                contentScale = ContentScale.Fit
                                            )
                                            
                                            Spacer(modifier = Modifier.width(16.dp))
                                            
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = channel.name,
                                                    color = Color.White,
                                                    fontSize = 20.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1
                                                )
                                                
                                                if (page == pagerState.currentPage && epgPrograms.isNotEmpty()) {
                                                    val currentProg = epgPrograms.first()
                                                    Text(
                                                        text = currentProg.title,
                                                        color = premiumAccent,
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        maxLines = 1
                                                    )
                                                    Text(
                                                        text = "${formatTimestamp(currentProg.startTime)} - ${formatTimestamp(currentProg.endTime)}",
                                                        color = Color.LightGray,
                                                        fontSize = 14.sp
                                                    )
                                                } else if (page == pagerState.currentPage) {
                                                    Text(text = "No EPG data", color = Color.Gray, fontSize = 14.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            } else {
                // Portrait/Dashboard Mode
                Row(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {
                    // Categories
                    LazyColumn(modifier = Modifier.fillMaxHeight().weight(0.2f).background(Color(0xFF1A1A1A))) {
                        item { Text("Categories", color = Color.White, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium) }
                        items(categories) { category ->
                            CategoryItem(
                                category = category,
                                isSelected = category.categoryId == selectedCategory?.categoryId,
                                onSelect = { 
                                    val isSensitive = category.categoryName.contains("adult", true) || 
                                                     category.categoryName.contains("xxx", true) || 
                                                     category.categoryName.contains("18+", true)
                                    if (isSensitive && parentalEnabled) categoryToUnlock = category
                                    else viewModel.selectCategory(category)
                                },
                                accentColor = premiumAccent
                            )
                        }
                    }

                    // Channels
                    Box(modifier = Modifier.fillMaxHeight().weight(0.3f).background(Color(0xFF222222))) {
                        if (channels.isEmpty() && !isSyncing) {
                            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("No Channels Found", color = Color.Gray)
                                if (selectedCategory?.categoryId != "-1" && selectedCategory?.categoryId != "-2") {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(onClick = { viewModel.forceSync() }, colors = ButtonDefaults.buttonColors(containerColor = premiumAccent)) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Force Sync Data", color = Color.White)
                                    }
                                }
                            }
                        } else {
                            LazyColumn {
                                item { Text(selectedCategory?.categoryName ?: "Select Category", color = Color.White, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium) }
                                items(channels) { channel ->
                                    ChannelItem(
                                        channel = channel,
                                        isSelected = channel.id == selectedChannel?.id,
                                        onSelect = { 
                                            viewModel.selectChannel(channel)
                                            viewModel.updateLastWatched(channel)
                                        },
                                        onLongClick = { channelToManage = it }
                                    )
                                }
                            }
                        }
                    }

                    // Preview and Info
                    Column(modifier = Modifier.fillMaxHeight().weight(0.5f)) {
                        Box(modifier = Modifier.fillMaxWidth().weight(0.4f).background(Color.Black)) {
                            selectedChannel?.let { channel ->
                                VideoPlayer(
                                    videoUrl = channel.streamUrl,
                                    title = channel.name,
                                    onProgressUpdate = { _, _ -> },
                                    isControllerVisible = false,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(modifier = Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = { isFullscreen = true })) {
                                    Icon(imageVector = Icons.Default.Fullscreen, contentDescription = "Fullscreen", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(24.dp))
                                    Text("PREVIEW", color = Color.White.copy(alpha = 0.5f), modifier = Modifier.align(Alignment.TopEnd).padding(8.dp), fontSize = 12.sp)
                                }
                            } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Select a channel to preview", color = Color.Gray)
                            }
                        }

                        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp)) {
                            selectedChannel?.let { channel ->
                                item {
                                    Text(channel.name, color = premiumAccent, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                if (epgPrograms.isNotEmpty()) {
                                    val currentProg = epgPrograms.first()
                                    item {
                                        Text(currentProg.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                                        Text("${formatTimestamp(currentProg.startTime)} - ${formatTimestamp(currentProg.endTime)}", color = Color.Gray, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(currentProg.description ?: "No description available", color = Color.LightGray, fontSize = 14.sp)
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.DarkGray)
                                        Text("Next Programs", color = premiumAccent, style = MaterialTheme.typography.titleSmall)
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                    items(epgPrograms.drop(1).take(5)) { nextProg ->
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                            Text(formatTimestamp(nextProg.startTime), color = Color.Gray, modifier = Modifier.width(60.dp))
                                            Text(nextProg.title, color = Color.White)
                                        }
                                    }
                                } else {
                                    item { Text("No EPG data found", color = Color.Gray) }
                                }
                            }
                        }
                    }
                }
            }
            
            if (isSyncing && !isFullscreen) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = premiumAccent)
                }
            }
        }
    }

    // Parental Pin Dialog
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
                    if (error) Text("Incorrect PIN", color = Color.Red, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(onClick = { if (pinInput == savedPin) { viewModel.selectCategory(categoryToUnlock!!); categoryToUnlock = null } else error = true }, colors = ButtonDefaults.buttonColors(containerColor = premiumAccent)) { Text("Unlock", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { categoryToUnlock = null }) { Text("Cancel") } }
        )
    }

    // Channel Options Dialog
    channelToManage?.let { channel ->
        AlertDialog(
            onDismissRequest = { channelToManage = null },
            shape = RoundedCornerShape(16.dp),
            title = { Text(text = "Channel Options", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
            text = { Text(text = channel.name, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold) },
            confirmButton = {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.toggleFavorite(channel.streamId); val msg = if (channel.isFavorite) "Removed from Favourites" else "Added to Favourites"; channelToManage = null; scope.launch { snackbarHostState.showSnackbar(msg) } }) { Text(text = if (channel.isFavorite) "Remove from Favourites" else "Add to Favourites", textAlign = TextAlign.Center) }
                    if (channel.lastWatched > 0L) {
                        TextButton(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.removeFromRecents(channel.streamId); channelToManage = null; scope.launch { snackbarHostState.showSnackbar("Removed from Recently Watched") } }) { Text(text = "Remove from Recents", color = Color.Red, textAlign = TextAlign.Center) }
                    }
                    TextButton(modifier = Modifier.fillMaxWidth(), onClick = { channelToManage = null }) { Text(text = "Cancel", textAlign = TextAlign.Center) }
                }
            },
            dismissButton = null
        )
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
fun CategoryItem(category: StreamCategory, isSelected: Boolean, onSelect: () -> Unit, accentColor: Color) {
    Surface(onClick = onSelect, color = if (isSelected) accentColor.copy(alpha = 0.15f) else Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Text(text = category.categoryName, color = if (isSelected) accentColor else Color.LightGray, modifier = Modifier.padding(16.dp), fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun ChannelItem(channel: LiveChannel, isSelected: Boolean, onSelect: () -> Unit, onLongClick: (LiveChannel) -> Unit) {
    val logoUrl = channel.logoUrl.takeIf { !it.isNullOrBlank() } ?: ChannelLogoMatcher.getFallbackLogoUrl(channel.name)
    Row(modifier = Modifier.fillMaxWidth().pointerInput(channel) { detectTapGestures(onTap = { onSelect() }, onLongPress = { onLongClick(channel) }) }.background(if (isSelected) Color.White.copy(alpha = 0.05f) else Color.Transparent).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = logoUrl, contentDescription = null, modifier = Modifier.size(50.dp).clip(RoundedCornerShape(4.dp)).border(1.dp, Color.Gray, RoundedCornerShape(4.dp)), contentScale = ContentScale.Fit)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(channel.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("Select to view EPG", color = Color.Gray, fontSize = 12.sp, maxLines = 1)
        }
    }
}
