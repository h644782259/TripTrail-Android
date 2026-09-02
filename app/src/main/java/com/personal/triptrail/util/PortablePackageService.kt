package com.personal.triptrail.util

import android.content.Context
import android.net.Uri
import com.personal.triptrail.data.AppData
import com.personal.triptrail.data.MediaReference
import com.personal.triptrail.data.TravelStory
import com.personal.triptrail.data.Trip
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.UUID

/** Reads the versioned media container produced by TripTrail on iOS. */
class PortablePackageService(private val context: Context) {
    fun readBackup(input: InputStream): AppData = prepareBackup(input).content

    fun prepareBackup(input: InputStream): PreparedImport<AppData> =
        read(input, PortablePackageKind.BACKUP, TripFileService::importBackup)
            .let { result -> PreparedImport(result.content.remapMedia(result.mediaUris), result.importedFiles) }

    fun readShared(input: InputStream): Pair<Trip?, TravelStory?> {
        return prepareShared(input).content
    }

    fun prepareShared(input: InputStream): PreparedImport<Pair<Trip?, TravelStory?>> {
        val buffered = if (input is BufferedInputStream) input else input.buffered()
        if (!PortablePackageCodec.hasMagic(buffered)) {
            return PreparedImport(TripFileService.importShared(buffered.reader(Charsets.UTF_8).use { it.readText() }))
        }
        return read(buffered, PortablePackageKind.SHARED_JOURNEY, TripFileService::importShared)
            .let { result -> PreparedImport(result.content.remapMedia(result.mediaUris), result.importedFiles) }
    }

    private fun <T> read(
        input: InputStream,
        expectedKind: PortablePackageKind,
        decodeContent: (String) -> T,
    ): PackageRead<T> {
        val buffered = if (input is BufferedInputStream) input else input.buffered()
        val envelope = PortablePackageCodec.readEnvelope(buffered)
        require(envelope.kind == expectedKind) { "文件内容类型与当前操作不匹配" }

        // Parse and validate all structured data before placing any media in permanent app storage.
        val decoded = decodeContent(envelope.content)
        val referencedIDs = when (decoded) {
            is AppData -> decoded.backupMediaReferences().map(MediaReference::id).toSet()
            is Pair<*, *> -> {
                val trip = decoded.first as? Trip
                val story = decoded.second as? TravelStory
                (trip?.days.orEmpty().flatMap { it.items }.flatMap { it.media } +
                    story?.days.orEmpty().flatMap { it.entries }.flatMap { it.media } +
                    listOfNotNull(story?.coverMedia)).map(MediaReference::id).toSet()
            }
            else -> emptySet()
        }
        require(referencedIDs == envelope.media.map(PortableMediaEntry::referenceID).toSet()) {
            "媒体清单与数据引用不一致"
        }

        val staging = File(context.cacheDir, "triptrail-import-${UUID.randomUUID()}").apply { mkdirs() }
        val permanent = File(context.filesDir, "media").apply { mkdirs() }
        val committedFiles = mutableListOf<File>()
        return try {
            val staged = PortablePackageCodec.extractPayload(buffered, envelope.media, staging)
            val committed = mutableMapOf<String, String>()
            staged.forEach { (entry, source) ->
                val extension = source.extension.ifBlank { "bin" }
                val target = File(permanent, "${entry.referenceID}-import-${UUID.randomUUID()}.$extension")
                source.inputStream().use { sourceInput -> target.outputStream().use(sourceInput::copyTo) }
                committedFiles += target
                committed[entry.referenceID] = Uri.fromFile(target).toString()
            }
            PackageRead(decoded, committed, committedFiles)
        } catch (error: Throwable) {
            committedFiles.forEach(File::delete)
            throw error
        } finally {
            staging.deleteRecursively()
        }
    }
}

class PreparedImport<T> internal constructor(
    val content: T,
    private val importedFiles: List<File> = emptyList(),
) {
    /** Removes media copied while the confirmation dialog was being prepared. */
    fun discard() {
        importedFiles.forEach(File::delete)
    }
}

private data class PackageRead<T>(
    val content: T,
    val mediaUris: Map<String, String>,
    val importedFiles: List<File>,
)

internal enum class PortablePackageKind { BACKUP, SHARED_JOURNEY }

internal data class PortableMediaEntry(
    val referenceID: String,
    val kindRaw: String,
    val originalFilename: String,
    val byteCount: Long,
)

internal data class PortableEnvelope(
    val kind: PortablePackageKind,
    val content: String,
    val media: List<PortableMediaEntry>,
)

internal object PortablePackageCodec {
    val magic: ByteArray = "TRIPTRAILPKG1\n".toByteArray(Charsets.UTF_8)
    private const val FORMAT = "triptrail.portable-package"
    private const val VERSION = 1
    private const val MAX_MANIFEST_BYTES = 64 * 1024 * 1024

