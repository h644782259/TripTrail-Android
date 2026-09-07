@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.personal.triptrail.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.triptrail.data.*
import com.personal.triptrail.util.SystemImagePickerContract
import com.personal.triptrail.util.ExternalApps
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun StoriesScreen(repository: TripRepository, stories: List<TravelStory>, trips: List<Trip>, modifier: Modifier = Modifier, onOpen: (String) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var selectedYear by rememberSaveable { mutableStateOf<Int?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TravelStory?>(null) }
    var sharing by remember { mutableStateOf<TravelStory?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val years = remember(stories) { stories.map { it.startDate.localDate().year }.distinct().sortedDescending() }
    val filtered = remember(stories, search, selectedYear) { stories.filter { story ->
        (selectedYear == null || story.startDate.localDate().year == selectedYear) &&
            (search.isBlank() || listOf(story.title, story.destination, story.summary).any { it.contains(search.trim(), true) } || story.days.flatMap { it.entries }.any { it.title.contains(search.trim(), true) })
    }.sortedByDescending { it.startDate } }
    val grouped = filtered.groupBy { it.startDate.localDate().year }.toSortedMap(compareByDescending { it })

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                var yearMenu by remember { mutableStateOf(false) }
                Box { TripRoundAction(Icons.Default.FilterList, "筛选年份") { yearMenu = true }; TripDropdownMenu(yearMenu, { yearMenu = false }) {
                    DropdownMenuItem({ Text("全部年份") }, { selectedYear = null; yearMenu = false }, leadingIcon = { if (selectedYear == null) Icon(Icons.Default.Check, null) })
                    years.forEach { year -> DropdownMenuItem({ Text("${year} 年") }, { selectedYear = year; yearMenu = false }, leadingIcon = { if (selectedYear == year) Icon(Icons.Default.Check, null) }) }
                } }
                Spacer(Modifier.width(10.dp)); TripRoundAction(Icons.Default.Add, "新建足迹") { creating = true }
            }
            if (stories.isNotEmpty()) TripSearchField(search, "搜索名称、城市、地点或摘要", { search = it }, Modifier.padding(horizontal = 16.dp))
            if (stories.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.MenuBook, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(14.dp)); Text("足迹还空着", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(7.dp)); Text("新建足迹，或从旅程中收录。", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(18.dp)); Button(onClick = { creating = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("新建足迹") }
                }
            } else if (filtered.isEmpty()) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(Icons.Default.SearchOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary); Text("没有找到足迹", style = MaterialTheme.typography.titleLarge); TextButton(onClick = { search = ""; selectedYear = null }) { Text("清除条件") } }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 112.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    grouped.forEach { (year, entries) ->
                        item(key = "year-$year") { Row(verticalAlignment = Alignment.CenterVertically) { Text("${year} 年", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface); Spacer(Modifier.width(8.dp)); Surface(shape = RoundedCornerShape(14.dp), color = TripLake.copy(alpha = .12f)) { Text("${entries.size} 段", Modifier.padding(horizontal = 9.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) } } }
                        items(entries, key = { it.id }) { story ->
                            StoryCard(
                                story = story,
                                onOpen = { onOpen(story.id) },
                                onShare = { sharing = story },
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
    sharing?.let { story -> StoryShareDialog(story, onDismiss = { sharing = null }) }
    message?.let { AlertDialog(modifier = androidx.compose.ui.Modifier.dismissKeyboardOnBlankTap(), onDismissRequest = { message = null }, title = { Text("提示") }, text = { Text(it) }, confirmButton = { TextButton(onClick = { message = null }) { Text("好") } }) }
}

@Composable
private fun StoryCard(story: TravelStory, onOpen: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(.8.dp, TripMist.copy(alpha = .34f)), shadowElevation = 1.dp) {
        Row(Modifier.clickable(onClick = onOpen).padding(14.dp), verticalAlignment = Alignment.Top) {
            val preview = story.coverMedia ?: story.days.flatMap { it.entries }.flatMap { it.media }.firstOrNull()
            if (preview != null) MediaThumbnail(preview, Modifier.size(112.dp)) else Box(Modifier.size(112.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.PhotoLibrary, null, tint = Color.Gray); Text("暂无图片", style = MaterialTheme.typography.bodySmall, color = Color.Gray) } }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).heightIn(min = 112.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(story.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (story.destination.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.PinDrop, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(5.dp)); Text(story.destination, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (story.summary.isNotBlank()) Text(story.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f)); Text("${story.startDate.chineseDateText()} — ${story.endDate.chineseDateText()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${story.days.size} 天 · ${story.days.sumOf { it.entries.size }} 个记录", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Box { IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreHoriz, "更多操作", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f)) }; TripDropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text("查看足迹") }, { menu = false; onOpen() }, leadingIcon = { Icon(Icons.Default.Edit, null) }); DropdownMenuItem({ Text("分享足迹") }, { menu = false; onShare() }, leadingIcon = { Icon(Icons.Default.Share, null) }); HorizontalDivider(); DropdownMenuItem({ Text("删除足迹", color = MaterialTheme.colorScheme.error) }, { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) })
            } }
        }
    }
}

