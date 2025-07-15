package com.ibbie.catrec_gamingscreenrecorder.audio

import android.content.Context
import android.media.*
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.Manifest
import android.widget.Toast

class CombinedAudioRecorder(
    private val context: Context,
    private val mediaProjection: MediaProjection?,
    private val outputFile: File,
    private val sampleRate: Int = 44100,
    private val enableMic: Boolean = true,
    private val enableInternal: Boolean = true,
    private val micVolume: Float = 1.0f,
    private val enableNoiseSuppression: Boolean = false
) {
    companion object {
        private const val TAG = "CombinedAudioRecorder"
    }

    private var micRecorder: AudioRecord? = null
    private var internalRecorder: AudioRecord? = null
    private var isRecording = false
    private var isPaused = false
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isMicMuted = false
    private var simultaneousRecordingSupported = false

    fun startRecording() {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }

        // Check if simultaneous recording is supported
        simultaneousRecordingSupported = checkSimultaneousRecordingSupport()
        
        if (simultaneousRecordingSupported) {
            Log.i(TAG, "Starting simultaneous mic + internal audio recording")
            startSimultaneousRecording()
        } else {
            Log.i(TAG, "Simultaneous recording not supported, falling back to internal audio only")
            showSimultaneousRecordingNotification()
            startInternalOnlyRecording()
        }
    }

    private fun checkSimultaneousRecordingSupport(): Boolean {
        // Check Android version (API 29+ generally supports playback capture)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false
        }

        // Check if we have media projection for internal audio
        if (mediaProjection == null || !enableInternal) {
            return false
        }

        // Check microphone permission
        if (enableMic && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return false
        }

        // Check if device supports audio playback capture
        try {
            val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .build()
            
            // Try to create a test AudioRecord to see if it works
            val testRecord = AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(config)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                        .build()
                )
                .build()
            
            val supported = testRecord.state == AudioRecord.STATE_INITIALIZED
            testRecord.release()
            return supported
        } catch (e: Exception) {
            Log.e(TAG, "Error checking simultaneous recording support", e)
            return false
        }
    }

    private fun startSimultaneousRecording() {
        try {
            // Set up internal audio recording
            if (enableInternal && mediaProjection != null) {
                setupInternalRecording()
            }

            // Set up microphone recording
            if (enableMic) {
                setupMicRecording()
            }

            isRecording = true
            isPaused = false

            recordingJob = scope.launch {
                recordAudioSimultaneous()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting simultaneous recording", e)
            fallbackToInternalOnly()
        }
    }

    private fun startInternalOnlyRecording() {
        try {
            if (enableInternal && mediaProjection != null) {
                setupInternalRecording()
            }

            isRecording = true
            isPaused = false

            recordingJob = scope.launch {
                recordInternalAudioOnly()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting internal audio recording", e)
            throw e
        }
    }

    private fun setupInternalRecording() {
        val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        internalRecorder = AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(config)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize * 4)
            .build()
    }

    private fun setupMicRecording() {
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        micRecorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize * 4
        )
    }

    private suspend fun recordAudioSimultaneous() {
        val micBuffer = ShortArray(4096)
        val internalBuffer = ShortArray(4096)
        val mixedBuffer = ShortArray(4096)
        val fileOutputStream = FileOutputStream(outputFile)

        try {
            micRecorder?.startRecording()
            internalRecorder?.startRecording()
            Log.d(TAG, "Simultaneous recording started")

            while (isRecording) {
                if (!isPaused) {
                    // Read from both sources
                    val micSamplesRead = if (enableMic && !isMicMuted) {
                        micRecorder?.read(micBuffer, 0, micBuffer.size) ?: 0
                    } else {
                        0
                    }

                    val internalSamplesRead = if (enableInternal) {
                        internalRecorder?.read(internalBuffer, 0, internalBuffer.size) ?: 0
                    } else {
                        0
                    }

                    // Mix the audio sources
                    val samplesToProcess = maxOf(micSamplesRead, internalSamplesRead)
                    if (samplesToProcess > 0) {
                        mixAudioSources(micBuffer, internalBuffer, mixedBuffer, samplesToProcess, micSamplesRead, internalSamplesRead)
                        
                        // Convert to byte array and write
                        val byteBuffer = ByteArray(samplesToProcess * 2)
                        for (i in 0 until samplesToProcess) {
                            val sample = mixedBuffer[i]
                            byteBuffer[i * 2] = (sample and 0xFF).toByte()
                            byteBuffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                        }
                        fileOutputStream.write(byteBuffer, 0, samplesToProcess * 2)
                    }
                } else {
                    // When paused, just wait a bit
                    delay(10)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during simultaneous recording", e)
        } finally {
            try {
                fileOutputStream.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing file output stream", e)
            }
        }
    }

    private suspend fun recordInternalAudioOnly() {
        val buffer = ShortArray(4096)
        val fileOutputStream = FileOutputStream(outputFile)

        try {
            internalRecorder?.startRecording()
            Log.d(TAG, "Internal audio recording started")

            while (isRecording) {
                if (!isPaused) {
                    val samplesRead = internalRecorder?.read(buffer, 0, buffer.size) ?: 0
                    if (samplesRead > 0) {
                        // Convert to byte array and write
                        val byteBuffer = ByteArray(samplesRead * 2)
                        for (i in 0 until samplesRead) {
                            val sample = buffer[i]
                            byteBuffer[i * 2] = (sample and 0xFF).toByte()
                            byteBuffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                        }
                        fileOutputStream.write(byteBuffer, 0, samplesRead * 2)
                    }
                } else {
                    delay(10)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during internal audio recording", e)
        } finally {
            try {
                fileOutputStream.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing file output stream", e)
            }
        }
    }

    private fun mixAudioSources(
        micBuffer: ShortArray,
        internalBuffer: ShortArray,
        mixedBuffer: ShortArray,
        samplesToProcess: Int,
        micSamplesRead: Int,
        internalSamplesRead: Int
    ) {
        for (i in 0 until samplesToProcess) {
            val micSample = if (i < micSamplesRead) {
                (micBuffer[i] * micVolume).toInt()
            } else {
                0
            }

            val internalSample = if (i < internalSamplesRead) {
                internalBuffer[i].toInt()
            } else {
                0
            }

            // Mix the samples with proper clipping
            val mixed = micSample + internalSample
            mixedBuffer[i] = mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun fallbackToInternalOnly() {
        Log.w(TAG, "Falling back to internal audio only due to error")
        showSimultaneousRecordingNotification()
        startInternalOnlyRecording()
    }

    private fun showSimultaneousRecordingNotification() {
        scope.launch(Dispatchers.Main) {
            val notificationManager = com.ibbie.catrec_gamingscreenrecorder.ui.ModernNotificationManager(context)
            notificationManager.showAudioLimitationNotification()
        }
    }

    fun pauseRecording() {
        isPaused = true
        Log.d(TAG, "Combined audio recording paused")
    }

    fun resumeRecording() {
        isPaused = false
        Log.d(TAG, "Combined audio recording resumed")
    }

    fun setMuted(muted: Boolean) {
        isMicMuted = muted
        Log.d(TAG, "Mic muted: $muted")
    }

    fun stopRecording() {
        if (!isRecording) return

        isRecording = false
        isPaused = false
        recordingJob?.cancel()

        try {
            micRecorder?.let { recorder ->
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
                recorder.release()
            }
            
            internalRecorder?.let { recorder ->
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
                recorder.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio recorders", e)
        } finally {
            micRecorder = null
            internalRecorder = null
        }

        Log.d(TAG, "Combined audio recording stopped")
    }

    fun cleanup() {
        stopRecording()
        scope.cancel()
    }

    fun isRecording(): Boolean = isRecording
    fun getOutputFile(): File = outputFile
}