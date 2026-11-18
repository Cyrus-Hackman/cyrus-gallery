package com.cyrus.gallery.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.cyrus.gallery.domain.model.CloudStorageState
import com.cyrus.gallery.domain.model.MediaGroup
import com.cyrus.gallery.domain.model.MediaItem
import com.cyrus.gallery.domain.model.MediaSource
import com.cyrus.gallery.domain.model.MediaType

@Composable
fun MediaGrid(
    groups: List<MediaGroup>,
    modifier: Modifier = Modifier,
    onMediaClick: ((MediaItem, List<MediaItem>) -> Unit)? = null,
) {
    val allItems = remember(groups) { groups.flatMap { it.items } }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 112.dp),
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        groups.forEach { group ->
            // Month header — full-width span
            item(key = "header:${group.label}", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = group.label,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 20.dp, bottom = 6.dp, end = 16.dp),
                )
            }

            // Media thumbnails
            items(group.items, key = { it.id }) { item ->
                MediaThumbnail(item = item, onClick = { onMediaClick?.invoke(item, allItems) })
            }
        }
    }
}

@Composable
private fun MediaThumbnail(item: MediaItem, onClick: () -> Unit) {
    val context = LocalContext.current
    val imageRequest = remember(item.id, item.localContentUri, item.thumbnailUrl) {
        ImageRequest.Builder(context)
            .data(item.localContentUri ?: item.thumbnailUrl)
            .crossfade(true)
            .size(360, 360)
            .apply {
                if (item.localContentUri != null && item.type == MediaType.VIDEO) {
                    videoFrameMillis(1000L)
                }
            }
            .build()
    }

    Box(
        modifier = Modifier
            .padding(1.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )

        // Video overlay badge
        if (item.type == MediaType.VIDEO) {
            Icon(
                imageVector = Icons.Outlined.PlayCircle,
                contentDescription = "Video",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp),
            )
        }

        // Cloud indicator badge
        if (item.source == MediaSource.ONEDRIVE && item.cloudStorageState == CloudStorageState.REMOTE_ONLY) {
            Icon(
                imageVector = Icons.Outlined.CloudQueue,
                contentDescription = "Cloud only",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
            )
        }

        // Favorite heart badge
        if (item.isFavourite) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = "Favorite",
                tint = androidx.compose.ui.graphics.Color.Red,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
            )
        }
    }
}
