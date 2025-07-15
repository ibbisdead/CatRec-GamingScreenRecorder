package com.ibbie.catrec_gamingscreenrecorder.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ibbie.catrec_gamingscreenrecorder.R

class ModernNotificationManager(private val context: Context) {
    
    companion object {
        private const val PERFORMANCE_CHANNEL_ID = "performance_warnings"
        private const val PERFORMANCE_CHANNEL_NAME = "Performance Warnings"
        private const val AUDIO_CHANNEL_ID = "audio_notifications"
        private const val AUDIO_CHANNEL_NAME = "Audio Notifications"
        private const val PERFORMANCE_NOTIFICATION_ID = 2001
        private const val AUDIO_NOTIFICATION_ID = 2002
    }
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    init {
        createNotificationChannels()
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val performanceChannel = NotificationChannel(
                PERFORMANCE_CHANNEL_ID,
                PERFORMANCE_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications about recording performance impact"
            }
            
            val audioChannel = NotificationChannel(
                AUDIO_CHANNEL_ID,
                AUDIO_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Audio recording status notifications"
            }
            
            notificationManager.createNotificationChannel(performanceChannel)
            notificationManager.createNotificationChannel(audioChannel)
        }
    }
    
    fun showPerformanceImpactWarning() {
        val notification = NotificationCompat.Builder(context, PERFORMANCE_CHANNEL_ID)
            .setContentTitle("Recording Performance Impact")
            .setContentText("Recording system + microphone audio may impact device performance during intensive tasks.")
            .setSmallIcon(R.drawable.ic_recording)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "Recording both system and microphone audio simultaneously may cause performance issues during gaming or other intensive tasks. " +
                "Consider using internal audio only for better performance."
            ))
            .build()
        
        notificationManager.notify(PERFORMANCE_NOTIFICATION_ID, notification)
    }
    
    fun showAudioLimitationNotification() {
        val notification = NotificationCompat.Builder(context, AUDIO_CHANNEL_ID)
            .setContentTitle("Audio Recording Limitation")
            .setContentText("Simultaneous mic + internal audio recording not supported on this device.")
            .setSmallIcon(R.drawable.ic_mic_off)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "Your device doesn't support simultaneous microphone and internal audio recording. " +
                "Recording will continue with internal audio only. This is a limitation of your Android version or device."
            ))
            .build()
        
        notificationManager.notify(AUDIO_NOTIFICATION_ID, notification)
    }
    
    fun dismissPerformanceWarning() {
        notificationManager.cancel(PERFORMANCE_NOTIFICATION_ID)
    }
    
    fun dismissAudioLimitationNotification() {
        notificationManager.cancel(AUDIO_NOTIFICATION_ID)
    }
}