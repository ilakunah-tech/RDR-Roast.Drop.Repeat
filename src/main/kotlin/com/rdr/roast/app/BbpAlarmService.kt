package com.rdr.roast.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

enum class BbpAlarmType {
    DURATION_EXCEEDED,
    MIN_TEMP_NOT_REACHED,
    RECORDING_STOPPED
}

data class BbpAlarm(
    val type: BbpAlarmType,
    val title: String,
    val message: String,
    val actionLabel: String? = null
)

class BbpAlarmService(
    private val bbpService: BbpService,
    private val scope: CoroutineScope
) {
    private val log = LoggerFactory.getLogger(BbpAlarmService::class.java)
    private val _alarms = MutableSharedFlow<BbpAlarm>(extraBufferCapacity = 8)
    val alarms: SharedFlow<BbpAlarm> = _alarms.asSharedFlow()

    private var monitorJob: Job? = null
    private var durationAlarmFired = false

    var maxDurationSec: Double = BbpService.MAX_DURATION_SEC
    var alarmsEnabled: Boolean = true

    fun startMonitoring() {
        monitorJob?.cancel()
        durationAlarmFired = false
        monitorJob = scope.launch {
            bbpService.stateFlow.collect { state ->
                when (state) {
                    BbpState.IDLE -> {
                        durationAlarmFired = false
                    }
                    BbpState.STOPPED -> {
                        if (!durationAlarmFired && alarmsEnabled) {
                            val elapsed = bbpService.bbpElapsedSec.value
                            if (elapsed >= maxDurationSec) {
                                durationAlarmFired = true
                                _alarms.emit(BbpAlarm(
                                    type = BbpAlarmType.DURATION_EXCEEDED,
                                    title = "Between batch time exceeded",
                                    message = "The recording limit of %.0f minutes was exceeded. Recording has stopped automatically.".format(maxDurationSec / 60.0),
                                    actionLabel = "Restart between batch protocol"
                                ))
                            }
                        }
                    }
                    BbpState.RECORDING -> { /* monitor continues */ }
                }
            }
        }
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }
}
