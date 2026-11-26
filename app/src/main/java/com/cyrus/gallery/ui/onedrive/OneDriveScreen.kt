package com.cyrus.gallery.ui.onedrive

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cyrus.gallery.data.auth.AuthState
import com.cyrus.gallery.domain.model.MediaItem
import com.cyrus.gallery.domain.model.groupByMonth
import com.cyrus.gallery.ui.components.EmptyLibraryState
import com.cyrus.gallery.ui.gallery.MediaGrid
import com.cyrus.gallery.ui.navigation.GalleryDestination
import com.cyrus.gallery.ui.viewer.ViewerScopeRoutes
import com.cyrus.gallery.ui.viewer.ViewerSessionConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter

@Composable
fun OneDriveScreen(
    onSettings: () -> Unit = {},
    onMediaClick: (MediaItem, List<MediaItem>) -> Unit = { _, _ -> },
    onOpenViewer: ((ViewerSessionConfig) -> Unit)? = null,
    tabReselected: Flow<String> = emptyFlow(),
    viewModel: OneDriveViewModel = hiltViewModel(),
) {
    val authState = viewModel.authState.collectAsStateWithLifecycle().value
    val cloudMedia = viewModel.cloudMedia.collectAsStateWithLifecycle().value
    val displayedMedia = viewModel.displayedMedia.collectAsStateWithLifecycle().value
    val isHydrating = viewModel.isHydrating.collectAsStateWithLifecycle().value
    val isSyncing = viewModel.isSyncing.collectAsStateWithLifecycle().value
    val statusMessage = viewModel.statusMessage.collectAsStateWithLifecycle().value
    val isSearchActive = viewModel.isSearchActive.collectAsStateWithLifecycle().value
    val searchQuery = viewModel.searchQuery.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val activity = context as? Activity
    val gridState = rememberLazyGridState()

    BackHandler(enabled = isSearchActive) {
        viewModel.setSearchActive(false)
    }

    LaunchedEffect(tabReselected, gridState) {
        tabReselected.filter { it == GalleryDestination.ONEDRIVE.route }.collect {
            gridState.animateScrollToItem(0)
        }
    }

    // Auto-dismiss sync status message after 3 seconds
    LaunchedEffect(statusMessage, isSyncing) {
        if (!statusMessage.isNullOrEmpty() && !isSyncing) {
            delay(3_000)
            viewModel.clearStatusMessage()
        }
    }

    val handleMediaClick: (MediaItem, List<MediaItem>) -> Unit = { clicked, all ->
        if (onOpenViewer != null) {
            onOpenViewer(
                ViewerSessionConfig(
                    sourceRoute = ViewerScopeRoutes.ONEDRIVE,
                    selectedItemId = clicked.id,
                    searchQuery = if (isSearchActive) searchQuery else "",
                    initialItems = all,
                ),
            )
        } else {
            onMediaClick(clicked, all)
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Screen Header
        if (isSearchActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = { Text("Search OneDrive photos, videos...") },
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
                    .padding(start = 20.dp, top = 18.dp, end = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("OneDrive", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Cloud media and albums",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (cloudMedia.isNotEmpty() || authState is AuthState.SignedIn || authState is AuthState.Offline) {
                        IconButton(onClick = { viewModel.setSearchActive(true) }) {
                            Icon(Icons.Outlined.Search, contentDescription = "Search OneDrive")
                        }
                    }
                    if (authState is AuthState.SignedIn) {
                        OutlinedButton(
                            onClick = { viewModel.sync() },
                            enabled = !isSyncing,
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Outlined.Sync, contentDescription = "Sync", modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(if (isSyncing) "Syncing" else "Sync")
                        }
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "Settings")
                    }
                }
            }
        }

        when (authState) {
            is AuthState.Authenticating -> {
                if (cloudMedia.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text("Connecting to Microsoft OneDrive...", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                } else {
                    // Keep showing cached Room media non-blockingly while MSAL connects silently
                    CloudStatusBanner(
                        title = "Connecting to OneDrive...",
                        subtitle = "${cloudMedia.size} cached items available",
                        isLoading = true,
                    )
                    OneDriveMediaBody(
                        cloudMedia = cloudMedia,
                        displayedMedia = displayedMedia,
                        searchQuery = searchQuery,
                        isHydrating = isHydrating,
                        isOffline = false,
                        gridState = gridState,
                        onMediaClick = handleMediaClick,
                    )
                }
            }

            is AuthState.Offline -> {
                CloudStatusBanner(
                    title = "${authState.displayName} · Offline",
                    subtitle = statusMessage ?: "${cloudMedia.size} cached items available on device",
                    actionLabel = "Reconnect",
                    onAction = { viewModel.retryConnection() },
                    secondaryActionLabel = "Sign out",
                    onSecondaryAction = { viewModel.signOut() },
                )
                OneDriveMediaBody(
                    cloudMedia = cloudMedia,
                    displayedMedia = displayedMedia,
                    searchQuery = searchQuery,
                    isHydrating = isHydrating,
                    isOffline = true,
                    gridState = gridState,
                    onMediaClick = handleMediaClick,
                )
            }

            is AuthState.SignedOut -> {
                if (cloudMedia.isEmpty()) {
                    SignedOutContent(
                        errorMessage = authState.message,
                        onSignIn = { activity?.let { viewModel.signIn(it) } },
                    )
                } else {
                    CloudStatusBanner(
                        title = "Sign in to sync OneDrive",
                        subtitle = authState.message ?: "${cloudMedia.size} cached items available on device",
                        actionLabel = "Sign in",
                        onAction = { activity?.let { viewModel.signIn(it) } },
                        isError = !authState.message.isNullOrEmpty(),
                    )
                    OneDriveMediaBody(
                        cloudMedia = cloudMedia,
                        displayedMedia = displayedMedia,
                        searchQuery = searchQuery,
                        isHydrating = isHydrating,
                        isOffline = true,
                        gridState = gridState,
                        onMediaClick = handleMediaClick,
                    )
                }
            }

            is AuthState.SignedIn -> {
                CloudStatusBanner(
                    title = authState.displayName,
                    subtitle = if (isSyncing) "Syncing..." else "${cloudMedia.size} items in cloud",
                    statusNote = statusMessage?.takeIf { it != "Signed in successfully" },
                    secondaryActionLabel = "Sign out",
                    onSecondaryAction = { viewModel.signOut() },
                )
                OneDriveMediaBody(
                    cloudMedia = cloudMedia,
                    displayedMedia = displayedMedia,
                    searchQuery = searchQuery,
                    isHydrating = isHydrating,
                    isOffline = false,
                    gridState = gridState,
                    onMediaClick = handleMediaClick,
                )
            }
        }
    }
}

