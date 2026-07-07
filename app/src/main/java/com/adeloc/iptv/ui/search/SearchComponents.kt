package com.adeloc.iptv.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.adeloc.iptv.data.local.entity.StreamEntity

@Composable
fun SearchToolbar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        placeholder = { Text("Search channels, movies...", color = Color.Gray) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = Color(0xFF1E1E1E),
            focusedContainerColor = Color(0xFF1E1E1E),
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = Color.Cyan
        ),
        singleLine = true
    )
}

@Composable
fun GlobalSearchResults(
    results: com.adeloc.iptv.data.repository.GlobalSearchResult,
    onResultClick: (StreamEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        if (results.channels.isNotEmpty()) {
            item { SearchHeader("Live TV") }
            items(results.channels) { SearchResultItem(it, onResultClick) }
        }
        
        if (results.movies.isNotEmpty()) {
            item { SearchHeader("Movies") }
            items(results.movies) { SearchResultItem(it, onResultClick) }
        }
        
        if (results.series.isNotEmpty()) {
            item { SearchHeader("Series") }
            items(results.series) { SearchResultItem(it, onResultClick) }
        }
    }
}

@Composable
private fun SearchHeader(title: String) {
    Text(
        text = title,
        color = Color.Cyan,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(16.dp)
    )
}

@Composable
private fun SearchResultItem(stream: StreamEntity, onClick: (StreamEntity) -> Unit) {
    ListItem(
        headlineContent = { Text(stream.name, color = Color.White) },
        supportingContent = { Text(stream.groupTitle ?: "", color = Color.Gray) },
        modifier = Modifier.clickable { onClick(stream) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
