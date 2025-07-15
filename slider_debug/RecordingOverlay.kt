package overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.ibbie.catrec_gamingscreenrecorder.R
import com.ibbie.catrec_gamingscreenrecorder.ScreenRecorderService
import com.ibbie.catrec_gamingscreenrecorder.SettingsDataStore
import com.ibbie.catrec_gamingscreenrecorder.ui.GestureToast
import com.ibbie.catrec_gamingscreenrecorder.ui.RecordingGestureDetector
import com.ibbie.catrec_gamingscreenrecorder.ui.StopConfirmationDialog
import kotlin.math.absoluteValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

class RecordingOverlay(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isShowing = false
    private var elapsedTimeHandler: Handler? = null
    private var elapsedTimeRunnable: Runnable? = null
    private var startTime: Long = 0L
    var onPauseResume: (() -> Unit)? = null
    private var isPaused = false
    var onMicMuteToggle: (() -> Unit)? = null
    private var isMicMuted = false
    
    // Gesture controls
    private var gestureDetector: android.view.GestureDetector? = null
    private var gestureToast: GestureToast? = null
    private var stopConfirmationDialog: StopConfirmationDialog? = null
    private var settingsDataStore: SettingsDataStore? = null
    private var gestureControlsEnabled = false
    private val scope = CoroutineScope(Dispatchers.Main)

    fun show() {
        if (isShowing) return

        val inflater = LayoutInflater.from(context)
        overlayView = inflater.inflate(R.layout.overlay_bubble, null)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        layoutParams.gravity = Gravity.TOP or Gravity.START
        layoutParams.x = 100
        layoutParams.y = 300

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager?.addView(overlayView, layoutParams)
        isShowing = true

        // Initialize gesture controls
        initializeGestureControls()

        // Pause/Resume button
        val pauseBtn = overlayView!!.findViewById<ImageView?>(R.id.btn_pause_overlay)
        pauseBtn?.setOnClickListener {
            onPauseResume?.invoke()
        }
        updatePauseUI()

        // Start elapsed time
        startTime = System.currentTimeMillis()
        val elapsedTimeView = overlayView!!.findViewById<TextView>(R.id.elapsed_time)
        elapsedTimeHandler = Handler(Looper.getMainLooper())
        elapsedTimeRunnable = object : Runnable {
            override fun run() {
                if (!isPaused) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val seconds = (elapsed / 1000) % 60
                    val minutes = (elapsed / (1000 * 60)) % 60
                    val hours = (elapsed / (1000 * 60 * 60))
                    elapsedTimeView.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                }
                elapsedTimeHandler?.postDelayed(this, 1000)
            }
        }
        elapsedTimeHandler?.post(elapsedTimeRunnable!!)

        // Mic mute/unmute button
        val micBtn = overlayView!!.findViewById<ImageView?>(R.id.btn_mic_overlay)
        micBtn?.setOnClickListener {
            onMicMuteToggle?.invoke()
        }
        updateMicMuteUI()

        // Handle drag and actions
        val bubble = overlayView!!.findViewById<ImageView>(R.id.overlay_bubble)
        bubble.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false
            private val dragThreshold = 20f

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                // Handle gestures if enabled
                if (gestureControlsEnabled && gestureDetector != null) {
                    val gestureHandled = gestureDetector!!.onTouchEvent(event)
                    if (gestureHandled) return true
                }

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = (event.rawX - initialTouchX).toInt()
                        val deltaY = (event.rawY - initialTouchY).toInt()
                        
                        // Check if we're dragging
                        if (!isDragging && (deltaX.absoluteValue > dragThreshold || deltaY.absoluteValue > dragThreshold)) {
                            isDragging = true
                        }
                        
                        if (isDragging) {
                            layoutParams.x = initialX + deltaX
                            layoutParams.y = initialY + deltaY
                            windowManager?.updateViewLayout(overlayView, layoutParams)
                        }
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            // Small movement, treat as tap
                            toggleControlPanel()
                        }
                        return true
                    }
                }
                return false
            }
        })

        setupControls()
    }

    private fun setupControls() {
        val stopButton = overlayView?.findViewById<View>(R.id.btn_stop)
        val pauseButton = overlayView?.findViewById<View>(R.id.btn_pause)
        val muteButton = overlayView?.findViewById<View>(R.id.btn_mic)

        stopButton?.setOnClickListener {
            val intent = Intent("com.ibbie.ACTION_STOP_RECORDING")
            intent.setPackage(context.packageName)
            context.sendBroadcast(intent)
            hide()
        }

        pauseButton?.setOnClickListener {
            val intent = Intent("com.ibbie.ACTION_TOGGLE_PAUSE")
            intent.setPackage(context.packageName)
            context.sendBroadcast(intent)
        }

        muteButton?.setOnClickListener {
            val intent = Intent("com.ibbie.ACTION_TOGGLE_MIC")
            intent.setPackage(context.packageName)
            context.sendBroadcast(intent)
        }
    }

    private fun toggleControlPanel() {
        val panel = overlayView?.findViewById<LinearLayout>(R.id.overlay_controls)
        panel?.visibility =
            if (panel?.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    fun hide() {
        if (!isShowing) return
        elapsedTimeHandler?.removeCallbacksAndMessages(null)
        elapsedTimeHandler = null
        elapsedTimeRunnable = null
        windowManager?.removeView(overlayView)
        overlayView = null
        isShowing = false
    }

    fun setPaused(paused: Boolean) {
        isPaused = paused
        updatePauseUI()
    }

    private fun updatePauseUI() {
        overlayView?.findViewById<View>(R.id.recording_indicator_container)?.alpha = if (isPaused) 0.5f else 1.0f
        overlayView?.findViewById<TextView>(R.id.paused_label)?.visibility = if (isPaused) View.VISIBLE else View.GONE
    }

    fun setMicMuted(muted: Boolean) {
        isMicMuted = muted
        updateMicMuteUI()
    }

    private fun updateMicMuteUI() {
        val micBtn = overlayView?.findViewById<ImageView>(R.id.btn_mic_overlay)
        val micLabel = overlayView?.findViewById<TextView>(R.id.mic_muted_label)
        if (isMicMuted) {
            micBtn?.setImageResource(R.drawable.ic_mic_off)
            micLabel?.visibility = View.VISIBLE
        } else {
            micBtn?.setImageResource(R.drawable.ic_mic)
            micLabel?.visibility = View.GONE
        }
    }

    private fun initializeGestureControls() {
        settingsDataStore = SettingsDataStore(context)
        gestureToast = GestureToast(context)
        stopConfirmationDialog = StopConfirmationDialog(context)
        
        scope.launch {
            gestureControlsEnabled = settingsDataStore!!.gestureControlsEnabled.first()
            updateGestureDetector()
        }
    }

    private fun updateGestureDetector() {
        if (gestureControlsEnabled) {
            val recordingGestureDetector = RecordingGestureDetector(
                onDoubleTap = { handleDoubleTap() },
                onSwipeUp = { handleSwipeUp() },
                onSwipeDown = { handleSwipeDown() }
            )
            gestureDetector = android.view.GestureDetector(context, recordingGestureDetector)
            
            // Show help toast for first-time users
            gestureToast?.showToast("Gesture Controls: Double-tap to pause, Swipe up for mic, Swipe down to stop", 3000L)
        } else {
            gestureDetector = null
        }
    }

    private fun handleDoubleTap() {
        gestureToast?.showToast("Pause/Resume")
        onPauseResume?.invoke()
    }

    private fun handleSwipeUp() {
        gestureToast?.showToast("Mic ${if (isMicMuted) "Unmuted" else "Muted"}")
        onMicMuteToggle?.invoke()
    }

    private fun handleSwipeDown() {
        stopConfirmationDialog?.showConfirmationDialog(
            onConfirm = {
                gestureToast?.showToast("Recording Stopped")
                val intent = Intent("com.ibbie.ACTION_STOP_RECORDING")
                intent.setPackage(context.packageName)
                context.sendBroadcast(intent)
                hide()
            },
            onCancel = {
                gestureToast?.showToast("Stop Cancelled")
            }
        )
    }

    fun refreshGestureControls() {
        scope.launch {
            gestureControlsEnabled = settingsDataStore!!.gestureControlsEnabled.first()
            updateGestureDetector()
        }
    }
}
