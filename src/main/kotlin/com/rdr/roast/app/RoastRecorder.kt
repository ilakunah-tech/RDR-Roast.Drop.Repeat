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
            bbpService = BbpService(samplingEngine)
        }

    private var samplingEngine: SamplingEngine = SamplingEngine(dataSource)

    /** BBP lifecycle is delegated to BbpService. */
    var bbpService: BbpService = BbpService(samplingEngine)
        private set

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

    /** Backward-compatible accessor: delegates to BbpService. */
    val currentBbpSession: BetweenBatchSession? get() = bbpService.currentSession

    /** Backward-compatible accessor: delegates to BbpService. */
    val bbpElapsedSec: StateFlow<Double> get() = bbpService.bbpElapsedSec

    private var connectionJob: kotlinx.coroutines.Job? = null

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
            bbpService.finalizeBbp(_currentProfile.value.mode)
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

    fun markEventAt(timeSec: Double, type: EventType) {
        val state = _stateFlow.value
        if (state != RecorderState.RECORDING && state != RecorderState.STOPPED) return
        val profile = _currentProfile.value
        val event = RoastEvent(timeSec = timeSec, type = type, tempBT = null, tempET = null)
        profile.addEvent(event)
    }

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
            RecorderState.BBP -> bbpService.addComment(comment)
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
                betweenBatchLog = live.betweenBatchLog,
                extraTemp = live.extraTemp.mapValues { ArrayList(it.value) }
            )
        }
        _currentProfile.value = snapshot

        if (betweenBatchProtocolEnabled) {
            _stateFlow.value = RecorderState.BBP
            bbpService.startBbp()
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
        profile.extraTemp.clear()
        profile.events.clear()
        profile.comments.clear()
        profile.controlEvents.clear()
        _currentSample.value = null
        _elapsedSec.value = 0.0
        _stateFlow.value = RecorderState.MONITORING
    }

    fun reset() {
        when (_stateFlow.value) {
            RecorderState.STOPPED -> _stateFlow.value = RecorderState.MONITORING
            RecorderState.BBP -> {
                bbpService.finalizeBbp()
                _stateFlow.value = RecorderState.MONITORING
            }
            else -> log.warn("reset ignored: state is {}", _stateFlow.value)
        }
    }

    fun restartBbp() {
        if (_stateFlow.value != RecorderState.BBP) return
        bbpService.restartBbp()
    }

    fun stopBbpRecording() {
        if (_stateFlow.value != RecorderState.BBP) return
        bbpService.stopBbp()
    }

    fun shutdown() {
        scope.cancel()
    }
}
