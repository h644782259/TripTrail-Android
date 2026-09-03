@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.personal.triptrail.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.personal.triptrail.R
import com.personal.triptrail.data.*
import com.personal.triptrail.util.ExternalApps
import com.personal.triptrail.util.ZhipuRecognitionService
import com.personal.triptrail.util.SecureRecognitionSettings
import com.personal.triptrail.util.SystemImagePickerContract
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.android.gms.tasks.Tasks
import java.io.File
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class TripHomeSection(val label: String) { CURRENT("进行中/待出发"), COMPLETED("已结束") }

private enum class SmartRecognitionSource(val title: String, val detail: String) {
    IMAGE("正在识别图片", "正在读取图片并请求大模型，请稍候…"),
    TEXT("正在识别文字", "正在发送文字内容并请求大模型，请稍候…"),
}

private data class PendingSmartJourney(
    val trip: Trip,
    val result: com.personal.triptrail.util.SmartJourneyRecognitionResult,
    val targetDayId: String? = null,
    val inputText: String,
    val source: SmartRecognitionSource = SmartRecognitionSource.TEXT,
)

private data class ItemLayout(val top: Float, val height: Float)

private data class PendingTimeReview(
    val tripId: String,
    val dayId: String,
    val adjustments: List<ItineraryTimeAdjustment>,
)

internal suspend fun recognizeScreenshotText(context: android.content.Context, uris: List<android.net.Uri>): String = withContext(Dispatchers.IO) {
    val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    try {
        uris.mapNotNull { uri ->
            runCatching { Tasks.await(recognizer.process(InputImage.fromFilePath(context, uri))) }.getOrNull()?.text?.takeIf { it.isNotBlank() }
        }.joinToString("\n")
    } finally {
        recognizer.close()
    }
}

