package com.personal.triptrail

import com.personal.triptrail.data.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.personal.triptrail.util.TripFileService
import com.personal.triptrail.util.PortablePackageCodec
import com.personal.triptrail.util.backupMediaReferences
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.Base64

class DomainModelTest {
    @Test
    fun timelineOrdersCurrentThenUpcomingThenHistory() {
        val today = parseDate("2026-08-31")!!
        val current = Trip(title = "当前", destination = "杭州", startDate = parseDate("2026-08-30")!!, endDate = parseDate("2026-09-01")!!)
        val upcoming = Trip(title = "未来", destination = "成都", startDate = parseDate("2026-09-10")!!, endDate = parseDate("2026-09-12")!!)
        val history = Trip(title = "过去", destination = "北京", startDate = parseDate("2026-08-01")!!, endDate = parseDate("2026-08-02")!!)

        assertEquals(listOf("当前", "未来", "过去"), listOf(history, upcoming, current).timelineSorted(today).map { it.title })
    }

    @Test
    fun appDataRoundTripsWithoutLosingHierarchy() {
        val day = parseDate("2026-09-10")!!
        val item = ItineraryItem(title = "灵隐寺", startTime = combineDateAndTime(day, "09:00")!!, endTime = combineDateAndTime(day, "11:00")!!, cost = 75.5)
        val source = AppData(trips = listOf(Trip(title = "杭州三日", destination = "杭州", startDate = day, endDate = day, days = listOf(TripDay(date = day, items = listOf(item))))))
        val json = Json { encodeDefaults = true }
        val restored = json.decodeFromString<AppData>(json.encodeToString(source))

        assertEquals("杭州三日", restored.trips.single().title)
        assertEquals("灵隐寺", restored.trips.single().days.single().items.single().title)
        assertEquals(75.5, restored.trips.single().days.single().items.single().cost, 0.001)
    }

    @Test
    fun tripProgressCountsNestedCompletedItems() {
        val day = parseDate("2026-09-10")!!
        val trip = Trip(title = "测试", destination = "杭州", startDate = day, endDate = day, days = listOf(
            TripDay(date = day, items = listOf(
                ItineraryItem(title = "A", isCompleted = true, executionStatus = ItineraryExecutionStatus.COMPLETED),
                ItineraryItem(title = "B", executionStatus = ItineraryExecutionStatus.NOT_STARTED),
            ))
        ))

        assertEquals(1, trip.completedCount)
        assertEquals(2, trip.totalCount)
        assertTrue(trip.nextUnfinishedItem?.title == "B")
    }

    @Test
    fun extendedJourneyFieldsRoundTripWithoutLoss() {
        val day = parseDate("2026-09-10")!!
        val itemMedia = MediaReference(id = "item-media", localUri = "file:///item.jpg", caption = "窗外", sortOrder = 1)
        val favoriteMedia = MediaReference(id = "favorite-media", localUri = "file:///favorite.jpg")
        val coverMedia = MediaReference(id = "cover-media", localUri = "file:///cover.jpg")
        val item = ItineraryItem(
            title = "高铁前往杭州",
            category = PlaceCategory.TRANSPORT,
            startTime = combineDateAndTime(day, "08:00")!!,
            endTime = combineDateAndTime(day, "09:05")!!,
            locationMode = ArrangementLocationMode.ROUTE,
            originName = "上海虹桥站",
            destinationName = "杭州东站",
            executionStatus = ItineraryExecutionStatus.IN_PROGRESS,
            isAutomaticCompletionOverridden = true,
            sourceFavoriteId = "favorite-source",
            media = listOf(itemMedia),
        )
        val favorite = item.copy(id = "favorite", isFavorite = true, media = listOf(favoriteMedia))
        val story = TravelStory(
            title = "杭州足迹", destination = "杭州", startDate = day, endDate = day,
            coverMedia = coverMedia, coverZoom = 1.4, coverOffsetX = .12, coverOffsetY = -.08,
        )
        val source = AppData(
            trips = listOf(Trip(title = "杭州", destination = "杭州", startDate = day, endDate = day, days = listOf(TripDay(date = day, items = listOf(item))))),
            stories = listOf(story),
            favorites = listOf(favorite),
        )

        val restored = TripFileService.restore(TripFileService.backup(source))
        val restoredItem = restored.trips.single().days.single().items.single()

        assertEquals(ArrangementLocationMode.ROUTE, restoredItem.locationMode)
        assertEquals("上海虹桥站", restoredItem.originName)
        assertEquals("杭州东站", restoredItem.destinationName)
        assertEquals(ItineraryExecutionStatus.IN_PROGRESS, restoredItem.executionStatus)
        assertTrue(restoredItem.isAutomaticCompletionOverridden)
        assertEquals("favorite-source", restoredItem.sourceFavoriteId)
        assertEquals("item-media", restoredItem.media.single().id)
        assertEquals(1.4, restored.stories.single().coverZoom, 0.001)
        assertEquals("cover-media", restored.stories.single().coverMedia?.id)
        assertEquals("favorite-media", restored.favorites.single().media.single().id)
    }

