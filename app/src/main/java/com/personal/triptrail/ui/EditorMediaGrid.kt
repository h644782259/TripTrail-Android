package com.personal.triptrail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.personal.triptrail.data.MediaReference

@Composable
internal fun EditorMediaGrid(media: List<MediaReference>, limit: Int, onAdd: () -> Unit, onRemove: (String) -> Unit) {
    val slots: List<MediaReference?> = media.sortedBy { it.sortOrder } + if (media.size < limit) listOf(null) else emptyList()
    val accent = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        slots.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { reference ->
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (reference == null) {
                            Box(Modifier.fillMaxSize().background(accent.copy(alpha = .045f), RoundedCornerShape(12.dp)).drawBehind {
                                drawRoundRect(accent.copy(alpha = .52f), cornerRadius = CornerRadius(12.dp.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))))
                            }.clickable(onClickLabel = "添加照片或视频", onClick = onAdd), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Add, null, tint = accent)
                            }
                        } else {
                            MediaThumbnail(reference, Modifier.fillMaxSize(), cornerRadius = 12.dp)
                            IconButton(onClick = { onRemove(reference.id) }, modifier = Modifier.align(Alignment.TopEnd).size(36.dp)) {
                                Icon(Icons.Default.Close, "移除这项素材", Modifier.size(20.dp).background(Color.Black.copy(alpha = .55f), CircleShape).padding(3.dp), tint = Color.White)
                            }
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