@Composable
private fun StoryEditorDialog(original: TravelStory?, onDismiss: () -> Unit, save: (String, String, Long, Long, String) -> Unit) {
    var title by remember(original?.id) { mutableStateOf(original?.title.orEmpty()) }; var destination by remember(original?.id) { mutableStateOf(original?.destination.orEmpty()) }; var start by remember(original?.id) { mutableLongStateOf(original?.startDate ?: System.currentTimeMillis().startOfDay()) }; var end by remember(original?.id) { mutableLongStateOf(original?.endDate ?: System.currentTimeMillis().startOfDay()) }; var summary by remember(original?.id) { mutableStateOf(original?.summary.orEmpty()) }
    AlertDialog(modifier = androidx.compose.ui.Modifier.dismissKeyboardOnBlankTap(), onDismissRequest = onDismiss, shape = RoundedCornerShape(28.dp), containerColor = MaterialTheme.colorScheme.surface, title = { Text(if (original == null) "新建足迹" else "编辑足迹") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) { TripFormField(title, { title = it }, "足迹名称"); TripFormField(destination, { destination = it }, "城市/目的地"); TripDateRangeField(start, end) { selectedStart, selectedEnd -> start = selectedStart; end = maxOf(selectedEnd, selectedStart) }; TripFormField(summary, { summary = it }, "摘要", minLines = 3, singleLine = false) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }, confirmButton = { Button(onClick = { save(title.trim(), destination.trim(), start, end, summary.trim()) }, enabled = title.isNotBlank()) { Text(if (original == null) "创建" else "保存") } })
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
    var showingShare by remember { mutableStateOf(false) }
    var shareDayId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var openTarget by remember { mutableStateOf<JourneyLocationTarget?>(null) }
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val orderedDays = remember(story.days) { story.days.sortedBy { it.sortOrder } }
    var collapsedDays by rememberSaveable(storyId) { mutableStateOf(emptyList<String>()) }
    val dayIndices = remember(orderedDays, collapsedDays) {
        var next = 1
        orderedDays.associate { day ->
            val header = next
            next += if (day.id in collapsedDays) 1 else day.entries.size + 2
            day.id to header
        }
    }
    val sourceItems = remember(data.trips, story.sourceTripId) {
        data.trips.firstOrNull { it.id == story.sourceTripId }?.allItems?.associateBy { it.id }.orEmpty()
    }
    var selectedDayId by rememberSaveable(storyId) { mutableStateOf(orderedDays.firstOrNull()?.id) }
    val coverPicker = rememberLauncherForActivityResult(SystemImagePickerContract()) { uris ->
        val uri = uris.firstOrNull()
        if (uri != null) runCatching { repository.importMedia(uri, MediaKind.IMAGE) }
            .onSuccess { repository.updateStory(story.copy(coverMedia = it, coverZoom = 1.0, coverOffsetX = 0.0, coverOffsetY = 0.0)) }
            .onFailure { message = "无法读取封面图片：${it.localizedMessage}" }
    }
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TripRoundAction(Icons.Default.ArrowBack, "返回", onBack)
                Text("足迹", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
                Box {
                    TripRoundAction(Icons.Default.MoreHoriz, "足迹操作") { actionMenu = true }
                    TripDropdownMenu(actionMenu, { actionMenu = false }) {
                        DropdownMenuItem({ Text("编辑足迹") }, { actionMenu = false; editingStory = true }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                        DropdownMenuItem({ Text("同步最新旅程") }, {
                            actionMenu = false
                            val sourceId = story.sourceTripId
                            message = when {
                                sourceId == null -> "这份足迹不是从旅程整理而来，暂时没有可同步的来源。"
                                data.trips.any { it.id == sourceId } && repository.archiveTrip(sourceId) != null -> "已同步最新旅程。"
                                else -> "原旅程已不存在，无法同步。"
                            }
                        }, leadingIcon = { Icon(Icons.Default.Sync, null) })
                        DropdownMenuItem({ Text("分享足迹") }, { actionMenu = false; shareDayId = null; showingShare = true }, leadingIcon = { Icon(Icons.Default.Share, null) })
                        HorizontalDivider()
                        DropdownMenuItem({ Text("删除足迹", color = MaterialTheme.colorScheme.error) }, { actionMenu = false; deletingStory = true }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) })
                    }
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                orderedDays.forEachIndexed { index, day ->
                    val active = day.id == selectedDayId
                    Surface(onClick = { selectedDayId = day.id; scope.launch { listState.animateScrollToItem(dayIndices[day.id] ?: 0) } }, shape = RoundedCornerShape(22.dp), color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, border = if (active) null else androidx.compose.foundation.BorderStroke(.8.dp, TripLake.copy(alpha = .28f))) {
                        Column(Modifier.padding(horizontal = 13.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("第 ${index + 1} 天", style = MaterialTheme.typography.labelSmall, color = if (active) Color.White else MaterialTheme.colorScheme.primary)
                            Text(day.date.chineseDateText(), style = MaterialTheme.typography.labelMedium, color = if (active) Color.White else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                TripRoundAction(Icons.Default.Add, "继续添加一天") {
                    val created = repository.addStoryDay(story.id)
                    selectedDayId = created?.id
                    scope.launch { delay(100); listState.animateScrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)) }
                }
            }
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 112.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Box {
                        StoryCover(story) { coverMenu = true }
                        TripDropdownMenu(coverMenu, { coverMenu = false }) {
                            DropdownMenuItem({ Text(if (story.coverMedia == null) "选择封面" else "更换封面") }, { coverMenu = false; coverPicker.launch(Unit) }, leadingIcon = { Icon(Icons.Default.PhotoLibrary, null) })
                            if (story.coverMedia != null) DropdownMenuItem({ Text("移除封面") }, { coverMenu = false; repository.updateStory(story.copy(coverMedia = null, coverZoom = 1.0, coverOffsetX = 0.0, coverOffsetY = 0.0)) }, leadingIcon = { Icon(Icons.Default.HideImage, null) })
                        }
                    }
                }
                orderedDays.forEach { day ->
                    val expanded = day.id !in collapsedDays
                    item(key = "day:${day.id}", contentType = "dayHeader") {
                    StoryDayGroupSurface {
                        val dateAndCount = "${day.date.localDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", java.util.Locale.CHINA))} · ${day.entries.size} 个记录"
                        Row(Modifier.fillMaxWidth().clickable { collapsedDays = if (expanded) collapsedDays + day.id else collapsedDays - day.id }, verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(4.dp).height(34.dp).clip(RoundedCornerShape(3.dp)).background(TripLake))
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(day.title.ifBlank { "第 ${day.sortOrder + 1} 天" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(dateAndCount, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.width(8.dp))
                            Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, if (expanded) "收起" else "展开", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            var dayMenu by remember(day.id) { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { dayMenu = true }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.MoreHoriz, "当天更多操作", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp).border(1.dp, MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape))
                                }
                                TripDropdownMenu(dayMenu, { dayMenu = false }) {
                                    DropdownMenuItem({ Text("编辑当天") }, { dayMenu = false; editingDay = day }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                                    DropdownMenuItem({ Text("添加记录") }, { dayMenu = false; editing = day to StoryEntry(startTime = combineDateAndTime(day.date, "09:00"), endTime = combineDateAndTime(day.date, "10:00")) }, leadingIcon = { Icon(Icons.Default.Add, null) })
                                    DropdownMenuItem({ Text("分享当天") }, { dayMenu = false; shareDayId = day.id; showingShare = true }, leadingIcon = { Icon(Icons.Default.Share, null) })
                                    HorizontalDivider()
                                    DropdownMenuItem({ Text("删除当天", color = MaterialTheme.colorScheme.error) }, { dayMenu = false; deletingDay = day }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) })
                                }
                            }
                        }
                        if (expanded && day.note.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(day.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    }
                    if (expanded) {
                        items(day.entries.sortedBy { it.sortOrder }, key = { "entry:${day.id}:${it.id}" }, contentType = { "storyEntry" }) { entry ->
                            StoryEntryCard(entry, sourceItems[entry.sourceItemId], { editing = day to entry }, { openTarget = it }, { deleting = day to entry })
                        }
                        item(key = "add:${day.id}", contentType = "addEntry") {
                            TextButton(onClick = { editing = day to StoryEntry(startTime = combineDateAndTime(day.date, "09:00"), endTime = combineDateAndTime(day.date, "10:00")) }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("添加记录") }
                        }
                    }
                }
            }
        }
    }
    if (editingStory) StoryEditorDialog(story, { editingStory = false }) { title, destination, start, end, summary -> repository.updateStory(story.copy(title = title, destination = destination, startDate = start, endDate = end, summary = summary)); editingStory = false }
    editing?.let { (day, entry) -> StoryEntryEditor(repository, entry, { editing = null }) { repository.saveStoryEntry(story.id, day.id, it); editing = null } }
    editingDay?.let { day -> StoryDayEditorDialog(day, { editingDay = null }) { repository.updateStoryDay(story.id, it); editingDay = null } }
    deleting?.let { (day, entry) -> ConfirmDeleteDialog("删除记录？", "“${entry.title}”将从足迹中删除。", { deleting = null }) { repository.deleteStoryEntry(story.id, day.id, entry.id); deleting = null } }
    deletingDay?.let { day -> ConfirmDeleteDialog("删除当天？", "“${day.title.ifBlank { day.date.chineseDateText() }}”以及其中的记录都会删除。", { deletingDay = null }) { repository.deleteStoryDay(story.id, day.id); deletingDay = null } }
    if (showingShare) StoryShareDialog(story, initialDayId = shareDayId, onDismiss = { showingShare = false; shareDayId = null })
    if (deletingStory) ConfirmDeleteDialog("删除足迹？", "“${story.title}”以及其中的日期、记录和媒体引用都会删除。", { deletingStory = false }) { repository.deleteStory(story.id); deletingStory = false; onBack() }
    message?.let { AlertDialog(modifier = androidx.compose.ui.Modifier.dismissKeyboardOnBlankTap(), onDismissRequest = { message = null }, title = { Text("提示") }, text = { Text(it) }, confirmButton = { TextButton(onClick = { message = null }) { Text("好") } }) }
    openTarget?.let { target -> OpenPlaceChooser(target.displayName, target.address, { openTarget = null }) { platform ->
        if (platform == "高德地图") ExternalApps.openAmapTarget(context, target) else ExternalApps.openDiscovery(context, platform, target.displayName, target.address)
        openTarget = null
    } }
}

