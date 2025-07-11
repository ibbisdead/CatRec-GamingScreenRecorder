package com.ibbie.catrec_gamingscreenrecorder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import android.provider.Settings
import android.media.projection.MediaProjectionManager
import android.app.Activity
import com.ibbie.catrec_gamingscreenrecorder.ui.CountdownOverlay

class ScheduledRecordingWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = SettingsDataStore(context)
        val countdown = settings.countdown.first()
        val orientation = settings.orientation.first()
        val recordMic = settings.audioSource.first() != "None"
        val recordInternal = settings.audioSource.first() == "System Audio"
        val resolution = settings.resolution.first()
        val bitrate = settings.videoBitrate.first()
        // Check overlay permission
        if (!hasOverlayPermission(context)) {
            showPermissionNotification(context, "Overlay permission required for scheduled recording.")
            return Result.failure()
        }
        // Check screen capture permission (requires user interaction)
        if (!hasScreenCapturePermission(context)) {
            showPermissionNotification(context, "Screen capture permission required for scheduled recording.")
            return Result.failure()
        }
        // Show notification that scheduled recording is starting
        showScheduledRecordingNotification(context)
        // Launch countdown overlay if enabled
        if (countdown > 0) {
            val overlay = CountdownOverlay(context)
            overlay.showCountdown(countdown) {
                startRecordingService(context, orientation, recordMic, recordInternal, resolution, bitrate)
            }
        } else {
            startRecordingService(context, orientation, recordMic, recordInternal, resolution, bitrate)
        }
        return Result.success()
    }

    private fun startRecordingService(
        context: Context,
        orientation: String,
        recordMic: Boolean,
        recordInternal: Boolean,
        resolution: String,
        bitrate: Int
    ) {
        val intent = Intent(context, ScreenRecorderService::class.java).apply {
            action = "START_RECORDING"
            putExtra("orientation", orientation)
            putExtra("recordMic", recordMic)
            putExtra("recordInternalAudio", recordInternal)
            putExtra("resolution", resolution)
            putExtra("bitrate", bitrate)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun showScheduledRecordingNotification(context: Context) {
        val channelId = "scheduled_recording"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Scheduled Recording", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val stopIntent = Intent(context, StopRecordingReceiver::class.java)
        val stopPendingIntent = PendingIntent.getBroadcast(
            context, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Scheduled Recording Started")
            .setContentText("Your scheduled recording has started.")
            .setSmallIcon(R.drawable.ic_recording)
            .setOngoing(true)
            .addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
        nm.notify(2002, builder.build())
    }

    private fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    private fun hasScreenCapturePermission(context: Context): Boolean {
        // This is a stub: in reality, MediaProjection permission must be granted by user interaction
        // Here, always return false to force notification if not already granted
        // In a real app, you would persist the projection intent/token after user grants it
        return false
    }

    private fun showPermissionNotification(context: Context, message: String) {
        val channelId = "scheduled_recording_permission"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Scheduled Recording Permissions", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val overlayIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            data = android.net.Uri.parse("package:" + context.packageName)
        }
        val overlayPendingIntent = PendingIntent.getActivity(
            context, 0, overlayIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Scheduled Recording Blocked")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_recording)
            .setContentIntent(overlayPendingIntent)
            .setAutoCancel(true)
        nm.notify(2003, builder.build())
    }
} 