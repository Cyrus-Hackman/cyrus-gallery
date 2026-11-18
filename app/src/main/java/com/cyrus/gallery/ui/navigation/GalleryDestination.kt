package com.cyrus.gallery.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.ui.graphics.vector.ImageVector

enum class GalleryDestination(val route: String, val label: String, val icon: ImageVector) {
    PHOTOS("photos", "Photos", Icons.Outlined.PhotoLibrary),
    ALBUMS("albums", "Albums", Icons.Outlined.Collections),
    ONEDRIVE("onedrive", "OneDrive", Icons.Outlined.FolderOpen),
    FAVOURITES("favourites", "Favourites", Icons.Outlined.FavoriteBorder),
}

