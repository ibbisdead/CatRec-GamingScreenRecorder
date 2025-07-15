package com.ibbie.catrec_gamingscreenrecorder.audio

import android.content.Context
import android.media.projection.MediaProjection
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.io.File

class CombinedAudioRecorderTest {

    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockMediaProjection: MediaProjection
    
    private lateinit var tempFile: File
    private lateinit var combinedAudioRecorder: CombinedAudioRecorder

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        tempFile = File.createTempFile("test_combined_audio", ".wav")
        combinedAudioRecorder = CombinedAudioRecorder(
            context = mockContext,
            mediaProjection = mockMediaProjection,
            outputFile = tempFile,
            sampleRate = 44100,
            enableMic = true,
            enableInternal = true,
            micVolume = 1.0f,
            enableNoiseSuppression = false
        )
    }

    @After
    fun tearDown() {
        combinedAudioRecorder.cleanup()
        if (tempFile.exists()) {
            tempFile.delete()
        }
    }

    @Test
    fun testInitialState() {
        assertFalse("CombinedAudioRecorder should not be recording initially", combinedAudioRecorder.isRecording())
    }

    @Test
    fun testGetOutputFile() {
        assertEquals("Output file should match the provided file", tempFile, combinedAudioRecorder.getOutputFile())
    }

    @Test
    fun testStartRecordingDoesNotCrash() {
        try {
            combinedAudioRecorder.startRecording()
            assertTrue("Start recording should not crash", true)
        } catch (e: Exception) {
            fail("Start recording should not throw exception: ${e.message}")
        }
    }

    @Test
    fun testStopRecordingWithoutStarting() {
        try {
            combinedAudioRecorder.stopRecording()
            assertFalse("Should not be recording after stop", combinedAudioRecorder.isRecording())
        } catch (e: Exception) {
            fail("Stop recording should not throw exception when not recording: ${e.message}")
        }
    }

    @Test
    fun testPauseAndResumeWithoutRecording() {
        try {
            combinedAudioRecorder.pauseRecording()
            combinedAudioRecorder.resumeRecording()
            assertTrue("Pause/resume should not crash when not recording", true)
        } catch (e: Exception) {
            fail("Pause/resume should not throw exception when not recording: ${e.message}")
        }
    }

    @Test
    fun testSetMuted() {
        try {
            combinedAudioRecorder.setMuted(true)
            combinedAudioRecorder.setMuted(false)
            assertTrue("Set muted should not crash", true)
        } catch (e: Exception) {
            fail("Set muted should not throw exception: ${e.message}")
        }
    }

    @Test
    fun testMicOnlyConfiguration() {
        val micOnlyRecorder = CombinedAudioRecorder(
            context = mockContext,
            mediaProjection = null,
            outputFile = tempFile,
            enableMic = true,
            enableInternal = false
        )

        try {
            micOnlyRecorder.startRecording()
            assertTrue("Mic-only configuration should work", true)
        } catch (e: Exception) {
            fail("Mic-only configuration should not throw exception: ${e.message}")
        } finally {
            micOnlyRecorder.cleanup()
        }
    }

    @Test
    fun testInternalOnlyConfiguration() {
        val internalOnlyRecorder = CombinedAudioRecorder(
            context = mockContext,
            mediaProjection = mockMediaProjection,
            outputFile = tempFile,
            enableMic = false,
            enableInternal = true
        )

        try {
            internalOnlyRecorder.startRecording()
            assertTrue("Internal-only configuration should work", true)
        } catch (e: Exception) {
            fail("Internal-only configuration should not throw exception: ${e.message}")
        } finally {
            internalOnlyRecorder.cleanup()
        }
    }

    @Test
    fun testCleanup() {
        try {
            combinedAudioRecorder.cleanup()
            assertTrue("Cleanup should not crash", true)
        } catch (e: Exception) {
            fail("Cleanup should not throw exception: ${e.message}")
        }
    }
}