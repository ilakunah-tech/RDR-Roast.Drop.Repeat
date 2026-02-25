package com.rdr.roast.domain.metrics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RoRTest {

    @Test
    fun `linear series rising 1 degree per second yields RoR ~60 per minute`() {
        val timex = (0..60).map { it.toDouble() }
        val series = timex.map { 100.0 + it }
        val ror = computeRoR(series, timex, atIndex = 60, windowSec = 30.0)
        assertEquals(60.0, ror, 0.01)
    }

    @Test
    fun `empty list returns 0`() {
        assertEquals(0.0, computeRoR(emptyList(), emptyList(), atIndex = 0))
    }

    @Test
    fun `at index 0 returns 0`() {
        val timex = listOf(0.0, 10.0, 20.0)
        val series = listOf(100.0, 110.0, 120.0)
        assertEquals(0.0, computeRoR(series, timex, atIndex = 0))
    }

    @Test
    fun `short window handles gracefully`() {
        val timex = listOf(0.0, 5.0, 10.0)
        val series = listOf(100.0, 105.0, 110.0)
        val ror = computeRoR(series, timex, atIndex = 2, windowSec = 30.0)
        assertEquals(0.0, ror)
    }

    @Test
    fun `short window with enough data uses available window`() {
        val timex = listOf(0.0, 10.0, 20.0, 30.0)
        val series = listOf(100.0, 110.0, 120.0, 130.0)
        val ror = computeRoR(series, timex, atIndex = 3, windowSec = 30.0)
        assertEquals(60.0, ror, 0.01)
    }

    @Test
    fun `single point returns 0`() {
        val timex = listOf(0.0)
        val series = listOf(100.0)
        assertEquals(0.0, computeRoR(series, timex, atIndex = 0))
    }
}
