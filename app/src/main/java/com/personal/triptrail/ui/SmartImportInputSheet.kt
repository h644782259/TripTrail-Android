@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.personal.triptrail.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.personal.triptrail.util.SystemImagePickerContract
import kotlinx.coroutines.launch

@Composable
internal fun SmartImportInputSheet(
    placeholder: String,
    maxImages: Int = 6,
    onDismiss: () -> Unit,
    recognizing: Boolean = false,
    extraContent: @Composable () -> Unit = {},
    onSubmit: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var imageMode by remember { mutableStateOf(false) }
    var images by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    var reading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val busy = reading || recognizing
    val picker = rememberLauncherForActivityResult(SystemImagePickerContract(multiple = maxImages > 1, maxSelectionCount = maxImages)) { selected ->
        if (selected.isNotEmpty()) { images = (images + selected).distinct().take(maxImages); error = null }
    }
    TripEditorSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("智能录入") },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") } },
        confirmButton = { Spacer(Modifier.width(64.dp)) },
        text = {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TripEditorGroup {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                            listOf("文字", "截图").forEachIndexed { index, label ->
                                SegmentedButton(
                                    selected = imageMode == (index == 1),
                                    onClick = { imageMode = index == 1; error = null },
                                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                                    enabled = !busy,
                                ) { Text(label) }
                            }
                        }
                    }
                    TripEditorSection(if (imageMode) "安排截图" else "安排内容")
                    TripEditorGroup {
                        if (imageMode) {
                            if (images.isEmpty()) Text(placeholder.replace("粘贴", "上传"), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val cells: List<android.net.Uri?> = images + if (images.size < maxImages) listOf(null) else emptyList()
                            val accent = MaterialTheme.colorScheme.primary
                            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                cells.chunked(3).forEach { row ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        row.forEach { uri ->
                                            Box(Modifier.weight(1f).aspectRatio(1f)) {
                                                if (uri == null) {
                                                    Box(Modifier.fillMaxSize().background(accent.copy(alpha = .045f), RoundedCornerShape(12.dp)).drawBehind {
                                                        drawRoundRect(accent.copy(alpha = .52f), cornerRadius = CornerRadius(12.dp.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))))
                                                    }.clickable(enabled = !busy, onClickLabel = "添加截图") { picker.launch(Unit) }, contentAlignment = Alignment.Center) {
                                                        Icon(Icons.Default.Add, null, tint = accent)
                                                    }
                                                } else {
                                                    val bitmap by produceState<android.graphics.Bitmap?>(null, uri) {
                                                        value = withContext(Dispatchers.IO) {
                                                            runCatching { context.contentResolver.openInputStream(uri)?.use { stream ->
                                                                android.graphics.BitmapFactory.decodeStream(stream, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 })
                                                            } }.getOrNull()
                                                        }
                                                    }
                                                    bitmap?.let { Image(it.asImageBitmap(), "截图", Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
                                                    IconButton(onClick = { images = images.filterNot { it == uri } }, enabled = !busy, modifier = Modifier.align(Alignment.TopEnd).size(36.dp)) {
                                                        Icon(Icons.Default.Close, "移除截图", Modifier.size(20.dp).background(Color.Black.copy(alpha = .55f), CircleShape).padding(3.dp), tint = Color.White)
                                                    }
                                                }
                                            }
                                        }
                                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        } else {
                            TextField(
                                value = text, onValueChange = { text = it }, enabled = !busy,
                                placeholder = { Text(placeholder) }, modifier = Modifier.fillMaxWidth().height(220.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent,
                                ),
                            )
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    extraContent()
                }
                Button(
                    onClick = {
                        if (!imageMode) onSubmit(text) else scope.launch {
                            reading = true; error = null
                            try {
                                val extracted = recognizeScreenshotText(context, images)
                                if (extracted.isBlank()) error = "没有识别到截图文字，请重新选择" else onSubmit(extracted)
                            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (failure: Exception) { error = failure.localizedMessage ?: "截图读取失败" }
                            finally { reading = false }
                        }
                    },
                    enabled = !busy && if (imageMode) images.isNotEmpty() else text.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).height(50.dp), shape = RoundedCornerShape(16.dp),
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text(if (busy) "识别中…" else "开始识别")
                }
            }
        },
    )
}
