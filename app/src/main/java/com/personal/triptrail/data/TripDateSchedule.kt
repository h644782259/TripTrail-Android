package com.personal.triptrail.data

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Keep existing day identities and content, matching iOS normalizeTripDaySchedule. */
fun Trip.withSynchronizedDates(zone: ZoneId = ZoneId.systemDefault()): Trip {
    val start = Instant.ofEpochMilli(startDate).atZone(zone).toLocalDate()
    val normalizedStart = start.atStartOfDay(zone).toInstant().toEpochMilli()
    if (days.isEmpty()) return copy(startDate = normalizedStart, endDate = maxOf(normalizedStart, Instant.ofEpochMilli(endDate).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()))
    val normalizedDays = days.sortedBy { it.sortOrder }.mapIndexed { index, day ->
        val expectedDate = start.plusDays(index.toLong())
        val oldDate = Instant.ofEpochMilli(day.date).atZone(zone).toLocalDate()
        val offset = ChronoUnit.DAYS.between(oldDate, expectedDate)
        fun shift(time: Long) = Instant.ofEpochMilli(time).atZone(zone).plusDays(offset).toInstant().toEpochMilli()
        day.copy(
            date = expectedDate.atStartOfDay(zone).toInstant().toEpochMilli(),
            sortOrder = index,
            title = if (Regex("第\\d+天").matches(day.title.replace(" ", ""))) "第 ${index + 1} 天" else day.title,
            items = if (offset == 0L) day.items else day.items.map { it.copy(startTime = shift(it.startTime), endTime = shift(it.endTime)) },
        )
    }
    return copy(startDate = normalizedStart, endDate = normalizedDays.last().date, days = normalizedDays)
}
