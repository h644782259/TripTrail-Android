@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.personal.triptrail.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personal.triptrail.data.Trip
import com.personal.triptrail.data.chineseDateText
import com.personal.triptrail.data.timelineSorted
import com.personal.triptrail.data.TripPhase
import com.personal.triptrail.data.phase
import java.util.Locale

@Composable
fun StatisticsScreen(trips: List<Trip>, modifier: Modifier = Modifier, onBack: () -> Unit) {
    val ordered = trips.timelineSorted()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val trip = ordered.firstOrNull { it.id == selectedId } ?: ordered.firstOrNull()
    var menu by remember { mutableStateOf(false) }
    Box(modifier.fillMaxSize().background(TripCanvas)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TripRoundAction(Icons.Default.ArrowBack, "返回", onBack); Text("统计", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center); Spacer(Modifier.width(48.dp))
            }
            if (trip == null) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(Icons.Default.BarChart, null, Modifier.size(52.dp), tint = TripLakeText); Spacer(Modifier.height(10.dp)); Text("还没有可统计的旅程", style = MaterialTheme.typography.headlineSmall); Text("创建旅程并记录花费后，这里会按天展示。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                val daily = trip.days.sortedBy { it.sortOrder }.map { day -> day to day.items.sumOf { it.cost } }
                val total = daily.sumOf { it.second }; val maximum = daily.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box { Surface(onClick = { menu = true }, shape = RoundedCornerShape(18.dp), color = TripSurface, border = androidx.compose.foundation.BorderStroke(.8.dp, TripMist.copy(alpha = .4f))) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(34.dp).background(TripLake.copy(alpha = .12f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Map, null, tint = TripLakeText, modifier = Modifier.size(20.dp)) }; Spacer(Modifier.width(10.dp)); Text(trip.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null, tint = Color.Gray) } } }
                    Surface(shape = RoundedCornerShape(24.dp), shadowElevation = 8.dp) { Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(TripInk, TripLake))).padding(20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { Row { Text("旅程总花费", color = Color.White.copy(alpha = .82f), fontWeight = FontWeight.SemiBold); Spacer(Modifier.weight(1f)); Surface(shape = RoundedCornerShape(14.dp), color = Color.White.copy(alpha = .14f)) { Text("${daily.size} 天", Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = Color.White, style = MaterialTheme.typography.labelMedium) } }; Text("¥${String.format(Locale.CHINA, "%.2f", total)}", color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold); Text("${trip.startDate.chineseDateText()} — ${trip.endDate.chineseDateText()}", color = Color.White.copy(alpha = .78f), style = MaterialTheme.typography.bodySmall) } }
                    TripSectionSurface { Text("每日花费", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(16.dp)); Row(Modifier.fillMaxWidth().height(250.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) { daily.forEach { (day, amount) -> Column(Modifier.width(58.dp), horizontalAlignment = Alignment.CenterHorizontally) { if (amount > 0) Text(String.format(Locale.CHINA, "%.0f", amount), style = MaterialTheme.typography.labelSmall); Spacer(Modifier.height(4.dp)); Box(Modifier.width(34.dp).height((190 * amount / maximum).toInt().coerceAtLeast(3).dp).background(Brush.verticalGradient(listOf(TripLake, TripLakeText)), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))); Spacer(Modifier.height(6.dp)); Text(day.date.chineseDateText(), style = MaterialTheme.typography.labelSmall) } } } }
                }
            }

        }
    }
    if (menu) StatisticsTripPicker(ordered, trip?.id, { menu = false }) { selectedId = it; menu = false }
}

@Composable
private fun StatisticsTripPicker(trips: List<Trip>, selectedId: String?, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    val matches = trips.filter { search.isBlank() || it.title.contains(search.trim(), true) || it.destination.contains(search.trim(), true) }
    val sections = listOf(TripPhase.CURRENT to "进行中", TripPhase.UPCOMING to "即将出发", TripPhase.HISTORY to "已结束")
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.75f).dismissKeyboardOnBlankTap()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Text("选择旅程", Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(64.dp))
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (matches.isEmpty()) item { Text("没有匹配的旅程", Modifier.fillMaxWidth().padding(vertical = 32.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                sections.forEach { (phase, label) ->
                    val group = matches.filter { it.phase() == phase }
                    if (group.isNotEmpty()) {
                        item(key = label) { Text(label, Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
                        items(group, key = { it.id }) { candidate ->
                            Surface(onClick = { onSelect(candidate.id) }, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(when (phase) { TripPhase.CURRENT -> Icons.Default.DirectionsWalk; TripPhase.UPCOMING -> Icons.Default.CalendarMonth; TripPhase.HISTORY -> Icons.Default.CheckCircleOutline }, null, tint = TripLakeText, modifier = Modifier.size(32.dp).background(TripLake.copy(alpha = .10f), RoundedCornerShape(9.dp)).padding(5.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(candidate.title, style = MaterialTheme.typography.titleMedium)
                                        Text(listOf(candidate.destination, "${candidate.startDate.chineseDateText()} — ${candidate.endDate.chineseDateText()}").filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (candidate.id == selectedId) { Spacer(Modifier.width(8.dp)); Icon(Icons.Default.CheckCircle, "已选择", tint = TripLakeText, modifier = Modifier.size(20.dp)) }
                                }
                            }
                        }
                    }
                }
            }
            TripSearchField(search, "搜索旅程名称或目的地", { search = it }, Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        }
    }
}
