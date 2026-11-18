package com.cyrus.gallery.ui.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyrus.gallery.domain.model.Album
import com.cyrus.gallery.domain.repository.GalleryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AlbumsUiState(
    val albums: List<Album> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class AlbumsViewModel @Inject constructor(repository: GalleryRepository) : ViewModel() {
    val uiState: StateFlow<AlbumsUiState> = repository.observeAlbums()
        .map { AlbumsUiState(albums = it, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlbumsUiState())
}
