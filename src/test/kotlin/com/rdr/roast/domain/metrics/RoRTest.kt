package com.rdr.roast.domain.metrics

import com.rdr.roast.domain.curves.RorCurveModel
import com.rdr.roast.domain.curves.StandardCurveModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Tests for the RorCurveModel (polynomial fit Rate-of-Rise).
 * Timestamps in milliseconds; values in °C; expected results in °C/min.
 */
class RoRTest {

    private fun buildModels(windowMs: Long = 30_000L): Pair<StandardCurveModel, RorCurveModel> {
        val raw = StandardCurveModel("BT")
        val ror = RorCurveModel(raw, windowMs)
        return raw to ror
    }

    @Test
    fun `linear series rising 1 degree per second yields RoR ~60 per minute`() {
        val (raw, ror) = buildModels(windowMs = 30_000L)
        // Feed 61 samples at 1-second intervals, each rising 1°C
        for (i in 0..60) {
            raw.put(i * 1000L, 100.0 + i)
        }
        val result = ror.getValue(60_000L)
        assertEquals(60.0, result!!, 0.5)
    }

    @Test
    fun `empty model returns null for any time`() {
        val (_, ror) = buildModels()
        assertNull(ror.getValue(0L))
        assertNull(ror.getValue(30_000L))
    }

    @Test
    fun `single point produces no RoR (needs at least 2 samples in window)`() {
        val (raw, ror) = buildModels()
        raw.put(0L, 100.0)
        assertNull(ror.getValue(0L))
    }

    @Test
    fun `two points in window produce correct RoR`() {
        val (raw, ror) = buildModels(windowMs = 30_000L)
        raw.put(0L, 100.0)
        raw.put(10_000L, 110.0)   // +10°C in 10 s → 60°C/min
        val result = ror.getValue(10_000L)
        assertEquals(60.0, result!!, 0.5)
    }

    @Test
    fun `flat series yields RoR near zero`() {
        val (raw, ror) = buildModels(windowMs = 30_000L)
        for (i in 0..30) {
            raw.put(i * 1000L, 200.0)
        }
        val result = ror.getValue(30_000L)
        assertEquals(0.0, result!!, 0.1)
    }
}
