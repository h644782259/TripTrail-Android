@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.personal.triptrail.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.triptrail.data.*
import com.personal.triptrail.util.ExternalApps
import com.personal.triptrail.util.TripFileService
import java.time.ZoneId

@Composable
fun StoriesScreen(repository: TripRepository, stories: List<TravelStory>, trips: List<Trip>, modifier: Modifier = Modifier, onOpen: (String) -> Unit) {
    var creating by remember { mutableStateOf(false) }
    var archiving by remember { mutableStateOf(false) }
    Scaffold(modifier = modifier, topBar = { TopAppBar(title = { Text("足迹") }, actions = {
        IconButton(onClick = { archiving = true }, enabled = trips.isNotEmpty()) { Icon(Icons.Default.BookmarkAdd, "从旅程收录") }
    }) }, floatingActionButton = { FloatingActionButton(onClick = { creating = true }) { Icon(Icons.Default.Add, "新建足迹") } }) { padding ->
        if (stories.isEmpty()) Box(Modifier.fillMaxSize().padding(padding).padding(28.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.AutoStories, null, Modifier.size(54.dp), tint = Lake)
                Text("还没有旅行足迹", style = MaterialTheme.typography.headlineSmall)
                Text("可以独立创建，也可以把已有旅程收进足迹。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { if (trips.isEmpty()) creating = true else archiving = true }) { Text(if (trips.isEmpty()) "新建足迹" else "从旅程收录") }
            }
        } else LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(stories.sortedByDescending { it.startDate }, key = { it.id }) { story ->
                ElevatedCard(onClick = { onOpen(story.id) }, shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(story.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ChevronRight, null)
                        }
                        Text("${story.destination} · ${story.startDate.chineseDateText()} — ${story.endDate.chineseDateText()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (story.summary.isNotBlank()) Text(story.summary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${story.days.size} 天 · ${story.days.sumOf { it.entries.size }} 个地点", style = MaterialTheme.typography.labelMedium, color = Lake)
                    }
                }
            }
        }
    }
    if (creating) NewStoryDialog(onDismiss = { creating = false }) { title, destination, start, end, summary ->
        val story = repository.createStory(title, destination, start, end, summary); creating = false; onOpen(story.id)
    }
    if (archiving) AlertDialog(onDismissRequest = { archiving = false }, title = { Text("把旅程收进足迹") },
        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) { trips.timelineSorted().forEach { trip ->
            ListItem(headlineContent = { Text(trip.title) }, supportingContent = { Text("${trip.destination} · ${trip.days.size} 天") },
                leadingContent = { Icon(Icons.Default.Map, null) }, modifier = Modifier.clickable {
                    repository.archiveTrip(trip.id)?.let { onOpen(it.id) }; archiving = false
                })
        } } }, confirmButton = { TextButton(onClick = { archiving = false }) { Text("取消") } })
}

@Composable
private fun NewStoryDialog(onDismiss: () -> Unit, save: (String, String, Long, Long, String) -> Unit) {
    var title by remember { mutableStateOf("") }; var destination by remember { mutableStateOf("") }
    var start by remember { mutableStateOf(System.currentTimeMillis().dateText()) }; var end by remember { mutableStateOf(System.currentTimeMillis().dateText()) }
    var summary by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("新建足迹") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("足迹名称") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(destination, { destination = it }, label = { Text("目的地") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(start, { start = it }, label = { Text("开始日期（yyyy-MM-dd）") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(end, { end = it }, label = { Text("结束日期（yyyy-MM-dd）") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(summary, { summary = it }, label = { Text("旅行小记") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = { Button(onClick = { save(title.trim(), destination.trim(), parseDate(start)!!, parseDate(end)!!, summary.trim()) },
            enabled = title.isNotBlank() && destination.isNotBlank() && parseDate(start) != null && parseDate(end) != null) { Text("创建") } })
}

@Composable
fun StoryDetailScreen(repository: TripRepository, storyId: String, onBack: () -> Unit) {
    val data by repository.data.collectAsStateWithLifecycle(); val story = data.stories.firstOrNull { it.id == storyId }
    if (story == null) { LaunchedEffect(Unit) { onBack() }; return }
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Pair<StoryDay, StoryEntry>?>(null) }
    var deleteEntry by remember { mutableStateOf<Pair<StoryDay, StoryEntry>?>(null) }
    var deleteStory by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var exportText by remember { mutableStateOf<String?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.triptrail.journey")) { uri ->
        if (uri != null) runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(exportText.orEmpty()) } }
            .onSuccess { message = "分享文件已导出。" }.onFailure { message = "导出失败：${it.localizedMessage}" }
        exportText = null
    }
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing, topBar = { TopAppBar(
        title = { Text(story.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } },
        actions = {
            if (story.sourceTripId != null) IconButton(onClick = { repository.archiveTrip(story.sourceTripId); message = "已同步源旅程的最新层级，不会覆盖足迹内容。" }) { Icon(Icons.Default.Sync, "同步") }
            IconButton(onClick = { exportText = TripFileService.shareStory(story); exporter.launch("${story.title}.triptrail") }) { Icon(Icons.Default.Share, "导出") }
            IconButton(onClick = { deleteStory = true }) { Icon(Icons.Default.Delete, "删除") }
        }
    ) }) { padding -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Ink)) { Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(story.destination, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("${story.startDate.chineseDateText()} — ${story.endDate.chineseDateText()}", color = androidx.compose.ui.graphics.Color.White.copy(alpha = .72f))
            if (story.summary.isNotBlank()) Text(story.summary, color = androidx.compose.ui.graphics.Color.White.copy(alpha = .88f))
        } } }
        items(story.days.sortedBy { it.sortOrder }, key = { it.id }) { day ->
            StoryDayCard(day, add = { editing = day to StoryEntry() }, edit = { editing = day to it }, delete = { deleteEntry = day to it }, action = { platform, entry ->
                message = when (platform) { "高德地图" -> if (ExternalApps.openAmap(context, entry)) null else "未检测到高德地图 App。"; else -> if (ExternalApps.openDiscovery(context, platform, entry.title, entry.address)) null else "暂时无法打开$platform。" }
            })
        }
        item { OutlinedButton(onClick = {
            val last = story.days.maxByOrNull { it.sortOrder }; val date = (last?.date ?: story.endDate).localDate().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            repository.updateStory(story.copy(endDate = maxOf(story.endDate, date), days = story.days + StoryDay(date = date, title = "第 ${story.days.size + 1} 天", sortOrder = story.days.size)))
        }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text("继续添加一天") } }
    } }
    editing?.let { (day, entry) -> StoryEntryEditor(repository, entry, onDismiss = { editing = null }) { repository.saveStoryEntry(story.id, day.id, it); editing = null } }
    deleteEntry?.let { (day, entry) -> ConfirmDeleteDialog("删除足迹安排？", "“${entry.title}”及其中的媒体引用将从当前足迹中删除，手机中的原文件不会受到影响。", { deleteEntry = null }) {
        repository.deleteStoryEntry(story.id, day.id, entry.id); deleteEntry = null
    } }
    if (deleteStory) ConfirmDeleteDialog("删除足迹？", "“${story.title}”及其中的当天足迹、具体安排和媒体引用将被永久删除，原旅程不会受到影响。", { deleteStory = false }) {
        repository.deleteStory(story.id); deleteStory = false; onBack()
    }
    message?.let { AlertDialog(onDismissRequest = { message = null }, title = { Text("提示") }, text = { Text(it) }, confirmButton = { TextButton(onClick = { message = null }) { Text("好") } }) }
}

