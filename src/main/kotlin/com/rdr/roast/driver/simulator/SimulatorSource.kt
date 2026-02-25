package com.rdr.roast.driver.simulator

import com.rdr.roast.driver.ConnectionState
import com.rdr.roast.driver.RoastDataSource
import com.rdr.roast.domain.TemperatureSample
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlin.math.min
import org.slf4j.LoggerFactory

class SimulatorSource : RoastDataSource {

    private val log = LoggerFactory.getLogger(SimulatorSource::class.java)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override fun connectionState(): StateFlow<ConnectionState> = _connectionState

    @Volatile
    private var running = false

    override fun connect() {
        log.info("Simulator connecting")
        _connectionState.value = ConnectionState.Connected("Simulator")
        running = true
    }

    override fun disconnect() {
        log.info("Simulator disconnecting")
        running = false
        _connectionState.value = ConnectionState.Disconnected
    }

    override fun sampleFlow(): Flow<TemperatureSample> = flow {
        var t = 0.0
        while (running && t <= 900.0) {
            val bt = when {
                t < 45.0 -> 200.0 - (45.0 / (t + 1.0)) * 2.0
                else -> min(155.0 + (t - 45.0) * 0.1, 215.0)
            }
            val et = (280.0 - t * 0.1).coerceIn(200.0, 280.0)
            emit(TemperatureSample(timeSec = t, bt = bt, et = et))
            delay(1000)
            t += 1.0
        }
    }
}
