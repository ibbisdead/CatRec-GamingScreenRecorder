package com.ibbie.catrec_gamingscreenrecorder

import com.ibbie.catrec_gamingscreenrecorder.audio.MicRecorder
import android.media.AudioRecord
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.io.File
import java.io.IOException

class MicRecorderTest {

    @Mock
    private lateinit var mockContext: android.content.Context
    
    private lateinit var tempFile: File
    private lateinit var micRecorder: MicRecorder

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        tempFile = File.createTempFile("test_mic", ".pcm")
        micRecorder = MicRecorder(
            context = mockContext,
            outputFile = tempFile,
            sampleRate = 44100,
            enableNoiseSuppressor = false,
            micVolume = 1.0f,
            micTestMode = false
        )
    }

    @After
    fun tearDown() {
        micRecorder.cleanup()
        if (tempFile.exists()) {
            tempFile.delete()
        }
    }

    @Test
    fun testAudioRecordInitializationSafety() {
        val recorder = MicRecorder(
            context = mockContext,
            outputFile = tempFile
        )

        try {
            // Try invoking a method that uses audioRecord
            recorder.startRecording()
            // If it doesn't crash, the test passes
            assertTrue(true)
        } catch (e: Exception) {
            fail("MicRecorder crashed with uninitialized AudioRecord: ${e.message}")
        } finally {
            recorder.cleanup()
        }
    }

    @Test
    fun testIsRecordingInitialState() {
        assertFalse("MicRecorder should not be recording initially", micRecorder.isRecording())
    }

    @Test
    fun testGetOutputFile() {
        assertEquals("Output file should match the provided file", tempFile, micRecorder.getOutputFile())
    }

    @Test
    fun testVolumeSetting() {
        try {
            micRecorder.setVolume(50)
            // The volume setting should not throw an exception
            assertTrue(true)
        } catch (e: Exception) {
            fail("Volume setting should not throw exception: ${e.message}")
        }
    }

    @Test
    fun testMuteSetting() {
        try {
            micRecorder.setMuted(true)
            // The mute setting should not throw an exception
            assertTrue(true)
        } catch (e: Exception) {
            fail("Mute setting should not throw exception: ${e.message}")
        }
    }

    @Test
    fun testPauseAndResumeWithoutRecording() {
        try {
            // These methods should not crash even when not recording
            micRecorder.pauseRecording()
            micRecorder.resumeRecording()
            assertTrue(true)
        } catch (e: Exception) {
            fail("Pause/resume should not throw exception when not recording: ${e.message}")
        }
    }

    @Test
    fun testStopRecordingWithoutStarting() {
        try {
            // Stopping without starting should not crash
            micRecorder.stopRecording()
            assertFalse("Should not be recording after stop", micRecorder.isRecording())
        } catch (e: Exception) {
            fail("Stop recording should not throw exception when not recording: ${e.message}")
        }
    }

    @Test
    fun testCleanup() {
        try {
            // Cleanup should not throw exceptions
            micRecorder.cleanup()
            assertTrue(true)
        } catch (e: Exception) {
            fail("Cleanup should not throw exception: ${e.message}")
        }
    }

    @Test
    fun testStartDoesNotCrashWhenNotPrepared() {
        val recorder = MicRecorder(
            context = mockContext,
            outputFile = tempFile
        )
        
        try {
            recorder.startRecording() // call without preparing/initializing
            assertTrue(true)
        } catch (e: Exception) {
            fail("MicRecorder.startRecording() crashed when AudioRecord was uninitialized: ${e.message}")
        } finally {
            recorder.cleanup()
        }
    }
} 