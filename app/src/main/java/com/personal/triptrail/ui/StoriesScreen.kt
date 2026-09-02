@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.personal.triptrail.ui

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.triptrail.data.*
import com.personal.triptrail.util.ExternalApps
import com.personal.triptrail.util.TripFileService
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun StoriesScreen(repository: TripRepository, stories: List<TravelStory>, trips: List<Trip>, modifier: Modifier = Modifier, onOpen: (String) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var selectedYear by rememberSaveable { mutableStateOf<Int?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TravelStory?>(null) }
    var exportText by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.triptrail.journey")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(exportText.orEmpty()) }
                ?: error("无法创建分享文件")
        }.onSuccess { message = "分享文件已导出。" }.onFailure { message = "导出失败：${it.localizedMessage}" }
        exportText = null
    }
    val years = remember(stories) { stories.map { it.startDate.localDate().year }.distinct().sortedDescending() }
    val filtered = remember(stories, search, selectedYear) { stories.filter { story ->
        (selectedYear == null || story.startDate.localDate().year == selectedYear) &&
            (search.isBlank() || listOf(story.title, story.destination, story.summary).any { it.contains(search.trim(), true) } || story.days.flatMap { it.entries }.any { it.title.contains(search.trim(), true) })
    }.sortedByDescending { it.startDate } }
    val grouped = filtered.groupBy { it.startDate.localDate().year }.toSortedMap(compareByDescending { it })

    Box(modifier.fillMaxSize().background(TripCanvas)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                var yearMenu by remember { mutableStateOf(false) }
                Box { TripRoundAction(Icons.Default.FilterList, "筛选年份") { yearMenu = true }; DropdownMenu(yearMenu, { yearMenu = false }) {
                    DropdownMenuItem({ Text("全部年份") }, { selectedYear = null; yearMenu = false }, leadingIcon = { if (selectedYear == null) Icon(Icons.Default.Check, null) })
                    years.forEach { year -> DropdownMenuItem({ Text("${year} 年") }, { selectedYear = year; yearMenu = false }, leadingIcon = { if (selectedYear == year) Icon(Icons.Default.Check, null) }) }
                } }
                Spacer(Modifier.width(10.dp)); TripRoundAction(Icons.Default.Add, "新建足迹") { creating = true }
            }
            if (stories.isNotEmpty()) TripSearchField(search, "搜索名称、城市、地点或摘要", { search = it }, Modifier.padding(horizontal = 16.dp))
            if (stories.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.MenuBook, null, Modifier.size(56.dp), tint = TripLakeText); Spacer(Modifier.height(14.dp)); Text("足迹还空着", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(7.dp)); Text("新建足迹，或从旅程中收录。", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(18.dp)); Button(onClick = { creating = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("新建足迹") }
                }
            } else if (filtered.isEmpty()) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(Icons.Default.SearchOff, null, Modifier.size(48.dp), tint = TripLakeText); Text("没有找到足迹", style = MaterialTheme.typography.titleLarge); TextButton(onClick = { search = ""; selectedYear = null }) { Text("清除条件") } }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 112.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    grouped.forEach { (year, entries) ->
                        item(key = "year-$year") { Row(verticalAlignment = Alignment.CenterVertically) { Text("${year} 年", style = MaterialTheme.typography.titleMedium, color = TripInk); Spacer(Modifier.width(8.dp)); Surface(shape = RoundedCornerShape(14.dp), color = TripLake.copy(alpha = .12f)) { Text("${entries.size} 段", Modifier.padding(horizontal = 9.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = TripLakeText) } } }
                        items(entries, key = { it.id }) { story ->
                            StoryCard(
                                story = story,
                                onOpen = { onOpen(story.id) },
                                onShare = { exportText = TripFileService.shareStory(story); exporter.launch("${story.title}.triptrail") },
                                onDelete = { deleting = story },
                            )
                        }
                    }
                }
            }
        }
    }
    if (creating) StoryEditorDialog(null, { creating = false }) { title, destination, start, end, summary -> val story = repository.createStory(title, destination, start, end, summary); creating = false; onOpen(story.id) }
    deleting?.let { story -> ConfirmDeleteDialog("删除足迹？", "“${story.title}”以及其中的日期、记录和媒体引用都会删除。", { deleting = null }) { repository.deleteStory(story.id); deleting = null } }
    message?.let { AlertDialog(onDismissRequest = { message = null }, title = { Text("提示") }, text = { Text(it) }, confirmButton = { TextButton(onClick = { message = null }) { Text("好") } }) }
}

