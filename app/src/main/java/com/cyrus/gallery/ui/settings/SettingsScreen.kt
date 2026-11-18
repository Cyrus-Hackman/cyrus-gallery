package com.cyrus.gallery.ui.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cyrus.gallery.domain.model.AutomaticDownloadMode
import com.cyrus.gallery.domain.model.SyncNetworkMode

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val storage = viewModel.storageSummary.collectAsStateWithLifecycle().value
    val policy = viewModel.policy.collectAsStateWithLifecycle().value
    val actionMessage = viewModel.actionMessage.collectAsStateWithLifecycle().value

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        // Storage Management Card
        Text(
            "Cloud Cache & Storage",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            color = MaterialTheme.colorScheme.primary,
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.SdCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("App-Private Managed Storage", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(12.dp))

                val totalCacheBytes = storage.cachedPhotoBytes + storage.cachedVideoBytes + storage.thumbnailBytes
                val totalCacheMb = totalCacheBytes / (1024 * 1024)
                val pinnedMb = storage.pinnedBytes / (1024 * 1024)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Temporary Cached Media", style = MaterialTheme.typography.bodyMedium)
                    Text("$totalCacheMb MB", style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Pinned Offline Media", style = MaterialTheme.typography.bodyMedium)
                    Text("$pinnedMb MB", style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Cached File Count", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${storage.cachedFileCount} files", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (!actionMessage.isNullOrEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        actionMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(Modifier.height(14.dp))
                OutlinedButton(
                    onClick = { viewModel.clearTemporaryCache() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Clear Temporary Cache")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Cache & Network Policy
        Text(
            "Sync & Download Rules",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
            color = MaterialTheme.colorScheme.primary,
        )

        ListItem(
            headlineContent = { Text("Network Mode") },
            supportingContent = {
                Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = policy.networkMode == SyncNetworkMode.WIFI_ONLY,
                        onClick = { viewModel.updateNetworkMode(SyncNetworkMode.WIFI_ONLY) },
                        label = { Text("Wi-Fi Only") },
                        leadingIcon = if (policy.networkMode == SyncNetworkMode.WIFI_ONLY) {
                            { Icon(Icons.Outlined.Check, null, modifier = Modifier.size(14.dp)) }
                        } else null,
                    )
                    FilterChip(
                        selected = policy.networkMode == SyncNetworkMode.WIFI_AND_MOBILE,
                        onClick = { viewModel.updateNetworkMode(SyncNetworkMode.WIFI_AND_MOBILE) },
                        label = { Text("Wi-Fi & Mobile") },
                        leadingIcon = if (policy.networkMode == SyncNetworkMode.WIFI_AND_MOBILE) {
                            { Icon(Icons.Outlined.Check, null, modifier = Modifier.size(14.dp)) }
                        } else null,
                    )
                }
            },
            leadingContent = { Icon(Icons.Outlined.Wifi, null) },
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        ListItem(
            headlineContent = { Text("Automatic Download") },
            supportingContent = {
                Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = policy.automaticDownload == AutomaticDownloadMode.THUMBNAILS_ONLY,
                        onClick = { viewModel.updateDownloadMode(AutomaticDownloadMode.THUMBNAILS_ONLY) },
                        label = { Text("Thumbnails Only") },
                    )
                    FilterChip(
                        selected = policy.automaticDownload == AutomaticDownloadMode.ON_DEMAND,
                        onClick = { viewModel.updateDownloadMode(AutomaticDownloadMode.ON_DEMAND) },
                        label = { Text("On-Demand") },
                    )
                }
            },
            leadingContent = { Icon(Icons.Outlined.CloudDownload, null) },
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        // System & About
        Spacer(Modifier.height(8.dp))
        ListItem(
            headlineContent = { Text("Theme") },
            supportingContent = { Text("Follows system appearance and supports dynamic color") },
            leadingContent = { Icon(Icons.Outlined.Palette, null) },
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        ListItem(
            headlineContent = { Text("About Cyrus Gallery") },
            supportingContent = { Text("Version 0.1.0 · Managed cloud cache & unified library") },
        )

        Spacer(Modifier.height(24.dp))
    }
}
