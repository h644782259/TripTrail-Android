@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.personal.triptrail.ui

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
    var search by rememberSaveable { mutableStateOf("") }
    var phaseFilter by rememberSaveable { mutableStateOf<TripPhase?>(null) }
    val ordered = trips.timelineSorted().filter { (phaseFilter == null || it.phase() == phaseFilter) && (search.isBlank() || it.title.contains(search, true) || it.destination.contains(search, true)) }
    var selectedId by remember(ordered.map { it.id }) { mutableStateOf(ordered.firstOrNull()?.id) }
    val trip = ordered.firstOrNull { it.id == selectedId } ?: ordered.firstOrNull()
    var menu by remember { mutableStateOf(false) }
    Box(modifier.fillMaxSize().background(TripCanvas)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TripRoundAction(Icons.Default.ArrowBack, "返回", onBack); Text("统计", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center); Spacer(Modifier.width(48.dp))
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(phaseFilter == null, { phaseFilter = null }, { Text("全部") })
                FilterChip(phaseFilter == TripPhase.CURRENT, { phaseFilter = TripPhase.CURRENT }, { Text("进行中") })
                FilterChip(phaseFilter == TripPhase.UPCOMING, { phaseFilter = TripPhase.UPCOMING }, { Text("待出发") })
                FilterChip(phaseFilter == TripPhase.HISTORY, { phaseFilter = TripPhase.HISTORY }, { Text("已结束") })
            }
            if (trip == null) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(Icons.Default.BarChart, null, Modifier.size(52.dp), tint = TripLakeText); Spacer(Modifier.height(10.dp)); Text("还没有可统计的旅程", style = MaterialTheme.typography.headlineSmall); Text("创建旅程并记录花费后，这里会按天展示。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                val daily = trip.days.sortedBy { it.sortOrder }.map { day -> day to day.items.sumOf { it.cost } }
                val total = daily.sumOf { it.second }; val maximum = daily.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box { Surface(onClick = { menu = true }, shape = RoundedCornerShape(18.dp), color = TripSurface, border = androidx.compose.foundation.BorderStroke(.8.dp, TripMist.copy(alpha = .4f))) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(34.dp).background(TripLake.copy(alpha = .12f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Map, null, tint = TripLakeText, modifier = Modifier.size(20.dp)) }; Spacer(Modifier.width(10.dp)); Text(trip.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null, tint = Color.Gray) } }; TripDropdownMenu(menu, { menu = false }) { ordered.forEach { candidate -> DropdownMenuItem({ Text(candidate.title) }, { selectedId = candidate.id; menu = false }, leadingIcon = { if (candidate.id == selectedId) Icon(Icons.Default.CheckCircle, null, tint = TripLakeText) }) } } }
                    Surface(shape = RoundedCornerShape(24.dp), shadowElevation = 8.dp) { Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(TripInk, TripLake))).padding(20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { Row { Text("旅程总花费", color = Color.White.copy(alpha = .82f), fontWeight = FontWeight.SemiBold); Spacer(Modifier.weight(1f)); Surface(shape = RoundedCornerShape(14.dp), color = Color.White.copy(alpha = .14f)) { Text("${daily.size} 天", Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = Color.White, style = MaterialTheme.typography.labelMedium) } }; Text("¥${String.format(Locale.CHINA, "%.2f", total)}", color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold); Text("${trip.startDate.chineseDateText()} — ${trip.endDate.chineseDateText()}", color = Color.White.copy(alpha = .78f), style = MaterialTheme.typography.bodySmall) } }
                    TripSectionSurface { Text("每日花费", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(16.dp)); Row(Modifier.fillMaxWidth().height(250.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) { daily.forEach { (day, amount) -> Column(Modifier.width(58.dp), horizontalAlignment = Alignment.CenterHorizontally) { if (amount > 0) Text(String.format(Locale.CHINA, "%.0f", amount), style = MaterialTheme.typography.labelSmall); Spacer(Modifier.height(4.dp)); Box(Modifier.width(34.dp).height((190 * amount / maximum).toInt().coerceAtLeast(3).dp).background(Brush.verticalGradient(listOf(TripLake, TripLakeText)), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))); Spacer(Modifier.height(6.dp)); Text(day.date.chineseDateText(), style = MaterialTheme.typography.labelSmall) } } } }
                }
            }
            TripSearchField(search, "搜索旅程或目的地", { search = it }, Modifier.padding(16.dp))
        }
    }
}
