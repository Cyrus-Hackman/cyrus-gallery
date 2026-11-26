package com.cyrus.gallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cyrus.gallery.domain.model.MediaItem
import com.cyrus.gallery.ui.albums.AlbumsScreen
import com.cyrus.gallery.ui.favourites.FavouritesScreen
import com.cyrus.gallery.ui.gallery.PhotosScreen
import com.cyrus.gallery.ui.navigation.GalleryDestination
import com.cyrus.gallery.ui.onedrive.OneDriveScreen
import com.cyrus.gallery.ui.settings.SettingsScreen
import com.cyrus.gallery.ui.viewer.MediaViewerScreen
import com.cyrus.gallery.ui.viewer.ViewerSessionConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

@Composable
fun CyrusGalleryApp() {
    val navController = rememberNavController()
    val current = navController.currentBackStackEntryAsState().value?.destination?.route
    var savedRoute by rememberSaveable { mutableStateOf(GalleryDestination.PHOTOS.route) }

    // Persist viewer target cleanly via primitive rememberSaveable fields so rotation keeps the open item without TransactionTooLargeException
    var viewerSourceRoute by rememberSaveable { mutableStateOf<String?>(null) }
    var viewerSelectedItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var viewerSearchQuery by rememberSaveable { mutableStateOf("") }
    var viewerAlbumId by rememberSaveable { mutableStateOf<String?>(null) }
    var viewerAlbumName by rememberSaveable { mutableStateOf<String?>(null) }
    var viewerRootFolderOnly by rememberSaveable { mutableStateOf(false) }
    var viewerSessionSeq by rememberSaveable { mutableIntStateOf(0) }
    var viewerInitialItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    val isViewerOpen = viewerSourceRoute != null && viewerSelectedItemId != null
    val tabReselected = remember { MutableSharedFlow<String>(extraBufferCapacity = 1) }

    // Track active top-level tab so it survives configuration changes and recomposition
    LaunchedEffect(current) {
        if (current != null && current != "settings") {
            savedRoute = current
        }
    }

    val navigateToTab: (String) -> Unit = { route ->
        savedRoute = route
        navController.navigate(route) {
            launchSingleTop = true
            restoreState = true
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        }
    }

    val onOpenViewer: (ViewerSessionConfig) -> Unit = { config ->
        viewerSourceRoute = config.sourceRoute
        viewerSelectedItemId = config.selectedItemId
        viewerSearchQuery = config.searchQuery
        viewerAlbumId = config.albumId
        viewerAlbumName = config.albumName
        viewerInitialItems = config.initialItems
        viewerSessionSeq++
    }

    val onDismissViewer = {
        val routeToRestore = savedRoute
        viewerSourceRoute = null
        viewerSelectedItemId = null
        viewerSearchQuery = ""
        viewerAlbumId = null
        viewerAlbumName = null
        viewerInitialItems = emptyList()
        if (current != routeToRestore) {
            navigateToTab(routeToRestore)
        }
    }

    // Save non-start tab scroll & ViewModel state on system Back instead of popping and destroying the tab
    BackHandler(
        enabled = !isViewerOpen &&
            current != null &&
            current != GalleryDestination.PHOTOS.route &&
            current != "settings",
    ) {
        navigateToTab(GalleryDestination.PHOTOS.route)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // App Scaffold remains persistently composed so grid scroll position is never lost while viewer is open
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (current != "settings" && !isViewerOpen) NavigationBar {
                    GalleryDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = current == destination.route,
                            onClick = {
                                if (current == destination.route) {
                                    tabReselected.tryEmit(destination.route)
                                } else {
                                    navigateToTab(destination.route)
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            },
        ) { padding ->
            GalleryNavHost(
                navController = navController,
                padding = padding,
                tabReselected = tabReselected,
                onOpenViewer = onOpenViewer,
            )
        }

        // Fullscreen Image/Video Viewer overlaid on top (BackHandler lives inside MediaViewerScreen for guaranteed precedence)
        val activeSource = viewerSourceRoute
        val activeSelectedId = viewerSelectedItemId
        if (activeSource != null && activeSelectedId != null) {
            key(viewerSessionSeq) {
                MediaViewerScreen(
                    config = ViewerSessionConfig(
                        sourceRoute = activeSource,
                        selectedItemId = activeSelectedId,
                        searchQuery = viewerSearchQuery,
                        albumId = viewerAlbumId,
                        albumName = viewerAlbumName,
                        initialItems = viewerInitialItems,
                    ),
                    onCurrentItemChanged = { newId ->
                        viewerSelectedItemId = newId
                    },
                    onBack = onDismissViewer,
                )
            }
        }
    }
}

@Composable
private fun GalleryNavHost(
    navController: androidx.navigation.NavHostController,
    padding: PaddingValues,
    tabReselected: Flow<String>,
    onOpenViewer: (ViewerSessionConfig) -> Unit,
) {
    NavHost(navController, startDestination = GalleryDestination.PHOTOS.route, modifier = Modifier.padding(padding)) {
        composable("photos") {
            PhotosScreen(
                onSettings = { navController.navigate("settings") },
                onOpenViewer = onOpenViewer,
                tabReselected = tabReselected,
            )
        }
        composable("albums") {
            AlbumsScreen(
                onOpenViewer = onOpenViewer,
                tabReselected = tabReselected,
            )
        }
        composable("onedrive") {
            OneDriveScreen(
                onSettings = { navController.navigate("settings") },
                onOpenViewer = onOpenViewer,
                tabReselected = tabReselected,
            )
        }
        composable("favourites") {
            FavouritesScreen(
                onOpenViewer = onOpenViewer,
                tabReselected = tabReselected,
            )
        }
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