    @Test
    fun favoriteImportContinuesTimeAndCreatesIndependentCopies() {
        val day = parseDate("2026-09-10")!!
        val sourceMedia = MediaReference(id = "source-media", localUri = "file:///lake.jpg")
        val favorite = ItineraryItem(
            id = "favorite-id", title = "羊卓雍措", playDurationMinutes = 90,
            isFavorite = true, media = listOf(sourceMedia),
        )
        val start = combineDateAndTime(day, "14:30")!!

        val first = favorite.importedFromFavorite(start, createdAt = 123L)
        val second = favorite.importedFromFavorite(first.endTime, createdAt = 124L)

        assertEquals("14:30", first.startTime.timeText())
        assertEquals("16:00", first.endTime.timeText())
        assertEquals(first.endTime, second.startTime)
        assertEquals("favorite-id", first.sourceFavoriteId)
        assertTrue(!first.isFavorite)
        assertTrue(first.id != favorite.id && second.id != first.id)
        assertTrue(first.media.single().id != sourceMedia.id)
    }

    @Test
    fun backupMediaCollectionIncludesTripStoryCoverAndFavoriteMedia() {
        val day = parseDate("2026-09-10")!!
        fun media(id: String) = MediaReference(id = id, localUri = "file:///$id.jpg")
        val data = AppData(
            trips = listOf(Trip(title = "trip", destination = "杭州", startDate = day, endDate = day, days = listOf(TripDay(date = day, items = listOf(ItineraryItem(media = listOf(media("trip")))))))),
            stories = listOf(TravelStory(title = "story", destination = "杭州", startDate = day, endDate = day, coverMedia = media("cover"), days = listOf(StoryDay(date = day, entries = listOf(StoryEntry(media = listOf(media("entry")))))))),
            favorites = listOf(ItineraryItem(isFavorite = true, media = listOf(media("favorite")))),
        )

        assertEquals(setOf("trip", "cover", "entry", "favorite"), data.backupMediaReferences().map { it.id }.toSet())
    }

    @Test
    fun executionStatusIsDerivedFromTime() {
        val now = parseDate("2026-09-10")!! + 12 * 3_600_000L

        assertEquals(ItineraryExecutionStatus.COMPLETED, ItineraryItem(startTime = now - 7_200_000L, endTime = now - 1).automaticExecutionStatus(now))
        assertEquals(ItineraryExecutionStatus.IN_PROGRESS, ItineraryItem(startTime = now - 1, endTime = now + 1).automaticExecutionStatus(now))
        assertEquals(ItineraryExecutionStatus.NOT_STARTED, ItineraryItem(startTime = now + 1, endTime = now + 3_600_000L).automaticExecutionStatus(now))
    }

