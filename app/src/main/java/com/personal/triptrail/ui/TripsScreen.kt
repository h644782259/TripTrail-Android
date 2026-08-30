@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.personal.triptrail.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.personal.triptrail.data.*
import com.personal.triptrail.util.ExternalApps
import com.personal.triptrail.util.SmartImportParser
import com.personal.triptrail.util.TripFileService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun TripsScreen(repository: TripRepository, trips: List<Trip>, modifier: Modifier = Modifier, onOpen: (String) -> Unit) {
    var creating by remember { mutableStateOf(false) }
    val ordered = remember(trips) { trips.timelineSorted() }
    val featured = ordered.firstOrNull { it.phase() != TripPhase.HISTORY }
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("旅程") }) },
        floatingActionButton = { FloatingActionButton(onClick = { creating = true }) { Icon(Icons.Default.Add, "新建旅程") } }
    ) { padding ->
        if (ordered.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(28.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Map, null, Modifier.size(54.dp), tint = Lake)
                    Text("还没有旅程", style = MaterialTheme.typography.headlineSmall)
                    Text("先记录目的地和日期，再按天安排地点。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { creating = true }) { Text("创建第一段旅程") }
                }
            }
        } else LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            featured?.let { trip -> item { FeaturedTripCard(trip) { onOpen(trip.id) } } }
            item { Text("全部旅程", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            items(ordered, key = { it.id }) { trip -> TripCard(trip) { onOpen(trip.id) } }
        }
    }
    if (creating) NewTripDialog(onDismiss = { creating = false }) { title, destination, start, end, note ->
        val trip = repository.createTrip(title, destination, start, end, note); creating = false; onOpen(trip.id)
    }
}

@Composable
private fun FeaturedTripCard(trip: Trip, open: () -> Unit) {
    val today = System.currentTimeMillis().startOfDay()
    val days = ChronoUnit.DAYS.between(today.localDate(), trip.startDate.localDate()).toInt()
    val status = when { trip.phase() == TripPhase.CURRENT -> "正在旅行"; days == 0 -> "今天出发"; else -> "还有 $days 天" }
    Card(onClick = open, shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Ink)) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Ink, Lake))).padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(status, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(trip.title, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleLarge)
            Text("${trip.destination} · ${trip.startDate.chineseDateText()} — ${trip.endDate.chineseDateText()}", color = androidx.compose.ui.graphics.Color.White.copy(alpha = .78f))
            LinearProgressIndicator(progress = { if (trip.totalCount == 0) 0f else trip.completedCount.toFloat() / trip.totalCount }, modifier = Modifier.fillMaxWidth(), color = Coral)
        }
    }
}

