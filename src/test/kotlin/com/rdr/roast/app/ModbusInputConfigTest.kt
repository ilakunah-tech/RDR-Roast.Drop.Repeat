package com.rdr.roast.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests for [ModbusInputConfig] (Artisan-style Input 1–10): divisionFactor from dividerIndex.
 */
class ModbusInputConfigTest {

    @Test
    fun `dividerIndex 0 yields divisionFactor 1`() {
        val config = ModbusInputConfig(dividerIndex = 0)
        assertEquals(1.0, config.divisionFactor())
    }

    @Test
    fun `dividerIndex 1 yields divisionFactor 10`() {
        val config = ModbusInputConfig(dividerIndex = 1)
        assertEquals(10.0, config.divisionFactor())
    }

    @Test
    fun `dividerIndex 2 yields divisionFactor 100`() {
        val config = ModbusInputConfig(dividerIndex = 2)
        assertEquals(100.0, config.divisionFactor())
    }

    @Test
    fun `default ModbusInputConfig has dividerIndex 0`() {
        val config = ModbusInputConfig()
        assertEquals(0, config.dividerIndex)
        assertEquals(1.0, config.divisionFactor())
    }

    @Test
    fun `defaultModbusInputs returns 10 entries`() {
        val list = defaultModbusInputs()
        assertEquals(10, list.size)
        list.forEach { assertEquals(ModbusInputConfig(), it) }
    }
}
