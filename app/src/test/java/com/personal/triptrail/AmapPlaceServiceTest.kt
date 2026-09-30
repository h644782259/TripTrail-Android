package com.personal.triptrail

import com.personal.triptrail.util.parseAmapPlaces
import org.junit.Assert.*
import org.junit.Test

class AmapPlaceServiceTest {
    @Test fun coordinatesAreLongitudeThenLatitude() {
        val places = parseAmapPlaces("""{"status":"1","pois":[{"name":"天坛","location":"116.4109,39.8813"},{"name":"无效","location":""}]}""")
        assertEquals(1, places.size)
        assertEquals(116.4109, places.single().longitude, 0.00001)
        assertEquals(39.8813, places.single().latitude, 0.00001)
    }
    @Test fun emptySearchCanTryNextQuery() {
        assertTrue(parseAmapPlaces("""{"status":"1","pois":[]}""").isEmpty())
    }
    @Test fun authenticationErrorIsNotTreatedAsMissingPlace() {
        try { parseAmapPlaces("""{"status":"0","infocode":"10001"}"""); fail("Must reject invalid key") }
        catch (error: IllegalStateException) { assertTrue(error.message!!.contains("Key")) }
    }
    @Test fun quotaErrorsPreserveCodeAndDistinguishDailyFromQps() {
        fun message(code: String): String {
            return try { parseAmapPlaces("""{"status":"0","infocode":"$code"}"""); error("Expected rejection") }
            catch (error: IllegalStateException) { error.message.orEmpty() }
        }
        assertTrue(message("10044").contains("每日"))
        assertTrue(message("10044").contains("10044"))
        assertTrue(message("10021").contains("QPS"))
        assertTrue(message("10010").contains("IP"))
    }
}
