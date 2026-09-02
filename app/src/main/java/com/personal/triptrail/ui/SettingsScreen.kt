@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.personal.triptrail.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.personal.triptrail.data.AppData
import com.personal.triptrail.data.TravelStory
import com.personal.triptrail.data.Trip
import com.personal.triptrail.data.TripRepository
import com.personal.triptrail.R
import com.personal.triptrail.util.PortablePackageService
import com.personal.triptrail.util.PreparedImport
import com.personal.triptrail.util.SecureRecognitionSettings
import com.personal.triptrail.util.TripBackupService
import com.personal.triptrail.util.backupMediaReferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(repository: TripRepository, data: AppData, modifier: Modifier = Modifier, onOpenStatistics: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recognitionSettings = remember { SecureRecognitionSettings(context) }
    var smartEnabled by remember { mutableStateOf(recognitionSettings.enabled) }
    var apiKey by remember { mutableStateOf(recognitionSettings.apiKey) }
    var deepSeekApiKey by remember { mutableStateOf(recognitionSettings.deepSeekApiKey) }
    var provider by remember { mutableStateOf(recognitionSettings.provider) }
    var revealKey by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<PreparedImport<AppData>?>(null) }
    var pendingShared by remember { mutableStateOf<PreparedImport<Pair<Trip?, TravelStory?>>?>(null) }
    var creator by remember { mutableStateOf(false) }
    val backupExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.triptrail.backup")) { uri ->
        if (uri != null) runCatching { context.contentResolver.openOutputStream(uri)?.use { TripBackupService(context).write(data, it) } }.onSuccess { message = "完整备份已导出，请保存到安全位置。" }.onFailure { message = "导出失败：${it.localizedMessage}" }
    }
    val backupImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { TripBackupService(context).prepareRead(it) }
                    ?: error("无法打开文件")
            } }.onSuccess { pendingRestore = it }.onFailure { message = "无法读取备份：${it.localizedMessage}" }
        }
    }
    val sharedImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { PortablePackageService(context).prepareShared(it) }
                    ?: error("无法打开文件")
            } }.onSuccess { pendingShared = it }.onFailure { message = "无法读取分享文件：${it.localizedMessage}" }
        }
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
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("模型", Modifier.weight(1f))
                    SingleChoiceSegmentedButtonRow {
                        SegmentedButton(provider == SecureRecognitionSettings.Provider.ZHIPU, { provider = SecureRecognitionSettings.Provider.ZHIPU; recognitionSettings.provider = provider }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("智谱") }
                        SegmentedButton(provider == SecureRecognitionSettings.Provider.DEEPSEEK, { provider = SecureRecognitionSettings.Provider.DEEPSEEK; recognitionSettings.provider = provider }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("DeepSeek") }
                    }
                }
                TripFormField(
                    value = if (provider == SecureRecognitionSettings.Provider.ZHIPU) apiKey else deepSeekApiKey,
                    onValueChange = { value -> if (provider == SecureRecognitionSettings.Provider.ZHIPU) { apiKey = value; recognitionSettings.apiKey = value } else { deepSeekApiKey = value; recognitionSettings.deepSeekApiKey = value } }, modifier = Modifier.padding(12.dp),
                    label = if (provider == SecureRecognitionSettings.Provider.ZHIPU) "智谱 API Key" else "DeepSeek API Key", singleLine = true,
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

    pendingRestore?.let { prepared ->
        val restored = prepared.content
        val mediaCount = restored.backupMediaReferences().distinctBy { it.id }.size
        fun cancel() { prepared.discard(); pendingRestore = null }
        AlertDialog(
            onDismissRequest = ::cancel,
            title = { Text("恢复这份备份？") },
            text = { Text("备份包含 ${restored.trips.size} 段旅程、${restored.stories.size} 个足迹、${restored.favorites.size} 个收藏、$mediaCount 个媒体文件。恢复后将替换本机当前所有数据，此操作不可撤销。") },
            dismissButton = { TextButton(onClick = ::cancel) { Text("取消") } },
            confirmButton = { Button(onClick = { repository.replaceAll(restored); pendingRestore = null; message = "恢复完成。" }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("替换本机数据") } },
        )
    }
    pendingShared?.let { prepared ->
        val (trip, story) = prepared.content
        fun cancel() { prepared.discard(); pendingShared = null }
        AlertDialog(
            onDismissRequest = ::cancel,
            title = { Text("收藏这份内容？") },
            text = { Text("“${trip?.title ?: story?.title}”会追加为独立副本，不会覆盖已有内容。") },
            dismissButton = { TextButton(onClick = ::cancel) { Text("取消") } },
            confirmButton = { Button(onClick = {
                val current = repository.data.value
                val exists = (trip != null && current.trips.any { it.id == trip.id }) || (story != null && current.stories.any { it.id == story.id })
                if (exists) {
                    prepared.discard()
                    message = "这份内容已经导入过了。"
                } else if (trip != null) {
                    repository.replaceAll(current.copy(trips = current.trips + trip))
                    message = "旅程已添加到我的旅迹。"
                } else if (story != null) {
                    repository.replaceAll(current.copy(stories = current.stories + story))
                    message = "足迹已添加到我的旅迹。"
                }
                pendingShared = null
            }) { Text("添加到我的旅迹") } },
        )
    }
    if (creator) AlertDialog(
        onDismissRequest = { creator = false },
        shape = RoundedCornerShape(28.dp),
        containerColor = TripSurface,
        text = {
            Image(
                    painter = painterResource(R.drawable.creator_reward),
                    contentDescription = "微信赞赏二维码",
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Fit,
                )
        },
        confirmButton = { TextButton(onClick = { creator = false }) { Text("完成") } },
    )
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
