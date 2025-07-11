package com.ibbie.catrec_gamingscreenrecorder.ui

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.ibbie.catrec_gamingscreenrecorder.R

class StopConfirmationDialog(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var dialogView: View? = null
    private var isShowing = false

    fun showConfirmationDialog(
        onConfirm: () -> Unit,
        onCancel: () -> Unit
    ) {
        if (isShowing) return
        isShowing = true

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(context)
        dialogView = inflater.inflate(R.layout.stop_confirmation_dialog, null)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
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

        val confirmButton = dialogView!!.findViewById<Button>(R.id.btn_confirm_stop)
        val cancelButton = dialogView!!.findViewById<Button>(R.id.btn_cancel_stop)

        confirmButton.setOnClickListener {
            hideDialog()
            onConfirm()
        }

        cancelButton.setOnClickListener {
            hideDialog()
            onCancel()
        }

        try {
            windowManager!!.addView(dialogView, params)
        } catch (e: Exception) {
            // Fallback to regular dialog if overlay permission not granted
            isShowing = false
            onCancel()
        }
    }

    private fun hideDialog() {
        if (!isShowing) return
        isShowing = false

        try {
            windowManager?.removeView(dialogView)
        } catch (_: Exception) { }
        dialogView = null
    }
} 