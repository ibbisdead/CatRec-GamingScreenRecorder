package com.ibbie.catrec_gamingscreenrecorder.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.ibbie.catrec_gamingscreenrecorder.R

class GestureToast(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var toastView: View? = null
    private var isShowing = false
    private val handler = Handler(Looper.getMainLooper())

    fun showToast(message: String, duration: Long = 1500L) {
        if (isShowing) {
            hideToast()
        }

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(context)
        toastView = inflater.inflate(R.layout.gesture_toast, null)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.CENTER
        params.y = 200 // Position above center

        val messageText = toastView!!.findViewById<TextView>(R.id.toast_message)
        messageText.text = message

        try {
            windowManager!!.addView(toastView, params)
            isShowing = true

            handler.postDelayed({
                hideToast()
            }, duration)
        } catch (e: Exception) {
            // Fallback to regular toast if overlay permission not granted
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun hideToast() {
        if (!isShowing) return
        isShowing = false

        try {
            windowManager?.removeView(toastView)
        } catch (_: Exception) { }
        toastView = null
    }
}

/**
 * Modern Compose-based gesture toast that uses StyledOverlayToast.
 * This provides a more consistent Material 3 design and better integration with Compose.
 */
@androidx.compose.runtime.Composable
fun GestureToastCompose(
    message: String,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
) {
    if (isVisible) {
        androidx.compose.animation.AnimatedVisibility(
            visible = isVisible,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(),
            modifier = modifier
        ) {
            Box(
                modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                StyledOverlayToast(
                    message = message,
                    modifier = androidx.compose.ui.Modifier
                        .padding(top = 100.dp)
                )
            }
        }
    }
} 