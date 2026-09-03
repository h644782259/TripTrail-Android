@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.personal.triptrail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
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

enum class RootTab(val label: String, val icon: ImageVector) {
    TRIPS("旅程", Icons.Default.Map),
    STORIES("足迹", Icons.Default.MenuBook),
    FAVORITES("收藏", Icons.Default.Favorite),
    SETTINGS("我的", Icons.Default.AccountCircle),
}

@Composable
fun TripBottomBar(selected: RootTab, onSelect: (RootTab) -> Unit) {
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(32.dp),
            color = TripSurface.copy(alpha = .97f),
            shadowElevation = 10.dp,
            tonalElevation = 0.dp,
        ) {
            Row(Modifier.fillMaxSize().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                RootTab.entries.forEach { tab ->
                    val active = tab == selected
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(26.dp))
                            .background(if (active) TripLake.copy(alpha = .13f) else Color.Transparent)
                            .clickable { onSelect(tab) },
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(tab.icon, contentDescription = tab.label, modifier = Modifier.size(22.dp), tint = if (active) TripLakeText else Color(0xFF171C19))
                        Text(tab.label, style = MaterialTheme.typography.labelSmall, color = if (active) TripLakeText else Color(0xFF171C19), fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
fun TripRoundAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(modifier = Modifier.size(44.dp), shape = CircleShape, color = TripSurface.copy(alpha = .96f), shadowElevation = 7.dp, onClick = onClick) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, tint = TripLakeText, modifier = Modifier.size(23.dp)) }
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
            focusedContainerColor = TripSurface, unfocusedContainerColor = TripSurface, disabledContainerColor = TripSurface,
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
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        minLines = minLines,
        singleLine = singleLine,
        readOnly = readOnly,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(17.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = TripSurface,
            unfocusedContainerColor = TripSurface,
            focusedBorderColor = TripLake,
            unfocusedBorderColor = TripMist.copy(alpha = .72f),
            focusedLabelColor = TripLakeText,
            cursorColor = TripLakeText,
        ),
    )
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
            leadingIcon = { Icon(Icons.Default.CalendarMonth, null, tint = TripLakeText) },
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
                    Text(label, Modifier.fillMaxWidth().padding(horizontal = 24.dp), style = MaterialTheme.typography.labelLarge, color = TripLakeText)
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
            leadingIcon = { Icon(Icons.Default.Schedule, null, tint = TripLakeText) },
        )
        Box(Modifier.matchParentSize().clickable { showing = true })
    }
    if (showing) {
        var hour by remember(value) { mutableStateOf(zoned.hour) }
        var minute by remember(value) { mutableStateOf(zoned.minute) }
        AlertDialog(
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
    OutlinedButton(
        onClick = { showing = true },
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        shape = RoundedCornerShape(17.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(Icons.Default.DateRange, null, tint = TripLakeText)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("日期范围", style = MaterialTheme.typography.labelSmall, color = TripLakeText)
            Text("${start.dateText()} — ${end.dateText()}", style = MaterialTheme.typography.bodyLarge, color = TripInk, maxLines = 1)
        }
        Icon(Icons.Default.KeyboardArrowDown, "选择日期范围", tint = TripLakeText)
    }
    if (showing) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = start.localDate().toEpochDay() * 86_400_000L,
            initialSelectedEndDateMillis = end.localDate().toEpochDay() * 86_400_000L,
        )
        DatePickerDialog(onDismissRequest = { showing = false }, confirmButton = {
            TextButton(onClick = {
                val s = state.selectedStartDateMillis; val e = state.selectedEndDateMillis ?: s
                if (s != null && e != null) onChange(
                    LocalDate.ofEpochDay(s / 86_400_000L).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    LocalDate.ofEpochDay(e / 86_400_000L).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                ); showing = false
            }) { Text("确定") }
        }, dismissButton = { TextButton(onClick = { showing = false }) { Text("取消") } }) {
            DateRangePicker(
                state = state,
                modifier = Modifier.padding(horizontal = 8.dp),
                showModeToggle = false,
                title = {
                    Text("选择日期范围", Modifier.fillMaxWidth().padding(horizontal = 24.dp), style = MaterialTheme.typography.labelLarge, color = TripLakeText)
                },
                headline = {
                    val selectedStart = state.selectedStartDateMillis?.let { LocalDate.ofEpochDay(it / 86_400_000L).let { date -> "${date.monthValue}月${date.dayOfMonth}日" } }
                    val selectedEnd = state.selectedEndDateMillis?.let { LocalDate.ofEpochDay(it / 86_400_000L).let { date -> "${date.monthValue}月${date.dayOfMonth}日" } }
                    val headlineText = when {
                        selectedStart != null && selectedEnd != null -> "$selectedStart — $selectedEnd"
                        selectedStart != null -> "$selectedStart — 请选择结束日期"
                        else -> "请选择开始日期"
                    }
                    Text(
                        headlineText,
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        style = MaterialTheme.typography.titleLarge,
                        color = TripInk,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1,
                    )
                },
                colors = DatePickerDefaults.colors(containerColor = TripSurface),
            )
        }
    }
}

