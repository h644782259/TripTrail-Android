package com.personal.triptrail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personal.triptrail.data.PlaceCategory

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
        value = value, onValueChange = onValueChange, modifier = modifier.fillMaxWidth().height(46.dp),
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(21.dp)) }, singleLine = true,
        shape = RoundedCornerShape(26.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = TripSurface, unfocusedContainerColor = TripSurface, disabledContainerColor = TripSurface,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
fun TripSectionSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier, shape = RoundedCornerShape(22.dp), color = TripSurface,
        border = androidx.compose.foundation.BorderStroke(.8.dp, TripMist.copy(alpha = .38f)), shadowElevation = 4.dp,
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
