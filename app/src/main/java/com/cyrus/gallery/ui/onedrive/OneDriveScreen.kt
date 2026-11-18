package com.cyrus.gallery.ui.onedrive

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.runtime.Composable
import com.cyrus.gallery.ui.components.EmptyLibraryState

@Composable
fun OneDriveScreen() = EmptyLibraryState(
    icon = Icons.Outlined.CloudQueue,
    title = "OneDrive isn’t connected",
    message = "Sign-in and Microsoft Graph browsing are deliberately configured in the cloud integration phase, after your app registration is ready.",
)

