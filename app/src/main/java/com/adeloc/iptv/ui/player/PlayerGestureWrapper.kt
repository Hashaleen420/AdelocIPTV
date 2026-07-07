package com.adeloc.iptv.ui.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlayerGestureWrapper(
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {},
    isControlsVisible: Boolean = false,
    isFullscreen: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val coroutineScope = rememberCoroutineScope()

    var overlayType by remember { mutableStateOf<GestureType?>(null) }
    var gestureValue by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var hideJob by remember { mutableStateOf<Job?>(null) }

    val animatedValue by animateFloatAsState(
        targetValue = gestureValue,
        label = "gestureValue",
        animationSpec = tween(150)
    )

    // Current levels for indicators when HUD is visible
    var currentVolume by remember { mutableFloatStateOf(0f) }
    var currentBrightness by remember { mutableFloatStateOf(0f) }

    fun syncSystemLevels() {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curr = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        currentVolume = if (max > 0) (curr.toFloat() / max.toFloat()).coerceIn(0f, 1f) else 0f
        
        val activity = context as? Activity
        val lp = activity?.window?.attributes
        currentBrightness = if (lp != null && lp.screenBrightness >= 0) lp.screenBrightness.coerceIn(0f, 1f) else 0.5f
    }

    LaunchedEffect(isControlsVisible) {
        if (isControlsVisible) syncSystemLevels()
    }

    fun updateOverlay(type: GestureType, value: Float) {
        overlayType = type
        gestureValue = value.coerceIn(0f, 1f)
        if (type == GestureType.VOLUME) currentVolume = gestureValue
        else currentBrightness = gestureValue
        
        isDragging = true
        hideJob?.cancel()
        hideJob = coroutineScope.launch {
            delay(1500)
            isDragging = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Gesture Detection Layer (Bottom, but above video surface if placed outside)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isFullscreen) {
                    if (!isFullscreen) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        
                        val x = down.position.x
                        val zone = when {
                            x < width * 0.15f -> GestureType.BRIGHTNESS
                            x > width * 0.85f -> GestureType.VOLUME
                            else -> null
                        }

                        var dragTriggered = false
                        val drag = awaitVerticalTouchSlopOrCancellation(down.id) { change, _ ->
                            if (zone != null) {
                                change.consume()
                                dragTriggered = true
                            }
                        }

                        if (dragTriggered && zone != null) {
                            syncSystemLevels()
                            var level = if (zone == GestureType.VOLUME) currentVolume else currentBrightness
                            updateOverlay(zone, level)

                            verticalDrag(down.id) { change ->
                                val deltaY = change.positionChange().y
                                change.consume()
                                level = (level - (deltaY / height)).coerceIn(0f, 1f)
                                
                                if (zone == GestureType.VOLUME) {
                                    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (level * max).toInt(), 0)
                                } else {
                                    val activity = context as? Activity
                                    val lp = activity?.window?.attributes
                                    if (lp != null) {
                                        lp.screenBrightness = level.coerceIn(0.01f, 1.0f)
                                        activity.window.attributes = lp
                                    }
                                }
                                updateOverlay(zone, level)
                            }
                        } else {
                            // If no drag meet slop, treat as tap
                            val up = waitForUpOrCancellation()
                            if (up != null) {
                                onTap()
                            }
                        }
                    }
                }
        )

        // 2. Main Content (Player buttons, etc.) - Top layer to ensure clickability
        content()

        // 3. Side indicators (Overlay)
        if (isFullscreen) {
            Box(modifier = Modifier.fillMaxSize()) {
                SideIndicator(
                    alignment = Alignment.CenterStart,
                    icon = Icons.Default.Brightness7,
                    value = if (isDragging && overlayType == GestureType.BRIGHTNESS) animatedValue else currentBrightness,
                    isVisible = isControlsVisible || (isDragging && overlayType == GestureType.BRIGHTNESS)
                )

                SideIndicator(
                    alignment = Alignment.CenterEnd,
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    value = if (isDragging && overlayType == GestureType.VOLUME) animatedValue else currentVolume,
                    isVisible = isControlsVisible || (isDragging && overlayType == GestureType.VOLUME)
                )
            }
        }
    }
}

@Composable
private fun BoxScope.SideIndicator(
    alignment: Alignment,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: Float,
    isVisible: Boolean
) {
    val isLeft = alignment == Alignment.CenterStart
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInHorizontally { if (isLeft) -it else it },
        exit = fadeOut() + slideOutHorizontally { if (isLeft) -it else it },
        modifier = Modifier.align(alignment)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .width(46.dp)
                .height(240.dp)
                .clip(RoundedCornerShape(23.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .weight(1f)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(value)
                        .align(Alignment.BottomCenter)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF00E5FF), Color(0xFF2979FF))
                            )
                        )
                )
            }
            Text(
                "${(value * 100).toInt()}%",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

enum class GestureType {
    VOLUME, BRIGHTNESS
}
