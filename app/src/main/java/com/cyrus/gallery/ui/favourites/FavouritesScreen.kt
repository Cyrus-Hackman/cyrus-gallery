package com.cyrus.gallery.ui.favourites

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.runtime.Composable
import com.cyrus.gallery.ui.components.EmptyLibraryState

@Composable
fun FavouritesScreen() = EmptyLibraryState(
    icon = Icons.Outlined.FavoriteBorder,
    title = "No favourites yet",
    message = "Favourites from both your device and OneDrive will be collected here.",
)

