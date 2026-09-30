package com.personal.triptrail.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Reveals actions only; swiping never deletes data. Vertical scrolling keeps its gesture. */
@Composable
internal fun CardSwipeActions(
    modifier: Modifier = Modifier,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val width = with(LocalDensity.current) { 148.dp.toPx() }
    var offset by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf(false) }
    val animated by animateFloatAsState(offset, tween(if (dragging) 0 else 180), label = "cardActions")
    fun close() { opened = false; offset = 0f }
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).semantics {
        customActions = listOf(
            CustomAccessibilityAction("编辑安排") { close(); onEdit(); true },
            CustomAccessibilityAction("删除安排") { close(); onDelete(); true },
        )
    }) {
        Box(Modifier.offset { IntOffset(animated.roundToInt(), 0) }
            .pointerInput(width) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false; opened = offset < -width / 3; offset = if (opened) -width else 0f },
                    onDragCancel = { dragging = false; offset = if (opened) -width else 0f },
                ) { change, amount ->
                    change.consume()
                    offset = (offset + amount).coerceIn(-width, 0f)
                }
            }) { content() }
        if (animated < -1f) {
            // Overlay only the exposed part, leaving the translated card fully usable.
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
                Row(Modifier.width(with(LocalDensity.current) { (-animated).toDp() }).fillMaxHeight().clip(RoundedCornerShape(0.dp))) {
                    Column(Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.primary)
                        .clickable { close(); onEdit() }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.Edit, null, tint = Color.White)
                        Spacer(Modifier.height(6.dp))
                        Text("编辑", color = Color.White, maxLines = 1)
                    }
                    Column(Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.error)
                        .clickable { close(); onDelete() }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.DeleteOutline, null, tint = Color.White)
                        Spacer(Modifier.height(6.dp))
                        Text("删除", color = Color.White, maxLines = 1)
                    }
                }
            }
        }
    }
}
