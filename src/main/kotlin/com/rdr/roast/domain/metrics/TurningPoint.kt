package com.rdr.roast.domain.metrics

import com.rdr.roast.domain.RoastProfile

/**
 * Turning point (TP): the index at which bean temperature reaches its minimum
 * in the early phase of the roast (dip after charge).
 *
 * If [chargeTimeSec] is set, search only in [chargeTimeSec, chargeTimeSec + searchWindowAfterChargeSec].
 * Otherwise search in [0, fallbackWindowSec] (legacy behavior).
 * Returns null if there are fewer than 2 points or no points in range.
 */
fun findTurningPointIndex(
    profile: RoastProfile,
    chargeTimeSec: Double? = null,
    searchWindowAfterChargeSec: Double = 120.0,
    fallbackWindowSec: Double = 180.0
): Int? {
    val timex = profile.timex
    val bt = profile.temp1
    if (timex.size < 2 || bt.size != timex.size) return null

    val (tStart, tEnd) = if (chargeTimeSec != null) {
        chargeTimeSec to (chargeTimeSec + searchWindowAfterChargeSec)
    } else {
        0.0 to fallbackWindowSec
    }

    var minIdx = -1
    var minBt = Double.MAX_VALUE
    for (i in timex.indices) {
        val t = timex[i]
        if (t < tStart) continue
        if (t > tEnd) break
        if (bt[i] < minBt) {
            minBt = bt[i]
            minIdx = i
        }
    }
    return minIdx.takeIf { it >= 0 }
}
