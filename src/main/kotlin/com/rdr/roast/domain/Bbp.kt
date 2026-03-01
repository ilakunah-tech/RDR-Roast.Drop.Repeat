package com.rdr.roast.domain

import kotlin.jvm.Synchronized

/**
 * Between Batch Protocol log: curves and metadata recorded between Stop and the next Start.
 * Immutable result; mirrors Cropster BetweenBatchLog (without previousBatch/nextBatch IDs).
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
    val highestTemperatureTimeMs: Long? = null
) {
    val isEmpty: Boolean get() = durationMs <= 0 || timex.isEmpty()

    /** Index of minimum BT (bean temp) in temp1. */
    fun lowestTemperatureIndex(): Int? = temp1.indices.minByOrNull { temp1[it] }

    /** Index of maximum BT in temp1. */
    fun highestTemperatureIndex(): Int? = temp1.indices.maxByOrNull { temp1[it] }
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

    /** Build immutable log from current data; computes lowest/highest BT times. */
    @Synchronized
    fun toLog(mode: TemperatureUnit = TemperatureUnit.CELSIUS): BetweenBatchLog? {
        if (timex.isEmpty()) return null
        val durationMs = ((timex.maxOrNull() ?: 0.0) * 1000).toLong()
        val lowIdx = temp1.withIndex().minByOrNull { it.value }?.index
        val highIdx = temp1.withIndex().maxByOrNull { it.value }?.index
        val lowestMs = lowIdx?.let { (timex.getOrNull(it) ?: 0.0) * 1000 }?.toLong()
        val highestMs = highIdx?.let { (timex.getOrNull(it) ?: 0.0) * 1000 }?.toLong()
        return BetweenBatchLog(
            startEpochMs = startEpochMs,
            durationMs = durationMs,
            timex = timex.toList(),
            temp1 = temp1.toList(),
            temp2 = temp2.toList(),
            mode = mode,
            lowestTemperatureTimeMs = lowestMs,
            highestTemperatureTimeMs = highestMs
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
    }
}
