package com.ibbie.catrec_gamingscreenrecorder.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import kotlinx.coroutines.*

class CombinedAudioRecorder(
    private val context: Context,
    private val mediaProjection: MediaProjection?,
    val mode: AudioMode,
    private val sampleRate: Int = 44100,
    private val bufferSize: Int = 2048,
    private val onPcmData: (pcm: ByteArray) -> Unit
) {
    enum class AudioMode { MIC, SYSTEM, BOTH }

    private var micRecorder: AudioRecord? = null
    private var sysRecorder: AudioRecord? = null
    private var recordingJob: Job? = null
    private var isRecording = false

    val isRecordingPublic: Boolean
        get() = isRecording

    fun startRecording() {
        if (isRecording) return
        isRecording = true
        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            when (mode) {
                AudioMode.MIC -> recordMic()
                AudioMode.SYSTEM -> recordSystem()
                AudioMode.BOTH -> recordAndMix()
            }
        }
    }

    fun stopRecording() {
        isRecording = false
        recordingJob?.cancel()
        micRecorder?.stop(); micRecorder?.release(); micRecorder = null
        sysRecorder?.stop(); sysRecorder?.release(); sysRecorder = null
    }

    fun pauseRecording() {}
    fun resumeRecording() {}
    fun setMuted(muted: Boolean) {}

    private fun recordMic() {
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        micRecorder = AudioRecord(
            MediaRecorder.AudioSource.MIC, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            minBuffer
        )
        val buffer = ByteArray(bufferSize)
        micRecorder?.startRecording()
        while (isRecording) {
            val read = micRecorder?.read(buffer, 0, buffer.size) ?: 0
            if (read > 0) onPcmData(buffer.copyOf(read))
        }
    }

    private fun recordSystem() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || mediaProjection == null) return
        val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .build()
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        sysRecorder = AudioRecord.Builder()
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuffer)
            .setAudioPlaybackCaptureConfig(config)
            .build()
        val buffer = ByteArray(bufferSize)
        sysRecorder?.startRecording()
        while (isRecording) {
            val read = sysRecorder?.read(buffer, 0, buffer.size) ?: 0
            if (read > 0) onPcmData(buffer.copyOf(read))
        }
    }

    private fun recordAndMix() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || mediaProjection == null) return
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        micRecorder = AudioRecord(
            MediaRecorder.AudioSource.MIC, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            minBuffer
        )
        val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .build()
        sysRecorder = AudioRecord.Builder()
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuffer)
            .setAudioPlaybackCaptureConfig(config)
            .build()
        val micBuffer = ByteArray(bufferSize)
        val sysBuffer = ByteArray(bufferSize)
        micRecorder?.startRecording()
        sysRecorder?.startRecording()
        while (isRecording) {
            val micRead = micRecorder?.read(micBuffer, 0, micBuffer.size) ?: 0
            val sysRead = sysRecorder?.read(sysBuffer, 0, sysBuffer.size) ?: 0
            if (micRead > 0 && sysRead > 0) {
                val mixed = mixPcm(micBuffer, sysBuffer, micRead)
                onPcmData(mixed)
            } else if (micRead > 0) {
                onPcmData(micBuffer.copyOf(micRead))
            } else if (sysRead > 0) {
                onPcmData(sysBuffer.copyOf(sysRead))
            }
        }
    }

    private fun mixPcm(buf1: ByteArray, buf2: ByteArray, len: Int): ByteArray {
        val out = ByteArray(len)
        for (i in 0 until len step 2) {
            val s1 = (((buf1.getOrNull(i + 1)?.toInt() ?: 0) and 0xFF) shl 8) or ((buf1.getOrNull(i)?.toInt() ?: 0) and 0xFF)
            val s2 = (((buf2.getOrNull(i + 1)?.toInt() ?: 0) and 0xFF) shl 8) or ((buf2.getOrNull(i)?.toInt() ?: 0) and 0xFF)
            var mixed = s1 + s2
            if (mixed > Short.MAX_VALUE.toInt()) mixed = Short.MAX_VALUE.toInt()
            if (mixed < Short.MIN_VALUE.toInt()) mixed = Short.MIN_VALUE.toInt()
            out[i] = (mixed and 0xFF).toByte()
            out[i + 1] = ((mixed shr 8) and 0xFF).toByte()
        }
        return out
    }
}