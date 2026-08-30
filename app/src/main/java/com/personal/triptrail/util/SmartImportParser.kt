package com.personal.triptrail.util

import com.personal.triptrail.data.ItineraryItem
import com.personal.triptrail.data.PlaceCategory
import com.personal.triptrail.data.TransportMode
import com.personal.triptrail.data.combineDateAndTime
import java.util.UUID

object SmartImportParser {
    private val timeRange = Regex("(?:^|\\s)([0-2]?\\d[:：][0-5]\\d)\\s*[-—~至到]\\s*([0-2]?\\d[:：][0-5]\\d)")
    private val singleTime = Regex("(?:^|\\s)([0-2]?\\d[:：][0-5]\\d)")
    private val money = Regex("(?:¥|￥|金额|合计|实付|费用)\\s*[:：]?\\s*(\\d+(?:\\.\\d{1,2})?)")
    private val distance = Regex("(\\d+(?:\\.\\d+)?\\s*(?:公里|km|KM|米)(?:\\s*[·•/]\\s*\\d+\\s*(?:分钟|min))?)")
    private val order = Regex("(?:订单号|订单编号|预订号|确认号)\\s*[:：]?\\s*([A-Za-z0-9-]+)")

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
        return lines.firstOrNull { line ->
            line.length in 2..36 && line !in labels && !line.matches(Regex(".*\\d{1,2}[:：]\\d{2}.*")) &&
                !line.startsWith("地址") && !line.startsWith("订单") && !line.startsWith("￥") && !line.startsWith("¥")
        } ?: when (category) { PlaceCategory.HOTEL -> "住宿安排"; PlaceCategory.RESTAURANT -> "用餐安排"; else -> "新安排" }
    }

    private fun inferAddress(lines: List<String>): String = lines.firstOrNull {
        it.startsWith("地址") || listOf("路", "街", "大道", "区", "县", "景区").any(it::contains)
    }?.replace(Regex("^地址\\s*[:：]?\\s*"), "").orEmpty()
}
