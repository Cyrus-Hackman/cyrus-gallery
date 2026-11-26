package com.cyrus.gallery.ui.viewer

import com.cyrus.gallery.domain.model.Album
import com.cyrus.gallery.domain.model.MediaItem
import com.cyrus.gallery.domain.model.MediaSource
import com.cyrus.gallery.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class MediaViewerScopeTest {

    private fun cloudItem(
        id: String,
        name: String,
        albumId: String? = null,
        isFav: Boolean = false,
        secondsOffset: Long = 0L,
    ) = MediaItem(
        id = "onedrive:$id",
        displayName = name,
        type = MediaType.IMAGE,
        source = MediaSource.ONEDRIVE,
        mimeType = "image/jpeg",
        createdAt = Instant.parse("2025-01-10T12:00:00Z").minusSeconds(secondsOffset),
        modifiedAt = Instant.parse("2025-01-10T12:00:00Z").minusSeconds(secondsOffset),
        oneDriveItemId = id,
        albumId = albumId,
        isFavourite = isFav,
    )

    @Test
    fun `filterOneDriveViewerItems scopes root folder tap only to root-level media`() {
        val folders = listOf(
            Album(id = "folder_vac", name = "Vacation", source = MediaSource.ONEDRIVE),
            Album(id = "local_bucket", name = "Camera", source = MediaSource.LOCAL),
        )
        // Drive root files can have either null albumId or the drive's excluded root item ID
        val rootFileNullAlbum = cloudItem(id = "r1", name = "RootNull.jpg", albumId = null)
        val rootFileDriveRootId = cloudItem(id = "r2", name = "RootDrive.jpg", albumId = "drive_root_0")
        val nestedFile = cloudItem(id = "n1", name = "Beach.jpg", albumId = "folder_vac")
        val allCloud = listOf(rootFileNullAlbum, rootFileDriveRootId, nestedFile)

        val rootScopedConfig = ViewerSessionConfig(
            sourceRoute = ViewerScopeRoutes.ONEDRIVE,
            selectedItemId = rootFileDriveRootId.id,
            albumId = null,
            rootFolderOnly = true,
        )

        val scoped = MediaViewerViewModel.filterOneDriveViewerItems(allCloud, folders, rootScopedConfig)
        assertEquals(listOf("onedrive:r1", "onedrive:r2"), scoped.map { it.id })

        val allMediaConfig = ViewerSessionConfig(
            sourceRoute = ViewerScopeRoutes.ONEDRIVE,
            selectedItemId = rootFileDriveRootId.id,
            albumId = null,
            rootFolderOnly = false,
        )
        val all = MediaViewerViewModel.filterOneDriveViewerItems(allCloud, folders, allMediaConfig)
        assertEquals(3, all.size)
    }

    @Test
    fun `filterOneDriveViewerItems restricts to matching folder when albumId is set`() {
        val items = listOf(
            cloudItem(id = "r1", name = "Root.jpg", albumId = null),
            cloudItem(id = "f1", name = "Pic1.jpg", albumId = "folder_vac"),
            cloudItem(id = "f2", name = "Pic2.jpg", albumId = "folder_docs"),
        )
        val config = ViewerSessionConfig(
            sourceRoute = ViewerScopeRoutes.ONEDRIVE,
            selectedItemId = "onedrive:f1",
            albumId = "folder_vac",
            rootFolderOnly = false,
        )

        val result = MediaViewerViewModel.filterOneDriveViewerItems(items, emptyList(), config)
        assertEquals(listOf("onedrive:f1"), result.map { it.id })
    }

    @Test
    fun `filterFavoriteSessionItems keeps duplicate-filename cloud favorites and unfavorited session items`() {
        val localDup = MediaItem(
            id = "local:1",
            displayName = "Sunset.jpg",
            type = MediaType.IMAGE,
            source = MediaSource.LOCAL,
            mimeType = "image/jpeg",
            createdAt = Instant.parse("2025-01-10T12:00:00Z"),
            modifiedAt = Instant.parse("2025-01-10T12:00:00Z"),
            isFavourite = false,
        )
        val cloudDupFav = cloudItem(
            id = "c_dup",
            name = "Sunset.jpg",
            isFav = true,
            secondsOffset = 60,
        )
        val cloudUnfavoritedInSession = cloudItem(
            id = "c_unfav",
            name = "Lake.jpg",
            isFav = false,
            secondsOffset = 120,
        )

        // unifiedMedia dropped cloudDupFav because local:1 had the same filename
        val result = MediaViewerViewModel.filterFavoriteSessionItems(
            unifiedMedia = listOf(localDup, cloudUnfavoritedInSession),
            cloudMedia = listOf(cloudDupFav, cloudUnfavoritedInSession),
            sessionAllowedIds = setOf("onedrive:c_dup", "onedrive:c_unfav"),
        )

        assertEquals(listOf("onedrive:c_dup", "onedrive:c_unfav"), result.map { it.id })
    }
}
