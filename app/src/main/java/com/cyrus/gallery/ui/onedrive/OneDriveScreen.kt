package com.cyrus.gallery.ui.onedrive

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cyrus.gallery.data.auth.AuthState
import com.cyrus.gallery.domain.model.Album
import com.cyrus.gallery.domain.model.MediaItem
import com.cyrus.gallery.domain.model.collectDescendantFolderIds
import com.cyrus.gallery.domain.model.groupByMonth
import com.cyrus.gallery.ui.albums.AlbumCard
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
    val cloudFolders = viewModel.cloudFolders.collectAsStateWithLifecycle().value
    val displayedMedia = viewModel.displayedMedia.collectAsStateWithLifecycle().value
    val isHydrating = viewModel.isHydrating.collectAsStateWithLifecycle().value
    val isSyncing = viewModel.isSyncing.collectAsStateWithLifecycle().value
    val statusMessage = viewModel.statusMessage.collectAsStateWithLifecycle().value
    val isSearchActive = viewModel.isSearchActive.collectAsStateWithLifecycle().value
    val searchQuery = viewModel.searchQuery.collectAsStateWithLifecycle().value
    val browseMode = viewModel.browseMode.collectAsStateWithLifecycle().value
    val folderStack = viewModel.folderStack.collectAsStateWithLifecycle().value
    val latestBrowseMode by rememberUpdatedState(browseMode)
    val latestFolderStack by rememberUpdatedState(folderStack)
    val latestIsSearchActive by rememberUpdatedState(isSearchActive)
    val context = LocalContext.current
    val activity = context as? Activity
    val gridState = rememberLazyGridState()
    val foldersGridState = rememberLazyGridState()
    var showOverflowMenu by remember { mutableStateOf(false) }

    val currentFolderId = folderStack.lastOrNull()
    val currentFolder = remember(currentFolderId, cloudFolders) {
        currentFolderId?.let { id -> cloudFolders.find { it.id == id } }
    }

    // Pop search first, otherwise navigate up nested OneDrive folders before leaving the OneDrive tab
    BackHandler(enabled = !isSearchActive && browseMode == OneDriveBrowseMode.FOLDERS && folderStack.isNotEmpty()) {
        viewModel.navigateUpFolder()
    }
    BackHandler(enabled = isSearchActive) {
        viewModel.setSearchActive(false)
    }

    LaunchedEffect(tabReselected, gridState, foldersGridState) {
        tabReselected.filter { it == GalleryDestination.ONEDRIVE.route }.collect {
            if (!latestIsSearchActive && latestFolderStack.isNotEmpty()) {
                viewModel.clearFolderNavigation()
            } else if (!latestIsSearchActive && latestBrowseMode == OneDriveBrowseMode.FOLDERS) {
                foldersGridState.animateScrollToItem(0)
            } else {
                gridState.animateScrollToItem(0)
            }
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
            val isFolderMode = !isSearchActive && browseMode == OneDriveBrowseMode.FOLDERS
            val isFolderRootBrowse = isFolderMode && currentFolderId == null
            val activeAlbumFilter = if (isFolderMode) currentFolderId else null
            onOpenViewer(
                ViewerSessionConfig(
                    sourceRoute = ViewerScopeRoutes.ONEDRIVE,
                    selectedItemId = clicked.id,
                    searchQuery = if (isSearchActive) searchQuery else "",
                    albumId = activeAlbumFilter,
                    rootFolderOnly = isFolderRootBrowse,
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
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                        ) {
                            if (authState is AuthState.SignedIn) {
                                DropdownMenuItem(
                                    text = { Text("Full re-sync library") },
                                    enabled = !isSyncing,
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.sync(forceFullSync = true)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = {
                                    showOverflowMenu = false
                                    onSettings()
                                },
                            )
                        }
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
                    OneDriveMediaSection(
                        cloudMedia = cloudMedia,
                        displayedMedia = displayedMedia,
                        cloudFolders = cloudFolders,
                        browseMode = browseMode,
                        isSearchActive = isSearchActive,
                        searchQuery = searchQuery,
                        currentFolder = currentFolder,
                        isHydrating = isHydrating,
                        isOffline = false,
                        gridState = gridState,
                        foldersGridState = foldersGridState,
                        onSelectMode = { viewModel.setBrowseMode(it) },
                        onOpenFolder = { viewModel.openFolder(it) },
                        onNavigateUp = { viewModel.navigateUpFolder() },
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
                OneDriveMediaSection(
                    cloudMedia = cloudMedia,
                    displayedMedia = displayedMedia,
                    cloudFolders = cloudFolders,
                    browseMode = browseMode,
                    isSearchActive = isSearchActive,
                    searchQuery = searchQuery,
                    currentFolder = currentFolder,
                    isHydrating = isHydrating,
                    isOffline = true,
                    gridState = gridState,
                    foldersGridState = foldersGridState,
                    onSelectMode = { viewModel.setBrowseMode(it) },
                    onOpenFolder = { viewModel.openFolder(it) },
                    onNavigateUp = { viewModel.navigateUpFolder() },
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
                    OneDriveMediaSection(
                        cloudMedia = cloudMedia,
                        displayedMedia = displayedMedia,
                        cloudFolders = cloudFolders,
                        browseMode = browseMode,
                        isSearchActive = isSearchActive,
                        searchQuery = searchQuery,
                        currentFolder = currentFolder,
                        isHydrating = isHydrating,
                        isOffline = true,
                        gridState = gridState,
                        foldersGridState = foldersGridState,
                        onSelectMode = { viewModel.setBrowseMode(it) },
                        onOpenFolder = { viewModel.openFolder(it) },
                        onNavigateUp = { viewModel.navigateUpFolder() },
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
                OneDriveMediaSection(
                    cloudMedia = cloudMedia,
                    displayedMedia = displayedMedia,
                    cloudFolders = cloudFolders,
                    browseMode = browseMode,
                    isSearchActive = isSearchActive,
                    searchQuery = searchQuery,
                    currentFolder = currentFolder,
                    isHydrating = isHydrating,
                    isOffline = false,
                    gridState = gridState,
                    foldersGridState = foldersGridState,
                    onSelectMode = { viewModel.setBrowseMode(it) },
                    onOpenFolder = { viewModel.openFolder(it) },
                    onNavigateUp = { viewModel.navigateUpFolder() },
                    onMediaClick = handleMediaClick,
                )
            }
        }
    }
}

@Composable
private fun OneDriveMediaSection(
    cloudMedia: List<MediaItem>,
    displayedMedia: List<MediaItem>,
    cloudFolders: List<Album>,
    browseMode: OneDriveBrowseMode,
    isSearchActive: Boolean,
    searchQuery: String,
    currentFolder: Album?,
    isHydrating: Boolean,
    isOffline: Boolean,
    gridState: LazyGridState,
    foldersGridState: LazyGridState,
    onSelectMode: (OneDriveBrowseMode) -> Unit,
    onOpenFolder: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onMediaClick: (MediaItem, List<MediaItem>) -> Unit,
) {
    if (!isSearchActive && (cloudFolders.isNotEmpty() || cloudMedia.isNotEmpty())) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = browseMode == OneDriveBrowseMode.ALL_MEDIA,
                onClick = { onSelectMode(OneDriveBrowseMode.ALL_MEDIA) },
                label = { Text("All Photos") },
            )
            FilterChip(
                selected = browseMode == OneDriveBrowseMode.FOLDERS,
                onClick = { onSelectMode(OneDriveBrowseMode.FOLDERS) },
                label = { Text("Folders (${cloudFolders.size})") },
            )
        }
    }

    when {
        isHydrating && cloudMedia.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        cloudMedia.isEmpty() && cloudFolders.isEmpty() -> {
            EmptyLibraryState(
                icon = if (isOffline) Icons.Outlined.CloudOff else Icons.Outlined.CloudDone,
                title = if (isOffline) "You are offline" else "OneDrive connected",
                message = if (isOffline)
                    "No cached cloud photos are stored on this device yet. Reconnect to sync your OneDrive library."
                else
                    "No photos or videos discovered yet. Tap 'Sync' above to discover your cloud media.",
            )
        }
        isSearchActive -> {
            if (displayedMedia.isEmpty() && searchQuery.isNotEmpty()) {
                EmptyLibraryState(
                    icon = Icons.Outlined.Search,
                    title = "No results found",
                    message = "No cloud photos matching \"$searchQuery\" were found.",
                )
            } else {
                val grouped = remember(displayedMedia) { displayedMedia.groupByMonth() }
                MediaGrid(
                    groups = grouped,
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    onMediaClick = onMediaClick,
                )
            }
        }
        browseMode == OneDriveBrowseMode.FOLDERS -> {
            OneDriveFoldersBody(
                cloudFolders = cloudFolders,
                cloudMedia = cloudMedia,
                currentFolder = currentFolder,
                foldersGridState = foldersGridState,
                onOpenFolder = onOpenFolder,
                onNavigateUp = onNavigateUp,
                onMediaClick = onMediaClick,
            )
        }
        else -> {
            val grouped = remember(displayedMedia) { displayedMedia.groupByMonth() }
            MediaGrid(
                groups = grouped,
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                onMediaClick = onMediaClick,
            )
        }
    }
}

