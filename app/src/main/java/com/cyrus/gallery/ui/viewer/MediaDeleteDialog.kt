package com.cyrus.gallery.ui.viewer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cyrus.gallery.domain.model.CloudStorageState
import com.cyrus.gallery.domain.model.MediaItem
import com.cyrus.gallery.domain.model.MediaSource

enum class DeleteTarget {
    LOCAL_DEVICE,
    CLOUD_CACHE_ONLY,
    ONEDRIVE_PERMANENT,
}

@Composable
fun MediaDeleteDialog(
    item: MediaItem,
    onConfirm: (DeleteTarget) -> Unit,
    onDismiss: () -> Unit,
) {
    val isLocal = item.source == MediaSource.LOCAL
    val isCached = item.cloudStorageState == CloudStorageState.CACHED || item.cloudStorageState == CloudStorageState.PINNED_OFFLINE

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Outlined.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(
                when {
                    isLocal -> "Delete from this device?"
                    isCached -> "Manage or delete cloud photo?"
                    else -> "Permanently delete from OneDrive?"
                },
            )
        },
        text = {
            Column {
                Text(
                    when {
                        isLocal -> "This photo will be permanently deleted from your phone's local storage. This action cannot be undone."
                        isCached -> "You can remove the locally downloaded copy to reclaim phone storage, or permanently delete the original from your OneDrive account."
                        else -> "This photo will be permanently deleted from your Microsoft OneDrive cloud storage. Other synced devices will also lose access."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!isLocal) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Cloud originals are never removed when you clear your cache.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Column {
                if (isCached) {
                    TextButton(
                        onClick = { onConfirm(DeleteTarget.CLOUD_CACHE_ONLY) },
                    ) {
                        Text("Remove Cached Copy Only")
                    }
                }
                TextButton(
                    onClick = {
                        onConfirm(if (isLocal) DeleteTarget.LOCAL_DEVICE else DeleteTarget.ONEDRIVE_PERMANENT)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(if (isLocal) "Delete from Device" else "Permanently Delete from OneDrive")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
