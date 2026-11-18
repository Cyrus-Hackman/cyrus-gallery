package com.cyrus.gallery.data.cache

import android.content.Context
import com.cyrus.gallery.domain.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrivateStorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val baseDir: File = File(context.filesDir, "cyrus_gallery").apply { mkdirs() }
    val cachePhotosDir: File = File(baseDir, "cache/photos").apply { mkdirs() }
    val cacheVideosDir: File = File(baseDir, "cache/videos").apply { mkdirs() }
    val cacheThumbnailsDir: File = File(baseDir, "cache/thumbnails").apply { mkdirs() }
    val offlinePhotosDir: File = File(baseDir, "offline/photos").apply { mkdirs() }
    val offlineVideosDir: File = File(baseDir, "offline/videos").apply { mkdirs() }
    val tempDir: File = File(baseDir, "temp").apply { mkdirs() }

    /** Atomically writes an input stream to a target managed location via a temporary file. */
    fun writeStreamAtomically(
        itemId: String,
        fileName: String,
        type: MediaType,
        isPinned: Boolean,
        sourceStream: InputStream,
        expectedSizeBytes: Long? = null,
        onProgress: ((bytesRead: Long) -> Unit)? = null,
    ): File {
        val targetDir = when {
            isPinned && type == MediaType.VIDEO -> offlineVideosDir
            isPinned -> offlinePhotosDir
            type == MediaType.VIDEO -> cacheVideosDir
            else -> cachePhotosDir
        }

        val safeName = "${itemId}_${fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")}"
        val tempFile = File(tempDir, "${safeName}_${System.currentTimeMillis()}.tmp")
        val finalFile = File(targetDir, safeName)

        tempFile.outputStream().use { output ->
            val buffer = ByteArray(16 * 1024)
            var bytesRead: Int
            var totalRead = 0L
            while (sourceStream.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                totalRead += bytesRead
                onProgress?.invoke(totalRead)
            }
            output.flush()
        }

        if (expectedSizeBytes != null && expectedSizeBytes > 0 && tempFile.length() != expectedSizeBytes) {
            tempFile.delete()
            throw IllegalStateException("Download truncated: expected $expectedSizeBytes bytes, got ${tempFile.length()} bytes")
        }

        if (finalFile.exists()) finalFile.delete()
        if (!tempFile.renameTo(finalFile)) {
            tempFile.copyTo(finalFile, overwrite = true)
            tempFile.delete()
        }

        return finalFile
    }

    /** Moves a file between cache and offline storage when pinning / unpinning. */
    fun migratePinnedFile(currentPath: String, type: MediaType, pinned: Boolean): String? {
        val currentFile = File(currentPath)
        if (!currentFile.exists()) return null

        val targetDir = when {
            pinned && type == MediaType.VIDEO -> offlineVideosDir
            pinned -> offlinePhotosDir
            type == MediaType.VIDEO -> cacheVideosDir
            else -> cachePhotosDir
        }

        val targetFile = File(targetDir, currentFile.name)
        if (currentFile.absolutePath == targetFile.absolutePath) return targetFile.absolutePath

        if (currentFile.renameTo(targetFile)) {
            return targetFile.absolutePath
        } else {
            currentFile.copyTo(targetFile, overwrite = true)
            currentFile.delete()
            return targetFile.absolutePath
        }
    }

    /** Deletes a cached file safely, guaranteeing it is inside cache/ and not offline/ or device. */
    fun deleteCachedFileSafely(path: String): Boolean {
        val file = File(path)
        val isInsideCache = file.canonicalPath.startsWith(File(baseDir, "cache").canonicalPath)
        return if (isInsideCache && file.exists()) {
            file.delete()
        } else {
            false
        }
    }

    /** Clears all temporary cached files (leaves offline/ untouched). */
    fun clearAllTemporaryCache(): Pair<Long, Int> {
        var reclaimedBytes = 0L
        var count = 0
        listOf(cachePhotosDir, cacheVideosDir, cacheThumbnailsDir).forEach { dir ->
            dir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    val size = file.length()
                    if (file.delete()) {
                        reclaimedBytes += size
                        count++
                    }
                }
            }
        }
        return Pair(reclaimedBytes, count)
    }

    fun calculateDirectoryBytes(dir: File): Long =
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
