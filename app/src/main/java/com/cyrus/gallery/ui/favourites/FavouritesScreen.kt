package com.cyrus.gallery.ui.favourites

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cyrus.gallery.domain.model.MediaItem
import com.cyrus.gallery.domain.model.groupByMonth
import com.cyrus.gallery.ui.components.EmptyLibraryState
import com.cyrus.gallery.ui.gallery.GalleryViewModel
import com.cyrus.gallery.ui.gallery.MediaGrid
import com.cyrus.gallery.ui.navigation.GalleryDestination
import com.cyrus.gallery.ui.viewer.ViewerScopeRoutes
import com.cyrus.gallery.ui.viewer.ViewerSessionConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter

@Composable
fun FavouritesScreen(
    onMediaClick: (MediaItem, List<MediaItem>) -> Unit = { _, _ -> },
    onOpenViewer: ((ViewerSessionConfig) -> Unit)? = null,
    tabReselected: Flow<String> = emptyFlow(),
    viewModel: GalleryViewModel = hiltViewModel(),
) {
    val favoriteItems = viewModel.favoriteMedia.collectAsStateWithLifecycle().value
    val gridState = rememberLazyGridState()

    LaunchedEffect(tabReselected, gridState) {
        tabReselected.filter { it == GalleryDestination.FAVOURITES.route }.collect {
            gridState.animateScrollToItem(0)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 12.dp),
        ) {
            Text("Favorites", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Starred photos and videos",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (favoriteItems.isEmpty()) {
            EmptyLibraryState(
                icon = Icons.Outlined.FavoriteBorder,
                title = "No favorites yet",
                message = "Tap the heart on any photo or video to add it to your favorites. They will be saved here offline.",
            )
        } else {
            MediaGrid(
                groups = favoriteItems.groupByMonth(),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                onMediaClick = { clicked, all ->
                    if (onOpenViewer != null) {
                        onOpenViewer(
                            ViewerSessionConfig(
                                sourceRoute = ViewerScopeRoutes.FAVOURITES,
                                selectedItemId = clicked.id,
                                initialItems = all,
                            ),
                        )
                    } else {
                        onMediaClick(clicked, all)
                    }
                },
            )
        }
    }
}
