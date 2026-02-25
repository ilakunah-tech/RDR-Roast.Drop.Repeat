package com.rdr.roast.driver.simulator

import com.rdr.roast.app.ProfileStorage
import com.rdr.roast.driver.ConnectionState
import com.rdr.roast.driver.RoastDataSource
import com.rdr.roast.domain.RoastProfile
import com.rdr.roast.domain.TemperatureSample
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * Replays a roast from an Artisan .alog file as a live stream (1 sample per second).
 * BT/ET are linear-interpolated from the profile. Use for demo/testing with a real profile.
 */
class AlogReplaySource(
    private val alogPath: Path
) : RoastDataSource {

    private val log = LoggerFactory.getLogger(AlogReplaySource::class.java)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override fun connectionState(): StateFlow<ConnectionState> = _connectionState

    @Volatile
    private var running = false

    private var profile: RoastProfile? = null

    override fun connect() {
        running = true
        profile = try {
            ProfileStorage.loadProfile(alogPath).also {
                log.info("Demo profile loaded from {} ({} points)", alogPath.fileName, it.timex.size)
            }
        } catch (e: Exception) {
            log.warn("Failed to load demo profile from {}: {}", alogPath, e.message)
            null
        }
        _connectionState.value = ConnectionState.Connected("Demo: ${alogPath.fileName}")
    }

    override fun disconnect() {
        running = false
        profile = null
        _connectionState.value = ConnectionState.Disconnected
    }

    override fun sampleFlow(): Flow<TemperatureSample> = flow {
        val p = profile
        if (p == null || p.timex.isEmpty()) {
            log.warn("No profile to replay")
            return@flow
        }
        val timex = p.timex
        val bt = p.temp1
        val et = p.temp2
        val maxTime = timex.maxOrNull() ?: 0.0
        var t = 0.0
        while (running && t <= maxTime) {
            val sample = TemperatureSample(
                timeSec = t,
                bt = interpolate(timex, bt, t),
                et = interpolate(timex, et, t)
            )
            emit(sample)
            delay(1000)
            t += 1.0
        }
    }

    private fun interpolate(timex: List<Double>, values: List<Double>, t: Double): Double {
        if (values.isEmpty()) return 0.0
        if (t <= timex[0]) return values[0]
        if (t >= timex.last()) return values.last()
        for (i in 0 until timex.size - 1) {
            if (timex[i] <= t && t <= timex[i + 1]) {
                val a = (t - timex[i]) / (timex[i + 1] - timex[i])
                return values[i] + a * (values[i + 1] - values[i])
            }
        }
        return values.last()
    }
}
