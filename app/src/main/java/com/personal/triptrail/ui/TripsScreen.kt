@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.personal.triptrail.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.personal.triptrail.R
import com.personal.triptrail.data.*
import com.personal.triptrail.util.ExternalApps
import com.personal.triptrail.util.TripFileService
import com.personal.triptrail.util.ZhipuRecognitionService
import kotlinx.coroutines.launch
import java.io.File
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

private enum class TripHomeSection(val label: String) { CURRENT("进行中/待出发"), COMPLETED("已结束") }

@Composable
fun TripsScreen(repository: TripRepository, trips: List<Trip>, modifier: Modifier = Modifier, onOpen: (String) -> Unit) {
    var section by rememberSaveable { mutableStateOf(TripHomeSection.CURRENT) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Trip?>(null) }
    var deleting by remember { mutableStateOf<Trip?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var exportText by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.triptrail.journey")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(exportText.orEmpty()) }
                ?: error("无法创建分享文件")
        }.onSuccess { message = "分享文件已导出。" }.onFailure { message = "导出失败：${it.localizedMessage}" }
        exportText = null
    }
    val current = remember(trips) { trips.timelineSorted().filter { it.phase() != TripPhase.HISTORY } }
    val completed = remember(trips) { trips.timelineSorted().filter { it.phase() == TripPhase.HISTORY } }
    val displayed = if (section == TripHomeSection.CURRENT) current else completed

    Box(modifier.fillMaxSize().background(TripCanvas)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TripRoundAction(Icons.Default.Add, "添加旅程") { creating = true }
                }
            }
            if (trips.isEmpty()) {
                item { JourneyEmptyHero() }
                item {
                    Column(Modifier.fillParentMaxHeight(.55f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.Luggage, null, tint = TripLakeText, modifier = Modifier.size(52.dp))
                        Spacer(Modifier.height(12.dp)); Text("下一站，去哪里？", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(6.dp)); Text("新建旅行，按天安排地点、交通和照片。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(18.dp)); Button(onClick = { creating = true }) { Text("创建第一段旅程") }
                    }
                }
            } else {
                item { TripSegmentedControl(section, current.size, completed.size) { section = it } }
                if (displayed.isEmpty()) {
                    item {
                        Column(Modifier.fillParentMaxHeight(.55f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(if (section == TripHomeSection.CURRENT) Icons.Default.EventAvailable else Icons.Default.History, null, tint = TripLakeText, modifier = Modifier.size(46.dp))
                            Spacer(Modifier.height(12.dp)); Text(if (section == TripHomeSection.CURRENT) "暂无进行中或待出发的旅程" else "还没有已结束的旅程", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                } else {
                    items(displayed, key = { it.id }) { trip ->
                        TripCardContainer(
                            trip = trip,
                            featured = section == TripHomeSection.CURRENT && trip.id == displayed.first().id,
                            onOpen = { onOpen(trip.id) },
                            onEdit = { editing = trip },
                            onArchive = { repository.archiveTrip(trip.id); message = "已整理成足迹。" },
                            onShare = {
                                exportText = TripFileService.shareTrip(trip)
                                exporter.launch("${trip.title}.triptrail")
                            },
                            onSmartImport = { onOpen(trip.id) },
                            onRoute = {
                                val targets = trip.days.sortedBy { it.sortOrder }.flatMap { day -> day.items.sortedBy { it.sortOrder } }.flatMap { it.locationTargets }
                                if (!ExternalApps.openAmapRoute(context, targets)) message = "至少需要两个已填写地点，并安装高德地图，才能规划全旅程路线。"
                            },
                            onDelete = { deleting = trip },
                        )
                    }
                }
            }
        }
    }
    if (creating) TripEditorDialog(null, { creating = false }) { title, destination, start, end, note ->
        val trip = repository.createTrip(title, destination, start, end, note); creating = false; onOpen(trip.id)
    }
    editing?.let { trip -> TripEditorDialog(trip, { editing = null }) { title, destination, start, end, note ->
        repository.updateTrip(trip.copy(title = title, destination = destination, startDate = start, endDate = end, note = note)); editing = null
    } }
    deleting?.let { trip -> ConfirmDeleteDialog("删除旅程？", "“${trip.title}”以及其中的日期、安排和媒体引用都会删除。", { deleting = null }) { repository.deleteTrip(trip.id); deleting = null } }
    message?.let { AlertDialog(onDismissRequest = { message = null }, title = { Text("提示") }, text = { Text(it) }, confirmButton = { TextButton(onClick = { message = null }) { Text("好") } }) }
}

@Composable
private fun TripSegmentedControl(selected: TripHomeSection, current: Int, completed: Int, onSelect: (TripHomeSection) -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFE8E7E2)) {
        Row(Modifier.fillMaxWidth().padding(2.dp)) {
            TripHomeSection.entries.forEach { section ->
                val active = section == selected
                Surface(
                    modifier = Modifier.weight(1f).height(34.dp), shape = RoundedCornerShape(16.dp),
                    color = if (active) Color.White else Color.Transparent, shadowElevation = if (active) 1.dp else 0.dp,
                    onClick = { onSelect(section) },
                ) { Box(contentAlignment = Alignment.Center) { Text("${section.label} ${if (section == TripHomeSection.CURRENT) current else completed}", style = MaterialTheme.typography.labelMedium, color = Color(0xFF181B18)) } }
            }
        }
    }
}

