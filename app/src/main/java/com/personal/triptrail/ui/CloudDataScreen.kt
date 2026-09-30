package com.personal.triptrail.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.personal.triptrail.data.TripRepository
import com.personal.triptrail.util.CloudSyncService
import com.personal.triptrail.util.TripFileService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun CloudBadge(id: String, kind: String, fontSize: androidx.compose.ui.unit.TextUnit = LocalTextStyle.current.fontSize) {
    val cloud = CloudSyncService.get(LocalContext.current)
    val state by cloud.state.collectAsState()
    if ("$kind:${id.lowercase()}" in state.bindings) Icon(Icons.Outlined.Cloud, contentDescription = "云端模式，本地副本已保留", modifier = Modifier.size(with(androidx.compose.ui.platform.LocalDensity.current) { fontSize.toDp() }), tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun CloudEntryButton(repository: TripRepository, kind: String? = null, modifier: Modifier = Modifier) {
    val cloud = CloudSyncService.get(LocalContext.current)
    val state by cloud.state.collectAsState()
    var show by remember { mutableStateOf(false) }
    FilledTonalButton(onClick = { show = true }, enabled = state.configured, modifier = modifier) { Text(if (kind == null) "☁️ 云端数据" else "☁️ 云端") }
    if (show) CloudDataScreen(repository, kind) { show = false }
}

@Composable
internal fun CloudDataScreen(repository: TripRepository, initialKind: String?, onDismiss: () -> Unit) {
    val cloud = CloudSyncService.get(LocalContext.current)
    val state by cloud.state.collectAsState()
    val data by repository.data.collectAsState()
    var kind by remember { mutableStateOf(initialKind ?: "trip") }
    var error by remember { mutableStateOf<String?>(null) }
    var resolving by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun run(action: suspend () -> Unit) { scope.launch { try { action() } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message } } }
    val locals = remember(data, kind) { TripFileService.cloudRecords(data).filter { it.kind == kind } }
    LaunchedEffect(kind) { cloud.sync(repository, kind = kind, browse = true, automatic = true) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDismiss) { Text("完成") }
                    Text("云端数据", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { run { cloud.sync(repository, kind = kind, browse = true) } }, enabled = state.configured && !state.busy) { Text("刷新") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("trip" to "旅程", "story" to "足迹", "favorite" to "收藏").forEach { (value, label) ->
                        FilterChip(selected = kind == value, onClick = { kind = value }, label = { Text(label) })
                    }
                }
                if (!state.configured) Text("当前安装包未配置云端服务")
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.message.isNotBlank()) Text(state.message, style = MaterialTheme.typography.bodySmall)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Text("本地内容", style = MaterialTheme.typography.titleMedium) }
                    items(locals, key = { "local:${it.key}" }) { local ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Row { Text(local.title.ifBlank { "未命名" }, Modifier.weight(1f)); CloudBadge(local.id, local.kind) }
                                when {
                                    local.key in state.conflicts -> TextButton(onClick = { resolving = local.key }) { Text("本地和云端都有修改 · 选择版本") }
                                    local.key in state.bindings -> Text("云端项目", style = MaterialTheme.typography.bodySmall)
                                    else -> TextButton(onClick = { run { cloud.enable(local); cloud.sync(repository, kind = kind, recordId = local.id) } }, enabled = state.configured && !state.busy) { Text("上传并设为云端模式") }
                                }
                            }
                        }
                    }

                }
            }
        }
    }
    resolving?.let { key -> AlertDialog(onDismissRequest = { resolving = null }, title = { Text("选择要保留的版本") },
        text = { Text("本地和云端都已修改。选择后，另一版本将被替换。") },
        confirmButton = { TextButton(onClick = { resolving = null; run { cloud.resolve(key, false, repository) } }) { Text("以本地更新云端") } },
        dismissButton = { TextButton(onClick = { resolving = null; run { cloud.resolve(key, true, repository) } }) { Text("使用云端版本") } }) }
    error?.let { text -> AlertDialog(onDismissRequest = { error = null }, title = { Text("云端数据") }, text = { Text(text) }, confirmButton = { TextButton(onClick = { error = null }) { Text("好") } }) }
}

