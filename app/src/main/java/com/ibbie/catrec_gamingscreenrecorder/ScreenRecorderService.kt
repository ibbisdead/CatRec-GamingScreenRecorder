package com.ibbie.catrec_gamingscreenrecorder

import android.app.*
import android.app.Service.STOP_FOREGROUND_REMOVE
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
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
import android.media.MediaScannerConnection
import com.ibbie.catrec_gamingscreenrecorder.audio.CombinedAudioRecorder
import android.os.Handler
import android.os.Looper

class ScreenRecorderService : Service() {

    companion object {
        private var recordMic: Boolean = false
        private var recordInternal: Boolean = false
        private const val TAG = "ScreenRecorderService"
        private const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "screen_recorder_channel"
        private const val CHANNEL_NAME = "Screen Recorder"
        const val ACTION_RECORDING_STOPPED = "com.ibbie.RECORDING_STOPPED"
    }

    private var mediaProjection: MediaProjection? = null
    private var mediaProjectionCallback: MediaProjection.Callback? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaCodec: MediaCodec? = null
    private var mediaMuxer: MediaMuxer? = null
    // REMOVE: All references to RecordingOverlay, recordingOverlay variable, and overlay logic

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
    private var videoTrackIndex: Int = -1
    private var audioTrackIndex: Int = -1
    private var muxerStarted: Boolean = false
    private var audioFormatReady = false
    private var videoFormatReady = false

    private var micRecorder: MicRecorder? = null
    private var combinedAudioRecorder: CombinedAudioRecorder? = null
    private lateinit var audioEncoder: MediaCodec
    private val audioBufferInfo = MediaCodec.BufferInfo()