@Composable
private fun StoryDayCard(day: StoryDay, add: () -> Unit, edit: (StoryEntry) -> Unit, delete: (StoryEntry) -> Unit, action: (String, StoryEntry) -> Unit) {
    ElevatedCard(shape = RoundedCornerShape(24.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(day.title.ifBlank { "第 ${day.sortOrder + 1} 天" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(day.date.chineseDateText(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (day.note.isNotBlank()) Text(day.note)
        day.entries.sortedBy { it.sortOrder }.forEach { entry ->
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .42f)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { edit(entry) }) {
                            Text(entry.title.ifBlank { "未命名地点" }, fontWeight = FontWeight.SemiBold)
                            if (entry.timeLabel.isNotBlank()) Text(entry.timeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        var menu by remember { mutableStateOf(false) }
                        Box { IconButton(onClick = { menu = true }) { Icon(Icons.Default.Place, "地点选项") }; DropdownMenu(menu, { menu = false }) {
                            listOf("高德地图", "小红书", "抖音").forEach { platform -> DropdownMenuItem({ Text(platform) }, onClick = { menu = false; action(platform, entry) }) }
                        } }
                        IconButton(onClick = { delete(entry) }) { Icon(Icons.Default.DeleteOutline, "删除") }
                    }
                    if (entry.note.isNotBlank()) Text(entry.note)
                    if (entry.media.isNotEmpty()) MediaStrip(entry.media)
                }
            }
        }
        FilledTonalButton(onClick = add, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text("添加足迹安排") }
    } }
}

@Composable
private fun StoryEntryEditor(repository: TripRepository, original: StoryEntry, onDismiss: () -> Unit, save: (StoryEntry) -> Unit) {
    var title by remember { mutableStateOf(original.title) }; var address by remember { mutableStateOf(original.address) }; var note by remember { mutableStateOf(original.note) }
    var route by remember { mutableStateOf(original.routeInfo) }; var cost by remember { mutableStateOf(if (original.cost == 0.0) "" else original.cost.toString()) }
    var category by remember { mutableStateOf(original.category) }; var media by remember { mutableStateOf(original.media) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris ->
        val imported = uris.take((9 - media.size).coerceAtLeast(0)).mapNotNull { uri -> runCatching { repository.importMedia(uri, if (context.contentResolver.getType(uri)?.startsWith("video") == true) MediaKind.VIDEO else MediaKind.IMAGE) }.getOrNull() }
        media = media + imported.mapIndexed { i, ref -> ref.copy(sortOrder = media.size + i) }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (original.title.isBlank()) "添加足迹安排" else "编辑足迹安排") },
        text = { Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("地点/安排名称") }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { PlaceCategory.entries.forEach { FilterChip(category == it, { category = it }, { Text(it.label) }) } }
            OutlinedTextField(address, { address = it }, label = { Text("地址") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(route, { route = it }, label = { Text("实际路线") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(cost, { cost = it }, label = { Text("实际花费") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(note, { note = it }, label = { Text("当时的感受与记录") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
            if (media.isNotEmpty()) MediaStrip(media) { id -> media = media.filterNot { it.id == id } }
            OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }, enabled = media.size < 9, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.AddPhotoAlternate, null); Text("添加照片或视频（${media.size}/9）")
            }
        } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = { Button(onClick = { save(original.copy(title = title.trim(), category = category, address = address.trim(), routeInfo = route.trim(), note = note.trim(), cost = cost.toDoubleOrNull() ?: 0.0, media = media)) }, enabled = title.isNotBlank()) { Text("保存") } })
}