@Composable
private fun OneDriveMediaBody(
    cloudMedia: List<MediaItem>,
    displayedMedia: List<MediaItem>,
    searchQuery: String,
    isHydrating: Boolean,
    isOffline: Boolean,
    gridState: LazyGridState,
    onMediaClick: (MediaItem, List<MediaItem>) -> Unit,
) {
    when {
        isHydrating && cloudMedia.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        cloudMedia.isEmpty() -> {
            EmptyLibraryState(
                icon = if (isOffline) Icons.Outlined.CloudOff else Icons.Outlined.CloudDone,
                title = if (isOffline) "You are offline" else "OneDrive connected",
                message = if (isOffline)
                    "No cached cloud photos are stored on this device yet. Reconnect to sync your OneDrive library."
                else
                    "No photos or videos discovered yet. Tap 'Sync' above to discover your cloud media.",
            )
        }
        displayedMedia.isEmpty() && searchQuery.isNotEmpty() -> {
            EmptyLibraryState(
                icon = Icons.Outlined.Search,
                title = "No results found",
                message = "No cloud photos matching \"$searchQuery\" were found.",
            )
        }
        else -> {
            MediaGrid(
                groups = displayedMedia.groupByMonth(),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                onMediaClick = onMediaClick,
            )
        }
    }
}

@Composable
private fun CloudStatusBanner(
    title: String,
    subtitle: String,
    statusNote: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    isLoading: Boolean = false,
    isError: Boolean = false,
) {
    val containerColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!statusNote.isNullOrEmpty()) {
                    Text(
                        statusNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (actionLabel != null && onAction != null) {
                    TextButton(onClick = onAction) {
                        Text(actionLabel, style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (secondaryActionLabel != null && onSecondaryAction != null) {
                    OutlinedButton(onClick = onSecondaryAction) {
                        Text(secondaryActionLabel, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SignedOutContent(
    errorMessage: String?,
    onSignIn: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.CloudQueue,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "Connect Microsoft OneDrive",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Browse and sync your personal cloud photos alongside your device library with Microsoft Entra single-sign-on.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (!errorMessage.isNullOrEmpty()) {
            Spacer(Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onSignIn,
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Sign in with Microsoft")
        }

        Spacer(Modifier.height(24.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Requires Microsoft Entra App Registration. See docs/entra-app-registration.md for setup instructions and client ID configuration.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
