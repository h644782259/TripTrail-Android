package com.personal.triptrail

import com.personal.triptrail.data.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.personal.triptrail.util.TripFileService
import com.personal.triptrail.util.backupMediaReferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
