package com.ibbie.catrec_gamingscreenrecorder

import android.content.Context
import android.util.Log
import androidx.work.*
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Session
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import com.ibbie.catrec_gamingscreenrecorder.AnalyticsManager

class HighlightClipExtractionWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "HighlightClipExtraction"
        private const val KEY_VIDEO_PATH = "video_path"
        private const val KEY_HIGHLIGHT_TIMESTAMPS = "highlight_timestamps"
        private const val KEY_CLIP_LENGTH = "clip_length"
    }

    private val analyticsManager by lazy { AnalyticsManager(context) }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val videoPath = inputData.getString(KEY_VIDEO_PATH) ?: return@withContext Result.failure()
            val highlightTimestampsJson = inputData.getString(KEY_HIGHLIGHT_TIMESTAMPS) ?: return@withContext Result.failure()
            val clipLengthSeconds = inputData.getInt(KEY_CLIP_LENGTH, 15)

            val videoFile = File(videoPath)
            if (!videoFile.exists()) {
                Log.e(TAG, "Video file does not exist: $videoPath")
                return@withContext Result.failure()
            }

            val highlightTimestamps = try {
                val jsonArray = JSONArray(highlightTimestampsJson)
                List(jsonArray.length()) { jsonArray.getLong(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse highlight timestamps: ${e.message}")
                return@withContext Result.failure()
            }

            if (highlightTimestamps.isEmpty()) {
                Log.d(TAG, "No highlight timestamps to process")
                return@withContext Result.success()
            }

            val outputDir = videoFile.parentFile
            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            val currentDate = dateFormat.format(Date())

            var successCount = 0
            var totalCount = highlightTimestamps.size
            val extractedClipPaths = mutableListOf<String>()

            for ((index, timestamp) in highlightTimestamps.withIndex()) {
                try {
                    val clipStartTime = (timestamp / 1000.0) - (clipLengthSeconds / 2.0)
                    val startTime = kotlin.math.max(0.0, clipStartTime)
                    val duration = clipLengthSeconds.toDouble()

                    val clipFileName = "CatRec_Highlight_${index + 1}_$currentDate.mp4"
                    val outputPath = File(outputDir, clipFileName).absolutePath

                    // FFmpeg command to extract clip
                    val ffmpegCommand = "-i \"$videoPath\" -ss $startTime -t $duration -c copy \"$outputPath\""
                    
                    Log.d(TAG, "Extracting clip $index: $ffmpegCommand")

                    val session = FFmpegKit.execute(ffmpegCommand)
                    val returnCode = session.returnCode

                    if (ReturnCode.isSuccess(returnCode)) {
                        successCount++
                        extractedClipPaths.add(outputPath)
                        Log.d(TAG, "Successfully extracted clip: $clipFileName")
                    } else {
                        Log.e(TAG, "Failed to extract clip $index: ${session.failStackTrace}")
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error extracting clip $index: ${e.message}")
                }
            }

            Log.d(TAG, "Highlight clip extraction completed: $successCount/$totalCount clips extracted successfully")
            
            // Auto-generate highlight reel if enabled and clips were extracted
            if (successCount > 0) {
                val settingsDataStore = SettingsDataStore(applicationContext)
                val autoReelGeneration = settingsDataStore.autoHighlightReelGeneration.first()
                
                if (autoReelGeneration) {
                    try {
                        val clipsJson = org.json.JSONArray(extractedClipPaths).toString()
                        val reelWorkRequest = OneTimeWorkRequestBuilder<HighlightReelGenerationWorker>()
                            .setInputData(workDataOf(
                                HighlightReelGenerationWorker.KEY_VIDEO_PATH to videoPath,
                                HighlightReelGenerationWorker.KEY_HIGHLIGHT_CLIPS to clipsJson
                            ))
                            .build()
                        
                        WorkManager.getInstance(applicationContext).enqueue(reelWorkRequest)
                        Log.d(TAG, "Triggered highlight reel generation")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to trigger highlight reel generation: ${e.message}")
                    }
                }
                
                Result.success()
            } else {
                Result.failure()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Highlight extraction failed", e)
            logCrashlyticsIfEnabled("Highlight extraction failed: ${e.message}", e, context)
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