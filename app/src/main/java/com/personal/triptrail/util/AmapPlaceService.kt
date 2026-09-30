package com.personal.triptrail.util

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.io.IOException

/** Official POI search; never log request URLs because they contain the Web service key. */
internal class AmapPlaceService(private val key: String) {
    fun search(query: String): List<AmapRouteStop> = synchronized(requestLock) {
        val cacheKey = key to query
        cache[cacheKey]?.let { return@synchronized it }
        val remaining = nextRequestNanos - System.nanoTime()
        if (remaining > 0) java.util.concurrent.TimeUnit.NANOSECONDS.sleep(remaining)
        nextRequestNanos = System.nanoTime() + 1_100_000_000L
        searchRemote(query).also { results ->
            if (results.isNotEmpty()) {
                cache[cacheKey] = results
                if (cache.size > 128) cache.remove(cache.keys.first())
            }
        }
    }

    private companion object {
        val requestLock = Any()
        var nextRequestNanos = 0L
        // Memory only: never persist credentials or search history in logs/files.
        val cache = linkedMapOf<Pair<String, String>, List<AmapRouteStop>>()
    }

    private fun searchRemote(query: String): List<AmapRouteStop> {
        val parameters = mapOf("key" to key, "keywords" to query, "offset" to "10", "page" to "1", "extensions" to "base", "output" to "JSON")
        val encoded = parameters.entries.joinToString("&") { (name, value) -> "$name=${URLEncoder.encode(value, "UTF-8")}" }
        val connection = URL("https://restapi.amap.com/v3/place/text?$encoded").openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.instanceFollowRedirects = false
            if (connection.responseCode != 200) throw IOException("高德搜索请求失败")
            val payload = connection.inputStream.bufferedReader().use { it.readText() }
            parseAmapPlaces(payload)
        } catch (error: IOException) {
            // Do not expose connection exceptions that may contain the credential-bearing URL.
            throw IOException("高德地点搜索网络异常，请检查网络后重试。")
        } finally {
            connection.disconnect()
        }
    }
}

internal fun parseAmapPlaces(payload: String): List<AmapRouteStop> {
    val root = JSONObject(payload)
    if (root.optString("status") != "1") {
        val code = root.optString("infocode")
        val message = when (code) {
            "10001", "10002", "10007", "10008", "10009", "10012", "10013" -> "高德 Key 无效、平台类型不符或权限受限，请检查“我的 → 高德路线”的 Web 服务 Key。"
            "10003", "10044" -> "高德每日调用额度已用尽，请检查开放平台每日配额；降低频率不能恢复今日额度。"
            "10004" -> "高德每分钟调用频率超限，请等待一分钟后重试。"
            "10010" -> "当前出口 IP 的高德访问量超限，请到高德开放平台提交工单检查。"
            "10014", "10015", "10019", "10020", "10021" -> "高德每秒调用额度（QPS）受限，请稍后重试；若首次查询仍失败，请检查地点搜索服务的 QPS 配额是否可用。"
            "10005" -> "高德 IP 白名单不匹配，请检查 Key 的 IP 白名单配置。"
            "10041" -> "高德地点搜索接口权限已过期，请在开放平台检查服务权限。"
            else -> "高德地点搜索服务返回错误，请检查 Key 的服务权限后重试。"
        }
        throw IllegalStateException("$message（错误码：${code.filter { it.isDigit() }.take(8).ifBlank { "未知" }}）")
    }
    val pois = root.optJSONArray("pois") ?: return emptyList()
    return (0 until pois.length()).mapNotNull { index ->
        val poi = pois.optJSONObject(index) ?: return@mapNotNull null
        val coordinates = poi.optString("location").split(',')
        if (coordinates.size != 2) return@mapNotNull null
        val longitude = coordinates[0].toDoubleOrNull() ?: return@mapNotNull null
        val latitude = coordinates[1].toDoubleOrNull() ?: return@mapNotNull null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return@mapNotNull null
        AmapRouteStop(poi.optString("name"), latitude, longitude)
    }
}