    @Test
    fun multiDayShareContainsOnlySelectedDays() {
        val first = TripDay(id = "one", date = parseDate("2026-09-10")!!, title = "第一天")
        val second = TripDay(id = "two", date = parseDate("2026-09-11")!!, title = "第二天")
        val trip = Trip(title = "杭州", destination = "杭州", startDate = first.date, endDate = second.date, days = listOf(first, second))

        val shared = TripFileService.tripForSharing(trip, setOf("two"))

        assertEquals(listOf("two"), shared.days.map { it.id })
        assertEquals(second.date, shared.startDate)
        assertEquals(second.date, shared.endDate)
    }

    @Test
    fun iosBackupJsonMapsMillisecondsChineseEnumsAndMedia() {
        val media = JSONObject().apply {
            put("id", "media-1"); put("localIdentifier", "ios-library-id"); put("kindRaw", "image")
            put("caption", "窗外"); put("createdAt", 1_788_341_732_003.561); put("sortOrder", 0)
        }
        val item = JSONObject().apply {
            put("id", "item-1"); put("title", "高铁前往杭州"); put("categoryRaw", "交通")
            put("startTime", 1_784_157_000_000L); put("endTime", 1_784_160_900_000L)
            put("locationModeRaw", "起终点"); put("transportRaw", "火车"); put("executionStatusRaw", "已完成")
            put("isCompleted", true); put("media", JSONArray().put(media))
        }
        val day = JSONObject().apply {
            put("id", "day-1"); put("date", 1_784_131_200_000L); put("title", "第 1 天")
            put("items", JSONArray().put(item))
        }
        val root = JSONObject().apply {
            put("formatVersion", 1)
            put("trips", JSONArray().put(JSONObject().apply {
                put("id", "trip-1"); put("title", "杭州"); put("destination", "杭州")
                put("startDate", 1_784_131_200_000L); put("endDate", 1_784_131_200_000L)
                put("days", JSONArray().put(day))
            }))
            put("stories", JSONArray()); put("favorites", JSONArray())
        }

        val restored = TripFileService.importBackup(root.toString())
        val restoredItem = restored.trips.single().days.single().items.single()

        assertEquals(PlaceCategory.TRANSPORT, restoredItem.category)
        assertEquals(ArrangementLocationMode.ROUTE, restoredItem.locationMode)
        assertEquals(TransportMode.TRAIN, restoredItem.transport)
        assertEquals(ItineraryExecutionStatus.COMPLETED, restoredItem.executionStatus)
        assertEquals("media-1", restoredItem.media.single().id)
        assertEquals(1_788_341_732_003L, restoredItem.media.single().createdAt)
    }

