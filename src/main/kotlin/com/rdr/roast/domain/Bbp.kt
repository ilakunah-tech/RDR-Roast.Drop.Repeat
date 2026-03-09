package com.rdr.roast.domain

import kotlin.jvm.Synchronized

/**
 * Between Batch Protocol log: curves and metadata recorded between Stop and the next Start.
 * Immutable result; mirrors Cropster BetweenBatchLog.
 */
data class BetweenBatchLog(
    val startEpochMs: Long,
    val durationMs: Long,
    /** Time in seconds from BBP start (0 = moment of Stop). */
    val timex: List<Double>,
    /** BT (bean temp) per time. */
    val temp1: List<Double>,
    /** ET (environment temp) per time. */
    val temp2: List<Double>,
    val mode: TemperatureUnit = TemperatureUnit.CELSIUS,
    /** Time from BBP start to lowest BT, in ms. Null if empty. */
    val lowestTemperatureTimeMs: Long? = null,
    /** Time from BBP start to highest BT, in ms. Null if empty. */
    val highestTemperatureTimeMs: Long? = null,
    /** Cropster-style BBP comments entered from the BBP chart. */
    val comments: List<ProtocolComment> = emptyList(),
    /** ID of the roast that ended before this BBP. */
    val previousRoastId: String? = null,
    /** ID of the roast that started after this BBP. */
    val nextRoastId: String? = null,
    /** Count of gas change comments during BBP. */
    val gasChanges: Int = 0,
    /** Count of airflow change comments during BBP. */
    val airChanges: Int = 0
) {
    val isEmpty: Boolean get() = durationMs <= 0 || timex.isEmpty()

    /** Index of minimum BT (bean temp) in temp1. */
    fun lowestTemperatureIndex(): Int? = temp1.indices.minByOrNull { temp1[it] }

    /** Index of maximum BT in temp1. */
    fun highestTemperatureIndex(): Int? = temp1.indices.maxByOrNull { temp1[it] }
}

/**
 * Wrapper for a BetweenBatchLog before persistence, with optional previous/next batch dates
 * for curve normalization. Composition (not inheritance) since BetweenBatchLog is a data class.
 */
data class UnsavedBetweenBatchLog(
    val log: BetweenBatchLog,
    /** Epoch ms of previous roast end. */
    val previousBatchDate: Long? = null,
    /** Epoch ms of next roast start. */
    val nextBatchDate: Long? = null
) {
    /**
     * Returns a new BetweenBatchLog where:
     * - timex values are shifted to 0-based (subtract first timex value)
     * - entries with time >= (nextBatchDate - startEpochMs)/1000.0 are trimmed (if nextBatchDate is set)
     * - duration is recalculated from the last remaining timex entry
     */
    fun normalizeCurves(): BetweenBatchLog {
        val l = log
        if (l.timex.isEmpty()) return l

        val firstTime = l.timex.first()
        val cutoffSec = nextBatchDate?.let { (it - l.startEpochMs) / 1000.0 }

        val indices = l.timex.indices.filter { i ->
            val t = l.timex[i] - firstTime
            cutoffSec == null || t < cutoffSec
        }
        if (indices.isEmpty()) return l

        val newTimex = indices.map { l.timex[it] - firstTime }
        val newTemp1 = indices.map { l.temp1[it] }
        val newTemp2 = indices.map { l.temp2[it] }
        val lastTimeSec = newTimex.last()
        val newDurationMs = (lastTimeSec * 1000).toLong()

        val cutoffForComments = cutoffSec?.let { it + firstTime }
        val newComments = l.comments.filter { c ->
            cutoffForComments == null || c.timeSec < cutoffForComments
        }

        val lowIdx = newTemp1.indices.minByOrNull { newTemp1[it] }
        val highIdx = newTemp1.indices.maxByOrNull { newTemp1[it] }
        val lowestMs = lowIdx?.let { (newTimex.getOrNull(it) ?: 0.0) * 1000 }?.toLong()
        val highestMs = highIdx?.let { (newTimex.getOrNull(it) ?: 0.0) * 1000 }?.toLong()

        return BetweenBatchLog(
            startEpochMs = l.startEpochMs,
            durationMs = newDurationMs,
            timex = newTimex,
            temp1 = newTemp1,
            temp2 = newTemp2,
            mode = l.mode,
            lowestTemperatureTimeMs = lowestMs,
            highestTemperatureTimeMs = highestMs,
            comments = newComments,
            previousRoastId = l.previousRoastId,
            nextRoastId = l.nextRoastId,
            gasChanges = l.gasChanges,
            airChanges = l.airChanges
        )
    }

    /** Returns copy with nextBatchDate set. */
    fun setNextBatch(date: Long): UnsavedBetweenBatchLog = copy(nextBatchDate = date)
}

/**
 * Live BBP session: receives samples after Stop until Start (new roast) or Restart/Stop BBP.
 * Mirrors Cropster BetweenBatchModel; RDR uses a single BT/ET pair instead of named channels.
 */
class BetweenBatchSession(
    var startEpochMs: Long = System.currentTimeMillis(),
    val maxDurationSec: Double = 15 * 60.0
) {
    val timex: MutableList<Double> = mutableListOf()
    val temp1: MutableList<Double> = mutableListOf()
    val temp2: MutableList<Double> = mutableListOf()
    val comments: MutableList<ProtocolComment> = mutableListOf()

    private var _stopped: Boolean = false

    /** Whether this session has been stopped; all access goes through the same monitor. */
    fun isStopped(): Boolean = synchronized(this) { _stopped }

    @Synchronized
    fun addSample(timeSec: Double, bt: Double, et: Double) {
        if (_stopped) return
        if (timeSec >= maxDurationSec) return
        timex.add(timeSec)
        temp1.add(bt)
        temp2.add(et)
    }

    @Synchronized
    fun setStopped(value: Boolean) {
        _stopped = value
    }

    @Synchronized
    fun addComment(comment: ProtocolComment) {
        if (_stopped) return
        if (comment.timeSec >= maxDurationSec) return
        comments.add(comment)
    }

    /** Build immutable log from current data; computes lowest/highest BT times and gas/air change counts. */
    @Synchronized
    fun toLog(mode: TemperatureUnit = TemperatureUnit.CELSIUS): BetweenBatchLog? {
        if (timex.isEmpty()) return null
        val durationMs = ((timex.maxOrNull() ?: 0.0) * 1000).toLong()
        val lowIdx = temp1.withIndex().minByOrNull { it.value }?.index
        val highIdx = temp1.withIndex().maxByOrNull { it.value }?.index
        val lowestMs = lowIdx?.let { (timex.getOrNull(it) ?: 0.0) * 1000 }?.toLong()
        val highestMs = highIdx?.let { (timex.getOrNull(it) ?: 0.0) * 1000 }?.toLong()
        val gasChanges = comments.count { it.gas != null }
        val airChanges = comments.count { it.airflow != null }
        return BetweenBatchLog(
            startEpochMs = startEpochMs,
            durationMs = durationMs,
            timex = timex.toList(),
            temp1 = temp1.toList(),
            temp2 = temp2.toList(),
            mode = mode,
            lowestTemperatureTimeMs = lowestMs,
            highestTemperatureTimeMs = highestMs,
            comments = comments.toList(),
            gasChanges = gasChanges,
            airChanges = airChanges
        )
    }

    /** Clear data and set new start time; re-enable recording. */
    @Synchronized
    fun reset() {
        _stopped = false
        startEpochMs = System.currentTimeMillis()
        timex.clear()
        temp1.clear()
        temp2.clear()
        comments.clear()
    }
}
