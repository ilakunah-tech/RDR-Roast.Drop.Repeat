package com.rdr.roast.app

import com.rdr.roast.domain.BetweenBatchLog
import com.rdr.roast.domain.BetweenBatchSession
import com.rdr.roast.domain.ProtocolComment
import com.rdr.roast.domain.TemperatureUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.slf4j.LoggerFactory

enum class BbpState { IDLE, RECORDING, STOPPED }

class BbpService(
    private val samplingEngine: SamplingEngine
) {
    companion object {
        const val MAX_DURATION_SEC = 15.0 * 60.0
    }

    private val log = LoggerFactory.getLogger(BbpService::class.java)

    private val _stateFlow = MutableStateFlow(BbpState.IDLE)
    val stateFlow: StateFlow<BbpState> = _stateFlow.asStateFlow()

    private val _bbpElapsedSec = MutableStateFlow(0.0)
    val bbpElapsedSec: StateFlow<Double> = _bbpElapsedSec.asStateFlow()

    var currentSession: BetweenBatchSession? = null
        private set

    fun isActive(): Boolean = _stateFlow.value == BbpState.RECORDING || _stateFlow.value == BbpState.STOPPED

    fun startBbp() {
        if (_stateFlow.value != BbpState.IDLE) {
            log.warn("startBbp ignored: state is {}", _stateFlow.value)
            return
        }
        val session = BetweenBatchSession(
            startEpochMs = System.currentTimeMillis(),
            maxDurationSec = MAX_DURATION_SEC
        )
        currentSession = session
        _stateFlow.value = BbpState.RECORDING
        _bbpElapsedSec.value = 0.0

        samplingEngine.start { sample ->
            val bbpTimeSec = (System.currentTimeMillis() - session.startEpochMs) / 1000.0
            if (bbpTimeSec >= MAX_DURATION_SEC) {
                session.setStopped(true)
                _bbpElapsedSec.value = MAX_DURATION_SEC
                samplingEngine.stop()
                _stateFlow.value = BbpState.STOPPED
                return@start
            }
            session.addSample(bbpTimeSec, sample.bt, sample.et)
            _bbpElapsedSec.value = bbpTimeSec
        }
    }

    fun stopBbp() {
        if (_stateFlow.value != BbpState.RECORDING) {
            log.warn("stopBbp ignored: state is {}", _stateFlow.value)
            return
        }
        currentSession?.setStopped(true)
        samplingEngine.stop()
        _stateFlow.value = BbpState.STOPPED
    }

    fun restartBbp() {
        val state = _stateFlow.value
        if (state != BbpState.RECORDING && state != BbpState.STOPPED) {
            log.warn("restartBbp ignored: state is {}", state)
            return
        }
        val session = currentSession ?: BetweenBatchSession(
            startEpochMs = System.currentTimeMillis(),
            maxDurationSec = MAX_DURATION_SEC
        )
        samplingEngine.stop()
        session.reset()
        currentSession = session
        _stateFlow.value = BbpState.RECORDING
        _bbpElapsedSec.value = 0.0

        samplingEngine.start { sample ->
            val bbpTimeSec = (System.currentTimeMillis() - session.startEpochMs) / 1000.0
            if (bbpTimeSec >= MAX_DURATION_SEC) {
                session.setStopped(true)
                _bbpElapsedSec.value = MAX_DURATION_SEC
                samplingEngine.stop()
                _stateFlow.value = BbpState.STOPPED
                return@start
            }
            session.addSample(bbpTimeSec, sample.bt, sample.et)
            _bbpElapsedSec.value = bbpTimeSec
        }
    }

    fun finalizeBbp(mode: TemperatureUnit = TemperatureUnit.CELSIUS): BetweenBatchLog? {
        val state = _stateFlow.value
        if (state != BbpState.RECORDING && state != BbpState.STOPPED) {
            log.warn("finalizeBbp ignored: state is {}", state)
            return null
        }
        samplingEngine.stop()
        val session = currentSession
        val result = session?.toLog(mode)
        currentSession = null
        _bbpElapsedSec.value = 0.0
        _stateFlow.value = BbpState.IDLE
        return result
    }

    fun addComment(comment: ProtocolComment) {
        if (_stateFlow.value != BbpState.RECORDING) {
            log.warn("addComment ignored: state is {} (only works when RECORDING)", _stateFlow.value)
            return
        }
        currentSession?.addComment(comment)
    }
}
