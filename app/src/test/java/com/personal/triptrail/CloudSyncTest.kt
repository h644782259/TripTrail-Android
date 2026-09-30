package com.personal.triptrail

import com.personal.triptrail.data.*
import com.personal.triptrail.util.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CloudSyncTest {
    @Test fun revisionMatrixPreventsLostOfflineEdits() {
        assertEquals(CloudSyncDecision.UPLOAD, CloudSyncDecision.choose(true, 0, null))
        assertEquals(CloudSyncDecision.UPLOAD, CloudSyncDecision.choose(true, 3, 3))
        assertEquals(CloudSyncDecision.DOWNLOAD, CloudSyncDecision.choose(false, 3, 4))
        assertEquals(CloudSyncDecision.CONFLICT, CloudSyncDecision.choose(true, 3, 4))
        assertEquals(CloudSyncDecision.MISSING, CloudSyncDecision.choose(false, 3, null))
        assertEquals(CloudSyncDecision.UNCHANGED, CloudSyncDecision.choose(false, 3, 3))
    }
    @Test fun mediaCacheLocationDoesNotBecomeAnEdit() {
        val a = JSONObject("""{"media":[{"id":"a","localIdentifier":"content://photos/one","caption":"旅途"}]}""")
        val b = JSONObject("""{"media":[{"caption":"旅途","id":"a","localIdentifier":"file:///cache/photo.jpg","cloudPath":"abc/hash.jpg"}]}""")
        assertEquals(CloudJson.fingerprint(a), CloudJson.fingerprint(b))
        b.getJSONArray("media").getJSONObject(0).put("caption", "新说明")
        assertNotEquals(CloudJson.fingerprint(a), CloudJson.fingerprint(b))
    }
    @Test fun cloudWireUsesIosDatesEnumsVouchersAndMedia() {
        val item = ItineraryItem(title = "住宿", startTime = 1_800_000_000_000, endTime = 1_800_003_600_000,
            category = PlaceCategory.HOTEL, locationMode = ArrangementLocationMode.ROUTE, originName = "成都", destinationName = "康定",
            isFixedTime = true, vouchers = listOf(TravelVoucher(name = "票据", mimeType = "image/png", dataBase64 = "YWJj")),
            media = listOf(MediaReference(localUri = "file:///media/a.jpg")))
        val trip = Trip(title = "跨端旅程", destination = "四川", startDate = item.startTime, endDate = item.endTime,
            days = listOf(TripDay(date = item.startTime, items = listOf(item))))
        val record = TripFileService.cloudRecords(AppData(trips = listOf(trip))).single()
        assertEquals(item.startTime, record.payload.getLong("startDate"))
        val obj = record.payload.getJSONArray("days").getJSONObject(0).getJSONArray("items").getJSONObject(0)
        assertEquals("起终点", obj.getString("locationModeRaw"))
        assertEquals("未开始", obj.getString("executionStatusRaw"))
        assertEquals("YWJj", obj.getJSONArray("vouchers").getJSONObject(0).getString("dataBase64"))
        assertEquals("file:///media/a.jpg", obj.getJSONArray("media").getJSONObject(0).getString("localIdentifier"))
        val envelope = JSONObject().put("formatVersion", 1).put("trips", org.json.JSONArray().put(record.payload))
        val restored = TripFileService.importBackup(envelope.toString()).trips.single()
        assertTrue(restored.allItems.single().isFixedTime)
        assertEquals("康定", restored.allItems.single().destinationName)
        assertEquals(item.vouchers, restored.allItems.single().vouchers)
    }
    @Test fun rejectWrongCloudIdentity() {
        val record = JSONObject("""{"id":"one","kind":"trip","revision":1,"payload":{"id":"two"}}""")
        assertThrows(IllegalArgumentException::class.java) { CloudRemoteRecord(record) }
    }
}
