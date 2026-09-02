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
enum class ArrangementLocationMode(val label: String) { SINGLE("单地点"), ROUTE("起终点") }

@Serializable
enum class ItineraryExecutionStatus(val label: String) {
    NOT_STARTED("未开始"), IN_PROGRESS("进行中"), COMPLETED("已完成")
}

@Serializable
enum class JourneyLocationRole(val label: String) { PLACE("地点"), ORIGIN("出发地"), DESTINATION("目的地") }

@Serializable
data class JourneyLocationTarget(
    val role: JourneyLocationRole,
    val name: String,
    val address: String = "",
) {
    val displayName get() = name.trim().ifBlank { address.trim() }
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
    val locationMode: ArrangementLocationMode = ArrangementLocationMode.SINGLE,
    val placeName: String = "",
    val placeAddress: String = "",
    val originName: String = "",
    val originAddress: String = "",
    val destinationName: String = "",
    val destinationAddress: String = "",
    val transport: TransportMode = TransportMode.CAR,
    val distanceText: String = "",
    val playDurationMinutes: Int = 60,
    val reservationInfo: String = "",
    val cost: Double = 0.0,
    val isCompleted: Boolean = false,
    val executionStatus: ItineraryExecutionStatus = if (isCompleted) ItineraryExecutionStatus.COMPLETED else ItineraryExecutionStatus.NOT_STARTED,
    val isAutomaticCompletionOverridden: Boolean = false,
    val sortOrder: Int = 0,
    val isFavorite: Boolean = false,
    val favoriteCreatedAt: Long = System.currentTimeMillis(),
    val sourceFavoriteId: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val media: List<MediaReference> = emptyList(),
) {
    val locationTargets: List<JourneyLocationTarget>
        get() = when (locationMode) {
            ArrangementLocationMode.SINGLE -> listOfNotNull(
                JourneyLocationTarget(
                    JourneyLocationRole.PLACE,
                    cleanLocationName(placeName.ifBlank { title }),
                    placeAddress.ifBlank { address }
                ).takeIf { it.displayName.isNotBlank() }
            )
            ArrangementLocationMode.ROUTE -> listOf(
                JourneyLocationTarget(JourneyLocationRole.ORIGIN, cleanLocationName(originName), originAddress),
                JourneyLocationTarget(JourneyLocationRole.DESTINATION, cleanLocationName(destinationName), destinationAddress)
            ).filter { it.displayName.isNotBlank() }
        }
    val primaryNavigationTarget get() = when (locationMode) {
        ArrangementLocationMode.SINGLE -> locationTargets.firstOrNull()
        ArrangementLocationMode.ROUTE -> locationTargets.firstOrNull { it.role == JourneyLocationRole.DESTINATION } ?: locationTargets.firstOrNull()
    }
    val nextNavigationTarget get() = when (locationMode) {
        ArrangementLocationMode.SINGLE -> locationTargets.firstOrNull()
        ArrangementLocationMode.ROUTE -> locationTargets.firstOrNull { it.role == JourneyLocationRole.ORIGIN } ?: locationTargets.firstOrNull()
    }
    val locationSummary get() = locationTargets.joinToString(" → ") { it.displayName }
}

fun ItineraryItem.importedFromFavorite(startTime: Long, createdAt: Long = System.currentTimeMillis()): ItineraryItem {
    val duration = playDurationMinutes.coerceAtLeast(60)
    return copy(
        id = UUID.randomUUID().toString(),
        startTime = startTime,
        endTime = startTime + duration * 60_000L,
        isFavorite = false,
        sourceFavoriteId = id,
        favoriteCreatedAt = createdAt,
        executionStatus = ItineraryExecutionStatus.NOT_STARTED,
        isCompleted = false,
        media = media.mapIndexed { index, reference -> reference.copy(id = UUID.randomUUID().toString(), sortOrder = index) },
    )
}

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
    val completedCount get() = allItems.count { it.executionStatus == ItineraryExecutionStatus.COMPLETED }
    val totalCount get() = allItems.size
    val nextUnfinishedItem get() = allItems.firstOrNull { it.executionStatus != ItineraryExecutionStatus.COMPLETED }
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
    val locationMode: ArrangementLocationMode = ArrangementLocationMode.SINGLE,
    val placeName: String = "",
    val placeAddress: String = "",
    val originName: String = "",
    val originAddress: String = "",
    val destinationName: String = "",
    val destinationAddress: String = "",
    val transport: TransportMode = TransportMode.CAR,
    val routeInfo: String = "",
    val cost: Double = 0.0,
    val sortOrder: Int = 0,
    val sourceItemId: String? = null,
    val media: List<MediaReference> = emptyList(),
) {
    val locationTargets: List<JourneyLocationTarget>
        get() = when (locationMode) {
            ArrangementLocationMode.SINGLE -> listOfNotNull(
                JourneyLocationTarget(JourneyLocationRole.PLACE, cleanLocationName(placeName.ifBlank { title }), placeAddress.ifBlank { address })
                    .takeIf { it.displayName.isNotBlank() }
            )
            ArrangementLocationMode.ROUTE -> listOf(
                JourneyLocationTarget(JourneyLocationRole.ORIGIN, cleanLocationName(originName), originAddress),
                JourneyLocationTarget(JourneyLocationRole.DESTINATION, cleanLocationName(destinationName), destinationAddress)
            ).filter { it.displayName.isNotBlank() }
        }
    val primaryNavigationTarget get() = locationTargets.firstOrNull { it.role == JourneyLocationRole.DESTINATION } ?: locationTargets.firstOrNull()
    val locationSummary get() = locationTargets.joinToString(" → ") { it.displayName }
}

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
    val coverMedia: MediaReference? = null,
    val coverZoom: Double = 1.0,
    val coverOffsetX: Double = 0.0,
    val coverOffsetY: Double = 0.0,
    val days: List<StoryDay> = emptyList(),
)

@Serializable
data class AppData(
    val formatVersion: Int = 1,
    val trips: List<Trip> = emptyList(),
    val stories: List<TravelStory> = emptyList(),
    val favorites: List<ItineraryItem> = emptyList(),
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

fun cleanLocationName(value: String): String {
    var result = value.trim()
    listOf("集合于", "游览", "参观", "打卡", "入住", "前往", "抵达", "到达").firstOrNull {
        result.startsWith(it) && result.length > it.length + 1
    }?.let { result = result.removePrefix(it).trim() }
    listOf("前往", "抵达", "到达", "去往").forEach { marker ->
        val suffix = result.substringAfterLast(marker, "").trim()
        if (suffix.length >= 2) result = suffix
    }
    return result.ifBlank { value.trim() }
}