@Composable
private fun StoryCover(story: TravelStory, onCoverAction: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 200.dp).clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(Color(0xFF244D47), Color(0xFF427F79)))).clickable(onClick = onCoverAction)) {
        story.coverMedia?.let { MediaThumbnail(it, Modifier.matchParentSize()) }
        if (story.coverMedia != null) Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .12f), Color.Black.copy(alpha = .72f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(24.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            if (story.destination.isNotBlank()) Text(story.destination.uppercase(), style = MaterialTheme.typography.labelMedium, color = Color(0xFFDCECE5))
            Text(story.title, style = MaterialTheme.typography.headlineLarge, color = Color.White)
            if (story.summary.isNotBlank()) Text(story.summary, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .88f))
            Text("${story.startDate.chineseDateText()} — ${story.endDate.chineseDateText()}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .72f))
        }
    }
}

@Composable
private fun StoryDayEditorDialog(day: StoryDay, onDismiss: () -> Unit, save: (StoryDay) -> Unit) {
    var title by remember(day.id) { mutableStateOf(day.title) }
    var date by remember(day.id) { mutableLongStateOf(day.date) }
    var note by remember(day.id) { mutableStateOf(day.note) }
    var details by remember(day.id) { mutableStateOf(day.details) }
    AlertDialog(modifier = androidx.compose.ui.Modifier.dismissKeyboardOnBlankTap(),
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("编辑足迹日") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TripFormField(title, { title = it }, "当天标题")
                TripDateField(date, "日期", { date = it })
                TripFormField(note, { note = it }, "当天摘要", minLines = 3, singleLine = false)
                TripFormField(details, { details = it }, "细节补充", minLines = 4, singleLine = false)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = { Button(onClick = { save(day.copy(title = title.trim(), date = date, note = note.trim(), details = details.trim())) }) { Text("保存") } },
    )
}

