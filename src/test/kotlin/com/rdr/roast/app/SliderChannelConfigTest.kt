package com.rdr.roast.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests for Artisan-style slider register value formula: value = (factor * sliderValue) + offset,
 * then clamped to 0..65535. Logic lives in BescaModbusTcpSource; we test the formula contract here
 * via a local implementation to avoid depending on driver internals.
 */
class SliderChannelConfigTest {

    /** Replicates BescaModbusTcpSource formula for testing. */
    private fun computeRegisterValue(sliderValue: Double, factor: Double, offset: Double): Int {
        val raw = (factor * sliderValue) + offset
        return raw.toInt().coerceIn(0, 65535)
    }

    @Test
    fun `factor 1 offset 0 leaves value unchanged`() {
        assertEquals(0, computeRegisterValue(0.0, 1.0, 0.0))
        assertEquals(100, computeRegisterValue(100.0, 1.0, 0.0))
        assertEquals(50, computeRegisterValue(50.0, 1.0, 0.0))
    }

    @Test
    fun `factor and offset applied correctly`() {
        // Artisan Burner example: factor 55, offset 0
        assertEquals(0, computeRegisterValue(0.0, 55.0, 0.0))
        assertEquals(5500, computeRegisterValue(100.0, 55.0, 0.0))
        // Artisan Air: factor 4.85, offset 100
        assertEquals(100, computeRegisterValue(0.0, 4.85, 100.0))
        assertEquals(585, computeRegisterValue(100.0, 4.85, 100.0))
    }

    @Test
    fun `result clamped to 0-65535`() {
        assertEquals(65535, computeRegisterValue(10000.0, 10.0, 0.0))
        assertEquals(0, computeRegisterValue(-100.0, 1.0, 0.0))
        assertEquals(0, computeRegisterValue(0.0, -1.0, 0.0))
    }

    @Test
    fun `default SliderChannelConfig has expected defaults`() {
        val c = SliderChannelConfig()
        assertEquals(0, c.register)
        assertEquals(0.0, c.min)
        assertEquals(100.0, c.max)
        assertEquals(1.0, c.factor)
        assertEquals(0.0, c.offset)
        assertEquals(1.0, c.step)
    }
}