    private var isMicMuted = false
    private var micMuteEnabled = false
    private var highlightTimestamps = mutableListOf<Long>()
    private var autoHighlightDetection = false
    private var recordingFileName: String? = null

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopRecordingReceiver, IntentFilter("com.ibbie.ACTION_STOP_RECORDING"), Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(controlReceiver, IntentFilter().apply {
                addAction("com.ibbie.ACTION_STOP_RECORDING")
                addAction("com.ibbie.ACTION_TOGGLE_PAUSE")
                addAction("com.ibbie.ACTION_TOGGLE_MIC")
            }, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stopRecordingReceiver, IntentFilter("com.ibbie.ACTION_STOP_RECORDING"))
            registerReceiver(controlReceiver, IntentFilter().apply {
                addAction("com.ibbie.ACTION_STOP_RECORDING")
                addAction("com.ibbie.ACTION_TOGGLE_PAUSE")
                addAction("com.ibbie.ACTION_TOGGLE_MIC")
            })
        }
        // Read pauseEnabled, micMuteEnabled, and autoHighlightDetection from settings
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            pauseEnabled = settingsDataStore.pauseEnabled.first()
            micMuteEnabled = settingsDataStore.micMuteEnabled.first()
            autoHighlightDetection = settingsDataStore.autoHighlightDetection.first()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("RecordingDebug", "ScreenRecorderService onStartCommand called with action: ${intent?.action}")

        // Start foreground service immediately with proper type for MediaProjection
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(
                NOTIFICATION_ID,
                createNotification()
            )
        }

        when (intent?.action) {
            "START_RECORDING" -> {
                val resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED)
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra("data", Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra("data")
                }
                val width = intent.getIntExtra("width", 1920)
                val height = intent.getIntExtra("height", 1080)
                val density = intent.getIntExtra("density", 1)
                val bitrateValue = intent.getIntExtra("bitrate", 8000000)
                val orientation = intent.getStringExtra("orientation") ?: "Auto"

                Log.d("RecordingDebug", "Starting recording with resultCode: $resultCode, data: ${data != null}")
                if (data != null) {
                    startRecording(resultCode, data, width, height, density, bitrateValue, orientation)
                } else {
                    Log.e("RecordingDebug", "MediaProjection data is null, cannot start recording")
                }
            }
            "STOP_RECORDING" -> {
                Log.d("RecordingDebug", "Stopping recording")
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
        orientation: String
    ) {
        Log.d("RecordingDebug", "startRecording called with dimensions: ${width}x${height}, bitrate: $bitrateValue")
        if (isRecording) {
            Log.w("RecordingDebug", "Recording already in progress, ignoring start request")
            return
        }

        vibrateStart()

        // Generate timestamped file name
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        recordingFileName = "CatRec_${timestamp}.mp4"
        Log.d("RecordingDebug", "Recording filename: $recordingFileName")

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
        Log.d("RecordingDebug", "Target resolution: ${targetWidth}x${targetHeight}")

        // Auto-stop timer
        GlobalScope.launch(Dispatchers.Main) {
            val minutes = settingsDataStore.autoStopMinutes.first()
            if (minutes > 0) {
                Log.d("RecordingDebug", "Auto-stop timer set for $minutes minutes")
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
        // REMOVE: All references to RecordingOverlay, recordingOverlay variable, and overlay logic
        Log.d("RecordingDebug", "Recording overlay shown")

        screenWidth = targetWidth
        screenHeight = targetHeight
        screenDensity = density
        bitrate = bitrateValue

        // Create output file
        outputFile = File(getExternalFilesDir(null), recordingFileName ?: "CatRec_unknown.mp4")
        Log.d("RecordingDebug", "Output file created: ${outputFile?.absolutePath}")

        // Start audio recording with new combined system
        try {
            val sampleRate = 44100
            val channelCount = 1
            val bitRate = 128_000
            val audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount)
            audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            audioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            audioEncoder.start()

            mediaMuxer = MediaMuxer(outputFile!!.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxerStarted = false

            // Set up CombinedAudioRecorder with selected mode:
            val audioMode = when {
                recordMic && recordInternal -> CombinedAudioRecorder.AudioMode.BOTH
                recordMic -> CombinedAudioRecorder.AudioMode.MIC
                recordInternal -> CombinedAudioRecorder.AudioMode.SYSTEM
                else -> CombinedAudioRecorder.AudioMode.MIC // fallback
            }
            combinedAudioRecorder = CombinedAudioRecorder(
                context = this@ScreenRecorderService,
                mediaProjection = mediaProjection,
                mode = audioMode,
                sampleRate = sampleRate,
                onPcmData = { pcmBuffer ->
                    encodeAndMuxAudio(pcmBuffer)
                }
            )
            combinedAudioRecorder?.startRecording()

            // Show performance warning if recording both mic and internal audio
            if (recordMic && recordInternal) {
                val notificationManager = com.ibbie.catrec_gamingscreenrecorder.ui.ModernNotificationManager(this@ScreenRecorderService)
                notificationManager.showPerformanceImpactWarning()
            }

            Log.d("RecordingDebug", "Combined audio recording started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Combined audio recording failed: ${e.message}")
            CoroutineScope(Dispatchers.IO).launch {
                logCrashlyticsIfEnabled("Combined audio recording failed: ${e.message}", e, this@ScreenRecorderService)
            }
        }

        // Set up MediaProjection
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)
        if (mediaProjection != null) {
            Log.d("RecordingDebug", "MediaProjection created successfully")
            // Register MediaProjection callback BEFORE using the projection
            mediaProjectionCallback = object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d(TAG, "MediaProjection callback: onStop called")
                    Log.d("RecordingDebug", "MediaProjection stopped by system - this might indicate the recording was interrupted")
                    // Release virtual display and stop service
                    virtualDisplay?.release()
                    virtualDisplay = null
                    stopSelf()

                    // Finalize muxer and audio
                    audioEncoder.stop()
                    audioEncoder.release()
                    mediaMuxer?.stop()
                    mediaMuxer?.release()
                    combinedAudioRecorder?.stopRecording()
                    combinedAudioRecorder = null

                    // Update output file path for MediaStore
                    val mediaStoreOutputFile = File(getExternalFilesDir(null), recordingFileName ?: "CatRec_unknown.mp4")
                    val finalFileName = recordingFileName?.replace(".mp4", "_with_audio.mp4") ?: "CatRec_unknown_with_audio.mp4"
                    val outputFile = File(getExternalFilesDir(null), finalFileName)

                    // Mux mic audio with video using FFFFFFmpegKit
                    try {
                        Log.d("RecordingDebug", "Starting FFmpeg muxing...")
                        Log.d("RecordingDebug", "Video file: ${mediaStoreOutputFile.absolutePath} (exists: ${mediaStoreOutputFile.exists()}, size: ${mediaStoreOutputFile.length()} bytes)")
                        Log.d("RecordingDebug", "Audio file: ${outputFile.absolutePath} (exists: ${outputFile.exists()}, size: ${outputFile.length()} bytes)")
                        Log.d("RecordingDebug", "Output file: ${outputFile.absolutePath}")

                        val ffmpegCommand = "-i ${mediaStoreOutputFile.absolutePath} -i ${outputFile.absolutePath} -c:v copy -c:a aac -strict experimental ${outputFile.absolutePath}"
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
                                        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                                        val randomTime = if (durationMs > 0) (1000000L..(durationMs * 1000L - 1)).random() else 0L
                                        val frame = retriever.getFrameAtTime(randomTime, MediaMetadataRetriever.OPTION_CLOSEST)
                                        if (frame != null) {
                                            val thumbFile = File(outputFile.parent, outputFile.nameWithoutExtension + "_thumbnail.png")
                                            FileOutputStream(thumbFile).use { out ->
                                                frame.compress(Bitmap.CompressFormat.PNG, 100, out)
                                            }
                                            Log.d(TAG, "Thumbnail generated (random frame): ${thumbFile.absolutePath}")
                                        }
                                        retriever.release()
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Thumbnail generation failed: ${e.message}")
                                        CoroutineScope(Dispatchers.IO).launch {
                                            logCrashlyticsIfEnabled("Thumbnail generation failed: ${e.message}", e, this@ScreenRecorderService)
                                        }
                                    }
                                }
                                // Launch PlaybackActivity for preview
                                val playbackIntent = Intent(this@ScreenRecorderService, PlaybackActivity::class.java)
                                playbackIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                playbackIntent.putExtra("video_uri", outputFile?.absolutePath ?: "")
                                startActivity(playbackIntent)

                                // Cloud backup if enabled
                                GlobalScope.launch(Dispatchers.Main) {
                                    val backupEnabled = settingsDataStore.cloudBackupEnabled.first()
                                    val provider = settingsDataStore.cloudBackupProvider.first()
                                    if (backupEnabled) {
                                        val providerString = provider?.toString() ?: ""
                                        val data = workDataOf(
                                            "video_path" to (outputFile?.absolutePath ?: ""),
                                            "thumbnail_path" to (outputFile?.parent + "/" + outputFile?.nameWithoutExtension + "_thumbnail.png"),
                                            "provider" to providerString
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
                }
            }
        } else {
            Log.e("RecordingDebug", "Failed to create MediaProjection")
            return
        }

        // Set up MediaCodec
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, screenWidth, screenHeight)
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)

        mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        mediaCodec?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        Log.d("RecordingDebug", "MediaCodec configured successfully")

        // Set up MediaMuxer
        mediaMuxer = MediaMuxer(outputFile!!.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        Log.d("RecordingDebug", "MediaMuxer created successfully")

        // Set up VirtualDisplay
        val surface = mediaCodec?.createInputSurface()
        if (surface == null) {
            Log.e("RecordingDebug", "Failed to create input surface from MediaCodec")
            return
        }
        Log.d("RecordingDebug", "Input surface created successfully")

        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                // handle stop
            }
        }, Handler(Looper.getMainLooper()))

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenRecording",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface, null, null
        )
        if (virtualDisplay != null) {
            Log.d("RecordingDebug", "VirtualDisplay created successfully with dimensions: ${screenWidth}x${screenHeight}")
        } else {
            Log.e("RecordingDebug", "Failed to create VirtualDisplay")
            return
        }

        // Start recording
        mediaCodec?.start()
        isRecording = true
        startTime = System.currentTimeMillis()
        muxerStarted = false
        Log.d("RecordingDebug", "Recording started successfully, isRecording: $isRecording")

        // Show notification (already started in onStartCommand)
        Log.d("RecordingDebug", "Foreground service already started with notification")

        // Start encoding thread
        Thread {
            encodeVideo()
        }.start()
        Log.d("RecordingDebug", "Encoding thread started")

        CoroutineScope(Dispatchers.IO).launch {
            analyticsManager.logRecordingStart()
        }
    }

    private fun encodeVideo() {
        val bufferInfo = MediaCodec.BufferInfo()
        var frameCount = 0
        var lastLogTime = System.currentTimeMillis()
        Log.d("RecordingDebug", "Encoding thread started - waiting for video data...")
        while (isRecording) {
            if (isPaused) {
                Thread.sleep(100)
                continue
            }
            val outputBufferId = mediaCodec?.dequeueOutputBuffer(bufferInfo, 10000)
            when (outputBufferId) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // No output available yet
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastLogTime > 5000) { // Log every 5 seconds
                        Log.d("RecordingDebug", "Still waiting for video data... (frameCount: $frameCount)")
                        lastLogTime = currentTime
                    }
                }
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    Log.d("RecordingDebug", "MediaCodec output format changed")
                    if (!videoFormatReady) {
                        val format = mediaCodec?.outputFormat
                        if (format != null) {
                            videoTrackIndex = mediaMuxer?.addTrack(format) ?: -1
                            videoFormatReady = true
                            if (audioFormatReady && !muxerStarted) {
                                mediaMuxer?.start()
                                muxerStarted = true
                                Log.d("RecordingDebug", "MediaMuxer started successfully, videoTrackIndex: $videoTrackIndex, audioTrackIndex: $audioTrackIndex")
                            }
                        } else {
                            Log.e("RecordingDebug", "MediaCodec output format is null")
                        }
                    }
                }
                else -> {
                    if (outputBufferId != null && outputBufferId >= 0) {
                        val outputBuffer = mediaCodec?.getOutputBuffer(outputBufferId)
                        if (outputBuffer != null && muxerStarted) {
                            mediaMuxer?.writeSampleData(videoTrackIndex, outputBuffer, bufferInfo)
                            frameCount++
                            Log.d("RecordingDebug", "Wrote frame $frameCount, size: ${bufferInfo.size}")
                        } else {
                            if (!muxerStarted) {
                                Log.w("RecordingDebug", "Received video data but muxer not started yet")
                            }
                        }
                        mediaCodec?.releaseOutputBuffer(outputBufferId, false)
                    } else if (outputBufferId != null) {
                        Log.e("RecordingDebug", "Invalid output buffer ID: $outputBufferId")
                    }
                }
            }
        }
        Log.d("RecordingDebug", "Encoding thread finished, total frames processed: $frameCount")
    }

    private fun encodeAndMuxAudio(pcmData: ByteArray) {
        val inputBufferIndex = audioEncoder.dequeueInputBuffer(10000)
        if (inputBufferIndex >= 0) {
            val inputBuffer = audioEncoder.getInputBuffer(inputBufferIndex)
            inputBuffer?.clear()
            inputBuffer?.put(pcmData)
            val presentationTimeUs = System.nanoTime() / 1000
            audioEncoder.queueInputBuffer(inputBufferIndex, 0, pcmData.size, presentationTimeUs, 0)
        }
        while (true) {
            val outputBufferIndex = audioEncoder.dequeueOutputBuffer(audioBufferInfo, 0)
            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                break
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (!audioFormatReady) {
                    audioTrackIndex = mediaMuxer?.addTrack(audioEncoder.outputFormat) ?: -1
                    audioFormatReady = true
                    if (videoFormatReady && !muxerStarted) {
                        mediaMuxer?.start()
                        muxerStarted = true
                    }
                }
            } else if (outputBufferIndex >= 0) {
                if (muxerStarted) {
                    val encodedData = audioEncoder.getOutputBuffer(outputBufferIndex)
                    encodedData?.position(audioBufferInfo.offset)
                    encodedData?.limit(audioBufferInfo.offset + audioBufferInfo.size)
                    mediaMuxer?.writeSampleData(audioTrackIndex, encodedData!!, audioBufferInfo)
                }
                audioEncoder.releaseOutputBuffer(outputBufferIndex, false)
            }
        }
    }

    private fun stopRecording() {
        if (!isRecording) {
            Log.d(TAG, "stopRecording called but recording was not active")
            return
        }

        Log.d(TAG, "Stopping recording")
        vibrateStop()

        autoStopJob?.cancel()
        autoStopJob = null

        // Hide recording indicator overlay
        // REMOVE: All references to RecordingOverlay, recordingOverlay variable, and overlay logic

        isRecording = false
        isPaused = false
        pauseStartTime = 0L
        totalPausedTime = 0L

        // Stop audio recording
        try {
            combinedAudioRecorder?.stopRecording()
        } catch (e: Exception) {
            Log.e(TAG, "Combined audio stop failed: ${e.message}")
            CoroutineScope(Dispatchers.IO).launch {
                logCrashlyticsIfEnabled("Combined audio stop failed: ${e.message}", e, this@ScreenRecorderService)
            }
        }
        combinedAudioRecorder = null

        // Stop video encoding
        mediaCodec?.stop()
        mediaCodec?.release()
        virtualDisplay?.release()

        // Unregister MediaProjection callback with null checks
        try {
            mediaProjectionCallback?.let { callback ->
                    mediaProjection?.unregisterCallback(callback)
                    Log.d(TAG, "MediaProjection callback unregistered successfully")
            }
            mediaProjectionCallback = null
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering MediaProjection callback: ${e.message}")
            CoroutineScope(Dispatchers.IO).launch {
                logCrashlyticsIfEnabled("MediaProjection callback unregistration failed: ${e.message}", e, this@ScreenRecorderService)
            }
        }

        // Stop MediaProjection
        mediaProjection?.stop()

        // Stop muxer
        if (muxerStarted) {
            Log.d("RecordingDebug", "Stopping MediaMuxer")
            mediaMuxer?.stop()
            mediaMuxer?.release()
            Log.d("RecordingDebug", "MediaMuxer stopped and released")
        } else {
            Log.w("RecordingDebug", "MediaMuxer was not started, skipping stop")
        }

        // Mux audio with video using FFmpegKit - now using combined audio
        try {
            val videoFile = File(getExternalFilesDir(null), recordingFileName ?: "CatRec_unknown.mp4")
            val finalFileName = recordingFileName?.replace(".mp4", "_final.mp4") ?: "CatRec_unknown_final.mp4"
            val outputFile = File(getExternalFilesDir(null), finalFileName)

            Log.d("RecordingDebug", "Starting FFmpeg muxing with combined audio...")
            Log.d("RecordingDebug", "Video file: ${videoFile.absolutePath} (exists: ${videoFile.exists()}, size: ${videoFile.length()} bytes)")
            Log.d("RecordingDebug", "Audio file: ${outputFile.absolutePath} (exists: ${outputFile.exists()}, size: ${outputFile.length()} bytes)")
            Log.d("RecordingDebug", "Output file: ${outputFile.absolutePath}")

            // Only mux if we have both video and audio
            if (videoFile.exists() && outputFile.exists() && outputFile.length() > 0) {
                val ffmpegCommand = "-i ${videoFile.absolutePath} -i ${outputFile.absolutePath} -c:v copy -c:a aac -strict experimental ${outputFile.absolutePath}"
                FFmpegKit.executeAsync(ffmpegCommand) { session ->
                    val returnCode = session.returnCode
                    if (ReturnCode.isSuccess(returnCode)) {
                        Log.d(TAG, "Muxing succeeded: ${outputFile.absolutePath}")
                        handleSuccessfulMuxing(outputFile)
                    } else {
                        Log.e(TAG, "Muxing failed: ${session.failStackTrace}")
                        // Use video file as fallback
                        handleMuxingFailure(videoFile)
                    }
                }
            } else {
                // No audio or audio is empty, use video only
                Log.w(TAG, "No audio available, using video-only file")
                handleMuxingFailure(videoFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Muxing failed: ${e.message}")
            // Use video file as fallback
            val videoFile = File(getExternalFilesDir(null), recordingFileName ?: "CatRec_unknown.mp4")
            handleMuxingFailure(videoFile)
        }

        // Hide overlay
        // REMOVE: All references to RecordingOverlay, recordingOverlay variable, and overlay logic

        // Stop foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        // Scan file for gallery
        outputFile?.let { file ->
            if (file.exists()) {
                // Use MediaScannerConnection to make the file visible in gallery
                MediaScannerConnection.scanFile(
                    this,
                    arrayOf(file.absolutePath),
                    null,
                    null
                )
            }
        }

        // Send broadcast that recording stopped
        val broadcastIntent = Intent(ACTION_RECORDING_STOPPED).apply { setPackage(packageName ?: "") }
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

    private fun handleSuccessfulMuxing(outputFile: File) {
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
                        generateThumbnail(outputFile)
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
        generateThumbnail(outputFile)

        // Launch PlaybackActivity for preview
        val playbackIntent = Intent(this@ScreenRecorderService, PlaybackActivity::class.java)
        playbackIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        playbackIntent.putExtra("video_uri", outputFile?.absolutePath ?: "")
        startActivity(playbackIntent)

        // Cloud backup if enabled
        scheduleCloudBackup(outputFile)
    }

    private fun handleMuxingFailure(videoFile: File) {
        Log.w(TAG, "Using video-only file as fallback")

        // Use video file as final output
        val finalFileName = recordingFileName?.replace(".mp4", "_video_only.mp4") ?: "CatRec_unknown_video_only.mp4"
        val outputFile = File(getExternalFilesDir(null), finalFileName)

        try {
            if (videoFile.exists()) {
                videoFile.copyTo(outputFile, overwrite = true)
                handleSuccessfulMuxing(outputFile)
            } else {
                Log.e(TAG, "Video file doesn't exist, cannot create output")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create video-only fallback: ${e.message}")
        }
    }

    private fun generateThumbnail(outputFile: File) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(outputFile.absolutePath)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val randomTime = if (durationMs > 0) (1000000L..(durationMs * 1000L - 1)).random() else 0L
                val frame = retriever.getFrameAtTime(randomTime, MediaMetadataRetriever.OPTION_CLOSEST)
                if (frame != null) {
                    val thumbFile = File(outputFile.parent, outputFile.nameWithoutExtension + "_thumbnail.png")
                    FileOutputStream(thumbFile).use { out ->
                        frame.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    Log.d(TAG, "Thumbnail generated (random frame): ${thumbFile.absolutePath}")
                }
                retriever.release()
            } catch (e: Exception) {
                Log.e(TAG, "Thumbnail generation failed: ${e.message}")
                CoroutineScope(Dispatchers.IO).launch {
                    logCrashlyticsIfEnabled("Thumbnail generation failed: ${e.message}", e, this@ScreenRecorderService)
                }
            }
        }
    }

    private fun launchPlaybackActivity(outputFile: File) {
        val playbackIntent = Intent(this, PlaybackActivity::class.java)
        playbackIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        playbackIntent.putExtra("video_uri", outputFile.absolutePath)
        startActivity(playbackIntent)
    }

    private fun scheduleCloudBackup(outputFile: File?) {
        GlobalScope.launch(Dispatchers.Main) {
            val backupEnabled = settingsDataStore.cloudBackupEnabled.first()
            val provider = settingsDataStore.cloudBackupProvider.first()
            if (backupEnabled) {
                val providerString = provider?.toString() ?: ""
                val data = workDataOf(
                    "video_path" to (outputFile?.absolutePath ?: ""),
                    "thumbnail_path" to (outputFile?.parent + "/" + outputFile?.nameWithoutExtension + "_thumbnail.png"),
                    "provider" to providerString
                )
                val request = OneTimeWorkRequestBuilder<CloudBackupWorker>()
                    .setInputData(data)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
                WorkManager.getInstance(this@ScreenRecorderService).enqueue(request)
            }
        }
    }

    private fun togglePauseResume() {
        if (!isRecording || !pauseEnabled) return
        isPaused = !isPaused
        combinedAudioRecorder?.let { recorder ->
            if (isPaused) {
                recorder.pauseRecording()
            } else {
                recorder.resumeRecording()
            }
        }

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
                analyticsManager.logRecordingPause()
            } else {
                analyticsManager.logRecordingResume()
            }
        }
    }

    private fun toggleMicMute() {
        if (!isRecording || !micMuteEnabled) return
        isMicMuted = !isMicMuted
        combinedAudioRecorder?.setMuted(isMicMuted)
    }

    private fun updateNoiseSuppression(enabled: Boolean) {
        if (!isRecording) return
        micRecorder?.setNoiseSuppression(enabled)
        Log.d(TAG, "Noise suppression updated to: $enabled")
    }

    private fun startHighlightDetection() {
        // Simple real-time volume spike detection on combined audio
        GlobalScope.launch(Dispatchers.IO) {
            val highlightThreshold = 20000 // Adjust as needed
            while (isRecording) {
                delay(100) // Check every 100ms

                // For now, we'll use a simplified approach
                // In a real implementation, you might want to access the audio data directly
                // from the CombinedAudioRecorder

                // Placeholder for volume spike detection
                // This would need to be implemented based on actual audio level monitoring
                val timestamp = System.currentTimeMillis() - startTime

                // Simulate highlight detection (replace with real implementation)
                if (timestamp > 0 && timestamp % 30000 == 0L) { // Every 30 seconds for demo
                    if (highlightTimestamps.isEmpty() || timestamp - highlightTimestamps.last() > 5000) {
                        highlightTimestamps.add(timestamp)
                        Log.d(TAG, "Highlight detected at $timestamp ms")
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister MediaProjection callback if registered
        try {
            mediaProjectionCallback?.let { cb ->
                mediaProjection?.unregisterCallback(cb)
                Log.d(TAG, "MediaProjection callback unregistered in onDestroy")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering MediaProjection callback in onDestroy: ${e.message}")
        }
        mediaProjectionCallback = null
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