@Composable
private fun StoryCard(story: TravelStory, onOpen: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = TripSurface, border = androidx.compose.foundation.BorderStroke(.8.dp, TripMist.copy(alpha = .34f)), shadowElevation = 5.dp) {
        Row(Modifier.clickable(onClick = onOpen).padding(14.dp), verticalAlignment = Alignment.Top) {
            val preview = story.coverMedia ?: story.days.flatMap { it.entries }.flatMap { it.media }.firstOrNull()
            if (preview != null) MediaThumbnail(preview, Modifier.size(112.dp)) else Box(Modifier.size(112.dp).clip(RoundedCornerShape(16.dp)).background(TripItemSurface), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.PhotoLibrary, null, tint = Color.Gray); Text("暂无图片", style = MaterialTheme.typography.bodySmall, color = Color.Gray) } }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).heightIn(min = 112.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(story.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (story.destination.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.PinDrop, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(5.dp)); Text(story.destination, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (story.summary.isNotBlank()) Text(story.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f)); Text("${story.startDate.chineseDateText()} — ${story.endDate.chineseDateText()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${story.days.size} 天 · ${story.days.sumOf { it.entries.size }} 个记录", style = MaterialTheme.typography.labelMedium, color = TripLakeText)
            }
            Box { IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreHoriz, "更多操作", tint = TripInk.copy(alpha = .72f)) }; DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text("查看足迹") }, { menu = false; onOpen() }, leadingIcon = { Icon(Icons.Default.Edit, null) }); DropdownMenuItem({ Text("分享足迹") }, { menu = false; onShare() }, leadingIcon = { Icon(Icons.Default.Share, null) }); HorizontalDivider(); DropdownMenuItem({ Text("删除足迹") }, { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null) })
            } }
        }
    }
}

