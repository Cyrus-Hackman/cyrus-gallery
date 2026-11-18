package com.cyrus.gallery.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        IconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp, top = 12.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
        Text("Settings", modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp), style = MaterialTheme.typography.headlineMedium)
        ListItem(headlineContent = { Text("OneDrive") }, supportingContent = { Text("Not connected — configured in Phase 3") }, leadingContent = { Icon(Icons.Outlined.CloudQueue, null) })
        HorizontalDivider()
        ListItem(headlineContent = { Text("Theme") }, supportingContent = { Text("Follows your system and supports dynamic colour") }, leadingContent = { Icon(Icons.Outlined.Palette, null) })
        HorizontalDivider()
        ListItem(headlineContent = { Text("About Cyrus Gallery") }, supportingContent = { Text("Version 0.1.0 · A private, unified photo library") })
    }
}

