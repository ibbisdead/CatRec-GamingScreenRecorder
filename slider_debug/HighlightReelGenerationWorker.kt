package com.ibbie.catrec_gamingscreenrecorder

import android.content.Context
import android.util.Log
import androidx.work.*
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class HighlightReelGenerationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "HighlightReelGeneration"
        const val KEY_VIDEO_PATH = "video_path"
        const val KEY_HIGHLIGHT_CLIPS = "highlight_clips"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val videoPath = inputData.getString(KEY_VIDEO_PATH) ?: return@withContext Result.failure()
            val highlightClipsJson = inputData.getString(KEY_HIGHLIGHT_CLIPS) ?: return@withContext Result.failure()

            val videoFile = File(videoPath)
            if (!videoFile.exists()) {
                Log.e(TAG, "Video file does not exist: $videoPath")
                return@withContext Result.failure()
            }

            val highlightClips = try {
                val clipsArray = org.json.JSONArray(highlightClipsJson)
                List(clipsArray.length()) { clipsArray.getString(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse highlight clips: ${e.message}")
                return@withContext Result.failure()
            }

            if (highlightClips.isEmpty()) {
                Log.d(TAG, "No highlight clips to concatenate")
                return@withContext Result.success()
            }

            val outputDir = videoFile.parentFile
            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            val currentDate = dateFormat.format(Date())
            val reelFileName = "CatRec_HighlightReel_$currentDate.mp4"
            val outputPath = File(outputDir, reelFileName).absolutePath

            // Create a temporary file list for FFmpeg concatenation
            val tempFileList = File(outputDir, "temp_file_list.txt")
            try {
                tempFileList.writeText(
                    highlightClips.joinToString("\n") { "file '$it'" }
                )

                // FFmpeg command to concatenate clips without re-encoding
                val ffmpegCommand = "-f concat -safe 0 -i \"${tempFileList.absolutePath}\" -c copy \"$outputPath\""
                
                Log.d(TAG, "Generating highlight reel: $ffmpegCommand")

                val session = FFmpegKit.execute(ffmpegCommand)
                val returnCode = session.returnCode

                if (ReturnCode.isSuccess(returnCode)) {
                    Log.d(TAG, "Successfully generated highlight reel: $reelFileName")
                    Result.success()
                } else {
                    Log.e(TAG, "Failed to generate highlight reel: ${session.failStackTrace}")
                    Result.failure()
                }

            } finally {
                // Clean up temporary file
                if (tempFileList.exists()) {
                    tempFileList.delete()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Highlight reel generation failed: ${e.message}")
            Result.failure()
        }
    }
} 