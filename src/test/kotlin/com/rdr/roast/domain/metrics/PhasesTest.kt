package com.rdr.roast.domain.metrics

import com.rdr.roast.domain.EventType
import com.rdr.roast.domain.PhaseDuration
import com.rdr.roast.domain.RoastEvent
import com.rdr.roast.domain.RoastProfile
import com.rdr.roast.domain.TemperatureSample
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PhasesTest {

    private fun profileWith20Points(
        chargeSec: Double? = null,
        ccSec: Double? = null,
        dropSec: Double? = null
    ): RoastProfile {
        val profile = RoastProfile()
        for (i in 0..19) {
            val t = i.toDouble()
            val bt = 150.0 + (50.0 / 19.0) * t
            profile.addSample(TemperatureSample(t, bt, bt - 20.0))
        }
        chargeSec?.let { profile.addEvent(RoastEvent(it, EventType.CHARGE)) }
        ccSec?.let { profile.addEvent(RoastEvent(it, EventType.CC)) }
        dropSec?.let { profile.addEvent(RoastEvent(it, EventType.DROP)) }
        return profile
    }

    @Test
    fun `profile with CHARGE at 0 CC at 10 DROP at 19 yields Drying 10s Maillard 9s`() {
        val profile = profileWith20Points(chargeSec = 0.0, ccSec = 10.0, dropSec = 19.0)
        val phases = computePhases(profile)
        assertEquals(3, phases.size)
        assertEquals("Drying", phases[0].name)
        assertEquals(10.0, phases[0].durationSec, 0.001)
        assertEquals("Maillard", phases[1].name)
        assertEquals(9.0, phases[1].durationSec, 0.001)
        assertEquals("Development", phases[2].name)
        assertEquals(0.0, phases[2].durationSec, 0.001)
    }

    @Test
    fun `profile with no events returns zero-duration phases`() {
        val profile = profileWith20Points()
        val phases = computePhases(profile)
        assertEquals(3, phases.size)
        assertEquals(0.0, phases[0].durationSec, 0.001)
        assertEquals(0.0, phases[1].durationSec, 0.001)
        assertEquals(0.0, phases[2].durationSec, 0.001)
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
