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

class TripRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    private val dataFile = File(context.filesDir, "triptrail-data.json")
    private val mediaDirectory = File(context.filesDir, "media").apply { mkdirs() }
    private val _data = MutableStateFlow(load().autoCompleteElapsed())
    val data: StateFlow<AppData> = _data.asStateFlow()

    @Synchronized
    private fun mutate(block: (AppData) -> AppData) {
        val updated = block(_data.value).autoCompleteElapsed()
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
                day.copy(items = if (exists) day.items.map { if (it.id == item.id) normalized else it } else day.items + normalized)
            }
        })
    }) }

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
    fun addSampleData() {
        if (_data.value.trips.isNotEmpty() || _data.value.stories.isNotEmpty() || _data.value.favorites.isNotEmpty()) return
        val today = System.currentTimeMillis().startOfDay()
        fun date(offset: Int) = today.localDate().plusDays(offset.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        fun at(day: Long, hour: Int, minute: Int = 0) = day.localDate().atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        fun sampleTrip(title: String, destination: String, startOffset: Int, days: Int, note: String): Trip {
            val start = date(startOffset); val tripDays = (0 until days).map { index -> TripDay(date = date(startOffset + index), title = "第 ${index + 1} 天", sortOrder = index) }
            return Trip(title = title, destination = destination, startDate = start, endDate = date(startOffset + days - 1), note = note, days = tripDays)
        }

        val currentSeed = sampleTrip("杭州山水接川西", "杭州、川西", 0, 8, "从西湖晨光到龙井茶山，一段有路线、预约、花费和影像记录的完整示例旅程。")
        val firstDay = currentSeed.days.first()
        val current = currentSeed.copy(days = currentSeed.days.mapIndexed { index, day -> if (index != 0) day else day.copy(title = "抵达与安顿", items = listOf(
            ItineraryItem(title = "高铁前往杭州", category = PlaceCategory.TRANSPORT, startTime = at(day.date, 8), endTime = at(day.date, 9, 5), locationMode = ArrangementLocationMode.ROUTE, originName = "上海虹桥站", originAddress = "上海市闵行区申贵路1500号", destinationName = "杭州东站", destinationAddress = "杭州市上城区全福桥路2号", transport = TransportMode.TRAIN, reservationInfo = "G7311 · 08车12A", distanceText = "高铁 1 小时 5 分", cost = 73.0, note = "提前 30 分钟到站，抵达后从东广场出站。", executionStatus = ItineraryExecutionStatus.COMPLETED, isCompleted = true, sortOrder = 0),
            ItineraryItem(title = "办理酒店入住", category = PlaceCategory.HOTEL, startTime = at(day.date, 15), endTime = at(day.date, 16, 30), placeName = "杭州西湖湖滨酒店", placeAddress = "杭州市上城区湖滨路", address = "杭州市上城区湖滨路", transport = TransportMode.BUS, reservationInfo = "大床房 · 含早", distanceText = "地铁 1 号线·龙翔桥站", cost = 688.0, note = "先寄存行李，14:00 后取房卡。", executionStatus = ItineraryExecutionStatus.COMPLETED, isCompleted = true, sortOrder = 1),
            ItineraryItem(title = "整理随身物品", category = PlaceCategory.OTHER, startTime = at(day.date, 16, 40), endTime = at(day.date, 18), placeName = "杭州西湖湖滨酒店", distanceText = "酒店内", note = "只带相机、雨伞和充电宝，大件行李留在酒店。", executionStatus = ItineraryExecutionStatus.COMPLETED, isCompleted = true, sortOrder = 2),
        )) })
        val trips = listOf(
            current,
            sampleTrip("上海周末城市漫步", "上海", 4, 1, "用于查看即将出发状态、城市坐标与跨系统分享效果。"),
            sampleTrip("西湖慢游三日", "杭州", 15, 4, "沿着湖边慢慢走，给好吃的和晚霞留出时间。"),
            sampleTrip("苏州园林小住", "苏州", -18, 3, "把走过的园林和旧城巷子整理成回忆。"),
            sampleTrip("厦门海风四日", "厦门", -48, 4, "沿海散步，记下日落与老街。"),
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
        fun story(title: String, destination: String, offset: Int, summary: String, cover: Boolean = true): TravelStory {
            val media = MediaReference(localUri = Uri.fromFile(sampleImageFile).toString())
            val entry = StoryEntry(title = "旅途片段", placeName = destination, note = summary, media = if (cover) listOf(media.copy()) else emptyList())
            val day = StoryDay(date = date(offset), title = "第 1 天", entries = listOf(entry))
            return TravelStory(title = title, destination = destination, startDate = date(offset), endDate = date(offset + 2), summary = summary, coverMedia = if (cover) media else null, days = listOf(day))
        }
        val stories = listOf(
            story("杭州湖畔慢游（测试）", "杭州", -4, "从测试行程同步而来的足迹，用于验证源行程同步、照片视频和逐日记录。"),
            story("上海夜色收藏（测试）", "上海", -15, "独立创建的足迹，不关联任何行程，用于验证收藏导入后的编辑体验。"),
            story("厦门海风旧游（测试）", "厦门", -48, "海边花园与旧城散步。"),
            story("厦门海风手记（测试）", "厦门", -48, "纯文字足迹，用于检查没有媒体时的占位状态与长文本排版。", cover = false),
        )
        mutate { it.copy(trips = trips, favorites = favorites, stories = stories) }
    }

    private fun AppData.autoCompleteElapsed(now: Long = System.currentTimeMillis()): AppData = copy(trips = trips.map { trip -> trip.copy(days = trip.days.map { day ->
        day.copy(items = day.items.map { item -> item.withAutomaticExecutionStatus(now) })
    }) })
}
