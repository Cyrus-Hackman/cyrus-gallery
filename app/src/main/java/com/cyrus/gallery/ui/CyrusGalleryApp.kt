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

@Composable
fun CyrusGalleryApp() {
    val navController = rememberNavController()
    val current = navController.currentBackStackEntryAsState().value?.destination?.route
    var viewingState by remember { mutableStateOf<Pair<List<MediaItem>, Int>?>(null) }
    var savedRoute by rememberSaveable { mutableStateOf(GalleryDestination.PHOTOS.route) }

    val activeState = viewingState

    // Track active top-level tab so it survives configuration changes and recomposition
    LaunchedEffect(current) {
        if (current != null && current != "settings") {
            savedRoute = current
        }
    }

    val onDismissViewer = {
        // Capture savedRoute before nulling viewingState to avoid recomposition race
        val routeToRestore = savedRoute
        viewingState = null
        if (current != routeToRestore) {
            navController.navigate(routeToRestore) {
                launchSingleTop = true
                restoreState = true
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            }
        }
    }

    // Intercept phone's physical back button / swipe back gesture when viewer is open
    BackHandler(enabled = activeState != null) {
        onDismissViewer()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // App Scaffold remains persistently composed so grid scroll position is never lost
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (current != "settings" && activeState == null) NavigationBar {
                    GalleryDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = current == destination.route,
                            onClick = {
                                savedRoute = destination.route
                                navController.navigate(destination.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
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
                onMediaClick = { clickedItem, allItems ->
                    val index = allItems.indexOfFirst { it.id == clickedItem.id }.coerceAtLeast(0)
                    viewingState = Pair(allItems, index)
                },
            )
        }

        // Fullscreen Image/Video Viewer overlaid on top without disposing the grid
        if (activeState != null && activeState.first.isNotEmpty()) {
            MediaViewerScreen(
                items = activeState.first,
                initialIndex = activeState.second,
                onBack = onDismissViewer,
            )
        }
    }
}

@Composable
private fun GalleryNavHost(
    navController: androidx.navigation.NavHostController,
    padding: PaddingValues,
    onMediaClick: (MediaItem, List<MediaItem>) -> Unit,
) {
    NavHost(navController, startDestination = GalleryDestination.PHOTOS.route, modifier = Modifier.padding(padding)) {
        composable("photos") {
            PhotosScreen(
                onSettings = { navController.navigate("settings") },
                onMediaClick = onMediaClick,
            )
        }
        composable("albums") {
            AlbumsScreen(onMediaClick = onMediaClick)
        }
        composable("onedrive") {
            OneDriveScreen(onMediaClick = onMediaClick)
        }
        composable("favourites") {
            FavouritesScreen(onMediaClick = onMediaClick)
        }
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