@Composable
private fun StoryEditorDialog(original: TravelStory?, onDismiss: () -> Unit, save: (String, String, Long, Long, String) -> Unit) {
    var title by remember(original?.id) { mutableStateOf(original?.title.orEmpty()) }; var destination by remember(original?.id) { mutableStateOf(original?.destination.orEmpty()) }; var start by remember(original?.id) { mutableStateOf(original?.startDate?.dateText() ?: System.currentTimeMillis().dateText()) }; var end by remember(original?.id) { mutableStateOf(original?.endDate?.dateText() ?: System.currentTimeMillis().dateText()) }; var summary by remember(original?.id) { mutableStateOf(original?.summary.orEmpty()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (original == null) "新建足迹" else "编辑足迹") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(title, { title = it }, label = { Text("足迹名称") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(destination, { destination = it }, label = { Text("城市/目的地") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(start, { start = it }, label = { Text("开始日期") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(end, { end = it }, label = { Text("结束日期") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(summary, { summary = it }, label = { Text("摘要") }, minLines = 3, modifier = Modifier.fillMaxWidth()) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }, confirmButton = { Button(onClick = { save(title.trim(), destination.trim(), parseDate(start)!!, parseDate(end)!!, summary.trim()) }, enabled = title.isNotBlank() && parseDate(start) != null && parseDate(end) != null) { Text(if (original == null) "创建" else "保存") } })
}

@Composable
fun StoryDetailScreen(repository: TripRepository, storyId: String, modifier: Modifier = Modifier, onBack: () -> Unit) {
    val data by repository.data.collectAsStateWithLifecycle(); val story = data.stories.firstOrNull { it.id == storyId }
    if (story == null) { LaunchedEffect(Unit) { onBack() }; return }
    var editing by remember { mutableStateOf<Pair<StoryDay, StoryEntry>?>(null) }
    var deleting by remember { mutableStateOf<Pair<StoryDay, StoryEntry>?>(null) }
    var editingStory by remember { mutableStateOf(false) }
    var deletingStory by remember { mutableStateOf(false) }
    var editingDay by remember { mutableStateOf<StoryDay?>(null) }
    var deletingDay by remember { mutableStateOf<StoryDay?>(null) }
    var actionMenu by remember { mutableStateOf(false) }
    var coverMenu by remember { mutableStateOf(false) }
    var exportText by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.triptrail.journey")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(exportText.orEmpty()) }
                ?: error("无法创建分享文件")
        }.onSuccess { message = "分享文件已导出。" }.onFailure { message = "导出失败：${it.localizedMessage}" }
        exportText = null
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) runCatching { repository.importMedia(uri, MediaKind.IMAGE) }
            .onSuccess { repository.updateStory(story.copy(coverMedia = it, coverZoom = 1.0, coverOffsetX = 0.0, coverOffsetY = 0.0)) }
            .onFailure { message = "无法读取封面图片：${it.localizedMessage}" }
    }
    Box(modifier.fillMaxSize().background(TripCanvas)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TripRoundAction(Icons.Default.ArrowBack, "返回", onBack)
                Text(story.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
                Box {
                    TripRoundAction(Icons.Default.MoreHoriz, "足迹操作") { actionMenu = true }
                    DropdownMenu(actionMenu, { actionMenu = false }) {
                        DropdownMenuItem({ Text("编辑足迹") }, { actionMenu = false; editingStory = true }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                        story.sourceTripId?.let { sourceId ->
                            DropdownMenuItem({ Text("同步最新旅程") }, {
                                actionMenu = false
                                message = if (data.trips.any { it.id == sourceId } && repository.archiveTrip(sourceId) != null) "已同步最新旅程。" else "原旅程已不存在，无法同步。"
                            }, leadingIcon = { Icon(Icons.Default.Sync, null) })
                        }
                        DropdownMenuItem({ Text("分享足迹") }, { actionMenu = false; exportText = TripFileService.shareStory(story); exporter.launch("${story.title}.triptrail") }, leadingIcon = { Icon(Icons.Default.Share, null) })
                        HorizontalDivider()
                        DropdownMenuItem({ Text("删除足迹", color = MaterialTheme.colorScheme.error) }, { actionMenu = false; deletingStory = true }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) })
                    }
                }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 112.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Box {
                        StoryCover(story) { coverMenu = true }
                        DropdownMenu(coverMenu, { coverMenu = false }) {
                            DropdownMenuItem({ Text(if (story.coverMedia == null) "选择封面" else "更换封面") }, { coverMenu = false; coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, leadingIcon = { Icon(Icons.Default.PhotoLibrary, null) })
                            if (story.coverMedia != null) DropdownMenuItem({ Text("移除封面") }, { coverMenu = false; repository.updateStory(story.copy(coverMedia = null, coverZoom = 1.0, coverOffsetX = 0.0, coverOffsetY = 0.0)) }, leadingIcon = { Icon(Icons.Default.HideImage, null) })
                        }
                    }
                }
                items(story.days.sortedBy { it.sortOrder }, key = { it.id }) { day ->
                    var expanded by rememberSaveable(day.id) { mutableStateOf(true) }
                    TripSectionSurface {
                        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(day.title.ifBlank { "第 ${day.sortOrder + 1} 天" }, style = MaterialTheme.typography.titleLarge)
                                Text(day.date.chineseDateText(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("${day.entries.size} 个记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
                            Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, if (expanded) "收起" else "展开", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            var dayMenu by remember(day.id) { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { dayMenu = true }) { Icon(Icons.Default.MoreHoriz, "当天更多操作") }
                                DropdownMenu(dayMenu, { dayMenu = false }) {
                                    DropdownMenuItem({ Text("编辑当天") }, { dayMenu = false; editingDay = day }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                                    DropdownMenuItem({ Text("添加记录") }, { dayMenu = false; editing = day to StoryEntry(startTime = day.date, endTime = day.date) }, leadingIcon = { Icon(Icons.Default.Add, null) })
                                    DropdownMenuItem({ Text("分享当天") }, { dayMenu = false; exportText = TripFileService.shareStoryDay(story, day); exporter.launch("${story.title}-${day.title.ifBlank { day.date.dateText() }}.triptrail") }, leadingIcon = { Icon(Icons.Default.Share, null) })
                                    HorizontalDivider()
                                    DropdownMenuItem({ Text("删除当天", color = MaterialTheme.colorScheme.error) }, { dayMenu = false; deletingDay = day }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) })
                                }
                            }
                        }
                        if (expanded) {
                            if (day.note.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(day.note, style = MaterialTheme.typography.bodyMedium) }
                            Spacer(Modifier.height(12.dp)); day.entries.sortedBy { it.sortOrder }.forEach { entry -> StoryEntryCard(entry, { editing = day to entry }, { ExternalApps.openAmap(context, entry) }, { deleting = day to entry }); Spacer(Modifier.height(10.dp)) }
                            TextButton(onClick = { editing = day to StoryEntry(startTime = day.date, endTime = day.date) }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("添加记录") }
                        }
                    }
                }
                item { TextButton(onClick = { repository.addStoryDay(story.id) }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("继续添加一天", fontWeight = FontWeight.Bold) } }
            }
        }
    }
    if (editingStory) StoryEditorDialog(story, { editingStory = false }) { title, destination, start, end, summary -> repository.updateStory(story.copy(title = title, destination = destination, startDate = start, endDate = end, summary = summary)); editingStory = false }
    editing?.let { (day, entry) -> StoryEntryEditor(repository, entry, { editing = null }) { repository.saveStoryEntry(story.id, day.id, it); editing = null } }
    editingDay?.let { day -> StoryDayEditorDialog(day, { editingDay = null }) { repository.updateStoryDay(story.id, it); editingDay = null } }
    deleting?.let { (day, entry) -> ConfirmDeleteDialog("删除记录？", "“${entry.title}”将从足迹中删除。", { deleting = null }) { repository.deleteStoryEntry(story.id, day.id, entry.id); deleting = null } }
    deletingDay?.let { day -> ConfirmDeleteDialog("删除当天？", "“${day.title.ifBlank { day.date.chineseDateText() }}”以及其中的记录都会删除。", { deletingDay = null }) { repository.deleteStoryDay(story.id, day.id); deletingDay = null } }
    if (deletingStory) ConfirmDeleteDialog("删除足迹？", "“${story.title}”以及其中的日期、记录和媒体引用都会删除。", { deletingStory = false }) { repository.deleteStory(story.id); deletingStory = false; onBack() }
    message?.let { AlertDialog(onDismissRequest = { message = null }, title = { Text("提示") }, text = { Text(it) }, confirmButton = { TextButton(onClick = { message = null }) { Text("好") } }) }
}

@Composable
private fun StoryCover(story: TravelStory, onCoverAction: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 200.dp).clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(TripInk, TripLake))).clickable(onClick = onCoverAction)) {
        story.coverMedia?.let { MediaThumbnail(it, Modifier.matchParentSize()) }
        if (story.coverMedia != null) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .34f)))
        Column(Modifier.align(Alignment.BottomStart).padding(24.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            if (story.destination.isNotBlank()) Text(story.destination.uppercase(), style = MaterialTheme.typography.labelMedium, color = TripSand)
            Text(story.title, style = MaterialTheme.typography.headlineLarge, color = Color.White)
            if (story.summary.isNotBlank()) Text(story.summary, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .88f))
            Text("${story.startDate.chineseDateText()} — ${story.endDate.chineseDateText()}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .72f))
        }
    }
}

