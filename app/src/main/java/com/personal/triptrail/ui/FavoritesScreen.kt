@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.personal.triptrail.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
    var openTarget by remember { mutableStateOf<JourneyLocationTarget?>(null) }
    val context = LocalContext.current
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
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.End) {
                    TripRoundAction(Icons.Default.Add, "新建收藏") { smartDraft = null; creating = true }
                }
                TripSearchField(search, "搜索名称、城市、地点或备注", { search = it }, Modifier.padding(horizontal = 16.dp))
                FavoriteFilterBar(favorites.size, category, { category = it }, Modifier.padding(16.dp, 14.dp, 16.dp, 12.dp))
                if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.SearchOff, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
                            Text("没有找到收藏", style = MaterialTheme.typography.titleLarge)
                            TextButton(onClick = { search = ""; category = null }) { Text("清除条件") }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 110.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(filtered, key = { it.id }) { favorite ->
                            FavoriteCard(favorite, { editing = favorite }, { deleting = favorite }, { openTarget = it })
                        }
                    }
                }
            }
        }
    }

    openTarget?.let { target ->
        OpenPlaceChooser(target.displayName, target.address, { openTarget = null }) { platform ->
            openTarget = null
            val opened = if (platform == "高德地图") ExternalApps.openAmapTarget(context, target)
                else ExternalApps.openDiscovery(context, platform, target.displayName, target.address)
            if (!opened && platform == "高德地图") android.widget.Toast.makeText(context, "暂时无法打开${platform}，请确认已安装或稍后重试。", android.widget.Toast.LENGTH_SHORT).show()
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
        "删除收藏？", "“${favorite.title}”将从收藏中删除，已导入旅程的安排不受影响。", { deleting = null }
    ) { repository.deleteFavorite(favorite.id); deleting = null } }
    smartTarget?.let { target ->
        FavoriteSmartDialog(
            onDismiss = { smartTarget = null; smartDraft = target; creating = true },
            onRecognized = { result ->
                smartTarget = null
                smartDraft = result.item.copy(id = target.id, isFavorite = true, favoriteCreatedAt = target.favoriteCreatedAt, media = target.media)
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
private fun FavoriteFilterBar(count: Int, selected: PlaceCategory?, onSelect: (PlaceCategory?) -> Unit, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 0.dp) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Favorite, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("$count 个想去的地方", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            Box {
                TextButton(onClick = { menu = true }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Icon(Icons.Default.FilterList, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text(selected?.label ?: "全部类型")
                }
                TripDropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("全部类型") }, onClick = { onSelect(null); menu = false }, leadingIcon = { if (selected == null) Icon(Icons.Default.Check, null) })
                    PlaceCategory.entries.forEach { item ->
                        DropdownMenuItem({ Text(item.label) }, onClick = { onSelect(item); menu = false }, leadingIcon = { Icon(if (selected == item) Icons.Default.Check else item.icon(), null) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteCard(favorite: ItineraryItem, onEdit: () -> Unit, onDelete: () -> Unit, onNavigate: (JourneyLocationTarget) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().height(280.dp * androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)),
        onClick = onEdit,
        shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(.8.dp, TripMist.copy(alpha = .42f)), shadowElevation = 2.dp,
    ) {
        Box {
            Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Surface(shape = RoundedCornerShape(14.dp), color = TripLake.copy(alpha = .11f)) {
                    Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(favorite.category.icon(), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp))
                        Text(favorite.category.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(favorite.title.ifBlank { "未命名收藏" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val city = favorite.favoriteCityLabel
                if (city.isNotBlank() && city != "未设置城市") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationCity, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(city, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                favorite.locationTargets.forEach { target ->
                    Row(Modifier.fillMaxWidth().clickable(onClickLabel = "打开地点") { onNavigate(target) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (target.role == JourneyLocationRole.ORIGIN) Icons.Default.MyLocation else Icons.Default.PinDrop, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(target.displayName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        LocationCopyButton(target.displayName)
                    }
                }
                if (favorite.note.isNotBlank()) Text(favorite.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                if (favorite.cost > 0) {
                    Text(listOfNotNull(favorite.cost.takeIf { it > 0 }?.let { "¥${it.toInt()}" }).joinToString("  "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box(Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreHoriz, "更多操作", tint = MaterialTheme.colorScheme.primary) }
                TripDropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("编辑收藏") }, onClick = { menu = false; onEdit() }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                    DropdownMenuItem({ Text("删除收藏", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) })
                }
            }
        }
    }
}

@Composable
private fun FavoriteEditorDialog(repository: TripRepository, original: ItineraryItem, isNew: Boolean, onDismiss: () -> Unit, onSmartImport: (ItineraryItem) -> Unit = {}, onSave: (ItineraryItem) -> Unit) {
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
            TripEditorGroup {
                TextButton(onClick = { onSmartImport(original.copy(title = title, favoriteCity = city, category = category, locationMode = mode, placeName = place, placeAddress = address, originName = origin, originAddress = originAddress, destinationName = destination, destinationAddress = destinationAddress, note = note, cost = cost.toDoubleOrNull() ?: 0.0, media = media)) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("智能录入")
                }
            }
            TripEditorSection("安排")
            TripEditorGroup {
                TripFormField(title, { title = it }, "安排名称")
                TripEditorDivider()
                TripFormField(note, { note = it }, "补充说明", minLines = 2, singleLine = false)
                TripEditorDivider()
                TripCategoryPicker(category) { category = it }
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
            TripEditorSection("照片与视频")
            TripEditorGroup {
                EditorMediaGrid(media, 20, onAdd = { picker.launch(Unit) }, onRemove = { id -> media = media.filterNot { it.id == id } })
            }
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = { TextButton(onClick = { onSave(original.copy(title = title.trim(), favoriteCity = city.trim(), category = category, locationMode = mode, placeName = place.trim(), placeAddress = address.trim(), address = address.trim(), originName = origin.trim(), originAddress = originAddress.trim(), destinationName = destination.trim(), destinationAddress = destinationAddress.trim(), note = note.trim(), cost = cost.toDoubleOrNull() ?: 0.0, media = media, isFavorite = true)) }, enabled = title.isNotBlank()) { Text("保存") } },
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
