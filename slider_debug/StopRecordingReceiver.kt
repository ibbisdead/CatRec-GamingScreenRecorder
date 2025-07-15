package com.ibbie.catrec_gamingscreenrecorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StopRecordingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val serviceIntent = Intent(context, ScreenRecorderService::class.java).apply {
            action = "STOP_RECORDING"
        }
        context?.startService(serviceIntent)
    }
} 