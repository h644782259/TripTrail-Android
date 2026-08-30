package com.personal.triptrail.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.ZoneId

class TripRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    private val dataFile = File(context.filesDir, "triptrail-data.json")
    private val mediaDirectory = File(context.filesDir, "media").apply { mkdirs() }
    private val _data = MutableStateFlow(load().autoCompleteElapsed())
    val data: StateFlow<AppData> = _data.asStateFlow()

    @Synchronized
    private fun mutate(block: (AppData) -> AppData) {
        val updated = block(_data.value)
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
        if (trip.id == tripId) trip.copy(days = trip.days.map { if (it.id == updated.id) updated else it }) else trip
    }) }

    fun deleteDay(tripId: String, dayId: String) = mutate { data -> data.copy(trips = data.trips.map { trip ->
        if (trip.id == tripId) trip.copy(days = trip.days.filterNot { it.id == dayId }.mapIndexed { i, d -> d.copy(sortOrder = i) }) else trip
    }) }

    fun suggestedStart(day: TripDay): Long = day.items.maxByOrNull { it.sortOrder }?.endTime
        ?: day.date.localDate().atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun saveItem(tripId: String, dayId: String, item: ItineraryItem) = mutate { data -> data.copy(trips = data.trips.map { trip ->
        if (trip.id != tripId) trip else trip.copy(days = trip.days.map { day ->
            if (day.id != dayId) day else {
                val exists = day.items.any { it.id == item.id }
                val normalized = item.copy(sortOrder = if (exists) item.sortOrder else day.items.size)
                day.copy(items = if (exists) day.items.map { if (it.id == item.id) normalized else it } else day.items + normalized)
            }
        })
    }) }

    fun deleteItem(tripId: String, dayId: String, itemId: String) = mutate { data -> data.copy(trips = data.trips.map { trip ->
        if (trip.id != tripId) trip else trip.copy(days = trip.days.map { day ->
            if (day.id != dayId) day else day.copy(items = day.items.filterNot { it.id == itemId }.mapIndexed { i, item -> item.copy(sortOrder = i) })
        })
    }) }

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
                            timeLabel = "${item.startTime.timeText()} – ${item.endTime.timeText()}", sortOrder = item.sortOrder)
                            ?: StoryEntry(title = item.title, category = item.category, startTime = item.startTime, endTime = item.endTime,
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

    fun addSampleData() {
        if (_data.value.trips.isNotEmpty()) return
        val today = System.currentTimeMillis().startOfDay()
        val trip = createTrip("西湖慢游三日", "杭州", today, today.localDate().plusDays(2).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), "沿湖慢慢走，给美食和晚霞留出时间。")
        val current = _data.value.trips.first { it.id == trip.id }
        val samples = listOf(listOf("断桥残雪", "孤山公园"), listOf("灵隐寺", "龙井村"), listOf("九溪烟树"))
        current.days.forEachIndexed { dayIndex, day -> samples[dayIndex].forEachIndexed { itemIndex, title ->
            val start = day.date.localDate().atTime(9 + itemIndex * 4, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            saveItem(trip.id, day.id, ItineraryItem(title = title,
                category = if (title == "龙井村") PlaceCategory.RESTAURANT else PlaceCategory.ATTRACTION,
                startTime = start, endTime = start + 7_200_000, address = "杭州 · $title", note = "到达后可以补充照片和当时的感受。"))
        } }
    }

    private fun AppData.autoCompleteElapsed(): AppData = copy(trips = trips.map { trip -> trip.copy(days = trip.days.map { day ->
        day.copy(items = day.items.map { item ->
            if (!item.isCompleted && !item.isAutomaticCompletionOverridden && item.endTime < System.currentTimeMillis()) item.copy(isCompleted = true) else item
        })
    }) })
}
