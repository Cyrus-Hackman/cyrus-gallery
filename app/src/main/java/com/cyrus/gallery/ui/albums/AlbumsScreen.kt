package com.cyrus.gallery.ui.albums

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.cyrus.gallery.domain.model.Album
import com.cyrus.gallery.domain.model.MediaItem
import com.cyrus.gallery.domain.model.MediaSource
import com.cyrus.gallery.domain.model.collectDescendantFolderIds
import com.cyrus.gallery.domain.model.groupByMonth
import com.cyrus.gallery.ui.components.EmptyLibraryState
import com.cyrus.gallery.ui.gallery.GalleryViewModel
import com.cyrus.gallery.ui.gallery.MediaGrid
import com.cyrus.gallery.ui.navigation.GalleryDestination
import com.cyrus.gallery.ui.permissions.MediaPermissionGate
import com.cyrus.gallery.ui.viewer.ViewerScopeRoutes
import com.cyrus.gallery.ui.viewer.ViewerSessionConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter

@Composable
fun AlbumsScreen(
    onMediaClick: (MediaItem, List<MediaItem>) -> Unit = { _, _ -> },
    onOpenViewer: ((ViewerSessionConfig) -> Unit)? = null,
    tabReselected: Flow<String> = emptyFlow(),
    viewModel: AlbumsViewModel = hiltViewModel(),
    galleryViewModel: GalleryViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val galleryState = galleryViewModel.uiState.collectAsStateWithLifecycle().value
    val cloudMedia = viewModel.cloudMedia.collectAsStateWithLifecycle().value
    var selectedAlbum by rememberSaveable { mutableStateOf<String?>(null) }
    val currentAlbumObj = selectedAlbum?.let { id -> state.albums.find { it.id == id } }
    val albumsListGridState = rememberLazyGridState()

    LaunchedEffect(tabReselected) {
        tabReselected.filter { it == GalleryDestination.ALBUMS.route }.collect {
            if (selectedAlbum != null) {
                selectedAlbum = null
            } else {
                albumsListGridState.animateScrollToItem(0)
            }
        }
    }

    // Intercept phone's physical back button when inside an album
    BackHandler(enabled = selectedAlbum != null) {
        selectedAlbum = null
    }

    Column(Modifier.fillMaxSize()) {
        val currentAlbum = currentAlbumObj
        if (currentAlbum != null) {
            val albumGridState = rememberLazyGridState()
            // Album Detail Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 8.dp, top = 12.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { selectedAlbum = null }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to albums")
                }
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(currentAlbum.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${currentAlbum.itemCount} items",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Filter media cleanly per album source so OneDrive folders aren't stripped by local name deduplication
            val albumMedia = remember(currentAlbum, galleryState.rawMedia, cloudMedia, state.albums) {
                if (currentAlbum.source == MediaSource.ONEDRIVE) {
                    val descendantIds = collectDescendantFolderIds(currentAlbum.id, state.albums)
                    cloudMedia.filter { it.albumId in descendantIds }
                } else {
                    galleryState.rawMedia.filter {
                        it.source == MediaSource.LOCAL &&
                            (it.albumId == currentAlbum.id || it.albumName == currentAlbum.name)
                    }
                }
            }
            val groupedAlbumMedia = remember(albumMedia) { albumMedia.groupByMonth() }

            if (albumMedia.isEmpty()) {
                EmptyLibraryState(
                    icon = Icons.Outlined.Collections,
                    title = "Album is empty",
                    message = "No photos or videos found in this album.",
                )
            } else {
                MediaGrid(
                    groups = groupedAlbumMedia,
                    state = albumGridState,
                    modifier = Modifier.fillMaxSize(),
                    onMediaClick = { clicked, all ->
                        if (onOpenViewer != null) {
                            onOpenViewer(
                                ViewerSessionConfig(
                                    sourceRoute = ViewerScopeRoutes.ALBUMS,
                                    selectedItemId = clicked.id,
                                    albumId = currentAlbum.id,
                                    albumName = currentAlbum.name,
                                    initialItems = all,
                                ),
                            )
                        } else {
                            onMediaClick(clicked, all)
                        }
                    },
                )
            }
        } else {
            // Main Albums Grid Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 12.dp),
            ) {
                Text("Albums", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Device collections and cloud folders",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            MediaPermissionGate(allowCloudWhenDenied = state.albums.isNotEmpty()) { hasPartialAccess ->
                if (state.isLoading) return@MediaPermissionGate
                if (state.albums.isEmpty()) {
                    EmptyLibraryState(
                        icon = Icons.Outlined.Collections,
                        title = if (hasPartialAccess) "Limited album access" else "No albums found",
                        message = if (hasPartialAccess)
                            "Only selected photos are accessible. Grant full access to organize your albums."
                        else
                            "Albums and device folders will appear here once media is found.",
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = albumsListGridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(state.albums, key = { it.id }) { album ->
                            AlbumCard(album = album, onClick = { selectedAlbum = album.id })
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AlbumCard(album: Album, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (album.coverUri != null || album.coverThumbnailUrl != null) {
                    AsyncImage(
                        model = album.coverUri ?: album.coverThumbnailUrl,
                        contentDescription = album.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = album.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${album.itemCount} ${if (album.itemCount == 1) "item" else "items"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
