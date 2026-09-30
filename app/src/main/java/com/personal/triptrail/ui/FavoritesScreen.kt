@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.personal.triptrail.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personal.triptrail.data.*
import com.personal.triptrail.util.ExternalApps
import com.personal.triptrail.util.SystemImagePickerContract
import com.personal.triptrail.util.SmartRecognitionResult
import com.personal.triptrail.util.ZhipuRecognitionService
import kotlinx.coroutines.launch

@Composable
fun FavoritesScreen(repository: TripRepository, favorites: List<ItineraryItem>, modifier: Modifier = Modifier) {
    var search by rememberSaveableState("")
    var category by remember { mutableStateOf<PlaceCategory?>(null) }
    var editing by remember { mutableStateOf<ItineraryItem?>(null) }
    var creating by remember { mutableStateOf(false) }
    var smartDraft by remember { mutableStateOf<ItineraryItem?>(null) }
    var smartTarget by remember { mutableStateOf<ItineraryItem?>(null) }
    var deleting by remember { mutableStateOf<ItineraryItem?>(null) }
    var openFavorite by remember { mutableStateOf<ItineraryItem?>(null) }
    val context = LocalContext.current
    LaunchedEffect(editing?.id, openFavorite?.id) {
        val id = editing?.id ?: openFavorite?.id
        if (id != null) com.personal.triptrail.util.CloudSyncService.get(context).sync(repository, kind = "favorite", recordId = id, automatic = true)
    }
    val filtered = remember(favorites, search, category) {
        favorites.filter { favorite ->
            (category == null || favorite.category == category) &&
                (search.isBlank() || listOf(favorite.title, favorite.locationSummary, favorite.note, favorite.favoriteCityLabel).any { it.contains(search.trim(), true) })
        }.sortedByDescending { it.favoriteCreatedAt }
    }

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (favorites.isEmpty()) {
            EmptyFavorites(onCreate = { creating = true })
        } else {
            Column(Modifier.fillMaxSize()) {
                TripSearchField(search, "搜索名称、城市、地点或备注", { search = it }, Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 2.dp), trailingContent = { FavoriteTypeMenu(category) { category = it } })
                if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.SearchOff, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
                            Text("没有找到收藏", style = MaterialTheme.typography.titleLarge)
                            TextButton(onClick = { search = ""; category = null }) { Text("清除条件") }
                        }
                    }
                } else {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                    val cardHeight = ((maxHeight - 12.dp) / 2).coerceAtLeast(240.dp * androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(filtered, key = { it.id }) { favorite ->
                            FavoriteCard(repository, favorite, { editing = favorite }, { deleting = favorite }, { openFavorite = favorite }, cardHeight)
                        }
                    }
                    }
                }
            }
        }
        TripFloatingCreateButton("新建收藏", Modifier.align(Alignment.BottomEnd)) { smartDraft = null; creating = true }
    }

    openFavorite?.let { favorite ->
        OpenPlaceChooser(favorite.title, "", { openFavorite = null }) { platform ->
            openFavorite = null
            if (platform == "高德地图") {
                val target = favorite.locationTargets.lastOrNull()
                if (target == null) android.widget.Toast.makeText(context, "请先为这条收藏填写地点。", android.widget.Toast.LENGTH_SHORT).show()
                else if (!ExternalApps.openAmapTarget(context, target)) android.widget.Toast.makeText(context, "暂时无法打开高德地图，请确认已安装或稍后重试。", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                ExternalApps.openDiscovery(context, platform, favorite.title, "")
            }
        }
    }

    if (creating) FavoriteEditorDialog(
        repository = repository,
        original = smartDraft ?: ItineraryItem(isFavorite = true),
        isNew = favorites.none { it.id == smartDraft?.id },
        onDismiss = { creating = false; smartDraft = null },
        onSmartImport = { target -> creating = false; editing = null; smartTarget = target },
        onSave = { repository.saveFavorite(it); creating = false; smartDraft = null },
    )
    editing?.let { favorite -> FavoriteEditorDialog(repository, favorite, false, { editing = null }, { target -> editing = null; smartTarget = target }) { repository.saveFavorite(it); editing = null } }
    deleting?.let { favorite -> ConfirmDeleteDialog(
        "删除收藏？", "“${favorite.title}”将移入回收站，24 小时内可恢复。云端收藏会同步删除，已导入旅程的安排不受影响。", { deleting = null }
    ) { repository.deleteFavorite(favorite.id); deleting = null } }
    smartTarget?.let { target ->
        FavoriteSmartDialog(
            onDismiss = { smartTarget = null; smartDraft = target; creating = true },
            onRecognized = { result ->
                smartTarget = null
                smartDraft = result.item.copy(id = target.id, favoriteCity = result.item.favoriteCity.trim().ifBlank { target.favoriteCity }, isFavorite = true, favoriteCreatedAt = target.favoriteCreatedAt, media = target.media)
                creating = true
            },
        )
    }
}