@Composable
private fun TripCard(trip: Trip, open: () -> Unit) {
    ElevatedCard(onClick = open, shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(trip.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${trip.destination} · ${trip.startDate.chineseDateText()} — ${trip.endDate.chineseDateText()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${trip.days.size} 天 · ${trip.completedCount}/${trip.totalCount} 项完成", style = MaterialTheme.typography.labelMedium, color = Lake)
            }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@Composable
private fun NewTripDialog(onDismiss: () -> Unit, save: (String, String, Long, Long, String) -> Unit) {
    var title by remember { mutableStateOf("") }; var destination by remember { mutableStateOf("") }
    var start by remember { mutableStateOf(System.currentTimeMillis().dateText()) }; var end by remember { mutableStateOf(System.currentTimeMillis().dateText()) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("新建旅程") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("旅程名称") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(destination, { destination = it }, label = { Text("目的地") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(start, { start = it }, label = { Text("开始日期（yyyy-MM-dd）") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(end, { end = it }, label = { Text("结束日期（yyyy-MM-dd）") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(note, { note = it }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = { Button(onClick = { save(title.trim(), destination.trim(), parseDate(start)!!, parseDate(end)!!, note.trim()) },
            enabled = title.isNotBlank() && destination.isNotBlank() && parseDate(start) != null && parseDate(end) != null) { Text("创建") } }
    )
}

@Composable
fun TripDetailScreen(repository: TripRepository, tripId: String, onBack: () -> Unit) {
    val data by repository.data.collectAsStateWithLifecycle()
    val trip = data.trips.firstOrNull { it.id == tripId }
    if (trip == null) { LaunchedEffect(Unit) { onBack() }; return }
    val context = LocalContext.current
    var edit by remember { mutableStateOf<Pair<TripDay, ItineraryItem>?>(null) }
    var importingDay by remember { mutableStateOf<TripDay?>(null) }
    var textImportDay by remember { mutableStateOf<TripDay?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var deleteTrip by remember { mutableStateOf(false) }
    var deleteItem by remember { mutableStateOf<Pair<TripDay, ItineraryItem>?>(null) }
    var exportText by remember { mutableStateOf<String?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.triptrail.journey")) { uri ->
        if (uri != null) runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(exportText.orEmpty()) } }
            .onSuccess { message = "分享文件已导出。" }.onFailure { message = "导出失败：${it.localizedMessage}" }
        exportText = null
    }
    val screenshotPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val day = importingDay; importingDay = null
        if (uri != null && day != null) {
            val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            runCatching { InputImage.fromFilePath(context, uri) }.onSuccess { image ->
                recognizer.process(image).addOnSuccessListener { result ->
                    edit = day to SmartImportParser.parse(result.text, day.date, repository.suggestedStart(day))
                }.addOnFailureListener { message = "截图识别失败：${it.localizedMessage}" }.addOnCompleteListener { recognizer.close() }
            }.onFailure { message = "无法读取截图：${it.localizedMessage}" }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { TopAppBar(
            title = { Text(trip.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } },
            actions = {
                IconButton(onClick = { repository.archiveTrip(trip.id); message = "已收进足迹，可在足迹页继续补充。" }) { Icon(Icons.Default.BookmarkAdd, "收进足迹") }
                IconButton(onClick = { exportText = TripFileService.shareTrip(trip); exporter.launch("${trip.title}.triptrail") }) { Icon(Icons.Default.Share, "导出分享") }
                IconButton(onClick = { deleteTrip = true }) { Icon(Icons.Default.Delete, "删除旅程") }
            }
        ) }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { TripHeader(trip, onNavigate = {
                val next = trip.nextUnfinishedItem
                message = if (next == null) "所有安排都已完成。" else if (ExternalApps.openAmap(context, next)) null else "未检测到高德地图 App，请先安装后重试。"
            }) }
            items(trip.days.sortedBy { it.sortOrder }, key = { it.id }) { day ->
                DaySection(day,
                    add = { val start = repository.suggestedStart(day); edit = day to ItineraryItem(startTime = start, endTime = start + 3_600_000) },
                    textImport = { textImportDay = day }, screenshotImport = { importingDay = day; screenshotPicker.launch("image/*") },
                    edit = { edit = day to it }, toggle = { repository.saveItem(trip.id, day.id, it.copy(isCompleted = !it.isCompleted, isAutomaticCompletionOverridden = true)) },
                    navigate = { message = if (ExternalApps.openAmap(context, it)) null else "未检测到高德地图 App。" }, delete = { deleteItem = day to it })
            }
            item { OutlinedButton(onClick = { repository.addDay(trip.id) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("继续添加一天") } }
        }
    }

    edit?.let { (day, item) -> ItemEditorDialog(repository, day, item, onDismiss = { edit = null }) { saved -> repository.saveItem(trip.id, day.id, saved); edit = null } }
    textImportDay?.let { day -> TextImportDialog(onDismiss = { textImportDay = null }) { text ->
        edit = day to SmartImportParser.parse(text, day.date, repository.suggestedStart(day)); textImportDay = null
    } }
    deleteItem?.let { (day, item) -> ConfirmDeleteDialog("删除安排？", "“${item.title}”及其中的媒体引用将被永久删除，手机中的原文件不会受到影响。", onDismiss = { deleteItem = null }) {
        repository.deleteItem(trip.id, day.id, item.id); deleteItem = null
    } }
    if (deleteTrip) ConfirmDeleteDialog("删除旅程？", "“${trip.title}”及其中的每日安排和媒体引用将被永久删除。", onDismiss = { deleteTrip = false }) {
        repository.deleteTrip(trip.id); deleteTrip = false; onBack()
    }
    message?.let { AlertDialog(onDismissRequest = { message = null }, title = { Text("提示") }, text = { Text(it) }, confirmButton = { TextButton(onClick = { message = null }) { Text("好") } }) }
}

@Composable
private fun TripHeader(trip: Trip, onNavigate: () -> Unit) {
    Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Ink)) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Ink, Lake))).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(trip.destination, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("${trip.startDate.chineseDateText()} — ${trip.endDate.chineseDateText()} · ${trip.days.size} 天", color = androidx.compose.ui.graphics.Color.White.copy(alpha = .8f))
            if (trip.note.isNotBlank()) Text(trip.note, color = androidx.compose.ui.graphics.Color.White.copy(alpha = .82f))
            Button(onClick = onNavigate, enabled = trip.nextUnfinishedItem != null, colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Ink)) {
                Icon(Icons.Default.Navigation, null); Spacer(Modifier.width(6.dp)); Text(trip.nextUnfinishedItem?.let { "下一个地点：${it.title}" } ?: "全部完成")
            }
        }
    }
}

@Composable
private fun DaySection(day: TripDay, add: () -> Unit, textImport: () -> Unit, screenshotImport: () -> Unit, edit: (ItineraryItem) -> Unit,
                       toggle: (ItineraryItem) -> Unit, navigate: (ItineraryItem) -> Unit, delete: (ItineraryItem) -> Unit) {
    var expanded by rememberSaveable(day.id) { mutableStateOf(!day.items.all { it.isCompleted }) }
    ElevatedCard(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(day.title.ifBlank { "第 ${day.sortOrder + 1} 天" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${day.date.chineseDateText()} · ${day.items.size} 项", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            if (expanded) {
                day.items.sortedWith(compareBy<ItineraryItem> { it.isCompleted }.thenBy { it.sortOrder }).forEach { item ->
                    ItemCard(item, edit = { edit(item) }, toggle = { toggle(item) }, navigate = { navigate(item) }, delete = { delete(item) })
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = add) { Icon(Icons.Default.Add, null); Text("添加安排") }
                    OutlinedButton(onClick = textImport) { Icon(Icons.Default.TextFields, null); Text("输入文本") }
                    OutlinedButton(onClick = screenshotImport) { Icon(Icons.Default.DocumentScanner, null); Text("选择截图") }
                }
            }
        }
    }
}

@Composable
private fun ItemCard(item: ItineraryItem, edit: () -> Unit, toggle: () -> Unit, navigate: () -> Unit, delete: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .42f)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Checkbox(item.isCompleted, onCheckedChange = { toggle() })
                Column(Modifier.weight(1f).clickable(onClick = edit)) {
                    Text(item.title.ifBlank { "未命名安排" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("${item.startTime.timeText()} — ${item.endTime.timeText()} · ${item.category.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (item.address.isNotBlank()) Text(item.address, style = MaterialTheme.typography.bodySmall)
                    if (item.cost > 0) Text("¥${String.format(Locale.CHINA, "%.2f", item.cost)}", color = Lake, style = MaterialTheme.typography.labelLarge)
                }
                IconButton(onClick = navigate) { Icon(Icons.Default.Navigation, "高德导航") }
                IconButton(onClick = delete) { Icon(Icons.Default.DeleteOutline, "删除") }
            }
            if (item.media.isNotEmpty()) MediaStrip(item.media)
        }
    }
}

