package com.cyrus.gallery.ui.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cyrus.gallery.domain.model.CloudStorageState
import com.cyrus.gallery.domain.model.MediaItem
import com.cyrus.gallery.domain.model.MediaSource
import com.cyrus.gallery.domain.model.MediaType

@Composable
fun MediaViewerScreen(
    item: MediaItem,
    onBack: () -> Unit,
    viewModel: MediaViewerViewModel = hiltViewModel(),
) {
    MediaViewerScreen(
        items = listOf(item),
        initialIndex = 0,
        onBack = onBack,
        viewModel = viewModel,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    items: List<MediaItem>,
    initialIndex: Int = 0,
    onBack: () -> Unit,
    viewModel: MediaViewerViewModel = hiltViewModel(),
) {
    if (items.isEmpty()) return

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, items.size - 1),
        pageCount = { items.size },
    )
    val currentItem = items.getOrNull(pagerState.currentPage) ?: return

    var controlsVisible by remember { mutableStateOf(true) }
    var showMetadataSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val snackbarHostState = remember { SnackbarHostState() }

    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    // Track active page zoom so horizontal pager gestures don't conflict with zoom/pan
    var currentScale by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(pagerState.currentPage) {
        currentScale = 1f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Horizontal Pager for swiping left & right
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = currentScale <= 1.05f,
        ) { page ->
            val pageItem = items[page]
            val isCurrentPage = page == pagerState.currentPage

            if (pageItem.type == MediaType.VIDEO) {
                VideoPage(
                    item = pageItem,
                    isCurrentPage = isCurrentPage,
                    onToggleControls = { controlsVisible = !controlsVisible },
                )
            } else {
                ZoomableImagePage(
                    item = pageItem,
                    isCurrentPage = isCurrentPage,
                    onToggleControls = { controlsVisible = !controlsVisible },
                    onScaleChanged = { scale ->
                        if (isCurrentPage) {
                            currentScale = scale
                        }
                    },
                )
            }
        }

        // Top Overlay Bar
        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentItem.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                    )
                    Text(
                        text = "${pagerState.currentPage + 1} of ${items.size} • ${if (currentItem.source == MediaSource.LOCAL) "Local" else "OneDrive"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray,
                    )
                }
                IconButton(onClick = { showMetadataSheet = true }) {
                    Icon(Icons.Outlined.Info, contentDescription = "Details", tint = Color.White)
                }
            }
        }

        // Bottom Action Bar
        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Favorite Toggle
                IconButton(onClick = { viewModel.toggleFavorite(currentItem) }) {
                    Icon(
                        if (currentItem.isFavourite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (currentItem.isFavourite) Color.Red else Color.White,
                    )
                }

                // Cloud actions
                if (currentItem.source == MediaSource.ONEDRIVE) {
                    val isPinned = currentItem.cloudStorageState == CloudStorageState.PINNED_OFFLINE
                    IconButton(onClick = { viewModel.togglePinned(currentItem) }) {
                        Icon(
                            Icons.Outlined.PushPin,
                            contentDescription = "Pin Offline",
                            tint = if (isPinned) MaterialTheme.colorScheme.primary else Color.White,
                        )
                    }
                    if (currentItem.cloudStorageState == CloudStorageState.REMOTE_ONLY) {
                        IconButton(onClick = { viewModel.downloadOnDemand(currentItem) }) {
                            Icon(Icons.Outlined.CloudDownload, contentDescription = "Download", tint = Color.White)
                        }
                    }
                } else {
                    // Local media: upload to OneDrive
                    IconButton(onClick = { viewModel.uploadToOneDrive(currentItem) }) {
                        Icon(Icons.Outlined.CloudUpload, contentDescription = "Upload to OneDrive", tint = Color.White)
                    }
                }

                // Delete Action
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = Color.White)
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp),
        )
    }

    // Metadata Bottom Sheet
    if (showMetadataSheet) {
        MediaMetadataSheet(
            item = currentItem,
            sheetState = sheetState,
            onDismiss = { showMetadataSheet = false },
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        MediaDeleteDialog(
            item = currentItem,
            onConfirm = { target ->
                showDeleteDialog = false
                viewModel.deleteMedia(currentItem, target) {
                    onBack()
                }
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

@Composable
private fun ZoomableImagePage(
    item: MediaItem,
    isCurrentPage: Boolean,
    onToggleControls: () -> Unit,
    onScaleChanged: (Float) -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            scale = 1f
            offsetX = 0f
            offsetY = 0f
            onScaleChanged(1f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onToggleControls() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = 2.5f
                        }
                        onScaleChanged(scale)
                    },
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    onScaleChanged(scale)
                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = item.localContentUri ?: item.highResUrl ?: item.thumbnailUrl,
            contentDescription = item.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY,
                ),
        )
    }
}

@Composable
private fun VideoPage(
    item: MediaItem,
    isCurrentPage: Boolean,
    onToggleControls: () -> Unit,
) {
    val context = LocalContext.current
    val mediaUri = item.localContentUri

    if (mediaUri != null) {
        val exoPlayer = remember(mediaUri) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(ExoMediaItem.fromUri(mediaUri))
                prepare()
                playWhenReady = isCurrentPage
            }
        }

        LaunchedEffect(isCurrentPage) {
            exoPlayer.playWhenReady = isCurrentPage
        }

        DisposableEffect(exoPlayer) {
            onDispose { exoPlayer.release() }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onToggleControls() })
                },
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    } else {
        // Cloud video preview if not downloaded
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onToggleControls() })
                },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = item.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            Icon(
                imageVector = Icons.Outlined.CloudDownload,
                contentDescription = "Cloud Video",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(64.dp),
            )
        }
    }
}
