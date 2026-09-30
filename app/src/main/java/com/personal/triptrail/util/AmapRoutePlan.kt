package com.personal.triptrail.util

import java.net.URLEncoder

internal data class AmapRouteStop(val name: String, val latitude: Double, val longitude: Double)

/** Android native route protocol: coordinates come from Amap Web search (GCJ-02). */
internal fun amapRouteUrl(rawStops: List<AmapRouteStop>): String {
    val stops = rawStops.fold(mutableListOf<AmapRouteStop>()) { result, stop ->
        val previous = result.lastOrNull()
        if (previous == null || previous.latitude != stop.latitude || previous.longitude != stop.longitude) result.add(stop)
        result
    }
    require(stops.size >= 2) { "至少选择两个地点才能规划路线。" }
    require(stops.all { it.name.isNotBlank() && it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }) {
        "路线地点缺少有效名称或坐标。"
    }
    val start = stops.first()
    val end = stops.last()
    val query = linkedMapOf(
        "sourceApplication" to "旅迹",
        "slat" to start.latitude.toString(), "slon" to start.longitude.toString(), "sname" to start.name,
        "dlat" to end.latitude.toString(), "dlon" to end.longitude.toString(), "dname" to end.name,
        "dev" to "0", "t" to "0",
    )
    val via = stops.drop(1).dropLast(1)
    if (via.isNotEmpty()) {
        query["vian"] = via.size.toString()
        query["vialons"] = via.joinToString("|") { it.longitude.toString() }
        query["vialats"] = via.joinToString("|") { it.latitude.toString() }
        // The native protocol reserves | as the point separator, even after URL decoding.
        query["vianames"] = via.joinToString("|") { it.name.replace('|', '｜') }
    }
    return "androidamap://route/plan/?" + query.entries.joinToString("&") { (key, value) ->
        "$key=${URLEncoder.encode(value, "UTF-8").replace("+", "%20")}"
    }
}


internal fun adjacentRouteTargets(targets: List<com.personal.triptrail.data.JourneyLocationTarget>): List<com.personal.triptrail.data.JourneyLocationTarget> {
    fun key(target: com.personal.triptrail.data.JourneyLocationTarget) =
        listOf(target.displayName, target.address).map { it.filterNot(Char::isWhitespace).lowercase(java.util.Locale.ROOT) }
    return targets.fold(mutableListOf()) { result, target ->
        if (result.lastOrNull()?.let { key(it) == key(target) } != true) result.add(target)
        result
    }
}
