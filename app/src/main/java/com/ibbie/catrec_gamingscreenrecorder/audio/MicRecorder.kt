package com.ibbie.catrec_gamingscreenrecorder.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.Manifest

class MicRecorder(
    private val context: android.content.Context,
    private val outputFile: File,
    private val sampleRate: Int = 44100,
    private val enableNoiseSuppressor: Boolean = false,
    private val micVolume: Float = 1.0f, // 1.0 = 100%
    private val micTestMode: Boolean = false // When true, plays mic audio to speaker in real-time
) {
    companion object {
        private const val TAG = "MicRecorder"
    }

    internal var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var isPaused = false
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentVolume = micVolume
    private var currentNoiseSuppression = enableNoiseSuppressor
    private var noiseSuppressor: NoiseSuppressor? = null
    private var isMuted = false

    fun startRecording() {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }

        // Permission check for RECORD_AUDIO
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted.")
            return
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid minBufferSize: $minBufferSize")
            return
        }

        val bufferSize = minBufferSize * 4
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord not initialized")
            return
        }

        // Noise suppressor
        setupNoiseSuppressor()

        isRecording = true
        isPaused = false
        recordingJob = scope.launch {
            recordAudio(bufferSize)
        }
    }

    private fun setupNoiseSuppressor() {
        if (currentNoiseSuppression && NoiseSuppressor.isAvailable()) {
            try {
                noiseSuppressor = NoiseSuppressor.create(audioRecord!!.audioSessionId)
                noiseSuppressor?.enabled = true
                Log.d(TAG, "Noise suppressor enabled")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create noise suppressor", e)
            }
        }
    }

    fun pauseRecording() {
        isPaused = true
        Log.d(TAG, "Mic recording paused")
    }

    fun resumeRecording() {
        isPaused = false
        Log.d(TAG, "Mic recording resumed")
    }

    fun setVolume(volume: Int) {
        currentVolume = volume / 100f
        Log.d(TAG, "Mic volume set to: $volume%")
    }

    fun setNoiseSuppression(enabled: Boolean) {
        currentNoiseSuppression = enabled
        if (audioRecord != null) {
            setupNoiseSuppressor()
        }
        Log.d(TAG, "Noise suppression ${if (enabled) "enabled" else "disabled"}")
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
    }

    private suspend fun recordAudio(bufferSize: Int) {
        val buffer = ByteArray(bufferSize)
        val fileOutputStream = FileOutputStream(outputFile)

        try {
            audioRecord?.startRecording()
            Log.d(TAG, "Mic recording started")

            while (isRecording) {
                if (!isPaused) {
                    if (isMuted) {
                        // Feed silence
                        val silence = ByteArray(buffer.size) { 0 }
                        fileOutputStream.write(silence)
                    } else {
                        // Normal mic capture
                        val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        if (bytesRead > 0) {
                            applyVolume(buffer, bytesRead, currentVolume)
                            fileOutputStream.write(buffer, 0, bytesRead)

                            // For mic test: playback to speaker
                            if (micTestMode) {
                                // We could add AudioTrack-based playback here if needed later
                            }
                        }
                    }
                } else {
                    // When paused, just wait a bit
                    delay(10)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error recording mic audio", e)
        } finally {
            try {
                fileOutputStream.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing mic output file", e)
            }
        }
    }

    private fun applyVolume(buffer: ByteArray, length: Int, volume: Float) {
        if (volume == 1.0f) return

        for (i in 0 until length step 2) {
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
            val adjusted = (sample * volume).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            buffer[i] = adjusted.toByte()
            buffer[i + 1] = (adjusted.toInt() shr 8).toByte()
        }
    }

    fun stopRecording() {
        if (!isRecording) return

        isRecording = false
        isPaused = false
        recordingJob?.cancel()

        try {
            audioRecord?.stop()
            audioRecord?.release()
            noiseSuppressor?.release()
            noiseSuppressor = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping mic AudioRecord", e)
        } finally {
            audioRecord = null
        }
    }

    fun isRecording(): Boolean = isRecording
    fun getOutputFile(): File = outputFile
    fun cleanup() {
        stopRecording()
        scope.cancel()
    }
} 