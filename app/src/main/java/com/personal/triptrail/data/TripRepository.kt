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

    @Synchronized
    private fun mutate(block: (AppData) -> AppData) {
        val updated = normalizeSchedules(block(_data.value)).autoCompleteElapsed()
        _data.value = updated
        val temporary = File(dataFile.parentFile, "${dataFile.name}.tmp")
        temporary.writeText(json.encodeToString(updated))
        if (!temporary.renameTo(dataFile)) {
            dataFile.writeText(temporary.readText())
            temporary.delete()
        }
    }

    private fun load(): AppData = runCatching {
        if (dataFile.exists()) json.decodeFromString<AppData>(dataFile.readText()) else AppData()
    }.getOrElse { AppData() }

    fun replaceAll(data: AppData) = mutate { data }
    fun refreshAutomaticStatuses() {
        val current = _data.value
        val refreshed = current.autoCompleteElapsed()
        if (refreshed != current) mutate { refreshed }
    }
    fun exportJson(): String = json.encodeToString(_data.value)
    fun decodeJson(value: String): AppData = json.decodeFromString(value)

    fun createTrip(title: String, destination: String, startDate: Long, endDate: Long, note: String = ""): Trip {
        val safeEnd = maxOf(startDate.startOfDay(), endDate.startOfDay())
        val start = startDate.startOfDay()
        val totalDays = java.time.temporal.ChronoUnit.DAYS.between(start.localDate(), safeEnd.localDate()).toInt() + 1
        val days = (0 until totalDays).map { index ->
            val date = start.localDate().plusDays(index.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            TripDay(date = date, title = "第 ${index + 1} 天", sortOrder = index)
        }
        val trip = Trip(title = title, destination = destination, startDate = start, endDate = safeEnd, note = note, days = days)
        mutate { it.copy(trips = it.trips + trip) }
        return trip
    }

    fun updateTrip(updated: Trip) = mutate { data -> data.copy(trips = data.trips.map { if (it.id == updated.id) updated else it }) }
    fun deleteTrip(id: String) = mutate { data -> data.copy(trips = data.trips.filterNot { it.id == id }) }

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

    fun suggestedStart(day: TripDay): Long = day.items.maxByOrNull { it.sortOrder }?.endTime
        ?: day.date.localDate().atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun saveItem(tripId: String, dayId: String, item: ItineraryItem) = mutate { data -> data.copy(trips = data.trips.map { trip ->
        if (trip.id != tripId) trip else trip.copy(days = trip.days.map { day ->
            if (day.id != dayId) day else {
                val exists = day.items.any { it.id == item.id }
                val normalized = item.copy(
                    sortOrder = if (exists) item.sortOrder else day.items.size,
                    isAutomaticCompletionOverridden = false,
                ).withAutomaticExecutionStatus()
                day.copy(items = (if (exists) day.items.map { if (it.id == item.id) normalized else it } else day.items + normalized).normalizeItems())
            }
        })
    }) }

    fun appendRecognizedJourney(tripId: String, recognizedDays: List<RecognizedJourneyDay>, targetDayId: String? = null): Int {
        var added = 0
        mutate { data ->
            data.copy(trips = data.trips.map { trip ->
                if (trip.id != tripId) return@map trip
                val ordered = trip.days.sortedBy { it.sortOrder }.toMutableList()
                val target = targetDayId?.let { id -> ordered.firstOrNull { it.id == id } }
                recognizedDays.forEachIndexed { dayIndex, recognized ->
                    val day = target ?: run {
                        val plannedDate = recognized.date ?: trip.startDate.localDate().plusDays((recognized.sourceDayNumber - 1).coerceAtLeast(0).toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        ordered.firstOrNull { it.date.startOfDay() == plannedDate.startOfDay() } ?: TripDay(
                            date = plannedDate,
                            title = recognized.title.ifBlank { "第 ${recognized.sourceDayNumber} 天" },
                            sortOrder = ordered.size,
                        ).also { ordered += it }
                    }
                    val nextOrder = (day.items.maxOfOrNull { it.sortOrder } ?: -1) + 1
                    val items = recognized.items.mapIndexed { index, item ->
                        added++
                        val shift = target?.date?.startOfDay()?.minus(item.startTime.startOfDay()) ?: 0L
                        item.copy(
                            id = java.util.UUID.randomUUID().toString(),
                            startTime = item.startTime + shift,
                            endTime = item.endTime + shift,
                            sortOrder = nextOrder + index,
                            isAutomaticCompletionOverridden = false,
                        ).withAutomaticExecutionStatus()
                    }
                    val updatedDay = day.copy(
                        title = if (day.title.isBlank() || target == null) recognized.title.ifBlank { day.title } else day.title,
                        note = listOf(day.note, recognized.note).filter { it.isNotBlank() }.joinToString("\n"),
                        items = day.items + items,
                    )
                    val index = ordered.indexOfFirst { it.id == day.id }
                    if (index >= 0) ordered[index] = updatedDay
                }
                val normalized = ordered.mapIndexed { index, day -> day.copy(sortOrder = index) }
                trip.copy(
                    startDate = normalized.minOfOrNull { it.date.startOfDay() } ?: trip.startDate,
                    endDate = maxOf(trip.endDate, normalized.maxOfOrNull { it.date.startOfDay() } ?: trip.endDate),
                    days = normalized,
                )
            })
        }
        return added
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

                    val slots = original.map(::timeSlot)
                    val reordered = original.toMutableList().apply { add(to, removeAt(from)) }
                    val updatedItems = reordered.mapIndexed { index, item ->
                        if (item.isFixedTime) item else {
                            val slot = slots[index]
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
                    adjustments.firstOrNull { it.item.id == item.id }?.let { adjustment ->
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
        sortedWith(compareBy<ItineraryItem> { it.startTime }.thenBy { it.id })
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
                    if (first == null || second == null) day else day.copy(items = day.items.map { item ->
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

    fun archiveTrip(tripId: String): TravelStory? {
        val trip = _data.value.trips.firstOrNull { it.id == tripId } ?: return null
        val existing = _data.value.stories.firstOrNull { it.sourceTripId == tripId }
        val story = (existing ?: TravelStory(
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
                        old?.copy(title = item.title, category = item.category, startTime = item.startTime, endTime = item.endTime,
                            locationMode = item.locationMode, placeName = item.placeName, placeAddress = item.placeAddress,
                            originName = item.originName, originAddress = item.originAddress,
                            destinationName = item.destinationName, destinationAddress = item.destinationAddress,
                            timeLabel = "${item.startTime.timeText()} – ${item.endTime.timeText()}", sortOrder = item.sortOrder)
                            ?: StoryEntry(title = item.title, category = item.category, startTime = item.startTime, endTime = item.endTime,
                                locationMode = item.locationMode, placeName = item.placeName, placeAddress = item.placeAddress,
                                originName = item.originName, originAddress = item.originAddress,
                                destinationName = item.destinationName, destinationAddress = item.destinationAddress,
                                timeLabel = "${item.startTime.timeText()} – ${item.endTime.timeText()}", sortOrder = item.sortOrder,
                                sourceItemId = item.id)
                    }
                )
            }
        )
        mutate { data -> data.copy(stories = if (existing == null) data.stories + story else data.stories.map { if (it.id == story.id) story else it }) }
        return story
    }

    fun updateStory(updated: TravelStory) = mutate { data -> data.copy(stories = data.stories.map { if (it.id == updated.id) updated else it }) }
    fun deleteStory(id: String) = mutate { data -> data.copy(stories = data.stories.filterNot { it.id == id }) }

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

    fun deleteFavorite(id: String) = mutate { data -> data.copy(favorites = data.favorites.filterNot { it.id == id }) }

    fun importFavorites(tripId: String, dayId: String, favoriteIds: Set<String>) {
        var day = _data.value.trips.firstOrNull { it.id == tripId }?.days?.firstOrNull { it.id == dayId } ?: return
        _data.value.favorites.filter { it.id in favoriteIds }.sortedBy { it.favoriteCreatedAt }.forEach { favorite ->
            val start = suggestedStart(day)
            val copy = favorite.importedFromFavorite(start)
            saveItem(tripId, dayId, copy)
            day = _data.value.trips.first { it.id == tripId }.days.first { it.id == dayId }
        }
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

    @SuppressLint("ResourceType")
    fun addSampleData(): Boolean {
        // The first Android seed accidentally created several empty days/trips. Repair only
        // that recognizable seed; never overwrite arbitrary user data.
        if (_data.value.hasLegacyIncompleteSample()) {
            mutate { sampleData() }
            return true
        }
        // Keep existing user content, while making the sample action useful even after the
        // user has already created/imported something. Add each missing sample by title so a
        // second tap remains idempotent instead of duplicating the demo content.
        val seed = sampleData()
        val current = _data.value
        val merged = current.copy(
            trips = current.trips + seed.trips.filterNot { sample -> current.trips.any { it.title == sample.title } },
            stories = current.stories + seed.stories.filterNot { sample -> current.stories.any { it.title == sample.title } },
            favorites = current.favorites + seed.favorites.filterNot { sample -> current.favorites.any { it.title == sample.title } },
        )
        if (merged == current) return false
        mutate { merged }
        return true
    }

    private fun AppData.hasLegacyIncompleteSample(): Boolean {
        val legacyTitles = setOf("杭州山水接川西", "上海周末城市漫步", "西湖慢游三日", "苏州园林小住", "厦门海风四日")
        return trips.size == 5 && trips.map { it.title }.toSet() == legacyTitles &&
            trips.first().days.size == 8 && trips.first().days.drop(1).all { it.items.isEmpty() } &&
            trips.drop(1).all { trip -> trip.days.all { it.items.isEmpty() } }
    }

    private fun sampleData(): AppData {
        val today = System.currentTimeMillis().startOfDay()
        fun date(offset: Int) = today.localDate().plusDays(offset.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        fun at(day: Long, hour: Int, minute: Int = 0) = day.localDate().atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        fun item(day: Long, title: String, category: PlaceCategory, start: String, end: String, order: Int,
                 place: String = title, placeAddress: String = "", note: String = "", transport: TransportMode = TransportMode.CAR,
                 distance: String = "", duration: Int = 60, reservation: String = "", cost: Double = 0.0,
                 completed: Boolean = false, media: List<MediaReference> = emptyList()) = ItineraryItem(
            title = title, category = category, startTime = at(day, start.substringBefore(':').toInt(), start.substringAfter(':').toInt()),
            endTime = at(day, end.substringBefore(':').toInt(), end.substringAfter(':').toInt()), address = placeAddress, note = note,
            placeName = place, placeAddress = placeAddress, transport = transport, distanceText = distance,
            playDurationMinutes = duration, reservationInfo = reservation, cost = cost, isCompleted = completed,
            executionStatus = if (completed) ItineraryExecutionStatus.COMPLETED else ItineraryExecutionStatus.NOT_STARTED,
            sortOrder = order, media = media,
        )

        val favorites = listOf(
            ItineraryItem(title = "羊卓雍措", category = PlaceCategory.ATTRACTION, placeName = "羊卓雍措", note = "2号观景台更出片", cost = 100.0, isFavorite = true, favoriteCreatedAt = System.currentTimeMillis() - 3_000),
            ItineraryItem(title = "品尝杭帮菜", category = PlaceCategory.RESTAURANT, placeName = "楼外楼（孤山店）", note = "尝试西湖醋鱼和龙井虾仁，用餐后可在孤山稍作休息。", distanceText = "步行 900 米", cost = 328.0, isFavorite = true, favoriteCreatedAt = System.currentTimeMillis() - 2_000),
            ItineraryItem(title = "Wk Road", category = PlaceCategory.ATTRACTION, placeName = "Shanghai Wukang Road", isFavorite = true, favoriteCreatedAt = System.currentTimeMillis() - 1_000),
        )
        val sampleImageFile = File(mediaDirectory, "journey-lake-sample.png")
        runCatching {
            if (!sampleImageFile.exists()) context.resources.openRawResource(R.drawable.journey_lake_hero).use { input -> sampleImageFile.outputStream().use { input.copyTo(it) } }
        }
        val lakeMedia = MediaReference(localUri = Uri.fromFile(sampleImageFile).toString())
        val currentStart = date(-1); val currentToday = date(0); val currentEnd = date(1)
        val current = Trip(
            title = "杭州湖畔慢游（测试）", destination = "杭州", startDate = currentStart, endDate = currentEnd,
            note = "覆盖路线、预约、费用、导航、照片和视频的完整测试旅程。",
            days = listOf(
                TripDay(date = currentStart, title = "抵达杭州", sortOrder = 0, items = listOf(
                    item(currentStart, "抵达杭州东站", PlaceCategory.TRANSPORT, "09:10", "09:40", 0, "杭州东站", note = "从东广场出站，乘地铁前往酒店。", transport = TransportMode.TRAIN, distance = "高铁 1 小时 5 分", duration = 30, reservation = "G7311 · 08车12A", cost = 73.0, completed = true),
                    item(currentStart, "入住湖滨酒店", PlaceCategory.HOTEL, "10:30", "11:00", 1, "湖滨酒店", note = "先寄存行李，下午两点后领取房卡。", transport = TransportMode.BUS, distance = "地铁 6 站", duration = 30, reservation = "预订号 TT20260830", cost = 688.0, completed = true),
                    item(currentStart, "补充旅行用品", PlaceCategory.OTHER, "11:10", "11:25", 2, "湖滨银泰 in77", note = "检查充电宝、纸巾和备用电池。", transport = TransportMode.WALK, distance = "步行 350 米", duration = 15, cost = 96.5),
                )),
                TripDay(date = currentToday, title = "西湖环线", sortOrder = 1, items = listOf(
                    item(currentToday, "游览断桥残雪", PlaceCategory.ATTRACTION, "08:00", "09:30", 0, "断桥残雪", note = "从北山街慢慢走到平湖秋月，拍一段湖面晨光。", transport = TransportMode.WALK, distance = "步行 1.8 公里", duration = 90, reservation = "无需预约", media = listOf(lakeMedia)),
                    item(currentToday, "在楼外楼用餐", PlaceCategory.RESTAURANT, "11:30", "13:00", 1, "楼外楼（孤山店）", note = "预留临窗位，尝试西湖醋鱼与龙井虾仁。", transport = TransportMode.WALK, distance = "步行 900 米", duration = 90, reservation = "12:00 · 2人 · 手机尾号 0830", cost = 328.0),
                    item(currentToday, "购买旅行伴手礼", PlaceCategory.OTHER, "15:20", "17:00", 2, "河坊街", note = "茶叶和桂花糕控制在一个手提袋内。", transport = TransportMode.RIDE, distance = "骑行 3.2 公里", duration = 100, cost = 180.0),
                )),
                TripDay(date = currentEnd, title = "茶园与返程", sortOrder = 2, items = listOf(
                    item(currentEnd, "漫步龙井村茶园", PlaceCategory.SPECIAL, "09:00", "11:30", 0, "龙井村茶园", note = "天气合适就沿十里琅珰走一小段。", transport = TransportMode.CAR, distance = "驾车约 11 公里", duration = 150, reservation = "茶室预约 09:30", cost = 120.0),
                    item(currentEnd, "乘坐返程高铁", PlaceCategory.TRANSPORT, "17:05", "18:10", 1, "杭州东站", note = "提前四十分钟到站。", transport = TransportMode.TRAIN, distance = "高铁 1 小时 5 分", duration = 65, reservation = "G7590 · 05车06F", cost = 73.0),
                )),
            ),
        )
        val upcomingDay = date(7)
        val upcoming = Trip(
            title = "上海周末城市漫步（测试）", destination = "上海", startDate = upcomingDay, endDate = date(8),
            note = "用于查看即将出发状态与跨系统分享效果。", days = listOf(TripDay(date = upcomingDay, title = "建筑与夜色", sortOrder = 0, items = listOf(
                item(upcomingDay, "虹桥机场集合", PlaceCategory.TRANSPORT, "08:00", "09:00", 0, "上海虹桥国际机场 T2", "上海市长宁区虹桥路2550号", "测试飞机交通方式与预约信息。", TransportMode.FLIGHT, "机场线", 60, "MU5101 · 登机口 C52", 860.0),
                item(upcomingDay, "外滩夜景", PlaceCategory.ATTRACTION, "18:30", "20:30", 1, "外滩", "上海市黄浦区中山东一路", "蓝调时刻前到达，测试城市照片展示。", TransportMode.BUS, "公交约 25 分钟", 120),
            ))))
        val historyDay = date(-45)
        val history = Trip(
            title = "厦门海风旧游（测试）", destination = "厦门", startDate = historyDay, endDate = date(-42),
            note = "用于查看历史旅程、全部完成进度与归档入口。", days = listOf(TripDay(date = historyDay, title = "鼓浪屿一日", sortOrder = 0, items = listOf(
                item(historyDay, "乘船前往鼓浪屿", PlaceCategory.TRANSPORT, "08:10", "08:35", 0, "厦门邮轮中心厦鼓码头", note = "刷身份证登船。", transport = TransportMode.CAR, distance = "轮渡约 25 分钟", duration = 25, reservation = "08:10 船票", cost = 35.0, completed = true),
                item(historyDay, "游览菽庄花园与钢琴博物馆", PlaceCategory.ATTRACTION, "09:20", "11:40", 1, "菽庄花园与钢琴博物馆", note = "旧旅程全部完成。", transport = TransportMode.WALK, distance = "步行 1.4 公里", duration = 140, cost = 30.0, completed = true),
            ))))
        val trips = listOf(current, upcoming, history)

        fun story(title: String, destination: String, offset: Int, summary: String, cover: Boolean = true): TravelStory {
            val media = lakeMedia.copy(id = java.util.UUID.randomUUID().toString())
            val entry = StoryEntry(title = "旅途片段", placeName = destination, note = summary, media = if (cover) listOf(media.copy()) else emptyList())
            val day = StoryDay(date = date(offset), title = "第 1 天", entries = listOf(entry))
            return TravelStory(title = title, destination = destination, startDate = date(offset), endDate = date(offset + 2), summary = summary, coverMedia = if (cover) media else null, days = listOf(day))
        }
        val stories = listOf(
            story("杭州湖畔慢游 · 足迹（测试）", "杭州", 0, "从测试旅程同步而来的足迹，用于验证源旅程同步、照片视频和逐日记录。"),
            story("上海夜色收藏（测试）", "上海", -12, "独立创建的足迹，不关联任何旅程，用于验证收藏导入后的编辑体验。"),
            story("厦门海风手记（测试）", "厦门", -45, "纯文字足迹，用于检查没有媒体时的占位状态与长文本排版。", cover = false),
        )
        return AppData(trips = trips, favorites = favorites, stories = stories)
    }

    private fun AppData.autoCompleteElapsed(now: Long = System.currentTimeMillis()): AppData = copy(trips = trips.map { trip -> trip.copy(days = trip.days.map { day ->
        day.copy(items = day.items.map { item -> item.withAutomaticExecutionStatus(now) })
    }) })
}
