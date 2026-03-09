package com.rdr.roast.app

import com.rdr.roast.domain.ProtocolComment
import com.rdr.roast.domain.TemperatureSample
import com.rdr.roast.domain.TemperatureUnit
import com.rdr.roast.driver.ConnectionState
import com.rdr.roast.driver.RoastDataSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BbpServiceTest {

    @Test
    fun startBbp_transitions_to_recording() {
        val source = FakeSource()
        val engine = SamplingEngine(source)
        val service = BbpService(engine)

        assertEquals(BbpState.IDLE, service.stateFlow.value)
        service.startBbp()
        assertEquals(BbpState.RECORDING, service.stateFlow.value)
        assertNotNull(service.currentSession)
        engine.stop()
    }

    @Test
    fun startBbp_ignores_when_not_idle() {
        val source = FakeSource()
        val engine = SamplingEngine(source)
        val service = BbpService(engine)

        service.startBbp()
        service.startBbp()
        assertEquals(BbpState.RECORDING, service.stateFlow.value)
        engine.stop()
    }

    @Test
    fun stopBbp_transitions_to_stopped() {
        val source = FakeSource()
        val engine = SamplingEngine(source)
        val service = BbpService(engine)

        service.startBbp()
        service.stopBbp()
        assertEquals(BbpState.STOPPED, service.stateFlow.value)
        assertTrue(service.currentSession!!.isStopped())
    }

    @Test
    fun restartBbp_resets_session() {
        val source = FakeSource()
        val engine = SamplingEngine(source)
        val service = BbpService(engine)

        service.startBbp()
        runBlocking {
            source.emitSample(0.0, 200.0, 300.0)
            delay(50)
        }
        val originalStart = service.currentSession!!.startEpochMs

        service.restartBbp()
        assertEquals(BbpState.RECORDING, service.stateFlow.value)
        assertTrue(service.currentSession!!.timex.isEmpty())
        assertTrue(service.currentSession!!.startEpochMs >= originalStart)
        engine.stop()
    }

    @Test
    fun finalizeBbp_returns_log_and_goes_idle() {
        val source = FakeSource()
        val engine = SamplingEngine(source)
        val service = BbpService(engine)

        service.startBbp()
        runBlocking {
            source.emitSample(0.0, 200.0, 300.0)
            delay(50)
        }
        awaitUntil { service.currentSession?.timex?.isNotEmpty() == true }

        val log = service.finalizeBbp(TemperatureUnit.CELSIUS)
        assertNotNull(log)
        assertTrue(log!!.timex.isNotEmpty())
        assertEquals(BbpState.IDLE, service.stateFlow.value)
        assertNull(service.currentSession)
        assertEquals(0.0, service.bbpElapsedSec.value)
    }

    @Test
    fun finalizeBbp_returns_null_when_idle() {
        val source = FakeSource()
        val engine = SamplingEngine(source)
        val service = BbpService(engine)

        val result = service.finalizeBbp()
        assertNull(result)
        assertEquals(BbpState.IDLE, service.stateFlow.value)
    }

    @Test
    fun addComment_works_when_recording() {
        val source = FakeSource()
        val engine = SamplingEngine(source)
        val service = BbpService(engine)

        service.startBbp()
        val comment = ProtocolComment(timeSec = 0.5, text = "test", gas = 5.0)
        service.addComment(comment)
        assertEquals(1, service.currentSession!!.comments.size)
        engine.stop()
    }

    @Test
    fun addComment_ignored_when_stopped() {
        val source = FakeSource()
        val engine = SamplingEngine(source)
        val service = BbpService(engine)

        service.startBbp()
        service.stopBbp()
        val comment = ProtocolComment(timeSec = 0.5, text = "test")
        service.addComment(comment)
        assertEquals(0, service.currentSession!!.comments.size)
    }

    @Test
    fun isActive_returns_true_when_recording_or_stopped() {
        val source = FakeSource()
        val engine = SamplingEngine(source)
        val service = BbpService(engine)

        assertFalse(service.isActive())
        service.startBbp()
        assertTrue(service.isActive())
        service.stopBbp()
        assertTrue(service.isActive())
        service.finalizeBbp()
        assertFalse(service.isActive())
    }

    private fun awaitUntil(timeoutMs: Long = 2000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(10)
        }
        assertTrue(condition(), "Condition not met within ${timeoutMs}ms")
    }

    private class FakeSource : RoastDataSource {
        private val connection = MutableStateFlow<ConnectionState>(ConnectionState.Connected("Fake"))
        private val samples = MutableSharedFlow<TemperatureSample>(extraBufferCapacity = 64)
        override fun connect() {}
        override fun disconnect() {}
        override fun sampleFlow(): Flow<TemperatureSample> = samples
        override fun connectionState(): StateFlow<ConnectionState> = connection
        suspend fun emitSample(timeSec: Double, bt: Double, et: Double) {
            samples.emit(TemperatureSample(timeSec, bt, et))
        }
    }
}
