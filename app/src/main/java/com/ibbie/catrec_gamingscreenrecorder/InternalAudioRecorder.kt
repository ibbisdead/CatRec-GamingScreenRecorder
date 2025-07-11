package com.ibbie.catrec_gamingscreenrecorder

import android.content.Context
import android.media.*
import android.media.projection.MediaProjection
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.Manifest

class InternalAudioRecorder(
    private val context: Context,
    private val mediaProjection: MediaProjection,
    private val pcmFile: File,
    private val videoFile: File,
    private val sampleRate: Int = 44100
) {
    companion object {
        private const val TAG = "InternalAudioRecorder"
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var isPaused = false
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val aacFile: File by lazy {
        File(pcmFile.parent, pcmFile.nameWithoutExtension + ".aac")
    }

    private val outputFile: File by lazy {
        File(videoFile.parent, videoFile.nameWithoutExtension + "_final.mp4")
    }

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

        try {
            val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val minBufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "Invalid minBufferSize: $minBufferSize")
                return
            }

            val bufferSize = minBufferSize * 4

            audioRecord = AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(config)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .build()

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized.")
                return
            }

            isRecording = true
            isPaused = false

            recordingJob = scope.launch {
                recordAudio()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting internal audio recording", e)
            throw e
        }
    }

    fun pauseRecording() {
        isPaused = true
        Log.d(TAG, "Internal audio recording paused")
    }

    fun resumeRecording() {
        isPaused = false
        Log.d(TAG, "Internal audio recording resumed")
    }

    private suspend fun recordAudio() {
        val buffer = ByteArray(4096)
        val fileOutputStream = FileOutputStream(pcmFile)

        try {
            audioRecord?.startRecording()
            Log.d(TAG, "Internal audio recording started")

            while (isRecording) {
                if (!isPaused) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                    if (bytesRead > 0) {
                        fileOutputStream.write(buffer, 0, bytesRead)
                    } else if (bytesRead < 0) {
                        Log.e(TAG, "Error reading audio data: $bytesRead")
                        break
                    }
                } else {
                    // When paused, just wait a bit
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

    fun stopRecording(onComplete: (File?) -> Unit) {
        if (!isRecording) return

        isRecording = false
        isPaused = false
        recordingJob?.cancel()

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        } finally {
            audioRecord = null
        }

        scope.launch {
            try {
                encodePcmToAac()
                muxAudioWithVideo()
                withContext(Dispatchers.Main) {
                    onComplete(outputFile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Post-processing failed", e)
                withContext(Dispatchers.Main) {
                    onComplete(null)
                }
            }
        }
    }

    private suspend fun encodePcmToAac() {
        val cmd = "-f s16le -ar $sampleRate -ac 2 -i \"${pcmFile.absolutePath}\" -c:a aac -b:a 192k -y \"${aacFile.absolutePath}\""
        val session = FFmpegKit.execute(cmd)

        if (ReturnCode.isSuccess(session.returnCode)) {
            Log.d(TAG, "AAC encoding succeeded")
        } else {
            throw IOException("AAC encoding failed: ${session.failStackTrace}")
        }
    }

    private suspend fun muxAudioWithVideo() {
        val cmd = "-i \"${videoFile.absolutePath}\" -i \"${aacFile.absolutePath}\" -c copy -map 0:v:0 -map 1:a:0 -y \"${outputFile.absolutePath}\""
        val session = FFmpegKit.execute(cmd)

        if (ReturnCode.isSuccess(session.returnCode)) {
            Log.d(TAG, "Muxing succeeded: ${outputFile.absolutePath}")
        } else {
            throw IOException("Muxing failed: ${session.failStackTrace}")
        }
    }

    fun cleanup() {
        stopRecording { }
        scope.cancel()
    }
}
