package com.personal.triptrail

import com.personal.triptrail.data.PlaceCategory
import com.personal.triptrail.data.TransportMode
import com.personal.triptrail.data.combineDateAndTime
import com.personal.triptrail.data.parseDate
import com.personal.triptrail.data.timeText
import com.personal.triptrail.util.SmartImportParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartImportParserTest {
    @Test
    fun parsesHotelOrderIntoEditableDraft() {
        val day = parseDate("2026-09-10")!!
        val draft = SmartImportParser.parse(
            """西湖国宾馆
                |地址：杭州市西湖区杨公堤18号
                |入住 14:00 - 退房 12:00
                |订单号：HT20260910
                |房型：湖景大床房
                |实付 ¥688.00
            """.trimMargin(), day, combineDateAndTime(day, "09:00")!!
        )
        assertEquals("西湖国宾馆", draft.title)
        assertEquals(PlaceCategory.HOTEL, draft.category)
        assertEquals(688.0, draft.cost, 0.001)
        assertTrue(draft.reservationInfo.contains("HT20260910"))
    }

    @Test
    fun parsesNavigationMetricsAndMode() {
        val day = parseDate("2026-09-10")!!
        val draft = SmartImportParser.parse("灵隐寺\n步行导航\n4.4 公里 · 11 分钟\n10:30 - 12:00", day, combineDateAndTime(day, "09:00")!!)
        assertEquals(TransportMode.WALK, draft.transport)
        assertTrue(draft.distanceText.contains("4.4"))
        assertEquals("10:30", draft.startTime.timeText())
    }

    @Test
    fun parsesWholeJourneyIntoEveryDayAndArrangement() {
        val reference = parseDate("2026-09-25")!!
        val days = SmartImportParser.parseJourney(
            """Day 1 抵达杭州
                |09:00–10:00 抵达杭州东站
                |地点：杭州东站
                |14:00–15:00 入住湖滨酒店
                |地点：湖滨酒店
                |
                |Day 2 西湖环线
                |08:00–09:30 游览断桥残雪
                |11:30–13:00 楼外楼用餐
            """.trimMargin(), reference
        )
        assertEquals(2, days.size)
        assertEquals(2, days[0].items.size)
        assertEquals(2, days[1].items.size)
        assertEquals("游览断桥残雪", days[1].items[0].title)
    }
}
