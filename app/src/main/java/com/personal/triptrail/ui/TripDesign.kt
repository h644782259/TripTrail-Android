@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.personal.triptrail.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import com.personal.triptrail.data.dateText
import com.personal.triptrail.data.chineseDateText
import com.personal.triptrail.data.localDate
import com.personal.triptrail.data.timeText
import com.personal.triptrail.data.PlaceCategory
import com.personal.triptrail.data.combineDateAndTime
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged

private val LocalGroupedEditor = androidx.compose.runtime.staticCompositionLocalOf { false }

enum class RootTab(val label: String, val icon: ImageVector) {
    TRIPS("旅程", Icons.Default.Map),
    STORIES("足迹", Icons.Default.MenuBook),
    FAVORITES("收藏", Icons.Default.Favorite),
    SETTINGS("我的", Icons.Default.AccountCircle),
}

@Composable
fun TripBottomBar(selected: RootTab, onSelect: (RootTab) -> Unit) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val barColor = if (dark) MaterialTheme.colorScheme.surface else Color.White
    val selectedColor = if (dark) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primaryContainer
    val inactiveColor = if (dark) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(32.dp),
            color = barColor,
            border = BorderStroke(1.5.dp, if (dark) MaterialTheme.colorScheme.outlineVariant else Color.White.copy(alpha = .95f)),
            shadowElevation = 2.dp,
            tonalElevation = 0.dp,
        ) {
            Row(Modifier.fillMaxSize().padding(4.dp).selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
                RootTab.entries.forEach { tab ->
                    val active = tab == selected
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(26.dp))
                            .background(if (active) selectedColor else Color.Transparent)
                            .selectable(selected = active, role = Role.Tab, onClick = { onSelect(tab) }),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val tint = if (active) MaterialTheme.colorScheme.primary else inactiveColor
                        when (tab) {
                            RootTab.TRIPS, RootTab.STORIES -> Icon(
                                painter = androidx.compose.ui.res.painterResource(if (tab == RootTab.TRIPS) com.personal.triptrail.R.drawable.tab_journey else com.personal.triptrail.R.drawable.tab_footprints),
                                contentDescription = null, modifier = Modifier.size(26.dp), tint = tint,
                            )
                            else -> Icon(tab.icon, contentDescription = null, modifier = Modifier.size(26.dp), tint = tint)
                        }
                        Text(tab.label, style = MaterialTheme.typography.labelSmall, color = if (active) MaterialTheme.colorScheme.primary else inactiveColor, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
fun TripRoundAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(modifier = Modifier.size(44.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = .96f), shadowElevation = 1.dp, onClick = onClick) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp)) }
    }
}

@Composable
fun TripSearchField(value: String, placeholder: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    TextField(
        value = value, onValueChange = onValueChange, modifier = modifier.fillMaxWidth().heightIn(min = 54.dp),
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium, maxLines = 1) },
        leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(21.dp)) }, singleLine = true,
        shape = RoundedCornerShape(26.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface, disabledContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
fun TripFormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    singleLine: Boolean = minLines == 1,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: (@Composable (() -> Unit))? = null,
    trailingIcon: (@Composable (() -> Unit))? = null,
) {
    if (LocalGroupedEditor.current) {
        Column(modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(value = value, onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp).semantics { contentDescription = label },
                minLines = minLines, singleLine = singleLine, readOnly = readOnly,
                keyboardOptions = keyboardOptions, visualTransformation = visualTransformation,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Normal),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary))
        }
        return
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, modifier = Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = value, onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            minLines = minLines, singleLine = singleLine, readOnly = readOnly,
            keyboardOptions = keyboardOptions, visualTransformation = visualTransformation,
            leadingIcon = leadingIcon, trailingIcon = trailingIcon,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Normal),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

@Composable
fun TripDateField(value: Long, label: String, onValueChange: (Long) -> Unit, modifier: Modifier = Modifier) {
    var showing by remember { mutableStateOf(false) }
    Box(modifier) {
        TripFormField(
            value = value.dateText(),
            onValueChange = {},
            label = label,
            readOnly = true,
            leadingIcon = { Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary) },
            trailingIcon = { IconButton(onClick = { showing = true }) { Icon(Icons.Default.KeyboardArrowDown, "选择日期") } },
        )
        Box(Modifier.matchParentSize().clickable { showing = true })
    }
    if (showing) {
        val state = rememberDatePickerState(initialSelectedDateMillis = value.localDate().toEpochDay() * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { showing = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { selected ->
                        val date = LocalDate.ofEpochDay(selected / 86_400_000L)
                        onValueChange(date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
                    }
                    showing = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showing = false }) { Text("取消") } },
            shape = RoundedCornerShape(28.dp),
        ) {
            DatePicker(
                state = state,
                showModeToggle = false,
                title = {
                    Text(label, Modifier.fillMaxWidth().padding(horizontal = 24.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                },
                headline = {
                    Text(value.chineseDateText(), Modifier.fillMaxWidth().padding(horizontal = 24.dp), style = MaterialTheme.typography.titleLarge, color = TripInk, maxLines = 1)
                },
                colors = DatePickerDefaults.colors(containerColor = TripSurface),
            )
        }
    }
}

