package com.rdr.roast.domain.metrics

import com.rdr.roast.domain.EventType
import com.rdr.roast.domain.PhaseDuration
import com.rdr.roast.domain.RoastEvent
import com.rdr.roast.domain.RoastProfile
import com.rdr.roast.domain.TemperatureSample
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PhasesTest {

    private fun profileWithPoints(
        count: Int = 20,
        chargeSec: Double? = null,
        ccSec: Double? = null,
        fcSec: Double? = null,
        dropSec: Double? = null
    ): RoastProfile {
        val profile = RoastProfile()
        for (i in 0 until count) {
            val t = i.toDouble()
            val bt = 150.0 + (50.0 / (count - 1).coerceAtLeast(1)) * t
            profile.addSample(TemperatureSample(t, bt, bt - 20.0))
        }
        chargeSec?.let { profile.addEvent(RoastEvent(it, EventType.CHARGE)) }
        ccSec?.let { profile.addEvent(RoastEvent(it, EventType.CC)) }
        fcSec?.let { profile.addEvent(RoastEvent(it, EventType.FC)) }
        dropSec?.let { profile.addEvent(RoastEvent(it, EventType.DROP)) }
        return profile
    }

    @Test
    fun `profile with CHARGE CC DROP but no FC yields Drying and Maillard only`() {
        val profile = profileWithPoints(chargeSec = 0.0, ccSec = 10.0, dropSec = 19.0)
        val phases = computePhases(profile)
        assertEquals(2, phases.size)
        assertEquals("Drying", phases[0].name)
        assertEquals(10.0, phases[0].durationSec, 0.001)
        assertEquals("Maillard", phases[1].name)
        assertEquals(9.0, phases[1].durationSec, 0.001)
    }

    @Test
    fun `profile with no events returns empty list`() {
        val profile = profileWithPoints()
        val phases = computePhases(profile)
        assertTrue(phases.isEmpty(), "No CHARGE → empty list")
    }

    @Test
    fun `only CHARGE yields Drying growing to current time`() {
        val profile = profileWithPoints(chargeSec = 0.0)
        val phases = computePhases(profile)
        assertEquals(1, phases.size)
        assertEquals("Drying", phases[0].name)
        assertEquals(19.0, phases[0].durationSec, 0.001)
    }

    @Test
    fun `CHARGE and CC yields Drying and Maillard growing`() {
        val profile = profileWithPoints(chargeSec = 0.0, ccSec = 8.0)
        val phases = computePhases(profile)
        assertEquals(2, phases.size)
        assertEquals("Drying", phases[0].name)
        assertEquals(8.0, phases[0].durationSec, 0.001)
        assertEquals("Maillard", phases[1].name)
        assertEquals(11.0, phases[1].durationSec, 0.001)
    }

    @Test
    fun `CHARGE CC FC yields all three phases growing`() {
        val profile = profileWithPoints(chargeSec = 0.0, ccSec = 6.0, fcSec = 12.0)
        val phases = computePhases(profile)
        assertEquals(3, phases.size)
        assertEquals("Drying", phases[0].name)
        assertEquals(6.0, phases[0].durationSec, 0.001)
        assertEquals("Maillard", phases[1].name)
        assertEquals(6.0, phases[1].durationSec, 0.001)
        assertEquals("Development", phases[2].name)
        assertEquals(7.0, phases[2].durationSec, 0.001)
    }

    @Test
    fun `CHARGE CC FC DROP yields all three with correct percentages`() {
        val profile = profileWithPoints(chargeSec = 0.0, ccSec = 6.0, fcSec = 12.0, dropSec = 18.0)
        val phases = computePhases(profile)
        assertEquals(3, phases.size)
        val total = 18.0
        assertEquals("Drying", phases[0].name)
        assertEquals(6.0, phases[0].durationSec, 0.001)
        assertEquals(6.0 / total * 100.0, phases[0].percent(total), 0.01)
        assertEquals("Maillard", phases[1].name)
        assertEquals(6.0, phases[1].durationSec, 0.001)
        assertEquals("Development", phases[2].name)
        assertEquals(6.0, phases[2].durationSec, 0.001)
    }

    @Test
    fun `profile with less than 2 points returns empty list`() {
        val profile = RoastProfile()
        profile.addSample(TemperatureSample(0.0, 150.0, 130.0))
        val phases = computePhases(profile)
        assertEquals(emptyList<PhaseDuration>(), phases)
    }

    @Test
    fun `profile with empty timex returns empty list`() {
        val profile = RoastProfile()
        val phases = computePhases(profile)
        assertEquals(emptyList<PhaseDuration>(), phases)
    }
}
