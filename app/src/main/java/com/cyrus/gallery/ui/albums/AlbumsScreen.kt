package com.cyrus.gallery.ui.albums

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.runtime.Composable
import com.cyrus.gallery.ui.components.EmptyLibraryState

@Composable
fun AlbumsScreen() = EmptyLibraryState(
    icon = Icons.Outlined.Collections,
    title = "Albums will appear here",
    message = "Device albums and OneDrive folders will remain clearly separated while still easy to browse.",
)