@Composable
fun TripTimeField(value: Long, label: String, onValueChange: (Long) -> Unit, modifier: Modifier = Modifier) {
    var showing by remember { mutableStateOf(false) }
    val local = value.localDate()
    val zoned = java.time.Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault())
    Box(modifier) {
        TripFormField(
            value = value.timeText(),
            onValueChange = {},
            label = label,
            readOnly = true,
            leadingIcon = { Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary) },
        )
        Box(Modifier.matchParentSize().clickable { showing = true })
    }
    if (showing) {
        var hour by remember(value) { mutableStateOf(zoned.hour) }
        var minute by remember(value) { mutableStateOf(zoned.minute) }
        AlertDialog(modifier = androidx.compose.ui.Modifier.dismissKeyboardOnBlankTap(),
            onDismissRequest = { showing = false },
            title = { Text(label) },
            text = { WheelTimePicker(hour, minute, { hour = it }, { minute = it }) },
            confirmButton = {
                TextButton(onClick = {
                    onValueChange(local.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                    showing = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showing = false }) { Text("取消") } },
            shape = RoundedCornerShape(28.dp),
            containerColor = TripSurface,
        )
    }
}

@Composable
fun TripDateRangeField(start: Long, end: Long, onChange: (Long, Long) -> Unit) {
    var showing by remember { mutableStateOf(false) }
    val grouped = LocalGroupedEditor.current
    Surface(
        onClick = { showing = true }, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (grouped) 0.dp else 14.dp),
        color = if (grouped) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(horizontal = if (grouped) 0.dp else 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("旅行日期", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Normal)
                Icon(Icons.Default.ChevronRight, "选择日期范围", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("出发" to start, "返程" to end).forEach { (label, value) ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        // Each endpoint gets its own column and may wrap when font scaling needs it.
                        Text(value.localDate().format(java.time.format.DateTimeFormatter.ofPattern(if (start.localDate().year == end.localDate().year) "M月d日" else "yyyy年M月d日")), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
    if (showing) TripDateRangeSheet(start, end, { showing = false }) { selectedStart, selectedEnd ->
        onChange(selectedStart, selectedEnd)
        showing = false
    }
}

@Composable
fun TripTimeRangeField(start: Long, end: Long, label: String = "时间", onChange: (Long, Long) -> Unit) {
    var showing by remember { mutableStateOf(false) }
    if (LocalGroupedEditor.current) {
        Row(Modifier.fillMaxWidth().clickable { showing = true }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text("${start.timeText()}–${end.timeText()}", color = TripInk, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"))
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
        }
    } else
    OutlinedButton(
        onClick = { showing = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .35f)),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Icon(Icons.Default.Schedule, null, tint = TripInk); Spacer(Modifier.width(8.dp)); Text("${start.timeText()}–${end.timeText()}", color = TripInk, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"))
    }
    if (showing) {
        val startZone = java.time.Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault())
        val endZone = java.time.Instant.ofEpochMilli(end).atZone(ZoneId.systemDefault())
        var startHour by remember(start) { mutableStateOf(startZone.hour) }
        var startMinute by remember(start) { mutableStateOf(startZone.minute) }
        var endHour by remember(end) { mutableStateOf(endZone.hour) }
        var endMinute by remember(end) { mutableStateOf(endZone.minute) }
        val selectedStart = start.localDate().atTime(startHour, startMinute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val selectedEnd = start.localDate().atTime(endHour, endMinute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        AlertDialog(modifier = androidx.compose.ui.Modifier.dismissKeyboardOnBlankTap(), onDismissRequest = { showing = false }, title = { Text("选择起止时间") },
            text = {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("开始时间", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        WheelTimePicker(startHour, startMinute, { startHour = it }, { startMinute = it })
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("结束时间", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        WheelTimePicker(endHour, endMinute, { endHour = it }, { endMinute = it })
                    }
                }
            }, confirmButton = { TextButton(onClick = {
                if (selectedEnd > selectedStart) { onChange(selectedStart, selectedEnd); showing = false }
            }, enabled = selectedEnd > selectedStart) { Text(if (selectedEnd > selectedStart) "确定" else "结束须晚于开始") } }, dismissButton = { TextButton(onClick = { showing = false }) { Text("取消") } })
    }
}

/** A touch-friendly 24-hour wheel used everywhere the app edits a time. */
@Composable
private fun WheelTimePicker(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WheelNumberPicker(hour, 23, onHourChange, "小时")
        Text(":", style = MaterialTheme.typography.headlineMedium, color = TripInk, modifier = Modifier.padding(horizontal = 8.dp))
        WheelNumberPicker(minute, 59, onMinuteChange, "分钟")
    }
}

@Composable
private fun WheelNumberPicker(value: Int, maximum: Int, onValueChange: (Int) -> Unit, contentDescription: String) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = value.coerceIn(0, maximum))
    val flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Center)
    val values = remember(maximum) { (0..maximum).toList() }
    LaunchedEffect(state) {
        snapshotFlow {
            val layout = state.layoutInfo
            val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            layout.visibleItemsInfo.minByOrNull { info -> abs(info.offset + info.size / 2 - center) }?.index
        }.distinctUntilChanged().collect { index ->
            if (index != null) onValueChange(index.coerceIn(0, maximum))
        }
    }
    LaunchedEffect(value) {
        val target = value.coerceIn(0, maximum)
        if (state.firstVisibleItemIndex != target || state.firstVisibleItemScrollOffset != 0) {
            state.animateScrollToItem(target)
        }
    }
    Box(
        modifier = Modifier.width(56.dp).height(144.dp),
        contentAlignment = Alignment.Center,
    ) {
        LazyColumn(
            state = state,
            flingBehavior = flingBehavior,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(values, key = { it }) { number ->
                Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                    Text(
                        number.toString().padStart(2, '0'),
                        modifier = Modifier,
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (number == value) TripInk else TripInk.copy(alpha = .30f),
                    )
                }
            }
        }
        Box(
            Modifier.fillMaxWidth().height(48.dp)
                .background(TripLake.copy(alpha = .10f), RoundedCornerShape(10.dp)),
        )
        Text(contentDescription, style = MaterialTheme.typography.labelSmall, color = TripInk.copy(alpha = .58f), modifier = Modifier.align(Alignment.BottomCenter).offset(y = 18.dp))
    }
}

private data class PlaceOpenOption(
    val platform: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val foreground: Color,
    val background: Color,
)

@Composable
fun OpenPlaceChooser(title: String, address: String, onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    val amapBlue = Color(0xFF1A73F2)
    val xiaohongshuRed = Color(0xFFFF2440)
    val options = listOf(
        PlaceOpenOption("高德地图", "高德地图导航", "打开 App 并规划路线", Icons.Default.NearMe, amapBlue, amapBlue.copy(alpha = .12f)),
        PlaceOpenOption("小红书", "小红书搜攻略", "搜索地点相关笔记", Icons.Default.MenuBook, xiaohongshuRed, xiaohongshuRed.copy(alpha = .11f)),
        PlaceOpenOption("抖音", "抖音搜攻略", "搜索地点相关视频", Icons.Default.MusicNote, Color.White, Color(0xFF14171F)),
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        dragHandle = { BottomSheetDefaults.DragHandle(width = 32.dp, height = 4.dp) },
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            options.forEach { option ->
                Surface(
                    onClick = { onDismiss(); onOpen(option.platform) },
                    modifier = Modifier.fillMaxWidth().semantics {
                        contentDescription = "${option.title}，${title.ifBlank { "未命名地点" }}${address.takeIf { it.isNotBlank() }?.let { "，$it" }.orEmpty()}"
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = option.background,
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(option.icon, null, tint = option.foreground, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(option.title, style = MaterialTheme.typography.titleMedium, color = option.foreground)
                            Text(option.subtitle, style = MaterialTheme.typography.bodySmall, color = option.foreground.copy(alpha = .78f))
                        }
                        Spacer(Modifier.width(12.dp))
                        Icon(Icons.Default.NorthEast, null, tint = option.foreground, modifier = Modifier.size(19.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun TripDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
        border = BorderStroke(.8.dp, TripMist.copy(alpha = .55f)),
        content = content,
    )
}

@Composable
fun TripSectionSurface(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surface, shadowElevation: Dp = 1.dp, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier, shape = RoundedCornerShape(22.dp), color = color,
        border = androidx.compose.foundation.BorderStroke(.8.dp, TripMist.copy(alpha = .38f)), shadowElevation = shadowElevation,
    ) { Column(Modifier.fillMaxWidth().padding(16.dp), content = content) }
}

fun PlaceCategory.icon(): ImageVector = when (this) {
    PlaceCategory.ATTRACTION -> Icons.Default.PhotoCamera
    PlaceCategory.RESTAURANT -> Icons.Default.Restaurant
    PlaceCategory.HOTEL -> Icons.Default.Hotel
    PlaceCategory.TRANSPORT -> Icons.Default.DirectionsCar
    PlaceCategory.SPECIAL -> Icons.Default.Star
    PlaceCategory.OTHER -> Icons.Default.MoreHoriz
}

/** Spacious editor with a stationary header and actions above the keyboard. */
@Composable
fun TripEditorSheet(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
        val colors = MaterialTheme.colorScheme
        MaterialTheme(colorScheme = colors.copy(
            background = if (androidx.compose.foundation.isSystemInDarkTheme()) colors.background else TripCanvas,
            surface = if (androidx.compose.foundation.isSystemInDarkTheme()) colors.surface else Color.White,
            onSurface = if (androidx.compose.foundation.isSystemInDarkTheme()) colors.onSurface else Color(0xFF1C1C1E),
            onBackground = if (androidx.compose.foundation.isSystemInDarkTheme()) colors.onBackground else Color(0xFF1C1C1E),
            onSurfaceVariant = if (androidx.compose.foundation.isSystemInDarkTheme()) colors.onSurfaceVariant else Color(0xFF86868B),
        )) {
            Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.BottomCenter) {
                Surface(modifier = Modifier.fillMaxWidth().fillMaxHeight(.97f), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), color = MaterialTheme.colorScheme.background) {
                    Scaffold(modifier = Modifier.dismissKeyboardOnBlankTap(), containerColor = MaterialTheme.colorScheme.background,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        topBar = {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) { dismissButton() }
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    androidx.compose.runtime.CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.titleMedium) { title() }
                                }
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) { confirmButton() }
                            }
                        },
                    ) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).navigationBarsPadding().padding(horizontal = 16.dp)) { text() }
                    }
                }
            }
        }
    }
}

