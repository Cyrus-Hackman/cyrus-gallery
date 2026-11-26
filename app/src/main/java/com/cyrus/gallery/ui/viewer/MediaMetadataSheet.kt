package com.cyrus.gallery.ui.viewer

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import com.cyrus.gallery.domain.model.CloudStorageState
import com.cyrus.gallery.domain.model.MediaItem
import com.cyrus.gallery.domain.model.MediaSource
import com.cyrus.gallery.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ExifDetails(
    val cameraModel: String? = null,
    val aperture: String? = null,
    val exposureTime: String? = null,
    val iso: String? = null,
    val focalLength: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val durationMillis: Long? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaMetadataSheet(
    item: MediaItem,
    sheetState: SheetState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val exifState by produceState<ExifDetails?>(initialValue = null, item.id, item.localContentUri, item.type) {
        value = withContext(Dispatchers.IO) {
            when (item.type) {
                MediaType.VIDEO -> readVideoDetails(context, item.localContentUri)
                MediaType.IMAGE -> readExif(context, item.localContentUri)
            }
        }
    }
    val exif = exifState
    val effectiveDurationMillis = item.durationMillis?.takeIf { it > 0L }
        ?: exif?.durationMillis?.takeIf { it > 0L }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Text("Details", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            // File Name & Type
            MetadataRow(
                icon = Icons.Outlined.Info,
                label = item.displayName,
                subtext = item.mimeType,
            )

            // Date / Time
            val dateStr = item.createdAt?.atZone(ZoneId.systemDefault())?.format(
                DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy · h:mm a", Locale.getDefault()),
            ) ?: "Date unknown"
            MetadataRow(
                icon = Icons.Outlined.Schedule,
                label = dateStr,
                subtext = "Created timestamp",
            )

            // Storage Source & Folder / Album Name
            val sourceLabel = when (item.source) {
                MediaSource.LOCAL -> "Stored locally on device"
                MediaSource.ONEDRIVE -> when (item.cloudStorageState) {
                    CloudStorageState.PINNED_OFFLINE -> "OneDrive (Pinned Offline)"
                    CloudStorageState.CACHED -> "OneDrive (Cached on device)"
                    CloudStorageState.REMOTE_ONLY, null -> "OneDrive (Cloud only)"
                }
            }
            val locationLabel = item.albumName?.takeIf { it.isNotBlank() }
                ?: if (item.source == MediaSource.ONEDRIVE) "OneDrive Root" else "Library"
            MetadataRow(
                icon = if (item.source == MediaSource.LOCAL) Icons.Outlined.Smartphone else Icons.Outlined.Cloud,
                label = sourceLabel,
                subtext = locationLabel,
            )

            val hasDuration = item.type == MediaType.VIDEO && effectiveDurationMillis != null
            val hasCameraInfo = exif != null && !exif.cameraModel.isNullOrEmpty()
            val hasDimensions = exif != null && exif.width > 0 && exif.height > 0

            // Media-specific details (video duration, EXIF camera specs, dimensions)
            if (hasDuration || hasCameraInfo || hasDimensions) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                if (hasDuration) {
                    MetadataRow(
                        icon = Icons.Outlined.Schedule,
                        label = formatMediaDuration(effectiveDurationMillis!!),
                        subtext = "Duration",
                    )
                }
                if (hasCameraInfo) {
                    MetadataRow(
                        icon = Icons.Outlined.Camera,
                        label = exif!!.cameraModel!!,
                        subtext = listOfNotNull(exif.aperture, exif.exposureTime, exif.iso, exif.focalLength).joinToString(" · "),
                    )
                }
                if (hasDimensions) {
                    val resolutionLabel = if (item.type == MediaType.VIDEO) {
                        "${exif!!.width} × ${exif.height}"
                    } else {
                        val megapixels = String.format(Locale.US, "%.1f MP", (exif!!.width * exif.height) / 1_000_000.0)
                        "${exif.width} × ${exif.height} ($megapixels)"
                    }
                    MetadataRow(
                        icon = Icons.Outlined.Folder,
                        label = resolutionLabel,
                        subtext = if (item.type == MediaType.VIDEO) "Video resolution" else "Resolution",
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun MetadataRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    subtext: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(subtext, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun formatMediaDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

private fun readVideoDetails(context: Context, uri: Uri?): ExifDetails? {
    if (uri == null) return null
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?.takeIf { it > 0L }
        val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val (width, height) = if (rotation == 90 || rotation == 270) {
            rawHeight to rawWidth
        } else {
            rawWidth to rawHeight
        }
        ExifDetails(
            width = width,
            height = height,
            durationMillis = duration,
        )
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private fun readExif(context: Context, uri: Uri?): ExifDetails? {
    if (uri == null) return null
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val ex = ExifInterface(stream)
            val model = ex.getAttribute(ExifInterface.TAG_MODEL)
            val make = ex.getAttribute(ExifInterface.TAG_MAKE)
            val fullModel = listOfNotNull(make, model).distinct().joinToString(" ")
            val fNumber = ex.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { "f/$it" }
            val exp = ex.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.let { "${it}s" }
            val iso = ex.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { "ISO $it" }
            val focal = ex.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.let { "${it}mm" }
            var w = ex.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
            var h = ex.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
            if (w <= 0 || h <= 0) {
                context.contentResolver.openInputStream(uri)?.use { dimStream ->
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(dimStream, null, opts)
                    if (opts.outWidth > 0 && opts.outHeight > 0) {
                        w = opts.outWidth
                        h = opts.outHeight
                    }
                }
            }

            ExifDetails(
                cameraModel = fullModel.takeIf { it.isNotEmpty() },
                aperture = fNumber,
                exposureTime = exp,
                iso = iso,
                focalLength = focal,
                width = w,
                height = h,
            )
        }
    } catch (_: Exception) {
        null
    }
}
