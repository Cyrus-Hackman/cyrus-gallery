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
}