@Composable
fun TripEditorSection(title: String) {
    Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 6.dp))
}

@Composable
fun TripEditorGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
        androidx.compose.runtime.CompositionLocalProvider(LocalGroupedEditor provides true) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), content = content)
        }
    }
}

@Composable
fun TripEditorDivider() { HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .09f)) }

@Composable
fun TripCategoryPicker(selected: PlaceCategory, onSelect: (PlaceCategory) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.fillMaxWidth().clickable { expanded = true }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("类型", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Icon(if (selected == PlaceCategory.TRANSPORT) Icons.Default.DirectionsCar else selected.icon(), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            Text(selected.label, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp))
            Icon(Icons.Default.UnfoldMore, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        }
        TripDropdownMenu(expanded, { expanded = false }) {
            PlaceCategory.entries.forEach { category ->
                DropdownMenuItem(text = { Text(category.label) }, onClick = { onSelect(category); expanded = false },
                    leadingIcon = { Icon(category.icon(), null) }, trailingIcon = { if (selected == category) Icon(Icons.Default.Check, null) })
            }
        }
    }
}

@Composable
fun TripLocationModePicker(selected: com.personal.triptrail.data.ArrangementLocationMode, onSelect: (com.personal.triptrail.data.ArrangementLocationMode) -> Unit) {
    // Keep the hit area generous without Material Surface adding a 48 dp visual gutter.
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
        Row(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .07f)).padding(2.dp).selectableGroup()) {
            com.personal.triptrail.data.ArrangementLocationMode.entries.forEach { mode ->
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp))
                    .background(if (mode == selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .selectable(selected = mode == selected, role = Role.Tab, onClick = { onSelect(mode) }),
                    contentAlignment = Alignment.Center) {
                    Text(mode.label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun TripToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val offset by androidx.compose.animation.core.animateDpAsState(if (checked) 22.dp else 2.dp, label = "toggleThumb")
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Normal)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(width = 51.dp, height = 31.dp).clip(CircleShape).background(if (checked) MaterialTheme.colorScheme.primary else Color(0xFFC7C7CC))) {
            Box(Modifier.offset(x = offset, y = 2.dp).size(27.dp).background(Color.White, CircleShape))
        }
    }
}


@Composable
fun TripCostField(value: String, onValueChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("¥", color = MaterialTheme.colorScheme.onSurfaceVariant)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).semantics { contentDescription = "花费" },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text("输入金额", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .55f))
                    inner()
                }
            },
        )
    }
}
