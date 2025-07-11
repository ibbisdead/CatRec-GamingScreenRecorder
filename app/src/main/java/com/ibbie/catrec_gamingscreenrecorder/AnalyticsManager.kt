package com.ibbie.catrec_gamingscreenrecorder

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.flow.first

class AnalyticsManager(private val context: Context) {
    private val analytics = FirebaseAnalytics.getInstance(context)
    private val settingsDataStore = SettingsDataStore(context)
    
    suspend fun logEvent(eventName: String, parameters: Map<String, String> = emptyMap()) {
        val enabled = settingsDataStore.analyticsEnabled.first()
        if (enabled) {
            val bundle = android.os.Bundle().apply {
                parameters.forEach { (key, value) ->
                    putString(key, value)
                }
            }
            analytics.logEvent(eventName, bundle)
        }
    }
    
    // Session events
    suspend fun logSessionStart() = logEvent("session_start")
    suspend fun logSessionEnd() = logEvent("session_end")
    
    // Recording events
    suspend fun logRecordingStart(duration: Long? = null) {
        val params = if (duration != null) mapOf("duration_seconds" to (duration / 1000).toString()) else emptyMap()
        logEvent("recording_start", params)
    }
    
    suspend fun logRecordingStop(duration: Long, fileSize: Long? = null) {
        val params = mutableMapOf("duration_seconds" to (duration / 1000).toString())
        fileSize?.let { params["file_size_mb"] = (it / (1024 * 1024)).toString() }
        logEvent("recording_stop", params)
    }
    
    suspend fun logRecordingPause() = logEvent("recording_pause")
    suspend fun logRecordingResume() = logEvent("recording_resume")
    
    // Highlight events
    suspend fun logHighlightDetected(timestamp: Long) {
        logEvent("highlight_detected", mapOf("timestamp_seconds" to (timestamp / 1000).toString()))
    }
    
    suspend fun logHighlightClipExtracted(clipLength: Int) {
        logEvent("highlight_clip_extracted", mapOf("clip_length_seconds" to clipLength.toString()))
    }
    
    suspend fun logHighlightReelGenerated(clipCount: Int) {
        logEvent("highlight_reel_generated", mapOf("clip_count" to clipCount.toString()))
    }
    
    // Auto-delete events
    suspend fun logAutoDeleteEnabled() = logEvent("auto_delete_enabled")
    suspend fun logAutoDeleteDisabled() = logEvent("auto_delete_disabled")
    suspend fun logAutoDeleteExecuted(deletedCount: Int) {
        logEvent("auto_delete_executed", mapOf("deleted_files" to deletedCount.toString()))
    }
    
    // Sharing events
    suspend fun logClipShared(singleClip: Boolean) {
        logEvent("clip_shared", mapOf("type" to if (singleClip) "single" else "batch"))
    }
    
    suspend fun logHighlightReelShared() = logEvent("highlight_reel_shared")
    
    // Settings events
    suspend fun logSettingChanged(settingName: String, newValue: String) {
        logEvent("setting_changed", mapOf("setting" to settingName, "value" to newValue))
    }
    
    suspend fun logThemeChanged(isDark: Boolean) {
        logEvent("theme_changed", mapOf("theme" to if (isDark) "dark" else "light"))
    }
    
    suspend fun logResolutionChanged(resolution: String) {
        logEvent("resolution_changed", mapOf("resolution" to resolution))
    }
    
    suspend fun logBitrateChanged(bitrate: Int) {
        logEvent("bitrate_changed", mapOf("bitrate_mbps" to (bitrate / 1000000).toString()))
    }
    
    suspend fun logAudioSourceChanged(source: String) {
        logEvent("audio_source_changed", mapOf("source" to source))
    }
    
    suspend fun logScheduledRecordingSet(recurrence: String) {
        logEvent("scheduled_recording_set", mapOf("recurrence" to recurrence))
    }
    
    suspend fun logScheduledRecordingCancelled() = logEvent("scheduled_recording_cancelled")
    
    // Test crash function for debugging crash reporting
    fun testCrash() {
        throw RuntimeException("Test crash for crash reporting verification")
    }
} 