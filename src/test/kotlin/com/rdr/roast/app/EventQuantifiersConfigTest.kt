package com.rdr.roast.app

import com.rdr.roast.domain.ControlEventType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests for [EventQuantifiersConfig]: get(type) returns correct config per slider event.
 */
class EventQuantifiersConfigTest {

    @Test
    fun `get returns air config for AIR`() {
        val airConfig = EventQuantifierConfig(source = QuantifierSource.ET, min = 10, max = 90)
        val config = EventQuantifiersConfig(air = airConfig)
        assertEquals(airConfig, config.get(ControlEventType.AIR))
        assertEquals(QuantifierSource.ET, config.get(ControlEventType.AIR).source)
        assertEquals(10, config.get(ControlEventType.AIR).min)
        assertEquals(90, config.get(ControlEventType.AIR).max)
    }

    @Test
    fun `get returns drum config for DRUM`() {
        val drumConfig = EventQuantifierConfig(source = QuantifierSource.BT, step = 5.0)
        val config = EventQuantifiersConfig(drum = drumConfig)
        assertEquals(drumConfig, config.get(ControlEventType.DRUM))
        assertEquals(QuantifierSource.BT, config.get(ControlEventType.DRUM).source)
    }

    @Test
    fun `get returns damper config for DAMPER`() {
        val damperConfig = EventQuantifierConfig(min = 0, max = 50)
        val config = EventQuantifiersConfig(damper = damperConfig)
        assertEquals(damperConfig, config.get(ControlEventType.DAMPER))
    }

    @Test
    fun `get returns burner config for GAS`() {
        val burnerConfig = EventQuantifierConfig(source = QuantifierSource.ET, actionEnabled = true)
        val config = EventQuantifiersConfig(burner = burnerConfig)
        assertEquals(burnerConfig, config.get(ControlEventType.GAS))
        assertEquals(true, config.get(ControlEventType.GAS).actionEnabled)
    }

    @Test
    fun `default config has NONE source and 0-100 range`() {
        val config = EventQuantifiersConfig()
        assertEquals(QuantifierSource.NONE, config.get(ControlEventType.AIR).source)
        assertEquals(0, config.get(ControlEventType.AIR).min)
        assertEquals(100, config.get(ControlEventType.AIR).max)
    }
}
