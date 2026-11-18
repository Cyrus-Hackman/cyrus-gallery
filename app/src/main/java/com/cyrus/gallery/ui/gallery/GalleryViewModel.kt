package com.cyrus.gallery.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyrus.gallery.domain.model.MediaGroup
import com.cyrus.gallery.domain.model.MediaItem
import com.cyrus.gallery.domain.model.groupByMonth
import com.cyrus.gallery.domain.repository.GalleryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class GalleryUiState(
    val groups: List<MediaGroup> = emptyList(),
    val isLoading: Boolean = true,
    val hasMedia: Boolean = false,
)

@HiltViewModel
class GalleryViewModel @Inject constructor(repository: GalleryRepository) : ViewModel() {
    val uiState: StateFlow<GalleryUiState> = repository.observeMedia()
        .map { items ->
            GalleryUiState(
                groups = items.groupByMonth(),
                isLoading = false,
                hasMedia = items.isNotEmpty(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GalleryUiState())
}

