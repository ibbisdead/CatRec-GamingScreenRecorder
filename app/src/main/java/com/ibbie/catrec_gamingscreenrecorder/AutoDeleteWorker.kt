package com.ibbie.catrec_gamingscreenrecorder

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ibbie.catrec_gamingscreenrecorder.SettingsDataStore
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import com.ibbie.catrec_gamingscreenrecorder.AnalyticsManager

class AutoDeleteWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    companion object {
        private const val TAG = "AutoDeleteWorker"
        fun schedule(context: Context) {
            val workRequest = androidx.work.PeriodicWorkRequestBuilder<AutoDeleteWorker>(1, java.util.concurrent.TimeUnit.DAYS)
                .build()
            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                TAG,
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }

    private val analyticsManager by lazy { AnalyticsManager(context) }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val settings = SettingsDataStore(applicationContext)
            val enabled = settings.autoDeleteEnabled.first()
            val retention = settings.retentionDays.first()
            if (!enabled) return@withContext Result.success()

            val now = System.currentTimeMillis()
            val retentionMillis = TimeUnit.DAYS.toMillis(retention.toLong())
            val deletedFiles = mutableListOf<String>()

            // Scan main recordings and highlight clips
            val dirs = listOf(
                File("/storage/emulated/0/Movies/CatRec"),
                File("/storage/emulated/0/Movies/CatRec/Highlights")
            )
            for (dir in dirs) {
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.forEach { file ->
                        // Skip files in use (lock/temp/partial)
                        if (file.name.endsWith(".tmp") || file.name.endsWith(".lock") || file.name.endsWith(".partial")) return@forEach
                        if (!file.isFile) return@forEach
                        val age = now - file.lastModified()
                        if (age > retentionMillis) {
                            if (file.delete()) {
                                deletedFiles.add(file.absolutePath)
                            }
                        }
                    }
                }
            }
            Log.d(TAG, "AutoDeleteWorker deleted files: $deletedFiles")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Auto-delete failed", e)
            logCrashlyticsIfEnabled("Auto-delete failed: ${e.message}", e, context)
            return@withContext Result.failure()
        }
    }

    private suspend fun logCrashlyticsIfEnabled(message: String, throwable: Throwable? = null, context: Context) {
        val settings = SettingsDataStore(context)
        val enabled = settings.crashReportingEnabled.first()
        if (enabled) {
            FirebaseCrashlytics.getInstance().log(message)
            throwable?.let { FirebaseCrashlytics.getInstance().recordException(it) }
        }
    }
} 