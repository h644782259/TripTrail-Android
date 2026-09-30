package com.personal.triptrail.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import com.personal.triptrail.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.ZoneId
import kotlin.math.abs

class TripRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    private val dataFile = File(context.filesDir, "triptrail-data.json")
    private val mediaDirectory = File(context.filesDir, "media").apply { mkdirs() }
    private val _data = MutableStateFlow(normalizeSchedules(load()).autoCompleteElapsed())
    val data: StateFlow<AppData> = _data.asStateFlow()

    private val cloudEditEvents = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val cloudEdits: kotlinx.coroutines.flow.SharedFlow<Unit> get() = cloudEditEvents

    @Synchronized
    private fun mutate(notifyCloud: Boolean = true, block: (AppData) -> AppData) {
        val updated = normalizeSchedules(block(_data.value)).autoCompleteElapsed()
        val temporary = File(dataFile.parentFile, "${dataFile.name}.tmp")
        temporary.outputStream().use { output ->
            output.write(json.encodeToString(updated).toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        try {
            java.nio.file.Files.move(temporary.toPath(), dataFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            java.nio.file.Files.move(temporary.toPath(), dataFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        // Publish only after the local copy is durable; failed cloud imports leave the old state visible.
        _data.value = updated
        if (notifyCloud) cloudEditEvents.tryEmit(Unit)
    }

    private fun load(): AppData = runCatching {
        if (dataFile.exists()) json.decodeFromString<AppData>(dataFile.readText()) else AppData()
    }.getOrElse { AppData() }

    internal fun removeForRecycle(id: String, kind: String) {
        val current = _data.value
        val next = when (kind) {
            "trip" -> current.copy(trips = current.trips.filterNot { it.id.equals(id, true) })
            "story" -> current.copy(stories = current.stories.filterNot { it.id.equals(id, true) })
            "favorite" -> current.copy(favorites = current.favorites.filterNot { it.id.equals(id, true) })
            else -> current
        }
        if (next != current) mutate(notifyCloud = false) { next }
    }
    fun replaceAll(data: AppData) = mutate(notifyCloud = false) { data }
    fun refreshAutomaticStatuses() {
        val current = _data.value
        val refreshed = current.autoCompleteElapsed()
        if (refreshed != current) mutate(notifyCloud = false) { refreshed }
    }
    fun exportJson(): String = json.encodeToString(_data.value)
    fun decodeJson(value: String): AppData = json.decodeFromString(value)

    fun createTrip(title: String, destination: String, startDate: Long, endDate: Long, note: String = "", licensePlate: String = ""): Trip {
        val safeEnd = maxOf(startDate.startOfDay(), endDate.startOfDay())
        val start = startDate.startOfDay()
        val totalDays = java.time.temporal.ChronoUnit.DAYS.between(start.localDate(), safeEnd.localDate()).toInt() + 1
        val days = (0 until totalDays).map { index ->
            val date = start.localDate().plusDays(index.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            TripDay(date = date, title = "第 ${index + 1} 天", sortOrder = index)
        }
        val trip = Trip(title = title, destination = destination, startDate = start, endDate = safeEnd, note = note, days = days, licensePlate = licensePlate.trim().uppercase(java.util.Locale.ROOT))
        mutate { it.copy(trips = it.trips + trip) }
        return trip
    }

    fun updateTrip(updated: Trip) = mutate { data -> data.copy(trips = data.trips.map { if (it.id == updated.id) updated.withSynchronizedDates() else it }) }
    fun deleteTrip(id: String) {
        com.personal.triptrail.util.CloudSyncService.get(context).trash(id, "trip", this)
        cloudEditEvents.tryEmit(Unit)
    }

    fun addDay(tripId: String): TripDay? {
        var created: TripDay? = null
        mutate { data -> data.copy(trips = data.trips.map { trip ->
            if (trip.id != tripId) trip else {
                val last = trip.days.maxByOrNull { it.sortOrder }
                val date = (last?.date ?: trip.endDate).localDate().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                created = TripDay(date = date, title = "第 ${trip.days.size + 1} 天", sortOrder = trip.days.size)
                trip.copy(endDate = maxOf(trip.endDate, date), days = trip.days + created!!)
            }
        }) }
        return created
    }

    fun updateDay(tripId: String, updated: TripDay) = mutate { data -> data.copy(trips = data.trips.map { trip ->
        if (trip.id != tripId) trip else {
            val previous = trip.days.firstOrNull { it.id == updated.id }
            val dayShift = previous?.let { updated.date.startOfDay() - it.date.startOfDay() } ?: 0L
            val normalized = if (dayShift == 0L) updated else updated.copy(
                items = updated.items.map { item -> item.copy(startTime = item.startTime + dayShift, endTime = item.endTime + dayShift) }
            )
            val days = trip.days.map { if (it.id == normalized.id) normalized else it }
            trip.copy(
                startDate = days.minOfOrNull { it.date.startOfDay() } ?: trip.startDate,
                endDate = days.maxOfOrNull { it.date.startOfDay() } ?: trip.endDate,
                days = days,
            )
        }
    }) }

    fun deleteDay(tripId: String, dayId: String) = mutate { data -> data.copy(trips = data.trips.map { trip ->
        if (trip.id != tripId) trip else {
            val days = trip.days.filterNot { it.id == dayId }.mapIndexed { i, d -> d.copy(sortOrder = i) }
            trip.copy(
                startDate = days.minOfOrNull { it.date.startOfDay() } ?: trip.startDate,
                endDate = days.maxOfOrNull { it.date.startOfDay() } ?: trip.endDate,
                days = days,
            )
        }
    }) }

    fun moveDay(tripId: String, dayId: String, delta: Int) = mutate { data -> data.copy(trips = data.trips.map { trip ->
        if (trip.id != tripId) trip else {
            val ordered = trip.days.sortedBy { it.sortOrder }.toMutableList()
            val from = ordered.indexOfFirst { it.id == dayId }
            if (from < 0 || ordered.size < 2) return@map trip
            val to = (from + delta).coerceIn(0, ordered.lastIndex)
            if (from != to) ordered.add(to, ordered.removeAt(from))
            val start = trip.startDate.startOfDay()
            val normalized = ordered.mapIndexed { index, day ->
                val newDate = start.localDate().plusDays(index.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val shift = newDate - day.date.startOfDay()
                day.copy(
                    date = newDate,
                    title = if (day.title.matches(Regex("第\\s*\\d+\\s*天"))) "第 ${index + 1} 天" else day.title,
                    sortOrder = index,
                    items = day.items.map { item -> item.copy(startTime = item.startTime + shift, endTime = item.endTime + shift) }
                )
            }
            trip.copy(endDate = normalized.lastOrNull()?.date ?: trip.endDate, days = normalized)
        }
    }) }

    fun suggestedStart(day: TripDay): Long = day.items.filterNot { it.isTimePending }.maxOfOrNull { it.endTime }
        ?: day.date.localDate().atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun saveItem(tripId: String, dayId: String, item: ItineraryItem) = mutate { data -> data.copy(trips = data.trips.map { trip ->
        if (trip.id != tripId) trip else trip.copy(days = trip.days.map { day ->
            if (day.id != dayId) day else {
                val exists = day.items.any { it.id == item.id }
                val normalized = item.onScheduleDay(day.date).copy(
                    sortOrder = if (exists) item.sortOrder else day.items.size,
                    isAutomaticCompletionOverridden = false,
                ).withAutomaticExecutionStatus()
                day.copy(items = (if (exists) day.items.map { if (it.id == item.id) normalized else it } else day.items + normalized).normalizeItems())
            }
        })
    }) }

    fun appendRecognizedJourney(tripId: String, recognizedDays: List<RecognizedJourneyDay>, targetDayId: String? = null): Int {
        var added = 0
        mutate { data -> data.copy(trips = data.trips.map { trip ->
            if (trip.id != tripId) trip else trip.importingRecognizedJourney(recognizedDays, targetDayId).also {
                added = it.totalCount - trip.totalCount
            }
        }) }
        return added
    }

    fun createRecognizedJourney(title: String, destination: String, startDate: Long, recognizedDays: List<RecognizedJourneyDay>, licensePlate: String = ""): Trip {
        require(title.isNotBlank() && recognizedDays.any { day -> day.items.any { it.title.isNotBlank() } }) { "请填写旅程名称并至少保留一个安排。" }
        val baseDate = recognizedDays.mapNotNull { it.date }.minOrNull() ?: startDate
        val trip = Trip(title = title.trim(), destination = destination.trim(), startDate = baseDate.startOfDay(), endDate = baseDate.startOfDay(), licensePlate = licensePlate.trim().uppercase(java.util.Locale.ROOT))
            .importingRecognizedJourney(recognizedDays)
        mutate { it.copy(trips = it.trips + trip) }
        return trip
    }

    fun deleteItem(tripId: String, dayId: String, itemId: String) = mutate { data -> data.copy(trips = data.trips.map { trip ->
        if (trip.id != tripId) trip else trip.copy(days = trip.days.map { day ->
            if (day.id != dayId) day else day.copy(items = day.items.filterNot { it.id == itemId }.mapIndexed { i, item -> item.copy(sortOrder = i) })
        })
    }) }

    fun moveItem(tripId: String, dayId: String, itemId: String, delta: Int) = mutate { data -> data.copy(trips = data.trips.map { trip ->
        if (trip.id != tripId) trip else trip.copy(days = trip.days.map { day ->
            if (day.id != dayId) day else {
                val ordered = day.items.sortedBy { it.sortOrder }.toMutableList()
                val from = ordered.indexOfFirst { it.id == itemId }
                if (from < 0 || ordered.size < 2) return@map day
                val to = (from + delta).coerceIn(0, ordered.lastIndex)
                if (from != to) ordered.add(to, ordered.removeAt(from))
                day.copy(items = ordered.mapIndexed { index, item -> item.copy(sortOrder = index) })
            }
        })
    }) }

    /** Reorders one day and reconciles the original time slots with the new order. */
    fun moveItemToDay(tripId: String, sourceDayId: String, itemId: String, targetDayId: String) = mutate { data ->
        data.copy(trips = data.trips.map { trip ->
            if (trip.id != tripId || sourceDayId == targetDayId) return@map trip
            val source = trip.days.firstOrNull { it.id == sourceDayId } ?: return@map trip
            val target = trip.days.firstOrNull { it.id == targetDayId } ?: return@map trip
            val item = source.items.firstOrNull { it.id == itemId } ?: return@map trip
            val start = target.date.localDate().atTime(java.time.Instant.ofEpochMilli(item.startTime).atZone(ZoneId.systemDefault()).toLocalTime()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val moved = item.copy(startTime = start, endTime = start + (item.endTime - item.startTime).coerceAtLeast(60_000L), sortOrder = target.items.size)
            trip.copy(days = trip.days.map { day -> when (day.id) {
                sourceDayId -> day.copy(items = day.items.filterNot { it.id == itemId })
                targetDayId -> day.copy(items = day.items + moved)
                else -> day
            } })
        })
    }

    fun moveItemWithTimeReview(tripId: String, dayId: String, itemId: String, targetIndex: Int): ItineraryMoveResult {
        var result = ItineraryMoveResult.UNCHANGED
        mutate { data ->
            data.copy(trips = data.trips.map { trip ->
                if (trip.id != tripId) return@map trip
                trip.copy(days = trip.days.map { day ->
                    if (day.id != dayId) return@map day
                    val original = day.items.sortedBy { it.sortOrder }
                    val from = original.indexOfFirst { it.id == itemId }
                    if (from < 0 || original.size < 2) return@map day
                    val to = targetIndex.coerceIn(0, original.lastIndex)
                    if (from == to) return@map day

                    val slots = original.filterNot { it.isTimePending }.map(::timeSlot)
                    val reordered = original.toMutableList().apply { add(to, removeAt(from)) }
                    var timedIndex = 0
                    val updatedItems = reordered.map { item ->
                        val slot = if (item.isTimePending) null else slots[timedIndex++]
                        if (item.isFixedTime || slot == null) item else {
                            val start = slotStart(day.date, slot)
                            item.copy(startTime = start, endTime = start + durationOf(item).coerceAtLeast(60_000L))
                        }
                    }.mapIndexed { index, item -> item.copy(sortOrder = index) }.normalizeItems()
                    result = ItineraryMoveResult(true, emptyList())
                    day.copy(items = updatedItems)
                })
            })
        }
        return result
    }

    fun applyTimeAdjustments(tripId: String, dayId: String, adjustments: List<ItineraryTimeAdjustment>) = mutate { data ->
        data.copy(trips = data.trips.map { trip ->
            if (trip.id != tripId) trip else trip.copy(days = trip.days.map { day ->
                if (day.id != dayId) day else day.copy(items = day.items.map { item ->
                    adjustments.firstOrNull { it.item.id == item.id && !item.isFixedTime && !item.isTimePending }?.let { adjustment ->
                        item.copy(startTime = adjustment.suggestedStartTime, endTime = adjustment.suggestedEndTime)
                    } ?: item
                })
            })
        })
    }

    private fun timeSlot(item: ItineraryItem): ItineraryTimeSlot {
        val local = java.time.Instant.ofEpochMilli(item.startTime).atZone(ZoneId.systemDefault())
        return ItineraryTimeSlot(local.hour * 60 + local.minute, durationOf(item))
    }

    private fun durationOf(item: ItineraryItem): Long = (item.endTime - item.startTime).coerceAtLeast(0L)

    private fun List<ItineraryItem>.normalizeItems(): List<ItineraryItem> =
        sortedWith(compareBy<ItineraryItem> { it.isTimePending }.thenBy { if (it.isTimePending) it.sortOrder.toLong() else it.startTime }.thenBy { it.id })
            .mapIndexed { index, item ->
                val safeEnd = maxOf(item.endTime, item.startTime + 60_000L)
                item.copy(endTime = safeEnd, sortOrder = index)
            }

    private fun normalizeSchedules(data: AppData): AppData = data.copy(
        trips = data.trips.map { trip -> trip.copy(days = trip.days.map { day -> day.copy(items = day.items.normalizeItems()) }) }
    )

    private fun slotStart(dayDate: Long, slot: ItineraryTimeSlot): Long {
        val date = dayDate.localDate()
        return date.atTime(slot.startMinute / 60, slot.startMinute % 60)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun suggestedRange(index: Int, slots: List<ItineraryTimeSlot>, dayDate: Long): Pair<Long, Long>? {
        if (index !in slots.indices) return null
        val slot = slots[index]
        val slotStart = slotStart(dayDate, slot)
        var start = slotStart
        var end = slotStart + slot.durationMillis
        if (index > 0) {
            val previous = slots[index - 1]
            start = maxOf(start, slotStart(dayDate, previous) + previous.durationMillis)
        }
        if (index < slots.lastIndex) {
            end = minOf(end, slotStart(dayDate, slots[index + 1]))
        }
        return if (end < start) slotStart to slotStart + slot.durationMillis else start to end
    }

    fun swapItemTimes(tripId: String, dayId: String, firstItemId: String, secondItemId: String) = mutate { data ->
        data.copy(trips = data.trips.map { trip ->
            if (trip.id != tripId) trip else trip.copy(days = trip.days.map { day ->
                if (day.id != dayId) day else {
                    val first = day.items.firstOrNull { it.id == firstItemId }
                    val second = day.items.firstOrNull { it.id == secondItemId }
                    if (first == null || second == null || first.isFixedTime || second.isFixedTime || first.isTimePending || second.isTimePending) day else day.copy(items = day.items.map { item ->
                        when (item.id) {
                            firstItemId -> item.copy(startTime = second.startTime, endTime = second.endTime)
                            secondItemId -> item.copy(startTime = first.startTime, endTime = first.endTime)
                            else -> item
                        }
                    })
                }
            })
        })
    }

    fun createStory(title: String, destination: String, startDate: Long, endDate: Long, summary: String = ""): TravelStory {
        val start = startDate.startOfDay(); val safeEnd = maxOf(start, endDate.startOfDay())
        val count = java.time.temporal.ChronoUnit.DAYS.between(start.localDate(), safeEnd.localDate()).toInt() + 1
        val days = (0 until count).map { index ->
            val date = start.localDate().plusDays(index.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            StoryDay(date = date, title = "第 ${index + 1} 天", sortOrder = index)
        }
        val story = TravelStory(title = title, destination = destination, startDate = start, endDate = safeEnd, summary = summary, days = days)
        mutate { it.copy(stories = it.stories + story) }
        return story
    }

    fun archiveFinishedTrips(now: Long = System.currentTimeMillis()) {
        val ended = _data.value.trips.filter { it.phase(now) == TripPhase.HISTORY }
        for (trip in ended) {
            if (!com.personal.triptrail.util.CloudSyncService.get(context).isDeleted("story:${trip.id.lowercase()}") && _data.value.stories.none { it.sourceTripId == trip.id }) archiveTrip(trip.id, automatic = true)
        }
    }

    fun archiveTrip(tripId: String, automatic: Boolean = false): TravelStory? {
        val trip = _data.value.trips.firstOrNull { it.id == tripId } ?: return null
        val existing = _data.value.stories.firstOrNull { it.sourceTripId == tripId }
        val story = (existing ?: TravelStory(
            id = if (automatic) trip.id else java.util.UUID.randomUUID().toString(),
            title = trip.title, destination = trip.destination, startDate = trip.startDate, endDate = trip.endDate,
            summary = trip.note, sourceTripId = trip.id
        )).copy(
            title = trip.title, destination = trip.destination, startDate = trip.startDate, endDate = trip.endDate,
            days = trip.days.sortedBy { it.sortOrder }.map { day ->
                val oldDay = existing?.days?.firstOrNull { it.sourceDayId == day.id }
                StoryDay(
                    id = oldDay?.id ?: java.util.UUID.randomUUID().toString(), date = day.date, title = day.title,
                    note = oldDay?.note.orEmpty(), details = oldDay?.details.orEmpty(), sortOrder = day.sortOrder, sourceDayId = day.id,
                    entries = day.items.sortedBy { it.sortOrder }.map { item ->
                        val old = oldDay?.entries?.firstOrNull { it.sourceItemId == item.id }
                        old?.copy(title = item.title, category = item.category, startTime = item.startTime.takeUnless { item.isTimePending }, endTime = item.endTime.takeUnless { item.isTimePending },
                            locationMode = item.locationMode, placeName = item.placeName, placeAddress = item.placeAddress,
                            originName = item.originName, originAddress = item.originAddress,
                            destinationName = item.destinationName, destinationAddress = item.destinationAddress,
                            timeLabel = item.timeRangeText, sortOrder = item.sortOrder)
                            ?: StoryEntry(title = item.title, category = item.category, startTime = item.startTime.takeUnless { item.isTimePending }, endTime = item.endTime.takeUnless { item.isTimePending },
                                locationMode = item.locationMode, placeName = item.placeName, placeAddress = item.placeAddress,
                                originName = item.originName, originAddress = item.originAddress,
                                destinationName = item.destinationName, destinationAddress = item.destinationAddress,
                                timeLabel = item.timeRangeText, sortOrder = item.sortOrder,
                                sourceItemId = item.id)
                    }
                )
            }
        )
        mutate { data -> data.copy(stories = if (existing == null) data.stories + story else data.stories.map { if (it.id == story.id) story else it }) }
        return story
    }

    fun updateStory(updated: TravelStory) = mutate { data -> data.copy(stories = data.stories.map { if (it.id == updated.id) updated else it }) }
    fun deleteStory(id: String) {
        com.personal.triptrail.util.CloudSyncService.get(context).trash(id, "story", this)
        cloudEditEvents.tryEmit(Unit)
    }

    fun addStoryDay(storyId: String): StoryDay? {
        var created: StoryDay? = null
        mutate { data -> data.copy(stories = data.stories.map { story ->
            if (story.id != storyId) story else {
                val last = story.days.maxByOrNull { it.sortOrder }
                val date = (last?.date ?: story.endDate).localDate().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                created = StoryDay(date = date, title = "第 ${story.days.size + 1} 天", sortOrder = story.days.size)
                story.copy(endDate = maxOf(story.endDate, date), days = story.days + created!!)
            }
        }) }
        return created
    }

    fun updateStoryDay(storyId: String, updated: StoryDay) = mutate { data -> data.copy(stories = data.stories.map { story ->
        if (story.id != storyId) story else {
            val previous = story.days.firstOrNull { it.id == updated.id }
            val dayShift = previous?.let { updated.date.startOfDay() - it.date.startOfDay() } ?: 0L
            val normalized = if (dayShift == 0L) updated else updated.copy(entries = updated.entries.map { entry ->
                entry.copy(startTime = entry.startTime?.plus(dayShift), endTime = entry.endTime?.plus(dayShift))
            })
            val days = story.days.map { if (it.id == normalized.id) normalized else it }
            story.copy(
                startDate = days.minOfOrNull { it.date.startOfDay() } ?: story.startDate,
                endDate = days.maxOfOrNull { it.date.startOfDay() } ?: story.endDate,
                days = days,
            )
        }
    }) }

    fun deleteStoryDay(storyId: String, dayId: String) = mutate { data -> data.copy(stories = data.stories.map { story ->
        if (story.id != storyId) story else {
            val days = story.days.filterNot { it.id == dayId }.mapIndexed { index, day -> day.copy(sortOrder = index) }
            story.copy(
                startDate = days.minOfOrNull { it.date.startOfDay() } ?: story.startDate,
                endDate = days.maxOfOrNull { it.date.startOfDay() } ?: story.endDate,
                days = days,
            )
        }
    }) }

    fun saveFavorite(favorite: ItineraryItem) = mutate { data ->
        val normalized = favorite.copy(isFavorite = true, executionStatus = ItineraryExecutionStatus.NOT_STARTED, isCompleted = false)
        val exists = data.favorites.any { it.id == favorite.id }
        data.copy(favorites = if (exists) data.favorites.map { if (it.id == favorite.id) normalized else it } else data.favorites + normalized)
    }

    fun deleteFavorite(id: String) {
        com.personal.triptrail.util.CloudSyncService.get(context).trash(id, "favorite", this)
        cloudEditEvents.tryEmit(Unit)
    }

    fun importFavorites(tripId: String, dayId: String, favoriteIds: Set<String>): Int {
        var count = 0
        mutate { data -> data.copy(trips = data.trips.map { trip ->
            if (trip.id != tripId) trip else trip.copy(days = trip.days.map { day ->
                if (day.id != dayId) day else {
                    val existing = day.items.mapNotNull { it.sourceFavoriteId }.toSet()
                    var updated = day
                    data.favorites.filter { it.id in favoriteIds && it.id !in existing }
                        .sortedByDescending { it.favoriteCreatedAt }.forEach { favorite ->
                            val item = favorite.importedFromFavorite(suggestedStart(updated)).copy(sortOrder = updated.items.size)
                            updated = updated.copy(items = updated.items + item)
                            count++
                        }
                    updated
                }
            })
        }) }
        return count
    }

    fun saveStoryEntry(storyId: String, dayId: String, entry: StoryEntry) = mutate { data -> data.copy(stories = data.stories.map { story ->
        if (story.id != storyId) story else story.copy(days = story.days.map { day ->
            if (day.id != dayId) day else {
                val exists = day.entries.any { it.id == entry.id }
                val normalized = entry.copy(sortOrder = if (exists) entry.sortOrder else day.entries.size)
                day.copy(entries = if (exists) day.entries.map { if (it.id == entry.id) normalized else it } else day.entries + normalized)
            }
        })
    }) }

    fun deleteStoryEntry(storyId: String, dayId: String, entryId: String) = mutate { data -> data.copy(stories = data.stories.map { story ->
        if (story.id != storyId) story else story.copy(days = story.days.map { day ->
            if (day.id != dayId) day else day.copy(entries = day.entries.filterNot { it.id == entryId }.mapIndexed { i, e -> e.copy(sortOrder = i) })
        })
    }) }

    fun importMedia(uri: Uri, kind: MediaKind): MediaReference {
        val extension = context.contentResolver.getType(uri)?.substringAfter('/')?.substringBefore('+') ?: if (kind == MediaKind.VIDEO) "mp4" else "jpg"
        val target = File(mediaDirectory, "${java.util.UUID.randomUUID()}.$extension")
        context.contentResolver.openInputStream(uri).use { input -> target.outputStream().use { output -> requireNotNull(input).copyTo(output) } }
        return MediaReference(localUri = Uri.fromFile(target).toString(), kind = kind)
    }

    private fun AppData.autoCompleteElapsed(now: Long = System.currentTimeMillis()): AppData = copy(trips = trips.map { trip -> trip.copy(days = trip.days.map { day ->
        day.copy(items = day.items.map { item -> item.withAutomaticExecutionStatus(now) })
    }) })
}
