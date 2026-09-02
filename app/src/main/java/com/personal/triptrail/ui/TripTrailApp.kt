package com.personal.triptrail.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.triptrail.data.TravelStory
import com.personal.triptrail.data.Trip
import com.personal.triptrail.data.TripRepository
import com.personal.triptrail.util.TripFileService

@Composable
fun TripTrailApp(repository: TripRepository, initialSharedFile: String?) {
    val data by repository.data.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(RootTab.TRIPS) }
    var tripId by rememberSaveable { mutableStateOf<String?>(null) }
    var storyId by rememberSaveable { mutableStateOf<String?>(null) }
    var showsStatistics by rememberSaveable { mutableStateOf(false) }
    var incoming by remember { mutableStateOf<Pair<Trip?, TravelStory?>?>(null) }
    var incomingError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialSharedFile) {
        if (!initialSharedFile.isNullOrBlank()) runCatching { TripFileService.importShared(initialSharedFile) }
            .onSuccess { incoming = it }.onFailure { incomingError = it.message ?: "无法读取分享文件" }
    }

    Scaffold(
        containerColor = TripCanvas,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        bottomBar = { TripBottomBar(selected = tab, onSelect = { selected -> tab = selected; tripId = null; storyId = null; showsStatistics = false }) },
    ) { padding ->
        val contentModifier = androidx.compose.ui.Modifier.padding(padding)
        when {
            tripId != null -> TripDetailScreen(repository, tripId!!, modifier = contentModifier, onBack = { tripId = null })
            storyId != null -> StoryDetailScreen(repository, storyId!!, modifier = contentModifier, onBack = { storyId = null })
            showsStatistics -> StatisticsScreen(data.trips, modifier = contentModifier, onBack = { showsStatistics = false })
            tab == RootTab.TRIPS -> TripsScreen(repository, data.trips, modifier = contentModifier) { tripId = it }
            tab == RootTab.STORIES -> StoriesScreen(repository, data.stories, data.trips, modifier = contentModifier) { storyId = it }
            tab == RootTab.FAVORITES -> FavoritesScreen(repository, data.favorites, modifier = contentModifier)
            else -> SettingsScreen(repository, data, modifier = contentModifier, onOpenStatistics = { showsStatistics = true })
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
            }) { Text("添加到我的旅迹") } },
        )
    }
    incomingError?.let { message ->
        AlertDialog(
            onDismissRequest = { incomingError = null }, title = { Text("无法打开分享") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = { incomingError = null }) { Text("好") } },
        )
    }
}
