package com.personal.triptrail

import com.personal.triptrail.util.CloudRecycleStore
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class CloudRecycleStoreTest {
    @Test fun offlineDeleteIntentSurvivesRestartAndInterruptedWrite() {
        val directory = Files.createTempDirectory("triptrail-recycle").toFile()
        try {
            val file = File(directory, "recycle.json")
            val store = CloudRecycleStore(file)
            val intent = """[{"id":"test","kind":"trip","pending":true,"payload":{"title":"离线删除"}}]""".toByteArray()
            store.write(intent)
            File(directory, "recycle.json.tmp").writeText("interrupted")
            val restarted = CloudRecycleStore(file).read()
            assertTrue(restarted.getJSONObject(0).getBoolean("pending"))
            assertEquals("离线删除", restarted.getJSONObject(0).getJSONObject("payload").getString("title"))
            try { store.write("invalid".toByteArray()); fail("Invalid replacement must fail") } catch (_: org.json.JSONException) { }
            assertEquals(restarted.toString(), store.read().toString())
            store.write("[]".toByteArray())
            assertEquals(0, CloudRecycleStore(file).read().length())
        } finally { directory.deleteRecursively() }
    }
}
