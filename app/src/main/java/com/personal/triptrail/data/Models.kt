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
    // Legacy storage compatibility only; no longer edited, inferred, or used for navigation.
    val transport: TransportMode = TransportMode.CAR,
    val distanceText: String = "",
    val playDurationMinutes: Int = 60,
    val reservationInfo: String = "",
    val cost: Double = 0.0,
    val isCompleted: Boolean = false,
    val executionStatus: ItineraryExecutionStatus = if (isCompleted) ItineraryExecutionStatus.COMPLETED else ItineraryExecutionStatus.NOT_STARTED,
    val isAutomaticCompletionOverridden: Boolean = false,
    val isFixedTime: Boolean = false,
    val isTimePending: Boolean = false,
    val vouchers: List<TravelVoucher> = emptyList(),
    val sortOrder: Int = 0,
    val isFavorite: Boolean = false,
    val favoriteCity: String = "",
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
        transport = TransportMode.CAR,
        distanceText = "",
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
    val licensePlate: String = "",
) {
    val allItems get() = days.sortedBy { it.sortOrder }.flatMap { it.items.sortedBy { item -> item.sortOrder } }
    val completedCount get() = allItems.count { it.executionStatus == ItineraryExecutionStatus.COMPLETED }
    val totalCount get() = allItems.size
    val nextUnfinishedItem get() = allItems.firstOrNull { !it.isTimePending && it.executionStatus != ItineraryExecutionStatus.COMPLETED } ?: allItems.firstOrNull { it.executionStatus != ItineraryExecutionStatus.COMPLETED }
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
    // Legacy storage compatibility only; no longer edited, inferred, or used for navigation.
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

/** Editable preview payload produced by a multi-day smart import. */
data class RecognizedJourneyDay(
    val sourceDayNumber: Int,
    val date: Long?,
    val title: String,
    val note: String,
    val items: List<ItineraryItem>,
)

/** A time slot captured before an itinerary reorder. */
data class ItineraryTimeSlot(
    val startMinute: Int,
    val durationMillis: Long,
)

/** A suggested time for an item whose duration does not fit its new slot. */
data class ItineraryTimeAdjustment(
    val item: ItineraryItem,
    val suggestedStartTime: Long,
    val suggestedEndTime: Long,
)

data class ItineraryMoveResult(
    val didMove: Boolean,
    val timeAdjustments: List<ItineraryTimeAdjustment> = emptyList(),
) {
    companion object { val UNCHANGED = ItineraryMoveResult(false) }
}

enum class TripPhase { CURRENT, UPCOMING, HISTORY }

fun Trip.phase(now: Long = System.currentTimeMillis()): TripPhase {
    val today = now.startOfDay()
    return when {
        startDate.startOfDay() <= today && endDate.startOfDay() >= today -> TripPhase.CURRENT
        startDate.startOfDay() > today -> TripPhase.UPCOMING
        else -> TripPhase.HISTORY
    }
}

fun ItineraryItem.automaticExecutionStatus(now: Long = System.currentTimeMillis()): ItineraryExecutionStatus = when {
    isTimePending -> executionStatus
    endTime <= now -> ItineraryExecutionStatus.COMPLETED
    startTime <= now -> ItineraryExecutionStatus.IN_PROGRESS
    else -> ItineraryExecutionStatus.NOT_STARTED
}

fun ItineraryItem.withAutomaticExecutionStatus(now: Long = System.currentTimeMillis()): ItineraryItem {
    val status = automaticExecutionStatus(now)
    return copy(
        executionStatus = status,
        isCompleted = status == ItineraryExecutionStatus.COMPLETED,
        isAutomaticCompletionOverridden = false,
    )
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

/** Plans first, then applies to a copy so cancel/failure never leaves a half-created journey. */
fun Trip.importingRecognizedJourney(recognizedDays: List<RecognizedJourneyDay>, targetDayId: String? = null): Trip {
    if (recognizedDays.isEmpty()) return this
    val ordered = days.sortedBy { it.date }.toMutableList()
    val target = targetDayId?.let { id -> ordered.firstOrNull { it.id == id } }
    require(targetDayId == null || target != null) { "目标日期已不存在，请重新选择。" }
    val replacesEmptySchedule = target == null && ordered.isNotEmpty() && ordered.all { it.items.isEmpty() && it.note.isBlank() } && recognizedDays.any { it.date != null }
    val emptyDates = ordered.filter { it.items.isEmpty() }.map { it.date.startOfDay() }
    val appendDate = ordered.maxOfOrNull { it.date.localDate().plusDays(1) } ?: startDate.localDate()
    val firstNumber = recognizedDays.minOf { it.sourceDayNumber }
    val anchor = recognizedDays.firstOrNull { it.date != null }
    val plan = recognizedDays.map { recognized ->
        val offset = (recognized.sourceDayNumber - firstNumber).coerceAtLeast(0)
        val date = target?.date?.startOfDay() ?: recognized.date?.startOfDay() ?: if (anchor != null) {
            anchor.date!!.localDate().plusDays((recognized.sourceDayNumber - anchor.sourceDayNumber).toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } else {
            emptyDates.getOrNull(offset) ?: appendDate.plusDays((offset - emptyDates.size).coerceAtLeast(0).toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        date to recognized
    }
    if (replacesEmptySchedule) {
        val oldDays = ordered.toList()
        ordered.clear()
        var date = plan.minOf { it.first }.localDate()
        val end = plan.maxOf { it.first }.localDate()
        var index = 0
        while (!date.isAfter(end)) {
            val timestamp = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            ordered += oldDays.getOrNull(index)?.copy(date = timestamp) ?: TripDay(date = timestamp, sortOrder = index)
            index++
            date = date.plusDays(1)
        }
    }
    // Preserve gaps such as Day 1 / Day 3 as editable days, matching iOS.
    var cursor = plan.minOf { it.first }.localDate()
    val last = plan.maxOf { it.first }.localDate()
    while (!cursor.isAfter(last)) {
        val date = cursor.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (ordered.none { it.date.startOfDay() == date }) ordered += TripDay(date = date, title = "", sortOrder = ordered.size)
        cursor = cursor.plusDays(1)
    }
    plan.forEach { (date, recognized) ->
        val index = ordered.indexOfFirst { it.date.startOfDay() == date }
        val day = ordered[index]
        val nextOrder = (day.items.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val imported = recognized.items.filter { it.title.isNotBlank() }.mapIndexed { itemIndex, item ->
            require(item.endTime > item.startTime) { "安排的结束时间必须晚于开始时间。" }
            val shift = date - item.startTime.startOfDay()
            item.copy(
                id = UUID.randomUUID().toString(), startTime = item.startTime + shift, endTime = item.endTime + shift,
                sortOrder = nextOrder + itemIndex, transport = TransportMode.CAR, distanceText = "",
                isAutomaticCompletionOverridden = false,
            ).withAutomaticExecutionStatus()
        }
        ordered[index] = day.copy(
            title = if (day.items.isEmpty()) recognized.title.ifBlank { day.title } else day.title,
            note = listOf(day.note, recognized.note).filter { it.isNotBlank() }.distinct().joinToString("\n"),
            items = (day.items + imported).sortedBy { it.startTime }.mapIndexed { i, item -> item.copy(sortOrder = i) },
        )
    }
    val normalized = ordered.sortedBy { it.date }.mapIndexed { index, day -> day.copy(sortOrder = index) }
    return copy(startDate = if (replacesEmptySchedule) normalized.first().date else minOf(startDate.startOfDay(), normalized.first().date), endDate = if (replacesEmptySchedule) normalized.last().date else maxOf(endDate.startOfDay(), normalized.last().date), days = normalized)
}


@Serializable
data class TravelVoucher(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val mimeType: String,
    val dataBase64: String,
)

val ItineraryItem.timeRangeText: String get() = if (isTimePending) "时间待定" else "${startTime.timeText()}–${endTime.timeText()}"


val ItineraryItem.favoriteCityLabel: String get() {
    if (favoriteCity.isNotBlank()) return favoriteCity.trim().removeSuffix("市")
    locationTargets.forEach { target ->
        val address = target.address.trim()
        listOf("北京", "上海", "天津", "重庆", "香港", "澳门").firstOrNull { address.startsWith(it) }?.let { return it }
        listOf("(?:省|自治区)([\\p{IsHan}]{2,8}?)市", "^([\\p{IsHan}]{2,8}?)市").forEach { pattern ->
            Regex(pattern).find(address)?.groupValues?.getOrNull(1)?.let { return it }
        }
    }
    return "未设置城市"
}
