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
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material.icons.outlined.Shield
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
fun SettingsScreen(
    repository: TripRepository,
    data: AppData,
    modifier: Modifier = Modifier,
    onOpenStatistics: () -> Unit,
    onOpenTrips: () -> Unit = {},
) {
    var recycleBin by remember { mutableStateOf(false) }
    if (recycleBin) RecycleBinScreen(repository) { recycleBin = false }
    val context = LocalContext.current
    val capacityCloud = remember { com.personal.triptrail.util.CloudSyncService.get(context) }
    var storageUsage by remember { mutableStateOf(capacityCloud.cachedStorageUsage()) }
    var storageUsageUnavailable by remember { mutableStateOf(false) }
    LaunchedEffect(capacityCloud) {
        try { storageUsage = capacityCloud.storageUsage(); storageUsageUnavailable = false }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { storageUsageUnavailable = true }
    }
    val scope = rememberCoroutineScope()
    val recognitionSettings = remember { SecureRecognitionSettings(context) }
    var smartEnabled by remember { mutableStateOf(recognitionSettings.enabled) }
    var amapKey by remember { mutableStateOf(recognitionSettings.amapWebKey) }
    var revealAmapKey by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf(recognitionSettings.apiKey) }
    var deepSeekApiKey by remember { mutableStateOf(recognitionSettings.deepSeekApiKey) }
    var provider by remember { mutableStateOf(recognitionSettings.provider) }
    var revealKey by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<PreparedImport<AppData>?>(null) }
    var pendingShared by remember { mutableStateOf<PreparedImport<Pair<Trip?, TravelStory?>>?>(null) }
    DisposableEffect(pendingRestore) {
        val restore = pendingRestore
        onDispose { restore?.discard() }
    }
    DisposableEffect(pendingShared) {
        val shared = pendingShared
        onDispose { shared?.discard() }
    }
    var creator by remember { mutableStateOf(false) }
    var restoringBackup by remember { mutableStateOf(false) }
    var backupDestination by remember { mutableStateOf(false) }
    var restoreSource by remember { mutableStateOf(false) }
    var uploadBackup by remember { mutableStateOf(false) }
    var backupManager by remember { mutableStateOf(false) }
    var preparedBackup by remember { mutableStateOf<TripBackupService.PreparedBackup?>(null) }
    var preparingBackup by remember { mutableStateOf(false) }
    var confirmPartialBackup by remember { mutableStateOf(false) }
    DisposableEffect(preparedBackup) {
        val prepared = preparedBackup
        onDispose { prepared?.file?.delete() }
    }
    val backupExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.triptrail.backup")) { uri ->
        val prepared = preparedBackup
        if (uri == null || prepared == null) { preparedBackup = null }
        else scope.launch {
            try {
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri) ?: error("无法写入备份文件")
                    output.use { target -> prepared.file.inputStream().use { it.copyTo(target) } }
                }
                message = if (prepared.skippedCount == 0) "备份已导出，请保存到安全位置。" else "备份已导出，已跳过 ${prepared.skippedCount} 个无法读取的资源。"
            } catch (failure: Exception) { message = "导出失败：${failure.localizedMessage}" }
            finally { preparedBackup = null }
        }
    }
    fun finishPreparedBackup() {
        val prepared = preparedBackup ?: return
        if (!uploadBackup) { backupExporter.launch("旅迹-完整备份.triptrailbackup"); return }
        preparingBackup = true
        scope.launch {
            try { com.personal.triptrail.util.CloudBackupService(context).upload(prepared.file); message = "云端备份已保存为新版本。" + if (prepared.skippedCount > 0) "已跳过 ${prepared.skippedCount} 个无法读取的资源。" else "" }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { message = "上传失败：${e.localizedMessage}" }
            finally { preparingBackup = false; preparedBackup = null }
        }
    }
    fun prepareBackupExport() {
        if (preparingBackup || preparedBackup != null) return
        preparingBackup = true
        scope.launch {
            try {
                val prepared = withContext(kotlinx.coroutines.Dispatchers.IO) { TripBackupService(context).prepareBackup(data) }
                preparedBackup = prepared
                if (prepared.skippedCount > 0) confirmPartialBackup = true
                else finishPreparedBackup()
            } catch (failure: Exception) { message = "生成备份失败：${failure.localizedMessage}" }
            finally { if (!uploadBackup || preparedBackup == null || confirmPartialBackup) preparingBackup = false }
        }
    }
    if (confirmPartialBackup) AlertDialog(
        onDismissRequest = { confirmPartialBackup = false; preparedBackup = null },
        title = { Text("部分资源无法读取") },
        text = { Text("有 ${preparedBackup?.skippedCount ?: 0} 个图片或视频可能已删除或无法访问。继续将跳过这些资源，其余内容正常备份。本机记录不会修改。") },
        confirmButton = { TextButton(onClick = { confirmPartialBackup = false; finishPreparedBackup() }) { Text("跳过并继续导出") } },
        dismissButton = { TextButton(onClick = { confirmPartialBackup = false; preparedBackup = null }) { Text("取消") } }
    )
    val backupImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            var prepared: PreparedImport<AppData>? = null
            try {
                withContext(Dispatchers.IO) {
                    prepared = context.contentResolver.openInputStream(uri)?.use { TripBackupService(context).prepareRead(it) }
                        ?: error("无法打开文件")
                }
                pendingRestore?.discard()
                pendingRestore = prepared
                prepared = null
            } catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (error: Exception) { message = "无法读取备份：${error.localizedMessage}" }
            finally { prepared?.discard() }
        }
    }
    if (backupDestination) AlertDialog(onDismissRequest = { backupDestination = false }, title = { Text("导出备份") },
        confirmButton = { TextButton(onClick = { backupDestination = false; uploadBackup = true; prepareBackupExport() }, enabled = com.personal.triptrail.util.CloudSyncService.get(context).state.value.configured) { Text("上传云端") } },
        dismissButton = { TextButton(onClick = { backupDestination = false; uploadBackup = false; prepareBackupExport() }) { Text("导出本地") } })
    if (restoreSource) AlertDialog(onDismissRequest = { restoreSource = false }, title = { Text("恢复备份") },
        confirmButton = { TextButton(onClick = { restoreSource = false; backupManager = true }, enabled = com.personal.triptrail.util.CloudSyncService.get(context).state.value.configured) { Text("从云端恢复") } },
        dismissButton = { TextButton(onClick = { restoreSource = false; backupImporter.launch(arrayOf("*/*")) }) { Text("从本地文件导入") } })
    if (backupManager) CloudBackupManagerScreen(onDismiss = { backupManager = false }, onExport = { backupManager = false; backupDestination = true }, onImport = { backupManager = false; backupImporter.launch(arrayOf("*/*")) }) { file, restore ->
        backupManager = false
        if (restore) scope.launch {
            try {
                val prepared = withContext(Dispatchers.IO) { file.inputStream().use { TripBackupService(context).prepareRead(it) } }
                pendingRestore?.discard(); pendingRestore = prepared
            } catch (e: Exception) { message = "无法读取备份：${e.localizedMessage}" }
            finally { file.delete() }
        } else {
            uploadBackup = false
            preparedBackup = TripBackupService.PreparedBackup(file, 0)
            backupExporter.launch("旅迹-云端备份.triptrailbackup")
        }
    }
    val sharedImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            var prepared: PreparedImport<Pair<Trip?, TravelStory?>>? = null
            try {
                withContext(Dispatchers.IO) {
                    prepared = context.contentResolver.openInputStream(uri)?.use { PortablePackageService(context).prepareShared(it) }
                        ?: error("无法打开文件")
                }
                pendingShared?.discard()
                pendingShared = prepared
                prepared = null
            } catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (error: Exception) { message = "无法读取分享文件：${error.localizedMessage}" }
            finally { prepared?.discard() }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp, 18.dp, 16.dp, 112.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        item { CloudEntryButton(repository) }
        item { SettingsGroup("旅行概览") { SettingsRow(Icons.Default.BarChart, "旅行统计", tint = TripLakeText, textColor = Color(0xFF1C1C1E), trailing = { Icon(Icons.Default.ChevronRight, null, tint = Color.Gray) }, action = onOpenStatistics) } }
        item { SettingsGroup("智能识别") {
            Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) { TripToggleRow("使用大模型智能识别", smartEnabled) { checked -> smartEnabled = checked; recognitionSettings.enabled = checked } }
            if (smartEnabled) {
                HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = TripMist.copy(alpha = .45f))
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("模型", Modifier.weight(1f))
                    Spacer(Modifier.width(16.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.width(216.dp)) {
                        SegmentedButton(provider == SecureRecognitionSettings.Provider.ZHIPU, { provider = SecureRecognitionSettings.Provider.ZHIPU; recognitionSettings.provider = provider }, shape = SegmentedButtonDefaults.itemShape(0, 2), icon = {}) { Text("智谱", maxLines = 1) }
                        SegmentedButton(provider == SecureRecognitionSettings.Provider.DEEPSEEK, { provider = SecureRecognitionSettings.Provider.DEEPSEEK; recognitionSettings.provider = provider }, shape = SegmentedButtonDefaults.itemShape(1, 2), icon = {}) { Text("DeepSeek", maxLines = 1) }
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = TripMist.copy(alpha = .45f))
                val keyValue = if (provider == SecureRecognitionSettings.Provider.ZHIPU) apiKey else deepSeekApiKey
                val keyLabel = if (provider == SecureRecognitionSettings.Provider.ZHIPU) "智谱 API Key" else "DeepSeek API Key"
                val updateKey: (String) -> Unit = { value ->
                    if (provider == SecureRecognitionSettings.Provider.ZHIPU) { apiKey = value; recognitionSettings.apiKey = value }
                    else { deepSeekApiKey = value; recognitionSettings.deepSeekApiKey = value }
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = keyValue, onValueChange = updateKey, modifier = Modifier.weight(1f), singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                        visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                        decorationBox = { inner -> Box {
                            if (keyValue.isEmpty()) Text(keyLabel, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            inner()
                        } },
                    )
                    TripClearTextButton(keyValue, { updateKey("") })
                    IconButton(onClick = { revealKey = !revealKey }) { Icon(if (revealKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (revealKey) "隐藏" else "显示") }
                }
            }
        } }
        item { SettingsGroup("高德路线", "用于查询路线地点坐标。Key 加密保存在本机，不包含在旅行备份中。") {
            TripFormField(
                value = amapKey,
                onValueChange = { amapKey = it; recognitionSettings.amapWebKey = it },
                modifier = Modifier.padding(12.dp), label = "高德 Web 服务 Key", singleLine = true,
                visualTransformation = if (revealAmapKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { IconButton(onClick = { revealAmapKey = !revealAmapKey }) { Icon(if (revealAmapKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, "显示或隐藏 Key") } },
            )
        } }
        item { SettingsGroup("数据管理") {
            SettingsRow(Icons.Default.Delete, "回收站", tint = TripLakeText) { recycleBin = true }
            SettingsRow(Icons.Default.History, "备份管理", tint = TripLakeText) { if (!preparingBackup) backupManager = true }
            GroupDivider()
            SettingsRow(Icons.Outlined.SystemUpdateAlt, "导入分享文件", tint = TripLakeText) { sharedImporter.launch(arrayOf("application/vnd.triptrail.journey", "application/json", "text/plain", "*/*")) }
        } }
        item { SettingsGroup("数据与隐私") { SettingsRow(Icons.Outlined.Shield, "本地始终保留数据副本", tint = TripLakeText); GroupDivider(); SettingsRow(Icons.Default.Cloud, "云端内容和备份为公开共享", subtitle = "云端备份独立保存历史版本。换机或卸载前，请确认完整备份已保存成功。", tint = TripLakeText) } }
        item { SettingsGroup("关于") { SettingsRow(Icons.Default.Person, "创作者", trailing = { Row(verticalAlignment = Alignment.CenterVertically) { Text("黄逸轩", color = Color.Gray); Icon(Icons.Default.ChevronRight, null, tint = Color.Gray) } }) { creator = true }; GroupDivider(); SettingsValueRow("版本", "0.1.0"); GroupDivider(); SettingsValueRow("系统要求", "Android 8.0+")
            GroupDivider()
            SettingsValueRow("云端数据库", storageUsage?.let { android.text.format.Formatter.formatFileSize(context, it.getLong("database_bytes")) } ?: if (storageUsageUnavailable) "暂不可用" else "加载中")
            GroupDivider()
            SettingsValueRow("云端对象存储", storageUsage?.let { android.text.format.Formatter.formatFileSize(context, it.getLong("object_bytes")) } ?: if (storageUsageUnavailable) "暂不可用" else "加载中")
            storageUsage?.let { usage ->
                Text("统计于 ${java.text.SimpleDateFormat("yyyy/M/d HH:mm", java.util.Locale.getDefault()).format(java.util.Date(usage.getLong("measured_at_ms")))} · 每日更新", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            } } }
    }

    pendingRestore?.let { prepared ->
        val restored = prepared.content
        val mediaCount = restored.backupMediaReferences().distinctBy { it.id }.size
        fun cancel() { if (!restoringBackup) { prepared.discard(); pendingRestore = null } }
        AlertDialog(modifier = androidx.compose.ui.Modifier.dismissKeyboardOnBlankTap(),
            onDismissRequest = ::cancel,
            title = { Text("恢复这份备份？") },
            text = { Text("备份包含 ${restored.trips.size} 段旅程、${restored.stories.size} 个足迹、${restored.favorites.size} 个收藏、$mediaCount 个媒体文件。恢复后将替换本机当前所有数据，此操作不可撤销。") },
            dismissButton = { TextButton(onClick = ::cancel) { Text("取消") } },
            confirmButton = { Button(onClick = { if (!restoringBackup) { restoringBackup = true; scope.launch {
                try { com.personal.triptrail.util.CloudSyncService.get(context).restoreBackup(repository, prepared); pendingRestore = null; message = "恢复完成。" }
                catch (e: Exception) { message = "恢复失败：${e.localizedMessage}" }
                finally { restoringBackup = false }
            } } }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("替换本机数据") } },
        )
    }
    pendingShared?.let { prepared ->
        val (trip, story) = prepared.content
        fun cancel() { prepared.discard(); pendingShared = null }
        AlertDialog(modifier = androidx.compose.ui.Modifier.dismissKeyboardOnBlankTap(),
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
                prepared.commit()
                pendingShared = null
            }) { Text("添加到我的旅迹") } },
        )
    }
    if (creator) AlertDialog(modifier = androidx.compose.ui.Modifier.dismissKeyboardOnBlankTap(),
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
    message?.let { AlertDialog(modifier = androidx.compose.ui.Modifier.dismissKeyboardOnBlankTap(), onDismissRequest = { message = null }, title = { Text("提示") }, text = { Text(it) }, confirmButton = { TextButton(onClick = { message = null }) { Text("好") } }) }
}

@Composable
private fun SettingsGroup(title: String, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = Color(0xFF818185), modifier = Modifier.padding(start = 16.dp))
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) { Column(Modifier.fillMaxWidth(), content = content) }
        footer?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFF818185), modifier = Modifier.padding(horizontal = 16.dp)) }
    }
}

@Composable
private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String? = null, tint: androidx.compose.ui.graphics.Color = TripInk, textColor: Color? = null, trailing: @Composable (() -> Unit)? = null, action: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (action != null) Modifier.clickable(onClick = action) else Modifier).heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal, color = textColor ?: if (action != null) TripLakeText else MaterialTheme.colorScheme.onSurface); subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.Gray) } }; trailing?.invoke()
    }
}

@Composable private fun SettingsValueRow(label: String, value: String) { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp)) { Text(label); Spacer(Modifier.weight(1f)); Text(value, color = Color.Gray) } }
@Composable private fun GroupDivider() { HorizontalDivider(Modifier.padding(start = 52.dp, end = 14.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .09f)) }
