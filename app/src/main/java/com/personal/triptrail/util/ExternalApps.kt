package com.personal.triptrail.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.personal.triptrail.data.ItineraryItem
import com.personal.triptrail.data.JourneyLocationTarget
import com.personal.triptrail.data.StoryEntry
import java.net.URLEncoder

object ExternalApps {
    fun openAmapTarget(context: Context, target: JourneyLocationTarget): Boolean {
        val keyword = URLEncoder.encode(listOf(target.displayName, target.address).filter { it.isNotBlank() }.joinToString(" "), "UTF-8")
        return launch(context, Intent(Intent.ACTION_VIEW, Uri.parse("androidamap://keywordNavi?sourceApplication=旅迹&keyword=$keyword&style=2")).setPackage("com.autonavi.minimap"))
    }
    fun openAmap(context: Context, item: ItineraryItem): Boolean {
        val target = item.primaryNavigationTarget ?: return false
        val encodedName = URLEncoder.encode(target.displayName, "UTF-8")
        val encodedAddress = URLEncoder.encode(target.address, "UTF-8")
        val uri = if (item.latitude != null && item.longitude != null && item.locationMode == com.personal.triptrail.data.ArrangementLocationMode.SINGLE) {
            Uri.parse("amapuri://route/plan/?sourceApplication=旅迹&dlat=${item.latitude}&dlon=${item.longitude}&dname=$encodedName&dev=0&t=0")
        } else {
            Uri.parse("androidamap://keywordNavi?sourceApplication=旅迹&keyword=${if (encodedAddress.isBlank()) encodedName else "$encodedName%20$encodedAddress"}&style=2")
        }
        return launch(context, Intent(Intent.ACTION_VIEW, uri).setPackage("com.autonavi.minimap"))
    }

    fun openAmap(context: Context, entry: StoryEntry): Boolean = launch(
        context,
        Intent(Intent.ACTION_VIEW, Uri.parse("androidamap://keywordNavi?sourceApplication=旅迹&keyword=${URLEncoder.encode(entry.primaryNavigationTarget?.let { "${it.displayName} ${it.address}" } ?: "${entry.title} ${entry.address}", "UTF-8")}&style=2"))
            .setPackage("com.autonavi.minimap")
    )

    suspend fun openAmapRoute(context: Context, targets: List<JourneyLocationTarget>) {
        require(targets.size >= 2) { "至少选择两个地点才能规划路线。" }
        check(context.packageManager.getLaunchIntentForPackage("com.autonavi.minimap") != null) {
            "未检测到高德地图，请安装后重试。"
        }
        val key = SecureRecognitionSettings(context).amapWebKey
        require(key.isNotBlank()) { "请先在“我的 → 高德路线”中配置高德 Web 服务 Key。" }
        val stops = withContext(Dispatchers.IO) {
            val service = AmapPlaceService(key)
            adjacentRouteTargets(targets).map { target -> resolveRoutePlace(target, service::search) }
        }
        val uri = Uri.parse(amapRouteUrl(stops))
        check(launch(context, Intent(Intent.ACTION_VIEW, uri).setPackage("com.autonavi.minimap"))) {
            "暂时无法打开高德地图，请稍后重试。"
        }
    }

    fun openDiscovery(context: Context, platform: String, title: String, address: String): Boolean {
        val keyword = listOf(title.trim(), address.trim()).filter { it.isNotBlank() }.distinct().joinToString(" ")
        val uri = when (platform) {
            "小红书" -> Uri.Builder().scheme("xhsdiscover").authority("search").appendPath("result")
            "抖音" -> Uri.Builder().scheme("snssdk1128").authority("search")
            else -> return false
        }.appendQueryParameter("keyword", keyword).build()
        val packageName = if (platform == "小红书") "com.xingin.xhs" else "com.ss.android.ugc.aweme"
        val opened = launch(context, Intent(Intent.ACTION_VIEW, uri).setPackage(packageName))
        if (!opened) {
            android.widget.Toast.makeText(context, "无法打开${platform}，请确认已安装并更新至最新版本。", android.widget.Toast.LENGTH_LONG).show()
        }
        return opened
    }

    private fun launch(context: Context, intent: Intent): Boolean = runCatching {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