@Composable
private fun EmptyFavorites(onCreate: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.FavoriteBorder, null, Modifier.size(58.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(14.dp))
        Text("还没有收藏", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("把想去的景点、餐厅或特别地点先收起来，有计划时再导入旅程。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        Button(onClick = onCreate) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("新建收藏") }
    }
}

@Composable
private fun FavoriteTypeMenu(selected: PlaceCategory?, onSelect: (PlaceCategory?) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { menu = true }) {
            Text(selected?.label ?: "全部类型", maxLines = 1)
            Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(16.dp))
        }
        TripDropdownMenu(menu, { menu = false }) {
            DropdownMenuItem({ Text("全部类型") }, onClick = { onSelect(null); menu = false }, leadingIcon = { if (selected == null) Icon(Icons.Default.Check, null) })
            PlaceCategory.entries.forEach { item ->
                DropdownMenuItem({ Text(item.label) }, onClick = { onSelect(item); menu = false }, leadingIcon = { Icon(if (selected == item) Icons.Default.Check else item.icon(), null) })
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FavoriteCard(repository: TripRepository, favorite: ItineraryItem, onEdit: () -> Unit, onDelete: () -> Unit, onNavigate: () -> Unit, cardHeight: androidx.compose.ui.unit.Dp) {
    var actions by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().height(cardHeight).clip(RoundedCornerShape(20.dp)).combinedClickable(onClickLabel = "编辑收藏", onLongClickLabel = "收藏功能", onClick = onEdit, onLongClick = { actions = true }),
        shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(.8.dp, TripMist.copy(alpha = .42f)), shadowElevation = 2.dp,
    ) {
        Box {
            Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(favorite.title.ifBlank { "未命名收藏" }, modifier = Modifier.weight(1f).combinedClickable(onClickLabel = "选择地图或搜索平台", onLongClickLabel = "收藏功能", onClick = onNavigate, onLongClick = { actions = true }), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                CloudBadge(favorite.id, "favorite", MaterialTheme.typography.titleMedium.fontSize)
                Box {
                    TripDropdownMenu(actions, { actions = false }) {
                        CloudModeAction(repository, favorite.id, "favorite")
                    HorizontalDivider()
                        DropdownMenuItem(text = { Text("删除", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) }, onClick = { actions = false; onDelete() })
                    }
                }



                }
                val cover = favorite.media.firstOrNull()
                if (cover != null) {
                    FavoriteCover(cover, Modifier.weight(1f).fillMaxWidth())
                } else {
                    FavoriteDefaultCover(favorite.category, Modifier.weight(1f).fillMaxWidth())
                }
                if (favorite.note.isNotBlank()) {
                    Text(favorite.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(shape = RoundedCornerShape(14.dp), color = TripLake.copy(alpha = .11f)) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(favorite.category.icon(), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(favorite.category.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                        }
                    }
                    val city = favorite.favoriteCityLabel
                    if (city.isNotBlank() && city != "未设置城市") {
                        Text(city, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (favorite.cost > 0) Text("¥${favorite.cost.toInt()}", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }


        }
    }
}

@Composable
private fun FavoriteCover(media: MediaReference, modifier: Modifier) {
    val coverModifier = modifier.clip(RoundedCornerShape(10.dp))
    if (media.kind != MediaKind.VIDEO) {
        MediaThumbnail(media, coverModifier, cornerRadius = 10.dp)
        return
    }
    val context = LocalContext.current
    val frame by produceState<android.graphics.Bitmap?>(null, media.localUri) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, android.net.Uri.parse(media.localUri))
                if (android.os.Build.VERSION.SDK_INT >= 27) {
                    retriever.getScaledFrameAtTime(-1, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 512, 288)
                } else {
                    retriever.frameAtTime?.let { original ->
                        val ratio = minOf(1f, 512f / maxOf(original.width, original.height))
                        val scaled = android.graphics.Bitmap.createScaledBitmap(original, (original.width * ratio).toInt().coerceAtLeast(1), (original.height * ratio).toInt().coerceAtLeast(1), true)
                        if (scaled !== original) original.recycle()
                        scaled
                    }
                }
            } catch (_: Exception) { null }
            finally { retriever.release() }
        }
    }
    Box(coverModifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        frame?.let { androidx.compose.foundation.Image(it.asImageBitmap(), "视频封面", Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
        Icon(Icons.Default.PlayCircle, "视频", tint = if (frame != null) Color.White else MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun FavoriteEditorDialog(repository: TripRepository, original: ItineraryItem, isNew: Boolean, onDismiss: () -> Unit, onSmartImport: (ItineraryItem) -> Unit = {}, onSave: (ItineraryItem) -> Unit) {
    var creationMethod by remember { mutableStateOf("普通新建") }
    var title by remember(original.id) { mutableStateOf(original.title) }
    var city by remember(original.id) { mutableStateOf(original.favoriteCity) }
    var category by remember(original.id) { mutableStateOf(original.category) }
    var mode by remember(original.id) { mutableStateOf(original.locationMode) }
    var place by remember(original.id) { mutableStateOf(original.placeName.ifBlank { original.address }) }
    var address by remember(original.id) { mutableStateOf(original.placeAddress.ifBlank { original.address }) }
    var origin by remember(original.id) { mutableStateOf(original.originName) }
    var originAddress by remember(original.id) { mutableStateOf(original.originAddress) }
    var destination by remember(original.id) { mutableStateOf(original.destinationName) }
    var destinationAddress by remember(original.id) { mutableStateOf(original.destinationAddress) }
    var note by remember(original.id) { mutableStateOf(original.note) }
    var cost by remember(original.id) { mutableStateOf(if (original.cost == 0.0) "" else original.cost.toString()) }
    var media by remember(original.id) { mutableStateOf(original.media) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(SystemImagePickerContract(multiple = true, allowImagesAndVideos = true)) { uris ->
        media = media + uris.take((20 - media.size).coerceAtLeast(0)).mapNotNull { uri ->
            runCatching { repository.importMedia(uri, if (context.contentResolver.getType(uri)?.startsWith("video") == true) MediaKind.VIDEO else MediaKind.IMAGE) }.getOrNull()
        }
    }
    TripEditorSheet(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "新建收藏" else "编辑收藏") },
        text = { Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (isNew) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("智能录入", "普通新建").forEach { method ->
                    Surface(onClick = { creationMethod = method }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), color = if (creationMethod == method) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                        Text(method, Modifier.padding(vertical = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            if (isNew && creationMethod == "智能录入") {
            TripEditorGroup {
                TextButton(onClick = { onSmartImport(original.copy(title = title, favoriteCity = city, category = category, locationMode = mode, placeName = place, placeAddress = address, originName = origin, originAddress = originAddress, destinationName = destination, destinationAddress = destinationAddress, note = note, cost = cost.toDoubleOrNull() ?: 0.0, media = media)) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("智能录入")
                }
            }
            } else {
            TripEditorSection("安排")
            TripEditorGroup {
                TripFormField(title, { title = it }, "安排名称")
                TripEditorDivider()
                TripFormField(note, { note = it }, "补充说明", minLines = 2, singleLine = false)
                TripEditorDivider()
                TripCategoryPicker(category) { category = it }
            }
            TripEditorSection("照片与视频")
            TripEditorGroup {
                EditorMediaGrid(media, 20, onAdd = { picker.launch(Unit) }, onRemove = { id -> media = media.filterNot { it.id == id } })
            }
            TripEditorSection("地点")
            TripEditorGroup {
                TripFormField(city, { city = it }, "城市（选填，用于筛选收藏）")
                TripEditorDivider()
                TripLocationModePicker(mode) { mode = it }
                TripEditorDivider()
                if (mode == ArrangementLocationMode.SINGLE) {
                    TripFormField(place, { place = it }, "地点名称")
                    TripEditorDivider()
                    TripFormField(address, { address = it }, "详细地址（选填）")
                } else {
                    TripFormField(origin, { origin = it }, "出发地")
                    TripFormField(originAddress, { originAddress = it }, "出发地详细地址（选填）")
                    TripEditorDivider()
                    TripFormField(destination, { destination = it }, "目的地")
                    TripFormField(destinationAddress, { destinationAddress = it }, "目的地详细地址（选填）")
                }
            }
            TripEditorSection("花费")
            TripEditorGroup { TripCostField(cost) { cost = it } }
            }
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = { TextButton(onClick = { onSave(original.copy(title = title.trim(), favoriteCity = city.trim(), category = category, locationMode = mode, placeName = place.trim(), placeAddress = address.trim(), address = address.trim(), originName = origin.trim(), originAddress = originAddress.trim(), destinationName = destination.trim(), destinationAddress = destinationAddress.trim(), note = note.trim(), cost = cost.toDoubleOrNull() ?: 0.0, media = media, isFavorite = true)) }, enabled = title.isNotBlank() && (!isNew || creationMethod == "普通新建")) { Text("保存") } },
    )
}

@Composable
private fun FavoriteSmartDialog(onDismiss: () -> Unit, onRecognized: (SmartRecognitionResult) -> Unit) {
    var text by remember { mutableStateOf("") }
    var recognizing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var fallbackResult by remember { mutableStateOf<SmartRecognitionResult?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun recognize() {
        recognizing = true; error = null; fallbackResult = null
        scope.launch {
            runCatching { ZhipuRecognitionService.recognizeSingleItemText(context, text, System.currentTimeMillis(), System.currentTimeMillis()) }
                .onSuccess { result ->
                    if (result.fallbackMessage != null) fallbackResult = result else onRecognized(result)
                }
                .onFailure { error = it.localizedMessage ?: "识别失败" }
            recognizing = false
        }
    }
    SmartImportInputSheet(
        placeholder = "粘贴 1 个安排或地点的描述",
        maxImages = 1,
        onDismiss = onDismiss,
        recognizing = recognizing,
        extraContent = {
            fallbackResult?.let { result ->
                Text(result.fallbackMessage.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row {
                    TextButton(onClick = ::recognize, enabled = !recognizing) { Text("重新识别") }
                    TextButton(onClick = { onRecognized(result) }, enabled = !recognizing) { Text("使用本地结果") }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        },
        onSubmit = { input -> text = input; recognize() },
    )
}

@Composable
private fun rememberSaveableState(initial: String): MutableState<String> = androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initial) }



@Composable
private fun FavoriteDefaultCover(category: PlaceCategory, modifier: Modifier = Modifier) {
    val (tint, symbol) = when (category) {
        PlaceCategory.ATTRACTION -> Color(0xFF3D8C75) to Icons.Default.Landscape
        PlaceCategory.RESTAURANT -> Color(0xFFC76E45) to Icons.Default.Restaurant
        PlaceCategory.HOTEL -> Color(0xFF6E75AD) to Icons.Default.Hotel
        PlaceCategory.TRANSPORT -> Color(0xFF4785B3) to Icons.Default.Tram
        else -> Color(0xFFAB8552) to Icons.Default.Luggage
    }
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.clip(RoundedCornerShape(10.dp)).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(tint.copy(alpha = .18f), tint.copy(alpha = .55f)))), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            drawCircle(Color.White.copy(alpha = .3f), radius = size.width * .4f, center = androidx.compose.ui.geometry.Offset(size.width * .9f, size.height * .2f))
            drawCircle(tint.copy(alpha = .14f), radius = size.width * .7f, center = androidx.compose.ui.geometry.Offset(size.width * .2f, size.height))
        }
        Icon(symbol, "${category.label}默认封面", modifier = Modifier.size(minOf(maxWidth * .43f, maxHeight * .45f)), tint = Color.White.copy(alpha = .95f))
    }
}
