package com.rdr.roast.driver.simulator

import com.rdr.roast.app.MachineConfig
import com.rdr.roast.driver.ConnectionState
import com.rdr.roast.driver.RoastDataSource
import com.rdr.roast.domain.TemperatureSample
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import org.slf4j.LoggerFactory

/**
 * Realistic S-curve roast simulator.
 * - Charge: BT starts ~200°C (hot drum), drops to TP ~90–100°C by t≈60–90s.
 * - Drying (90–240s): BT ~100→150°C, RoR ~10–15 °C/min.
 * - Maillard (240–420s): BT ~150→195°C, RoR ~12→8 °C/min.
 * - Development (420–540s): BT ~195→210°C, RoR ~5–7 °C/min, drop ~540s.
 * - ET: ~300°C down to ~250°C (smooth, slower than BT).
 * - ±0.3°C noise for realism.
 */
class SimulatorSource(private val config: MachineConfig) : RoastDataSource {

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
            val bt = btCurve(t)
            val et = etCurve(t)
            val noise = 0.3 * (Random.nextDouble() * 2.0 - 1.0)
            emit(TemperatureSample(timeSec = t, bt = bt + noise, et = et + noise))
            delay(config.pollingIntervalMs)
            t += config.pollingIntervalMs / 1000.0
        }
    }

    /**
     * S-curve BT: charge drop to TP ~75s, then smooth rise through drying → Maillard → development.
     * Designed so 30s-window RoR stays in roughly -5..25 °C/min and TP is at 60–90s.
     */
    private fun btCurve(t: Double): Double {
        val tpSec = 75.0       // turning point time (min BT)
        val btTp = 97.0        // BT at TP °C
        val chargeTemp = 200.0 // drum at charge

        return when {
            // Charge → TP: smooth drop from 200 to ~97 by t=75s; zero derivative at t=0 and t=tp (smoothstep)
            t <= tpSec -> {
                val x = t / tpSec
                val smooth = x * x * (3.0 - 2.0 * x)
                chargeTemp - (chargeTemp - btTp) * smooth
            }
            // Drying 75–240s: 97 → 150, gentle rise
            t <= 240.0 -> {
                val u = (t - tpSec) / (240.0 - tpSec)
                val s = u * u * (3.0 - 2.0 * u)
                btTp + (150.0 - btTp) * s
            }
            // Maillard 240–420s: 150 → 195, RoR tapering 12→8
            t <= 420.0 -> {
                val u = (t - 240.0) / 180.0
                val s = u + 0.15 * sin(PI * u)
                150.0 + 45.0 * s
            }
            // Development 420–540s: 195 → 210, RoR ~5–7
            t <= 540.0 -> {
                val u = (t - 420.0) / 120.0
                195.0 + 15.0 * u
            }
            // After drop: hold ~210
            else -> 210.0
        }
    }

    /**
     * ET: starts ~300°C, smoothly decreases to ~250°C by end of roast (slower change than BT).
     */
    private fun etCurve(t: Double): Double {
        val endSec = 540.0
        if (t >= endSec) return 250.0
        val u = t / endSec
        val smooth = 1.0 - u * u * (3.0 - 2.0 * u)
        return 250.0 + 50.0 * smooth
    }
}
