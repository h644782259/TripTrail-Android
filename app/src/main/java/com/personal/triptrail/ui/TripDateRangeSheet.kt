@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.personal.triptrail.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.personal.triptrail.data.localDate
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@Composable
fun TripDateRangeSheet(start: Long, end: Long, onDismiss: () -> Unit, onConfirm: (Long, Long) -> Unit) {
    var first by remember(start) { mutableStateOf(start.localDate()) }
    var last by remember(end) { mutableStateOf<LocalDate?>(end.localDate().coerceAtLeast(first)) }
    var month by remember(start) { mutableStateOf(YearMonth.from(first)) }
    val today = LocalDate.now()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(width = 32.dp, height = 4.dp) },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text("旅行日期", style = MaterialTheme.typography.titleMedium) }
                TextButton(onClick = {
                    val zone = ZoneId.systemDefault()
                    onConfirm(first.atStartOfDay(zone).toInstant().toEpochMilli(), (last ?: first).atStartOfDay(zone).toInstant().toEpochMilli())
                }) { Text("完成") }
            }
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.Default.ChevronLeft, "上个月") }
                    Text("${month.year}年${month.monthValue}月", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.Default.ChevronRight, "下个月") }
                }
                Row(Modifier.fillMaxWidth()) {
                    listOf("日", "一", "二", "三", "四", "五", "六").forEach { day ->
                        Box(Modifier.weight(1f).height(32.dp), contentAlignment = Alignment.Center) { Text(day, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                val offset = month.atDay(1).dayOfWeek.value % 7
                // Always reserve six weeks so switching months does not resize the sheet.
                repeat(6) { week ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(7) { weekday ->
                            val number = week * 7 + weekday - offset + 1
                            Box(Modifier.weight(1f).height(46.dp), contentAlignment = Alignment.Center) {
                                if (number in 1..month.lengthOfMonth()) {
                                    val date = month.atDay(number)
                                    val endpoint = date == first || date == last
                                    val within = last?.let { date > first && date < it } == true
                                    Surface(
                                        onClick = { if (last != null || date < first) { first = date; last = null } else last = date },
                                        modifier = Modifier.fillMaxWidth().height(40.dp).semantics {
                                            contentDescription = "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
                                            selected = endpoint || within
                                        },
                                        shape = if (endpoint) CircleShape else RoundedCornerShape(9.dp),
                                        color = if (endpoint) TripLake else if (within) TripLake.copy(alpha = .15f) else Color.Transparent,
                                        border = if (date == today && !endpoint) BorderStroke(1.dp, TripLake.copy(alpha = .6f)) else null,
                                    ) {
                                        Box(contentAlignment = Alignment.Center) { Text(number.toString(), color = if (endpoint) Color.White else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium) }
                                    }
                                }
                            }
                        }
                    }
                }
                Text(if (last == null) "请选择返程日期，或直接完成以选择当天" else "${first.monthValue}月${first.dayOfMonth}日 — ${last!!.monthValue}月${last!!.dayOfMonth}日", modifier = Modifier.fillMaxWidth().padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}
