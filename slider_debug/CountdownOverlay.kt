package com.ibbie.catrec_gamingscreenrecorder.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.ibbie.catrec_gamingscreenrecorder.R

class CountdownOverlay(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isVisible = false
    private var countdownHandler: Handler? = null
    private var countdownRunnable: Runnable? = null

    @SuppressLint("InflateParams")
    fun showCountdown(seconds: Int, onComplete: () -> Unit) {
        if (isVisible) return
        isVisible = true

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(context)
        overlayView = inflater.inflate(R.layout.countdown_overlay, null)

        val layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

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

        val countdownText = overlayView!!.findViewById<TextView>(R.id.countdown_text)
        var currentCount = seconds

        countdownRunnable = object : Runnable {
            override fun run() {
                if (currentCount > 0) {
                    countdownText.text = currentCount.toString()
                    currentCount--
                    countdownHandler?.postDelayed(this, 1000)
                } else {
                    countdownText.text = "GO!"
                    countdownHandler?.postDelayed({
                        removeOverlay()
                        onComplete()
                    }, 500)
                }
            }
        }

        countdownHandler = Handler(Looper.getMainLooper())
        countdownHandler?.post(countdownRunnable!!)

        windowManager!!.addView(overlayView, params)
    }

    fun removeOverlay() {
        if (!isVisible) return
        isVisible = false

        countdownHandler?.removeCallbacksAndMessages(null)
        countdownHandler = null
        countdownRunnable = null

        try {
            windowManager?.removeView(overlayView)
        } catch (_: Exception) { }
        overlayView = null
    }
} 