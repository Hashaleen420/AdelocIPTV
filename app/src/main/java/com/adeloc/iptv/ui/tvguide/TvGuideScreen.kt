package com.adeloc.iptv.ui.tvguide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.adeloc.iptv.data.local.AppDatabase
import com.adeloc.iptv.data.local.datastore.UserPreferences
import com.adeloc.iptv.data.local.entity.EpgProgramEntity
import com.adeloc.iptv.data.local.entity.LiveChannel
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

@Composable
fun TvGuideScreen(navController: NavController) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getInstance(context) }
    val userPrefs = remember { UserPreferences.getInstance(context) }
    val activePlaylistId by userPrefs.activePlaylistIdFlow.collectAsState(initial = -1)

    val viewModel: TvGuideViewModel? = if (activePlaylistId != -1) {
        viewModel(factory = TvGuideViewModel.Factory(database, activePlaylistId))
    } else null

    val categories by viewModel?.categories?.collectAsStateWithLifecycle(initialValue = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val selectedCategory = viewModel?.selectedCategory
    val categoryEpgData by viewModel?.categoryEpgData?.collectAsStateWithLifecycle(initialValue = emptyMap()) ?: remember { mutableStateOf(emptyMap()) }

    LaunchedEffect(categories) {
        if (viewModel != null && viewModel.selectedCategory == null && categories.isNotEmpty()) {
            viewModel.selectCategory(categories.first())
        }
    }

    val channelListState = rememberLazyListState()
    val epgGridState = rememberLazyListState()

    // Synced scrolling using snapshotFlow to prevent unnecessary recompositions
    LaunchedEffect(channelListState) {
        snapshotFlow { channelListState.firstVisibleItemIndex to channelListState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (channelListState.isScrollInProgress) {
                    epgGridState.scrollToItem(index, offset)
                }
            }
    }

    LaunchedEffect(epgGridState) {
        snapshotFlow { epgGridState.firstVisibleItemIndex to epgGridState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (epgGridState.isScrollInProgress) {
                    channelListState.scrollToItem(index, offset)
                }
            }
    }

    val currentLocalTime = remember { LocalTime.now() }
    val minutesSinceMidnight = currentLocalTime.hour * 60 + currentLocalTime.minute
    val currentTimeLineOffset = (minutesSinceMidnight * 4).dp

    val zoneId = remember { ZoneId.systemDefault() }
    val midnightEpochMillis = remember {
        LocalDate.now().atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    val horizontalScrollState = rememberScrollState()
    val density = LocalDensity.current

    LaunchedEffect(Unit) {
        val currentTimeLineOffsetPx = with(density) { currentTimeLineOffset.toPx().toInt() }
        horizontalScrollState.scrollTo(maxOf(0, currentTimeLineOffsetPx - 200))
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Category Selection Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E1E))
                    .padding(vertical = 8.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { viewModel?.selectCategory(null) },
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
                        selected = selectedCategory == category,
                        onClick = { viewModel?.loadEpgForCategory(category) },
                        label = { Text(category.categoryName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White,
                            labelColor = Color.Gray
                        )
                    )
                }
            }

            Row(modifier = Modifier.fillMaxSize()) {
                // Left 25% for Channel Names
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.25f)
                        .background(Color(0xFF1E1E1E))
                ) {
                    // Placeholder space to match TimeHeader height (48.dp)
                    Spacer(modifier = Modifier.height(48.dp))
                    if (categoryEpgData.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        LazyColumn(
                            state = channelListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(categoryEpgData.keys.toList(), key = { it.streamId }) { channel ->
                                ChannelNameItem(channel = channel) { clickedChannel ->
                                    val encodedUrl = URLEncoder.encode(clickedChannel.streamUrl, "UTF-8")
                                    val encodedTitle = URLEncoder.encode(clickedChannel.name, "UTF-8")
                                    val encodedPoster = URLEncoder.encode(clickedChannel.logoUrl ?: "", "UTF-8")
                                    navController.navigate("player?url=$encodedUrl&title=$encodedTitle&poster=$encodedPoster&id=${clickedChannel.streamId}&type=live")
                                }
                            }
                        }
                    }
                }

                // Right 75% for EPG Programs (synced horizontally)
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.75f)
                        .horizontalScroll(horizontalScrollState)
                ) {
                    // Time Header
                    TimeHeader()

                    // EPG Grid
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (viewModel == null) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No active playlist selected.", color = Color.White)
                            }
                        } else if (categoryEpgData.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No EPG Data for this category",
                                    color = Color.Gray,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        } else {
                            LazyColumn(
                                state = epgGridState,
                                modifier = Modifier.width(5760.dp).fillMaxHeight(),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(categoryEpgData.keys.toList(), key = { it.streamId }) { channel ->
                                    EpgProgramRow(
                                        channel = channel,
                                        programs = categoryEpgData[channel] ?: emptyList(),
                                        midnightEpochMillis = midnightEpochMillis
                                    ) { clickedChannel, _ ->
                                        val encodedUrl = URLEncoder.encode(clickedChannel.streamUrl, "UTF-8")
                                        val encodedTitle = URLEncoder.encode(clickedChannel.name, "UTF-8")
                                        val encodedPoster = URLEncoder.encode(clickedChannel.logoUrl ?: "", "UTF-8")
                                        navController.navigate("player?url=$encodedUrl&title=$encodedTitle&poster=$encodedPoster&id=${clickedChannel.streamId}&type=live")
                                    }
                                }
                            }
                        }
                        // Current Time Line
                        if (categoryEpgData.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(2.dp)
                                    .offset(x = currentTimeLineOffset)
                                    .background(Color.Red)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelNameItem(
    channel: LiveChannel,
    onClick: (LiveChannel) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp) // Fixed height for channel row
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable { onClick(channel) },
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
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = channel.name,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun EpgProgramRow(
    channel: LiveChannel,
    programs: List<EpgProgramEntity>,
    midnightEpochMillis: Long,
    onProgramClick: (LiveChannel, EpgProgramEntity) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .width(5760.dp)
            .height(60.dp) // Fixed height to match channel row
            .padding(vertical = 4.dp),
        userScrollEnabled = false
    ) {
        val todayEndMillis = midnightEpochMillis + 24 * 3600 * 1000L
        val filteredPrograms = programs.filter {
            it.endTime > midnightEpochMillis && it.startTime < todayEndMillis
        }.sortedBy { it.startTime }

        if (filteredPrograms.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .width(5760.dp)
                        .fillMaxHeight()
                        .padding(horizontal = 2.dp)
                        .background(Color(0xFF2C2C2C), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No EPG Data available for this channel",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            var currentXTimeMillis = midnightEpochMillis

            filteredPrograms.forEachIndexed { index, program ->
                if (program.startTime > currentXTimeMillis) {
                    val gapMinutes = (program.startTime - currentXTimeMillis) / 60000
                    if (gapMinutes > 0) {
                        item {
                            Spacer(modifier = Modifier.width((gapMinutes.toInt() * 4).dp))
                        }
                    }
                }

                val effectiveStart = maxOf(program.startTime, midnightEpochMillis)
                val effectiveEnd = minOf(program.endTime, todayEndMillis)
                val durationMinutes = ((effectiveEnd - effectiveStart) / 60000).coerceAtLeast(1L)
                val safeWidth = max(30, (durationMinutes.toInt() * 4)).dp

                item(key = "${program.id}-$index") {
                    EpgProgramBlock(
                        program = program,
                        widthDp = safeWidth
                    ) { clickedProgram ->
                        onProgramClick(channel, clickedProgram)
                    }
                }
                currentXTimeMillis = effectiveEnd
            }

            if (currentXTimeMillis < todayEndMillis) {
                val remainingMinutes = (todayEndMillis - currentXTimeMillis) / 60000
                if (remainingMinutes > 0) {
                    item {
                        Spacer(modifier = Modifier.width((remainingMinutes.toInt() * 4).dp))
                    }
                }
            }
        }
    }
}

@Composable
fun EpgProgramBlock(
    program: EpgProgramEntity,
    widthDp: androidx.compose.ui.unit.Dp,
    onClick: (EpgProgramEntity) -> Unit
) {
    val startLocalTime = Instant.ofEpochMilli(program.startTime).atZone(ZoneId.systemDefault()).toLocalTime()
    val endLocalTime = Instant.ofEpochMilli(program.endTime).atZone(ZoneId.systemDefault()).toLocalTime()

    Box(modifier = Modifier
        .width(widthDp)
        .fillMaxHeight()
        .padding(horizontal = 2.dp) // Small padding between blocks
        .border(0.5.dp, Color.Gray, RoundedCornerShape(4.dp))
        .clip(RoundedCornerShape(4.dp))
        .background(Color(0xFF3A3A3A))
        .clickable { onClick(program) },
        contentAlignment = Alignment.CenterStart
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                text = program.title,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${startLocalTime.format(DateTimeFormatter.ofPattern("HH:mm"))} - ${endLocalTime.format(DateTimeFormatter.ofPattern("HH:mm"))}",
                color = Color.LightGray,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TimeHeader() {
    val startTime = remember { LocalTime.MIDNIGHT } // Start from 00:00
    val halfHourBlocks = remember {
        generateSequence(startTime) { it.plusMinutes(30) }
            .take(48) // 24 hours * 2 half-hour blocks = 48 blocks
            .toList()
    }
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") } // Changed to HH:mm for 24-hour format

    LazyRow(
        modifier = Modifier
            .width(5760.dp)
            .height(48.dp) // Height for the time header
            .background(Color(0xFF2C2C2C)),
        userScrollEnabled = false
    ) {
        items(halfHourBlocks.size) { index ->
            val time = halfHourBlocks[index]
            Box(
                modifier = Modifier
                    .width(120.dp) // 30 minutes * 4.dp/minute = 120.dp
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = time.format(formatter),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
