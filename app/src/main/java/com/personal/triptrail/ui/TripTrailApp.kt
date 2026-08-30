package com.personal.triptrail.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.triptrail.data.TravelStory
import com.personal.triptrail.data.Trip
import com.personal.triptrail.data.TripRepository
import com.personal.triptrail.util.TripFileService

private enum class RootTab(val label: String) { TRIPS("旅程"), STORIES("足迹"), STATS("统计"), SETTINGS("我的") }

@Composable
fun TripTrailApp(repository: TripRepository, initialSharedFile: String?) {
    val data by repository.data.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(RootTab.TRIPS) }
    var tripId by rememberSaveable { mutableStateOf<String?>(null) }
    var storyId by rememberSaveable { mutableStateOf<String?>(null) }
    var incoming by remember { mutableStateOf<Pair<Trip?, TravelStory?>?>(null) }
    var incomingError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialSharedFile) {
        if (!initialSharedFile.isNullOrBlank()) runCatching { TripFileService.importShared(initialSharedFile) }
            .onSuccess { incoming = it }.onFailure { incomingError = it.message ?: "无法读取分享文件" }
    }

    when {
        tripId != null -> TripDetailScreen(repository, tripId!!, onBack = { tripId = null })
        storyId != null -> StoryDetailScreen(repository, storyId!!, onBack = { storyId = null })
        else -> Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(tab == RootTab.TRIPS, { tab = RootTab.TRIPS }, { Icon(Icons.Default.Map, null) }, label = { Text("旅程") })
                    NavigationBarItem(tab == RootTab.STORIES, { tab = RootTab.STORIES }, { Icon(Icons.Default.Luggage, null) }, label = { Text("足迹") })
                    NavigationBarItem(tab == RootTab.STATS, { tab = RootTab.STATS }, { Icon(Icons.Default.BarChart, null) }, label = { Text("统计") })
                    NavigationBarItem(tab == RootTab.SETTINGS, { tab = RootTab.SETTINGS }, { Icon(Icons.Default.Person, null) }, label = { Text("我的") })
                }
            }
        ) { padding ->
            when (tab) {
                RootTab.TRIPS -> TripsScreen(repository, data.trips, Modifier.padding(padding)) { tripId = it }
                RootTab.STORIES -> StoriesScreen(repository, data.stories, data.trips, Modifier.padding(padding)) { storyId = it }
                RootTab.STATS -> StatisticsScreen(data.trips, Modifier.padding(padding))
                RootTab.SETTINGS -> SettingsScreen(repository, data, Modifier.padding(padding))
            }
        }
    }

    incoming?.let { (trip, story) ->
        AlertDialog(
            onDismissRequest = { incoming = null },
            title = { Text("收藏这份${if (trip != null) "旅程" else "足迹"}？") },
            text = { Text("“${trip?.title ?: story?.title}”会作为独立副本添加，不会覆盖本机已有内容。") },
            dismissButton = { TextButton(onClick = { incoming = null }) { Text("取消") } },
            confirmButton = { TextButton(onClick = {
                val current = repository.data.value
                if (trip != null && current.trips.none { it.id == trip.id }) repository.replaceAll(current.copy(trips = current.trips + trip))
                if (story != null && current.stories.none { it.id == story.id }) repository.replaceAll(current.copy(stories = current.stories + story))
                incoming = null
            }) { Text("添加到我的旅迹") } }
        )
    }
    incomingError?.let { message ->
        AlertDialog(onDismissRequest = { incomingError = null }, title = { Text("无法打开分享") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = { incomingError = null }) { Text("好") } })
    }
}
