package com.personal.triptrail

import com.personal.triptrail.util.AmapRouteStop
import com.personal.triptrail.util.amapRouteUrl
import java.net.URI
import java.net.URLDecoder
import org.junit.Assert.*
import org.junit.Test

class AmapRoutePlanTest {
    private val start = AmapRouteStop("起点", 39.9, 116.3)
    private val end = AmapRouteStop("终点", 40.0, 116.5)

    private fun parameters(stops: List<AmapRouteStop>): Map<String, String> =
        URI(amapRouteUrl(stops)).rawQuery.split('&').associate {
            val (key, value) = it.split('=', limit = 2)
            key to URLDecoder.decode(value, "UTF-8")
        }

    @Test fun twoStopsIncludeCoordinatesWithoutWaypoints() {
        val query = parameters(listOf(start, end))
        assertEquals("39.9", query["slat"])
        assertEquals("116.3", query["slon"])
        assertEquals("40.0", query["dlat"])
        assertEquals("116.5", query["dlon"])
        assertEquals("1", query["dev"])
        assertEquals("0", query["t"])
        assertFalse(query.containsKey("vian"))
    }

    @Test fun allIntermediateStopsAreEncodedInItineraryOrder() {
        val query = parameters(listOf(start, AmapRouteStop("景点一", 39.91, 116.31), AmapRouteStop("景点二", 39.92, 116.32), end))
        assertEquals("2", query["vian"])
        assertEquals("116.31|116.32", query["vialons"])
        assertEquals("39.91|39.92", query["vialats"])
        assertEquals("景点一|景点二", query["vianames"])
        assertEquals("起点", query["sname"])
        assertEquals("终点", query["dname"])
    }

    @Test fun specialCharactersCannotBreakQueryOrWaypointCount() {
        val query = parameters(listOf(start, AmapRouteStop("A&B + 广场|东门", 39.91, 116.31), end))
        assertEquals("1", query["vian"])
        assertEquals("A&B + 广场｜东门", query["vianames"])
    }

    @Test fun returnToStartIsPreserved() {
        val query = parameters(listOf(start, end, start))
        assertEquals("起点", query["dname"])
        assertEquals("终点", query["vianames"])
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidIntermediateCoordinateFailsInsteadOfDroppingPoint() {
        amapRouteUrl(listOf(start, AmapRouteStop("无效地点", Double.NaN, 116.4), end))
    }

    @Test(expected = IllegalArgumentException::class)
    fun singleStopCannotProduceRoute() { amapRouteUrl(listOf(start)) }
}
