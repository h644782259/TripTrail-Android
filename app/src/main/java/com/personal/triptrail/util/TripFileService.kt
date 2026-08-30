package com.personal.triptrail.util

import com.personal.triptrail.data.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

object TripFileService {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    fun backup(data: AppData): String = json.encodeToString(data)
    fun restore(text: String): AppData = json.decodeFromString(text)

    fun shareTrip(trip: Trip): String = JSONObject().apply {
        put("format", "triptrail.shared-journey")
        put("formatVersion", 1)
        put("sharedAt", Instant.now().toString())
        put("kind", "trip")
        put("trip", tripJson(trip, includeLocalUris = false))
    }.toString(2)

    fun shareStory(story: TravelStory): String = JSONObject().apply {
        put("format", "triptrail.shared-journey")
        put("formatVersion", 1)
        put("sharedAt", Instant.now().toString())
        put("kind", "footprint")
        put("story", storyJson(story, includeLocalUris = false))
    }.toString(2)

    fun importShared(text: String): Pair<Trip?, TravelStory?> {
        val root = JSONObject(text)
        require(root.optString("format") == "triptrail.shared-journey" && root.optInt("formatVersion") == 1) { "不支持的旅迹分享文件" }
        return when (root.getString("kind")) {
            "trip" -> parseTrip(root.getJSONObject("trip")) to null
            "footprint" -> null to parseStory(root.getJSONObject("story"))
            else -> error("分享文件内容无效")
        }
    }

    private fun tripJson(trip: Trip, includeLocalUris: Boolean) = JSONObject().apply {
        put("id", trip.id); put("title", trip.title); put("destination", trip.destination)
        put("startDate", Instant.ofEpochMilli(trip.startDate).toString()); put("endDate", Instant.ofEpochMilli(trip.endDate).toString())
        put("note", trip.note); put("createdAt", Instant.ofEpochMilli(trip.createdAt).toString())
        put("days", JSONArray(trip.days.sortedBy { it.sortOrder }.map { dayJson(it, includeLocalUris) }))
    }

    private fun dayJson(day: TripDay, includeLocalUris: Boolean) = JSONObject().apply {
        put("id", day.id); put("date", Instant.ofEpochMilli(day.date).toString()); put("title", day.title); put("note", day.note); put("sortOrder", day.sortOrder)
        put("items", JSONArray(day.items.sortedBy { it.sortOrder }.map { itemJson(it, includeLocalUris) }))
    }

    private fun itemJson(item: ItineraryItem, includeLocalUris: Boolean) = JSONObject().apply {
        put("id", item.id); put("title", item.title); put("categoryRaw", item.category.label)
        put("startTime", Instant.ofEpochMilli(item.startTime).toString()); put("endTime", Instant.ofEpochMilli(item.endTime).toString())
        put("address", item.address); put("note", item.note); put("transportRaw", item.transport.label); put("distanceText", item.distanceText)
        put("playDurationMinutes", item.playDurationMinutes); put("reservationInfo", item.reservationInfo); put("cost", item.cost)
        put("isCompleted", item.isCompleted); put("isAutomaticCompletionOverridden", item.isAutomaticCompletionOverridden); put("sortOrder", item.sortOrder)
        put("media", JSONArray(if (includeLocalUris) item.media.map(::mediaJson) else emptyList<JSONObject>()))
    }

    private fun storyJson(story: TravelStory, includeLocalUris: Boolean) = JSONObject().apply {
        put("id", story.id); put("title", story.title); put("destination", story.destination)
        put("startDate", Instant.ofEpochMilli(story.startDate).toString()); put("endDate", Instant.ofEpochMilli(story.endDate).toString())
        put("summary", story.summary); put("createdAt", Instant.ofEpochMilli(story.createdAt).toString())
        put("sourceTripID", JSONObject.NULL); put("syncScopeRaw", "trip"); put("sourceSelectionIDsRaw", "")
        put("days", JSONArray(story.days.sortedBy { it.sortOrder }.map { storyDayJson(it) }))
        put("entries", JSONArray(story.days.sortedBy { it.sortOrder }.flatMap { day -> day.entries.sortedBy { it.sortOrder }.map { storyEntryJson(it, day.id, includeLocalUris) } }))
    }

    private fun storyDayJson(day: StoryDay) = JSONObject().apply {
        put("id", day.id); put("date", Instant.ofEpochMilli(day.date).toString()); put("title", day.title); put("note", day.note); put("details", day.details)
        put("didMigrateInlineSummary", true); put("sortOrder", day.sortOrder); put("sourceDayID", JSONObject.NULL)
    }

