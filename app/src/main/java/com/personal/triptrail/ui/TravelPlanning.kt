package com.personal.triptrail.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun LocationCopyButton(text: String) {
    val context = LocalContext.current
    var copied by remember(text) { mutableStateOf(false) }
    var copyCount by remember(text) { mutableIntStateOf(0) }
    IconButton(onClick = {
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("地点", text))
        copied = true; copyCount++
        android.widget.Toast.makeText(context, "已复制", android.widget.Toast.LENGTH_SHORT).show()
    }, modifier = Modifier.size(32.dp)) {
        Icon(if (copied) Icons.Default.Check else Icons.Default.ContentCopy, if (copied) "已复制" else "复制$text", tint = TripLakeText, modifier = Modifier.size(16.dp))
    }
    LaunchedEffect(copyCount) {
        if (copyCount > 0) { kotlinx.coroutines.delay(1500); copied = false }
    }
}
