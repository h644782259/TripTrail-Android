@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.personal.triptrail.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.personal.triptrail.data.*
import com.personal.triptrail.util.ShareExportService
import com.personal.triptrail.util.SharePreviewData
import com.personal.triptrail.util.SharePreviewDay
import com.personal.triptrail.util.SharePreviewItem
import com.personal.triptrail.util.TripFileService
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FullDateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)
private val DayDateFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)

@Composable
fun TripShareDialog(trip: Trip, initialDayId: String? = null, onDismiss: () -> Unit) {
    val sortedDays = remember(trip) { trip.days.sortedBy { it.sortOrder } }
    SharePreviewScreen(
        allDayIds = sortedDays.map { it.id },
        initialDayId = initialDayId,
        dataFor = { trip.sharePreviewData(it) },
        importable = { TripFileService.shareTripDays(trip, it) },
        onDismiss = onDismiss,
    )
}

@Composable
fun StoryShareDialog(story: TravelStory, initialDayId: String? = null, onDismiss: () -> Unit) {
    val sortedDays = remember(story) { story.days.sortedBy { it.sortOrder } }
    SharePreviewScreen(
        allDayIds = sortedDays.map { it.id },
        initialDayId = initialDayId,
        dataFor = { story.sharePreviewData(it) },
        importable = { TripFileService.shareStoryDays(story, it) },
        onDismiss = onDismiss,
    )
}

@Composable
private fun SharePreviewScreen(
    allDayIds: List<String>,
    initialDayId: String?,
    dataFor: (Set<String>) -> SharePreviewData,
    importable: (Set<String>) -> String,
    onDismiss: () -> Unit,
) {
    val allIds = remember(allDayIds) { allDayIds.toSet() }
    var selectedIds by remember(allDayIds, initialDayId) {
        mutableStateOf(initialDayId?.let(::setOf)?.intersect(allIds).orEmpty().ifEmpty { allIds })
    }
    var showingRange by remember { mutableStateOf(false) }
    val data = remember(selectedIds, allDayIds) { dataFor(selectedIds) }
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val toolbarOnPoster by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 86 }
    }
    val toolbarColor = if (toolbarOnPoster) Color.White else TripInk

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = TripCanvas,
            topBar = {
                CenterAlignedTopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    title = { Text("分享预览", color = toolbarColor, fontWeight = FontWeight.Bold) },
                    actions = { TextButton(onClick = onDismiss) { Text("完成", color = toolbarColor, fontWeight = FontWeight.Bold) } },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                )
            },
        ) { padding ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 10.dp,
                    bottom = padding.calculateBottomPadding() + 28.dp,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item { ScopePicker(data.scopeSummary) { showingRange = true } }
                item { SharePoster(data) }
                item {
                    Column(
                        modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = { ShareExportService.shareImage(context, data); onDismiss() },
                            enabled = selectedIds.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Default.Image, null)
                            Spacer(Modifier.width(8.dp))
                            Text("分享精美长图", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { ShareExportService.shareImportable(context, data.title, importable(selectedIds)); onDismiss() },
                            enabled = selectedIds.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, TripMist),
                        ) {
                            Icon(Icons.Default.Description, null)
                            Spacer(Modifier.width(8.dp))
                            Text("发送可导入的${data.scopeLabel}", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    if (showingRange) {
        ModalBottomSheet(
            onDismissRequest = { showingRange = false },
            containerColor = TripSurface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("选择分享范围", style = MaterialTheme.typography.titleLarge, color = TripInk)
                Text("可以分享一天、连续几天或整段内容", style = MaterialTheme.typography.bodySmall, color = TripInk.copy(alpha = .66f))
                Spacer(Modifier.height(4.dp))
                RangeRow("整段内容", "${allDayIds.size} 天", selectedIds == allIds) { selectedIds = allIds }
                allDayIds.forEachIndexed { index, id ->
                    val selected = id in selectedIds
                    RangeRow("第 ${index + 1} 天", "单独或组合分享", selected) {
                        selectedIds = if (selected) {
                            if (selectedIds.size > 1) selectedIds - id else selectedIds
                        } else selectedIds + id
                    }
                }
                Button(onClick = { showingRange = false }, Modifier.fillMaxWidth().padding(top = 6.dp)) { Text("确认范围") }
            }
        }
    }
}

@Composable
private fun ScopePicker(summary: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = TripSurface,
        border = BorderStroke(1.dp, TripMist.copy(alpha = .58f)),
        shadowElevation = 2.dp,
    ) {
        Row(Modifier.padding(horizontal = 15.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = TripLake.copy(alpha = .13f)) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.ViewDay, null, tint = TripLakeText, modifier = Modifier.size(19.dp))
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("选择分享范围", style = MaterialTheme.typography.labelMedium, color = TripInk.copy(alpha = .62f))
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = TripInk, fontWeight = FontWeight.SemiBold)
            }
            Icon(Icons.Default.KeyboardArrowDown, "展开范围选择", tint = TripLakeText)
        }
    }
}

