package com.personal.triptrail.util

import android.content.Context
import android.net.Uri
import com.personal.triptrail.BuildConfig
import com.personal.triptrail.data.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal data class CloudLocalRecord(val id: String, val kind: String, val title: String, val payload: JSONObject, val media: List<MediaReference>) {
    val key get() = "$kind:${id.lowercase()}"
    val fingerprint get() = CloudJson.fingerprint(payload)
}
internal data class CloudRemoteRecord(val value: JSONObject) {
    val id = value.getString("id")
    val kind = value.getString("kind")
    val title = value.optString("title", "未命名")
    val revision = value.getLong("revision")
    val payload = value.getJSONObject("payload")
    val key get() = "$kind:${id.lowercase()}"
    init { require(kind in listOf("trip", "story", "favorite") && revision > 0 && payload.getString("id").equals(id, true)) { "云端数据格式不受支持" } }
}
internal object CloudJson {
    fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    fun transform(value: Any?, media: (JSONObject) -> JSONObject): Any? = when(value) {
        is JSONObject -> {
            val source = if (value.has("localIdentifier")) media(JSONObject(value.toString())) else value
            JSONObject().apply { source.keys().forEach { put(it, transform(source.get(it), media)) } }
        }
        is JSONArray -> JSONArray().apply { (0 until value.length()).forEach { put(transform(value.get(it), media)) } }
        else -> value
    }
    fun canonical(value: Any?): String = when(value) {
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") { JSONObject.quote(it) + ":" + canonical(value.get(it)) }
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.get(it)) }
        is String -> JSONObject.quote(value)
        null, JSONObject.NULL -> "null"
        else -> value.toString()
    }
    fun fingerprint(payload: JSONObject): String {
        val normalized = transform(payload) { it.put("localIdentifier", it.getString("id")).apply { remove("cloudPath") } }
        return digest(canonical(normalized).toByteArray())
    }
}
internal data class CloudState(val busy: Boolean = false, val configured: Boolean = false, val bindings: Set<String> = emptySet(), val remote: List<CloudRemoteRecord> = emptyList(), val conflicts: Set<String> = emptySet(), val message: String = "")

internal class CloudSyncService private constructor(private val context: Context) {
    companion object {
        @Volatile private var instance: CloudSyncService? = null
        fun get(context: Context): CloudSyncService = instance ?: synchronized(this) { instance ?: CloudSyncService(context.applicationContext).also { instance = it } }
    }
    fun cachedStorageUsage(): JSONObject? = runCatching {
        JSONObject(preferences.getString("storageUsage.$projectUrl", null) ?: return null)
    }.getOrNull()

    suspend fun storageUsage(): JSONObject {
        val key = "storageUsage.$projectUrl"
        cachedStorageUsage()?.let {
            val age = System.currentTimeMillis() - it.optLong("measured_at_ms", 0)
            if (age in 0 until 86_400_000L) return it
        }
        val result = JSONArray(String(request("rest/v1/rpc/triptrail_storage_usage", "POST", "{}".toByteArray()))).getJSONObject(0)
        require(result.getLong("database_bytes") >= 0 && result.getLong("object_bytes") >= 0)
        result.getLong("measured_at_ms")
        preferences.edit().putString(key, result.toString()).apply()
        return result
    }

