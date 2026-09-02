@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.personal.triptrail.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.personal.triptrail.data.AppData
import com.personal.triptrail.data.TripRepository
import com.personal.triptrail.util.SecureRecognitionSettings
import com.personal.triptrail.util.TripBackupService
import com.personal.triptrail.util.TripFileService

@Composable
fun SettingsScreen(repository: TripRepository, data: AppData, modifier: Modifier = Modifier, onOpenStatistics: () -> Unit) {
    val context = LocalContext.current
    val recognitionSettings = remember { SecureRecognitionSettings(context) }
    var smartEnabled by remember { mutableStateOf(recognitionSettings.enabled) }
    var apiKey by remember { mutableStateOf(recognitionSettings.apiKey) }
    var revealKey by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<AppData?>(null) }
    var pendingShared by remember { mutableStateOf<Pair<com.personal.triptrail.data.Trip?, com.personal.triptrail.data.TravelStory?>?>(null) }
    var creator by remember { mutableStateOf(false) }
    val backupExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.triptrail.backup")) { uri ->
        if (uri != null) runCatching { context.contentResolver.openOutputStream(uri)?.use { TripBackupService(context).write(data, it) } }.onSuccess { message = "完整备份已导出，请保存到安全位置。" }.onFailure { message = "导出失败：${it.localizedMessage}" }
    }
    val backupImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { context.contentResolver.openInputStream(uri)?.use { TripBackupService(context).read(it) } ?: error("无法打开文件") }.onSuccess { pendingRestore = it }.onFailure { message = "无法读取备份：${it.localizedMessage}" }
    }
    val sharedImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty() }.mapCatching(TripFileService::importShared).onSuccess { pendingShared = it }.onFailure { message = "无法读取分享文件：${it.localizedMessage}" }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(Color(0xFFF4F3F7)),
        contentPadding = PaddingValues(16.dp, 18.dp, 16.dp, 112.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { SettingsGroup("开始体验") { SettingsRow(Icons.Default.AutoAwesome, "添加示例旅程", tint = TripLakeText) { repository.addSampleData(); message = if (data.trips.isEmpty()) "示例旅程已添加。" else "已有旅程，未重复添加示例。" } } }
        item { SettingsGroup("旅行概览") { SettingsRow(Icons.Default.BarChart, "旅行统计", tint = TripLakeText, trailing = { Icon(Icons.Default.ChevronRight, null, tint = Color.Gray) }, action = onOpenStatistics) } }
        item { SettingsGroup("智能识别") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Text("使用大模型智能识别", Modifier.weight(1f)); Switch(smartEnabled, { checked -> smartEnabled = checked; recognitionSettings.enabled = checked }) }
            if (smartEnabled) {
                HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = TripMist.copy(alpha = .45f))
                OutlinedTextField(
                    value = apiKey, onValueChange = { apiKey = it; recognitionSettings.apiKey = it }, modifier = Modifier.fillMaxWidth().padding(12.dp),
                    label = { Text("智谱 API Key") }, singleLine = true,
                    visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { IconButton(onClick = { revealKey = !revealKey }) { Icon(if (revealKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (revealKey) "隐藏" else "显示") } },
                )
            }
        } }
        item { SettingsGroup("备份与恢复", "含照片和视频；恢复将替换本机数据。") {
            SettingsRow(Icons.Default.IosShare, "导出备份", tint = TripLakeText) { backupExporter.launch("TripTrail-Backup.triptrailbackup") }; GroupDivider(); SettingsRow(Icons.Default.DownloadForOffline, "恢复备份", tint = TripLakeText) { backupImporter.launch(arrayOf("application/vnd.triptrail.backup", "application/json", "text/plain", "*/*")) }
        } }
        item { SettingsGroup("接收分享") { SettingsRow(Icons.Default.MoveToInbox, "导入旅程或足迹", tint = TripLakeText) { sharedImporter.launch(arrayOf("application/vnd.triptrail.journey", "application/json", "text/plain", "*/*")) } } }
        item { SettingsGroup("数据与隐私") { SettingsRow(Icons.Default.Security, "数据存在本机", tint = TripLakeText); GroupDivider(); SettingsRow(Icons.Default.WarningAmber, "卸载 App 会清除本地数据", subtitle = "换机或卸载前请先导出完整备份。", tint = Color(0xFFB06A35)) } }
        item { SettingsGroup("关于") { SettingsRow(Icons.Default.Person, "创作者", trailing = { Row(verticalAlignment = Alignment.CenterVertically) { Text("黄逸轩", color = Color.Gray); Icon(Icons.Default.ChevronRight, null, tint = Color.Gray) } }) { creator = true }; GroupDivider(); SettingsValueRow("版本", "0.1.0"); GroupDivider(); SettingsValueRow("系统要求", "Android 8.0+") } }
    }

    pendingRestore?.let { restored -> AlertDialog(onDismissRequest = { pendingRestore = null }, title = { Text("恢复这份备份？") }, text = { Text("备份包含 ${restored.trips.size} 段旅程、${restored.stories.size} 个足迹。恢复后将替换本机当前所有数据，此操作不可撤销。") }, dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("取消") } }, confirmButton = { Button(onClick = { repository.replaceAll(restored); pendingRestore = null; message = "恢复完成。" }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("替换本机数据") } }) }
    pendingShared?.let { (trip, story) -> AlertDialog(onDismissRequest = { pendingShared = null }, title = { Text("收藏这份内容？") }, text = { Text("“${trip?.title ?: story?.title}”会追加为独立副本，不会覆盖已有内容。") }, dismissButton = { TextButton(onClick = { pendingShared = null }) { Text("取消") } }, confirmButton = { Button(onClick = { val current = repository.data.value; if (trip != null && current.trips.none { it.id == trip.id }) repository.replaceAll(current.copy(trips = current.trips + trip)); if (story != null && current.stories.none { it.id == story.id }) repository.replaceAll(repository.data.value.copy(stories = repository.data.value.stories + story)); pendingShared = null; message = "已添加到我的旅迹。" }) { Text("添加到我的旅迹") } }) }
    if (creator) AlertDialog(onDismissRequest = { creator = false }, icon = { Icon(Icons.Default.Favorite, null, tint = TripSand) }, title = { Text("黄逸轩") }, text = { Text("感谢使用旅迹。愿每一次出发都有期待，每一段回忆都有地方安放。") }, confirmButton = { TextButton(onClick = { creator = false }) { Text("完成") } })
    message?.let { AlertDialog(onDismissRequest = { message = null }, title = { Text("提示") }, text = { Text(it) }, confirmButton = { TextButton(onClick = { message = null }) { Text("好") } }) }
}

@Composable
private fun SettingsGroup(title: String, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF818185), modifier = Modifier.padding(start = 16.dp))
        Surface(shape = RoundedCornerShape(24.dp), color = TripSurface) { Column(Modifier.fillMaxWidth(), content = content) }
        footer?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFF818185), modifier = Modifier.padding(horizontal = 16.dp)) }
    }
}

@Composable
private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String? = null, tint: androidx.compose.ui.graphics.Color = TripInk, trailing: @Composable (() -> Unit)? = null, action: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (action != null) Modifier.clickable(onClick = action) else Modifier).padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.bodyLarge, color = if (action != null) TripLakeText else TripInk); subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.Gray) } }; trailing?.invoke()
    }
}

@Composable private fun SettingsValueRow(label: String, value: String) { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp)) { Text(label); Spacer(Modifier.weight(1f)); Text(value, color = Color.Gray) } }
@Composable private fun GroupDivider() { HorizontalDivider(Modifier.padding(start = 52.dp, end = 14.dp), color = TripMist.copy(alpha = .45f)) }
