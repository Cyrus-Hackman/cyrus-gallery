package com.cyrus.gallery.domain.model

import android.net.Uri

/** Unified album / folder representation for local device collections and OneDrive folders. */
data class Album(
    val id: String,
    val name: String,
    val coverUri: Uri? = null,
    val coverThumbnailUrl: String? = null,
    val itemCount: Int = 0,
    val source: MediaSource = MediaSource.LOCAL,
    val parentFolderId: String? = null,
)

/** Collects [rootFolderId] plus all nested child folder IDs from [albums]. */
fun collectDescendantFolderIds(rootFolderId: String, albums: List<Album>): Set<String> {
    val result = linkedSetOf(rootFolderId)
    val queue = ArrayDeque<String>()
    queue.add(rootFolderId)
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        for (album in albums) {
            if (album.parentFolderId == current && result.add(album.id)) {
                queue.add(album.id)
            }
        }
    }
    return result
}
