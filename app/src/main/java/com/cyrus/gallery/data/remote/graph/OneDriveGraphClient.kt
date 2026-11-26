package com.cyrus.gallery.data.remote.graph

import android.net.Uri
import com.cyrus.gallery.data.auth.OneDriveAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class GraphDriveItem(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val eTag: String?,
    val cTag: String?,
    val isFolder: Boolean,
    val isImage: Boolean,
    val isVideo: Boolean,
    val mimeType: String,
    val createdAt: Instant?,
    val thumbnailUrl: String? = null,
    val downloadUrl: String? = null,
    val parentFolderId: String? = null,
)

data class GraphPagedResult(
    val items: List<GraphDriveItem>,
    val nextLink: String? = null,
)

data class GraphDeltaResult(
    val items: List<GraphDriveItem>,
    val deletedIds: List<String>,
    val nextLink: String? = null,
    val deltaLink: String? = null,
)

interface OneDriveGraphClient {
    suspend fun getDriveRootChildren(nextLink: String? = null): Result<GraphPagedResult>
    suspend fun getFolderChildren(folderId: String, nextLink: String? = null): Result<GraphPagedResult>
    suspend fun getDelta(deltaLinkOrToken: String? = null): Result<GraphDeltaResult>
    suspend fun deleteItem(itemId: String): Result<Unit>
    suspend fun uploadItem(fileName: String, bytes: ByteArray, mimeType: String): Result<GraphDriveItem>
    suspend fun uploadStream(
        fileName: String,
        contentLength: Long,
        mimeType: String,
        openStream: () -> InputStream?,
    ): Result<GraphDriveItem>
}

