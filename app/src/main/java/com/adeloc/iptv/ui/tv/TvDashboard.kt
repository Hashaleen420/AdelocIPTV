package com.adeloc.iptv.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.adeloc.iptv.data.local.entity.LiveChannel
import com.adeloc.iptv.data.local.entity.StreamCategory

@Composable
fun TvDashboard(
    categories: List<StreamCategory>,
    channels: List<LiveChannel>,
    selectedCategory: StreamCategory?,
    onCategoryClick: (StreamCategory) -> Unit,
    onChannelClick: (LiveChannel) -> Unit
) {
    Row(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {
        // Navigation Drawer (Left Side)
        Column(
            modifier = Modifier
                .width(250.dp)
                .fillMaxHeight()
                .background(Color(0xFF1A1A1A))
                .padding(16.dp)
        ) {
            Text(
                text = "Categories",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            categories.forEach { category ->
                NavigationItem(
                    title = category.categoryName,
                    isSelected = category.id == selectedCategory?.id,
                    onClick = { onCategoryClick(category) }
                )
            }
        }

        // Main Grid (Right Side)
        TvChannelGrid(
            channels = channels,
            onChannelClick = onChannelClick,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun NavigationItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .onFocusChanged { isFocused = it.isFocused },
        color = when {
            isSelected -> Color.White.copy(alpha = 0.2f)
            isFocused -> Color.White.copy(alpha = 0.1f)
            else -> Color.Transparent
        },
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isSelected || isFocused) Color.White else Color.Gray,
            modifier = Modifier.padding(12.dp)
        )
    }
}