@Composable
private fun JourneyEmptyHero() {
    Surface(shape = RoundedCornerShape(28.dp), border = androidx.compose.foundation.BorderStroke(.8.dp, TripMist.copy(alpha = .5f)), shadowElevation = 7.dp) {
        Box(Modifier.fillMaxWidth().height(190.dp)) {
            Image(painterResource(R.drawable.journey_lake_hero), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Row(Modifier.fillMaxSize().padding(22.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) { Text("把期待排进日历", style = MaterialTheme.typography.headlineSmall, color = TripInk); Spacer(Modifier.height(7.dp)); Text("路线、照片和回忆，都在一处。", color = TripInk.copy(alpha = .66f)) }
                Icon(Icons.Default.DirectionsWalk, null, tint = TripLakeText, modifier = Modifier.size(50.dp))
            }
        }
    }
}

@Composable
private fun TripCardContainer(
    trip: Trip, featured: Boolean, onOpen: () -> Unit, onEdit: () -> Unit, onArchive: () -> Unit,
    onShare: () -> Unit, onSmartImport: () -> Unit, onRoute: () -> Unit, onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        if (featured) FeaturedTripHero(trip, onOpen) else StandardTripCard(trip, onOpen)
        Box(Modifier.align(Alignment.TopEnd).padding(9.dp)) {
            IconButton(onClick = { menu = true }, modifier = Modifier.background(TripSurface.copy(alpha = .72f), CircleShape)) { Icon(Icons.Default.MoreHoriz, "更多操作", tint = TripInk.copy(alpha = .72f)) }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text("编辑旅程") }, { menu = false; onEdit() }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                DropdownMenuItem({ Text("整理成足迹") }, { menu = false; onArchive() }, leadingIcon = { Icon(Icons.Default.MenuBook, null) })
                DropdownMenuItem({ Text("分享旅程") }, { menu = false; onShare() }, leadingIcon = { Icon(Icons.Default.Share, null) })
                DropdownMenuItem({ Text("智能录入") }, { menu = false; onSmartImport() }, leadingIcon = { Icon(Icons.Default.AutoAwesome, null) })
                DropdownMenuItem({ Text("规划全行程路线") }, { menu = false; onRoute() }, leadingIcon = { Icon(Icons.Default.Route, null) })
                HorizontalDivider()
                DropdownMenuItem({ Text("删除旅程", color = MaterialTheme.colorScheme.error) }, { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) })
            }
        }
    }
}

