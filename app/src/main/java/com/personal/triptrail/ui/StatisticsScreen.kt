@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.personal.triptrail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personal.triptrail.data.Trip
import com.personal.triptrail.data.chineseDateText
import com.personal.triptrail.data.timelineSorted
import java.util.Locale

@Composable
fun StatisticsScreen(trips: List<Trip>, modifier: Modifier = Modifier) {
    val ordered = trips.timelineSorted()
    var selectedId by remember(ordered.map { it.id }) { mutableStateOf(ordered.firstOrNull()?.id) }
    val trip = ordered.firstOrNull { it.id == selectedId } ?: ordered.firstOrNull()
    Scaffold(modifier = modifier, topBar = { TopAppBar(title = { Text("统计") }) }) { padding ->
        if (trip == null) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.BarChart, null, Modifier.size(52.dp), tint = Lake)
                Text("还没有可统计的旅程", style = MaterialTheme.typography.headlineSmall)
                Text("创建旅程并记录花费后，这里会按天展示。")
            }
        } else {
            val daily = trip.days.sortedBy { it.sortOrder }.map { day -> day to day.items.sumOf { it.cost } }
            val total = daily.sumOf { it.second }; val maximum = daily.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                var menu by remember { mutableStateOf(false) }
                Box { ElevatedCard(onClick = { menu = true }, shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Map, null, tint = Lake); Spacer(Modifier.width(10.dp)); Text(trip.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Text("切换") }
                }; DropdownMenu(menu, { menu = false }) { ordered.forEach { candidate -> DropdownMenuItem({ Text(candidate.title) }, onClick = { selectedId = candidate.id; menu = false }) } } }
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Ink)) {
                    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Ink, Lake))).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("旅程总花费", color = androidx.compose.ui.graphics.Color.White.copy(alpha = .8f))
                        Text("¥${String.format(Locale.CHINA, "%.2f", total)}", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        Text("${trip.startDate.chineseDateText()} — ${trip.endDate.chineseDateText()} · ${daily.size} 天", color = androidx.compose.ui.graphics.Color.White.copy(alpha = .75f))
                    }
                }
                ElevatedCard(shape = RoundedCornerShape(22.dp)) { Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("每日花费", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth().height(250.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
                        daily.forEach { (day, amount) -> Column(Modifier.width(58.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (amount > 0) Text(String.format(Locale.CHINA, "%.0f", amount), style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.width(34.dp).height((190 * amount / maximum).toInt().coerceAtLeast(3).dp).background(Lake, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)))
                            Spacer(Modifier.height(6.dp)); Text(day.date.chineseDateText(), style = MaterialTheme.typography.labelSmall)
                        } }
                    }
                } }
            }
        }
    }
}