    private fun storyEntryJson(entry: StoryEntry, dayId: String, includeLocalUris: Boolean) = JSONObject().apply {
        put("id", entry.id); put("title", entry.title); put("categoryRaw", entry.category.label)
        put("startTime", entry.startTime?.let { Instant.ofEpochMilli(it).toString() } ?: JSONObject.NULL)
        put("endTime", entry.endTime?.let { Instant.ofEpochMilli(it).toString() } ?: JSONObject.NULL)
        put("timeLabel", entry.timeLabel); put("address", entry.address); put("supplementalInfo", entry.supplementalInfo); put("note", entry.note)
        put("transportRaw", entry.transport.label); put("routeInfo", entry.routeInfo); put("cost", entry.cost); put("didPrefillSourceMemory", true)
        put("sourceMemoryPrefill", JSONObject.NULL); put("sortOrder", entry.sortOrder); put("sourceItemID", JSONObject.NULL); put("storyDayID", dayId)
        put("media", JSONArray(if (includeLocalUris) entry.media.map(::mediaJson) else emptyList<JSONObject>()))
    }

    private fun mediaJson(media: MediaReference) = JSONObject().apply {
        put("id", media.id); put("localIdentifier", media.localUri); put("kindRaw", if (media.kind == MediaKind.VIDEO) "video" else "image")
        put("caption", media.caption); put("createdAt", Instant.ofEpochMilli(media.createdAt).toString()); put("sortOrder", media.sortOrder)
    }

    private fun parseTrip(obj: JSONObject) = Trip(
        id = obj.getString("id"), title = obj.getString("title"), destination = obj.optString("destination"),
        startDate = instant(obj.getString("startDate")), endDate = instant(obj.getString("endDate")), note = obj.optString("note"),
        createdAt = instant(obj.optString("createdAt", Instant.now().toString())),
        days = obj.getJSONArray("days").objects().map(::parseDay)
    )

    private fun parseDay(obj: JSONObject) = TripDay(
        id = obj.getString("id"), date = instant(obj.getString("date")), title = obj.optString("title"), note = obj.optString("note"), sortOrder = obj.optInt("sortOrder"),
        items = obj.optJSONArray("items")?.objects()?.map(::parseItem).orEmpty()
    )

    private fun parseItem(obj: JSONObject) = ItineraryItem(
        id = obj.getString("id"), title = obj.getString("title"), category = PlaceCategory.fromLabel(obj.optString("categoryRaw")),
        startTime = instant(obj.getString("startTime")), endTime = instant(obj.getString("endTime")), address = obj.optString("address"), note = obj.optString("note"),
        transport = TransportMode.fromLabel(obj.optString("transportRaw")), distanceText = obj.optString("distanceText"), playDurationMinutes = obj.optInt("playDurationMinutes", 60),
        reservationInfo = obj.optString("reservationInfo"), cost = obj.optDouble("cost", 0.0), isCompleted = obj.optBoolean("isCompleted"),
        isAutomaticCompletionOverridden = obj.optBoolean("isAutomaticCompletionOverridden"), sortOrder = obj.optInt("sortOrder")
    )

    private fun parseStory(obj: JSONObject): TravelStory {
        val dayObjects = obj.getJSONArray("days").objects()
        val entries = obj.optJSONArray("entries")?.objects().orEmpty()
        return TravelStory(
            id = obj.getString("id"), title = obj.getString("title"), destination = obj.optString("destination"),
            startDate = instant(obj.getString("startDate")), endDate = instant(obj.getString("endDate")), summary = obj.optString("summary"),
            createdAt = instant(obj.optString("createdAt", Instant.now().toString())),
            days = dayObjects.map { day -> StoryDay(
                id = day.getString("id"), date = instant(day.getString("date")), title = day.optString("title"), note = day.optString("note"),
                details = day.optString("details"), sortOrder = day.optInt("sortOrder"),
                entries = entries.filter { it.optString("storyDayID") == day.getString("id") }.map(::parseStoryEntry)
            ) }
        )
    }

    private fun parseStoryEntry(obj: JSONObject) = StoryEntry(
        id = obj.getString("id"), title = obj.getString("title"), category = PlaceCategory.fromLabel(obj.optString("categoryRaw")),
        startTime = obj.optString("startTime").takeIf { it.isNotBlank() && it != "null" }?.let(::instant),
        endTime = obj.optString("endTime").takeIf { it.isNotBlank() && it != "null" }?.let(::instant), timeLabel = obj.optString("timeLabel"),
        address = obj.optString("address"), supplementalInfo = obj.optString("supplementalInfo"), note = obj.optString("note"),
        transport = TransportMode.fromLabel(obj.optString("transportRaw")), routeInfo = obj.optString("routeInfo"), cost = obj.optDouble("cost", 0.0), sortOrder = obj.optInt("sortOrder")
    )

    private fun instant(value: String): Long = Instant.parse(value).toEpochMilli()
    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}
