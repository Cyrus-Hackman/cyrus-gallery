package com.cyrus.gallery.data.remote.graph

import com.cyrus.gallery.data.auth.OneDriveAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
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
}

@Singleton
class OneDriveGraphClientImpl @Inject constructor(
    private val authManager: OneDriveAuthManager,
    private val httpClient: OkHttpClient,
) : OneDriveGraphClient {

    companion object {
        private const val GRAPH_BASE_URL = "https://graph.microsoft.com/v1.0"
        private const val DELTA_SELECT_FIELDS =
            "id,name,size,eTag,cTag,createdDateTime,lastModifiedDateTime,file,folder,image,video,photo,fileSystemInfo,@microsoft.graph.downloadUrl,parentReference"
        private const val CHILDREN_SELECT_FIELDS =
            "id,name,size,eTag,cTag,createdDateTime,lastModifiedDateTime,file,folder,image,video,photo,fileSystemInfo,@microsoft.graph.downloadUrl,thumbnails,parentReference"
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

    override suspend fun uploadItem(fileName: String, bytes: ByteArray, mimeType: String): Result<GraphDriveItem> = withContext(Dispatchers.IO) {
        val token = authManager.getAccessToken()
            ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
        val mediaType = mimeType.toMediaTypeOrNull()
        val body = bytes.toRequestBody(mediaType)
        val url = "$GRAPH_BASE_URL/me/drive/root:/$fileName:/content"
        val request = Request.Builder()
            .url(url)
            .put(body)
            .addHeader("Authorization", "Bearer $token")
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Graph upload failed: ${response.code} ${response.message}"))
                }
                val bodyStr = response.body?.string() ?: ""
                val driveItem = parseDriveItem(JSONObject(bodyStr))
                    ?: return@withContext Result.failure(IOException("Could not parse uploaded item"))
                Result.success(driveItem)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parsePagedResult(jsonString: String): GraphPagedResult {
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

    private fun parseDeltaResult(jsonString: String): GraphDeltaResult {
        val json = JSONObject(jsonString)
        val valueArray = json.optJSONArray("value") ?: return GraphDeltaResult(emptyList(), emptyList())
        val items = mutableListOf<GraphDriveItem>()
        val deletedIds = mutableListOf<String>()

        for (i in 0 until valueArray.length()) {
            val obj = valueArray.getJSONObject(i)
            if (obj.has("deleted")) {
                deletedIds.add(obj.optString("id"))
            } else {
                parseDriveItem(obj)?.let { items.add(it) }
            }
        }

        val nextLink = json.optString("@odata.nextLink").takeIf { it.isNotEmpty() }
        val deltaLink = json.optString("@odata.deltaLink").takeIf { it.isNotEmpty() }
        return GraphDeltaResult(items = items, deletedIds = deletedIds, nextLink = nextLink, deltaLink = deltaLink)
    }

    private fun parseDriveItem(obj: JSONObject): GraphDriveItem? {
        val id = obj.optString("id").takeIf { it.isNotEmpty() } ?: return null
        val name = obj.optString("name", "")
        val size = obj.optLong("size", 0L)
        val eTag = obj.optString("eTag").takeIf { it.isNotEmpty() }
        val cTag = obj.optString("cTag").takeIf { it.isNotEmpty() }
        val isFolder = obj.has("folder")
        val isImage = obj.has("image")
        val isVideo = obj.has("video")

        val fileObj = obj.optJSONObject("file")
        val mimeType = fileObj?.optString("mimeType") ?: when {
            isImage -> "image/*"
            isVideo -> "video/*"
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
        val parentRef = obj.optJSONObject("parentReference")
        val parentFolderId = parentRef?.optString("id")

        // High-quality thumbnail: prefer expanded 'large' preview, then downloadUrl (for images), then Graph large thumbnail endpoint
        val effectiveThumbnailUrl = thumbnailUrl
            ?: (if (isImage) downloadUrl else null)
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
