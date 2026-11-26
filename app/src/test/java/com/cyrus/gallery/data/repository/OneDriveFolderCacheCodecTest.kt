package com.cyrus.gallery.data.repository

import com.cyrus.gallery.domain.model.Album
import com.cyrus.gallery.domain.model.MediaSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OneDriveFolderCacheCodecTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `encode and decode preserve folder hierarchy and omit root entries`() {
        val folders = listOf(
            Album(id = "root_0", name = "root", source = MediaSource.ONEDRIVE, parentFolderId = null),
            Album(
                id = "pic_1",
                name = "Pictures",
                coverThumbnailUrl = "https://graph.microsoft.com/thumb/1",
                source = MediaSource.ONEDRIVE,
                parentFolderId = "root_0",
            ),
            Album(
                id = "vac_2",
                name = "Vacation",
                coverThumbnailUrl = null,
                source = MediaSource.ONEDRIVE,
                parentFolderId = "pic_1",
            ),
        )

        val encoded = OneDriveFolderCacheCodec.encode(folders)
        val decoded = OneDriveFolderCacheCodec.decode(encoded)

        assertEquals(2, decoded.size)
        assertEquals("pic_1", decoded[0].id)
        assertEquals("Pictures", decoded[0].name)
        assertEquals("https://graph.microsoft.com/thumb/1", decoded[0].coverThumbnailUrl)
        assertEquals("root_0", decoded[0].parentFolderId)
        assertEquals(MediaSource.ONEDRIVE, decoded[0].source)

        assertEquals("vac_2", decoded[1].id)
        assertNull(decoded[1].coverThumbnailUrl)
        assertEquals("pic_1", decoded[1].parentFolderId)
    }

    @Test
    fun `mergeSyncedFolders updates existing folders applies deletions and drops root`() {
        val existing = listOf(
            Album(id = "f1", name = "Old Name", source = MediaSource.ONEDRIVE, parentFolderId = "root_0"),
            Album(id = "f2", name = "Deleted Folder", source = MediaSource.ONEDRIVE, parentFolderId = "root_0"),
        )
        val incoming = listOf(
            Album(id = "root_0", name = "root", source = MediaSource.ONEDRIVE, parentFolderId = null),
            Album(id = "f1", name = "Renamed Folder", source = MediaSource.ONEDRIVE, parentFolderId = "root_0"),
            Album(id = "f3", name = "New Folder", source = MediaSource.ONEDRIVE, parentFolderId = "f1"),
        )

        val merged = OneDriveFolderCacheCodec.mergeSyncedFolders(
            existing = existing,
            incoming = incoming,
            deletedIds = setOf("f2"),
        )

        assertEquals(listOf("f1", "f3"), merged.map { it.id })
        assertEquals("Renamed Folder", merged.first { it.id == "f1" }.name)
    }

    @Test
    fun `writeToFileAtomically and readFromFile survive corruption checks and restore cleanly`() {
        val file = File(tempFolder.root, "onedrive_folders_cache.json")
        val folders = listOf(
            Album(id = "f1", name = "Camera Roll", source = MediaSource.ONEDRIVE, parentFolderId = "root_0"),
            Album(id = "f2", name = "2026", source = MediaSource.ONEDRIVE, parentFolderId = "f1"),
        )

        OneDriveFolderCacheCodec.writeToFileAtomically(file, folders)
        val restored = OneDriveFolderCacheCodec.readFromFile(file)
        assertEquals(2, restored.size)
        assertEquals(listOf("Camera Roll", "2026"), restored.map { it.name })

        file.writeText("{corrupt json")
        assertTrue(OneDriveFolderCacheCodec.readFromFile(file).isEmpty())
    }
}