@Composable
private fun FeaturedTripHero(trip: Trip, onOpen: () -> Unit) {
    val phase = trip.phase()
    val today = System.currentTimeMillis().startOfDay()
    val totalDays = (ChronoUnit.DAYS.between(trip.startDate.localDate(), trip.endDate.localDate()).toInt() + 1).coerceAtLeast(1)
    val currentDay = (ChronoUnit.DAYS.between(trip.startDate.localDate(), today.localDate()).toInt() + 1).coerceIn(1, totalDays)
    val todayItems = trip.days.firstOrNull { it.date.startOfDay() == today }?.items.orEmpty()
    val doneToday = todayItems.count { it.executionStatus == ItineraryExecutionStatus.COMPLETED }
    val currentItem = todayItems.firstOrNull { it.executionStatus == ItineraryExecutionStatus.IN_PROGRESS }
    val nextItem = todayItems.firstOrNull { it.executionStatus == ItineraryExecutionStatus.NOT_STARTED }
    val fraction = if (phase == TripPhase.UPCOMING) 0f else currentDay.toFloat() / totalDays
    Surface(
        modifier = Modifier.fillMaxWidth(), onClick = onOpen, shape = RoundedCornerShape(28.dp),
        border = androidx.compose.foundation.BorderStroke(1.4.dp, TripLake.copy(alpha = .56f)), shadowElevation = 10.dp,
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = if (phase == TripPhase.CURRENT) 292.dp else 216.dp)) {
            Image(painterResource(R.drawable.journey_lake_hero), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            if (phase == TripPhase.CURRENT) Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = .12f), Color.White.copy(alpha = .9f)))))
            Column(Modifier.fillMaxWidth().padding(22.dp).padding(end = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = .75f), border = androidx.compose.foundation.BorderStroke(.8.dp, TripLake.copy(alpha = .3f))) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (phase == TripPhase.CURRENT) Icons.Default.NearMe else Icons.Default.Event, null, tint = TripLakeText, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(5.dp)); Text(if (phase == TripPhase.CURRENT) "当前旅程" else "下一段旅程", style = MaterialTheme.typography.labelMedium, color = TripLakeText)
                            }
                        }
                        Text(trip.title, style = MaterialTheme.typography.headlineLarge, color = TripInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (trip.destination.isNotBlank()) IconText(Icons.Default.PinDrop, trip.destination, TripInk.copy(alpha = .68f))
                        IconText(Icons.Default.CalendarMonth, "${trip.startDate.chineseDateText()} — ${trip.endDate.chineseDateText()}", TripInk.copy(alpha = .68f))
                    }
                    Box(Modifier.size(82.dp).padding(top = 38.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxSize(), color = TripLake, trackColor = TripLake.copy(alpha = .22f), strokeWidth = 7.dp)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (phase == TripPhase.UPCOMING) "还有" else "第 ${currentDay} 天", style = MaterialTheme.typography.labelSmall, color = TripInk.copy(alpha = .64f))
                            Text(if (phase == TripPhase.UPCOMING) "${daysUntil(trip.startDate)} 天" else "$currentDay/$totalDays", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TripInk)
                        }
                    }
                }
                if (trip.note.isNotBlank()) Text(trip.note, style = MaterialTheme.typography.bodyMedium, color = TripInk.copy(alpha = .72f), maxLines = 2)
                if (phase == TripPhase.CURRENT) {
                    HorizontalDivider(color = TripLake.copy(alpha = .26f)); Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ListAlt, null, tint = TripInk, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text("今日安排", fontWeight = FontWeight.Bold, color = TripInk); Spacer(Modifier.weight(1f)); Text(if (todayItems.isEmpty()) "暂无安排" else "$doneToday/${todayItems.size} 已完成", style = MaterialTheme.typography.labelMedium, color = TripLakeText)
                    }
                    LinearProgressIndicator(progress = { if (todayItems.isEmpty()) 0f else doneToday.toFloat() / todayItems.size }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = TripLake, trackColor = TripLake.copy(alpha = .18f))
                    ScheduleLine("正在进行", currentItem); ScheduleLine("接下来", nextItem)
                }
            }
        }
    }
}

@Composable
private fun ScheduleLine(label: String, item: ItineraryItem?) {
    Row { Text("$label：", style = MaterialTheme.typography.labelMedium, color = TripInk.copy(alpha = .82f)); Text(item?.title?.ifBlank { "未命名安排" } ?: "暂无", style = MaterialTheme.typography.labelMedium, color = TripInk.copy(alpha = if (item == null) .62f else 1f), maxLines = 1) }
}

