package com.personal.triptrail.util

import android.content.Context
import android.net.Uri
import com.personal.triptrail.data.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class TripBackupService(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    fun write(data: AppData, output: OutputStream) {
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("triptrail-data.json"))
            zip.write(json.encodeToString(data).toByteArray())
            zip.closeEntry()
            allMedia(data).distinctBy { it.id }.forEach { media ->
                openMedia(media.localUri)?.use { input ->
                    val suffix = Uri.parse(media.localUri).lastPathSegment?.substringAfterLast('.', "bin") ?: "bin"
                    zip.putNextEntry(ZipEntry("media/${media.id}.$suffix"))
                    input.copyTo(zip)
                    zip.closeEntry()
                }
            }
        }
    }

    fun read(input: InputStream): AppData {
        val restoreDir = File(context.filesDir, "media").apply { mkdirs() }
        var data: AppData? = null
        val restored = mutableMapOf<String, String>()
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when {
                    entry.name == "triptrail-data.json" -> {
                        val buffer = ByteArrayOutputStream()
                        zip.copyTo(buffer)
                        data = json.decodeFromString(buffer.toString(Charsets.UTF_8.name()))
                    }
                    entry.name.startsWith("media/") && !entry.isDirectory -> {
                        val sourceName = entry.name.substringAfterLast('/')
                        val id = sourceName.substringBeforeLast('.')
                        val suffix = sourceName.substringAfterLast('.', "bin")
                        val target = File(restoreDir, "$id-restored.$suffix")
                        target.outputStream().use { zip.copyTo(it) }
                        restored[id] = Uri.fromFile(target).toString()
                    }
                }
                zip.closeEntry()
            }
        }
        return requireNotNull(data) { "备份中缺少旅行数据" }.remapMedia(restored)
    }

    private fun openMedia(value: String): InputStream? {
        val uri = Uri.parse(value)
        return if (uri.scheme == "file") uri.path?.let { File(it).takeIf(File::exists)?.inputStream() } else context.contentResolver.openInputStream(uri)
    }

    private fun allMedia(data: AppData): List<MediaReference> =
        data.trips.flatMap { it.days }.flatMap { it.items }.flatMap { it.media } +
            data.stories.flatMap { it.days }.flatMap { it.entries }.flatMap { it.media }

    private fun AppData.remapMedia(map: Map<String, String>) = copy(
        trips = trips.map { trip -> trip.copy(days = trip.days.map { day -> day.copy(items = day.items.map { item ->
            item.copy(media = item.media.map { media -> map[media.id]?.let { media.copy(localUri = it) } ?: media })
        }) }) },
        stories = stories.map { story -> story.copy(days = story.days.map { day -> day.copy(entries = day.entries.map { entry ->
            entry.copy(media = entry.media.map { media -> map[media.id]?.let { media.copy(localUri = it) } ?: media })
        }) }) }
    )
}
