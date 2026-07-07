package com.adeloc.iptv.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adeloc.iptv.data.repository.GlobalSearchResult
import com.adeloc.iptv.data.repository.SearchRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchViewModel(
    private val searchRepository: SearchRepository,
    private val playlistId: Int
) : ViewModel() {

    var searchQuery by mutableStateOf("")
        private set

    var searchResult by mutableStateOf(GlobalSearchResult(emptyList(), emptyList(), emptyList()))
        private set

    private var searchJob: Job? = null

    fun onSearchQueryChange(newQuery: String) {
        searchQuery = newQuery
        
        // Debouncing logic
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (newQuery.length >= 2) {
                delay(300) // 300ms debounce
                searchResult = searchRepository.searchAll(playlistId, newQuery)
            } else {
                searchResult = GlobalSearchResult(emptyList(), emptyList(), emptyList())
            }
        }
    }
}
