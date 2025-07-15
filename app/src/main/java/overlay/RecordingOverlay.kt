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
import android.widget.RelativeLayout
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.util.DisplayMetrics
import com.ibbie.catrec_gamingscreenrecorder.R
import com.ibbie.catrec_gamingscreenrecorder.ScreenRecorderService
import com.ibbie.catrec_gamingscreenrecorder.SettingsDataStore
import com.ibbie.catrec_gamingscreenrecorder.ui.GestureToast
import com.ibbie.catrec_gamingscreenrecorder.ui.RecordingGestureDetector
import com.ibbie.catrec_gamingscreenrecorder.ui.StopConfirmationDialog
import kotlin.math.absoluteValue
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

class RecordingOverlay(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var closeZoneView: View? = null
    private var isShowing = false
    private var elapsedTimeHandler: Handler? = null
    private var elapsedTimeRunnable: Runnable? = null
    private var startTime: Long = 0L
    var onPauseResume: (() -> Unit)? = null
    private var isPaused = false
    var onMicMuteToggle: (() -> Unit)? = null
    private var isMicMuted = false
    
    // New overlay state
    private var isExpanded = false
    private var isDragging = false
    private var screenWidth = 0
    private var screenHeight = 0
    private var closeZoneShowing = false
    private var overlayInCloseZone = false
    private var layoutParams: WindowManager.LayoutParams? = null
    
    // Gesture controls
    private var gestureDetector: android.view.GestureDetector? = null
    private var gestureToast: GestureToast? = null
    private var stopConfirmationDialog: StopConfirmationDialog? = null
    private var settingsDataStore: SettingsDataStore? = null
    private var gestureControlsEnabled = false
    private val scope = CoroutineScope(Dispatchers.Main)

    fun show() {
        if (isShowing) return

        // Get screen dimensions
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()
        windowManager?.defaultDisplay?.getMetrics(displayMetrics)
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels

        val inflater = LayoutInflater.from(context)
        overlayView = inflater.inflate(R.layout.overlay_bubble, null)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        // Start position on right edge, vertically centered
        layoutParams!!.gravity = Gravity.TOP or Gravity.START
        layoutParams!!.x = screenWidth - 100 // Near right edge
        layoutParams!!.y = screenHeight / 2 - 50 // Vertically centered

        windowManager?.addView(overlayView, layoutParams)
        isShowing = true

        // Initialize gesture controls
        initializeGestureControls()

        // Set up bubble touch handling
        setupBubbleTouch()

        // Set up control buttons
        setupControlButtons()

        // Start elapsed time
        startTime = System.currentTimeMillis()
        startElapsedTimeTimer()

        // Update UI states
        updatePauseUI()
        updateMicMuteUI()
        updateRecordingIndicator()
        
        // Ensure overlay starts in minimized state
        setExpanded(false)
    }

    private fun setupBubbleTouch() {
        val bubbleContainer = overlayView?.findViewById<RelativeLayout>(R.id.bubble_container)
        bubbleContainer?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var dragStartTime = 0L
            private val dragThreshold = 20f

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                // Handle gestures if enabled
                if (gestureControlsEnabled && gestureDetector != null) {
                    val gestureHandled = gestureDetector!!.onTouchEvent(event)
                    if (gestureHandled) return true
                }

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams?.x ?: 0
                        initialY = layoutParams?.y ?: 0
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        dragStartTime = System.currentTimeMillis()
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
                            layoutParams?.let { params ->
                                params.x = initialX + deltaX
                                params.y = initialY + deltaY
                                windowManager?.updateViewLayout(overlayView, params)
                                
                                // Check if we're in bottom center area
                                checkBottomCenterPosition(params.x, params.y)
                            }
                        }
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        if (isDragging) {
                            handleDragEnd()
                        } else {
                            // Small movement, treat as tap
                            val tapDuration = System.currentTimeMillis() - dragStartTime
                            if (tapDuration < 300) { // Quick tap
                                toggleExpanded()
                            }
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun checkBottomCenterPosition(x: Int, y: Int) {
        val centerX = screenWidth / 2
        val bottomY = screenHeight - 200 // 200px from bottom
        
        val distanceFromCenter = abs(x - centerX)
        val isInBottomArea = y > bottomY
        val isInCenterArea = distanceFromCenter < 100 // 100px tolerance
        
        val shouldShowCloseZone = isInBottomArea && isInCenterArea
        
        if (shouldShowCloseZone && !closeZoneShowing) {
            showCloseZone()
        } else if (!shouldShowCloseZone && closeZoneShowing) {
            hideCloseZone()
        }
        
        // Update overlay color based on close zone proximity
        updateOverlayColorForCloseZone(shouldShowCloseZone)
    }

    private fun showCloseZone() {
        if (closeZoneShowing) return
        
        val inflater = LayoutInflater.from(context)
        closeZoneView = inflater.inflate(R.layout.close_zone, null)
        
        val closeZoneParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        
        closeZoneParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        closeZoneParams.y = 100 // 100px from bottom
        
        windowManager?.addView(closeZoneView, closeZoneParams)
        closeZoneShowing = true
    }

    private fun hideCloseZone() {
        if (!closeZoneShowing) return
        
        closeZoneView?.let { view ->
            windowManager?.removeView(view)
        }
        closeZoneView = null
        closeZoneShowing = false
    }

    private fun updateOverlayColorForCloseZone(inCloseZone: Boolean) {
        val bubbleContainer = overlayView?.findViewById<RelativeLayout>(R.id.bubble_container)
        overlayInCloseZone = inCloseZone
        
        if (inCloseZone) {
            bubbleContainer?.setBackgroundColor(Color.parseColor("#CCFF4444"))
        } else {
            bubbleContainer?.setBackgroundResource(R.drawable.bubble_background)
        }
    }

    private fun handleDragEnd() {
        if (overlayInCloseZone) {
            // Close the overlay
            hide()
            val intent = Intent("com.ibbie.ACTION_STOP_RECORDING")
            intent.setPackage(context.packageName)
            context.sendBroadcast(intent)
            return
        }
        
        // Magnetize to edges
        magnetizeToEdge()
        
        // Hide close zone
        hideCloseZone()
        updateOverlayColorForCloseZone(false)
    }

    private fun magnetizeToEdge() {
        layoutParams?.let { params ->
            val currentX = params.x
            val currentY = params.y
            val overlayWidth = overlayView?.width ?: 56
            val overlayHeight = overlayView?.height ?: 56
            
            // Calculate distances to edges
            val distanceToLeft = currentX
            val distanceToRight = screenWidth - currentX - overlayWidth
            val distanceToTop = currentY
            val distanceToBottom = screenHeight - currentY - overlayHeight
            
            // Find the closest edge
            val minDistance = minOf(distanceToLeft, distanceToRight, distanceToTop, distanceToBottom)
            
            val targetX: Int
            val targetY: Int
            
            when (minDistance) {
                distanceToLeft -> {
                    targetX = 0
                    targetY = currentY.coerceIn(0, screenHeight - overlayHeight)
                }
                distanceToRight -> {
                    targetX = screenWidth - overlayWidth
                    targetY = currentY.coerceIn(0, screenHeight - overlayHeight)
                }
                distanceToTop -> {
                    targetX = currentX.coerceIn(0, screenWidth - overlayWidth)
                    targetY = 0
                }
                else -> { // distanceToBottom
                    targetX = currentX.coerceIn(0, screenWidth - overlayWidth)
                    targetY = screenHeight - overlayHeight
                }
            }
            
            // Animate to target position
            animateToPosition(targetX, targetY)
        }
    }

    private fun animateToPosition(targetX: Int, targetY: Int) {
        layoutParams?.let { params ->
            val startX = params.x
            val startY = params.y
            
            val animator = ValueAnimator.ofFloat(0f, 1f)
            animator.duration = 300
            animator.addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                params.x = (startX + (targetX - startX) * fraction).toInt()
                params.y = (startY + (targetY - startY) * fraction).toInt()
                windowManager?.updateViewLayout(overlayView, params)
            }
            animator.start()
        }
    }

    private fun toggleExpanded() {
        setExpanded(!isExpanded)
    }

    private fun setExpanded(expanded: Boolean) {
        isExpanded = expanded
        val expandedControls = overlayView?.findViewById<LinearLayout>(R.id.expanded_controls)
        
        if (expanded) {
            expandedControls?.visibility = View.VISIBLE
            // Animate expansion
            expandedControls?.alpha = 0f
            expandedControls?.animate()
                ?.alpha(1f)
                ?.setDuration(200)
                ?.start()
        } else {
            expandedControls?.animate()
                ?.alpha(0f)
                ?.setDuration(200)
                ?.withEndAction {
                    expandedControls.visibility = View.GONE
                }
                ?.start()
        }
    }

    private fun setupControlButtons() {
        // Pause/Resume button
        val pauseBtn = overlayView?.findViewById<ImageView>(R.id.btn_pause)
        pauseBtn?.setOnClickListener {
            onPauseResume?.invoke()
        }

        // Mic toggle button
        val micBtn = overlayView?.findViewById<ImageView>(R.id.btn_mic)
        micBtn?.setOnClickListener {
            onMicMuteToggle?.invoke()
        }

        // Stop button
        val stopBtn = overlayView?.findViewById<ImageView>(R.id.btn_stop)
        stopBtn?.setOnClickListener {
            val intent = Intent("com.ibbie.ACTION_STOP_RECORDING")
            intent.setPackage(context.packageName)
            context.sendBroadcast(intent)
            hide()
        }
    }

    private fun startElapsedTimeTimer() {
        val elapsedTimeView = overlayView?.findViewById<TextView>(R.id.elapsed_time)
        elapsedTimeHandler = Handler(Looper.getMainLooper())
        elapsedTimeRunnable = object : Runnable {
            override fun run() {
                if (!isPaused) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val seconds = (elapsed / 1000) % 60
                    val minutes = (elapsed / (1000 * 60)) % 60
                    val hours = (elapsed / (1000 * 60 * 60))
                    elapsedTimeView?.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                }
                elapsedTimeHandler?.postDelayed(this, 1000)
            }
        }
        elapsedTimeHandler?.post(elapsedTimeRunnable!!)
    }

    private fun updateRecordingIndicator() {
        val recordingDot = overlayView?.findViewById<View>(R.id.recording_dot)
        val recordingIndicatorDot = overlayView?.findViewById<View>(R.id.recording_indicator_dot)
        
        // Animate recording dots
        recordingDot?.let { dot ->
            val animator = ObjectAnimator.ofFloat(dot, "alpha", 1f, 0.3f)
            animator.duration = 1000
            animator.repeatCount = ObjectAnimator.INFINITE
            animator.repeatMode = ObjectAnimator.REVERSE
            animator.start()
        }
        
        recordingIndicatorDot?.let { dot ->
            val animator = ObjectAnimator.ofFloat(dot, "alpha", 1f, 0.3f)
            animator.duration = 1000
            animator.repeatCount = ObjectAnimator.INFINITE
            animator.repeatMode = ObjectAnimator.REVERSE
            animator.start()
        }
    }

    fun hide() {
        if (!isShowing) return
        
        elapsedTimeHandler?.removeCallbacksAndMessages(null)
        elapsedTimeHandler = null
        elapsedTimeRunnable = null
        
        hideCloseZone()
        
        overlayView?.let { view ->
            windowManager?.removeView(view)
        }
        overlayView = null
        isShowing = false
        isExpanded = false
        isDragging = false
        closeZoneShowing = false
        overlayInCloseZone = false
    }

    fun setPaused(paused: Boolean) {
        isPaused = paused
        updatePauseUI()
    }

    private fun updatePauseUI() {
        val pausedLabel = overlayView?.findViewById<TextView>(R.id.paused_label)
        val pauseBtn = overlayView?.findViewById<ImageView>(R.id.btn_pause)
        
        if (isPaused) {
            pausedLabel?.visibility = View.VISIBLE
            pauseBtn?.setImageResource(R.drawable.ic_play) // Assuming you have a play icon
        } else {
            pausedLabel?.visibility = View.GONE
            pauseBtn?.setImageResource(R.drawable.ic_pause)
        }
    }

    fun setMicMuted(muted: Boolean) {
        isMicMuted = muted
        updateMicMuteUI()
    }

    private fun updateMicMuteUI() {
        val micBtn = overlayView?.findViewById<ImageView>(R.id.btn_mic)
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
