package com.personal.triptrail

import com.personal.triptrail.data.*
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class TripDateScheduleTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun time(value: String) = ZonedDateTime.parse(value).toInstant().toEpochMilli()

    @Test fun movingDepartureKeepsTabsAndItemDatesTogether() {
        val old = time("2026-09-01T00:00:00+08:00")
        val start = time("2026-10-01T00:00:00+08:00")
        val item = ItineraryItem(title = "车次", startTime = old + 9 * 3600000L, endTime = old + 10 * 3600000L, isFixedTime = true)
        val first = TripDay(date = old, title = "第 1 天", items = listOf(item))
        val second = TripDay(date = old + 86400000L, title = "游览", sortOrder = 1)
        val updated = Trip(title = "旅程", destination = "杭州", startDate = start, endDate = start, days = listOf(second, first)).withSynchronizedDates(zone)
        assertEquals(listOf(start, start + 86400000L), updated.days.map { it.date })
        assertEquals(updated.days.last().date, updated.endDate)
        assertEquals(first.id, updated.days.first().id)
        assertEquals("游览", updated.days.last().title)
        assertEquals(item.copy(startTime = start + 9 * 3600000L, endTime = start + 10 * 3600000L), updated.days.first().items.single())
        assertEquals(updated, updated.withSynchronizedDates(zone))
    }

    @Test fun calendarDayShiftPreservesClockAcrossDaylightSaving() {
        val zone = ZoneId.of("America/New_York")
        val old = time("2026-03-07T00:00:00-05:00")
        val start = time("2026-03-09T00:00:00-04:00")
        val item = ItineraryItem(startTime = time("2026-03-07T09:00:00-05:00"), endTime = time("2026-03-07T10:00:00-05:00"))
        val updated = Trip(title = "行程", destination = "纽约", startDate = start, endDate = start, days = listOf(TripDay(date = old, items = listOf(item)))).withSynchronizedDates(zone)
        assertEquals(time("2026-03-09T09:00:00-04:00"), updated.days.single().items.single().startTime)
    }
}
