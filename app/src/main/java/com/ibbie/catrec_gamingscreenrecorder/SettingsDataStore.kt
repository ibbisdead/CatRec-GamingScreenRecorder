package com.ibbie.catrec_gamingscreenrecorder

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import com.ibbie.catrec_gamingscreenrecorder.model.Settings

val Context.settingsDataStore by preferencesDataStore(name = "settings")

object SettingsKeys {
    val VIDEO_BITRATE = intPreferencesKey("video_bitrate")
    val RESOLUTION = stringPreferencesKey("resolution")
    val AUDIO_BITRATE = stringPreferencesKey("audio_bitrate")
    val AUDIO_SAMPLE_RATE = stringPreferencesKey("audio_sample_rate")
    val AUDIO_CHANNEL = stringPreferencesKey("audio_channel")
    val FPS = intPreferencesKey("fps")
    val MIC_VOLUME = intPreferencesKey("mic_volume")
    val PAUSE_ON_SCREEN_OFF = booleanPreferencesKey("pause_on_screen_off")
    val PAUSE_ENABLED = booleanPreferencesKey("pause_enabled")
    val MIC_MUTE_ENABLED = booleanPreferencesKey("mic_mute_enabled")
    val OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")

    val AUDIO_SOURCE = stringPreferencesKey("audio_source")
    val ORIENTATION = stringPreferencesKey("orientation")
    // Remove FILE_DESTINATION key
    val SHOW_TOUCHES = booleanPreferencesKey("show_touches")
    val COUNTDOWN = intPreferencesKey("countdown")
    val STOP_OPTIONS = stringSetPreferencesKey("stop_options")
    val THEME_DARK = booleanPreferencesKey("theme_dark")
    val AUTO_STOP_MINUTES = intPreferencesKey("auto_stop_minutes")
    val CLOUD_BACKUP_ENABLED = booleanPreferencesKey("cloud_backup_enabled")
    val CLOUD_BACKUP_PROVIDER = stringPreferencesKey("cloud_backup_provider")
    val AUTO_TRIM_ENABLED = booleanPreferencesKey("auto_trim_enabled")
    val AUTO_TRIM_START = intPreferencesKey("auto_trim_start_seconds")
    val AUTO_TRIM_END = intPreferencesKey("auto_trim_end_seconds")
    val GESTURE_CONTROLS_ENABLED = booleanPreferencesKey("gesture_controls_enabled")
    val SCHEDULED_RECORDING_ENABLED = booleanPreferencesKey("scheduled_recording_enabled")
    val SCHEDULED_RECORDING_TIME = longPreferencesKey("scheduled_recording_time")
    val SCHEDULED_RECURRENCE = stringPreferencesKey("scheduled_recurrence")
    val SCHEDULED_CUSTOM_DAYS = stringSetPreferencesKey("scheduled_custom_days")
    val AUTO_HIGHLIGHT_DETECTION = booleanPreferencesKey("auto_highlight_detection")
    val AUTO_HIGHLIGHT_CLIP_EXTRACTION = booleanPreferencesKey("auto_highlight_clip_extraction")
    val HIGHLIGHT_CLIP_LENGTH = intPreferencesKey("highlight_clip_length")
    val AUTO_HIGHLIGHT_REEL_GENERATION = booleanPreferencesKey("auto_highlight_reel_generation")
    val AUTO_DELETE_ENABLED = booleanPreferencesKey("auto_delete_enabled")
    val RETENTION_DAYS = intPreferencesKey("retention_days")
    val ANALYTICS_ENABLED = booleanPreferencesKey("analytics_enabled")
    val AUTO_UPDATE_CHECK_ENABLED = booleanPreferencesKey("auto_update_check_enabled")
    val CRASH_REPORTING_ENABLED = booleanPreferencesKey("crash_reporting_enabled")
}