@Composable
fun TripTimeRangeField(start: Long, end: Long, onChange: (Long, Long) -> Unit) {
    var showing by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { showing = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp)) {
        Icon(Icons.Default.Schedule, null, tint = TripLakeText); Spacer(Modifier.width(8.dp)); Text("${start.timeText()} — ${end.timeText()}")
    }
    if (showing) {
        val startZone = java.time.Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault())
        val endZone = java.time.Instant.ofEpochMilli(end).atZone(ZoneId.systemDefault())
        var startHour by remember(start) { mutableStateOf(startZone.hour) }
        var startMinute by remember(start) { mutableStateOf(startZone.minute) }
        var endHour by remember(end) { mutableStateOf(endZone.hour) }
        var endMinute by remember(end) { mutableStateOf(endZone.minute) }
        AlertDialog(onDismissRequest = { showing = false }, title = { Text("选择起止时间") },
            text = {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("开始时间", style = MaterialTheme.typography.labelLarge, color = TripLakeText)
                        WheelTimePicker(startHour, startMinute, { startHour = it }, { startMinute = it })
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("结束时间", style = MaterialTheme.typography.labelLarge, color = TripLakeText)
                        WheelTimePicker(endHour, endMinute, { endHour = it }, { endMinute = it })
                    }
                }
            }, confirmButton = { TextButton(onClick = {
                val date = start.localDate(); val s = date.atTime(startHour, startMinute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                var e = date.atTime(endHour, endMinute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                if (e <= s) e += 86_400_000L
                onChange(s, e); showing = false
            }) { Text("确定") } }, dismissButton = { TextButton(onClick = { showing = false }) { Text("取消") } })
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

@Composable
fun OpenPlaceChooser(title: String, address: String, onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    val options = listOf(
        Triple("高德地图", "地图中查看位置与路线", Icons.Default.Map),
        Triple("小红书", "搜索地点相关内容", Icons.Default.Search),
        Triple("抖音", "搜索地点相关视频", Icons.Default.PlayArrow),
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.widthIn(min = 280.dp, max = 360.dp),
            shape = RoundedCornerShape(28.dp),
            color = TripSurface,
            shadowElevation = 16.dp,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = TripLake.copy(alpha = .14f)) {
                        Icon(Icons.Default.Place, null, tint = TripLakeText, modifier = Modifier.padding(10.dp).size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("打开地点", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TripInk)
                        Text(title.ifBlank { "未命名地点" }, style = MaterialTheme.typography.bodyLarge, color = TripLakeText, maxLines = 1)
                    }
                }
                if (address.isNotBlank()) {
                    Text(address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                }
                options.forEach { (platform, detail, icon) ->
                    Surface(
                        onClick = { onOpen(platform) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = TripCanvas,
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, null, tint = TripLakeText, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(platform, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = TripInk)
                                Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = TripLakeText)
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("取消") }
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
        containerColor = TripSurface,
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
        border = BorderStroke(.8.dp, TripMist.copy(alpha = .55f)),
        content = content,
    )
}

@Composable
fun TripSectionSurface(modifier: Modifier = Modifier, color: Color = TripSurface, shadowElevation: Dp = 4.dp, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier, shape = RoundedCornerShape(22.dp), color = color,
        border = androidx.compose.foundation.BorderStroke(.8.dp, TripMist.copy(alpha = .38f)), shadowElevation = shadowElevation,
    ) { Column(Modifier.fillMaxWidth().padding(16.dp), content = content) }
}

fun PlaceCategory.icon(): ImageVector = when (this) {
    PlaceCategory.ATTRACTION -> Icons.Default.PhotoCamera
    PlaceCategory.RESTAURANT -> Icons.Default.Restaurant
    PlaceCategory.HOTEL -> Icons.Default.Hotel
    PlaceCategory.TRANSPORT -> Icons.Default.DirectionsTransit
    PlaceCategory.SPECIAL -> Icons.Default.Star
    PlaceCategory.OTHER -> Icons.Default.MoreHoriz
}
