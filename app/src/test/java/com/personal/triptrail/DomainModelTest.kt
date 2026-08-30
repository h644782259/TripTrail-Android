package com.personal.triptrail

import com.personal.triptrail.data.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
            TripDay(date = day, items = listOf(ItineraryItem(title = "A", isCompleted = true), ItineraryItem(title = "B")))
        ))

        assertEquals(1, trip.completedCount)
        assertEquals(2, trip.totalCount)
        assertTrue(trip.nextUnfinishedItem?.title == "B")
    }
}
