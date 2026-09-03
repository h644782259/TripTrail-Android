package com.personal.triptrail.util

import com.personal.triptrail.data.ItineraryItem
import com.personal.triptrail.data.PlaceCategory
import com.personal.triptrail.data.TransportMode
import com.personal.triptrail.data.combineDateAndTime
import com.personal.triptrail.data.RecognizedJourneyDay
import com.personal.triptrail.data.localDate
import java.time.LocalDate
import java.util.UUID

object SmartImportParser {
    private val timeRange = Regex("(?:^|\\s)([0-2]?\\d[:：][0-5]\\d)\\s*[-—–~至到]\\s*([0-2]?\\d[:：][0-5]\\d)")
    private val singleTime = Regex("(?:^|\\s)([0-2]?\\d[:：][0-5]\\d)")
    private val money = Regex("(?:¥|￥|金额|合计|实付|费用)\\s*[:：]?\\s*(\\d+(?:\\.\\d{1,2})?)")
    private val distance = Regex("(\\d+(?:\\.\\d+)?\\s*(?:公里|km|KM|米)(?:\\s*[·•/]\\s*\\d+\\s*(?:分钟|min))?)")
    private val order = Regex("(?:订单号|订单编号|预订号|确认号)\\s*[:：]?\\s*([A-Za-z0-9-]+)")
    private val dayHeader = Regex("(?i)^(?:day\\s*(\\d+)|第\\s*(\\d+)\\s*天)\\s*[:：、.-]?\\s*(.*)$")
    private val dateOnly = Regex("^(?:(\\d{4})[-/年](\\d{1,2})[-/月](\\d{1,2})日?|(?:(\\d{1,2})月)?(\\d{1,2})日)$")
    private val arrangementStart = Regex("^(?:[-•·*]\\s*)?(?:[0-2]?\\d[:：][0-5]\\d(?:\\s*[-—~至到]\\s*[0-2]?\\d[:：][0-5]\\d)?\\s*)?.{2,}$")

    fun parseJourney(text: String, referenceDate: Long): List<RecognizedJourneyDay> {
        val lines = text.replace('\r', '\n').lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        data class Bucket(val number: Int, var date: Long?, var title: String, val lines: MutableList<String>)
        val buckets = mutableListOf<Bucket>()
        var current = Bucket(1, null, "", mutableListOf())
        var sawDayHeader = false
        var nextNumber = 1
        fun flush() {
            if (current.lines.isNotEmpty() || buckets.isEmpty()) buckets += current
        }
        lines.forEach { line ->
            val header = dayHeader.matchEntire(line)
            if (header != null) {
                flush()
                val number = (header.groupValues[1].ifBlank { header.groupValues[2] }).toIntOrNull() ?: nextNumber
                current = Bucket(number, null, header.groupValues[3].trim(), mutableListOf())
                nextNumber = number + 1
                sawDayHeader = true
            } else {
                val parsedDate = parseDateOnly(line, referenceDate)
                if (parsedDate != null) {
                    current.date = parsedDate
                } else {
                    current.lines += line
                }
            }
        }
        flush()
        if (!sawDayHeader && buckets.size == 1) {
            val blocks = splitArrangementBlocks(buckets.single().lines)
            return listOf(RecognizedJourneyDay(1, buckets.single().date ?: referenceDate, buckets.single().title, "", blocks.mapIndexed { index, block ->
                parse(block.joinToString("\n"), buckets.single().date ?: referenceDate, suggestedStartFor(referenceDate, index))
            }))
        }
        return buckets.mapIndexedNotNull { index, bucket ->
            val dayDate = bucket.date ?: referenceDate.localDate().plusDays((bucket.number - 1).coerceAtLeast(0).toLong()).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val blocks = splitArrangementBlocks(bucket.lines)
            val items = blocks.mapIndexed { itemIndex, block -> parse(block.joinToString("\n"), dayDate, suggestedStartFor(dayDate, itemIndex)) }
            if (items.isEmpty()) null else RecognizedJourneyDay(bucket.number.takeIf { it > 0 } ?: index + 1, bucket.date, bucket.title, "", items)
        }
    }

    private fun splitArrangementBlocks(lines: List<String>): List<List<String>> {
        if (lines.isEmpty()) return emptyList()
        val blocks = mutableListOf<MutableList<String>>()
        lines.forEach { line ->
            val starts = line.matches(arrangementStart) && (timeRange.containsMatchIn(line) || singleTime.containsMatchIn(line) || blocks.isEmpty())
            if (starts) blocks += mutableListOf(line) else if (blocks.isNotEmpty()) blocks.last() += line
        }
        return blocks.filter { it.any { line -> line.length >= 2 } }
    }

