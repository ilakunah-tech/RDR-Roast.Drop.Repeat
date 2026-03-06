package com.rdr.roast.app

import com.rdr.roast.driver.ConnectionState
import com.rdr.roast.driver.RoastDataSource
import com.rdr.roast.domain.TemperatureSample
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class RoastRecorderBbpLifecycleTest {

    @Test
    fun stop_with_bbp_enabled_enters_bbp_and_keeps_finished_profile_separate() {
        val source = FakeRoastDataSource()
        val recorder = RoastRecorder(source)
        try {
            recorder.connect()
            awaitUntil { recorder.stateFlow.value == RecorderState.MONITORING }

            recorder.startRecording()
            runBlocking {
                source.emitSample(0.0, 200.0, 300.0)
                source.emitSample(1.0, 198.0, 299.0)
            }
            awaitUntil { recorder.currentProfile.value.timex.size == 2 }

            val stoppedProfile = recorder.stop()
            awaitUntil { recorder.stateFlow.value == RecorderState.BBP && recorder.currentBbpSession != null }

            runBlocking {
                delay(60)
                source.emitSample(2.0, 197.0, 298.0)
            }
            awaitUntil { recorder.currentBbpSession?.timex?.isNotEmpty() == true }

            assertEquals(2, stoppedProfile.timex.size)
            assertNull(stoppedProfile.betweenBatchLog)
            assertEquals(2, recorder.currentProfile.value.timex.size)
            assertTrue(recorder.currentBbpSession!!.timex.isNotEmpty())
        } finally {
            recorder.shutdown()
        }
    }

    @Test
    fun start_recording_from_bbp_attaches_pending_log_to_next_roast() {
        val source = FakeRoastDataSource()
        val recorder = RoastRecorder(source)
        try {
            recorder.connect()
            awaitUntil { recorder.stateFlow.value == RecorderState.MONITORING }

            recorder.startRecording()
            runBlocking { source.emitSample(0.0, 200.0, 300.0) }
            awaitUntil { recorder.currentProfile.value.timex.size == 1 }

            recorder.stop()
            awaitUntil { recorder.stateFlow.value == RecorderState.BBP && recorder.currentBbpSession != null }

            runBlocking {
                delay(60)
                source.emitSample(1.0, 195.0, 295.0)
            }
            awaitUntil { recorder.currentBbpSession?.timex?.isNotEmpty() == true }
            recorder.addCommentAt(timeSec = 0.1, text = "BBP note", tempBT = 195.0, gas = 5.0, airflow = 3.0)

            recorder.startRecording()
            awaitUntil { recorder.stateFlow.value == RecorderState.RECORDING }

            val nextRoast = recorder.currentProfile.value
            assertNull(recorder.currentBbpSession)
            assertTrue(nextRoast.timex.isEmpty())
            assertNotNull(nextRoast.betweenBatchLog)
            assertTrue(nextRoast.betweenBatchLog!!.timex.isNotEmpty())
            assertEquals(1, nextRoast.betweenBatchLog!!.comments.size)
            assertEquals("BBP note", nextRoast.betweenBatchLog!!.comments.first().text)
        } finally {
            recorder.shutdown()
        }
    }

    @Test
    fun stop_bbp_recording_freezes_session_for_next_roast() {
        val source = FakeRoastDataSource()
        val recorder = RoastRecorder(source)
        try {
            recorder.connect()
            awaitUntil { recorder.stateFlow.value == RecorderState.MONITORING }

            recorder.startRecording()
            runBlocking { source.emitSample(0.0, 200.0, 300.0) }
            awaitUntil { recorder.currentProfile.value.timex.size == 1 }

            recorder.stop()
            awaitUntil { recorder.stateFlow.value == RecorderState.BBP && recorder.currentBbpSession != null }

            runBlocking {
                delay(60)
                source.emitSample(1.0, 190.0, 290.0)
            }
            awaitUntil { recorder.currentBbpSession?.timex?.size == 1 }
            recorder.addCommentAt(timeSec = 0.1, text = "First", gas = 5.0)
            recorder.stopBbpRecording()

            runBlocking {
                delay(60)
                source.emitSample(2.0, 180.0, 280.0)
            }
            recorder.addCommentAt(timeSec = 0.2, text = "Ignored", gas = 7.0)

            recorder.startRecording()
            val bbp = recorder.currentProfile.value.betweenBatchLog
            assertNotNull(bbp)
            assertEquals(1, bbp!!.timex.size)
            assertEquals(1, bbp.comments.size)
            assertEquals("First", bbp.comments.first().text)
        } finally {
            recorder.shutdown()
        }
    }

    @Test
    fun restart_bbp_discards_previous_session_before_next_roast() {
        val source = FakeRoastDataSource()
        val recorder = RoastRecorder(source)
        try {
            recorder.connect()
            awaitUntil { recorder.stateFlow.value == RecorderState.MONITORING }

            recorder.startRecording()
            runBlocking { source.emitSample(0.0, 200.0, 300.0) }
            awaitUntil { recorder.currentProfile.value.timex.size == 1 }

            recorder.stop()
            awaitUntil { recorder.stateFlow.value == RecorderState.BBP && recorder.currentBbpSession != null }

            runBlocking {
                delay(60)
                source.emitSample(1.0, 195.0, 295.0)
            }
            awaitUntil { recorder.currentBbpSession?.timex?.isNotEmpty() == true }
            recorder.addCommentAt(timeSec = 0.1, text = "Old note", gas = 4.0)

            recorder.restartBbp()
            awaitUntil {
                val session = recorder.currentBbpSession
                session != null && session.timex.isEmpty() && session.comments.isEmpty()
            }

            runBlocking {
                delay(60)
                source.emitSample(2.0, 188.0, 288.0)
            }
            awaitUntil { recorder.currentBbpSession?.timex?.size == 1 }
            recorder.addCommentAt(timeSec = 0.1, text = "New note", airflow = 3.0)

            recorder.startRecording()
            val bbp = recorder.currentProfile.value.betweenBatchLog
            assertNotNull(bbp)
            assertEquals(1, bbp!!.timex.size)
            assertEquals(1, bbp.comments.size)
            assertEquals("New note", bbp.comments.first().text)
        } finally {
            recorder.shutdown()
        }
    }

    @Test
    fun save_round_trip_persists_bbp_only_on_next_roast() {
        val source = FakeRoastDataSource()
        val recorder = RoastRecorder(source)
        try {
            recorder.connect()
            awaitUntil { recorder.stateFlow.value == RecorderState.MONITORING }

            recorder.startRecording()
            runBlocking { source.emitSample(0.0, 200.0, 300.0) }
            awaitUntil { recorder.currentProfile.value.timex.size == 1 }

            val firstRoast = recorder.stop()
            awaitUntil { recorder.stateFlow.value == RecorderState.BBP && recorder.currentBbpSession != null }
            runBlocking {
                delay(60)
                source.emitSample(1.0, 190.0, 290.0)
            }
            awaitUntil { recorder.currentBbpSession?.timex?.isNotEmpty() == true }
            recorder.addCommentAt(timeSec = 0.1, text = "Saved for next roast", gas = 5.0, airflow = 3.0)

            val firstFile = Files.createTempFile("rdr_first_roast_", ".alog")
            val secondFile = Files.createTempFile("rdr_second_roast_", ".alog")
            try {
                ProfileStorage.saveProfile(firstRoast, firstFile)
                val firstReloaded = ProfileStorage.parseAlogContent(Files.readString(firstFile))
                assertNull(firstReloaded.betweenBatchLog)

                recorder.startRecording()
                runBlocking { source.emitSample(0.0, 210.0, 305.0) }
                awaitUntil { recorder.currentProfile.value.timex.size == 1 }
                val secondRoast = recorder.currentProfile.value.deepCopy()
                ProfileStorage.saveProfile(secondRoast, secondFile)
                val secondReloaded = ProfileStorage.parseAlogContent(Files.readString(secondFile))
                assertNotNull(secondReloaded.betweenBatchLog)
                assertEquals(1, secondReloaded.betweenBatchLog!!.comments.size)
                assertEquals("Saved for next roast", secondReloaded.betweenBatchLog!!.comments.first().text)
            } finally {
                Files.deleteIfExists(firstFile)
                Files.deleteIfExists(secondFile)
            }
        } finally {
            recorder.shutdown()
        }
    }

    private fun awaitUntil(timeoutMs: Long = 2000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(10)
        }
        assertTrue(condition(), "Condition not met within ${timeoutMs}ms")
    }

    private class FakeRoastDataSource : RoastDataSource {
        private val connection = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        private val samples = MutableSharedFlow<TemperatureSample>(extraBufferCapacity = 64)

        override fun connect() {
            connection.value = ConnectionState.Connected("Fake roaster")
        }

        override fun disconnect() {
            connection.value = ConnectionState.Disconnected
        }

        override fun sampleFlow(): Flow<TemperatureSample> = samples

        override fun connectionState(): StateFlow<ConnectionState> = connection

        suspend fun emitSample(timeSec: Double, bt: Double, et: Double) {
            samples.emit(TemperatureSample(timeSec, bt, et))
        }
    }
}
