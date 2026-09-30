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

    data class PreparedBackup(val file: File, val skippedCount: Int)

    fun prepareBackup(data: AppData): PreparedBackup {
        val directory = File(context.cacheDir, "triptrail-import-backup-stage-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val output = File.createTempFile("triptrail-import-backup-export-", ".triptrailbackup", context.cacheDir)
        try {
            val sources = mutableMapOf<String, File>()
            val skipped = mutableSetOf<String>()
            data.backupMediaReferences().distinctBy { it.id }.forEach { media ->
                val target = File(directory, media.id)
                try {
                    val input = openMedia(media.localUri) ?: throw java.io.FileNotFoundException()
                    input.use { source -> target.outputStream().use { source.copyTo(it) } }
                    require(target.length() > 0)
                    sources[media.id] = target
                } catch (failure: Exception) {
                    target.delete()
                    skipped += media.id
                }
            }
            val filtered = data.copy(
                trips = data.trips.map { trip -> trip.copy(days = trip.days.map { day -> day.copy(items = day.items.map { item -> item.copy(media = item.media.filterNot { it.id in skipped }) }) }) },
                stories = data.stories.map { story -> story.copy(coverMedia = story.coverMedia?.takeUnless { it.id in skipped }, days = story.days.map { day -> day.copy(entries = day.entries.map { entry -> entry.copy(media = entry.media.filterNot { it.id in skipped }) }) }) },
                favorites = data.favorites.map { it.copy(media = it.media.filterNot { media -> media.id in skipped }) }
            )
            output.outputStream().use { stream ->
                ZipOutputStream(stream.buffered()).use { zip ->
                    zip.putNextEntry(ZipEntry("triptrail-data.json"))
                    zip.write(json.encodeToString(filtered).toByteArray())
                    zip.closeEntry()
                    filtered.backupMediaReferences().distinctBy { it.id }.forEach { media ->
                        val suffix = Uri.parse(media.localUri).lastPathSegment?.substringAfterLast('.', "bin") ?: "bin"
                        zip.putNextEntry(ZipEntry("media/${media.id}.$suffix"))
                        sources.getValue(media.id).inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
            return PreparedBackup(output, skipped.size)
        } catch (failure: Throwable) { output.delete(); throw failure }
        finally { directory.deleteRecursively() }
    }

    fun write(data: AppData, output: OutputStream) {
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("triptrail-data.json"))
            zip.write(json.encodeToString(data).toByteArray())
            zip.closeEntry()
            data.backupMediaReferences().distinctBy { it.id }.forEach { media ->
                openMedia(media.localUri)?.use { input ->
                    val suffix = Uri.parse(media.localUri).lastPathSegment?.substringAfterLast('.', "bin") ?: "bin"
                    zip.putNextEntry(ZipEntry("media/${media.id}.$suffix"))
                    input.copyTo(zip)
                    zip.closeEntry()
                }
            }
        }
    }

    fun read(input: InputStream): AppData = prepareRead(input).content

    fun prepareRead(input: InputStream): PreparedImport<AppData> {
        val buffered = if (input is java.io.BufferedInputStream) input else input.buffered()
        if (PortablePackageCodec.hasMagic(buffered)) {
            return PortablePackageService(context).prepareBackup(buffered)
        }
        buffered.mark(4)
        val signature = ByteArray(4)
        val signatureSize = buffered.read(signature)
        buffered.reset()
        if (signatureSize < 2 || signature[0] != 'P'.code.toByte() || signature[1] != 'K'.code.toByte()) {
            return PreparedImport(TripFileService.importBackup(buffered.reader(Charsets.UTF_8).use { it.readText() }))
        }

        val restoreDir = File(context.filesDir, "media").apply { mkdirs() }
        var data: AppData? = null
        val restored = mutableMapOf<String, String>()
        val restoredFiles = mutableListOf<File>()
        return try {
            ZipInputStream(buffered).use { zip ->
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
                            val target = File(restoreDir, "$id-restored-${java.util.UUID.randomUUID()}.$suffix")
                            target.outputStream().use { zip.copyTo(it) }
                            restoredFiles += target
                            restored[id] = Uri.fromFile(target).toString()
                        }
                    }
                    zip.closeEntry()
                }
            }
            PreparedImport(requireNotNull(data) { "备份中缺少旅行数据" }.remapMedia(restored), restoredFiles)
        } catch (error: Throwable) {
            restoredFiles.forEach(File::delete)
            throw error
        }
    }

    private fun openMedia(value: String): InputStream? {
        val uri = Uri.parse(value)
        return if (uri.scheme == "file") uri.path?.let { File(it).takeIf(File::exists)?.inputStream() } else context.contentResolver.openInputStream(uri)
    }

    private fun AppData.remapMedia(map: Map<String, String>) = copy(
        trips = trips.map { trip -> trip.copy(days = trip.days.map { day -> day.copy(items = day.items.map { item ->
            item.copy(media = item.media.map { media -> map[media.id]?.let { media.copy(localUri = it) } ?: media })
        }) }) },
        stories = stories.map { story -> story.copy(
            coverMedia = story.coverMedia?.let { media -> map[media.id]?.let { media.copy(localUri = it) } ?: media },
            days = story.days.map { day -> day.copy(entries = day.entries.map { entry ->
            entry.copy(media = entry.media.map { media -> map[media.id]?.let { media.copy(localUri = it) } ?: media })
        }) }) },
        favorites = favorites.map { favorite -> favorite.copy(media = favorite.media.map { media -> map[media.id]?.let { media.copy(localUri = it) } ?: media }) }
    )
}

internal fun AppData.backupMediaReferences(): List<MediaReference> =
    trips.flatMap { it.days }.flatMap { it.items }.flatMap { it.media } +
        stories.flatMap { it.days }.flatMap { it.entries }.flatMap { it.media } +
        stories.mapNotNull { it.coverMedia } + favorites.flatMap { it.media }