@Singleton
class OneDriveGraphClientImpl @Inject constructor(
    private val authManager: OneDriveAuthManager,
    private val httpClient: OkHttpClient,
) : OneDriveGraphClient {

    companion object {
        private const val GRAPH_BASE_URL = "https://graph.microsoft.com/v1.0"
        private const val SIMPLE_UPLOAD_MAX_BYTES = 4L * 1024 * 1024
        private const val UPLOAD_CHUNK_BYTES = 320 * 1024 * 10 // 3.2 MB (multiple of 320 KiB per Graph upload session spec)
        private const val DELTA_SELECT_FIELDS =
            "id,name,size,eTag,cTag,createdDateTime,lastModifiedDateTime,file,folder,root,image,video,photo,fileSystemInfo,@microsoft.graph.downloadUrl,parentReference"
        private const val CHILDREN_SELECT_FIELDS =
            "id,name,size,eTag,cTag,createdDateTime,lastModifiedDateTime,file,folder,root,image,video,photo,fileSystemInfo,@microsoft.graph.downloadUrl,thumbnails,parentReference"

        internal fun parsePagedResult(jsonString: String): GraphPagedResult {
            val json = JSONObject(jsonString)
            val valueArray = json.optJSONArray("value") ?: return GraphPagedResult(emptyList())
            val items = mutableListOf<GraphDriveItem>()

            for (i in 0 until valueArray.length()) {
                val obj = valueArray.getJSONObject(i)
                parseDriveItem(obj)?.let { items.add(it) }
            }

            val nextLink = json.optString("@odata.nextLink").takeIf { it.isNotEmpty() }
            return GraphPagedResult(items = items, nextLink = nextLink)
        }

        internal fun parseDeltaResult(jsonString: String): GraphDeltaResult {
            val json = JSONObject(jsonString)
            val valueArray = json.optJSONArray("value") ?: return GraphDeltaResult(emptyList(), emptyList())
            val items = mutableListOf<GraphDriveItem>()
            val deletedIds = mutableListOf<String>()

            for (i in 0 until valueArray.length()) {
                val obj = valueArray.getJSONObject(i)
                val isDeleted = obj.has("deleted") || obj.has("@removed")
                if (isDeleted) {
                    obj.optString("id").takeIf { it.isNotEmpty() }?.let { deletedIds.add(it) }
                } else {
                    parseDriveItem(obj)?.let { items.add(it) }
                }
            }

            val nextLink = json.optString("@odata.nextLink").takeIf { it.isNotEmpty() }
            val deltaLink = json.optString("@odata.deltaLink").takeIf { it.isNotEmpty() }
            return GraphDeltaResult(items = items, deletedIds = deletedIds, nextLink = nextLink, deltaLink = deltaLink)
        }

        internal fun parseDriveItem(obj: JSONObject): GraphDriveItem? {
            val id = obj.optString("id").takeIf { it.isNotEmpty() } ?: return null
            val name = obj.optString("name", "")
            val parentRef = obj.optJSONObject("parentReference")
            val parentFolderId = parentRef?.optString("id")?.takeIf { it.isNotEmpty() }

            // Microsoft Graph /delta returns the drive's root DriveItem itself; exclude it so
            // top-level folders remain at root instead of being nested under a synthetic "root" album.
            val isDriveRoot = obj.has("root") ||
                (obj.has("folder") && parentFolderId == null && name.equals("root", ignoreCase = true))
            if (isDriveRoot) return null

            val size = obj.optLong("size", 0L)
            val eTag = obj.optString("eTag").takeIf { it.isNotEmpty() }
            val cTag = obj.optString("cTag").takeIf { it.isNotEmpty() }
            val isFolder = obj.has("folder")

            val fileObj = obj.optJSONObject("file")
            val rawMimeType = fileObj?.optString("mimeType")

            val ext = name.substringAfterLast('.', "").lowercase()
            val isImageExtension = ext in setOf(
                "jpg", "jpeg", "png", "heic", "heif", "webp", "gif", "bmp", "dng", "cr2", "nef", "arw", "tif", "tiff"
            )
            val isVideoExtension = ext in setOf(
                "mp4", "mov", "m4v", "mkv", "webm", "3gp", "avi", "wmv", "flv", "ts", "mts"
            )

            // Comprehensive media classification: check facets, MIME types, and file extensions
            val isImage = !isFolder && (
                obj.has("image") ||
                obj.has("photo") ||
                rawMimeType?.startsWith("image/") == true ||
                isImageExtension
            )

            val isVideo = !isFolder && !isImage && (
                obj.has("video") ||
                rawMimeType?.startsWith("video/") == true ||
                isVideoExtension
            )

            val mimeType = rawMimeType ?: when {
                isImage -> if (ext.isNotEmpty()) "image/$ext" else "image/jpeg"
                isVideo -> if (ext.isNotEmpty()) "video/$ext" else "video/mp4"
                isFolder -> "vnd.android.cursor.item/directory"
                else -> "application/octet-stream"
            }

            // Extract best timestamp: photo.takenDateTime > fileSystemInfo.createdDateTime > createdDateTime > fileSystemInfo.lastModifiedDateTime > lastModifiedDateTime
            val photoObj = obj.optJSONObject("photo")
            val takenTimeStr = photoObj?.optString("takenDateTime")?.takeIf { it.isNotBlank() }
            val fsObj = obj.optJSONObject("fileSystemInfo")
            val fsCreatedStr = fsObj?.optString("createdDateTime")?.takeIf { it.isNotBlank() }
            val topCreatedStr = obj.optString("createdDateTime").takeIf { it.isNotBlank() }
            val fsModifiedStr = fsObj?.optString("lastModifiedDateTime")?.takeIf { it.isNotBlank() }
            val topModifiedStr = obj.optString("lastModifiedDateTime").takeIf { it.isNotBlank() }

            val bestTimestampStr = takenTimeStr
                ?: fsCreatedStr
                ?: topCreatedStr
                ?: fsModifiedStr
                ?: topModifiedStr

            val createdAt = try {
                bestTimestampStr?.let { Instant.parse(it) }
            } catch (_: Exception) {
                null
            }

            // Thumbnails - prefer 'large' for crisp, high-quality rendering
            var thumbnailUrl: String? = null
            val thumbsArray = obj.optJSONArray("thumbnails")
            if (thumbsArray != null && thumbsArray.length() > 0) {
                val thumbSet = thumbsArray.getJSONObject(0)
                val large = thumbSet.optJSONObject("large")
                val medium = thumbSet.optJSONObject("medium")
                thumbnailUrl = (large ?: medium)?.optString("url")
            }

            val downloadUrl = obj.optString("@microsoft.graph.downloadUrl").takeIf { it.isNotEmpty() }

            // High-quality thumbnail: prefer expanded 'large' preview (available from /children calls),
            // then the stable authenticated Graph thumbnail endpoint. Never fall back to the expiring
            // pre-signed @microsoft.graph.downloadUrl here — /delta doesn't expand thumbnails, so for
            // delta items the downloadUrl would be used as the thumbnail, loading full-resolution files
            // in the grid and breaking thumbnails once the pre-signed URL expires (~1 hour).
            // The downloadUrl is still stored in GraphDriveItem.downloadUrl for use as highResUrl.
            val effectiveThumbnailUrl = thumbnailUrl
                ?: "$GRAPH_BASE_URL/me/drive/items/$id/thumbnails/0/large/content"

            return GraphDriveItem(
                id = id,
                name = name,
                sizeBytes = size,
                eTag = eTag,
                cTag = cTag,
                isFolder = isFolder,
                isImage = isImage,
                isVideo = isVideo,
                mimeType = mimeType,
                createdAt = createdAt,
                thumbnailUrl = effectiveThumbnailUrl,
                downloadUrl = downloadUrl,
                parentFolderId = parentFolderId,
            )
        }
    }

    private suspend fun executeGet(url: String): Result<String> = withContext(Dispatchers.IO) {
        val token = authManager.getAccessToken()
            ?: return@withContext Result.failure(IllegalStateException("Not authenticated with Microsoft account"))

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/json")
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    val msg = response.body?.string() ?: response.message
                    return@withContext Result.failure(IOException("Graph API error $code: $msg"))
                }
                val body = response.body?.string() ?: ""
                Result.success(body)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getDriveRootChildren(nextLink: String?): Result<GraphPagedResult> {
        val url = nextLink
            ?: "$GRAPH_BASE_URL/me/drive/root/children?\$select=$CHILDREN_SELECT_FIELDS&\$expand=thumbnails&\$top=100"
        return executeGet(url).map { parsePagedResult(it) }
    }

    override suspend fun getFolderChildren(folderId: String, nextLink: String?): Result<GraphPagedResult> {
        val url = nextLink
            ?: "$GRAPH_BASE_URL/me/drive/items/$folderId/children?\$select=$CHILDREN_SELECT_FIELDS&\$expand=thumbnails&\$top=100"
        return executeGet(url).map { parsePagedResult(it) }
    }

    override suspend fun getDelta(deltaLinkOrToken: String?): Result<GraphDeltaResult> {
        val url = when {
            deltaLinkOrToken == null -> "$GRAPH_BASE_URL/me/drive/root/delta?\$select=$DELTA_SELECT_FIELDS"
            deltaLinkOrToken.startsWith("http") -> deltaLinkOrToken
            else -> "$GRAPH_BASE_URL/me/drive/root/delta?token=$deltaLinkOrToken&\$select=$DELTA_SELECT_FIELDS"
        }
        return executeGet(url).map { parseDeltaResult(it) }
    }

    override suspend fun deleteItem(itemId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val token = authManager.getAccessToken()
            ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
        val request = Request.Builder()
            .url("$GRAPH_BASE_URL/me/drive/items/$itemId")
            .delete()
            .addHeader("Authorization", "Bearer $token")
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 204 || response.code == 404) {
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("Graph delete failed: ${response.code} ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadItem(fileName: String, bytes: ByteArray, mimeType: String): Result<GraphDriveItem> =
        uploadStream(fileName, bytes.size.toLong(), mimeType) { ByteArrayInputStream(bytes) }

    override suspend fun uploadStream(
        fileName: String,
        contentLength: Long,
        mimeType: String,
        openStream: () -> InputStream?,
    ): Result<GraphDriveItem> = withContext(Dispatchers.IO) {
        val token = authManager.getAccessToken()
            ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
        val encodedName = Uri.encode(fileName)

        // Resolve exact byte size without loading into RAM when caller didn't know contentLength
        val totalBytes = if (contentLength > 0L) contentLength else {
            val probe = openStream() ?: return@withContext Result.failure(IOException("Could not open upload stream"))
            probe.use { input ->
                val buf = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val r = input.read(buf)
                    if (r == -1) break
                    total += r
                }
                total
            }
        }

        if (totalBytes <= SIMPLE_UPLOAD_MAX_BYTES) {
            return@withContext executeSimpleUpload(encodedName, token, mimeType, totalBytes, openStream)
        }
        executeChunkedUpload(encodedName, token, totalBytes, openStream)
    }

    private fun executeSimpleUpload(
        encodedName: String,
        token: String,
        mimeType: String,
        totalBytes: Long,
        openStream: () -> InputStream?,
    ): Result<GraphDriveItem> {
        val mediaType = mimeType.toMediaTypeOrNull()
        val body = object : RequestBody() {
            override fun contentType() = mediaType
            override fun contentLength() = totalBytes
            override fun writeTo(sink: BufferedSink) {
                val stream = openStream() ?: throw IOException("Could not open upload stream")
                stream.use { input ->
                    val buffer = ByteArray(16 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        sink.write(buffer, 0, read)
                    }
                }
            }
        }
        val request = Request.Builder()
            .url("$GRAPH_BASE_URL/me/drive/root:/$encodedName:/content")
            .put(body)
            .addHeader("Authorization", "Bearer $token")
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(IOException("Graph upload failed: ${response.code} ${response.message}"))
                }
                val bodyStr = response.body?.string() ?: ""
                val driveItem = parseDriveItem(JSONObject(bodyStr))
                    ?: return Result.failure(IOException("Could not parse uploaded item"))
                Result.success(driveItem)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun executeChunkedUpload(
        encodedName: String,
        token: String,
        totalBytes: Long,
        openStream: () -> InputStream?,
    ): Result<GraphDriveItem> {
        val sessionBody = """{"item":{"@microsoft.graph.conflictBehavior":"rename"}}"""
            .toRequestBody("application/json".toMediaTypeOrNull())
        val sessionReq = Request.Builder()
            .url("$GRAPH_BASE_URL/me/drive/root:/$encodedName:/createUploadSession")
            .post(sessionBody)
            .addHeader("Authorization", "Bearer $token")
            .build()

        val uploadUrl = try {
            httpClient.newCall(sessionReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return Result.failure(IOException("Create upload session failed: ${resp.code} ${resp.message}"))
                }
                JSONObject(resp.body?.string() ?: "").optString("uploadUrl").takeIf { it.isNotEmpty() }
                    ?: return Result.failure(IOException("Missing uploadUrl from Graph session"))
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val stream = openStream() ?: return Result.failure(IOException("Could not open upload stream"))
        return try {
            stream.use { input ->
                val buffer = ByteArray(UPLOAD_CHUNK_BYTES)
                var offset = 0L
                while (offset < totalBytes) {
                    var filled = 0
                    while (filled < buffer.size && offset + filled < totalBytes) {
                        val read = input.read(buffer, filled, buffer.size - filled)
                        if (read == -1) break
                        filled += read
                    }
                    if (filled == 0) break

                    val end = offset + filled - 1
                    val chunkReq = Request.Builder()
                        .url(uploadUrl)
                        .put(buffer.toRequestBody("application/octet-stream".toMediaTypeOrNull(), 0, filled))
                        .addHeader("Content-Range", "bytes $offset-$end/$totalBytes")
                        .build()

                    httpClient.newCall(chunkReq).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            return Result.failure(IOException("Chunk upload failed: ${resp.code} ${resp.message}"))
                        }
                        if (resp.code == 200 || resp.code == 201) {
                            val driveItem = parseDriveItem(JSONObject(resp.body?.string() ?: ""))
                                ?: return Result.failure(IOException("Could not parse uploaded item"))
                            return Result.success(driveItem)
                        }
                    }
                    offset += filled
                }
                Result.failure(IOException("Upload ended before completion"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
