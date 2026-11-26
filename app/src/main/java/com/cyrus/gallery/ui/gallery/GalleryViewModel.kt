package com.cyrus.gallery.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyrus.gallery.domain.model.MediaGroup
import com.cyrus.gallery.domain.model.MediaItem
import com.cyrus.gallery.domain.model.groupByMonth
import com.cyrus.gallery.domain.repository.GalleryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class GalleryUiState(
    val groups: List<MediaGroup> = emptyList(),
    val rawMedia: List<MediaItem> = emptyList(),
    val isLoading: Boolean = true,
    val hasMedia: Boolean = false,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
)

@HiltViewModel
class GalleryViewModel @Inject constructor(repository: GalleryRepository) : ViewModel() {

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val uiState: StateFlow<GalleryUiState> = combine(
        repository.observeMedia(),
        _isSearchActive,
        _searchQuery,
    ) { items, isSearch, query ->
        val filtered = if (isSearch && query.isNotBlank()) {
            items.filter { it.matchesQuery(query) }
        } else {
            items
        }

        GalleryUiState(
            groups = filtered.groupByMonth(),
            rawMedia = items,
            isLoading = false,
            hasMedia = items.isNotEmpty(),
            isSearchActive = isSearch,
            searchQuery = query,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GalleryUiState())

    fun setSearchActive(active: Boolean) {
        _isSearchActive.value = active
        if (!active) _searchQuery.value = ""
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }
}

