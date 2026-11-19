package com.cyrus.gallery.ui.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cyrus.gallery.domain.model.MediaItem
import com.cyrus.gallery.ui.components.EmptyLibraryState
import com.cyrus.gallery.ui.permissions.MediaPermissionGate

@Composable
fun PhotosScreen(
    onSettings: () -> Unit,
    onMediaClick: (MediaItem, List<MediaItem>) -> Unit = { _, _ -> },
    viewModel: GalleryViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val gridState = rememberLazyGridState()

    // Close search on phone back gesture / button press instead of quitting app
    BackHandler(enabled = state.isSearchActive) {
        viewModel.setSearchActive(false)
    }

    Column(Modifier.fillMaxSize()) {
        // Header
        if (state.isSearchActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = { Text("Search photos, videos, albums...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { viewModel.setSearchActive(false) }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Close search")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 20.dp, top = 18.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Cyrus Gallery", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Your photos, in one place",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Row {
                    IconButton(onClick = { viewModel.setSearchActive(true) }) {
                        Icon(Icons.Outlined.Search, "Search")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.MoreVert, "Settings")
                    }
                }
            }
        }

        // Permission gate wraps the grid
        MediaPermissionGate { hasPartialAccess ->
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@MediaPermissionGate
            }
            if (!state.hasMedia) {
                EmptyLibraryState(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = if (hasPartialAccess) "Limited access" else "No media found",
                    message = if (hasPartialAccess)
                        "You've granted access to selected photos only. Expand access in Settings to see your full library."
                    else
                        "No photos or videos were found on this device.",
                )
            } else if (state.groups.isEmpty() && state.searchQuery.isNotEmpty()) {
                EmptyLibraryState(
                    icon = Icons.Outlined.Search,
                    title = "No results found",
                    message = "No photos matching \"${state.searchQuery}\" were found.",
                )
            } else {
                MediaGrid(
                    groups = state.groups,
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    onMediaClick = onMediaClick,
                )
            }
        }
    }
}

