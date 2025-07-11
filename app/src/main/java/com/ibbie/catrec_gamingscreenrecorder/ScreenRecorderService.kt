package com.ibbie.catrec_gamingscreenrecorder

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import overlay.RecordingOverlay
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.ibbie.catrec_gamingscreenrecorder.audio.MicRecorder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.*
import android.widget.Toast
import com.ibbie.catrec_gamingscreenrecorder.SettingsDataStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import android.media.MediaMetadataRetriever
import android.graphics.Bitmap
import java.io.FileOutputStream
import androidx.work.*
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import kotlin.math.abs
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.ibbie.catrec_gamingscreenrecorder.AnalyticsManager

class ScreenRecorderService : Service() {

    companion object {
        private const val TAG = "ScreenRecorderService"
        private const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "screen_recorder_channel"
        private const val CHANNEL_NAME = "Screen Recorder"
        const val ACTION_RECORDING_STOPPED = "com.ibbie.RECORDING_STOPPED"
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaCodec: MediaCodec? = null
    private var mediaMuxer: MediaMuxer? = null
    private var recordingOverlay: RecordingOverlay? = null

    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0
    private var bitrate = 0
    private var outputFile: File? = null
    private var isRecording = false
    private var isPaused = false
    private var pauseEnabled = false
    private var startTime: Long = 0L
    private var pauseStartTime: Long = 0L
    private var totalPausedTime: Long = 0L
    private var videoTrackIndex = -1
    private var muxerStarted = false
    private var recordingFileName: String? = null

    private var micRecorder: MicRecorder? = null
    private lateinit var micOutputFile: File

    private var isMicMuted = false
    private var micMuteEnabled = false
    private var highlightTimestamps = mutableListOf<Long>()
    private var autoHighlightDetection = false

    private val stopRecordingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.ibbie.ACTION_STOP_RECORDING") {
                stopRecording()
            }
        }
    }

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.ibbie.ACTION_STOP_RECORDING" -> stopRecording()
                "com.ibbie.ACTION_TOGGLE_PAUSE" -> togglePauseResume()
                "com.ibbie.ACTION_TOGGLE_MIC" -> toggleMicMute()
            }
        }
    }

    private val settingsDataStore by lazy { SettingsDataStore(this) }
    private var autoStopJob: Job? = null
    private val analyticsManager by lazy { AnalyticsManager(this) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerReceiver(stopRecordingReceiver, IntentFilter("com.ibbie.ACTION_STOP_RECORDING"))
        registerReceiver(controlReceiver, IntentFilter().apply {
            addAction("com.ibbie.ACTION_STOP_RECORDING")
            addAction("com.ibbie.ACTION_TOGGLE_PAUSE")
            addAction("com.ibbie.ACTION_TOGGLE_MIC")
        })
        // Read pauseEnabled, micMuteEnabled, and autoHighlightDetection from settings
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            pauseEnabled = settingsDataStore.pauseEnabled.first()
            micMuteEnabled = settingsDataStore.micMuteEnabled.first()
            autoHighlightDetection = settingsDataStore.autoHighlightDetection.first()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_RECORDING" -> {
                val resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED)
                val data = intent.getParcelableExtra<Intent>("data")
                val width = intent.getIntExtra("width", 1920)
                val height = intent.getIntExtra("height", 1080)
                val density = intent.getIntExtra("density", 1)
                val bitrateValue = intent.getIntExtra("bitrate", 8000000)
                val orientation = intent.getIntExtra("orientation", 0)
                
                if (data != null) {
                    startRecording(resultCode, data, width, height, density, bitrateValue, orientation)
                }
            }
            "STOP_RECORDING" -> {
                stopRecording()
            }
        }
        return START_NOT_STICKY
    }

    private fun vibrateStart() {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            (getSystemService(VibratorManager::class.java) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(100)
        }
    }
    private fun vibrateStop() {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            (getSystemService(VibratorManager::class.java) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 80, 80, 80) // double pulse
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    private fun startRecording(
        resultCode: Int,
        data: Intent,
        width: Int,
        height: Int,
        density: Int,
        bitrateValue: Int,
        orientation: Int
    ) {
        if (isRecording) return

        vibrateStart()

        // Generate timestamped file name
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        recordingFileName = "CatRec_${timestamp}.mp4"

        // Read selected resolution
        var targetWidth = width
        var targetHeight = height
        runBlocking {
            when (settingsDataStore.resolution.first()) {
                "1280x720" -> { targetWidth = 1280; targetHeight = 720 }
                "1920x1080" -> { targetWidth = 1920; targetHeight = 1080 }
                // "Native" or unknown: use provided width/height
            }
        }

        // Auto-stop timer
        GlobalScope.launch(Dispatchers.Main) {
            val minutes = settingsDataStore.autoStopMinutes.first()
            if (minutes > 0) {
                autoStopJob?.cancel()
                autoStopJob = GlobalScope.launch(Dispatchers.Main) {
                    delay(minutes.toLong() * 60L * 1000L)
                    if (isRecording) {
                        stopRecording()
                        Toast.makeText(this@ScreenRecorderService, "Recording auto-stopped after $minutes min", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        // Show recording indicator overlay
        if (recordingOverlay == null) {
            recordingOverlay = RecordingOverlay(this)
        }
        recordingOverlay?.show()
        recordingOverlay?.onPauseResume = {
            if (pauseEnabled) togglePauseResume()
        }
        recordingOverlay?.onMicMuteToggle = {
            if (micMuteEnabled) toggleMicMute()
        }
        recordingOverlay?.setMicMuted(isMicMuted)

        screenWidth = targetWidth
        screenHeight = targetHeight
        screenDensity = density
        bitrate = bitrateValue

        // Create output file
        outputFile = File(getExternalFilesDir(null), recordingFileName!!)

        // Start mic recording
        try {
            micOutputFile = File(getExternalFilesDir(null), "mic_audio.aac")
            micRecorder = MicRecorder(
                context = this,
                outputFile = micOutputFile,
                sampleRate = 44100,
                enableNoiseSuppressor = true,
                micVolume = 1.0f
            )
            micRecorder?.startRecording()
            if (autoHighlightDetection) {
                startHighlightDetection()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Mic recording failed: ${e.message}")
            CoroutineScope(Dispatchers.IO).launch {
                logCrashlyticsIfEnabled("Mic recording failed: ${e.message}", e, this@ScreenRecorderService)
            }
        }

        // Set up MediaProjection
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        // Set up MediaCodec
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, screenWidth, screenHeight)
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)

        mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        mediaCodec?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        // Set up MediaMuxer
        mediaMuxer = MediaMuxer(outputFile!!.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        // Set up VirtualDisplay
        val surface = mediaCodec?.createInputSurface()
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenRecording",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface, null, null
        )

        // Start recording
        mediaCodec?.start()
        isRecording = true
        startTime = System.currentTimeMillis()
        muxerStarted = false

        // Show notification
        startForeground(NOTIFICATION_ID, createNotification())

        // Start encoding thread
        Thread {
            encodeVideo()
        }.start()

        CoroutineScope(Dispatchers.IO).launch {
            analyticsManager.logRecordingStart()
        }
    }

    private fun encodeVideo() {
        val bufferInfo = MediaCodec.BufferInfo()
        
        while (isRecording) {
            if (isPaused) {
                Thread.sleep(100)
                continue
            }
            val outputBufferId = mediaCodec?.dequeueOutputBuffer(bufferInfo, 10000)
            
            when (outputBufferId) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // No output available yet
                }
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // Format changed, add track to muxer
                    if (!muxerStarted) {
                        val format = mediaCodec?.outputFormat
                        if (format != null) {
                            videoTrackIndex = mediaMuxer?.addTrack(format) ?: -1
                            mediaMuxer?.start()
                            muxerStarted = true
                        }
                    }
                }
                MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                    // Ignore
                }
                else -> {
                    if (outputBufferId != null && outputBufferId >= 0) {
                        val outputBuffer = mediaCodec?.getOutputBuffer(outputBufferId)
                        if (outputBuffer != null && muxerStarted) {
                            mediaMuxer?.writeSampleData(videoTrackIndex, outputBuffer, bufferInfo)
                        }
                        mediaCodec?.releaseOutputBuffer(outputBufferId, false)
                    }
                }
            }
        }
    }

    private fun stopRecording() {
        if (!isRecording) return

        vibrateStop()

        autoStopJob?.cancel()
        autoStopJob = null

        // Hide recording indicator overlay
        recordingOverlay?.hide()
        recordingOverlay = null

        isRecording = false
        isPaused = false
        pauseStartTime = 0L
        totalPausedTime = 0L

        // Stop mic recording
        try {
            micRecorder?.stopRecording()
        } catch (e: Exception) {
            Log.e(TAG, "Mic stop failed: ${e.message}")
            CoroutineScope(Dispatchers.IO).launch {
                logCrashlyticsIfEnabled("Mic stop failed: ${e.message}", e, this@ScreenRecorderService)
            }
        }
        micRecorder = null

        // Stop video encoding
        mediaCodec?.stop()
        mediaCodec?.release()
        virtualDisplay?.release()
        mediaProjection?.stop()

        // Stop muxer
        if (muxerStarted) {
            mediaMuxer?.stop()
            mediaMuxer?.release()
        }

        // Mux mic audio with video using FFmpegKit
        try {
            val videoFile = File(getExternalFilesDir(null), recordingFileName ?: "CatRec_unknown.mp4")
            val finalFileName = recordingFileName?.replace(".mp4", "_with_audio.mp4") ?: "CatRec_unknown_with_audio.mp4"
            val outputFile = File(getExternalFilesDir(null), finalFileName)
            val ffmpegCommand = "-i ${videoFile.absolutePath} -i ${micOutputFile.absolutePath} -c:v copy -c:a aac -strict experimental ${outputFile.absolutePath}"
            FFmpegKit.executeAsync(ffmpegCommand) { session ->
                val returnCode = session.returnCode
                if (ReturnCode.isSuccess(returnCode)) {
                    Log.d(TAG, "Muxing succeeded: ${outputFile.absolutePath}")
                    // Auto-trim if enabled
                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            val trimEnabled = settingsDataStore.autoTrimEnabled.first()
                            val trimStart = settingsDataStore.autoTrimStartSeconds.first()
                            val trimEnd = settingsDataStore.autoTrimEndSeconds.first()
                            if (trimEnabled && (trimStart > 0 || trimEnd > 0)) {
                                val duration = getVideoDurationSec(outputFile.absolutePath)
                                val trimTo = (duration - trimEnd).coerceAtLeast(trimStart + 1)
                                val trimmedFile = File(outputFile.parent, outputFile.nameWithoutExtension + "_trimmed.mp4")
                                val trimCmd = "-i ${outputFile.absolutePath} -ss $trimStart -to $trimTo -c copy ${trimmedFile.absolutePath}"
                                val trimSession = FFmpegKit.execute(trimCmd)
                                if (ReturnCode.isSuccess(trimSession.returnCode) && trimmedFile.exists()) {
                                    outputFile.delete()
                                    trimmedFile.renameTo(outputFile)
                                    Log.d(TAG, "Auto-trimmed video: ${outputFile.absolutePath}")
                                    // Regenerate thumbnail for trimmed file
                                    try {
                                        val retriever = MediaMetadataRetriever()
                                        retriever.setDataSource(outputFile.absolutePath)
                                        val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                        if (frame != null) {
                                            val thumbFile = File(outputFile.parent, outputFile.nameWithoutExtension + "_thumbnail.png")
                                            FileOutputStream(thumbFile).use { out ->
                                                frame.compress(Bitmap.CompressFormat.PNG, 100, out)
                                            }
                                            Log.d(TAG, "Thumbnail updated: ${thumbFile.absolutePath}")
                                        }
                                        retriever.release()
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Thumbnail update failed: ${e.message}")
                                    }
                                } else {
                                    Log.e(TAG, "Auto-trim failed, using untrimmed file.")
                                    CoroutineScope(Dispatchers.IO).launch {
                                        logCrashlyticsIfEnabled("Auto-trim failed", null, this@ScreenRecorderService)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Auto-trim error: ${e.message}")
                            CoroutineScope(Dispatchers.IO).launch {
                                logCrashlyticsIfEnabled("Auto-trim error: ${e.message}", e, this@ScreenRecorderService)
                            }
                        }
                    }
                    // Generate thumbnail after muxing
                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            val retriever = MediaMetadataRetriever()
                            retriever.setDataSource(outputFile.absolutePath)
                            val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            if (frame != null) {
                                val thumbFile = File(outputFile.parent, outputFile.nameWithoutExtension + "_thumbnail.png")
                                FileOutputStream(thumbFile).use { out ->
                                    frame.compress(Bitmap.CompressFormat.PNG, 100, out)
                                }
                                Log.d(TAG, "Thumbnail generated: ${thumbFile.absolutePath}")
                            }
                            retriever.release()
                        } catch (e: Exception) {
                            Log.e(TAG, "Thumbnail generation failed: ${e.message}")
                            CoroutineScope(Dispatchers.IO).launch {
                                logCrashlyticsIfEnabled("Thumbnail generation failed: ${e.message}", e, this@ScreenRecorderService)
                            }
                        }
                    }
                    // After muxing, save highlight timestamps if any
                    if (autoHighlightDetection && highlightTimestamps.isNotEmpty()) {
                        val highlightFile = File(outputFile.parent, outputFile.nameWithoutExtension + "_highlights.json")
                        val json = JSONArray(highlightTimestamps)
                        highlightFile.writeText(json.toString())
                        
                        // Trigger highlight clip extraction if enabled
                        GlobalScope.launch(Dispatchers.Main) {
                            val clipExtractionEnabled = settingsDataStore.autoHighlightClipExtraction.first()
                            val clipLength = settingsDataStore.highlightClipLength.first()
                            if (clipExtractionEnabled) {
                                val data = workDataOf(
                                    "video_path" to outputFile.absolutePath,
                                    "highlight_timestamps" to json.toString(),
                                    "clip_length" to clipLength
                                )
                                val request = OneTimeWorkRequestBuilder<HighlightClipExtractionWorker>()
                                    .setInputData(data)
                                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                                    .build()
                                WorkManager.getInstance(this@ScreenRecorderService).enqueue(request)
                                Log.d(TAG, "Highlight clip extraction queued for ${highlightTimestamps.size} highlights")
                            }
                        }
                    }
                    // Launch PlaybackActivity for preview
                    val playbackIntent = Intent(this, PlaybackActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        putExtra("video_uri", outputFile.absolutePath)
                    }
                    startActivity(playbackIntent)

                    // Cloud backup if enabled
                    GlobalScope.launch(Dispatchers.Main) {
                        val backupEnabled = settingsDataStore.cloudBackupEnabled.first()
                        val provider = settingsDataStore.cloudBackupProvider.first()
                        if (backupEnabled) {
                            val data = workDataOf(
                                "video_path" to outputFile.absolutePath,
                                "thumbnail_path" to (outputFile.parent + "/" + outputFile.nameWithoutExtension + "_thumbnail.png"),
                                "provider" to provider
                            )
                            val request = OneTimeWorkRequestBuilder<CloudBackupWorker>()
                                .setInputData(data)
                                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                                .build()
                            WorkManager.getInstance(this@ScreenRecorderService).enqueue(request)
                        }
                    }
                } else {
                    Log.e(TAG, "Muxing failed: ${session.failStackTrace}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Muxing failed: ${e.message}")
        }

        // Hide overlay
        recordingOverlay?.hide()

        // Stop foreground service
        stopForeground(true)

        // Scan file for gallery
        outputFile?.let { file ->
            if (file.exists()) {
                // Use MediaScanner to make the file visible in gallery
                val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                intent.data = android.net.Uri.fromFile(file)
                sendBroadcast(intent)
            }
        }

        // Send broadcast that recording stopped
        val broadcastIntent = Intent(ACTION_RECORDING_STOPPED).apply { setPackage(packageName) }
        sendBroadcast(broadcastIntent)

        // Stop service
        stopSelf()

        val duration = System.currentTimeMillis() - startTime
        val fileSize = outputFile?.length()
        CoroutineScope(Dispatchers.IO).launch {
            analyticsManager.logRecordingStop(duration, fileSize)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, StopRecordingReceiver::class.java)
        val stopPendingIntent = PendingIntent.getBroadcast(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Recording")
            .setContentText("Recording in progress...")
            .setSmallIcon(R.drawable.ic_recording)
            .setOngoing(true)
            .addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun togglePauseResume() {
        if (!isRecording || !pauseEnabled) return
        isPaused = !isPaused
        recordingOverlay?.setPaused(isPaused)
        if (isPaused) {
            pauseStartTime = System.currentTimeMillis()
            // Pause auto-stop timer
            autoStopJob?.cancel()
        } else {
            // Resume auto-stop timer
            val pausedDuration = System.currentTimeMillis() - pauseStartTime
            totalPausedTime += pausedDuration
            // Resume timer with remaining time
            GlobalScope.launch(Dispatchers.Main) {
                val minutes = settingsDataStore.autoStopMinutes.first()
                if (minutes > 0) {
                    val currentTime = System.currentTimeMillis()
                    val elapsed = (currentTime - startTime) - totalPausedTime
                    val remaining = (minutes.toLong() * 60L * 1000L) - elapsed
                    if (remaining > 0) {
                        autoStopJob?.cancel()
                        autoStopJob = GlobalScope.launch(Dispatchers.Main) {
                            delay(remaining)
                            if (isRecording && !isPaused) {
                                stopRecording()
                                Toast.makeText(this@ScreenRecorderService, "Recording auto-stopped after $minutes min", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            if (isPaused) {
                analyticsManager.logRecordingResume()
            } else {
                analyticsManager.logRecordingPause()
            }
        }
    }

    private fun toggleMicMute() {
        if (!isRecording || !micMuteEnabled) return
        isMicMuted = !isMicMuted
        recordingOverlay?.setMicMuted(isMicMuted)
        // MicRecorder should feed silence if muted
        micRecorder?.setMuted(isMicMuted)
    }

    private fun startHighlightDetection() {
        // Simple real-time volume spike detection on mic audio
        GlobalScope.launch(Dispatchers.IO) {
            val buffer = ShortArray(2048)
            val audioRecord = micRecorder?.audioRecord ?: return@launch
            val highlightThreshold = 20000 // Adjust as needed
            while (isRecording) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val max = buffer.take(read).maxOf { abs(it.toInt()) }
                    if (max > highlightThreshold) {
                        val timestamp = System.currentTimeMillis() - startTime
                        if (highlightTimestamps.isEmpty() || timestamp - highlightTimestamps.last() > 2000) {
                            highlightTimestamps.add(timestamp)
                            Log.d(TAG, "Highlight detected at $timestamp ms")
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(stopRecordingReceiver)
        unregisterReceiver(controlReceiver)
        stopRecording()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

private fun getVideoDurationSec(path: String): Int {
    return try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(path)
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        retriever.release()
        (durationMs / 1000).toInt()
    } catch (e: Exception) { 0 }
}

private suspend fun logCrashlyticsIfEnabled(message: String, throwable: Throwable? = null, context: Context) {
    val settings = SettingsDataStore(context)
    val enabled = settings.crashReportingEnabled.first()
    if (enabled) {
        FirebaseCrashlytics.getInstance().log(message)
        throwable?.let { FirebaseCrashlytics.getInstance().recordException(it) }
    }
}