class SettingsDataStore(internal val context: Context) {
    val videoBitrate: Flow<Int> = context.settingsDataStore.data.map { it[SettingsKeys.VIDEO_BITRATE] ?: 8000000 }
    val resolution: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.RESOLUTION] ?: "Native" }
    val audioBitrate: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.AUDIO_BITRATE] ?: "128 kbps" }
    val audioSampleRate: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.AUDIO_SAMPLE_RATE] ?: "44100 Hz" }
    val audioChannel: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.AUDIO_CHANNEL] ?: "Stereo" }
    val fps: Flow<Int> = context.settingsDataStore.data.map { it[SettingsKeys.FPS] ?: 60 }
    val micVolume: Flow<Int> = context.settingsDataStore.data.map { it[SettingsKeys.MIC_VOLUME] ?: 100 }
    val pauseOnScreenOff: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.PAUSE_ON_SCREEN_OFF] ?: false }
    val pauseEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.PAUSE_ENABLED] ?: false }
    val micMuteEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.MIC_MUTE_ENABLED] ?: false }
    val overlayEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.OVERLAY_ENABLED] ?: true }
    val analyticsEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.ANALYTICS_ENABLED] ?: false }
    val autoUpdateCheckEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.AUTO_UPDATE_CHECK_ENABLED] ?: true }
    val crashReportingEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.CRASH_REPORTING_ENABLED] ?: false }
    val audioSource: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.AUDIO_SOURCE] ?: "System Audio" }
    val orientation: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.ORIENTATION] ?: "Auto" }
    // Remove fileDestination flow - hardcoded to Movies/CatRec
    val showTouches: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.SHOW_TOUCHES] ?: false }
    val countdown: Flow<Int> = context.settingsDataStore.data.map { it[SettingsKeys.COUNTDOWN] ?: 0 }
    val stopOptions: Flow<Set<String>> = context.settingsDataStore.data.map { it[SettingsKeys.STOP_OPTIONS] ?: setOf("Screen Off") }
    val themeDark: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.THEME_DARK] ?: true }
    val autoStopMinutes: Flow<Int> = context.settingsDataStore.data.map { it[SettingsKeys.AUTO_STOP_MINUTES] ?: 0 }
    val cloudBackupEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.CLOUD_BACKUP_ENABLED] ?: false }
    val cloudBackupProvider: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.CLOUD_BACKUP_PROVIDER] ?: "Google Drive" }
    val autoTrimEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.AUTO_TRIM_ENABLED] ?: false }
    val autoTrimStartSeconds: Flow<Int> = context.settingsDataStore.data.map { it[SettingsKeys.AUTO_TRIM_START] ?: 0 }
    val autoTrimEndSeconds: Flow<Int> = context.settingsDataStore.data.map { it[SettingsKeys.AUTO_TRIM_END] ?: 0 }
    val gestureControlsEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.GESTURE_CONTROLS_ENABLED] ?: false }
    val scheduledRecordingEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.SCHEDULED_RECORDING_ENABLED] ?: false }
    val scheduledRecordingTime: Flow<Long> = context.settingsDataStore.data.map { it[SettingsKeys.SCHEDULED_RECORDING_TIME] ?: 0L }
    val scheduledRecurrence: Flow<String> = context.settingsDataStore.data.map { it[SettingsKeys.SCHEDULED_RECURRENCE] ?: "None" }
    val scheduledCustomDays: Flow<Set<String>> = context.settingsDataStore.data.map { it[SettingsKeys.SCHEDULED_CUSTOM_DAYS] ?: setOf() }
    val autoHighlightDetection: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.AUTO_HIGHLIGHT_DETECTION] ?: false }
    val autoHighlightClipExtraction: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.AUTO_HIGHLIGHT_CLIP_EXTRACTION] ?: false }
    val highlightClipLength: Flow<Int> = context.settingsDataStore.data.map { it[SettingsKeys.HIGHLIGHT_CLIP_LENGTH] ?: 15 }
    val autoHighlightReelGeneration: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.AUTO_HIGHLIGHT_REEL_GENERATION] ?: false }
    val autoDeleteEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[SettingsKeys.AUTO_DELETE_ENABLED] ?: false }
    val retentionDays: Flow<Int> = context.settingsDataStore.data.map { it[SettingsKeys.RETENTION_DAYS] ?: 30 }

    // Combined settings flow for ViewModel
    val settingsFlow: Flow<Settings> = context.settingsDataStore.data.map { preferences ->
        Settings(
            videoBitrate = preferences[SettingsKeys.VIDEO_BITRATE] ?: 8000000,
            resolution = preferences[SettingsKeys.RESOLUTION] ?: "Native",
            audioBitrate = preferences[SettingsKeys.AUDIO_BITRATE] ?: "128 kbps",
            audioSampleRate = preferences[SettingsKeys.AUDIO_SAMPLE_RATE] ?: "44100 Hz",
            audioChannel = preferences[SettingsKeys.AUDIO_CHANNEL] ?: "Stereo",
            fps = preferences[SettingsKeys.FPS] ?: 60,
            micVolume = preferences[SettingsKeys.MIC_VOLUME] ?: 100,
            pauseOnScreenOff = preferences[SettingsKeys.PAUSE_ON_SCREEN_OFF] ?: false,
            pauseEnabled = preferences[SettingsKeys.PAUSE_ENABLED] ?: false,
            micMuteEnabled = preferences[SettingsKeys.MIC_MUTE_ENABLED] ?: false,
            overlayEnabled = preferences[SettingsKeys.OVERLAY_ENABLED] ?: true,
            analyticsEnabled = preferences[SettingsKeys.ANALYTICS_ENABLED] ?: false,
            autoUpdateCheckEnabled = preferences[SettingsKeys.AUTO_UPDATE_CHECK_ENABLED] ?: true,
            crashReportingEnabled = preferences[SettingsKeys.CRASH_REPORTING_ENABLED] ?: false,
            audioSource = preferences[SettingsKeys.AUDIO_SOURCE] ?: "System Audio",
            orientation = preferences[SettingsKeys.ORIENTATION] ?: "Auto",
            // Remove fileDestination - hardcoded to Movies/CatRec
            showTouches = preferences[SettingsKeys.SHOW_TOUCHES] ?: false,
            countdown = preferences[SettingsKeys.COUNTDOWN] ?: 0,
            stopOptions = preferences[SettingsKeys.STOP_OPTIONS] ?: setOf("Screen Off"),
            themeDark = preferences[SettingsKeys.THEME_DARK] ?: true,
            isDarkTheme = preferences[SettingsKeys.THEME_DARK] ?: true,
            autoStopMinutes = preferences[SettingsKeys.AUTO_STOP_MINUTES] ?: 0,
            cloudBackupEnabled = preferences[SettingsKeys.CLOUD_BACKUP_ENABLED] ?: false,
            cloudBackupProvider = preferences[SettingsKeys.CLOUD_BACKUP_PROVIDER] ?: "Google Drive",
            autoTrimEnabled = preferences[SettingsKeys.AUTO_TRIM_ENABLED] ?: false,
            autoTrimStartSeconds = preferences[SettingsKeys.AUTO_TRIM_START] ?: 0,
            autoTrimEndSeconds = preferences[SettingsKeys.AUTO_TRIM_END] ?: 0,
            gestureControlsEnabled = preferences[SettingsKeys.GESTURE_CONTROLS_ENABLED] ?: false,
            scheduledRecordingEnabled = preferences[SettingsKeys.SCHEDULED_RECORDING_ENABLED] ?: false,
            scheduledRecordingTime = preferences[SettingsKeys.SCHEDULED_RECORDING_TIME] ?: 0L,
            scheduledRecurrence = preferences[SettingsKeys.SCHEDULED_RECURRENCE] ?: "None",
            scheduledCustomDays = preferences[SettingsKeys.SCHEDULED_CUSTOM_DAYS] ?: setOf(),
            autoHighlightDetection = preferences[SettingsKeys.AUTO_HIGHLIGHT_DETECTION] ?: false,
            autoHighlightClipExtraction = preferences[SettingsKeys.AUTO_HIGHLIGHT_CLIP_EXTRACTION] ?: false,
            highlightClipLength = preferences[SettingsKeys.HIGHLIGHT_CLIP_LENGTH] ?: 15,
            autoHighlightReelGeneration = preferences[SettingsKeys.AUTO_HIGHLIGHT_REEL_GENERATION] ?: false,
            autoDeleteEnabled = preferences[SettingsKeys.AUTO_DELETE_ENABLED] ?: false,
            retentionDays = preferences[SettingsKeys.RETENTION_DAYS] ?: 30
        )
    }

    // All setter functions
    suspend fun setAutoHighlightDetection(value: Boolean) = context.settingsDataStore.edit { it[SettingsKeys.AUTO_HIGHLIGHT_DETECTION] = value }
    suspend fun setAutoHighlightClipExtraction(value: Boolean) = context.settingsDataStore.edit { it[SettingsKeys.AUTO_HIGHLIGHT_CLIP_EXTRACTION] = value }
    suspend fun setHighlightClipLength(value: Int) = context.settingsDataStore.edit { it[SettingsKeys.HIGHLIGHT_CLIP_LENGTH] = value }
    suspend fun setAutoHighlightReelGeneration(value: Boolean) = context.settingsDataStore.edit { it[SettingsKeys.AUTO_HIGHLIGHT_REEL_GENERATION] = value }
    suspend fun setCountdown(value: Int) = context.settingsDataStore.edit { it[SettingsKeys.COUNTDOWN] = value }
    suspend fun setPauseOnScreenOff(value: Boolean) = context.settingsDataStore.edit { it[SettingsKeys.PAUSE_ON_SCREEN_OFF] = value }
    suspend fun setPauseEnabled(value: Boolean) = context.settingsDataStore.edit { it[SettingsKeys.PAUSE_ENABLED] = value }
    suspend fun setMicMuteEnabled(value: Boolean) = context.settingsDataStore.edit { it[SettingsKeys.MIC_MUTE_ENABLED] = value }
    suspend fun setOverlayEnabled(value: Boolean) = context.settingsDataStore.edit { it[SettingsKeys.OVERLAY_ENABLED] = value }
    suspend fun setGestureControlsEnabled(value: Boolean) = context.settingsDataStore.edit { it[SettingsKeys.GESTURE_CONTROLS_ENABLED] = value }
    suspend fun setCloudBackupEnabled(value: Boolean) = context.settingsDataStore.edit { it[SettingsKeys.CLOUD_BACKUP_ENABLED] = value }
    suspend fun setCloudBackupProvider(value: String) = context.settingsDataStore.edit { it[SettingsKeys.CLOUD_BACKUP_PROVIDER] = value }
    suspend fun setResolution(value: String) = context.settingsDataStore.edit { it[SettingsKeys.RESOLUTION] = value }
    suspend fun setVideoBitrate(value: Int) = context.settingsDataStore.edit { it[SettingsKeys.VIDEO_BITRATE] = value }
    suspend fun setFps(value: Int) = context.settingsDataStore.edit { it[SettingsKeys.FPS] = value }
    suspend fun setMicVolume(value: Int) = context.settingsDataStore.edit { it[SettingsKeys.MIC_VOLUME] = value }
    suspend fun setShowTouches(value: Boolean) = context.settingsDataStore.edit { it[SettingsKeys.SHOW_TOUCHES] = value }
    suspend fun setAutoTrimEnabled(value: Boolean) = context.settingsDataStore.edit { it[SettingsKeys.AUTO_TRIM_ENABLED] = value }
    suspend fun setAutoTrimStartSeconds(value: Int) = context.settingsDataStore.edit { it[SettingsKeys.AUTO_TRIM_START] = value }
    suspend fun setAutoTrimEndSeconds(value: Int) = context.settingsDataStore.edit { it[SettingsKeys.AUTO_TRIM_END] = value }
    suspend fun setScheduledRecordingEnabled(value: Boolean) = context.settingsDataStore.edit { it[SettingsKeys.SCHEDULED_RECORDING_ENABLED] = value }
    suspend fun setScheduledRecordingTime(value: Long) = context.settingsDataStore.edit { it[SettingsKeys.SCHEDULED_RECORDING_TIME] = value }
    suspend fun setScheduledRecurrence(value: String) = context.settingsDataStore.edit { it[SettingsKeys.SCHEDULED_RECURRENCE] = value }
    suspend fun setScheduledCustomDays(value: Set<String>) = context.settingsDataStore.edit { it[SettingsKeys.SCHEDULED_CUSTOM_DAYS] = value }
    suspend fun setAnalyticsEnabled(value: Boolean) = context.settingsDataStore.edit { it[SettingsKeys.ANALYTICS_ENABLED] = value }
    suspend fun setAutoUpdateCheckEnabled(value: Boolean) = context.settingsDataStore.edit { it[SettingsKeys.AUTO_UPDATE_CHECK_ENABLED] = value }
    suspend fun setCrashReportingEnabled(value: Boolean) = context.settingsDataStore.edit { it[SettingsKeys.CRASH_REPORTING_ENABLED] = value }
    
    // Function to schedule the initial recording
    suspend fun scheduleInitialRecording() {
        val enabled = scheduledRecordingEnabled.first()
        val time = scheduledRecordingTime.first()
        
        if (enabled && time > System.currentTimeMillis()) {
            val delay = time - System.currentTimeMillis()
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<ScheduledRecordingWorker>()
                .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
                .addTag("scheduled_recording")
                .build()
            
            androidx.work.WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
    suspend fun setOrientation(value: String) = context.settingsDataStore.edit { it[SettingsKeys.ORIENTATION] = value }
    suspend fun setAudioBitrate(value: String) = context.settingsDataStore.edit { it[SettingsKeys.AUDIO_BITRATE] = value }
    suspend fun setAudioSampleRate(value: String) = context.settingsDataStore.edit { it[SettingsKeys.AUDIO_SAMPLE_RATE] = value }
    suspend fun setAudioChannel(value: String) = context.settingsDataStore.edit { it[SettingsKeys.AUDIO_CHANNEL] = value }
    suspend fun setAudioSource(value: String) = context.settingsDataStore.edit { it[SettingsKeys.AUDIO_SOURCE] = value }
    suspend fun setStopOptions(value: Set<String>) = context.settingsDataStore.edit { it[SettingsKeys.STOP_OPTIONS] = value }
    suspend fun setAutoDeleteEnabled(value: Boolean) = context.settingsDataStore.edit { it[SettingsKeys.AUTO_DELETE_ENABLED] = value }
    suspend fun setRetentionDays(value: Int) = context.settingsDataStore.edit { it[SettingsKeys.RETENTION_DAYS] = value }
} 