@Composable
private fun RangeRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) TripLake.copy(alpha = .11f) else Color.Transparent,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TripInk.copy(alpha = .6f))
            }
            Checkbox(checked = selected, onCheckedChange = { onClick() })
        }
    }
}

@Composable
private fun SharePoster(data: SharePreviewData) {
    Column(
        modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth()
            .shadow(18.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(alpha = .16f), spotColor = Color.Black.copy(alpha = .16f))
            .clip(RoundedCornerShape(24.dp)),
    ) {
        ShareCover(data)
        Column(
            modifier = Modifier.background(Brush.verticalGradient(listOf(SharePaperTop, SharePaperBottom))).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (data.summary.isNotBlank()) SummaryCard(data.summary)
            data.days.forEachIndexed { index, day -> ShareDayCard(index, data.days.size, day) }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("旅迹", style = MaterialTheme.typography.labelLarge, color = TripInk, fontWeight = FontWeight.Bold)
                    Text("把走过的路留下来", style = MaterialTheme.typography.labelSmall, color = TripInk.copy(alpha = .56f))
                }
                Icon(Icons.Default.Share, null, tint = TripLakeText)
            }
        }
    }
}

@Composable
private fun ShareCover(data: SharePreviewData) {
    val bitmap = rememberMediaBitmap(data.cover)
    Box(Modifier.fillMaxWidth().height(252.dp).background(Brush.linearGradient(listOf(TripInk, TripLake)))) {
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.size(210.dp).align(Alignment.TopEnd).offset(x = 60.dp, y = (-70).dp).background(Color.White.copy(.08f), CircleShape))
            Box(Modifier.size(170.dp).align(Alignment.BottomStart).offset(x = (-64).dp, y = 50.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(Color.White.copy(.06f)))
            }
            Icon(Icons.Default.Map, null, tint = Color.White.copy(.08f), modifier = Modifier.size(72.dp).align(Alignment.CenterEnd).offset(x = (-35).dp, y = 32.dp))
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(.08f), Color.Transparent, Color.Black.copy(.72f)))))
        Column(Modifier.fillMaxSize().padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(data.eyebrow, color = Color.White.copy(.9f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.35.sp)
                Spacer(Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(50), color = Color.White.copy(.18f)) {
                    Text(data.scopeLabel, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(data.title, color = Color.White, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, null, tint = Color.White.copy(.88f), modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(data.destination.ifBlank { "目的地待定" }, color = Color.White.copy(.9f), style = MaterialTheme.typography.labelSmall)
                Text("  ·  ", color = Color.White.copy(.7f), style = MaterialTheme.typography.labelSmall)
                Text(data.dateRange, color = Color.White.copy(.9f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = ShareSummary, border = BorderStroke(1.dp, TripLake.copy(.24f))) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.Top) {
            Text("“", color = TripLakeText, fontSize = 25.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = TripInk.copy(.78f), lineHeight = 21.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ShareDayCard(index: Int, dayCount: Int, day: SharePreviewDay) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(ShareDayPanel)) {
        Box(Modifier.matchParentSize().padding(start = 1.dp, top = 20.dp, bottom = 20.dp), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(TripLake.copy(.58f), RoundedCornerShape(50)))
        }
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (dayCount > 1) {
                    Surface(shape = CircleShape, color = TripLake) {
                        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            Text("D${index + 1}", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(11.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(day.heading, style = MaterialTheme.typography.titleMedium, color = TripInk, fontWeight = FontWeight.Bold, maxLines = 2)
                    Text(day.date, style = MaterialTheme.typography.bodySmall, color = TripInk.copy(.62f))
                }
                Surface(shape = RoundedCornerShape(50), color = ShareTimeBadge) {
                    Text("${day.items.size} 个片段", Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = TripLakeText, fontWeight = FontWeight.Bold)
                }
            }
            if (day.narrative.isNotBlank()) Text(day.narrative, style = MaterialTheme.typography.bodySmall, color = TripInk.copy(.68f), lineHeight = 18.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            if (day.items.isEmpty()) {
                Surface(shape = RoundedCornerShape(14.dp), color = ShareItemTop) {
                    Text("这一天还没有具体内容", Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodySmall, color = TripInk.copy(.55f))
                }
            } else day.items.forEachIndexed { itemIndex, item -> ShareItemCard(itemIndex, item) }
        }
    }
}

@Composable
private fun ShareItemCard(index: Int, item: SharePreviewItem) {
    Column(
        Modifier.fillMaxWidth().shadow(7.dp, RoundedCornerShape(16.dp), ambientColor = TripInk.copy(.07f), spotColor = TripInk.copy(.07f))
            .clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(ShareItemTop, ShareItemBottom))).padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = if (item.completed) TripSage else TripLake.copy(.13f)) {
                Box(Modifier.size(25.dp), contentAlignment = Alignment.Center) {
                    if (item.completed) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
                    else Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = TripLakeText, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(9.dp))
            Text(item.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = TripInk, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (item.time.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Surface(shape = RoundedCornerShape(50), color = ShareTimeBadge) {
                    Text(item.time, Modifier.padding(horizontal = 8.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = TripLakeText, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (item.detail.isNotBlank()) Text(item.detail, style = MaterialTheme.typography.bodySmall, color = TripInk.copy(.66f), lineHeight = 18.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
        SharePhotoGrid(item.media)
    }
}

@Composable
private fun SharePhotoGrid(media: List<MediaReference>) {
    val allImages = media.filter { it.kind == MediaKind.IMAGE }
    val decoded = mutableListOf<Pair<MediaReference, android.graphics.Bitmap>>()
    allImages.take(4).forEach { ref -> rememberMediaBitmap(ref)?.let { decoded += ref to it } }
    if (decoded.isEmpty()) return
    val columns = if (decoded.size == 1) 1 else 2
    decoded.chunked(columns).forEachIndexed { rowIndex, row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEachIndexed { columnIndex, (_, bitmap) ->
                val globalIndex = rowIndex * columns + columnIndex
                Box(Modifier.weight(1f).aspectRatio(if (decoded.size == 1) 1.6f else 1.15f).clip(RoundedCornerShape(11.dp))) {
                    Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    if (globalIndex == 3 && allImages.size > 4) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(.42f)), contentAlignment = Alignment.Center) {
                            Text("+${allImages.size - 4}", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (row.size == 1 && decoded.size > 1) Spacer(Modifier.weight(1f))
        }
        if (rowIndex < decoded.chunked(columns).lastIndex) Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun rememberMediaBitmap(media: MediaReference?): android.graphics.Bitmap? {
    val path = media?.localUri?.let { runCatching { Uri.parse(it).path }.getOrNull() }
    return remember(path) { path?.let(BitmapFactory::decodeFile) }
}

private fun Trip.sharePreviewData(selectedIds: Set<String>): SharePreviewData {
    val allDays = days.sortedBy { it.sortOrder }
    val selected = allDays.filter { it.id in selectedIds }.ifEmpty { allDays }
    val sections = selected.map { day ->
        SharePreviewDay(
            id = day.id,
            heading = day.title.ifBlank { "第 ${allDays.indexOf(day) + 1} 天" },
            date = day.date.dayDateText(),
            narrative = if (selected.size == 1) "" else day.note,
            items = day.items.sortedBy { it.sortOrder }.map { item ->
                val status = item.automaticExecutionStatus()
                SharePreviewItem(
                    id = item.id,
                    title = item.title.ifBlank { "未命名安排" },
                    time = "${item.startTime.timeText()}–${item.endTime.timeText()}",
                    detail = listOf(item.locationSummary, item.category.label, item.distanceText, item.note).filter { it.isNotBlank() }.joinToString(" · "),
                    completed = status == ItineraryExecutionStatus.COMPLETED,
                    statusText = status.label,
                    media = item.media.sortedBy { it.sortOrder },
                )
            },
        )
    }
    val first = selected.firstOrNull()?.date ?: startDate
    val last = selected.lastOrNull()?.date ?: endDate
    val isAll = selected.size == allDays.size
    return SharePreviewData(
        title = title,
        destination = destination,
        dateRange = if (first == last) first.fullDateText() else "${first.fullDateText()} — ${last.fullDateText()}",
        summary = if (selected.size == 1) selected.first().note.ifBlank { "${selected.first().items.size} 段当天安排" } else note.ifBlank { "${selected.size} 天 · ${sections.sumOf { it.items.size }} 段安排" },
        eyebrow = "TRIP PLAN · 旅程计划",
        scopeLabel = if (isAll) "整段旅程" else if (selected.size == 1) "单日旅程" else "多日旅程",
        scopeSummary = if (isAll) "整段旅程 · ${selected.size} 天" else "已选择 ${selected.size} 天 · ${sections.sumOf { it.items.size }} 段安排",
        cover = selected.flatMap { it.items.sortedBy { item -> item.sortOrder } }.flatMap { it.media.sortedBy { media -> media.sortOrder } }.firstOrNull { it.kind == MediaKind.IMAGE },
        days = sections,
    )
}

private fun TravelStory.sharePreviewData(selectedIds: Set<String>): SharePreviewData {
    val allDays = days.sortedBy { it.sortOrder }
    val selected = allDays.filter { it.id in selectedIds }.ifEmpty { allDays }
    val sections = selected.map { day ->
        SharePreviewDay(
            id = day.id,
            heading = day.title.ifBlank { "第 ${allDays.indexOf(day) + 1} 天" },
            date = day.date.dayDateText(),
            narrative = if (selected.size == 1) day.details else listOf(day.note, day.details).filter { it.isNotBlank() }.joinToString("\n"),
            items = day.entries.sortedBy { it.sortOrder }.map { entry ->
                SharePreviewItem(
                    id = entry.id,
                    title = entry.title.ifBlank { "未命名记录" },
                    time = entry.timeLabel.ifBlank { entry.startTime?.timeText().orEmpty() },
                    detail = listOf(entry.locationSummary, entry.note).filter { it.isNotBlank() }.joinToString(" · "),
                    completed = true,
                    media = entry.media.sortedBy { it.sortOrder },
                )
            },
        )
    }
    val first = selected.firstOrNull()?.date ?: startDate
    val last = selected.lastOrNull()?.date ?: endDate
    val isAll = selected.size == allDays.size
    val fallbackCover = selected.flatMap { it.entries.sortedBy { entry -> entry.sortOrder } }.flatMap { it.media.sortedBy { media -> media.sortOrder } }.firstOrNull { it.kind == MediaKind.IMAGE }
    return SharePreviewData(
        title = title,
        destination = destination,
        dateRange = if (first == last) first.fullDateText() else "${first.fullDateText()} — ${last.fullDateText()}",
        summary = if (selected.size == 1) selected.first().note.ifBlank { "${selected.first().entries.size} 个当天片段" } else summary.ifBlank { "${selected.size} 天 · ${sections.sumOf { it.items.size }} 个旅行片段" },
        eyebrow = "TRAVEL MEMORY · 旅行足迹",
        scopeLabel = if (isAll) "整段足迹" else if (selected.size == 1) "单日足迹" else "多日足迹",
        scopeSummary = if (isAll) "整段足迹 · ${selected.size} 天" else "已选择 ${selected.size} 天 · ${sections.sumOf { it.items.size }} 个片段",
        cover = coverMedia ?: fallbackCover,
        coverZoom = coverZoom,
        coverOffsetX = coverOffsetX,
        coverOffsetY = coverOffsetY,
        days = sections,
    )
}

private fun Long.fullDateText(): String = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(FullDateFormatter)
private fun Long.dayDateText(): String = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(DayDateFormatter)

private val SharePaperTop = Color(0xFFF9F5E9)
private val SharePaperBottom = Color(0xFFF2EEDB)
private val ShareSummary = Color(0xFFD8E6DB)
private val ShareDayPanel = Color(0xFFE6EBE2)
private val ShareItemTop = Color(0xFFFBF8EF)
private val ShareItemBottom = Color(0xFFF6F3E8)
private val ShareTimeBadge = Color(0xFFD1E4E0)
