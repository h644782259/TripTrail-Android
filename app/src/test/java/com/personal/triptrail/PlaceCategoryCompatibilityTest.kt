package com.personal.triptrail

import com.personal.triptrail.data.PlaceCategory
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceCategoryCompatibilityTest {
    @Test fun legacySpecialCategoryBecomesOther() {
        assertEquals(PlaceCategory.OTHER, Json.decodeFromString<PlaceCategory>("\"SPECIAL\""))
        assertEquals(PlaceCategory.OTHER, PlaceCategory.fromLabel("特殊位置"))
        assertEquals("\"OTHER\"", Json.encodeToString(PlaceCategory.OTHER))
        assertEquals(PlaceCategory.HOTEL, Json.decodeFromString<PlaceCategory>("\"HOTEL\""))
    }
}