@Composable
internal fun CloudModeAction(repository: TripRepository, id: String, kind: String) {
    val context = LocalContext.current
    val cloud = CloudSyncService.get(context)
    val state by cloud.state.collectAsState()
    val scope = rememberCoroutineScope()
    val key = "$kind:${id.lowercase()}"
    val linked = key in state.bindings
    if (!linked) DropdownMenuItem(text = { Text("设为云端") }, leadingIcon = { Icon(Icons.Outlined.Cloud, contentDescription = null) }, enabled = !state.busy && state.configured, onClick = {
        scope.launch {
            try {
                val record = TripFileService.cloudRecords(repository.data.value).firstOrNull { it.key == key } ?: return@launch
                cloud.enable(record)
                cloud.sync(repository, kind = kind, recordId = id)
                android.widget.Toast.makeText(context, cloud.state.value.message.ifBlank { "已设为云端" }, android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { android.widget.Toast.makeText(context, e.localizedMessage ?: "上传失败，本地数据已保留", android.widget.Toast.LENGTH_LONG).show() }
        }
    })
}

@Composable
internal fun CreationMethodSelector(value: String, smart: Boolean, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (if (smart) listOf("智能录入", "普通新建") else listOf("普通新建")).forEach { method ->
            Surface(onClick = { onSelect(method) }, modifier = Modifier.weight(1f), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), color = if (value == method) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                Text(method, Modifier.padding(vertical = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
internal fun CloudBackupManagerScreen(onDismiss: () -> Unit, onExport: () -> Unit, onImport: () -> Unit, onDownload: (java.io.File, Boolean) -> Unit) {
    val context = LocalContext.current
    val service = remember { com.personal.triptrail.util.CloudBackupService(context) }
    val scope = rememberCoroutineScope()
    var versions by remember { mutableStateOf(emptyList<com.personal.triptrail.util.CloudBackupVersion>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var choosingRestore by remember { mutableStateOf(false) }
    val backupListState = androidx.compose.foundation.lazy.rememberLazyListState()
    var deleting by remember { mutableStateOf<com.personal.triptrail.util.CloudBackupVersion?>(null) }
    suspend fun refresh() {
        busy = true
        try { versions = service.list() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = "读取备份失败：${e.localizedMessage}" }
        finally { busy = false }
    }
    fun download(v: com.personal.triptrail.util.CloudBackupVersion, restore: Boolean) {
        busy = true
        scope.launch {
            try { onDownload(service.download(v), restore) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.localizedMessage }
            finally { busy = false }
        }
    }
    LaunchedEffect(Unit) { refresh() }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDismiss, enabled = !busy) { Text("完成") }
                    Text("备份管理", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(64.dp))
                }
                Box(Modifier.fillMaxWidth().height(6.dp)) { if (busy) LinearProgressIndicator(Modifier.fillMaxWidth()) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onExport, enabled = !busy) { Text("导出备份") }
                    TextButton(onClick = { choosingRestore = true }, enabled = !busy) { Text("恢复备份") }
                }
                LazyColumn(state = backupListState, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(versions, key = { it.id }) { v ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(v.title, style = MaterialTheme.typography.titleMedium)
                                Text(android.text.format.Formatter.formatFileSize(context, v.bytes))
                                if (v.deleting) Text("删除未完成，可再次删除重试")
                                else if (!v.ready) Text("上传未完成，可删除后重新上传")
                                Row {
                                    TextButton(onClick = { download(v, false) }, enabled = !busy && v.ready && !v.deleting) { Text("导出") }
                                    TextButton(onClick = { download(v, true) }, enabled = !busy && v.ready && !v.deleting) { Text("恢复") }
                                    Spacer(Modifier.weight(1f))
                                    TextButton(onClick = { deleting = v }, enabled = !busy) { Text("删除", color = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }
                    if (versions.isEmpty()) item { Text(if (busy) "正在加载…" else "暂无云端备份") }
                }
            }
        }
    }
    if (choosingRestore) AlertDialog(
        onDismissRequest = { choosingRestore = false }, title = { Text("恢复备份") },
        confirmButton = { TextButton(onClick = { choosingRestore = false; scope.launch { backupListState.animateScrollToItem(0) } }) { Text("从云端恢复") } },
        dismissButton = { TextButton(onClick = { choosingRestore = false; onImport() }) { Text("从本地文件导入") } }
    )
    deleting?.let { v ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除这个云端备份版本？") }, text = { Text("删除后无法恢复，不影响本机数据。") },
            confirmButton = { TextButton(onClick = {
                deleting = null; busy = true
                scope.launch {
                    try { service.delete(v) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.localizedMessage }
                    finally { refresh() }
                }
            }) { Text("删除备份") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } })
    }
    error?.let { AlertDialog(onDismissRequest = { error = null }, title = { Text("提示") }, text = { Text(it) }, confirmButton = { TextButton(onClick = { error = null }) { Text("确定") } }) }
}


@Composable
internal fun RecycleBinScreen(repository: TripRepository, onDismiss: () -> Unit) {
    val cloud = CloudSyncService.get(LocalContext.current)
    val state by cloud.state.collectAsState()
    val recycle by cloud.recycle.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { cloud.refreshRecycle(repository) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDismiss) { Text("完成") }
                    Text("回收站", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { scope.launch { cloud.refreshRecycle(repository) } }, enabled = !state.busy) { Text("刷新") }
                }
                Text("保留一天", style = MaterialTheme.typography.labelMedium)
                val entries = recycle.filter { it.getLong("expires_at_ms") > System.currentTimeMillis() && (it.optBoolean("recoverable", true) || it.has("payload")) }.sortedByDescending { it.getLong("expires_at_ms") }
                if (entries.isEmpty()) Text("回收站为空", modifier = Modifier.padding(16.dp))
                LazyColumn(Modifier.weight(1f)) {
                    items(entries, key = { it.getString("kind") + it.getString("id") }) { entry ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.getString("title"))
                                val type = when (entry.getString("kind")) { "trip" -> "旅程"; "story" -> "足迹"; else -> "收藏" }
                                val status = if (entry.optBoolean("pending")) "待同步删除" else "保留至 " + java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(entry.getLong("expires_at_ms")))
                                Text("$type · $status", style = MaterialTheme.typography.bodySmall)
                            }
                            if (entry.optBoolean("cloud")) Icon(Icons.Outlined.Cloud, "云端", Modifier.size(18.dp))
                            TextButton(onClick = { scope.launch { cloud.restoreRecycle(entry, repository) } }, enabled = !state.busy) { Text("恢复") }
                        }
                    }
                }
                if (state.message.isNotBlank()) Text(state.message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
