package com.personal.triptrail.util

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import com.personal.triptrail.data.AppData
import kotlinx.serialization.json.Json
import java.io.File

/** External receivers can read asynchronously; grant shared files a 3-hour lease. */
object TemporaryFileCleanup {
    private const val LEASE_MS = 3 * 60 * 60 * 1000L
    private const val JOB_ID = 73041
    private var started = false

    @Synchronized fun start(context: Context) {
        if (started) return
        started = true
        context.cacheDir.listFiles()?.filter { it.name.startsWith("triptrail-import-") }
            ?.forEach { it.deleteRecursively() }
        // Decode independently: a corrupt database must never trigger media deletion.
        val database = File(context.filesDir, "triptrail-data.json")
        val data = runCatching {
            Json { ignoreUnknownKeys = true }.decodeFromString<AppData>(database.readText())
        }.getOrNull()
        if (data != null) {
            val used = data.backupMediaReferences().mapNotNull { media ->
                val uri = Uri.parse(media.localUri)
                if (uri.scheme == "file") uri.path else null
            }.toSet()
            File(context.filesDir, "media").listFiles()?.filter {
                (it.name.contains("-import-") || it.name.contains("-restored")) && it.absolutePath !in used
            }?.forEach { it.delete() }
        }
        sweepShares(context)
        schedule(context)
    }

    fun sweepShares(context: Context) {
        val cutoff = System.currentTimeMillis() - LEASE_MS
        File(context.cacheDir, "shares").listFiles()?.filter { it.lastModified() <= cutoff }
            ?.forEach { it.deleteRecursively() }
    }

    fun schedule(context: Context, replace: Boolean = false) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val nextExpiry = File(context.cacheDir, "shares").listFiles()?.minOfOrNull { it.lastModified() + LEASE_MS } ?: return
        if (replace || scheduler.getPendingJob(JOB_ID) == null) {
            scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(context, TemporaryFileCleanupJob::class.java))
                .setMinimumLatency((nextExpiry - System.currentTimeMillis()).coerceAtLeast(1_000L)).build())
        }
    }
}

class TemporaryFileCleanupJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            TemporaryFileCleanup.sweepShares(applicationContext)
            jobFinished(params, false)
            TemporaryFileCleanup.schedule(applicationContext, replace = true)
        }.start()
        return true
    }
    override fun onStopJob(params: JobParameters) = true
}
