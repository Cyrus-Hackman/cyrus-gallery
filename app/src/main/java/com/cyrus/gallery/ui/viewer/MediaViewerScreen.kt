package com.cyrus.gallery.ui.viewer

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.cyrus.gallery.domain.model.CloudStorageState
import com.cyrus.gallery.domain.model.MediaItem
import com.cyrus.gallery.domain.model.MediaSource
import com.cyrus.gallery.domain.model.MediaType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    item: MediaItem,
    onBack: () -> Unit,
    viewModel: MediaViewerViewModel = viewModel(),
) {
    val context = LocalContext.current
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

    // Zoom & pan state for images
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Media Content (Image or Video)
        if (item.type == MediaType.VIDEO) {
            val mediaUri = item.localContentUri
            if (mediaUri != null) {
                val exoPlayer = remember(mediaUri) {
                    ExoPlayer.Builder(context).build().apply {
                        setMediaItem(ExoMediaItem.fromUri(mediaUri))
                        prepare()
                        playWhenReady = true
                    }
                }

                DisposableEffect(exoPlayer) {
                    onDispose { exoPlayer.release() }
                }

                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // Cloud video thumbnail preview if not downloaded
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            // Zoomable Image
            AsyncImage(
                model = item.localContentUri ?: item.thumbnailUrl,
                contentDescription = item.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { controlsVisible = !controlsVisible },
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                } else {
                                    scale = 2.5f
                                }
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY,
                    ),
            )
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
                    Text(item.displayName, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1)
                    Text(
                        if (item.source == MediaSource.LOCAL) "Local" else "OneDrive",
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
                IconButton(onClick = { viewModel.toggleFavorite(item) }) {
                    Icon(
                        if (item.isFavourite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (item.isFavourite) Color.Red else Color.White,
                    )
                }

                // Cloud actions
                if (item.source == MediaSource.ONEDRIVE) {
                    val isPinned = item.cloudStorageState == CloudStorageState.PINNED_OFFLINE
                    IconButton(onClick = { viewModel.togglePinned(item) }) {
                        Icon(
                            Icons.Outlined.PushPin,
                            contentDescription = "Pin Offline",
                            tint = if (isPinned) MaterialTheme.colorScheme.primary else Color.White,
                        )
                    }
                    if (item.cloudStorageState == CloudStorageState.REMOTE_ONLY) {
                        IconButton(onClick = { viewModel.downloadOnDemand(item) }) {
                            Icon(Icons.Outlined.CloudDownload, contentDescription = "Download", tint = Color.White)
                        }
                    }
                } else {
                    // Local media: upload to OneDrive
                    IconButton(onClick = { viewModel.uploadToOneDrive(item) }) {
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
            item = item,
            sheetState = sheetState,
            onDismiss = { showMetadataSheet = false },
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        MediaDeleteDialog(
            item = item,
            onConfirm = { target ->
                showDeleteDialog = false
                viewModel.deleteMedia(item, target) {
                    onBack()
                }
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}
