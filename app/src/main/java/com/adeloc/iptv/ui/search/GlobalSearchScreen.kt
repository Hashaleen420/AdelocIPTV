package com.adeloc.iptv.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.adeloc.iptv.data.local.entity.LiveChannel
import com.adeloc.iptv.data.local.entity.StreamEntity
import com.adeloc.iptv.data.local.entity.StreamType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    viewModel: GlobalSearchViewModel,
    onChannelClick: (LiveChannel) -> Unit,
    onStreamClick: (StreamEntity) -> Unit,
    onBack: () -> Unit
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val liveChannels by viewModel.liveChannels.collectAsStateWithLifecycle()
    val movies by viewModel.movies.collectAsStateWithLifecycle()
    val series by viewModel.series.collectAsStateWithLifecycle()
    val parentalEnabled by viewModel.parentalControlEnabled.collectAsStateWithLifecycle()
    val savedPin by viewModel.parentalControlPin.collectAsStateWithLifecycle()

    var showFavoriteDialog by remember { mutableStateOf(false) }
    var selectedStream by remember { mutableStateOf<StreamEntity?>(null) }
    var selectedChannel by remember { mutableStateOf<LiveChannel?>(null) }

    var showPinDialog by remember { mutableStateOf(false) }
    var pendingNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }

    val handleSecureClick: (String, () -> Unit) -> Unit = { name, navigate ->
        val isSensitive = name.contains("adult", true) || 
                         name.contains("xxx", true) || 
                         name.contains("18+", true)
        
        if (isSensitive && parentalEnabled) {
            pendingNavigation = navigate
            showPinDialog = true
        } else {
            navigate()
        }
    }

    if (showPinDialog) {
        var pinInput by remember { mutableStateOf("") }
        var isError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { 
                showPinDialog = false
                pendingNavigation = null
            },
            title = { Text("Parental Lock") },
            text = {
                Column {
                    Text("Enter PIN to view restricted content", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { 
                            if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                pinInput = it
                                isError = false
                            }
                        },
                        label = { Text("PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = isError,
                        supportingText = { if (isError) Text("Incorrect PIN", color = Color.Red) }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinInput == savedPin) {
                            showPinDialog = false
                            pendingNavigation?.invoke()
                            pendingNavigation = null
                        } else {
                            isError = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)
                ) {
                    Text("Unlock", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showPinDialog = false
                    pendingNavigation = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showFavoriteDialog) {
        AlertDialog(
            onDismissRequest = { showFavoriteDialog = false },
            title = { Text("Favourites") },
            text = { 
                val name = selectedStream?.name ?: selectedChannel?.name ?: ""
                val isFav = selectedStream?.isFavorite ?: selectedChannel?.isFavorite ?: false
                Text(if (isFav) "Remove $name from favourites?" else "Add $name to favourites?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedStream?.let { viewModel.toggleFavorite(it.streamId ?: "", it.streamType) }
                        selectedChannel?.let { viewModel.toggleFavorite(it.streamId, StreamType.LIVE) }
                        showFavoriteDialog = false
                    }
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFavoriteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onQueryChange(it) },
                        placeholder = { Text("Search channels, movies, series...", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onQueryChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF121212)),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (liveChannels.isNotEmpty()) {
                item {
                    SearchSectionHeader("Live Channels")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(liveChannels) { channel ->
                            SearchItemCard(
                                title = channel.name,
                                imageUrl = channel.logoUrl,
                                onClick = { handleSecureClick(channel.name) { onChannelClick(channel) } },
                                onLongClick = {
                                    selectedChannel = channel
                                    selectedStream = null
                                    showFavoriteDialog = true
                                }
                            )
                        }
                    }
                }
            }

            if (movies.isNotEmpty()) {
                item {
                    SearchSectionHeader("Movies")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(movies) { movie ->
                            SearchItemCard(
                                title = movie.name,
                                imageUrl = movie.logoUrl,
                                onClick = { handleSecureClick(movie.name) { onStreamClick(movie) } },
                                onLongClick = {
                                    selectedStream = movie
                                    selectedChannel = null
                                    showFavoriteDialog = true
                                }
                            )
                        }
                    }
                }
            }

            if (series.isNotEmpty()) {
                item {
                    SearchSectionHeader("Series")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(series) { s ->
                            SearchItemCard(
                                title = s.name,
                                imageUrl = s.logoUrl,
                                onClick = { handleSecureClick(s.name) { onStreamClick(s) } },
                                onLongClick = {
                                    selectedStream = s
                                    selectedChannel = null
                                    showFavoriteDialog = true
                                }
                            )
                        }
                    }
                }
            }

            if (searchQuery.length >= 2 && liveChannels.isEmpty() && movies.isEmpty() && series.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No results found for \"$searchQuery\"", color = Color.Gray)
                    }
                }
            } else if (searchQuery.length < 2) {
                item {
                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Type at least 2 characters to search", color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun SearchSectionHeader(title: String) {
    Text(
        text = title,
        color = Color.Cyan,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun SearchItemCard(title: String, imageUrl: String?, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier
                .size(120.dp, 170.dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 2,
            lineHeight = 14.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
            fontWeight = FontWeight.Medium
        )
    }
}
