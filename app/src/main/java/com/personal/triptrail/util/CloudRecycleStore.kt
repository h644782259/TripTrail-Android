package com.personal.triptrail.util

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONArray

/** Intent is durable before the repository removes the local record. */
internal class CloudRecycleStore(private val file: File) {
    fun read(): JSONArray = if (file.exists()) JSONArray(file.readText()) else JSONArray()
    fun write(bytes: ByteArray) {
        JSONArray(String(bytes)) // Reject incomplete data before touching the committed file.
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.outputStream().use { stream -> stream.write(bytes); stream.fd.sync() }
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
}
