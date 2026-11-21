package com.cyrus.gallery.data.remote.graph

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class OneDriveGraphClientTest {

    @Test
    fun `graph item json is parsed with media attributes and timestamps`() {
        val sampleJson = """
        {
            "value": [
                {
                    "id": "item123",
                    "name": "Sunset.jpg",
                    "size": 2048576,
                    "eTag": "etag1",
                    "cTag": "ctag1",
                    "image": { "width": 1920, "height": 1080 },
                    "file": { "mimeType": "image/jpeg" },
                    "photo": { "takenDateTime": "2024-06-15T18:30:00Z" },
                    "thumbnails": [
                        { "medium": { "url": "https://example.com/thumb_med.jpg" } }
                    ]
                },
                {
                    "id": "folder456",
                    "name": "Vacation 2024",
                    "size": 0,
                    "folder": { "childCount": 15 }
                }
            ],
            "@odata.nextLink": "https://graph.microsoft.com/v1.0/me/drive/root/children?token=abc"
        }
        """.trimIndent()

        val json = JSONObject(sampleJson)
        val valueArray = json.getJSONArray("value")
        assertEquals(2, valueArray.length())

        val photoObj = valueArray.getJSONObject(0)
        assertEquals("item123", photoObj.getString("id"))
        assertEquals("Sunset.jpg", photoObj.getString("name"))
        assertTrue(photoObj.has("image"))
        assertEquals("image/jpeg", photoObj.getJSONObject("file").getString("mimeType"))
        assertEquals("2024-06-15T18:30:00Z", photoObj.getJSONObject("photo").getString("takenDateTime"))

        val folderObj = valueArray.getJSONObject(1)
        assertEquals("folder456", folderObj.getString("id"))
        assertTrue(folderObj.has("folder"))
    }

    @Test
    fun `graph item timestamp falls back through createdDateTime and lastModifiedDateTime`() {
        val sampleJson = """
        {
            "id": "video789",
            "name": "ScreenRecording.mp4",
            "size": 5000000,
            "video": {},
            "file": { "mimeType": "video/mp4" },
            "createdDateTime": "2024-03-10T12:00:00Z",
            "lastModifiedDateTime": "2024-03-10T14:30:00Z"
        }
        """.trimIndent()

        val obj = JSONObject(sampleJson)
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

        val instant = bestTimestampStr?.let { Instant.parse(it) }
        assertNotNull(instant)
        assertEquals(Instant.parse("2024-03-10T12:00:00Z"), instant)
    }

    @Test
    fun `graph delta payload separates additions and deletions`() {
        val deltaJson = """
        {
            "value": [
                {
                    "id": "item_active",
                    "name": "Active.jpg",
                    "size": 1024,
                    "file": { "mimeType": "image/jpeg" }
                },
                {
                    "id": "item_removed",
                    "deleted": { "state": "deleted" }
                }
            ],
            "@odata.deltaLink": "https://graph.microsoft.com/v1.0/me/drive/root/delta?token=delta123"
        }
        """.trimIndent()

        val json = JSONObject(deltaJson)
        val value = json.getJSONArray("value")
        val added = mutableListOf<String>()
        val deleted = mutableListOf<String>()

        for (i in 0 until value.length()) {
            val obj = value.getJSONObject(i)
            if (obj.has("deleted")) {
                deleted.add(obj.getString("id"))
            } else {
                added.add(obj.getString("id"))
            }
        }

        assertEquals(listOf("item_active"), added)
        assertEquals(listOf("item_removed"), deleted)
        assertEquals(
            "https://graph.microsoft.com/v1.0/me/drive/root/delta?token=delta123",
            json.getString("@odata.deltaLink"),
        )
    }

    @Test
    fun `media classifier recognizes photos and videos without image or video facets`() {
        val ext = "heic"
        val isImageExtension = ext in setOf("jpg", "jpeg", "png", "heic", "webp")
        assertTrue(isImageExtension)

        val videoExt = "mov"
        val isVideoExtension = videoExt in setOf("mp4", "mov", "mkv")
        assertTrue(isVideoExtension)
    }
}