@Composable
private fun ItemEditorDialog(repository: TripRepository, day: TripDay, original: ItineraryItem, onDismiss: () -> Unit, save: (ItineraryItem) -> Unit) {
    var item by remember(original.id) { mutableStateOf(original) }
    var title by remember(original.id) { mutableStateOf(item.title) }; var address by remember { mutableStateOf(item.address) }; var note by remember { mutableStateOf(item.note) }
    var start by remember { mutableStateOf(item.startTime.timeText()) }; var end by remember { mutableStateOf(item.endTime.timeText()) }
    var distance by remember { mutableStateOf(item.distanceText) }; var reservation by remember { mutableStateOf(item.reservationInfo) }; var cost by remember { mutableStateOf(if (item.cost == 0.0) "" else item.cost.toString()) }
    var category by remember { mutableStateOf(item.category) }; var transport by remember { mutableStateOf(item.transport) }; var media by remember { mutableStateOf(item.media) }
    val context = LocalContext.current
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris ->
        val remaining = (9 - media.size).coerceAtLeast(0)
        val imported = uris.take(remaining).mapNotNull { uri -> runCatching {
            repository.importMedia(uri, if (context.contentResolver.getType(uri)?.startsWith("video") == true) MediaKind.VIDEO else MediaKind.IMAGE)
        }.getOrNull() }
        media = media + imported.mapIndexed { index, ref -> ref.copy(sortOrder = media.size + index) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original.title.isBlank()) "添加安排" else "编辑安排") },
        text = { Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth())
            Text("类别", style = MaterialTheme.typography.labelMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { PlaceCategory.entries.forEach { FilterChip(category == it, { category = it }, { Text(it.label) }) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(start, { start = it }, label = { Text("开始") }, modifier = Modifier.weight(1f))
                OutlinedTextField(end, { end = it }, label = { Text("结束") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(address, { address = it }, label = { Text("地址") }, modifier = Modifier.fillMaxWidth())
            Text("前往方式", style = MaterialTheme.typography.labelMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { TransportMode.entries.forEach { FilterChip(transport == it, { transport = it }, { Text(it.label) }) } }
            OutlinedTextField(distance, { distance = it }, label = { Text("路程（例：4.4 公里 · 11 分钟）") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(reservation, { reservation = it }, label = { Text("预约信息") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            OutlinedTextField(cost, { cost = it }, label = { Text("花费") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(note, { note = it }, label = { Text("笔记") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            if (media.isNotEmpty()) MediaStrip(media, onDelete = { id -> media = media.filterNot { it.id == id } })
            OutlinedButton(onClick = { mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }, enabled = media.size < 9, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.AddPhotoAlternate, null); Spacer(Modifier.width(6.dp)); Text("添加照片或视频（${media.size}/9）")
            }
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = { Button(onClick = {
            val startMs = combineDateAndTime(day.date, start)!!; val endMs = combineDateAndTime(day.date, end)!!
            save(item.copy(title = title.trim(), category = category, startTime = startMs, endTime = endMs, address = address.trim(), note = note.trim(), transport = transport,
                distanceText = distance.trim(), playDurationMinutes = ((endMs - startMs) / 60_000).toInt().coerceAtLeast(1), reservationInfo = reservation.trim(), cost = cost.toDoubleOrNull() ?: 0.0, media = media))
        }, enabled = title.isNotBlank() && combineDateAndTime(day.date, start) != null && combineDateAndTime(day.date, end) != null) { Text("保存") } }
    )
}

@Composable
fun MediaStrip(media: List<MediaReference>, onDelete: ((String) -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        media.sortedBy { it.sortOrder }.forEach { ref -> Box {
            MediaThumbnail(ref, Modifier.size(84.dp))
            if (onDelete != null) IconButton(onClick = { onDelete(ref.id) }, modifier = Modifier.align(Alignment.TopEnd).size(28.dp)) { Icon(Icons.Default.Cancel, "移除", tint = MaterialTheme.colorScheme.error) }
        } }
    }
}

@Composable
fun MediaThumbnail(media: MediaReference, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(media.localUri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(media.localUri) { bitmap = withContext(Dispatchers.IO) {
        runCatching { val uri = Uri.parse(media.localUri); if (uri.scheme == "file") BitmapFactory.decodeFile(uri.path) else context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream) }.getOrNull()
    } }
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Box(contentAlignment = Alignment.Center) {
            bitmap?.let { androidx.compose.foundation.Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            if (media.kind == MediaKind.VIDEO || bitmap == null) Icon(if (media.kind == MediaKind.VIDEO) Icons.Default.PlayCircle else Icons.Default.Image, null)
        }
    }
}

@Composable
private fun TextImportDialog(onDismiss: () -> Unit, parse: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("输入行程文本") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("粘贴酒店订单、导航、票务或备忘文本。识别后会进入可编辑表单，不会直接保存。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp), label = { Text("原始文本") })
        } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = { Button(onClick = { parse(text) }, enabled = text.isNotBlank()) { Text("识别并编辑") } })
}

@Composable
fun ConfirmDeleteDialog(title: String, message: String, onDismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = { Button(onClick = confirm, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("确认删除") } })
}
