package com.rdr.roast.app

import com.rdr.roast.domain.EventType
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

enum class RecorderState { DISCONNECTED, MONITORING, RECORDING, STOPPED }

class RoastRecorder(
    var dataSource: RoastDataSource
) {
    private val log = LoggerFactory.getLogger(RoastRecorder::class.java)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _stateFlow = MutableStateFlow(RecorderState.DISCONNECTED)
    val stateFlow: StateFlow<RecorderState> = _stateFlow.asStateFlow()

    private val _currentProfile = MutableStateFlow(RoastProfile())
    val currentProfile: StateFlow<RoastProfile> = _currentProfile.asStateFlow()

    private val _currentSample = MutableStateFlow<TemperatureSample?>(null)
    val currentSample: StateFlow<TemperatureSample?> = _currentSample.asStateFlow()

    private val _elapsedSec = MutableStateFlow(0.0)
    val elapsedSec: StateFlow<Double> = _elapsedSec.asStateFlow()

    private var connectionJob: kotlinx.coroutines.Job? = null
    private var sampleCollectionJob: kotlinx.coroutines.Job? = null

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
        if (_stateFlow.value != RecorderState.MONITORING) {
            log.warn("startRecording ignored: state is {}", _stateFlow.value)
            return
        }
        val profile = RoastProfile(mode = TemperatureUnit.CELSIUS)
        // #region agent log
        val oldRef = System.identityHashCode(_currentProfile.value)
        // #endregion
        _currentProfile.value = profile
        // #region agent log
        val newRef = System.identityHashCode(_currentProfile.value)
        val localRef = System.identityHashCode(profile)
        java.io.File("/opt/cursor/logs/debug.log").appendText("""{"id":"log_startRec_1","timestamp":${System.currentTimeMillis()},"location":"RoastRecorder.kt:startRecording","message":"profile created and assigned","data":{"oldRef":$oldRef,"newRef":$newRef,"localRef":$localRef,"refsMatch":${newRef == localRef},"stateFlowUpdated":${newRef != oldRef}},"hypothesisId":"A"}""" + "\n")
        // #endregion
        _currentSample.value = null
        _elapsedSec.value = 0.0
        _stateFlow.value = RecorderState.RECORDING

        sampleCollectionJob?.cancel()
        sampleCollectionJob = scope.launch {
            // #region agent log
            var sampleCount = 0
            // #endregion
            dataSource.sampleFlow().collect { sample ->
                if (!isActive) return@collect
                profile.addSample(sample)
                // #region agent log
                sampleCount++
                if (sampleCount <= 3 || sampleCount % 10 == 0) {
                    val flowProfileRef = System.identityHashCode(profile)
                    val stateFlowRef = System.identityHashCode(_currentProfile.value)
                    java.io.File("/opt/cursor/logs/debug.log").appendText("""{"id":"log_sample_$sampleCount","timestamp":${System.currentTimeMillis()},"location":"RoastRecorder.kt:sampleFlow","message":"after addSample","data":{"sampleCount":$sampleCount,"profileTimexSize":${profile.timex.size},"flowProfileRef":$flowProfileRef,"stateFlowRef":$stateFlowRef,"refsMismatch":${flowProfileRef != stateFlowRef}},"hypothesisId":"A,D"}""" + "\n")
                }
                // #endregion
                _currentSample.value = sample
                _elapsedSec.value = sample.timeSec
            }
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

    fun stop(): RoastProfile {
        // #region agent log
        java.io.File("/opt/cursor/logs/debug.log").appendText("""{"id":"log_stop_entry","timestamp":${System.currentTimeMillis()},"location":"RoastRecorder.kt:stop","message":"stop() called","data":{"currentState":"${_stateFlow.value}"},"hypothesisId":"B"}""" + "\n")
        // #endregion
        if (_stateFlow.value != RecorderState.RECORDING) {
            log.warn("stop ignored: state is {}", _stateFlow.value)
            // #region agent log
            java.io.File("/opt/cursor/logs/debug.log").appendText("""{"id":"log_stop_ignored","timestamp":${System.currentTimeMillis()},"location":"RoastRecorder.kt:stop","message":"stop IGNORED - not RECORDING","data":{"state":"${_stateFlow.value}","profileTimexSize":${_currentProfile.value.timex.size}},"hypothesisId":"B"}""" + "\n")
            // #endregion
            return _currentProfile.value
        }
        sampleCollectionJob?.cancel()
        sampleCollectionJob = null
        _stateFlow.value = RecorderState.STOPPED
        val live = _currentProfile.value
        // #region agent log
        val liveRef = System.identityHashCode(live)
        java.io.File("/opt/cursor/logs/debug.log").appendText("""{"id":"log_stop_before","timestamp":${System.currentTimeMillis()},"location":"RoastRecorder.kt:stop","message":"before snapshot","data":{"liveRef":$liveRef,"liveTimexSize":${live.timex.size},"liveTemp1Size":${live.temp1.size},"liveTemp2Size":${live.temp2.size},"liveEventsSize":${live.events.size}},"hypothesisId":"A,C"}""" + "\n")
        // #endregion
        val snapshot = synchronized(live) {
            RoastProfile(
                timex = ArrayList(live.timex),
                temp1 = ArrayList(live.temp1),
                temp2 = ArrayList(live.temp2),
                events = ArrayList(live.events),
                mode = live.mode
            )
        }
        // #region agent log
        val snapRef = System.identityHashCode(snapshot)
        java.io.File("/opt/cursor/logs/debug.log").appendText("""{"id":"log_stop_after","timestamp":${System.currentTimeMillis()},"location":"RoastRecorder.kt:stop","message":"after snapshot","data":{"snapRef":$snapRef,"snapTimexSize":${snapshot.timex.size},"snapTemp1Size":${snapshot.temp1.size},"snapTemp2Size":${snapshot.temp2.size}},"hypothesisId":"A,C"}""" + "\n")
        // #endregion
        _currentProfile.value = snapshot
        return snapshot
    }

    fun abort() {
        if (_stateFlow.value != RecorderState.RECORDING) {
            log.warn("abort ignored: state is {}", _stateFlow.value)
            return
        }
        sampleCollectionJob?.cancel()
        sampleCollectionJob = null
        val profile = _currentProfile.value
        profile.timex.clear()
        profile.temp1.clear()
        profile.temp2.clear()
        profile.events.clear()
        _currentSample.value = null
        _elapsedSec.value = 0.0
        _stateFlow.value = RecorderState.MONITORING
    }

    fun reset() {
        if (_stateFlow.value != RecorderState.STOPPED) {
            log.warn("reset ignored: state is {}", _stateFlow.value)
            return
        }
        _stateFlow.value = RecorderState.MONITORING
    }

    fun shutdown() {
        scope.cancel()
    }
}