    private fun suggestedStartFor(day: Long, index: Int): Long = day.localDate().atTime(9 + index.coerceAtMost(8), 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun parseDateOnly(value: String, referenceDate: Long): Long? {
        val match = dateOnly.matchEntire(value) ?: return null
        val year = match.groupValues[1].toIntOrNull() ?: referenceDate.localDate().year
        val month = match.groupValues[2].ifBlank { match.groupValues[4] }.toIntOrNull() ?: return null
        val day = match.groupValues[3].ifBlank { match.groupValues[5] }.toIntOrNull() ?: return null
        return runCatching { LocalDate.of(year, month, day).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
    }

    fun parse(text: String, targetDay: Long, suggestedStart: Long): ItineraryItem {
        val clean = text.replace('\r', '\n').lines().map { it.trim() }.filter { it.isNotBlank() }
        val joined = clean.joinToString("\n")
        val category = when {
            listOf("酒店", "民宿", "入住", "离店", "房型").any(joined::contains) -> PlaceCategory.HOTEL
            listOf("餐厅", "餐馆", "美食", "用餐").any(joined::contains) -> PlaceCategory.RESTAURANT
            listOf("机场", "车站", "航班", "列车", "高铁").any(joined::contains) -> PlaceCategory.TRANSPORT
            else -> PlaceCategory.ATTRACTION
        }
        val title = inferTitle(clean, category)
        val range = timeRange.find(joined)
        val first = range?.groupValues?.getOrNull(1) ?: singleTime.find(joined)?.groupValues?.getOrNull(1)
        val second = range?.groupValues?.getOrNull(2)
        val start = first?.replace('：', ':')?.let { combineDateAndTime(targetDay, it) } ?: suggestedStart
        val end = second?.replace('：', ':')?.let { combineDateAndTime(targetDay, it) } ?: start + if (category == PlaceCategory.HOTEL) 12 * 3_600_000 else 3_600_000
        val transport = when {
            listOf("步行", "步行导航").any(joined::contains) -> TransportMode.WALK
            listOf("骑行", "骑车").any(joined::contains) -> TransportMode.RIDE
            listOf("公交", "地铁").any(joined::contains) -> TransportMode.BUS
            listOf("火车", "高铁", "列车").any(joined::contains) -> TransportMode.TRAIN
            listOf("航班", "飞机").any(joined::contains) -> TransportMode.FLIGHT
            else -> TransportMode.CAR
        }
        val reservationBits = buildList {
            order.find(joined)?.groupValues?.getOrNull(1)?.let { add("订单号：$it") }
            clean.firstOrNull { it.contains("房型") }?.let(::add)
            clean.firstOrNull { it.contains("取消") }?.let(::add)
        }
        return ItineraryItem(
            id = UUID.randomUUID().toString(), title = title, category = category, startTime = start,
            endTime = maxOf(start + 60_000, end), address = inferAddress(clean), transport = transport,
            distanceText = distance.find(joined)?.value.orEmpty(), playDurationMinutes = ((end - start) / 60_000).toInt().coerceAtLeast(1),
            reservationInfo = reservationBits.joinToString("\n"), cost = money.find(joined)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0,
            note = joined.take(1200)
        )
    }

    private fun inferTitle(lines: List<String>, category: PlaceCategory): String {
        val labels = setOf("订单详情", "导航", "路线", "行程", "预订成功", "酒店订单")
        return lines.map { line ->
            line.replace(Regex("^(?:[-•·*]\\s*)?[0-2]?\\d[:：][0-5]\\d(?:\\s*[-—–~至到]\\s*[0-2]?\\d[:：][0-5]\\d)?\\s*"), "").trim()
        }.firstOrNull { line ->
            line.length in 2..36 && line !in labels &&
                !line.startsWith("地址") && !line.startsWith("地点") && !line.startsWith("出发地") && !line.startsWith("目的地") &&
                !line.startsWith("订单") && !line.startsWith("￥") && !line.startsWith("¥")
        } ?: when (category) { PlaceCategory.HOTEL -> "住宿安排"; PlaceCategory.RESTAURANT -> "用餐安排"; else -> "新安排" }
    }

    private fun inferAddress(lines: List<String>): String = lines.firstOrNull {
        it.startsWith("地址") || listOf("路", "街", "大道", "区", "县", "景区").any(it::contains)
    }?.replace(Regex("^地址\\s*[:：]?\\s*"), "").orEmpty()
}