    @Test
    fun iosPortableEnvelopeDecodesBase64ContentAndMediaPayload() {
        val content = """{"formatVersion":1,"trips":[],"stories":[],"favorites":[]}"""
        val payload = "image-bytes".toByteArray()
        val manifest = JSONObject().apply {
            put("format", "triptrail.portable-package"); put("formatVersion", 1); put("kind", "backup")
            put("contentData", Base64.getEncoder().encodeToString(content.toByteArray()))
            put("media", JSONArray().put(JSONObject().apply {
                put("referenceID", "media-1"); put("kindRaw", "image")
                put("originalFilename", "photo.jpg"); put("byteCount", payload.size)
            }))
        }.toString().toByteArray()
        val bytes = ByteArrayOutputStream().apply {
            write(PortablePackageCodec.magic)
            write(ByteBuffer.allocate(Long.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).putLong(manifest.size.toLong()).array())
            write(manifest); write(payload)
        }.toByteArray()
        val input = ByteArrayInputStream(bytes).buffered()
        val envelope = PortablePackageCodec.readEnvelope(input)
        val directory = Files.createTempDirectory("triptrail-portable-test").toFile()

        try {
            val extracted = PortablePackageCodec.extractPayload(input, envelope.media, directory)
            assertEquals(content, envelope.content)
            assertEquals("media-1", extracted.single().first.referenceID)
            assertTrue(extracted.single().second.readBytes().contentEquals(payload))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun iosSharedTripJsonParsesIsoDatesAndMediaReferences() {
        val shared = JSONObject().apply {
            put("format", "triptrail.shared-journey"); put("formatVersion", 1); put("kind", "trip")
            put("trip", JSONObject().apply {
                put("id", "trip-1"); put("title", "杭州三日"); put("destination", "杭州")
                put("startDate", "2026-07-15T00:00:00Z"); put("endDate", "2026-07-15T00:00:00Z")
                put("days", JSONArray().put(JSONObject().apply {
                    put("id", "day-1"); put("date", "2026-07-15T00:00:00Z")
                    put("items", JSONArray().put(JSONObject().apply {
                        put("id", "item-1"); put("title", "西湖"); put("categoryRaw", "景点")
                        put("startTime", "2026-07-15T01:00:00Z"); put("endTime", "2026-07-15T03:00:00Z")
                        put("media", JSONArray().put(JSONObject().apply {
                            put("id", "photo-1"); put("localIdentifier", ""); put("kindRaw", "image")
                        }))
                    }))
                }))
            })
        }

        val (trip, story) = TripFileService.importShared(shared.toString())

        assertEquals(null, story)
        assertEquals("杭州三日", trip?.title)
        assertEquals("photo-1", trip?.days?.single()?.items?.single()?.media?.single()?.id)
    }

    @Test
    fun iosSharedFootprintJsonAttachesFlatEntriesToTheirDays() {
        val shared = JSONObject().apply {
            put("format", "triptrail.shared-journey"); put("formatVersion", 1); put("kind", "footprint")
            put("story", JSONObject().apply {
                put("id", "story-1"); put("title", "杭州足迹"); put("destination", "杭州")
                put("startDate", "2026-07-15T00:00:00Z"); put("endDate", "2026-07-15T00:00:00Z")
                put("days", JSONArray().put(JSONObject().apply {
                    put("id", "story-day-1"); put("date", "2026-07-15T00:00:00Z"); put("title", "第一天")
                }))
                put("entries", JSONArray().put(JSONObject().apply {
                    put("id", "entry-1"); put("storyDayID", "story-day-1"); put("title", "断桥残雪")
                    put("categoryRaw", "景点"); put("startTime", "2026-07-15T01:00:00Z")
                    put("media", JSONArray().put(JSONObject().apply {
                        put("id", "photo-1"); put("localIdentifier", ""); put("kindRaw", "image")
                    }))
                }))
            })
        }

        val (trip, story) = TripFileService.importShared(shared.toString())

        assertEquals(null, trip)
        assertEquals("entry-1", story?.days?.single()?.entries?.single()?.id)
        assertEquals("photo-1", story?.days?.single()?.entries?.single()?.media?.single()?.id)
    }

    @Test
    fun suppliedIosBackupFixtureCanBeDecodedAndFullyRead() {
        val path = System.getenv("TRIPTRAIL_IOS_BACKUP_FIXTURE")
        assumeTrue(!path.isNullOrBlank() && File(path).isFile)
        File(path!!).inputStream().buffered().use { input ->
            val envelope = PortablePackageCodec.readEnvelope(input)
            val restored = TripFileService.importBackup(envelope.content)
            val directory = Files.createTempDirectory("triptrail-ios-fixture").toFile()
            try {
                val extracted = PortablePackageCodec.extractPayload(input, envelope.media, directory)
                assertTrue(restored.trips.isNotEmpty() || restored.stories.isNotEmpty())
                assertEquals(envelope.media.size, extracted.size)
                assertEquals(envelope.media.sumOf { it.byteCount }, extracted.sumOf { it.second.length() })
                assertEquals(
                    envelope.media.map { it.referenceID }.toSet(),
                    restored.backupMediaReferences().map { it.id }.toSet(),
                )
            } finally {
                directory.deleteRecursively()
            }
        }
    }
}
