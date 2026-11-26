package com.cyrus.gallery.data.repository

import com.cyrus.gallery.domain.model.Album
import com.cyrus.gallery.domain.model.MediaSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Serializes and persists OneDrive folder metadata locally so incremental delta syncs
 * retain the folder hierarchy across app restarts without needing a full rescan.
 */
internal object OneDriveFolderCacheCodec {

    fun encode(folders: List<Album>): String {
        val array = JSONArray()
        for (folder in folders.filterEligibleCloudFolders()) {
            val obj = JSONObject().apply {
                put("id", folder.id)
                put("name", folder.name)
                if (!folder.coverThumbnailUrl.isNullOrEmpty()) {
                    put("coverThumbnailUrl", folder.coverThumbnailUrl)
                }
                if (!folder.parentFolderId.isNullOrEmpty()) {
                    put("parentFolderId", folder.parentFolderId)
                }
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun decode(jsonString: String): List<Album> {
        if (jsonString.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(jsonString)
            val result = mutableListOf<Album>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id").takeIf { it.isNotEmpty() } ?: continue
                val name = obj.optString("name", "")
                val coverUrl = obj.optString("coverThumbnailUrl").takeIf { it.isNotEmpty() }
                val parentId = obj.optString("parentFolderId").takeIf { it.isNotEmpty() }
                result.add(
                    Album(
                        id = id,
                        name = name,
                        coverThumbnailUrl = coverUrl,
                        source = MediaSource.ONEDRIVE,
                        parentFolderId = parentId,
                    ),
                )
            }
            result.filterEligibleCloudFolders().distinctBy { it.id }
        }.getOrDefault(emptyList())
    }

    fun mergeSyncedFolders(
        existing: List<Album>,
        incoming: List<Album>,
        deletedIds: Set<String>,
    ): List<Album> = (incoming + existing.filter { it.id !in deletedIds })
        .filterEligibleCloudFolders()
        .distinctBy { it.id }

    fun readFromFile(file: File): List<Album> {
        if (!file.exists()) return emptyList()
        return runCatching { decode(file.readText()) }.getOrDefault(emptyList())
    }

    fun writeToFileAtomically(file: File, folders: List<Album>) {
        val parent = file.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()
        val tempFile = File(parent, "${file.name}.tmp")
        tempFile.writeText(encode(folders))
        if (file.exists()) file.delete()
        if (!tempFile.renameTo(file)) {
            tempFile.copyTo(file, overwrite = true)
            tempFile.delete()
        }
    }

    private fun List<Album>.filterEligibleCloudFolders(): List<Album> = filterNot { folder ->
        folder.parentFolderId.isNullOrEmpty() && folder.name.equals("root", ignoreCase = true)
    }
}
