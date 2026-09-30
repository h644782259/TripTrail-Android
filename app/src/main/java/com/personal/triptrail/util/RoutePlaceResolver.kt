package com.personal.triptrail.util

import com.personal.triptrail.data.JourneyLocationTarget
import java.io.IOException

/** Try a precise combined query, then the POI name used by iOS, then the address. */
internal fun routePlaceQueries(target: JourneyLocationTarget): List<String> {
    val name = target.name.trim()
    val address = target.address.trim()
    return listOf(listOf(address, name).filter { it.isNotBlank() }.distinct().joinToString(" "), name, address)
        .filter { it.isNotBlank() }.distinct()
}

internal fun resolveRoutePlace(
    target: JourneyLocationTarget,
    search: (String) -> List<AmapRouteStop>,
): AmapRouteStop {
    val name = target.displayName
    require(name.isNotBlank()) { "路线中有未填写名称的地点。" }
    var failure: IOException? = null
    for (query in routePlaceQueries(target)) {
        val results = try { search(query) } catch (error: IOException) {
            failure = error
            continue
        }
        val valid = results.filter { it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }
        val match = valid.firstOrNull { it.name.trim() == name } ?: valid.firstOrNull()
        if (match != null) return match.copy(name = name)
    }
    if (failure != null) throw IllegalStateException("“$name”坐标查询服务不可用或网络异常，请稍后重试。", failure)
    throw IllegalArgumentException("高德地点搜索未找到“$name”的坐标，请补充城市和详细地址。")
}
