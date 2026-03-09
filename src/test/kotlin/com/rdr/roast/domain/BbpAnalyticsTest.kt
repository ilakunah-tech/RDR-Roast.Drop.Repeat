package com.rdr.roast.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BbpAnalyticsTest {

    @Test
    fun computeStatistics_basic() {
        val log = BetweenBatchLog(
            startEpochMs = 1000L,
            durationMs = 120000L,
            timex = listOf(0.0, 30.0, 60.0, 90.0, 120.0),
            temp1 = listOf(210.0, 200.0, 195.0, 198.0, 205.0),
            temp2 = listOf(300.0, 290.0, 285.0, 288.0, 295.0),
            gasChanges = 2,
            airChanges = 1,
            comments = listOf(
                ProtocolComment(timeSec = 10.0, text = "Gas up", gas = 5.0),
                ProtocolComment(timeSec = 80.0, text = "Gas down", gas = 3.0)
            )
        )
        val stats = BbpAnalytics.computeStatistics(log)
        assertEquals(120.0, stats.durationSec)
        assertEquals(195.0, stats.minTemp)
        assertEquals(60.0, stats.minTempTimeSec)
        assertEquals(210.0, stats.maxTemp)
        assertEquals(0.0, stats.maxTempTimeSec)
        assertNotNull(stats.recoveryRate)
        assertTrue(stats.recoveryRate!! > 0)
        assertEquals(2, stats.gasChanges)
        assertEquals(1, stats.airChanges)
        assertNotNull(stats.timeFromLastGasChangeSec)
        assertEquals(40.0, stats.timeFromLastGasChangeSec)
    }

    @Test
    fun computeStatistics_empty_log() {
        val log = BetweenBatchLog(
            startEpochMs = 0L,
            durationMs = 0L,
            timex = emptyList(),
            temp1 = emptyList(),
            temp2 = emptyList()
        )
        val stats = BbpAnalytics.computeStatistics(log)
        assertEquals(0.0, stats.durationSec)
        assertNull(stats.minTemp)
        assertNull(stats.recoveryRate)
    }

    @Test
    fun compare_two_statistics() {
        val current = BbpStatistics(
            durationSec = 120.0,
            minTemp = 195.0, minTempTimeSec = 60.0,
            maxTemp = 210.0, maxTempTimeSec = 0.0,
            recoveryRate = 10.0,
            gasChanges = 3, airChanges = 1,
            timeFromLastGasChangeSec = 40.0
        )
        val reference = BbpStatistics(
            durationSec = 100.0,
            minTemp = 190.0, minTempTimeSec = 50.0,
            maxTemp = 215.0, maxTempTimeSec = 0.0,
            recoveryRate = 12.0,
            gasChanges = 2, airChanges = 2,
            timeFromLastGasChangeSec = 30.0
        )
        val cmp = BbpAnalytics.compare(current, reference)
        assertEquals(20.0, cmp.durationDeltaSec)
        assertEquals(5.0, cmp.minTempDelta)
        assertEquals(-2.0, cmp.recoveryRateDelta)
        assertEquals(1, cmp.gasChangesDelta)
        assertEquals(-1, cmp.airChangesDelta)
    }
}
