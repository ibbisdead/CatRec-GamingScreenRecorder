package com.ibbie.catrec_gamingscreenrecorder.model

/**
 * Data class representing all app settings in a single object.
 * This provides a clean interface for the ViewModel to manage settings state.
 */
data class Settings(
    val videoBitrate: Int = 8000000,
    val resolution: String = "Native",
    val audioBitrate: String = "128 kbps",
    val audioSampleRate: String = "44100 Hz",
    val audioChannel: String = "Stereo",
    val fps: Int = 60,
    val micVolume: Int = 100,
    val recordingOverlay: Boolean = true,
    val pauseOnScreenOff: Boolean = false,
    val overlayEnabled: Boolean = false,
    val overlayButton: Boolean = true,
    val pauseEnabled: Boolean = false,
    val micMuteEnabled: Boolean = false,
    val analyticsEnabled: Boolean = false,
    val autoUpdateCheckEnabled: Boolean = true,
    val crashReportingEnabled: Boolean = false,
    val audioSource: String = "System Audio",
    val orientation: String = "Auto",
    val showTouches: Boolean = false,
    val countdown: Int = 0,
    val stopOptions: Set<String> = setOf("Screen Off"),
    val themeDark: Boolean = true,
    val isDarkTheme: Boolean = true,
    val autoStopMinutes: Int = 0,
    val cloudBackupEnabled: Boolean = false,
    val cloudBackupProvider: String = "Google Drive",
    val autoTrimEnabled: Boolean = false,
    val autoTrimStartSeconds: Int = 0,
    val autoTrimEndSeconds: Int = 0,
    val gestureControlsEnabled: Boolean = false,
    val scheduledRecordingEnabled: Boolean = false,
    val scheduledRecordingTime: Long = 0L,
    val scheduledRecurrence: String = "None",
    val scheduledCustomDays: Set<String> = setOf(),
    val autoHighlightDetection: Boolean = false,
    val autoHighlightClipExtraction: Boolean = false,
    val highlightClipLength: Int = 15,
    val autoHighlightReelGeneration: Boolean = false,
    val autoDeleteEnabled: Boolean = false,
    val retentionDays: Int = 30
) 