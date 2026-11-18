package com.cyrus.gallery.domain.model

import com.cyrus.gallery.domain.model.MediaItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A chronological group header + its media items. */
data class MediaGroup(
    val label: String,
    val items: List<MediaItem>,
)

private val MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

/**
 * Groups a flat chronological list of [MediaItem]s into month buckets.
 * Items without a [MediaItem.createdAt] are placed in an "Unknown date" bucket.
 */
fun List<MediaItem>.groupByMonth(zoneId: ZoneId = ZoneId.systemDefault()): List<MediaGroup> {
    val grouped = LinkedHashMap<String, MutableList<MediaItem>>()
    for (item in this) {
        val label = item.createdAt
            ?.atZone(zoneId)
            ?.format(MONTH_FORMATTER)
            ?: "Unknown date"
        grouped.getOrPut(label) { mutableListOf() }.add(item)
    }
    return grouped.map { (label, items) -> MediaGroup(label, items) }
}
