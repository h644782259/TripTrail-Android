package com.personal.triptrail.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.personal.triptrail.data.ItineraryItem
import com.personal.triptrail.data.JourneyLocationTarget
import com.personal.triptrail.data.StoryEntry
import java.net.URLEncoder

object ExternalApps {
    fun openAmap(context: Context, item: ItineraryItem): Boolean {
        val target = item.primaryNavigationTarget ?: return false
        val encodedName = URLEncoder.encode(target.displayName, "UTF-8")
        val encodedAddress = URLEncoder.encode(target.address, "UTF-8")
        val uri = if (item.latitude != null && item.longitude != null && item.locationMode == com.personal.triptrail.data.ArrangementLocationMode.SINGLE) {
            Uri.parse("amapuri://route/plan/?sourceApplication=旅迹&dlat=${item.latitude}&dlon=${item.longitude}&dname=$encodedName&dev=0&t=${item.transport.amapValue}")
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

    fun openAmapRoute(context: Context, targets: List<JourneyLocationTarget>): Boolean {
        val points = targets.mapNotNull { target ->
            target.displayName.takeIf { it.isNotBlank() }?.let { name -> name to target.address }
        }.distinct()
        if (points.size < 2) return false
        val start = points.first()
        val destination = points.last()
        val uri = Uri.parse(
            "androidamap://route/plan/?sourceApplication=旅迹" +
                "&sname=${URLEncoder.encode(listOf(start.first, start.second).filter { it.isNotBlank() }.joinToString(" "), "UTF-8")}" +
                "&dname=${URLEncoder.encode(listOf(destination.first, destination.second).filter { it.isNotBlank() }.joinToString(" "), "UTF-8")}" +
                "&dev=0&t=0"
        )
        return launch(context, Intent(Intent.ACTION_VIEW, uri).setPackage("com.autonavi.minimap"))
    }

    fun openDiscovery(context: Context, platform: String, title: String, address: String): Boolean {
        val query = URLEncoder.encode("$title $address", "UTF-8")
        val uri = when (platform) {
            "小红书" -> Uri.parse("https://www.xiaohongshu.com/search_result?keyword=$query")
            else -> Uri.parse("https://www.douyin.com/search/$query")
        }
        return launch(context, Intent(Intent.ACTION_VIEW, uri))
    }

    private fun launch(context: Context, intent: Intent): Boolean = runCatching {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