@Composable
fun TripsScreen(repository: TripRepository, trips: List<Trip>, modifier: Modifier = Modifier, onOpen: (String) -> Unit) {
    var section by rememberSaveable { mutableStateOf(TripHomeSection.CURRENT) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Trip?>(null) }
    var deleting by remember { mutableStateOf<Trip?>(null) }
    var sharing by remember { mutableStateOf<Trip?>(null) }
    var routeTrip by remember { mutableStateOf<Trip?>(null) }
    var routeTargets by remember { mutableStateOf<List<JourneyLocationTarget>>(emptyList()) }
    var smartTrip by remember { mutableStateOf<Trip?>(null) }
    var smartTextTrip by remember { mutableStateOf<Trip?>(null) }
    var smartImageTrip by remember { mutableStateOf<Trip?>(null) }
    var pendingSmartJourney by remember { mutableStateOf<PendingSmartJourney?>(null) }
    var recognizingSmart by remember { mutableStateOf<SmartRecognitionSource?>(null) }
    var openTarget by remember { mutableStateOf<JourneyLocationTarget?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val density = LocalDensity.current
    val recognitionScope = rememberCoroutineScope()
    val smartScreenshotPicker = rememberLauncherForActivityResult(SystemImagePickerContract(multiple = true)) { uris ->
        val trip = smartImageTrip
        smartImageTrip = null
        if (uris.isEmpty() || trip == null) {
            return@rememberLauncherForActivityResult
        }
        recognitionScope.launch {
            recognizingSmart = SmartRecognitionSource.IMAGE
            try {
                runCatching {
                    val text = recognizeScreenshotText(context, uris)
                    require(text.isNotBlank()) { "没有识别到截图文字" }
                    text to ZhipuRecognitionService.recognizeJourneyText(context, text, trip.startDate)
                }.onSuccess { (text, result) -> pendingSmartJourney = PendingSmartJourney(trip, result, null, text, SmartRecognitionSource.IMAGE) }
                    .onFailure { message = "截图识别失败：${it.localizedMessage}" }
            } finally {
                recognizingSmart = null
            }
        }
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
                    Column(Modifier.fillMaxWidth().fillParentMaxHeight(.55f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
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
                        Column(Modifier.fillMaxWidth().fillParentMaxHeight(.55f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
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
                            onShare = { sharing = trip },
                            onSmartImport = { smartTrip = trip },
                            onRoute = { routeTrip = trip },
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
    sharing?.let { trip -> TripShareDialog(trip, onDismiss = { sharing = null }) }
    routeTrip?.let { trip ->
        RoutePointChooser(routeTargets.ifEmpty { trip.days.sortedBy { it.sortOrder }.flatMap { it.items.sortedBy { item -> item.sortOrder } }.flatMap { it.locationTargets } }, { routeTrip = null; routeTargets = emptyList() }) { selected ->
            routeTrip = null
            if (!ExternalApps.openAmapRoute(context, selected)) message = "至少需要两个已填写地点，并安装高德地图，才能规划路线。"
        }
    }
    smartTrip?.let { trip ->
        SmartImportChoiceDialog(
            onDismiss = { smartTrip = null },
            onChoose = { fromImage ->
                smartTrip = null
                if (fromImage) {
                    smartImageTrip = trip
                    smartScreenshotPicker.launch(Unit)
                } else {
                    smartTextTrip = trip
                }
            },
        )
    }
    smartTextTrip?.let { trip ->
        TextImportDialog(onDismiss = { smartTextTrip = null }) { text ->
            smartTextTrip = null
            recognitionScope.launch {
                recognizingSmart = SmartRecognitionSource.TEXT
                try {
                    val recognized = ZhipuRecognitionService.recognizeJourneyText(context, text, trip.startDate)
                    pendingSmartJourney = PendingSmartJourney(trip, recognized, null, text, SmartRecognitionSource.TEXT)
                } finally {
                    recognizingSmart = null
                }
            }
        }
    }
    recognizingSmart?.let { SmartRecognitionProgressDialog(it) }
    pendingSmartJourney?.let { pending ->
        SmartJourneyPreviewDialog(
            pending = pending,
            onDismiss = { pendingSmartJourney = null },
            onRetry = {
                recognitionScope.launch {
                    recognizingSmart = pending.source
                    try {
                        runCatching { ZhipuRecognitionService.recognizeJourneyText(context, pending.inputText, pending.targetDayId?.let { pending.trip.days.firstOrNull { day -> day.id == it }?.date } ?: pending.trip.startDate) }
                            .onSuccess { pendingSmartJourney = PendingSmartJourney(pending.trip, it, pending.targetDayId, pending.inputText, pending.source) }
                            .onFailure { message = "重试失败：${it.localizedMessage}" }
                    } finally {
                        recognizingSmart = null
                    }
                }
            },
            onSave = { days ->
                val count = repository.appendRecognizedJourney(pending.trip.id, days, pending.targetDayId)
                pendingSmartJourney = null
                message = "已识别并添加 $count 个安排。"
            },
        )
    }
    openTarget?.let { target -> OpenPlaceChooser(target.displayName, target.address, { openTarget = null }) { platform ->
        if (platform == "高德地图") ExternalApps.openAmapTarget(context, target)
        else ExternalApps.openDiscovery(context, platform, target.displayName, target.address)
        openTarget = null
    } }
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
            TripDropdownMenu(menu, { menu = false }) {
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
                        if (trip.destination.isNotBlank()) IconText(Icons.Default.PinDrop, trip.destination, TripInk.copy(alpha = .78f))
                        IconText(Icons.Default.CalendarMonth, "${trip.startDate.chineseDateText()} — ${trip.endDate.chineseDateText()}", TripInk.copy(alpha = .78f))
                    }
                    TripProgressRing(
                        fraction = fraction,
                        caption = if (phase == TripPhase.UPCOMING) "还有" else "第 ${currentDay} 天",
                        value = if (phase == TripPhase.UPCOMING) "${daysUntil(trip.startDate)} 天" else "$currentDay/$totalDays",
                        modifier = Modifier.padding(top = 38.dp),
                    )
                }
                if (trip.note.isNotBlank()) Text(trip.note, style = MaterialTheme.typography.bodyMedium, color = TripInk.copy(alpha = .84f), maxLines = 2)
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
    Row { Text("$label：", style = MaterialTheme.typography.labelMedium, color = TripInk.copy(alpha = .9f)); Text(item?.title?.ifBlank { "未命名安排" } ?: "暂无", style = MaterialTheme.typography.labelMedium, color = TripInk.copy(alpha = if (item == null) .74f else 1f), maxLines = 1) }
}

@Composable
private fun TripProgressRing(fraction: Float, caption: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(80.dp),
        shape = CircleShape,
        color = TripSurface.copy(alpha = .88f),
        shadowElevation = 2.dp,
        tonalElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val strokeWidth = 7.dp.toPx()
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                val arcOrigin = Offset(strokeWidth / 2f, strokeWidth / 2f)
                drawCircle(
                    color = TripLake.copy(alpha = .24f),
                    radius = (size.minDimension - strokeWidth) / 2f,
                    style = Stroke(width = strokeWidth),
                )
                if (fraction > 0f) {
                    drawArc(
                        color = TripLake,
                        startAngle = -90f,
                        sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                        useCenter = false,
                        topLeft = arcOrigin,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy((-1).dp)) {
                Text(caption, style = MaterialTheme.typography.labelSmall, color = TripInk.copy(alpha = .74f), fontWeight = FontWeight.Medium)
                Text(value, fontSize = 19.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, color = TripInk, maxLines = 1)
            }
        }
    }
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
    var start by remember(original?.id) { mutableLongStateOf(original?.startDate ?: System.currentTimeMillis().startOfDay()) }
    var end by remember(original?.id) { mutableLongStateOf(original?.endDate ?: System.currentTimeMillis().startOfDay()) }
    var note by remember(original?.id) { mutableStateOf(original?.note.orEmpty()) }
    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(28.dp), containerColor = TripSurface, title = { Text(if (original == null) "新建旅程" else "编辑旅程") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TripFormField(title, { title = it }, "旅程名称")
            TripFormField(destination, { destination = it }, "目的地")
            TripDateRangeField(start, end) { selectedStart, selectedEnd -> start = selectedStart; end = maxOf(selectedEnd, selectedStart) }
            TripFormField(note, { note = it }, "备注", minLines = 3, singleLine = false)
        } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = { Button(onClick = { save(title.trim(), destination.trim(), start, end, note.trim()) }, enabled = title.isNotBlank() && destination.isNotBlank()) { Text("保存") } })
}

@Composable
private fun DayEditorDialog(day: TripDay, onDismiss: () -> Unit, save: (TripDay) -> Unit) {
    var title by remember(day.id) { mutableStateOf(day.title) }
    var date by remember(day.id) { mutableLongStateOf(day.date) }
    var note by remember(day.id) { mutableStateOf(day.note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = TripSurface,
        title = { Text("编辑当天") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TripFormField(title, { title = it }, "当天标题")
                TripDateField(date, "日期", { date = it })
                TripFormField(note, { note = it }, "当天摘要", minLines = 3, singleLine = false)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = {
            Button(
                onClick = { save(day.copy(title = title.trim(), date = date, note = note.trim())) },
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
    var pendingSmartJourney by remember { mutableStateOf<PendingSmartJourney?>(null) }
    var recognizingSmart by remember { mutableStateOf<SmartRecognitionSource?>(null) }
    var draggingItemId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dragStartTop by remember { mutableFloatStateOf(0f) }
    var dragItemHeight by remember { mutableFloatStateOf(0f) }
    var dragTargetInsertion by remember { mutableIntStateOf(0) }
    var listRootY by remember { mutableFloatStateOf(0f) }
    var dragContainerRootY by remember { mutableFloatStateOf(0f) }
    val itemLayouts = remember { mutableStateMapOf<String, ItemLayout>() }
    var pendingTimeReview by remember { mutableStateOf<PendingTimeReview?>(null) }
    var deleting by remember { mutableStateOf<Pair<TripDay, ItineraryItem>?>(null) }
    var addMenu by remember { mutableStateOf(false) }
    var textImportDay by remember { mutableStateOf<TripDay?>(null) }
    var imageImportDay by remember { mutableStateOf<TripDay?>(null) }
    var favoriteImportDay by remember { mutableStateOf<TripDay?>(null) }
    var editingDay by remember { mutableStateOf<TripDay?>(null) }
    var deletingDay by remember { mutableStateOf<TripDay?>(null) }
    var dayMenu by remember { mutableStateOf(false) }
    var inputMenu by remember { mutableStateOf(false) }
    var sharingDay by remember { mutableStateOf<TripDay?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var routeTrip by remember { mutableStateOf<Trip?>(null) }
    var routeTargets by remember { mutableStateOf<List<JourneyLocationTarget>>(emptyList()) }
    var openTarget by remember { mutableStateOf<JourneyLocationTarget?>(null) }
    val context = LocalContext.current
    val density = LocalDensity.current
    val recognitionScope = rememberCoroutineScope()
    val screenshotPicker = rememberLauncherForActivityResult(SystemImagePickerContract(multiple = true)) { uris ->
        val day = imageImportDay; imageImportDay = null
        if (uris.isNotEmpty() && day != null) {
            recognitionScope.launch {
                recognizingSmart = SmartRecognitionSource.IMAGE
                try {
                    runCatching {
                        val text = recognizeScreenshotText(context, uris)
                        require(text.isNotBlank()) { "没有识别到截图文字" }
                        text to ZhipuRecognitionService.recognizeJourneyText(context, text, day.date)
                    }.onSuccess { (text, result) -> pendingSmartJourney = PendingSmartJourney(trip, result, day.id, text, SmartRecognitionSource.IMAGE) }
                        .onFailure { message = "截图识别失败：${it.localizedMessage}" }
                } finally {
                    recognizingSmart = null
                }
            }
        }
    }
    Box(modifier.fillMaxSize().background(TripCanvas)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TripRoundAction(Icons.Default.ArrowBack, "返回", onBack); Text(trip.title, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box {
                    TripRoundAction(Icons.Default.MoreHoriz, "当天更多操作") { dayMenu = true }
                    TripDropdownMenu(dayMenu, { dayMenu = false }) {
                        selectedDay?.let { day ->
                            DropdownMenuItem({ Text("编辑当天") }, { dayMenu = false; editingDay = day }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                            DropdownMenuItem({ Text("录入当天") }, { dayMenu = false; inputMenu = true }, leadingIcon = { Icon(Icons.Default.AddBox, null) }, trailingIcon = { Icon(Icons.Default.ChevronRight, null) })
                            DropdownMenuItem({ Text("分享当天") }, {
                                dayMenu = false
                                sharingDay = day
                            }, leadingIcon = { Icon(Icons.Default.Share, null) })
                            DropdownMenuItem({ Text("规划当天路线") }, {
                                dayMenu = false
                                routeTargets = day.items.sortedBy { it.sortOrder }.flatMap { it.locationTargets }; routeTrip = trip
                            }, leadingIcon = { Icon(Icons.Default.Route, null) })
                            HorizontalDivider()
                            DropdownMenuItem({ Text("删除当天", color = MaterialTheme.colorScheme.error) }, { dayMenu = false; deletingDay = day }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) })
                        }
                    }
                    TripDropdownMenu(inputMenu, { inputMenu = false }) {
                        selectedDay?.let { day ->
                            DropdownMenuItem({ Text("文字录入") }, { inputMenu = false; textImportDay = day }, leadingIcon = { Icon(Icons.Default.TextFields, null) })
                            DropdownMenuItem({ Text("图片录入") }, { inputMenu = false; imageImportDay = day; screenshotPicker.launch(Unit) }, leadingIcon = { Icon(Icons.Default.PhotoLibrary, null) })
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                days.forEachIndexed { index, day ->
                    val active = day.id == selectedDay?.id
                    var dragX by remember(day.id) { mutableFloatStateOf(0f) }
                    Surface(onClick = { selectedDayId = day.id }, shape = RoundedCornerShape(24.dp), color = if (active) TripLake else TripSurface, border = if (active) null else androidx.compose.foundation.BorderStroke(.8.dp, TripLake.copy(alpha = .2f))) {
                        Row(
                            Modifier.pointerInput(day.id, index, days.size) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { dragX = 0f }, onDragEnd = { dragX = 0f }, onDragCancel = { dragX = 0f },
                                ) { change, amount ->
                                    change.consume(); dragX += amount.x
                                    if (abs(dragX) > 56f) { repository.moveDay(trip.id, day.id, if (dragX > 0) 1 else -1); dragX = 0f }
                                }
                            }.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Spacer(Modifier.width(4.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("第 ${index + 1} 天", style = MaterialTheme.typography.labelSmall, color = if (active) Color.White else TripInk); Text(day.date.chineseDateText(), style = MaterialTheme.typography.labelMedium, color = if (active) Color.White else TripInk) }
                        }
                    }
                }
                TripRoundAction(Icons.Default.Add, "添加一天") { repository.addDay(trip.id)?.let { selectedDayId = it.id } }
            }
            selectedDay?.let { day ->
                val orderedItems = day.items.sortedBy { it.sortOrder }
                val draggedItem = draggingItemId?.let { id -> orderedItems.firstOrNull { it.id == id } }
                val remainingItems = orderedItems.filterNot { it.id == draggingItemId }
                val placeholderHeight = with(androidx.compose.ui.platform.LocalDensity.current) {
                    (dragItemHeight.takeIf { it > 0f } ?: 170.dp.toPx()).toDp()
                }
                fun updateDragTarget() {
                    if (draggingItemId == null) return
                    val remaining = orderedItems.filterNot { it.id == draggingItemId }
                    val center = dragStartTop + dragOffsetY + dragItemHeight / 2f
                    val insertion = remaining.indexOfFirst { candidate ->
                        itemLayouts[candidate.id]?.let { center < it.top + it.height / 2f } == true
                    }.let { if (it < 0) remaining.size else it }
                    dragTargetInsertion = insertion.coerceIn(0, remaining.size)
                }
                fun finishDrag() {
                    val item = draggingItemId?.let { id -> orderedItems.firstOrNull { it.id == id } }
                    if (item != null) {
                        val from = orderedItems.indexOfFirst { it.id == item.id }
                        val remaining = orderedItems.filterNot { it.id == item.id }
                        val insertion = dragTargetInsertion.coerceIn(0, remaining.size)
                        if (from >= 0 && insertion != from) {
                            val result = repository.moveItemWithTimeReview(trip.id, day.id, item.id, insertion)
                            if (result.timeAdjustments.isNotEmpty()) {
                                pendingTimeReview = PendingTimeReview(trip.id, day.id, result.timeAdjustments)
                            }
                        }
                    }
                    draggingItemId = null
                    dragOffsetY = 0f
                    dragStartTop = 0f
                    dragItemHeight = 0f
                }
                fun moveByButton(item: ItineraryItem, delta: Int) {
                    val ordered = day.items.sortedBy { it.sortOrder }
                    val from = ordered.indexOfFirst { it.id == item.id }
                    val to = (from + delta).coerceIn(0, ordered.lastIndex)
                    if (from >= 0 && from != to) {
                        val result = repository.moveItemWithTimeReview(trip.id, day.id, item.id, to)
                        if (result.timeAdjustments.isNotEmpty()) {
                            pendingTimeReview = PendingTimeReview(trip.id, day.id, result.timeAdjustments)
                        }
                    }
                }
                Box(Modifier.fillMaxSize().onGloballyPositioned { dragContainerRootY = it.positionInRoot().y }) {
                    LazyColumn(
                        Modifier.fillMaxSize().onGloballyPositioned { listRootY = it.positionInRoot().y },
                        contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 112.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item { Text(day.title.ifBlank { "第 ${(days.indexOf(day) + 1)} 天" }, style = MaterialTheme.typography.headlineSmall, color = TripInk) }
                        if (draggedItem == null) {
                            items(orderedItems, key = { it.id }) { item ->
                                ItineraryItemCard(
                                    item, { editing = day to item }, { openTarget = it },
                                    { moveByButton(item, -1) },
                                    { moveByButton(item, 1) },
                                    { deleting = day to item },
                                    modifier = Modifier.onGloballyPositioned { coordinates ->
                                        itemLayouts[item.id] = ItemLayout(coordinates.positionInRoot().y - listRootY, coordinates.size.height.toFloat())
                                    },
                                    onDragStart = {
                                        draggingItemId = item.id
                                        dragOffsetY = 0f
                                        dragStartTop = itemLayouts[item.id]?.top ?: 0f
                                        dragItemHeight = itemLayouts[item.id]?.height ?: with(density) { 170.dp.toPx() }
                                        dragTargetInsertion = orderedItems.indexOf(item).coerceIn(0, orderedItems.size - 1)
                                    },
                                    onDragBy = { amount -> dragOffsetY += amount; updateDragTarget() },
                                    onDragEnd = ::finishDrag,
                                    onDragCancel = {
                                        draggingItemId = null; dragOffsetY = 0f; dragStartTop = 0f; dragItemHeight = 0f
                                    },
                                )
                            }
                        } else {
                            remainingItems.forEachIndexed { index, arrangement ->
                                if (index == dragTargetInsertion) item(key = "drag-placeholder-$index") { DragPlaceholder(placeholderHeight) }
                                item(key = arrangement.id) {
                                    ItineraryItemCard(
                                        arrangement, { editing = day to arrangement }, { openTarget = it },
                                        { moveByButton(arrangement, -1) },
                                        { moveByButton(arrangement, 1) },
                                        { deleting = day to arrangement },
                                        modifier = Modifier.onGloballyPositioned { coordinates ->
                                            itemLayouts[arrangement.id] = ItemLayout(coordinates.positionInRoot().y - listRootY, coordinates.size.height.toFloat())
                                        },
                                    )
                                }
                            }
                            if (dragTargetInsertion >= remainingItems.size) item(key = "drag-placeholder-end") { DragPlaceholder(placeholderHeight) }
                        }
                        item {
                            Box {
                                TextButton(onClick = { addMenu = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("添加安排", fontWeight = FontWeight.Bold) }
                                TripDropdownMenu(addMenu, { addMenu = false }) {
                                    DropdownMenuItem({ Text("手动") }, { addMenu = false; editing = day to ItineraryItem(startTime = repository.suggestedStart(day), endTime = repository.suggestedStart(day) + 3_600_000) }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                                    DropdownMenuItem({ Text("从收藏导入") }, { addMenu = false; favoriteImportDay = day }, leadingIcon = { Icon(Icons.Default.FavoriteBorder, null) })
                                    DropdownMenuItem({ Text("文字录入") }, { addMenu = false; textImportDay = day }, leadingIcon = { Icon(Icons.Default.TextFields, null) })
                                    DropdownMenuItem({ Text("图片录入") }, { addMenu = false; imageImportDay = day; screenshotPicker.launch(Unit) }, leadingIcon = { Icon(Icons.Default.PhotoLibrary, null) })
                                }
                            }
                        }
                    }
                    // The handle lives outside LazyColumn, so replacing the list contents
                    // with the placeholder cannot cancel its pointer-input coroutine.
                    orderedItems.forEach { arrangement ->
                        itemLayouts[arrangement.id]?.let { layout ->
                            Box(
                                Modifier.fillMaxWidth()
                                    .offset { IntOffset(0, (layout.top + listRootY - dragContainerRootY).roundToInt()) }
                                    .height(48.dp)
                                    .zIndex(10f),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Box(
                                    Modifier.padding(end = 4.dp).size(48.dp).pointerInput(arrangement.id) {
                                        detectDragGestures(
                                            onDragStart = {
                                                draggingItemId = arrangement.id
                                                dragOffsetY = 0f
                                                dragStartTop = layout.top
                                                dragItemHeight = layout.height
                                                dragTargetInsertion = orderedItems.indexOf(arrangement).coerceIn(0, orderedItems.size - 1)
                                            },
                                            onDragEnd = ::finishDrag,
                                            onDragCancel = {
                                                draggingItemId = null; dragOffsetY = 0f; dragStartTop = 0f; dragItemHeight = 0f
                                            },
                                        ) { change, amount ->
                                            change.consume()
                                            dragOffsetY += amount.y
                                            updateDragTarget()
                                        }
                                    },
                                )
                            }
                        }
                    }
                    draggedItem?.let { item ->
                        ItineraryItemCard(
                            item, { }, { }, {}, {}, {},
                            modifier = Modifier.offset { IntOffset(0, (dragStartTop + listRootY - dragContainerRootY + dragOffsetY).roundToInt()) }
                                .zIndex(20f),
                        )
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
            recognizingSmart = SmartRecognitionSource.TEXT
            try {
                val recognized = ZhipuRecognitionService.recognizeJourneyText(context, text, day.date)
                pendingSmartJourney = PendingSmartJourney(trip, recognized, day.id, text, SmartRecognitionSource.TEXT)
            } finally {
                recognizingSmart = null
            }
        }
    } }
    recognizingSmart?.let { SmartRecognitionProgressDialog(it) }
    pendingSmartJourney?.let { pending ->
        SmartJourneyPreviewDialog(
            pending = pending,
            onDismiss = { pendingSmartJourney = null },
            onRetry = {
                recognitionScope.launch {
                    recognizingSmart = pending.source
                    try {
                        runCatching { ZhipuRecognitionService.recognizeJourneyText(context, pending.inputText, pending.targetDayId?.let { pending.trip.days.firstOrNull { day -> day.id == it }?.date } ?: pending.trip.startDate) }
                            .onSuccess { pendingSmartJourney = PendingSmartJourney(pending.trip, it, pending.targetDayId, pending.inputText, pending.source) }
                            .onFailure { message = "重试失败：${it.localizedMessage}" }
                    } finally {
                        recognizingSmart = null
                    }
                }
            },
            onSave = { days ->
                val count = repository.appendRecognizedJourney(pending.trip.id, days, pending.targetDayId)
                pendingSmartJourney = null
                message = "已识别并添加 $count 个安排。"
            },
        )
    }
    pendingTimeReview?.let { request ->
        ItineraryTimeReviewDialog(
            request = request,
            onDismiss = { pendingTimeReview = null },
            onSave = { adjustments ->
                repository.applyTimeAdjustments(request.tripId, request.dayId, adjustments)
                pendingTimeReview = null
            },
        )
    }
    favoriteImportDay?.let { day -> FavoriteImportDialog(data.favorites, { favoriteImportDay = null }) { ids -> repository.importFavorites(trip.id, day.id, ids); favoriteImportDay = null } }
    sharingDay?.let { day -> TripShareDialog(trip, initialDayId = day.id, onDismiss = { sharingDay = null }) }
    openTarget?.let { target ->
        OpenPlaceChooser(target.displayName, target.address, { openTarget = null }) { platform ->
            if (platform == "高德地图") ExternalApps.openAmapTarget(context, target)
            else ExternalApps.openDiscovery(context, platform, target.displayName, target.address)
            openTarget = null
        }
    }
    message?.let { AlertDialog(onDismissRequest = { message = null }, title = { Text("提示") }, text = { Text(it) }, confirmButton = { TextButton(onClick = { message = null }) { Text("好") } }) }
}

@Composable
private fun DragPlaceholder(height: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.fillMaxWidth().height(height).drawBehind {
            drawRoundRect(
                color = TripLakeText.copy(alpha = .72f),
                style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 9.dp.toPx()))),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        Text("松开后放置于此", style = MaterialTheme.typography.labelMedium, color = TripLakeText)
    }
}

private data class TimeReviewDraft(val item: ItineraryItem, val startTime: Long, val endTime: Long)

@Composable
private fun ItineraryTimeReviewDialog(
    request: PendingTimeReview,
    onDismiss: () -> Unit,
    onSave: (List<ItineraryTimeAdjustment>) -> Unit,
) {
    var drafts by remember(request) {
        mutableStateOf(request.adjustments.map { TimeReviewDraft(it.item, it.suggestedStartTime, it.suggestedEndTime) })
    }
    val hasInvalidRange = drafts.any { it.endTime < it.startTime }
    val hasOverlap = drafts.sortedBy { it.startTime }.zipWithNext().any { (first, second) -> first.endTime > second.startTime }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = TripSurface,
        title = { Text("调整旅程时间") },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("安排时长不同，已按新的顺序预填时间，请确认或修改。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                drafts.forEachIndexed { index, draft ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(draft.item.title.ifBlank { "未命名安排" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            TripTimeRangeField(draft.startTime, draft.endTime) { start, end ->
                                drafts = drafts.toMutableList().also { it[index] = draft.copy(startTime = start, endTime = end) }
                            }
                            if (draft.endTime < draft.startTime) {
                                Text("结束时间不能早于开始时间", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                if (hasOverlap) {
                    Text("调整后的安排存在时间重叠，请继续修改。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("稍后修改") } },
        confirmButton = {
            Button(
                onClick = { onSave(drafts.map { ItineraryTimeAdjustment(it.item, it.startTime, it.endTime) }) },
                enabled = !hasInvalidRange && !hasOverlap,
            ) { Text("保存时间") }
        },
    )
}

@Composable
private fun ItineraryItemCard(
    item: ItineraryItem,
    onEdit: () -> Unit,
    onNavigate: (JourneyLocationTarget) -> Unit,
    moveUp: () -> Unit,
    moveDown: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onDragStart: () -> Unit = {},
    onDragBy: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
) {
    val statusColor = when (item.executionStatus) { ItineraryExecutionStatus.NOT_STARTED -> Color(0xFF8F662E); ItineraryExecutionStatus.IN_PROGRESS -> TripLake; ItineraryExecutionStatus.COMPLETED -> TripSage }
    var menu by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        color = if (item.executionStatus == ItineraryExecutionStatus.COMPLETED) Color(0xFFE7EEE8) else TripSurface,
        border = androidx.compose.foundation.BorderStroke(if (item.executionStatus == ItineraryExecutionStatus.IN_PROGRESS) 2.dp else 1.dp, statusColor.copy(alpha = .58f)), shadowElevation = if (item.executionStatus == ItineraryExecutionStatus.IN_PROGRESS) 8.dp else 3.dp,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Surface(shape = CircleShape, color = if (item.executionStatus == ItineraryExecutionStatus.IN_PROGRESS) statusColor else statusColor.copy(alpha = .10f), border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = .3f)), onClick = onEdit) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { Icon(when (item.executionStatus) { ItineraryExecutionStatus.NOT_STARTED -> Icons.Default.Schedule; ItineraryExecutionStatus.IN_PROGRESS -> Icons.Default.PlayArrow; ItineraryExecutionStatus.COMPLETED -> Icons.Default.Check }, item.executionStatus.label, tint = if (item.executionStatus == ItineraryExecutionStatus.IN_PROGRESS) Color.White else statusColor, modifier = Modifier.size(20.dp)) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).clickable { onEdit() }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(item.category.icon(), null, tint = if (item.executionStatus == ItineraryExecutionStatus.COMPLETED) Color.Gray else TripInk, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(7.dp)); Text(item.title.ifBlank { "未命名安排" }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Surface(shape = RoundedCornerShape(18.dp), color = statusColor.copy(alpha = if (item.executionStatus == ItineraryExecutionStatus.IN_PROGRESS) 1f else .12f)) { Text("${item.startTime.timeText()}–${item.endTime.timeText()}", Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium, color = if (item.executionStatus == ItineraryExecutionStatus.IN_PROGRESS) Color.White else TripInk) }
                }
                item.locationTargets.forEach { target ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onNavigate(target) }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (target.role == JourneyLocationRole.ORIGIN) Icons.Default.MyLocation else Icons.Default.PinDrop, null, Modifier.size(17.dp), tint = TripLakeText)
                        Spacer(Modifier.width(6.dp))
                        Text("${target.role.label}：${target.displayName}", style = MaterialTheme.typography.bodyMedium, color = TripLakeText)
                    }
                }
                if (item.reservationInfo.isNotBlank()) IconText(Icons.Default.ConfirmationNumber, item.reservationInfo, MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.distanceText.isNotBlank() || item.cost > 0) IconText(Icons.Default.SwapHoriz, listOfNotNull(item.distanceText.takeIf { it.isNotBlank() }, item.cost.takeIf { it > 0 }?.let { "¥${String.format(Locale.CHINA, "%.2f", it)}" }).joinToString("   "), MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.note.isNotBlank()) Text(item.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.media.isNotEmpty()) MediaStrip(item.media)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.DragIndicator,
                    "长按拖动安排排序",
                    modifier = Modifier.size(30.dp),
                    tint = TripLakeText.copy(alpha = .72f),
                )
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.MoreVert, "安排操作") }
                    TripDropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("编辑") }, { menu = false; onEdit() }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                    DropdownMenuItem({ Text("上移") }, { menu = false; moveUp() }, leadingIcon = { Icon(Icons.Default.KeyboardArrowUp, null) })
                    DropdownMenuItem({ Text("下移") }, { menu = false; moveDown() }, leadingIcon = { Icon(Icons.Default.KeyboardArrowDown, null) })
                    DropdownMenuItem({ Text("删除", color = MaterialTheme.colorScheme.error) }, { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ArrangementEditorDialog(repository: TripRepository, day: TripDay, original: ItineraryItem, onDismiss: () -> Unit, save: (ItineraryItem) -> Unit) {
    var title by remember(original.id) { mutableStateOf(original.title) }; var category by remember(original.id) { mutableStateOf(original.category) }
    var start by remember(original.id) { mutableLongStateOf(original.startTime) }; var end by remember(original.id) { mutableLongStateOf(original.endTime) }
    var mode by remember(original.id) { mutableStateOf(original.locationMode) }
    var place by remember(original.id) { mutableStateOf(original.placeName.ifBlank { original.title.takeIf { original.address.isNotBlank() }.orEmpty() }) }; var placeAddress by remember(original.id) { mutableStateOf(original.placeAddress.ifBlank { original.address }) }
    var origin by remember(original.id) { mutableStateOf(original.originName) }; var originAddress by remember(original.id) { mutableStateOf(original.originAddress) }
    var destination by remember(original.id) { mutableStateOf(original.destinationName) }; var destinationAddress by remember(original.id) { mutableStateOf(original.destinationAddress) }
    var note by remember(original.id) { mutableStateOf(original.note) }; var reservation by remember(original.id) { mutableStateOf(original.reservationInfo) }; var distance by remember(original.id) { mutableStateOf(original.distanceText) }
    var cost by remember(original.id) { mutableStateOf(if (original.cost == 0.0) "" else original.cost.toString()) }; var media by remember(original.id) { mutableStateOf(original.media) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(SystemImagePickerContract(multiple = true, allowImagesAndVideos = true)) { uris -> media = media + uris.take((9 - media.size).coerceAtLeast(0)).mapNotNull { uri -> runCatching { repository.importMedia(uri, if (context.contentResolver.getType(uri)?.startsWith("video") == true) MediaKind.VIDEO else MediaKind.IMAGE) }.getOrNull() } }
    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(28.dp), containerColor = TripSurface, title = { Text(if (original.title.isBlank()) "添加安排" else "编辑安排") },
        text = { Column(Modifier.heightIn(max = 650.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TripFormField(title, { title = it }, "安排名称")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { PlaceCategory.entries.forEach { item -> FilterChip(category == item, { category = item }, { Text(item.label) }, leadingIcon = { Icon(item.icon(), null, Modifier.size(16.dp)) }) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TripTimeRangeField(start, end) { selectedStart, selectedEnd -> start = selectedStart; end = selectedEnd }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { ArrangementLocationMode.entries.forEachIndexed { index, item -> SegmentedButton(selected = mode == item, onClick = { mode = item }, shape = SegmentedButtonDefaults.itemShape(index, ArrangementLocationMode.entries.size)) { Text(item.label) } } }
            if (mode == ArrangementLocationMode.SINGLE) {
                TripFormField(place, { place = it }, "地点名称"); TripFormField(placeAddress, { placeAddress = it }, "详细地址（选填）")
            } else {
                TripFormField(origin, { origin = it }, "出发地名称"); TripFormField(originAddress, { originAddress = it }, "出发地详细地址（选填）"); TripFormField(destination, { destination = it }, "目的地名称"); TripFormField(destinationAddress, { destinationAddress = it }, "目的地详细地址（选填）")
            }
            TripFormField(reservation, { reservation = it }, "预约信息"); TripFormField(distance, { distance = it }, "交通/距离"); TripFormField(cost, { cost = it }, "花费", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)); TripFormField(note, { note = it }, "说明", minLines = 3, singleLine = false)
            if (media.isNotEmpty()) MediaStrip(media) { id -> media = media.filterNot { it.id == id } }
            OutlinedButton(onClick = { picker.launch(Unit) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.AddPhotoAlternate, null); Spacer(Modifier.width(6.dp)); Text("添加照片或视频（${media.size}/9）") }
        } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = { Button(onClick = { save(original.copy(title = title.trim(), category = category, startTime = start, endTime = end, locationMode = mode, placeName = place.trim(), placeAddress = placeAddress.trim(), address = placeAddress.trim(), originName = origin.trim(), originAddress = originAddress.trim(), destinationName = destination.trim(), destinationAddress = destinationAddress.trim(), note = note.trim(), reservationInfo = reservation.trim(), distanceText = distance.trim(), cost = cost.toDoubleOrNull() ?: 0.0, isAutomaticCompletionOverridden = false, media = media).withAutomaticExecutionStatus()) }, enabled = title.isNotBlank() && end > start) { Text("保存") } })
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
private fun SmartJourneyPreviewDialog(
    pending: PendingSmartJourney,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onSave: (List<com.personal.triptrail.data.RecognizedJourneyDay>) -> Unit,
) {
    var days by remember(pending.result) { mutableStateOf(pending.result.days) }
    val itemCount = days.sumOf { it.items.size }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = TripSurface,
        title = { Text(if (pending.targetDayId == null) "录入整段旅程" else "录入当天") },
        text = {
            Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("已识别 ${days.size} 天、$itemCount 个安排。保存前可以修改安排名称。", color = TripInk)
                pending.result.fallbackMessage?.let { notice ->
                    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFFFF3E0)) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(notice, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Color(0xFF8A4B08))
                            TextButton(onClick = onRetry) { Text("重试大模型") }
                        }
                    }
                }
                days.forEachIndexed { dayIndex, day ->
                    Surface(shape = RoundedCornerShape(18.dp), color = TripCanvas, border = androidx.compose.foundation.BorderStroke(.7.dp, TripMist.copy(alpha = .55f))) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(day.title.ifBlank { "第 ${day.sourceDayNumber} 天" }, style = MaterialTheme.typography.titleMedium, color = TripInk)
                            day.items.forEachIndexed { itemIndex, item ->
                                TripFormField(
                                    value = item.title,
                                    onValueChange = { title -> days = days.mapIndexed { d, value -> if (d == dayIndex) value.copy(items = value.items.mapIndexed { i, current -> if (i == itemIndex) current.copy(title = title) else current }) else value } },
                                    label = "安排 ${itemIndex + 1}",
                                )
                                Text("${item.startTime.timeText()} – ${item.endTime.timeText()}${item.locationSummary.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = { Button(onClick = { onSave(days) }, enabled = days.any { it.items.isNotEmpty() }) { Text("保存全部") } },
    )
}

@Composable
private fun SmartRecognitionProgressDialog(source: SmartRecognitionSource) {
    AlertDialog(
        onDismissRequest = {},
        shape = RoundedCornerShape(24.dp),
        containerColor = TripSurface,
        title = { Text(source.title) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(color = TripLakeText, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                Text(source.detail, color = TripInk)
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun TextImportDialog(onDismiss: () -> Unit, parse: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(28.dp), containerColor = TripSurface, title = { Text("文字录入") }, text = { TripFormField(text, { text = it }, "粘贴一段安排文字", minLines = 7, singleLine = false) }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }, confirmButton = { Button(onClick = { parse(text) }, enabled = text.isNotBlank()) { Text("识别并预览") } })
}

@Composable
fun ConfirmDeleteDialog(title: String, message: String, onDismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }, confirmButton = { Button(onClick = confirm, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("确认删除") } })
}

@Composable
private fun SmartImportChoiceDialog(onDismiss: () -> Unit, onChoose: (Boolean) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("智能录入") }, text = { Text("请选择录入方式") },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }, confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onChoose(true) }) { Text("截图录入") }
                TextButton(onClick = { onChoose(false) }) { Text("文字录入") }
            }
        })
}

@Composable
private fun RoutePointChooser(targets: List<JourneyLocationTarget>, onDismiss: () -> Unit, onConfirm: (List<JourneyLocationTarget>) -> Unit) {
    val distinct = targets.distinctBy { "${it.role}:${it.displayName}:${it.address}" }
    var selected by remember(distinct) { mutableStateOf(distinct.toSet()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("选择路线地点") }, text = {
        Column(Modifier.heightIn(max = 520.dp)) {
            Text("默认全选，可取消不需要规划的地点。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { selected = distinct.toSet() }) { Text("全选") }
                TextButton(onClick = { selected = emptySet() }) { Text("取消全选") }
            }
            LazyColumn { items(distinct) { target ->
                Row(Modifier.fillMaxWidth().clickable { selected = if (target in selected) selected - target else selected + target }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(target in selected, { checked -> selected = if (checked) selected + target else selected - target })
                    Column { Text(target.displayName, fontWeight = FontWeight.SemiBold); if (target.address.isNotBlank()) Text(target.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            } }
        }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }, confirmButton = { Button(onClick = { onConfirm(distinct.filter { it in selected }) }, enabled = selected.size >= 2) { Text("规划 ${selected.size} 个地点") } })
}