@Composable
private fun StoryEntryCard(entry: StoryEntry, sourceItem: ItineraryItem?, onEdit: () -> Unit, onNavigate: (JourneyLocationTarget) -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var sourceExpanded by rememberSaveable(entry.id) { mutableStateOf(false) }
    Surface(onClick = onEdit, shape = RoundedCornerShape(17.dp), color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(.8.dp, TripMist.copy(alpha = .30f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (entry.category == PlaceCategory.TRANSPORT) Icons.Default.DirectionsCar else entry.category.icon(), null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(7.dp))
                Text(entry.title.ifBlank { "未命名记录" }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                if (entry.timeLabel.isNotBlank()) Text(entry.timeLabel, style = MaterialTheme.typography.bodySmall, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box {
                    IconButton(onClick = { menu = true }, Modifier.size(34.dp)) { Icon(Icons.Default.MoreVert, "记录操作", Modifier.size(18.dp)) }
                    TripDropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("编辑记录") }, { menu = false; onEdit() }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                        DropdownMenuItem({ Text("删除记录", color = MaterialTheme.colorScheme.error) }, { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) })
                    }
                }
            }
            entry.locationTargets.forEach { target ->
                Column {
                    Row(Modifier.fillMaxWidth().clickable { onNavigate(target) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(14.dp).then(
                                if (target.role == JourneyLocationRole.ORIGIN) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
                                else Modifier.background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(if (target.role == JourneyLocationRole.ORIGIN) Icons.Default.NearMe else Icons.Default.PushPin, null, Modifier.size(10.dp), tint = if (target.role == JourneyLocationRole.ORIGIN) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("${target.role.label}：${target.displayName}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontSize = 15.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.primary)
                        LocationCopyButton(target.displayName)
                    }
                    val address = target.address.trim()
                    if (address.isNotBlank() && address != target.displayName) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(address, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (entry.media.isNotEmpty()) StoryEntryMediaGrid(entry.media)
            if (entry.note.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.FormatQuote, null, Modifier.size(16.dp).rotate(180f), tint = MaterialTheme.colorScheme.primary)
                        Text("回忆", style = MaterialTheme.typography.labelMedium, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(entry.note, style = MaterialTheme.typography.bodyMedium, fontSize = 15.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            sourceItem?.note?.trim()?.takeIf { it.isNotEmpty() }?.let { sourceNote ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { sourceExpanded = !sourceExpanded }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Link, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Text("来自原旅程", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(if (sourceExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight, if (sourceExpanded) "收起原旅程说明" else "展开原旅程说明", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    if (sourceExpanded) {
                        Text("原说明：$sourceNote", style = MaterialTheme.typography.bodySmall, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryEntryEditor(repository: TripRepository, original: StoryEntry, onDismiss: () -> Unit, save: (StoryEntry) -> Unit) {
    val baseDay = original.startTime ?: original.endTime ?: System.currentTimeMillis()
    var title by remember(original.id) { mutableStateOf(original.title) }
    var mode by remember(original.id) { mutableStateOf(original.locationMode) }
    var place by remember(original.id) { mutableStateOf(original.placeName.ifBlank { original.address }) }
    var address by remember(original.id) { mutableStateOf(original.placeAddress.ifBlank { original.address }) }
    var origin by remember(original.id) { mutableStateOf(original.originName) }
    var originAddress by remember(original.id) { mutableStateOf(original.originAddress) }
    var destination by remember(original.id) { mutableStateOf(original.destinationName) }
    var destinationAddress by remember(original.id) { mutableStateOf(original.destinationAddress) }
    var hasTime by remember(original.id) { mutableStateOf(original.startTime != null || original.timeLabel.isNotBlank()) }
    var start by remember(original.id) { mutableLongStateOf(original.startTime ?: combineDateAndTime(baseDay, "09:00")!!) }
    var end by remember(original.id) { mutableLongStateOf(original.endTime ?: combineDateAndTime(baseDay, "10:00")!!) }
    var note by remember(original.id) { mutableStateOf(original.note) }
    var media by remember(original.id) { mutableStateOf(original.media) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(SystemImagePickerContract(multiple = true, allowImagesAndVideos = true)) { uris ->
        media = media + uris.take((9 - media.size).coerceAtLeast(0)).mapNotNull { uri ->
            runCatching { repository.importMedia(uri, if (context.contentResolver.getType(uri)?.startsWith("video") == true) MediaKind.VIDEO else MediaKind.IMAGE) }.getOrNull()
        }
    }
    val validTime = !hasTime || end > start
    val validLocation = if (mode == ArrangementLocationMode.SINGLE) place.isNotBlank() else origin.isNotBlank() && destination.isNotBlank()
    TripEditorSheet(
        onDismissRequest = onDismiss,
        title = { Text("编辑记录") },
        text = {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TripEditorSection("地点")
                TripEditorGroup {
                    TripLocationModePicker(mode) { mode = it }
                    TripEditorDivider()
                    if (mode == ArrangementLocationMode.SINGLE) {
                        TripInlineField(place, { place = it }, "地点名称")
                        TripEditorDivider()
                        TripInlineField(address, { address = it }, "地点详细地址（选填）")
                    } else {
                        TripInlineField(origin, { origin = it }, "出发地")
                        TripEditorDivider()
                        TripInlineField(originAddress, { originAddress = it }, "出发地详细地址（选填）")
                        TripEditorDivider()
                        TripInlineField(destination, { destination = it }, "目的地")
                        TripEditorDivider()
                        TripInlineField(destinationAddress, { destinationAddress = it }, "目的地详细地址（选填）")
                    }
                    TripEditorDivider()
                    TripFormField(title, { title = it }, "记录标题（选填）")
                }
                TripEditorSection("时间（选填）")
                TripEditorGroup {
                    if (hasTime) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                TripTimeRangeField(start, end, label = "起止时间") { selectedStart, selectedEnd -> start = selectedStart; end = selectedEnd }
                            }
                            IconButton(onClick = { hasTime = false }) { Icon(Icons.Default.Cancel, "清除时间", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth().clickable { hasTime = true }.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("起止时间", Modifier.weight(1f))
                            Text("添加", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.AddCircleOutline, "添加起止时间", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        }
                    }
                }
                TripEditorSection("回忆")
                TripEditorGroup { TripInlineField(note, { note = it }, "记录这段足迹的见闻和感受", minLines = 5) }
                TripEditorSection("照片与视频")
                TripEditorGroup {
                    EditorMediaGrid(media, 9, onAdd = { picker.launch(Unit) }, onRemove = { id -> media = media.filterNot { it.id == id } })
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = {
            TextButton(onClick = {
                val startValue = if (hasTime) start else null
                val endValue = if (hasTime) end else null
                save(original.copy(
                    title = title.trim().ifBlank { if (mode == ArrangementLocationMode.SINGLE) place.trim() else "${origin.trim()} → ${destination.trim()}" }, locationMode = mode,
                    placeName = place.trim(), placeAddress = address.trim(), address = if (mode == ArrangementLocationMode.SINGLE) address.trim() else destinationAddress.trim(),
                    originName = origin.trim(), originAddress = originAddress.trim(), destinationName = destination.trim(), destinationAddress = destinationAddress.trim(),
                    startTime = startValue, endTime = endValue, timeLabel = if (hasTime && startValue != null && endValue != null) "${startValue.timeText()} – ${endValue.timeText()}" else "",
                    note = note.trim(), media = media,
                ))
            }, enabled = validLocation && validTime) { Text("保存") }
        },
    )
}

@Composable
fun TripInlineField(value: String, onChange: (String) -> Unit, placeholder: String, minLines: Int = 1) {
    androidx.compose.foundation.text.BasicTextField(
        value = value, onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(vertical = 15.dp).semantics { contentDescription = placeholder },
        minLines = minLines, singleLine = minLines == 1,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Normal),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { inner -> Box { if (value.isEmpty()) Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge); inner() } },
    )
}


@Composable
private fun StoryDayGroupSurface(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.linearGradient(listOf(TripLake.copy(alpha = .11f), TripMist.copy(alpha = .13f), MaterialTheme.colorScheme.surface.copy(alpha = .92f))), shape)
            .border(1.dp, TripLake.copy(alpha = .23f), shape)
            .padding(18.dp),
        content = content,
    )
}

@Composable
private fun StoryEntryMediaGrid(media: List<MediaReference>) {
    MediaGallery(media)
}
