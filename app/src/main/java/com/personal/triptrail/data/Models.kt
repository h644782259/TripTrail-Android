package com.personal.triptrail.data

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

@Serializable
enum class PlaceCategory(val label: String) {
    ATTRACTION("景点"), RESTAURANT("餐饮"), HOTEL("住宿"), TRANSPORT("交通"), SPECIAL("特殊位置"), OTHER("其他");

    companion object {
        fun fromLabel(value: String) = entries.firstOrNull { it.label == value } ?: when (value) {
            "购物", "待办" -> OTHER
            else -> ATTRACTION
        }
    }
}

@Serializable
enum class TransportMode(val label: String, val amapValue: String) {
    CAR("驾车", "0"), WALK("步行", "2"), RIDE("骑行", "3"), BUS("公交", "1"), TRAIN("火车", "0"), FLIGHT("飞机", "0");

    companion object { fun fromLabel(value: String) = entries.firstOrNull { it.label == value } ?: CAR }
}

@Serializable
enum class MediaKind { IMAGE, VIDEO }

@Serializable
data class MediaReference(
    val id: String = UUID.randomUUID().toString(),
    val localUri: String,
    val kind: MediaKind = MediaKind.IMAGE,
    val caption: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0,
)

@Serializable
data class ItineraryItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val category: PlaceCategory = PlaceCategory.ATTRACTION,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long = System.currentTimeMillis() + 3_600_000,
    val address: String = "",
    val note: String = "",
    val transport: TransportMode = TransportMode.CAR,
    val distanceText: String = "",
    val playDurationMinutes: Int = 60,
    val reservationInfo: String = "",
    val cost: Double = 0.0,
    val isCompleted: Boolean = false,
    val isAutomaticCompletionOverridden: Boolean = false,
    val sortOrder: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val media: List<MediaReference> = emptyList(),
)

@Serializable
data class TripDay(
    val id: String = UUID.randomUUID().toString(),
    val date: Long,
    val title: String = "",
    val note: String = "",
    val sortOrder: Int = 0,
    val items: List<ItineraryItem> = emptyList(),
)

@Serializable
data class Trip(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val destination: String,
    val startDate: Long,
    val endDate: Long,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val days: List<TripDay> = emptyList(),
) {
    val allItems get() = days.sortedBy { it.sortOrder }.flatMap { it.items.sortedBy { item -> item.sortOrder } }
    val completedCount get() = allItems.count { it.isCompleted }
    val totalCount get() = allItems.size
    val nextUnfinishedItem get() = allItems.firstOrNull { !it.isCompleted }
}

@Serializable
data class StoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val category: PlaceCategory = PlaceCategory.ATTRACTION,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val timeLabel: String = "",
    val address: String = "",
    val supplementalInfo: String = "",
    val note: String = "",
    val transport: TransportMode = TransportMode.CAR,
    val routeInfo: String = "",
    val cost: Double = 0.0,
    val sortOrder: Int = 0,
    val sourceItemId: String? = null,
    val media: List<MediaReference> = emptyList(),
)

@Serializable
data class StoryDay(
    val id: String = UUID.randomUUID().toString(),
    val date: Long,
    val title: String = "",
    val note: String = "",
    val details: String = "",
    val sortOrder: Int = 0,
    val sourceDayId: String? = null,
    val entries: List<StoryEntry> = emptyList(),
)

@Serializable
data class TravelStory(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val destination: String,
    val startDate: Long,
    val endDate: Long,
    val summary: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val sourceTripId: String? = null,
    val days: List<StoryDay> = emptyList(),
)

@Serializable
data class AppData(
    val formatVersion: Int = 1,
    val trips: List<Trip> = emptyList(),
    val stories: List<TravelStory> = emptyList(),
)

enum class TripPhase { CURRENT, UPCOMING, HISTORY }

fun Trip.phase(now: Long = System.currentTimeMillis()): TripPhase {
    val today = now.startOfDay()
    return when {
        startDate.startOfDay() <= today && endDate.startOfDay() >= today -> TripPhase.CURRENT
        startDate.startOfDay() > today -> TripPhase.UPCOMING
        else -> TripPhase.HISTORY
    }
}

fun List<Trip>.timelineSorted(now: Long = System.currentTimeMillis()): List<Trip> = sortedWith(
    compareBy<Trip> { it.phase(now).ordinal }.thenComparator { a, b ->
        when (a.phase(now)) {
            TripPhase.CURRENT -> compareValues(a.endDate, b.endDate)
            TripPhase.UPCOMING -> compareValues(a.startDate, b.startDate)
            TripPhase.HISTORY -> compareValues(b.endDate, a.endDate)
        }.takeIf { it != 0 } ?: compareValues(b.createdAt, a.createdAt)
    }
)

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val chineseDateFormatter = DateTimeFormatter.ofPattern("M月d日")
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun Long.localDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
fun Long.startOfDay(): Long = localDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
fun Long.dateText(): String = localDate().format(dateFormatter)
fun Long.chineseDateText(): String = localDate().format(chineseDateFormatter)
fun Long.timeText(): String = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFormatter)
fun parseDate(value: String): Long? = runCatching { LocalDate.parse(value.trim(), dateFormatter).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
fun combineDateAndTime(day: Long, text: String): Long? = runCatching {
    val pieces = text.trim().split(":")
    day.localDate().atTime(pieces[0].toInt(), pieces[1].toInt()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}.getOrNull()