@Composable
private fun StandardTripCard(trip: Trip, onOpen: () -> Unit) {
    val phase = trip.phase()
    val statusColor = when (phase) { TripPhase.CURRENT -> TripSage; TripPhase.UPCOMING -> TripLakeText; TripPhase.HISTORY -> Color.Gray }
    val statusText = when (phase) { TripPhase.CURRENT -> "旅行中"; TripPhase.UPCOMING -> "${daysUntil(trip.startDate)}天后出发"; TripPhase.HISTORY -> "已结束" }
    val totalDays = (ChronoUnit.DAYS.between(trip.startDate.localDate(), trip.endDate.localDate()).toInt() + 1).coerceAtLeast(1)
    Surface(
        modifier = Modifier.fillMaxWidth(), onClick = onOpen, shape = RoundedCornerShape(24.dp),
        color = TripSurface, border = androidx.compose.foundation.BorderStroke(.9.dp, statusColor.copy(alpha = .2f)), shadowElevation = 5.dp,
    ) {
        Box(Modifier.background(Brush.linearGradient(listOf(statusColor.copy(alpha = .11f), TripSurface, TripSand.copy(alpha = .08f))))) {
            Box(Modifier.align(Alignment.CenterStart).padding(start = 2.dp).width(3.dp).height(48.dp).background(statusColor.copy(alpha = .72f), CircleShape))
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.padding(end = 42.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(trip.title, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                        IconText(Icons.Default.PinDrop, trip.destination.ifBlank { "待确定目的地" }, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(shape = RoundedCornerShape(16.dp), color = statusColor.copy(alpha = .12f), border = androidx.compose.foundation.BorderStroke(.7.dp, statusColor.copy(alpha = .16f))) { Text(statusText, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = statusColor) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) { IconText(Icons.Default.CalendarMonth, "${trip.startDate.chineseDateText()} — ${trip.endDate.chineseDateText()}", MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.weight(1f)); Text("共 $totalDays 天", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (trip.note.isNotBlank()) Text(trip.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (phase == TripPhase.UPCOMING) 1 else 2)
                if (phase != TripPhase.UPCOMING) {
                    Row { Text("旅程进度", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.weight(1f)); Text("${trip.completedCount}/${trip.totalCount} 项安排已完成", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    LinearProgressIndicator(progress = { if (trip.totalCount == 0) 0f else trip.completedCount.toFloat() / trip.totalCount }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = statusColor)
                }
            }
        }
    }
}

@Composable
private fun IconText(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = color, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(6.dp)); Text(text, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

private fun daysUntil(date: Long): Int = ChronoUnit.DAYS.between(System.currentTimeMillis().startOfDay().localDate(), date.localDate()).toInt().coerceAtLeast(1)

@Composable
private fun TripEditorDialog(original: Trip?, onDismiss: () -> Unit, save: (String, String, Long, Long, String) -> Unit) {
    var title by remember(original?.id) { mutableStateOf(original?.title.orEmpty()) }
    var destination by remember(original?.id) { mutableStateOf(original?.destination.orEmpty()) }
    var start by remember(original?.id) { mutableStateOf(original?.startDate?.dateText() ?: System.currentTimeMillis().dateText()) }
    var end by remember(original?.id) { mutableStateOf(original?.endDate?.dateText() ?: System.currentTimeMillis().dateText()) }
    var note by remember(original?.id) { mutableStateOf(original?.note.orEmpty()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (original == null) "新建旅程" else "编辑旅程") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("旅程名称") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(destination, { destination = it }, label = { Text("目的地") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(start, { start = it }, label = { Text("开始日期（yyyy-MM-dd）") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(end, { end = it }, label = { Text("结束日期（yyyy-MM-dd）") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(note, { note = it }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = { Button(onClick = { save(title.trim(), destination.trim(), parseDate(start)!!, parseDate(end)!!, note.trim()) }, enabled = title.isNotBlank() && destination.isNotBlank() && parseDate(start) != null && parseDate(end) != null) { Text("保存") } })
}

@Composable
private fun DayEditorDialog(day: TripDay, onDismiss: () -> Unit, save: (TripDay) -> Unit) {
    var title by remember(day.id) { mutableStateOf(day.title) }
    var date by remember(day.id) { mutableStateOf(day.date.dateText()) }
    var note by remember(day.id) { mutableStateOf(day.note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑当天") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("当天标题") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(date, { date = it }, label = { Text("日期（yyyy-MM-dd）") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("当天摘要") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = {
            Button(
                onClick = { save(day.copy(title = title.trim(), date = parseDate(date)!!, note = note.trim())) },
                enabled = parseDate(date) != null,
            ) { Text("保存") }
        },
    )
}

@Composable
fun TripDetailScreen(repository: TripRepository, tripId: String, modifier: Modifier = Modifier, onBack: () -> Unit) {
    val data by repository.data.collectAsStateWithLifecycle()
    val trip = data.trips.firstOrNull { it.id == tripId }
    if (trip == null) { LaunchedEffect(Unit) { onBack() }; return }
    val days = trip.days.sortedBy { it.sortOrder }
    var selectedDayId by rememberSaveable(tripId) { mutableStateOf(days.firstOrNull()?.id) }
    val selectedDay = days.firstOrNull { it.id == selectedDayId } ?: days.firstOrNull()
    var editing by remember { mutableStateOf<Pair<TripDay, ItineraryItem>?>(null) }
    var deleting by remember { mutableStateOf<Pair<TripDay, ItineraryItem>?>(null) }
    var addMenu by remember { mutableStateOf(false) }
    var textImportDay by remember { mutableStateOf<TripDay?>(null) }
    var imageImportDay by remember { mutableStateOf<TripDay?>(null) }
    var favoriteImportDay by remember { mutableStateOf<TripDay?>(null) }
    var editingDay by remember { mutableStateOf<TripDay?>(null) }
    var deletingDay by remember { mutableStateOf<TripDay?>(null) }
    var dayMenu by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var exportText by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val recognitionScope = rememberCoroutineScope()
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.triptrail.journey")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(exportText.orEmpty()) }
                ?: error("无法创建分享文件")
        }.onSuccess { message = "分享文件已导出。" }.onFailure { message = "导出失败：${it.localizedMessage}" }
        exportText = null
    }
    val screenshotPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val day = imageImportDay; imageImportDay = null
        if (uri != null && day != null) {
            val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            runCatching { InputImage.fromFilePath(context, uri) }.onSuccess { image ->
                recognizer.process(image).addOnSuccessListener { result ->
                    recognitionScope.launch {
                        val recognized = ZhipuRecognitionService.recognizeSingleItemText(context, result.text, day.date, repository.suggestedStart(day))
                        editing = day to recognized.item
                        recognized.fallbackMessage?.let { message = it }
                    }
                }
                    .addOnFailureListener { message = "截图识别失败：${it.localizedMessage}" }.addOnCompleteListener { recognizer.close() }
            }.onFailure { message = "无法读取截图：${it.localizedMessage}" }
        }
    }
    Box(modifier.fillMaxSize().background(TripCanvas)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TripRoundAction(Icons.Default.ArrowBack, "返回", onBack); Text(trip.title, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box {
                    TripRoundAction(Icons.Default.MoreHoriz, "当天更多操作") { dayMenu = true }
                    DropdownMenu(dayMenu, { dayMenu = false }) {
                        selectedDay?.let { day ->
                            DropdownMenuItem({ Text("编辑当天") }, { dayMenu = false; editingDay = day }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                            DropdownMenuItem({ Text("文字录入") }, { dayMenu = false; textImportDay = day }, leadingIcon = { Icon(Icons.Default.TextFields, null) })
                            DropdownMenuItem({ Text("图片录入") }, { dayMenu = false; imageImportDay = day; screenshotPicker.launch("image/*") }, leadingIcon = { Icon(Icons.Default.PhotoLibrary, null) })
                            DropdownMenuItem({ Text("分享当天") }, {
                                dayMenu = false
                                exportText = TripFileService.shareTripDay(trip, day)
                                exporter.launch("${trip.title}-${day.title.ifBlank { day.date.dateText() }}.triptrail")
                            }, leadingIcon = { Icon(Icons.Default.Share, null) })
                            DropdownMenuItem({ Text("规划当天路线") }, {
                                dayMenu = false
                                val targets = day.items.sortedBy { it.sortOrder }.flatMap { it.locationTargets }
                                if (!ExternalApps.openAmapRoute(context, targets)) message = "至少需要两个已填写地点，并安装高德地图，才能规划当天路线。"
                            }, leadingIcon = { Icon(Icons.Default.Route, null) })
                            HorizontalDivider()
                            DropdownMenuItem({ Text("删除当天", color = MaterialTheme.colorScheme.error) }, { dayMenu = false; deletingDay = day }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) })
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                days.forEachIndexed { index, day ->
                    val active = day.id == selectedDay?.id
                    Surface(onClick = { selectedDayId = day.id }, shape = RoundedCornerShape(24.dp), color = if (active) TripLake else TripSurface, border = if (active) null else androidx.compose.foundation.BorderStroke(.8.dp, TripLake.copy(alpha = .2f))) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("第 ${index + 1} 天", style = MaterialTheme.typography.labelSmall, color = if (active) Color.White else TripInk); Text(day.date.chineseDateText(), style = MaterialTheme.typography.labelMedium, color = if (active) Color.White else TripInk) }
                    }
                }
                TripRoundAction(Icons.Default.Add, "添加一天") { repository.addDay(trip.id)?.let { selectedDayId = it.id } }
            }
            selectedDay?.let { day ->
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 112.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item { Text(day.title.ifBlank { "第 ${(days.indexOf(day) + 1)} 天" }, style = MaterialTheme.typography.headlineSmall, color = TripInk) }
                    items(day.items.sortedBy { it.sortOrder }, key = { it.id }) { item ->
                        ItineraryItemCard(item, { editing = day to item }, { ExternalApps.openAmap(context, item) }, { repository.moveItem(trip.id, day.id, item.id, -1) }, { repository.moveItem(trip.id, day.id, item.id, 1) }, { deleting = day to item })
                    }
                    item {
                        Box {
                            TextButton(onClick = { addMenu = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("添加安排", fontWeight = FontWeight.Bold) }
                            DropdownMenu(addMenu, { addMenu = false }) {
                                DropdownMenuItem({ Text("手动") }, { addMenu = false; editing = day to ItineraryItem(startTime = repository.suggestedStart(day), endTime = repository.suggestedStart(day) + 3_600_000) }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                                DropdownMenuItem({ Text("从收藏导入") }, { addMenu = false; favoriteImportDay = day }, leadingIcon = { Icon(Icons.Default.FavoriteBorder, null) })
                                DropdownMenuItem({ Text("文字录入") }, { addMenu = false; textImportDay = day }, leadingIcon = { Icon(Icons.Default.TextFields, null) })
                                DropdownMenuItem({ Text("图片录入") }, { addMenu = false; imageImportDay = day; screenshotPicker.launch("image/*") }, leadingIcon = { Icon(Icons.Default.PhotoLibrary, null) })
                            }
                        }
                    }
                }
            }
        }
    }
    editing?.let { (day, item) -> ArrangementEditorDialog(repository, day, item, { editing = null }) { repository.saveItem(trip.id, day.id, it); editing = null } }
    editingDay?.let { day -> DayEditorDialog(day, { editingDay = null }) { repository.updateDay(trip.id, it); editingDay = null } }
    deleting?.let { (day, item) -> ConfirmDeleteDialog("删除安排？", "“${item.title}”将从这一天删除。", { deleting = null }) { repository.deleteItem(trip.id, day.id, item.id); deleting = null } }
    deletingDay?.let { day -> ConfirmDeleteDialog("删除当天？", "“${day.title.ifBlank { day.date.chineseDateText() }}”以及其中的所有安排都会删除。", { deletingDay = null }) { repository.deleteDay(trip.id, day.id); deletingDay = null; selectedDayId = repository.data.value.trips.firstOrNull { it.id == trip.id }?.days?.minByOrNull { it.sortOrder }?.id } }
    textImportDay?.let { day -> TextImportDialog({ textImportDay = null }) { text ->
        textImportDay = null
        recognitionScope.launch {
            val recognized = ZhipuRecognitionService.recognizeSingleItemText(context, text, day.date, repository.suggestedStart(day))
            editing = day to recognized.item
            recognized.fallbackMessage?.let { message = it }
        }
    } }
    favoriteImportDay?.let { day -> FavoriteImportDialog(data.favorites, { favoriteImportDay = null }) { ids -> repository.importFavorites(trip.id, day.id, ids); favoriteImportDay = null } }
    message?.let { AlertDialog(onDismissRequest = { message = null }, title = { Text("提示") }, text = { Text(it) }, confirmButton = { TextButton(onClick = { message = null }) { Text("好") } }) }
}

@Composable
private fun ItineraryItemCard(item: ItineraryItem, onEdit: () -> Unit, onNavigate: () -> Unit, moveUp: () -> Unit, moveDown: () -> Unit, onDelete: () -> Unit) {
    val statusColor = when (item.executionStatus) { ItineraryExecutionStatus.NOT_STARTED -> Color(0xFF8F662E); ItineraryExecutionStatus.IN_PROGRESS -> TripLake; ItineraryExecutionStatus.COMPLETED -> TripSage }
    var menu by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(), onClick = onEdit, shape = RoundedCornerShape(16.dp),
        color = if (item.executionStatus == ItineraryExecutionStatus.COMPLETED) Color(0xFFE7EEE8) else TripSurface,
        border = androidx.compose.foundation.BorderStroke(if (item.executionStatus == ItineraryExecutionStatus.IN_PROGRESS) 2.dp else 1.dp, statusColor.copy(alpha = .58f)), shadowElevation = if (item.executionStatus == ItineraryExecutionStatus.IN_PROGRESS) 8.dp else 3.dp,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Surface(shape = CircleShape, color = if (item.executionStatus == ItineraryExecutionStatus.IN_PROGRESS) statusColor else statusColor.copy(alpha = .10f), border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = .3f))) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { Icon(when (item.executionStatus) { ItineraryExecutionStatus.NOT_STARTED -> Icons.Default.Schedule; ItineraryExecutionStatus.IN_PROGRESS -> Icons.Default.PlayArrow; ItineraryExecutionStatus.COMPLETED -> Icons.Default.Check }, item.executionStatus.label, tint = if (item.executionStatus == ItineraryExecutionStatus.IN_PROGRESS) Color.White else statusColor, modifier = Modifier.size(20.dp)) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(item.category.icon(), null, tint = if (item.executionStatus == ItineraryExecutionStatus.COMPLETED) Color.Gray else TripInk, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(7.dp)); Text(item.title.ifBlank { "未命名安排" }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Surface(shape = RoundedCornerShape(18.dp), color = statusColor.copy(alpha = if (item.executionStatus == ItineraryExecutionStatus.IN_PROGRESS) 1f else .12f)) { Text("${item.startTime.timeText()}–${item.endTime.timeText()}", Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium, color = if (item.executionStatus == ItineraryExecutionStatus.IN_PROGRESS) Color.White else TripInk) }
                }
                item.locationTargets.forEach { target -> TextButton(onClick = onNavigate, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 28.dp)) { Icon(if (target.role == JourneyLocationRole.ORIGIN) Icons.Default.MyLocation else Icons.Default.PinDrop, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("${target.role.label}：${target.displayName}", style = MaterialTheme.typography.bodyMedium) } }
                if (item.reservationInfo.isNotBlank()) IconText(Icons.Default.ConfirmationNumber, item.reservationInfo, MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.distanceText.isNotBlank() || item.cost > 0) IconText(Icons.Default.SwapHoriz, listOfNotNull(item.distanceText.takeIf { it.isNotBlank() }, item.cost.takeIf { it > 0 }?.let { "¥${String.format(Locale.CHINA, "%.2f", it)}" }).joinToString("   "), MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.note.isNotBlank()) Text(item.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.media.isNotEmpty()) MediaStrip(item.media)
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.MoreVert, "安排操作") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("编辑") }, { menu = false; onEdit() }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                    DropdownMenuItem({ Text("上移") }, { menu = false; moveUp() }, leadingIcon = { Icon(Icons.Default.KeyboardArrowUp, null) })
                    DropdownMenuItem({ Text("下移") }, { menu = false; moveDown() }, leadingIcon = { Icon(Icons.Default.KeyboardArrowDown, null) })
                    DropdownMenuItem({ Text("删除") }, { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null) })
                }
            }
        }
    }
}

@Composable
private fun ArrangementEditorDialog(repository: TripRepository, day: TripDay, original: ItineraryItem, onDismiss: () -> Unit, save: (ItineraryItem) -> Unit) {
    var title by remember(original.id) { mutableStateOf(original.title) }; var category by remember(original.id) { mutableStateOf(original.category) }
    var start by remember(original.id) { mutableStateOf(original.startTime.timeText()) }; var end by remember(original.id) { mutableStateOf(original.endTime.timeText()) }
    var mode by remember(original.id) { mutableStateOf(original.locationMode) }
    var place by remember(original.id) { mutableStateOf(original.placeName.ifBlank { original.title.takeIf { original.address.isNotBlank() }.orEmpty() }) }; var placeAddress by remember(original.id) { mutableStateOf(original.placeAddress.ifBlank { original.address }) }
    var origin by remember(original.id) { mutableStateOf(original.originName) }; var originAddress by remember(original.id) { mutableStateOf(original.originAddress) }
    var destination by remember(original.id) { mutableStateOf(original.destinationName) }; var destinationAddress by remember(original.id) { mutableStateOf(original.destinationAddress) }
    var note by remember(original.id) { mutableStateOf(original.note) }; var reservation by remember(original.id) { mutableStateOf(original.reservationInfo) }; var distance by remember(original.id) { mutableStateOf(original.distanceText) }
    var cost by remember(original.id) { mutableStateOf(if (original.cost == 0.0) "" else original.cost.toString()) }; var status by remember(original.id) { mutableStateOf(original.executionStatus) }; var media by remember(original.id) { mutableStateOf(original.media) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris -> media = media + uris.take((9 - media.size).coerceAtLeast(0)).mapNotNull { uri -> runCatching { repository.importMedia(uri, if (context.contentResolver.getType(uri)?.startsWith("video") == true) MediaKind.VIDEO else MediaKind.IMAGE) }.getOrNull() } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (original.title.isBlank()) "添加安排" else "编辑安排") },
        text = { Column(Modifier.heightIn(max = 650.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("安排名称") }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { PlaceCategory.entries.forEach { item -> FilterChip(category == item, { category = item }, { Text(item.label) }, leadingIcon = { Icon(item.icon(), null, Modifier.size(16.dp)) }) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = start,
                    onValueChange = { start = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("开始") },
                )
                OutlinedTextField(
                    value = end,
                    onValueChange = { end = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("结束") },
                )
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { ArrangementLocationMode.entries.forEachIndexed { index, item -> SegmentedButton(selected = mode == item, onClick = { mode = item }, shape = SegmentedButtonDefaults.itemShape(index, ArrangementLocationMode.entries.size)) { Text(item.label) } } }
            if (mode == ArrangementLocationMode.SINGLE) {
                OutlinedTextField(place, { place = it }, label = { Text("地点名称") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(placeAddress, { placeAddress = it }, label = { Text("详细地址（选填）") }, modifier = Modifier.fillMaxWidth())
            } else {
                OutlinedTextField(origin, { origin = it }, label = { Text("出发地名称") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(originAddress, { originAddress = it }, label = { Text("出发地详细地址（选填）") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(destination, { destination = it }, label = { Text("目的地名称") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(destinationAddress, { destinationAddress = it }, label = { Text("目的地详细地址（选填）") }, modifier = Modifier.fillMaxWidth())
            }
            Text("执行状态", style = MaterialTheme.typography.labelLarge); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { ItineraryExecutionStatus.entries.forEach { item -> FilterChip(status == item, { status = item }, { Text(item.label) }) } }
            OutlinedTextField(reservation, { reservation = it }, label = { Text("预约信息") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(distance, { distance = it }, label = { Text("交通/距离") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(cost, { cost = it }, label = { Text("花费") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)); OutlinedTextField(note, { note = it }, label = { Text("说明") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            if (media.isNotEmpty()) MediaStrip(media) { id -> media = media.filterNot { it.id == id } }
            OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.AddPhotoAlternate, null); Spacer(Modifier.width(6.dp)); Text("添加照片或视频（${media.size}/9）") }
        } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = { Button(onClick = { val s = combineDateAndTime(day.date, start)!!; val e = combineDateAndTime(day.date, end)!!; save(original.copy(title = title.trim(), category = category, startTime = s, endTime = e, locationMode = mode, placeName = place.trim(), placeAddress = placeAddress.trim(), address = placeAddress.trim(), originName = origin.trim(), originAddress = originAddress.trim(), destinationName = destination.trim(), destinationAddress = destinationAddress.trim(), note = note.trim(), reservationInfo = reservation.trim(), distanceText = distance.trim(), cost = cost.toDoubleOrNull() ?: 0.0, executionStatus = status, isCompleted = status == ItineraryExecutionStatus.COMPLETED, media = media)) }, enabled = title.isNotBlank() && combineDateAndTime(day.date, start) != null && combineDateAndTime(day.date, end) != null) { Text("保存") } })
}

@Composable
private fun FavoriteImportDialog(favorites: List<ItineraryItem>, onDismiss: () -> Unit, onImport: (Set<String>) -> Unit) {
    var selected by remember { mutableStateOf(setOf<String>()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("从收藏导入") }, text = { LazyColumn(Modifier.heightIn(max = 460.dp)) { items(favorites, key = { it.id }) { favorite -> Row(Modifier.fillMaxWidth().clickable { selected = if (favorite.id in selected) selected - favorite.id else selected + favorite.id }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(favorite.id in selected, { checked -> selected = if (checked) selected + favorite.id else selected - favorite.id }); Spacer(Modifier.width(8.dp)); Column { Text(favorite.title, fontWeight = FontWeight.SemiBold); Text(favorite.locationSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }, confirmButton = { Button(onClick = { onImport(selected) }, enabled = selected.isNotEmpty()) { Text("导入 ${selected.size} 项") } })
}

@Composable
fun MediaStrip(media: List<MediaReference>, onDelete: ((String) -> Unit)? = null) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { media.sortedBy { it.sortOrder }.forEach { ref -> Box { MediaThumbnail(ref, Modifier.size(86.dp)); if (onDelete != null) IconButton(onClick = { onDelete(ref.id) }, Modifier.align(Alignment.TopEnd).size(28.dp).background(Color.Black.copy(.5f), CircleShape)) { Icon(Icons.Default.Close, "移除", tint = Color.White, modifier = Modifier.size(16.dp)) } } } }
}

@Composable
fun MediaThumbnail(media: MediaReference, modifier: Modifier = Modifier) {
    val path = runCatching { android.net.Uri.parse(media.localUri).path }.getOrNull()
    val bitmap = remember(path) { path?.let { BitmapFactory.decodeFile(it) } }
    if (bitmap != null && media.kind == MediaKind.IMAGE) Image(bitmap.asImageBitmap(), media.caption, modifier.clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
    else Box(modifier.clip(RoundedCornerShape(14.dp)).background(TripItemSurface), contentAlignment = Alignment.Center) { Icon(if (media.kind == MediaKind.VIDEO) Icons.Default.PlayCircle else Icons.Default.BrokenImage, null, tint = TripLakeText) }
}

@Composable
private fun TextImportDialog(onDismiss: () -> Unit, parse: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("文字录入") }, text = { OutlinedTextField(text, { text = it }, label = { Text("粘贴一段安排文字") }, minLines = 7, modifier = Modifier.fillMaxWidth()) }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }, confirmButton = { Button(onClick = { parse(text) }, enabled = text.isNotBlank()) { Text("识别并预览") } })
}

@Composable
fun ConfirmDeleteDialog(title: String, message: String, onDismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }, confirmButton = { Button(onClick = confirm, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("确认删除") } })
}
