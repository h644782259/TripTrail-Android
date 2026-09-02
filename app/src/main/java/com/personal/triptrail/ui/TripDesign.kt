@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.personal.triptrail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.personal.triptrail.data.dateText
import com.personal.triptrail.data.localDate
import com.personal.triptrail.data.timeText
import com.personal.triptrail.data.PlaceCategory
import com.personal.triptrail.data.combineDateAndTime
import java.time.LocalDate
import java.time.ZoneId

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
        ) { DatePicker(state = state, showModeToggle = false) }
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
        val state = rememberTimePickerState(initialHour = zoned.hour, initialMinute = zoned.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showing = false },
            title = { Text(label) },
            text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state) } },
            confirmButton = {
                TextButton(onClick = {
                    onValueChange(local.atTime(state.hour, state.minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
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
    OutlinedButton(onClick = { showing = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp)) {
        Icon(Icons.Default.DateRange, null, tint = TripLakeText); Spacer(Modifier.width(8.dp))
        Text("${start.dateText()} — ${end.dateText()}", Modifier.weight(1f))
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
            DateRangePicker(state = state, showModeToggle = false)
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
        val startState = rememberTimePickerState(startZone.hour, startZone.minute, true)
        val endState = rememberTimePickerState(endZone.hour, endZone.minute, true)
        AlertDialog(onDismissRequest = { showing = false }, title = { Text("选择起止时间") },
            text = { Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("开始时间", style = MaterialTheme.typography.labelLarge, color = TripLakeText); TimeInput(startState)
                Spacer(Modifier.height(8.dp)); Text("结束时间", style = MaterialTheme.typography.labelLarge, color = TripLakeText); TimeInput(endState)
            } }, confirmButton = { TextButton(onClick = {
                val date = start.localDate(); val s = date.atTime(startState.hour, startState.minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                var e = date.atTime(endState.hour, endState.minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                if (e <= s) e += 86_400_000L
                onChange(s, e); showing = false
            }) { Text("确定") } }, dismissButton = { TextButton(onClick = { showing = false }) { Text("取消") } })
    }
}

@Composable
fun OpenPlaceChooser(title: String, address: String, onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("打开地点") }, text = { Text("$title\n$address", color = MaterialTheme.colorScheme.onSurfaceVariant) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }, confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { onOpen("高德地图") }) { Text("高德地图") }
                TextButton(onClick = { onOpen("小红书") }) { Text("小红书") }
                TextButton(onClick = { onOpen("抖音") }) { Text("抖音") }
            }
        })
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