    private val syncMutex = Mutex()
    private val lastAutomaticCheck = mutableMapOf<String, Long>()
    private val preferences = context.getSharedPreferences("triptrail-cloud", Context.MODE_PRIVATE)
    val projectUrl get() = BuildConfig.SUPABASE_URL
    val publicKey get() = BuildConfig.SUPABASE_ANON_KEY
    private val bindings = runCatching { JSONObject(preferences.getString("relational-bindings", "{}")!!) }.getOrDefault(JSONObject())
    private val mediaPaths = runCatching { JSONObject(preferences.getString("relational-mediaPaths", "{}")!!) }.getOrDefault(JSONObject())
    private val _state = MutableStateFlow(CloudState(configured = publicKey.isNotBlank(), bindings = linkedKeys()))
    val state = _state.asStateFlow()
    private fun linkedKeys() = bindings.keys().asSequence().filter { bindings.getJSONObject(it).optString("origin") == projectUrl }.toSet()
    private fun persist() {
        check(preferences.edit().putString("relational-bindings", bindings.toString()).putString("relational-mediaPaths", mediaPaths.toString()).commit()) { "无法保存本地同步状态" }
        _state.value = _state.value.copy(bindings = linkedKeys(), configured = publicKey.isNotBlank())
    }
    private val recycleFile = File(context.filesDir, "recycle.json")
    private val recycleStore = CloudRecycleStore(recycleFile)
    private var recycleReadError: String? = null
    private var recycleEntries = try { recycleStore.read() } catch (e: Exception) { recycleReadError = "回收站读取失败，请保留应用数据：${e.message}"; JSONArray() }
    private val _recycle = MutableStateFlow(recycleList())
    val recycle = _recycle.asStateFlow()
    private fun recycleList(): List<JSONObject> = (0 until recycleEntries.length()).map { recycleEntries.getJSONObject(it) }
    private fun recycleKey(entry: JSONObject) = "${entry.getString("kind")}:${entry.getString("id").lowercase()}"
    fun isDeleted(key: String) = recycleReadError != null || recycleList().any { recycleKey(it) == key }
    private fun saveRecycle(entries: List<JSONObject>) {
        val bytes = JSONArray(entries).toString().toByteArray()
        recycleStore.write(bytes)
        recycleEntries = JSONArray(String(bytes)); _recycle.value = recycleList()
    }
    fun trash(id: String, kind: String, repository: TripRepository) {
        check(recycleReadError == null) { recycleReadError.orEmpty() }
        val record = TripFileService.cloudRecords(repository.data.value).firstOrNull { it.id.equals(id, true) && it.kind == kind } ?: return
        val cloud = record.key in linkedKeys()
        val entry = JSONObject().put("id", id).put("kind", kind).put("title", record.title).put("payload", record.payload)
            .put("expires_at_ms", System.currentTimeMillis() + 86_400_000L).put("cloud", cloud).put("pending", cloud).put("operation_id", java.util.UUID.randomUUID().toString())
        saveRecycle(recycleList().filterNot { recycleKey(it) == record.key } + entry)
        repository.removeForRecycle(id, kind)
        bindings.remove(record.key); persist()
        _state.value = _state.value.copy(remote = _state.value.remote.filterNot { it.key == record.key }, conflicts = _state.value.conflicts - record.key)
    }
    private suspend fun recycleRPC(name: String, entry: JSONObject) {
        val body = JSONObject().put("record_id", entry.getString("id")).put("record_kind", entry.getString("kind"))
        if (name == "triptrail_trash_record" && entry.has("operation_id")) body.put("operation_id", entry.getString("operation_id"))
        request("rest/v1/rpc/$name", "POST", body.toString().toByteArray())
    }
    private suspend fun flushDeletes(repository: TripRepository) {
        check(recycleReadError == null) { recycleReadError.orEmpty() }
        recycleList().forEach { entry ->
            if ((entry.optBoolean("pending") && recycleKey(entry) in linkedKeys()) || !entry.optBoolean("cloud")) {
                repository.removeForRecycle(entry.getString("id"), entry.getString("kind"))
                bindings.remove(recycleKey(entry)); persist()
            }
            if (entry.optBoolean("pending")) {
                recycleRPC("triptrail_trash_record", entry)
                saveRecycle(recycleList().map { if (recycleKey(it) == recycleKey(entry)) JSONObject(it.toString()).put("pending", false) else it })
            }
        }
        saveRecycle(recycleList().map { JSONObject(it.toString()).apply { if (getLong("expires_at_ms") <= System.currentTimeMillis()) remove("payload") } })
    }
    private suspend fun loadDeletions(repository: TripRepository) {
        val entries = mutableListOf<JSONObject>(); var offset = 0
        while (true) {
            val rows = JSONArray(String(request("rest/v1/triptrail_deleted_records?select=*&order=kind.asc,id.asc&limit=100&offset=$offset")))
            (0 until rows.length()).forEach { index ->
                val row = rows.getJSONObject(index)
                row.getString("id"); row.getString("kind"); row.getLong("expires_at_ms")
                row.put("cloud", true).put("pending", false)
                if (row.getLong("expires_at_ms") > System.currentTimeMillis()) {
                    recycleList().firstOrNull { recycleKey(it) == recycleKey(row) }?.optJSONObject("payload")?.let { row.put("payload", it) }
                }
                entries += row
            }
            if (rows.length() < 100) break
            offset += rows.length()
        }
        val retained = recycleList().filter { !it.optBoolean("cloud") || it.optBoolean("pending") }
        saveRecycle(retained + entries.filter { row -> retained.none { recycleKey(it) == recycleKey(row) } })
        entries.filter { recycleKey(it) in linkedKeys() }.forEach {
            repository.removeForRecycle(it.getString("id"), it.getString("kind"))
            bindings.remove(recycleKey(it))
        }
        _state.value = _state.value.copy(remote = _state.value.remote.filterNot { isDeleted(it.key) }, conflicts = _state.value.conflicts.filterNot { isDeleted(it) }.toSet())
        persist()
    }
    suspend fun refreshRecycle(repository: TripRepository) = syncMutex.withLock {
        _state.value = _state.value.copy(busy = true)
        try {
            flushDeletes(repository)
            if (publicKey.isNotBlank()) {
                loadDeletions(repository)
                request("rest/v1/rpc/triptrail_purge_recycle", "POST", "{}".toByteArray())
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { _state.value = _state.value.copy(message = "回收站暂未同步：${e.message}") }
        finally { _state.value = _state.value.copy(busy = false) }
    }
    suspend fun restoreRecycle(entry: JSONObject, repository: TripRepository) = syncMutex.withLock {
        _state.value = _state.value.copy(busy = true)
        try {
            check(entry.getLong("expires_at_ms") > System.currentTimeMillis()) { "已超过 24 小时，无法恢复" }
            val key = recycleKey(entry)
            check(TripFileService.cloudRecords(repository.data.value).none { it.key == key }) { "已有相同标识的本地内容，未覆盖" }
            if (entry.optBoolean("cloud")) {
                if (entry.optBoolean("pending")) recycleRPC("triptrail_trash_record", entry)
                recycleRPC("triptrail_restore_record", entry)
                loadRemote(ids = listOf(entry.getString("id").lowercase()))
                val server = _state.value.remote.firstOrNull { it.key == key }
                if (server != null) {
                    saveRecycle(recycleList().filterNot { recycleKey(it) == key })
                    receive(server, null, repository)
                } else {
                    TripFileService.applyCloudRecord(entry.getString("kind"), entry.getJSONObject("payload"), repository)
                    enable(TripFileService.cloudRecords(repository.data.value).first { it.key == key })
                }
            } else TripFileService.applyCloudRecord(entry.getString("kind"), entry.getJSONObject("payload"), repository)
            saveRecycle(recycleList().filterNot { recycleKey(it) == key })
            lastAutomaticCheck.clear(); _state.value = _state.value.copy(message = "已恢复")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { _state.value = _state.value.copy(message = "恢复失败：${e.message}") }
        finally { _state.value = _state.value.copy(busy = false) }
    }

    suspend fun restoreBackup(repository: TripRepository, prepared: PreparedImport<AppData>) = syncMutex.withLock {
        repository.replaceAll(prepared.content)
        prepared.commit()
        detachAll()
        val restored = TripFileService.cloudRecords(repository.data.value).map { it.key }.toSet()
        saveRecycle(recycleList().filter { it.optBoolean("cloud") || recycleKey(it) !in restored })
    }
    fun detachAll() { bindings.keys().asSequence().toList().forEach { bindings.remove(it) }; persist() }
    fun enable(local: CloudLocalRecord) {
        check(publicKey.isNotBlank()) { "当前安装包未配置云端服务" }
        if (!bindings.has(local.key)) bindings.put(local.key, JSONObject().put("revision", 0).put("baseline", "").put("origin", projectUrl))
        persist()
    }
    suspend fun sync(repository: TripRepository, kind: String? = null, recordId: String? = null, browse: Boolean = false, automatic: Boolean = false) = syncMutex.withLock {
        if (publicKey.isBlank()) return@withLock
        _state.value = _state.value.copy(busy = true)
        try {
            flushDeletes(repository)
            val now = System.currentTimeMillis()
            val records = TripFileService.cloudRecords(repository.data.value).filter {
                it.key in linkedKeys() && (kind == null || kind == it.kind) && (recordId == null || recordId.equals(it.id, true)) &&
                    CloudRefreshPolicy.shouldRequest(automatic, it.fingerprint != bindings.getJSONObject(it.key).getString("baseline"), lastAutomaticCheck[it.key], now)
            }
            val catalogKey = "catalog:" + (kind ?: "all")
            val fetchCatalog = (browse || recordId == null) && CloudRefreshPolicy.shouldRequest(automatic, records.isNotEmpty(), lastAutomaticCheck[catalogKey], now)
            if (!fetchCatalog && records.isEmpty()) return@withLock
            loadDeletions(repository)
            if (fetchCatalog) { loadRemote(kind = kind); lastAutomaticCheck[catalogKey] = now }
            else records.map { it.id.lowercase() }.chunked(100).forEach { loadRemote(ids = it) }
            records.forEach { snapshot ->
                val local = TripFileService.cloudRecords(repository.data.value).firstOrNull { it.key == snapshot.key && it.key in linkedKeys() } ?: return@forEach
                try { syncOne(local, repository); lastAutomaticCheck[local.key] = now }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { _state.value = _state.value.copy(message = "本地已保留，稍后重试：${e.message}") }
            }
            if (fetchCatalog) {
                _state.value.remote.filter { kind == null || it.kind == kind }.forEach { server ->
                    val exists = TripFileService.cloudRecords(repository.data.value).any { it.key == server.key }
                    if (!exists) {
                        try { receive(server, null, repository); lastAutomaticCheck[server.key] = now }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { _state.value = _state.value.copy(message = "部分云端内容暂未加载，本地数据已保留：${e.localizedMessage}") }
                    }
                }
            }
            val existing = TripFileService.cloudRecords(repository.data.value).map { it.key }.toSet()
            bindings.keys().asSequence().toList().filterNot { it in existing }.forEach { bindings.remove(it) }
            persist()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { _state.value = _state.value.copy(message = "云端暂不可用，继续使用本地数据：${e.message}") }
        finally { _state.value = _state.value.copy(busy = false) }
    }
    suspend fun archiveFinishedTrips(repository: TripRepository) {
        try {
            repository.archiveFinishedTrips()
            val data = repository.data.value
            val endedCloudIds = data.trips.filter { it.phase() == TripPhase.HISTORY && "trip:${it.id.lowercase()}" in linkedKeys() }.map { it.id }.toSet()
            val storyIds = data.stories.filter { it.sourceTripId in endedCloudIds }.map { it.id }.toSet()
            TripFileService.cloudRecords(data).filter { it.kind == "story" && it.id in storyIds && it.key !in linkedKeys() }.forEach { enable(it) }
            if (storyIds.isNotEmpty()) uploadPending(repository)
        } catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) { _state.value = _state.value.copy(message = "自动整理足迹未完成：${error.localizedMessage}") }
    }

    suspend fun uploadPending(repository: TripRepository) = syncMutex.withLock {
        if (publicKey.isBlank()) return@withLock
        _state.value = _state.value.copy(busy = true)
        try {
            flushDeletes(repository)
            TripFileService.cloudRecords(repository.data.value).filter { it.key in linkedKeys() && !isDeleted(it.key) }.forEach { local ->
                val binding = bindings.getJSONObject(local.key)
                if (local.fingerprint != binding.getString("baseline")) {
                    try { send(local, binding.getLong("revision")) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { _state.value = _state.value.copy(message = "本地已保存，云端待同步：${e.message}") }
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { _state.value = _state.value.copy(message = "云端待同步：${e.message}") }
        finally { _state.value = _state.value.copy(busy = false) }
    }
    private suspend fun loadRemote(kind: String? = null, ids: List<String>? = null) {
        val records = mutableListOf<CloudRemoteRecord>(); var offset = 0
        val filter = if (ids != null) "&id=in.(${ids.joinToString(",")})" else if (kind != null) "&kind=eq.$kind" else ""
        while (true) {
            val array = JSONArray(String(request("rest/v1/triptrail_cloud_records?select=*&order=id.asc&limit=100&offset=$offset$filter")))
            (0 until array.length()).forEach { records += CloudRemoteRecord(array.getJSONObject(it)) }
            if (array.length() < 100) break
            offset += array.length()
        }
        val retained = _state.value.remote.filterNot { if (ids != null) it.id.lowercase() in ids else kind == null || it.kind == kind }
        _state.value = _state.value.copy(remote = retained + records, message = "")
    }
    private suspend fun syncOne(local: CloudLocalRecord, repository: TripRepository) {
        val binding = bindings.getJSONObject(local.key)
        val server = _state.value.remote.firstOrNull { it.key == local.key }
        val revision = binding.getLong("revision")
        val dirty = local.fingerprint != binding.getString("baseline")
        when (CloudSyncDecision.choose(dirty, revision, server?.revision)) {
            CloudSyncDecision.CONFLICT -> _state.value = _state.value.copy(conflicts = _state.value.conflicts + local.key, message = "本地和云端同时修改，请选择版本")
            CloudSyncDecision.DOWNLOAD -> receive(requireNotNull(server), local, repository)
            CloudSyncDecision.UPLOAD -> send(local, revision)
            CloudSyncDecision.MISSING -> error("云端记录已不存在，本地副本仍保留")
            CloudSyncDecision.UNCHANGED -> {
                val missingMedia = withContext(Dispatchers.IO) {
                    local.media.any { media ->
                        runCatching {
                            val uri = Uri.parse(media.localUri)
                            if (uri.scheme == "file") File(requireNotNull(uri.path)).canRead()
                            else context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
                        }.getOrDefault(false).not()
                    }
                }
                if (missingMedia && server != null) receive(server, local, repository)
            }
        }
    }
    suspend fun acquire(server: CloudRemoteRecord, repository: TripRepository) = syncMutex.withLock {
        check(!_state.value.busy) { "正在同步，请稍后再试" }
        check(TripFileService.cloudRecords(repository.data.value).none { it.key == server.key }) { "本地已有同一条内容，请从本地内容启用云端模式" }
        _state.value = _state.value.copy(busy = true)
        try { receive(server, null, repository) } finally { _state.value = _state.value.copy(busy = false) }
    }
    suspend fun resolve(key: String, useCloud: Boolean, repository: TripRepository) = syncMutex.withLock {
        check(!_state.value.busy)
        _state.value = _state.value.copy(busy = true)
        try {
            loadRemote(ids = listOf(key.substringAfter(':')))
            val local = TripFileService.cloudRecords(repository.data.value).first { it.key == key }
            val server = _state.value.remote.first { it.key == key }
            if (useCloud) receive(server, local, repository) else send(local, server.revision)
            _state.value = _state.value.copy(conflicts = _state.value.conflicts - key)
        } finally { _state.value = _state.value.copy(busy = false) }
    }
    private suspend fun send(local: CloudLocalRecord, revision: Long) {
        if (isDeleted(local.key)) return
        val paths = mutableMapOf<String, String>()
        local.media.forEach { media ->
            val cacheKey = "$projectUrl|${media.localUri}"
            if (mediaPaths.has(cacheKey)) paths[media.id] = mediaPaths.getString(cacheKey)
            else {
                val bytes = withContext(Dispatchers.IO) {
                    val uri = Uri.parse(media.localUri)
                    (if (uri.scheme == "file") File(requireNotNull(uri.path)).inputStream() else context.contentResolver.openInputStream(uri))?.use { it.readBytes() }
                        ?: error("媒体无法读取，本地内容未上传")
                }
                val mime = context.contentResolver.getType(Uri.parse(media.localUri))
                val extension = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
                    ?: Uri.parse(media.localUri).lastPathSegment?.substringAfterLast('.', "")?.takeIf { it.length in 1..5 && it.all(Char::isLetterOrDigit) }
                    ?: if (media.kind == MediaKind.VIDEO) "mp4" else "jpg"
                val path = "${local.id.lowercase()}/${CloudJson.digest(bytes)}.$extension"
                request("storage/v1/object/triptrail-media/$path", "POST", bytes, "application/octet-stream", true)
                mediaPaths.put(cacheKey, path); paths[media.id] = path; persist()
            }
        }
        val payload = CloudJson.transform(local.payload) { it.put("localIdentifier", "").put("cloudPath", paths[it.getString("id")]) }
        if (isDeleted(local.key)) return
        val body = JSONObject().put("record_id", local.id).put("record_kind", local.kind).put("record_title", local.title).put("record_payload", payload).put("expected_revision", revision)
        try {
            val text = String(request("rest/v1/rpc/triptrail_save_record", "POST", body.toString().toByteArray()))
            val server = CloudRemoteRecord(if (text.trimStart().startsWith("[")) JSONArray(text).getJSONObject(0) else JSONObject(text))
            bindings.put(local.key, JSONObject().put("revision", server.revision).put("baseline", local.fingerprint).put("origin", projectUrl))
            persist()
            _state.value = _state.value.copy(remote = _state.value.remote.filterNot { it.key == server.key } + server, message = "已同步，本地副本已保留")
        } catch(e: Exception) {
            if (e.message?.contains("409") == true) _state.value = _state.value.copy(conflicts = _state.value.conflicts + local.key)
            throw e
        }
    }
    private suspend fun receive(server: CloudRemoteRecord, local: CloudLocalRecord?, repository: TripRepository) {
        val descriptors = mutableListOf<JSONObject>()
        CloudJson.transform(server.payload) { descriptors += it; it }
        val identifiers = mutableMapOf<String, String>()
        val directory = File(context.filesDir, "cloud-media").apply { mkdirs() }
        descriptors.forEach { media ->
            val path = media.getString("cloudPath")
            require(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/[0-9a-f]{64}\\.[a-zA-Z0-9]+").matches(path)) { "云端媒体路径无效" }
            val file = File(directory, CloudJson.digest(projectUrl.toByteArray()) + "-" + path.replace('/', '-'))
            if (!file.exists()) {
                val bytes = request("storage/v1/object/triptrail-media/$path")
                require(CloudJson.digest(bytes) == path.substringAfterLast('/').substringBeforeLast('.')) { "媒体校验失败" }
                withContext(Dispatchers.IO) {
                    val temp = File(directory, file.name + ".tmp")
                    temp.writeBytes(bytes); check(temp.renameTo(file)) { "无法保存本地媒体" }
                }
            }
            val uri = Uri.fromFile(file).toString()
            identifiers[media.getString("id")] = uri; mediaPaths.put("$projectUrl|$uri", path)
        }
        if (isDeleted(server.key)) return
        val current = TripFileService.cloudRecords(repository.data.value).firstOrNull { it.key == server.key }
        check(current?.fingerprint == local?.fingerprint) { "下载期间本地有修改，将在下次同步处理" }
        val payload = CloudJson.transform(server.payload) { it.put("localIdentifier", identifiers[it.getString("id")]) } as JSONObject
        TripFileService.applyCloudRecord(server.kind, payload, repository)
        val applied = TripFileService.cloudRecords(repository.data.value).first { it.key == server.key }
        bindings.put(server.key, JSONObject().put("revision", server.revision).put("baseline", applied.fingerprint).put("origin", projectUrl))
        persist(); _state.value = _state.value.copy(conflicts = _state.value.conflicts - server.key, message = "已获取云端内容，并保存到本地")
    }
    internal suspend fun request(path: String, method: String = "GET", body: ByteArray? = null, contentType: String = "application/json", allowDuplicate: Boolean = false): ByteArray = withContext(Dispatchers.IO) {
        val connection = URL("$projectUrl/$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method; connection.connectTimeout = 15000; connection.readTimeout = if (path.contains("triptrail-backups")) 180000 else 30000
            connection.setRequestProperty("apikey", publicKey)
            if (publicKey.split('.').size == 3) connection.setRequestProperty("Authorization", "Bearer $publicKey")
            connection.setRequestProperty("Content-Type", contentType)
            body?.let { connection.doOutput = true; connection.outputStream.use { output -> output.write(it) } }
            val code = connection.responseCode
            val bytes = (if (code in 200..299) connection.inputStream else connection.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
            if (allowDuplicate && (code == 409 || (code == 400 && runCatching { JSONObject(String(bytes)).optString("error") == "Duplicate" }.getOrDefault(false)))) return@withContext bytes
            check(code in 200..299) { "HTTP $code：云端服务暂不可用，本地内容仍保留" }
            bytes
        } finally { connection.disconnect() }
    }
}

internal enum class CloudSyncDecision {
    UPLOAD, DOWNLOAD, CONFLICT, MISSING, UNCHANGED;
    companion object {
        fun choose(localChanged: Boolean, baseRevision: Long, remoteRevision: Long?): CloudSyncDecision {
            if (remoteRevision == null) return if (baseRevision == 0L) UPLOAD else MISSING
            if (remoteRevision != baseRevision) return if (localChanged) CONFLICT else DOWNLOAD
            return if (localChanged) UPLOAD else UNCHANGED
        }
    }
}

internal object CloudRefreshPolicy {
    // This is evaluated on navigation only; it never schedules a refresh.
    fun shouldRequest(automatic: Boolean, dirty: Boolean, lastCheck: Long?, now: Long): Boolean =
        !automatic || dirty || lastCheck == null || now - lastCheck >= 60_000
}

internal data class CloudBackupVersion(val id: String, val createdAt: String, val path: String, val bytes: Long, val sha256: String, val ready: Boolean, val deleting: Boolean, val chunks: Int) {
    val title: String get() = runCatching { java.time.OffsetDateTime.parse(createdAt).atZoneSameInstant(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) }.getOrDefault(createdAt)
}
internal class CloudBackupService(private val context: Context) {
    private val cloud = CloudSyncService.get(context)
    suspend fun list(): List<CloudBackupVersion> {
        val result = mutableListOf<CloudBackupVersion>()
        while (true) {
            val page = JSONArray(String(cloud.request("rest/v1/triptrail_backups?select=*&order=created_at.desc,id.desc&limit=100&offset=${result.size}")))
            for (i in 0 until page.length()) {
                val v = page.getJSONObject(i)
                result += CloudBackupVersion(v.getString("id"), v.getString("created_at"), v.getString("object_path"), v.getLong("bytes"), v.getString("sha256"), v.getBoolean("ready"), v.getBoolean("deleting"), v.getInt("chunk_count"))
            }
            if (page.length() < 100) return result
        }
    }
    private val chunkSize = 8 * 1024 * 1024
    suspend fun upload(file: File) {
        val size = file.length()
        check(size > 0) { "备份文件为空" }
        val checksum = withContext(Dispatchers.IO) {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(chunkSize)
                while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
        val count = ((size + chunkSize - 1) / chunkSize).toInt()
        val id = java.util.UUID.randomUUID().toString()
        val path = "$id.triptrailbackup"
        val body = JSONObject().put("id", id).put("object_path", path).put("bytes", size).put("format_version", 1).put("sha256", checksum).put("chunk_count", count)
        cloud.request("rest/v1/triptrail_backups", "POST", body.toString().toByteArray())
        try {
            file.inputStream().use { input ->
                for (index in 0 until count) {
                    val sizeToRead = minOf(chunkSize.toLong(), size - index.toLong() * chunkSize).toInt()
                    val bytes = withContext(Dispatchers.IO) {
                        val buffer = ByteArray(sizeToRead)
                        var offset = 0
                        while (offset < buffer.size) {
                            val n = input.read(buffer, offset, buffer.size - offset)
                            check(n > 0) { "备份文件读取不完整" }; offset += n
                        }
                        buffer
                    }
                    cloud.request("storage/v1/object/triptrail-backups/$path/$index", "POST", bytes, "application/octet-stream")
                }
            }
            cloud.request("rest/v1/triptrail_backups?id=eq.$id", "PATCH", """{"ready":true}""".toByteArray())
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error("上传未完成或结果未确认，请在备份管理中刷新查看。未完成的版本可删除后重新上传。") }
    }
    suspend fun download(version: CloudBackupVersion): File {
        check(version.ready && !version.deleting) { "这个备份尚未完成或正在删除" }
        check(version.bytes > 0 && version.chunks.toLong() == (version.bytes + chunkSize - 1) / chunkSize) { "备份版本信息无效" }
        val file = withContext(Dispatchers.IO) { File.createTempFile("cloud-backup-", ".triptrailbackup", context.cacheDir) }
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            file.outputStream().use { output ->
                for (index in 0 until version.chunks) {
                    val bytes = cloud.request("storage/v1/object/authenticated/triptrail-backups/${version.path}/$index")
                    check(bytes.size <= chunkSize) { "备份分块大小无效" }
                    withContext(Dispatchers.IO) { output.write(bytes); digest.update(bytes) }
                    size += bytes.size
                }
            }
            val checksum = digest.digest().joinToString("") { "%02x".format(it) }
            check(size == version.bytes && checksum == version.sha256) { "备份文件校验失败，请重新下载" }
            return file
        } catch(e: Exception) { file.delete(); throw e }
    }
    suspend fun delete(version: CloudBackupVersion) {
        val endpoint = "rest/v1/triptrail_backups?id=eq.${version.id}"
        cloud.request(endpoint, "PATCH", """{"deleting":true}""".toByteArray())
        (0 until version.chunks).chunked(100).forEach { indices ->
            cloud.request("storage/v1/object/triptrail-backups", "DELETE", JSONObject().put("prefixes", JSONArray(indices.map { "${version.path}/$it" })).toString().toByteArray())
        }
        cloud.request(endpoint, "DELETE")
    }
}
