@file:OptIn(com.google.accompanist.permissions.ExperimentalPermissionsApi::class)

package com.cyrus.gallery.ui.permissions

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

/**
 * Returns the correct set of permissions to request based on API level.
 * - API 34+: READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_MEDIA_VISUAL_USER_SELECTED (partial)
 * - API 33:  READ_MEDIA_IMAGES, READ_MEDIA_VIDEO
 * - API ≤32: READ_EXTERNAL_STORAGE
 */
fun requiredMediaPermissions(): List<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> listOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
    )
    else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

/** True when the user has granted at least partial access sufficient to query MediaStore. */
fun hasMediaAccess(state: MultiplePermissionsState): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        // Full access: both images and video granted
        val full = state.permissions
            .filter { it.permission != Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED }
            .all { it.status.isGranted }
        // Partial access: READ_MEDIA_VISUAL_USER_SELECTED granted
        val partial = state.permissions
            .firstOrNull { it.permission == Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED }
            ?.status?.isGranted == true
        return full || partial
    }
    return state.allPermissionsGranted
}

@Composable
fun MediaPermissionGate(content: @Composable (hasPartialAccess: Boolean) -> Unit) {
    val permissions = requiredMediaPermissions()
    val state = rememberMultiplePermissionsState(permissions)

    when {
        hasMediaAccess(state) -> {
            val isPartial = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                    !state.permissions
                        .filter { it.permission != Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED }
                        .all { it.status.isGranted }
            content(isPartial)
        }
        state.permissions.any { it.status.shouldShowRationale } ->
            PermissionRationaleScreen(onRequest = { state.launchMultiplePermissionRequest() })
        else ->
            PermissionRequestScreen(onRequest = { state.launchMultiplePermissionRequest() })
    }
}

@Composable
private fun PermissionRequestScreen(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.PhotoLibrary, contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text("Allow photo access", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "Cyrus Gallery needs access to your photos and videos to display them here.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onRequest) { Text("Allow access") }
    }
}

@Composable
private fun PermissionRationaleScreen(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.PhotoLibrary, contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text("Permission required", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "Cyrus Gallery was not granted access to your photos. Grant photo access so your library can appear here.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onRequest) { Text("Grant access") }
    }
}
