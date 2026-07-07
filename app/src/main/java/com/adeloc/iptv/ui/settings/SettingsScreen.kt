@file:OptIn(ExperimentalMaterial3Api::class)

package com.adeloc.iptv.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adeloc.iptv.data.local.entity.PlaylistProfile
import com.adeloc.iptv.data.local.entity.StreamCategory

enum class PinAction { TOGGLE_OFF, CHANGE_PIN, REMOVE_PIN }

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToDashboard: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val playlists by viewModel.playlists.collectAsState()
    val activeId by viewModel.activePlaylistId.collectAsState()
    val syncInterval by viewModel.syncInterval.collectAsState()
    val parentalEnabled by viewModel.parentalControlEnabled.collectAsState()
    val currentPin by viewModel.parentalControlPin.collectAsState()
    val syncProgressMessage by viewModel.syncProgressMessage.collectAsState()
    
    val context = LocalContext.current

    var showAddDialog by remember { mutableStateOf(false) }
    var playlistToActivate by remember { mutableStateOf<PlaylistProfile?>(null) }
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showVerifyPinDialog by remember { mutableStateOf(false) }
    var pendingPinAction by remember { mutableStateOf<PinAction?>(null) }
    
    var showCategoryEditor by remember { mutableStateOf(false) }

    val premiumAccent = Color(0xFF2979FF)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121212))
            )
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Section 1: Playlist Management
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Manage Playlists",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add", tint = premiumAccent)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                items(playlists) { playlist ->
                    val isActive = playlist.id == activeId
                    PlaylistCard(
                        playlist = playlist,
                        isActive = isActive,
                        onSelect = { 
                            if (!isActive) {
                                playlistToActivate = playlist
                            }
                        },
                        onDelete = { 
                            viewModel.deletePlaylist(playlist, onNoPlaylistsLeft = onNavigateToLogin) 
                        },
                        onEditCategories = {
                            showCategoryEditor = true
                        },
                        accentColor = premiumAccent
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item { Spacer(modifier = Modifier.height(32.dp)) }

                // Section 2: Parental Control
                item {
                    Text(
                        "Parental Control",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E1E1E))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Enable PIN Lock", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Locks Adult/Sensitive categories", color = Color.Gray, fontSize = 12.sp)
                        }
                        Switch(
                            checked = parentalEnabled,
                            onCheckedChange = { enabled ->
                                if (parentalEnabled && !enabled) {
                                    pendingPinAction = PinAction.TOGGLE_OFF
                                    showVerifyPinDialog = true
                                } else {
                                    viewModel.setParentalControlEnabled(enabled)
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = premiumAccent)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (parentalEnabled) {
                                    pendingPinAction = PinAction.CHANGE_PIN
                                    showVerifyPinDialog = true
                                } else {
                                    showPinSetupDialog = true
                                }
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E1E))
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = premiumAccent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (parentalEnabled) "Change PIN" else "Set PIN", color = Color.White)
                        }

                        if (parentalEnabled) {
                            Button(
                                onClick = {
                                    pendingPinAction = PinAction.REMOVE_PIN
                                    showVerifyPinDialog = true
                                },
                                modifier = Modifier.weight(1f).height(56.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF331111))
                            ) {
                                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color.Red)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Remove", color = Color.White)
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(32.dp)) }

                // Section 3: Sync Preferences
                item {
                    Text(
                        "Auto-Update & Sync",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    SyncIntervalPicker(
                        selectedInterval = syncInterval,
                        onIntervalSelected = { viewModel.updateSyncInterval(context, it) },
                        accentColor = premiumAccent
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(
                        onClick = { viewModel.syncNow() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = premiumAccent),
                        enabled = !viewModel.isSyncing
                    ) {
                        if (viewModel.isSyncing) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                        } else {
                            Text("Sync Now", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (viewModel.isSyncing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = premiumAccent)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(syncProgressMessage, color = Color.White)
                        }
                    }
                }
            }
        }
    }

    if (showCategoryEditor) {
        CategoryEditorUI(
            viewModel = viewModel,
            onDismiss = { showCategoryEditor = false },
            accentColor = premiumAccent
        )
    }

    if (showAddDialog) {
        AddPlaylistDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, url, user, pass -> 
                viewModel.addPlaylist(name, url, user, pass)
                showAddDialog = false
            },
            accentColor = premiumAccent
        )
    }

    if (showVerifyPinDialog) {
        var pinInput by remember { mutableStateOf("") }
        var isError by remember { mutableStateOf(false) }
        val focusManager = LocalFocusManager.current

        AlertDialog(
            onDismissRequest = { 
                showVerifyPinDialog = false
                pendingPinAction = null
            },
            title = { Text("Enter Current PIN") },
            text = {
                Column {
                    Text("Security verification required.", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { 
                            if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                pinInput = it
                                isError = false
                            }
                        },
                        label = { Text("Current PIN") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                        }),
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
                        if (pinInput == currentPin) {
                            showVerifyPinDialog = false
                            when (pendingPinAction) {
                                PinAction.TOGGLE_OFF -> viewModel.setParentalControlEnabled(false)
                                PinAction.CHANGE_PIN -> showPinSetupDialog = true
                                PinAction.REMOVE_PIN -> viewModel.removeParentalControl()
                                null -> {}
                            }
                            pendingPinAction = null
                        } else {
                            isError = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = premiumAccent)
                ) {
                    Text("Verify", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showVerifyPinDialog = false
                    pendingPinAction = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPinSetupDialog) {
        var pinText by remember { mutableStateOf("") }
        val focusManager = LocalFocusManager.current
        
        AlertDialog(
            onDismissRequest = { showPinSetupDialog = false },
            title = { Text(if (parentalEnabled) "Change Parental PIN" else "Set Parental PIN") },
            text = {
                Column {
                    Text("Enter a new 4-digit number", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pinText,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pinText = it },
                        label = { Text("New PIN") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                        }),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinText.length == 4) {
                            viewModel.saveParentalControlPin(pinText)
                            viewModel.setParentalControlEnabled(true)
                            showPinSetupDialog = false
                        }
                    },
                    enabled = pinText.length == 4,
                    colors = ButtonDefaults.buttonColors(containerColor = premiumAccent)
                ) {
                    Text("Save PIN", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinSetupDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    playlistToActivate?.let { playlist ->
        AlertDialog(
            onDismissRequest = { playlistToActivate = null },
            title = { Text("Activate Playlist?") },
            text = { Text("Switch to '${playlist.name}' and sync new content?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.onPlaylistSelect(playlist.id) {
                            onNavigateToDashboard()
                        }
                        playlistToActivate = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = premiumAccent)
                ) {
                    Text("Confirm", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToActivate = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun CategoryEditorUI(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
    accentColor: Color
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Live TV", "Movies", "Series")
    val types = listOf("live", "vod", "series")
    
    val categories by viewModel.getCategories(types[selectedTab]).collectAsState(initial = emptyList())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E1E1E),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.9f)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = "Edit Categories",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.padding(16.dp),
                fontWeight = FontWeight.Bold
            )

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = accentColor,
                divider = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
            ) {
                items(categories, key = { it.id }) { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = category.categoryName,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = !category.isHidden,
                            onCheckedChange = { viewModel.toggleCategoryVisibility(category) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = accentColor,
                                checkedTrackColor = accentColor.copy(alpha = 0.5f)
                            )
                        )
                    }
                    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.5f))
                }
            }
            
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Text("Done", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PlaylistCard(
    playlist: PlaylistProfile,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onEditCategories: () -> Unit,
    accentColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onSelect() },
        shape = RoundedCornerShape(12.dp),
        border = if (isActive) BorderStroke(2.dp, accentColor) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) Color(0xFF1E3A3A) else Color(0xFF1E1E1E)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.PlaylistPlay,
                    contentDescription = null,
                    tint = if (isActive) accentColor else Color.Gray
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(playlist.name, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(playlist.serverUrl, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
                }
                if (isActive) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = accentColor)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.6f))
                }
            }
            
            if (isActive) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onEditCategories,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D2D2D)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edit Categories", color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun SyncIntervalPicker(selectedInterval: Int, onIntervalSelected: (Int) -> Unit, accentColor: Color) {
    val intervals = listOf(
        "Daily" to 1,
        "3 Days" to 3,
        "5 Days" to 5,
        "Weekly" to 7
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        intervals.forEach { (label, days) ->
            val isSelected = selectedInterval == days
            FilterChip(
                selected = isSelected,
                onClick = { onIntervalSelected(days) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accentColor,
                    selectedLabelColor = Color.Black
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun AddPlaylistDialog(onDismiss: () -> Unit, onAdd: (String, String, String, String) -> Unit, accentColor: Color) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    val focusManager = LocalFocusManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Xtream Playlist") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("http://example.com:8080") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        onAdd(name, url, user, pass)
                    })
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(name, url, user, pass) },
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Text("Save", color = Color.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
