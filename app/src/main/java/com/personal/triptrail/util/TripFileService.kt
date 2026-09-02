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
    fun restore(text: String): AppData = importBackup(text)

    /** Accepts both the Android JSON backup and the JSON content embedded by iOS. */
    fun importBackup(text: String): AppData {
        val root = JSONObject(text)
        require(root.optInt("formatVersion", 1) == 1) { "暂不支持此备份版本" }
        require(root.has("trips") || root.has("stories") || root.has("favorites")) { "这不是有效的旅迹备份文件" }
        return AppData(
            formatVersion = 1,
            trips = root.optJSONArray("trips")?.objects()?.map(::parseTrip).orEmpty(),
            stories = root.optJSONArray("stories")?.objects()?.map(::parseStory).orEmpty(),
            favorites = root.optJSONArray("favorites")?.objects()?.map(::parseItem).orEmpty(),
        )
    }

    fun shareTrip(trip: Trip): String = JSONObject().apply {
        put("format", "triptrail.shared-journey")
        put("formatVersion", 1)
        put("sharedAt", Instant.now().toString())
        put("kind", "trip")
        put("trip", tripJson(trip, includeLocalUris = false))
    }.toString(2)

    fun shareTripDay(trip: Trip, day: TripDay): String = shareTrip(
        trip.copy(
            title = "${trip.title} · ${day.title.ifBlank { day.date.chineseDateText() }}",
            startDate = day.date.startOfDay(),
            endDate = day.date.startOfDay(),
            days = listOf(day.copy(sortOrder = 0)),
        )
    )

    fun shareTripDays(trip: Trip, dayIds: Set<String>): String {
        return shareTrip(tripForSharing(trip, dayIds))
    }

    fun tripForSharing(trip: Trip, dayIds: Set<String>): Trip {
        val days = trip.days.sortedBy { it.sortOrder }.filter { it.id in dayIds }
        require(days.isNotEmpty()) { "请至少选择一天" }
        return trip.copy(
            startDate = days.minOf { it.date.startOfDay() },
            endDate = days.maxOf { it.date.startOfDay() },
            days = days.mapIndexed { index, day -> day.copy(sortOrder = index) },
        )
    }

    fun shareStory(story: TravelStory): String = JSONObject().apply {
        put("format", "triptrail.shared-journey")
        put("formatVersion", 1)
        put("sharedAt", Instant.now().toString())
        put("kind", "footprint")
        put("story", storyJson(story, includeLocalUris = false))
    }.toString(2)

    fun shareStoryDay(story: TravelStory, day: StoryDay): String = shareStory(
        story.copy(
            title = "${story.title} · ${day.title.ifBlank { day.date.chineseDateText() }}",
            startDate = day.date.startOfDay(),
            endDate = day.date.startOfDay(),
            days = listOf(day.copy(sortOrder = 0)),
        )
    )

    fun shareStoryDays(story: TravelStory, dayIds: Set<String>): String {
        return shareStory(storyForSharing(story, dayIds))
    }

    fun storyForSharing(story: TravelStory, dayIds: Set<String>): TravelStory {
        val days = story.days.sortedBy { it.sortOrder }.filter { it.id in dayIds }
        require(days.isNotEmpty()) { "请至少选择一天" }
        return story.copy(
            startDate = days.minOf { it.date.startOfDay() },
            endDate = days.maxOf { it.date.startOfDay() },
            days = days.mapIndexed { index, day -> day.copy(sortOrder = index) },
        )
    }

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
        put("address", item.address); put("note", item.note)
        put("locationModeRaw", item.locationMode.name.lowercase()); put("placeName", item.placeName); put("placeAddress", item.placeAddress)
        put("originName", item.originName); put("originAddress", item.originAddress); put("destinationName", item.destinationName); put("destinationAddress", item.destinationAddress)
        put("transportRaw", item.transport.label); put("distanceText", item.distanceText)
        put("playDurationMinutes", item.playDurationMinutes); put("reservationInfo", item.reservationInfo); put("cost", item.cost)
        put("isCompleted", item.isCompleted); put("executionStatusRaw", item.executionStatus.name.lowercase()); put("isAutomaticCompletionOverridden", item.isAutomaticCompletionOverridden); put("sortOrder", item.sortOrder)
        put("isFavorite", item.isFavorite); put("favoriteCreatedAt", Instant.ofEpochMilli(item.favoriteCreatedAt).toString()); put("sourceFavoriteID", item.sourceFavoriteId ?: JSONObject.NULL)
        put("media", JSONArray(if (includeLocalUris) item.media.map(::mediaJson) else emptyList<JSONObject>()))
    }

    private fun storyJson(story: TravelStory, includeLocalUris: Boolean) = JSONObject().apply {
        put("id", story.id); put("title", story.title); put("destination", story.destination)
        put("startDate", Instant.ofEpochMilli(story.startDate).toString()); put("endDate", Instant.ofEpochMilli(story.endDate).toString())
        put("summary", story.summary); put("createdAt", Instant.ofEpochMilli(story.createdAt).toString())
        put("coverZoom", story.coverZoom); put("coverOffsetX", story.coverOffsetX); put("coverOffsetY", story.coverOffsetY)
        put("coverMedia", if (includeLocalUris && story.coverMedia != null) mediaJson(story.coverMedia) else JSONObject.NULL)
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
        put("locationModeRaw", entry.locationMode.name.lowercase()); put("placeName", entry.placeName); put("placeAddress", entry.placeAddress)
        put("originName", entry.originName); put("originAddress", entry.originAddress); put("destinationName", entry.destinationName); put("destinationAddress", entry.destinationAddress)
        put("transportRaw", entry.transport.label); put("routeInfo", entry.routeInfo); put("cost", entry.cost); put("didPrefillSourceMemory", true)
        put("sourceMemoryPrefill", JSONObject.NULL); put("sortOrder", entry.sortOrder); put("sourceItemID", JSONObject.NULL); put("storyDayID", dayId)
        put("media", JSONArray(if (includeLocalUris) entry.media.map(::mediaJson) else emptyList<JSONObject>()))
    }

    private fun mediaJson(media: MediaReference) = JSONObject().apply {
        put("id", media.id); put("localIdentifier", media.localUri); put("kindRaw", if (media.kind == MediaKind.VIDEO) "video" else "image")
        put("caption", media.caption); put("createdAt", Instant.ofEpochMilli(media.createdAt).toString()); put("sortOrder", media.sortOrder)
    }

    private fun parseTrip(obj: JSONObject) = Trip(
        id = obj.id(), title = obj.optString("title"), destination = obj.optString("destination"),
        startDate = obj.date("startDate"), endDate = obj.date("endDate"), note = obj.optString("note"),
        createdAt = obj.dateOrNull("createdAt") ?: System.currentTimeMillis(),
        days = obj.optJSONArray("days")?.objects()?.map(::parseDay).orEmpty(),
    )

    private fun parseDay(obj: JSONObject) = TripDay(
        id = obj.id(), date = obj.date("date"), title = obj.optString("title"), note = obj.optString("note"), sortOrder = obj.optInt("sortOrder"),
        items = obj.optJSONArray("items")?.objects()?.map(::parseItem).orEmpty(),
    )

    private fun parseItem(obj: JSONObject) = ItineraryItem(
        id = obj.id(), title = obj.optString("title"), category = placeCategory(obj.stringFrom("categoryRaw", "category")),
        startTime = obj.date("startTime"), endTime = obj.date("endTime"), address = obj.optString("address"), note = obj.optString("note"),
        locationMode = arrangementLocationMode(obj.stringFrom("locationModeRaw", "locationMode")),
        placeName = obj.optString("placeName"), placeAddress = obj.optString("placeAddress"), originName = obj.optString("originName"), originAddress = obj.optString("originAddress"),
        destinationName = obj.optString("destinationName"), destinationAddress = obj.optString("destinationAddress"),
        transport = transportMode(obj.stringFrom("transportRaw", "transport")), distanceText = obj.optString("distanceText"), playDurationMinutes = obj.optInt("playDurationMinutes", 60),
        reservationInfo = obj.optString("reservationInfo"), cost = obj.optDouble("cost", 0.0), isCompleted = obj.optBoolean("isCompleted"),
        executionStatus = executionStatus(obj.stringFrom("executionStatusRaw", "executionStatus"), obj.optBoolean("isCompleted")),
        isAutomaticCompletionOverridden = obj.optBoolean("isAutomaticCompletionOverridden"), sortOrder = obj.optInt("sortOrder"), isFavorite = obj.optBoolean("isFavorite"),
        favoriteCreatedAt = obj.dateOrNull("favoriteCreatedAt") ?: System.currentTimeMillis(),
        sourceFavoriteId = obj.optionalString("sourceFavoriteID") ?: obj.optionalString("sourceFavoriteId"),
        media = obj.optJSONArray("media")?.objects()?.map(::parseMedia).orEmpty(),
    )

    private fun parseStory(obj: JSONObject): TravelStory {
        val dayObjects = obj.optJSONArray("days")?.objects().orEmpty()
        val entries = obj.optJSONArray("entries")?.objects().orEmpty()
        return TravelStory(
            id = obj.id(), title = obj.optString("title"), destination = obj.optString("destination"),
            startDate = obj.date("startDate"), endDate = obj.date("endDate"), summary = obj.optString("summary"),
            createdAt = obj.dateOrNull("createdAt") ?: System.currentTimeMillis(),
            sourceTripId = obj.optionalString("sourceTripID") ?: obj.optionalString("sourceTripId"),
            coverMedia = obj.optJSONObject("coverMedia")?.let(::parseMedia), coverZoom = obj.optDouble("coverZoom", 1.0),
            coverOffsetX = obj.optDouble("coverOffsetX", 0.0), coverOffsetY = obj.optDouble("coverOffsetY", 0.0),
            days = dayObjects.map { day -> StoryDay(
                id = day.id(), date = day.date("date"), title = day.optString("title"), note = day.optString("note"),
                details = day.optString("details"), sortOrder = day.optInt("sortOrder"), sourceDayId = day.optionalString("sourceDayID") ?: day.optionalString("sourceDayId"),
                entries = day.optJSONArray("entries")?.objects()?.map(::parseStoryEntry)
                    ?: entries.filter { (it.optionalString("storyDayID") ?: it.optionalString("storyDayId")) == day.id() }.map(::parseStoryEntry),
            ) }
        )
    }

    private fun parseStoryEntry(obj: JSONObject) = StoryEntry(
        id = obj.id(), title = obj.optString("title"), category = placeCategory(obj.stringFrom("categoryRaw", "category")),
        startTime = obj.dateOrNull("startTime"),
        endTime = obj.dateOrNull("endTime"), timeLabel = obj.optString("timeLabel"),
        address = obj.optString("address"), supplementalInfo = obj.optString("supplementalInfo"), note = obj.optString("note"),
        locationMode = arrangementLocationMode(obj.stringFrom("locationModeRaw", "locationMode")),
        placeName = obj.optString("placeName"), placeAddress = obj.optString("placeAddress"), originName = obj.optString("originName"), originAddress = obj.optString("originAddress"),
        destinationName = obj.optString("destinationName"), destinationAddress = obj.optString("destinationAddress"),
        transport = transportMode(obj.stringFrom("transportRaw", "transport")), routeInfo = obj.optString("routeInfo"), cost = obj.optDouble("cost", 0.0), sortOrder = obj.optInt("sortOrder"),
        sourceItemId = obj.optionalString("sourceItemID") ?: obj.optionalString("sourceItemId"),
        media = obj.optJSONArray("media")?.objects()?.map(::parseMedia).orEmpty(),
    )

    private fun parseMedia(obj: JSONObject) = MediaReference(
        id = obj.id(), localUri = obj.optString("localUri").ifBlank { obj.optString("localIdentifier") },
        kind = if (obj.optString("kindRaw").equals("video", true) || obj.optString("kind").equals("VIDEO", true)) MediaKind.VIDEO else MediaKind.IMAGE,
        caption = obj.optString("caption"),
        createdAt = obj.dateOrNull("createdAt") ?: System.currentTimeMillis(), sortOrder = obj.optInt("sortOrder"),
    )

    private fun placeCategory(raw: String): PlaceCategory =
        PlaceCategory.entries.firstOrNull { it.name.equals(raw, true) || it.label == raw } ?: PlaceCategory.fromLabel(raw)

    private fun transportMode(raw: String): TransportMode =
        TransportMode.entries.firstOrNull { it.name.equals(raw, true) || it.label == raw } ?: TransportMode.CAR

    private fun arrangementLocationMode(raw: String): ArrangementLocationMode =
        ArrangementLocationMode.entries.firstOrNull { it.name.equals(raw, true) || it.label == raw } ?: ArrangementLocationMode.SINGLE

    private fun executionStatus(raw: String, completed: Boolean): ItineraryExecutionStatus =
        ItineraryExecutionStatus.entries.firstOrNull { it.name.equals(raw, true) || it.label == raw }
            ?: if (completed) ItineraryExecutionStatus.COMPLETED else ItineraryExecutionStatus.NOT_STARTED

    private fun JSONObject.id(): String = optionalString("id") ?: java.util.UUID.randomUUID().toString()
    private fun JSONObject.stringFrom(primary: String, fallback: String): String =
        optString(primary).ifBlank { optString(fallback) }
    private fun JSONObject.optionalString(key: String): String? = opt(key)
        ?.takeUnless { it == JSONObject.NULL }
        ?.toString()
        ?.takeIf { it.isNotBlank() && it != "null" }

    private fun JSONObject.date(key: String): Long = dateOrNull(key) ?: error("日期字段 $key 无效")

    private fun JSONObject.dateOrNull(key: String): Long? = when (val value = opt(key)) {
        is Number -> value.toLong()
        is String -> value.toDoubleOrNull()?.toLong() ?: runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
        else -> null
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}
