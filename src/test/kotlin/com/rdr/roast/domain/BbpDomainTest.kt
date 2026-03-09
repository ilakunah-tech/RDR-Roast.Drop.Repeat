package com.rdr.roast.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BbpDomainTest {

    @Test
    fun betweenBatchLog_new_fields_have_defaults() {
        val log = BetweenBatchLog(
            startEpochMs = 1000L,
            durationMs = 5000L,
            timex = listOf(0.0, 1.0, 2.0),
            temp1 = listOf(200.0, 195.0, 198.0),
            temp2 = listOf(300.0, 295.0, 298.0)
        )
        assertNull(log.previousRoastId)
        assertNull(log.nextRoastId)
        assertEquals(0, log.gasChanges)
        assertEquals(0, log.airChanges)
    }

    @Test
    fun betweenBatchLog_with_roast_ids() {
        val log = BetweenBatchLog(
            startEpochMs = 1000L,
            durationMs = 5000L,
            timex = listOf(0.0, 1.0),
            temp1 = listOf(200.0, 195.0),
            temp2 = listOf(300.0, 295.0),
            previousRoastId = "roast-abc",
            nextRoastId = "roast-def",
            gasChanges = 3,
            airChanges = 2
        )
        assertEquals("roast-abc", log.previousRoastId)
        assertEquals("roast-def", log.nextRoastId)
        assertEquals(3, log.gasChanges)
        assertEquals(2, log.airChanges)
    }

    @Test
    fun session_toLog_computes_gas_and_air_changes() {
        val session = BetweenBatchSession(startEpochMs = 1000L)
        session.addSample(0.0, 200.0, 300.0)
        session.addSample(1.0, 195.0, 295.0)
        session.addComment(ProtocolComment(timeSec = 0.5, text = "Gas up", gas = 5.0))
        session.addComment(ProtocolComment(timeSec = 0.8, text = "Air up", airflow = 3.0))
        session.addComment(ProtocolComment(timeSec = 0.9, text = "Both", gas = 6.0, airflow = 4.0))

        val log = session.toLog()!!
        assertEquals(2, log.gasChanges)
        assertEquals(2, log.airChanges)
    }

    @Test
    fun unsavedBbpLog_normalizeCurves_shifts_to_zero_based() {
        val log = BetweenBatchLog(
            startEpochMs = 10000L,
            durationMs = 3000L,
            timex = listOf(1.0, 2.0, 3.0),
            temp1 = listOf(200.0, 195.0, 198.0),
            temp2 = listOf(300.0, 295.0, 298.0)
        )
        val unsaved = UnsavedBetweenBatchLog(log = log)
        val normalized = unsaved.normalizeCurves()

        assertEquals(listOf(0.0, 1.0, 2.0), normalized.timex)
        assertEquals(3, normalized.temp1.size)
        assertEquals(3, normalized.temp2.size)
    }

    @Test
    fun unsavedBbpLog_normalizeCurves_trims_at_next_batch_date() {
        val startMs = 10000L
        val log = BetweenBatchLog(
            startEpochMs = startMs,
            durationMs = 5000L,
            timex = listOf(0.0, 1.0, 2.0, 3.0, 4.0, 5.0),
            temp1 = listOf(200.0, 198.0, 195.0, 193.0, 196.0, 200.0),
            temp2 = listOf(300.0, 298.0, 295.0, 293.0, 296.0, 300.0),
            comments = listOf(
                ProtocolComment(timeSec = 1.0, text = "early"),
                ProtocolComment(timeSec = 4.0, text = "late")
            )
        )
        val nextBatchDate = startMs + 3000L
        val unsaved = UnsavedBetweenBatchLog(log = log, nextBatchDate = nextBatchDate)
        val normalized = unsaved.normalizeCurves()

        assertEquals(3, normalized.timex.size)
        assertEquals(listOf(0.0, 1.0, 2.0), normalized.timex)
        assertEquals(3, normalized.temp1.size)
        val lastMs = (normalized.timex.last() * 1000).toLong()
        assertEquals(lastMs, normalized.durationMs)
        assertEquals(1, normalized.comments.size)
        assertEquals("early", normalized.comments.first().text)
    }

    @Test
    fun unsavedBbpLog_setNextBatch_computes_copy() {
        val log = BetweenBatchLog(
            startEpochMs = 10000L,
            durationMs = 0L,
            timex = listOf(0.0),
            temp1 = listOf(200.0),
            temp2 = listOf(300.0)
        )
        val unsaved = UnsavedBetweenBatchLog(log = log, previousBatchDate = 5000L)
        val withNext = unsaved.setNextBatch(15000L)

        assertEquals(15000L, withNext.nextBatchDate)
        assertEquals(5000L, withNext.previousBatchDate)
    }

    @Test
    fun unsavedBbpLog_normalizeCurves_empty_log_returns_same() {
        val log = BetweenBatchLog(
            startEpochMs = 0L,
            durationMs = 0L,
            timex = emptyList(),
            temp1 = emptyList(),
            temp2 = emptyList()
        )
        val unsaved = UnsavedBetweenBatchLog(log = log)
        val normalized = unsaved.normalizeCurves()
        assertTrue(normalized.timex.isEmpty())
    }
}