    fun hasMagic(input: BufferedInputStream): Boolean {
        input.mark(magic.size)
        val prefix = input.readExactOrNull(magic.size)
        input.reset()
        return prefix?.contentEquals(magic) == true
    }

    fun readEnvelope(input: BufferedInputStream): PortableEnvelope {
        require(input.readExact(magic.size).contentEquals(magic)) { "这不是有效的旅迹媒体包" }
        val manifestLength = ByteBuffer.wrap(input.readExact(Long.SIZE_BYTES))
            .order(ByteOrder.BIG_ENDIAN)
            .long
        require(manifestLength in 1..MAX_MANIFEST_BYTES.toLong()) { "旅迹媒体包清单大小无效" }
        val manifest = JSONObject(input.readExact(manifestLength.toInt()).toString(Charsets.UTF_8))
        require(manifest.optString("format") == FORMAT) { "这不是有效的旅迹媒体包" }
        require(manifest.optInt("formatVersion") == VERSION) {
            "暂不支持此媒体包版本：${manifest.optInt("formatVersion")}"
        }
        val kind = when (manifest.optString("kind")) {
            "backup" -> PortablePackageKind.BACKUP
            "sharedJourney" -> PortablePackageKind.SHARED_JOURNEY
            else -> error("旅迹媒体包类型无效")
        }
        val content = runCatching {
            Base64.getDecoder().decode(manifest.getString("contentData")).toString(Charsets.UTF_8)
        }.getOrElse { error("旅迹媒体包内容无效") }
        val mediaArray = manifest.optJSONArray("media") ?: error("旅迹媒体包缺少媒体清单")
        val media = (0 until mediaArray.length()).map { index ->
            val entry = mediaArray.getJSONObject(index)
            PortableMediaEntry(
                referenceID = entry.getString("referenceID"),
                kindRaw = entry.optString("kindRaw", "image"),
                originalFilename = entry.optString("originalFilename", "media.bin"),
                byteCount = entry.getLong("byteCount"),
            ).also { require(it.referenceID.isNotBlank() && it.byteCount > 0) { "媒体清单无效" } }
        }
        require(media.map(PortableMediaEntry::referenceID).distinct().size == media.size) { "媒体清单包含重复项目" }
        return PortableEnvelope(kind, content, media)
    }

    fun extractPayload(
        input: InputStream,
        media: List<PortableMediaEntry>,
        directory: File,
    ): List<Pair<PortableMediaEntry, File>> {
        val result = media.map { entry ->
            val extension = entry.originalFilename.substringAfterLast('.', "bin")
                .lowercase().takeIf { it.matches(Regex("[a-z0-9]{1,10}")) } ?: "bin"
            val target = File(directory, "${entry.referenceID}.$extension")
            target.outputStream().use { output ->
                var remaining = entry.byteCount
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (remaining > 0) {
                    val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (count <= 0) throw EOFException("媒体“${entry.originalFilename}”数据不完整")
                    output.write(buffer, 0, count)
                    remaining -= count
                }
            }
            entry to target
        }
        require(input.read() == -1) { "旅迹媒体包包含未识别的数据" }
        return result
    }

    private fun InputStream.readExact(size: Int): ByteArray = readExactOrNull(size)
        ?: throw EOFException("旅迹媒体包数据不完整")

    private fun InputStream.readExactOrNull(size: Int): ByteArray? {
        val result = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val count = read(result, offset, size - offset)
            if (count <= 0) return null
            offset += count
        }
        return result
    }
}

private fun AppData.remapMedia(map: Map<String, String>) = copy(
    trips = trips.map { trip -> trip.copy(days = trip.days.map { day -> day.copy(items = day.items.map { item ->
        item.copy(media = item.media.map { media -> map[media.id]?.let { media.copy(localUri = it) } ?: media })
    }) }) },
    stories = stories.map { story -> story.copy(
        coverMedia = story.coverMedia?.let { media -> map[media.id]?.let { media.copy(localUri = it) } ?: media },
        days = story.days.map { day -> day.copy(entries = day.entries.map { entry ->
            entry.copy(media = entry.media.map { media -> map[media.id]?.let { media.copy(localUri = it) } ?: media })
        }) },
    ) },
    favorites = favorites.map { favorite ->
        favorite.copy(media = favorite.media.map { media -> map[media.id]?.let { media.copy(localUri = it) } ?: media })
    },
)

private fun Pair<Trip?, TravelStory?>.remapMedia(map: Map<String, String>): Pair<Trip?, TravelStory?> =
    first?.let { AppData(trips = listOf(it)).remapMedia(map).trips.single() } to
        second?.let { AppData(stories = listOf(it)).remapMedia(map).stories.single() }
