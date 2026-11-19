package com.cyrus.gallery.ui.onedrive

import android.app.Activity
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import kotlinx.coroutines.delay

@Composable
fun OneDriveScreen(
    onMediaClick: (MediaItem, List<MediaItem>) -> Unit = { _, _ -> },
    viewModel: OneDriveViewModel = hiltViewModel(),
) {
    val authState = viewModel.authState.collectAsStateWithLifecycle().value
    val cloudMedia = viewModel.cloudMedia.collectAsStateWithLifecycle().value
    val isHydrating = viewModel.isHydrating.collectAsStateWithLifecycle().value
    val isSyncing = viewModel.isSyncing.collectAsStateWithLifecycle().value
    val statusMessage = viewModel.statusMessage.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val activity = context as? Activity
    val gridState = rememberLazyGridState()

    // Auto-dismiss sync status message after 3 seconds
    LaunchedEffect(statusMessage, isSyncing) {
        if (!statusMessage.isNullOrEmpty() && !isSyncing) {
            delay(3_000)
            viewModel.clearStatusMessage()
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Screen Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("OneDrive", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Cloud media and albums",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
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
        }

        when (authState) {
            is AuthState.Authenticating -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Connecting to Microsoft OneDrive...", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            is AuthState.SignedOut -> {
                SignedOutContent(
                    errorMessage = authState.message,
                    onSignIn = { activity?.let { viewModel.signIn(it) } },
                )
            }

            is AuthState.SignedIn -> {
                SignedInContent(
                    accountName = authState.displayName,
                    mediaCount = cloudMedia.size,
                    isSyncing = isSyncing,
                    statusMessage = statusMessage,
                    onSignOut = { viewModel.signOut() },
                )

                if (isHydrating && cloudMedia.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (cloudMedia.isEmpty()) {
                    EmptyLibraryState(
                        icon = Icons.Outlined.CloudDone,
                        title = "OneDrive connected",
                        message = "No photos or videos discovered yet. Tap 'Sync' above to discover your cloud media.",
                    )
                } else {
                    MediaGrid(
                        groups = cloudMedia.groupByMonth(),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        onMediaClick = onMediaClick,
                    )
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

@Composable
private fun SignedInContent(
    accountName: String,
    mediaCount: Int,
    isSyncing: Boolean,
    statusMessage: String?,
    onSignOut: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(accountName, style = MaterialTheme.typography.titleSmall)
                Text(
                    if (isSyncing) "Syncing..." else "$mediaCount items in cloud",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!statusMessage.isNullOrEmpty() && statusMessage != "Signed in successfully") {
                    Text(
                        statusMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            OutlinedButton(onClick = onSignOut) {
                Text("Sign out", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
