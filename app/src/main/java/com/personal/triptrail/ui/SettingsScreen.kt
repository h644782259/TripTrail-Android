@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.personal.triptrail.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personal.triptrail.data.AppData
import com.personal.triptrail.data.TripRepository
import com.personal.triptrail.util.TripFileService
import com.personal.triptrail.util.TripBackupService

@Composable
fun SettingsScreen(repository: TripRepository, data: AppData, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<AppData?>(null) }
    var pendingShared by remember { mutableStateOf<Pair<com.personal.triptrail.data.Trip?, com.personal.triptrail.data.TravelStory?>?>(null) }
    var creator by remember { mutableStateOf(false) }
    val backupExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.triptrail.backup")) { uri ->
        if (uri != null) runCatching { context.contentResolver.openOutputStream(uri)?.use { TripBackupService(context).write(data, it) } }
            .onSuccess { message = "完整备份已导出，请保存到安全位置。" }.onFailure { message = "导出失败：${it.localizedMessage}" }
    }
    val backupImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { context.contentResolver.openInputStream(uri)?.use { TripBackupService(context).read(it) } ?: error("无法打开文件") }
            .onSuccess { pendingRestore = it }.onFailure { message = "无法读取备份：${it.localizedMessage}" }
    }
    val sharedImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty() }
            .mapCatching(TripFileService::importShared).onSuccess { pendingShared = it }.onFailure { message = "无法读取分享文件：${it.localizedMessage}" }
    }

    Scaffold(modifier = modifier, topBar = { TopAppBar(title = { Text("我的") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { SettingsSection("照片与视频") {
                ListItem(headlineContent = { Text("Android 系统照片选择器") }, supportingContent = { Text("仅在你主动选择时读取；选中的媒体会复制到 App 本地空间，方便稳定展示和备份。") }, leadingContent = { Icon(Icons.Default.PhotoLibrary, null, tint = Lake) })
            } }
            item { SettingsSection("开始体验") {
                ListItem(headlineContent = { Text("添加杭州示例旅程") }, supportingContent = { Text(if (data.trips.isEmpty()) "快速体验旅程、安排和统计" else "已有旅程时不会重复添加") },
                    leadingContent = { Icon(Icons.Default.AutoAwesome, null, tint = Lake) }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { repository.addSampleData(); message = if (data.trips.isEmpty()) "示例旅程已添加。" else "已有旅程，未重复添加示例。" }, modifier = Modifier.fillMaxWidth()) { Text("添加示例") }
            } }
            item { SettingsSection("数据备份与换机") {
                SettingsAction(Icons.Default.UploadFile, "导出完整备份", "包含旅行数据及照片、视频原件") { backupExporter.launch("TripTrail-Backup.triptrailbackup") }
                HorizontalDivider()
                SettingsAction(Icons.Default.Download, "从备份恢复", "恢复前预览数量，并二次确认替换") { backupImporter.launch(arrayOf("application/vnd.triptrail.backup", "application/json", "text/plain", "*/*")) }
            } }
            item { SettingsSection("接收分享") {
                SettingsAction(Icons.Default.BookmarkAdd, "收藏别人分享的旅程或足迹", "支持 iPhone 旅迹导出的无媒体 .triptrail 文件") { sharedImporter.launch(arrayOf("application/vnd.triptrail.journey", "application/json", "text/plain", "*/*")) }
            } }
            item { SettingsSection("数据与隐私") {
                ListItem(headlineContent = { Text("旅行数据仅保存在本机") }, leadingContent = { Icon(Icons.Default.Lock, null, tint = Lake) })
                ListItem(headlineContent = { Text("卸载 App 会清除本地数据") }, supportingContent = { Text("换机或卸载前请先导出完整备份。") }, leadingContent = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) })
            } }
            item { SettingsSection("关于") {
                ListItem(headlineContent = { Text("App") }, trailingContent = { Text("旅迹") })
                ListItem(headlineContent = { Text("创作者") }, trailingContent = { Text("黄逸轩") }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { creator = true }, modifier = Modifier.fillMaxWidth()) { Text("查看创作者") }
                ListItem(headlineContent = { Text("版本") }, trailingContent = { Text("0.1.0 MVP") })
                ListItem(headlineContent = { Text("系统要求") }, trailingContent = { Text("Android 8.0+") })
            } }
        }
    }
    pendingRestore?.let { restored -> AlertDialog(onDismissRequest = { pendingRestore = null }, title = { Text("恢复这份备份？") },
        text = { Text("备份包含 ${restored.trips.size} 段旅程、${restored.stories.size} 个足迹。恢复后将替换本机当前所有数据，此操作不可撤销。") },
        dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("取消") } }, confirmButton = { Button(onClick = { repository.replaceAll(restored); pendingRestore = null; message = "恢复完成。" }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("替换本机数据") } }) }
    pendingShared?.let { (trip, story) -> AlertDialog(onDismissRequest = { pendingShared = null }, title = { Text("收藏这份内容？") },
        text = { Text("“${trip?.title ?: story?.title}”会追加为独立副本，不会覆盖已有内容。") }, dismissButton = { TextButton(onClick = { pendingShared = null }) { Text("取消") } },
        confirmButton = { Button(onClick = {
            val current = repository.data.value
            if (trip != null && current.trips.none { it.id == trip.id }) repository.replaceAll(current.copy(trips = current.trips + trip))
            if (story != null && current.stories.none { it.id == story.id }) repository.replaceAll(repository.data.value.copy(stories = repository.data.value.stories + story))
            pendingShared = null; message = "已添加到我的旅迹。"
        }) { Text("添加") } }) }
    if (creator) AlertDialog(onDismissRequest = { creator = false }, icon = { Icon(Icons.Default.Favorite, null, tint = Coral) }, title = { Text("黄逸轩") },
        text = { Text("感谢使用旅迹。愿每一次出发都有期待，每一段回忆都有地方安放。") }, confirmButton = { TextButton(onClick = { creator = false }) { Text("完成") } })
    message?.let { AlertDialog(onDismissRequest = { message = null }, title = { Text("提示") }, text = { Text(it) }, confirmButton = { TextButton(onClick = { message = null }) { Text("好") } }) }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = Lake, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
        ElevatedCard(shape = RoundedCornerShape(20.dp)) { Column(Modifier.fillMaxWidth().padding(10.dp), content = content) }
    }
}

@Composable
private fun SettingsAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, action: () -> Unit) {
    TextButton(onClick = action, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(8.dp)) {
        Icon(icon, null); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }; Icon(Icons.Default.ChevronRight, null)
    }
}
