package com.ibbie.catrec_gamingscreenrecorder.ui

import android.view.GestureDetector
import android.view.MotionEvent
import kotlin.math.abs

class RecordingGestureDetector(
    private val onDoubleTap: () -> Unit,
    private val onSwipeUp: () -> Unit,
    private val onSwipeDown: () -> Unit
) : GestureDetector.SimpleOnGestureListener() {

    private var lastTapTime = 0L
    private val doubleTapTimeout = 300L
    private val swipeThreshold = 100f
    private val swipeVelocityThreshold = 50f

    override fun onDoubleTap(e: MotionEvent): Boolean {
        onDoubleTap()
        return true
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTapTime < doubleTapTimeout) {
            onDoubleTap()
            return true
        }
        lastTapTime = currentTime
        return false
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        if (e1 == null) return false

        val deltaX = e2.x - e1.x
        val deltaY = e2.y - e1.y

        // Check if it's a vertical swipe
        if (abs(deltaY) > abs(deltaX) && abs(deltaY) > swipeThreshold) {
            if (abs(velocityY) > swipeVelocityThreshold) {
                if (deltaY < 0) {
                    // Swipe up
                    onSwipeUp()
                    return true
                } else {
                    // Swipe down
                    onSwipeDown()
                    return true
                }
            }
        }

        return false
    }
} 