@Composable
private fun StoryDayEditorDialog(day: StoryDay, onDismiss: () -> Unit, save: (StoryDay) -> Unit) {
    var title by remember(day.id) { mutableStateOf(day.title) }
    var date by remember(day.id) { mutableStateOf(day.date.dateText()) }
    var note by remember(day.id) { mutableStateOf(day.note) }
    var details by remember(day.id) { mutableStateOf(day.details) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑足迹日") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("当天标题") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(date, { date = it }, label = { Text("日期（yyyy-MM-dd）") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("当天摘要") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(details, { details = it }, label = { Text("细节补充") }, minLines = 4, modifier = Modifier.fillMaxWidth())
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = { Button(onClick = { save(day.copy(title = title.trim(), date = parseDate(date)!!, note = note.trim(), details = details.trim())) }, enabled = parseDate(date) != null) { Text("保存") } },
    )
}

@Composable
private fun StoryEntryCard(entry: StoryEntry, onEdit: () -> Unit, onNavigate: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(onClick = onEdit, shape = RoundedCornerShape(18.dp), color = TripItemSurface, border = androidx.compose.foundation.BorderStroke(.8.dp, TripMist.copy(alpha = .34f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(entry.category.icon(), null, tint = TripLakeText, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(7.dp))
                Text(entry.title.ifBlank { "未命名记录" }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (entry.timeLabel.isNotBlank()) Text(entry.timeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box {
                    IconButton(onClick = { menu = true }, Modifier.size(34.dp)) { Icon(Icons.Default.MoreVert, "记录操作", Modifier.size(18.dp)) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("编辑记录") }, { menu = false; onEdit() }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                        DropdownMenuItem({ Text("删除记录") }, { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null) })
                    }
                }
            }
            entry.locationTargets.forEach { target -> TextButton(onClick = onNavigate, contentPadding = PaddingValues(0.dp)) { Icon(if (target.role == JourneyLocationRole.ORIGIN) Icons.Default.MyLocation else Icons.Default.PinDrop, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("${target.role.label}：${target.displayName}") } }
            if (entry.media.isNotEmpty()) MediaStrip(entry.media)
            if (entry.note.isNotBlank()) { Text("回忆", style = MaterialTheme.typography.labelMedium, color = TripLakeText); Text(entry.note, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun StoryEntryEditor(repository: TripRepository, original: StoryEntry, onDismiss: () -> Unit, save: (StoryEntry) -> Unit) {
    val baseDay = original.startTime ?: original.endTime ?: System.currentTimeMillis()
    var title by remember(original.id) { mutableStateOf(original.title) }
    var category by remember(original.id) { mutableStateOf(original.category) }
    var mode by remember(original.id) { mutableStateOf(original.locationMode) }
    var place by remember(original.id) { mutableStateOf(original.placeName.ifBlank { original.address }) }
    var address by remember(original.id) { mutableStateOf(original.placeAddress.ifBlank { original.address }) }
    var origin by remember(original.id) { mutableStateOf(original.originName) }
    var originAddress by remember(original.id) { mutableStateOf(original.originAddress) }
    var destination by remember(original.id) { mutableStateOf(original.destinationName) }
    var destinationAddress by remember(original.id) { mutableStateOf(original.destinationAddress) }
    var hasTime by remember(original.id) { mutableStateOf(original.startTime != null || original.timeLabel.isNotBlank()) }
    var start by remember(original.id) { mutableStateOf(original.startTime?.timeText() ?: "09:00") }
    var end by remember(original.id) { mutableStateOf(original.endTime?.timeText() ?: "10:00") }
    var note by remember(original.id) { mutableStateOf(original.note) }
    var route by remember(original.id) { mutableStateOf(original.routeInfo) }
    var cost by remember(original.id) { mutableStateOf(if (original.cost == 0.0) "" else original.cost.toString()) }
    var media by remember(original.id) { mutableStateOf(original.media) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris ->
        media = media + uris.take((9 - media.size).coerceAtLeast(0)).mapNotNull { uri ->
            runCatching { repository.importMedia(uri, if (context.contentResolver.getType(uri)?.startsWith("video") == true) MediaKind.VIDEO else MediaKind.IMAGE) }.getOrNull()
        }
    }
    val validTime = !hasTime || (combineDateAndTime(baseDay, start) != null && combineDateAndTime(baseDay, end) != null)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original.title.isBlank()) "添加记录" else "编辑记录") },
        text = {
            Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("地点/安排名称") }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { PlaceCategory.entries.forEach { item -> FilterChip(category == item, { category = item }, { Text(item.label) }) } }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { ArrangementLocationMode.entries.forEachIndexed { index, item -> SegmentedButton(selected = mode == item, onClick = { mode = item }, shape = SegmentedButtonDefaults.itemShape(index, ArrangementLocationMode.entries.size)) { Text(item.label) } } }
                if (mode == ArrangementLocationMode.SINGLE) {
                    OutlinedTextField(place, { place = it }, label = { Text("地点名称") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(address, { address = it }, label = { Text("详细地址") }, modifier = Modifier.fillMaxWidth())
                } else {
                    OutlinedTextField(origin, { origin = it }, label = { Text("出发地名称") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(originAddress, { originAddress = it }, label = { Text("出发地详细地址") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(destination, { destination = it }, label = { Text("目的地名称") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(destinationAddress, { destinationAddress = it }, label = { Text("目的地详细地址") }, modifier = Modifier.fillMaxWidth())
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Text("记录时间", Modifier.weight(1f)); Switch(hasTime, { hasTime = it }) }
                if (hasTime) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(start, { start = it }, modifier = Modifier.weight(1f), label = { Text("开始") })
                    OutlinedTextField(end, { end = it }, modifier = Modifier.weight(1f), label = { Text("结束") })
                }
                OutlinedTextField(route, { route = it }, label = { Text("实际路线") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(cost, { cost = it }, label = { Text("实际花费") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("当时的感受与记录") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
                if (media.isNotEmpty()) MediaStrip(media) { id -> media = media.filterNot { it.id == id } }
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.AddPhotoAlternate, null); Spacer(Modifier.width(6.dp)); Text("添加照片或视频（${media.size}/9）") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = {
            Button(onClick = {
                val startValue = if (hasTime) combineDateAndTime(baseDay, start) else null
                val endValue = if (hasTime) combineDateAndTime(baseDay, end) else null
                save(original.copy(
                    title = title.trim(), category = category, locationMode = mode,
                    placeName = place.trim(), placeAddress = address.trim(), address = address.trim(),
                    originName = origin.trim(), originAddress = originAddress.trim(), destinationName = destination.trim(), destinationAddress = destinationAddress.trim(),
                    startTime = startValue, endTime = endValue, timeLabel = if (hasTime && startValue != null && endValue != null) "${startValue.timeText()} – ${endValue.timeText()}" else "",
                    routeInfo = route.trim(), note = note.trim(), cost = cost.toDoubleOrNull() ?: 0.0, media = media,
                ))
            }, enabled = title.isNotBlank() && validTime) { Text("保存") }
        },
    )
}
