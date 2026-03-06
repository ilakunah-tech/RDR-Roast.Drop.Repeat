package com.rdr.roast.app

import com.rdr.roast.domain.BetweenBatchSession
import com.rdr.roast.domain.ControlEventType
import com.rdr.roast.domain.EventType
import com.rdr.roast.domain.ProtocolComment
import com.rdr.roast.domain.RoastEvent
import com.rdr.roast.domain.RoastProfile
import com.rdr.roast.domain.TemperatureSample
import com.rdr.roast.domain.TemperatureUnit
import com.rdr.roast.driver.ConnectionState
import com.rdr.roast.driver.RoastDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

enum class RecorderState { DISCONNECTED, MONITORING, RECORDING, STOPPED, BBP }

class RoastRecorder(
    dataSource: RoastDataSource
) {
    var dataSource: RoastDataSource = dataSource
        set(value) {
            field = value
            samplingEngine = SamplingEngine(value)
        }

    private var samplingEngine: SamplingEngine = SamplingEngine(dataSource)

    private val log = LoggerFactory.getLogger(RoastRecorder::class.java)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** When true, after Stop transition to BBP and record BT/ET until Start or Stop BBP. */
    var betweenBatchProtocolEnabled: Boolean = true

    private val _stateFlow = MutableStateFlow(RecorderState.DISCONNECTED)
    val stateFlow: StateFlow<RecorderState> = _stateFlow.asStateFlow()

    private val _currentProfile = MutableStateFlow(RoastProfile())
    val currentProfile: StateFlow<RoastProfile> = _currentProfile.asStateFlow()

    private val _currentSample = MutableStateFlow<TemperatureSample?>(null)
    val currentSample: StateFlow<TemperatureSample?> = _currentSample.asStateFlow()

    private val _elapsedSec = MutableStateFlow(0.0)
    val elapsedSec: StateFlow<Double> = _elapsedSec.asStateFlow()

    /** BBP session when state is BBP; receives samples after Stop until Start/Restart/Stop BBP. */
    var currentBbpSession: BetweenBatchSession? = null
        private set

    private val _bbpElapsedSec = MutableStateFlow(0.0)
    val bbpElapsedSec: StateFlow<Double> = _bbpElapsedSec.asStateFlow()

    private var connectionJob: kotlinx.coroutines.Job? = null

    private fun startBbpCollection(session: BetweenBatchSession) {
        samplingEngine.stop()
        samplingEngine.start { sample ->
            val bbpTimeSec = (System.currentTimeMillis() - session.startEpochMs) / 1000.0
            if (bbpTimeSec >= session.maxDurationSec) {
                session.setStopped(true)
                _bbpElapsedSec.value = session.maxDurationSec
                samplingEngine.stop()
                return@start
            }
            session.addSample(bbpTimeSec, sample.bt, sample.et)
            _bbpElapsedSec.value = bbpTimeSec
        }
    }

    fun connect() {
        dataSource.connect()
        connectionJob?.cancel()
        connectionJob = scope.launch {
            dataSource.connectionState().collect { state ->
                when (state) {
                    is ConnectionState.Connected -> {
                        log.info("Connected to {}", state.deviceName)
                        _stateFlow.value = RecorderState.MONITORING
                    }
                    is ConnectionState.Disconnected -> {
                        if (_stateFlow.value == RecorderState.MONITORING || _stateFlow.value == RecorderState.DISCONNECTED) {
                            _stateFlow.value = RecorderState.DISCONNECTED
                        }
                    }
                    is ConnectionState.Error -> {
                        log.warn("Connection error: {}", state.message)
                        _stateFlow.value = RecorderState.DISCONNECTED
                    }
                    is ConnectionState.Connecting -> { /* wait */ }
                }
            }
        }
    }

    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        dataSource.disconnect()
        _stateFlow.value = RecorderState.DISCONNECTED
    }

    fun startRecording() {
        val state = _stateFlow.value
        if (state != RecorderState.MONITORING && state != RecorderState.BBP) {
            log.warn("startRecording ignored: state is {}", state)
            return
        }

        val pendingBbpLog = if (state == RecorderState.BBP) {
            samplingEngine.stop()
            val session = currentBbpSession
            currentBbpSession = null
            _bbpElapsedSec.value = 0.0
            session?.toLog(_currentProfile.value.mode)
        } else null

        val profile = RoastProfile(
            mode = TemperatureUnit.CELSIUS,
            betweenBatchLog = pendingBbpLog
        )
        _currentProfile.value = profile
        _currentSample.value = null
        _elapsedSec.value = 0.0
        _stateFlow.value = RecorderState.RECORDING

        samplingEngine.start { sample ->
            profile.addSample(sample)
            _currentSample.value = sample
            _elapsedSec.value = sample.timeSec
        }
    }

    fun markEvent(type: EventType) {
        if (_stateFlow.value != RecorderState.RECORDING) {
            log.warn("markEvent ignored: state is {}", _stateFlow.value)
            return
        }
        val sample = _currentSample.value ?: return
        val event = RoastEvent(
            timeSec = sample.timeSec,
            type = type,
            tempBT = sample.bt,
            tempET = sample.et
        )
        _currentProfile.value.addEvent(event)
    }

    /** Add an event at a specific time (e.g. DE/FC from chart popup). Allowed when RECORDING or STOPPED. */
    fun markEventAt(timeSec: Double, type: EventType) {
        val state = _stateFlow.value
        if (state != RecorderState.RECORDING && state != RecorderState.STOPPED) return
        val profile = _currentProfile.value
        val event = RoastEvent(timeSec = timeSec, type = type, tempBT = null, tempET = null)
        profile.addEvent(event)
    }

    /** Add a control event (gas/air/drum) at the given time. When RECORDING uses roast elapsed time; when BBP uses BBP elapsed time. */
    fun addControlEvent(timeSec: Double, type: ControlEventType, value: Double, displayString: String? = null) {
        if (_stateFlow.value != RecorderState.RECORDING && _stateFlow.value != RecorderState.BBP) return
        _currentProfile.value.addControlEvent(timeSec, type, value, displayString)
    }

    fun addCommentAt(
        timeSec: Double,
        text: String,
        tempBT: Double? = null,
        gas: Double? = null,
        airflow: Double? = null
    ) {
        if (text.isBlank() && gas == null && airflow == null) return
        val comment = ProtocolComment(
            timeSec = timeSec,
            text = text.trim(),
            tempBT = tempBT,
            gas = gas,
            airflow = airflow
        )
        when (_stateFlow.value) {
            RecorderState.RECORDING -> _currentProfile.value.addComment(comment)
            RecorderState.BBP -> currentBbpSession?.addComment(comment)
            else -> log.warn("addCommentAt ignored: state is {}", _stateFlow.value)
        }
    }

    fun stop(): RoastProfile {
        if (_stateFlow.value != RecorderState.RECORDING) {
            log.warn("stop ignored: state is {}", _stateFlow.value)
            return _currentProfile.value
        }
        samplingEngine.stop()
        val live = _currentProfile.value
        val snapshot = synchronized(live) {
            RoastProfile(
                timex = ArrayList(live.timex),
                temp1 = ArrayList(live.temp1),
                temp2 = ArrayList(live.temp2),
                events = ArrayList(live.events),
                comments = ArrayList(live.comments),
                controlEvents = ArrayList(live.controlEvents),
                mode = live.mode,
                betweenBatchLog = live.betweenBatchLog
            )
        }
        _currentProfile.value = snapshot

        if (betweenBatchProtocolEnabled) {
            _stateFlow.value = RecorderState.BBP
            val session = BetweenBatchSession(startEpochMs = System.currentTimeMillis())
            currentBbpSession = session
            _bbpElapsedSec.value = 0.0
            startBbpCollection(session)
        } else {
            _stateFlow.value = RecorderState.STOPPED
        }
        return snapshot
    }

    fun abort() {
        if (_stateFlow.value != RecorderState.RECORDING) {
            log.warn("abort ignored: state is {}", _stateFlow.value)
            return
        }
        samplingEngine.stop()
        val profile = _currentProfile.value
        profile.timex.clear()
        profile.temp1.clear()
        profile.temp2.clear()
        profile.events.clear()
        profile.comments.clear()
        profile.controlEvents.clear()
        _currentSample.value = null
        _elapsedSec.value = 0.0
        _stateFlow.value = RecorderState.MONITORING
    }

    /** Transition to MONITORING (from STOPPED or BBP). From BBP cancels BBP job and clears session. */
    fun reset() {
        when (_stateFlow.value) {
            RecorderState.STOPPED -> _stateFlow.value = RecorderState.MONITORING
            RecorderState.BBP -> {
                samplingEngine.stop()
                currentBbpSession = null
                _bbpElapsedSec.value = 0.0
                _stateFlow.value = RecorderState.MONITORING
            }
            else -> log.warn("reset ignored: state is {}", _stateFlow.value)
        }
    }

    /** Restart BBP recording (new session, clear data). Only in BBP state. */
    fun restartBbp() {
        if (_stateFlow.value != RecorderState.BBP) return
        samplingEngine.stop()
        val session = currentBbpSession ?: BetweenBatchSession()
        session.reset()
        currentBbpSession = session
        _bbpElapsedSec.value = 0.0
        startBbpCollection(session)
    }

    /** Stop BBP recording (keep data until Start). Only in BBP state. */
    fun stopBbpRecording() {
        if (_stateFlow.value != RecorderState.BBP) return
        currentBbpSession?.setStopped(true)
        samplingEngine.stop()
    }

    fun shutdown() {
        scope.cancel()
    }
}
