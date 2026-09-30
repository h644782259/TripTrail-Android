package com.personal.triptrail

import com.personal.triptrail.data.*
import com.personal.triptrail.util.*
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class RoutePlaceResolverTest {
    private val target = JourneyLocationTarget(JourneyLocationRole.PLACE, "西湖", "杭州市西湖区")
    @Test fun retriesNameWhenCombinedQueryHasNoResults() {
        val queries = mutableListOf<String>()
        val result = resolveRoutePlace(target) {
            queries += it
            if (it == "西湖") listOf(AmapRouteStop("西湖", 30.24, 120.14)) else emptyList()
        }
        assertEquals(listOf("杭州市西湖区 西湖", "西湖"), queries)
        assertEquals(120.14, result.longitude, 0.001)
    }
    @Test fun addressOnlyDoesNotRepeatTheSameQuery() {
        assertEquals(listOf("杭州"), routePlaceQueries(target.copy(name = "", address = "杭州")))
    }
    @Test fun invalidCoordinatesDoNotPreventAddressFallback() {
        val result = resolveRoutePlace(target) {
            if (it == target.address) listOf(AmapRouteStop("地址", 30.2, 120.1)) else listOf(AmapRouteStop("错误", Double.NaN, 0.0))
        }
        assertEquals("西湖", result.name)
        assertEquals(30.2, result.latitude, 0.001)
    }
    @Test fun backendFailureIsNotReportedAsMissingPlace() {
        try {
            resolveRoutePlace(target) { throw IOException("backend unavailable") }
            fail("Expected service failure")
        } catch (error: IllegalStateException) {
            assertTrue(error.cause is IOException)
            assertTrue(error.message!!.contains("服务不可用"))
        }
    }
}