@Composable
private fun OneDriveFoldersBody(
    cloudFolders: List<Album>,
    cloudMedia: List<MediaItem>,
    currentFolder: Album?,
    foldersGridState: LazyGridState,
    onOpenFolder: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onMediaClick: (MediaItem, List<MediaItem>) -> Unit,
) {
    val folderIds = remember(cloudFolders) { cloudFolders.map { it.id }.toSet() }
    val childFolders = remember(cloudFolders, currentFolder, folderIds) {
        if (currentFolder == null) {
            // Root-level folders have no known parent folder in the synced folder tree
            cloudFolders.filter { it.parentFolderId == null || it.parentFolderId !in folderIds }
        } else {
            cloudFolders.filter { it.parentFolderId == currentFolder.id }
        }
    }
    val folderMedia = remember(cloudMedia, currentFolder, folderIds) {
        if (currentFolder == null) {
            cloudMedia.filter { it.albumId == null || it.albumId !in folderIds }
        } else {
            // Show direct items when subfolders exist, or descendant items in leaf folders
            val direct = cloudMedia.filter { it.albumId == currentFolder.id }
            if (direct.isNotEmpty() || childFolders.isNotEmpty()) direct
            else {
                val descendants = collectDescendantFolderIds(currentFolder.id, cloudFolders)
                cloudMedia.filter { it.albumId in descendants }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (currentFolder != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onNavigateUp) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back up folder")
                }
                Column {
                    Text(currentFolder.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${currentFolder.itemCount} items",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (childFolders.isEmpty() && folderMedia.isEmpty()) {
            EmptyLibraryState(
                icon = Icons.Outlined.Folder,
                title = "Folder is empty",
                message = "No photos, videos, or subfolders were found here.",
            )
        } else if (childFolders.isEmpty()) {
            val grouped = remember(folderMedia) { folderMedia.groupByMonth() }
            MediaGrid(
                groups = grouped,
                modifier = Modifier.fillMaxSize(),
                onMediaClick = onMediaClick,
            )
        } else {
            val groupedMedia = remember(folderMedia) { folderMedia.groupByMonth() }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = foldersGridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(childFolders, key = { "folder:${it.id}" }) { folder ->
                    AlbumCard(album = folder, onClick = { onOpenFolder(folder.id) })
                }
                if (folderMedia.isNotEmpty()) {
                    item(key = "root_files_header", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "${folderMedia.size} media items here",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(folderMedia, key = { "file:${it.id}" }) { media ->
                        AlbumCard(
                            album = Album(
                                id = media.id,
                                name = media.displayName,
                                coverUri = media.localContentUri,
                                coverThumbnailUrl = media.thumbnailUrl,
                                itemCount = 1,
                                source = media.source,
                            ),
                            onClick = { onMediaClick(media, groupedMedia.flatMap { it.items }) },
                        )
                    }
                }
            }
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
            "Browse, search, and sync your cloud photos and folders alongside your device library.",
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
    }
}
