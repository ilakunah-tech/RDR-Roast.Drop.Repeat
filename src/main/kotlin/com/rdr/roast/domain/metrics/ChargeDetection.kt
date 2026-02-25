package com.rdr.roast.domain.metrics

import com.rdr.roast.domain.RoastProfile

/**
 * Auto-detect charge: finds the index at which a significant BT drop starts (cold beans absorbing heat).
 * Cropster-style: "temperature drops by X °C over Y seconds" → charge at start of that drop.
 *
 * @param profile current profile (timex, temp1 = BT)
 * @param windowSec look-back window in seconds (default 30)
 * @param minDropC minimum BT drop in °C to consider as charge (default 6)
 * @param minElapsedSec do not detect before this many seconds from first sample (default 18)
 * @return index in timex where charge should be set (start of drop), or null if not detected
 */
fun findChargeDropIndex(
    profile: RoastProfile,
    windowSec: Double = 30.0,
    minDropC: Double = 6.0,
    minElapsedSec: Double = 18.0
): Int? {
    val timex = profile.timex
    val bt = profile.temp1
    if (timex.size < 2 || bt.size != timex.size) return null
    val n = timex.size
    for (i in 1 until n) {
        val tNow = timex[i]
        if (tNow < minElapsedSec) continue
        val tStart = tNow - windowSec
        val j = timex.indexOfFirst { it >= tStart }.takeIf { it >= 0 } ?: 0
        if (j >= i) continue
        val drop = bt[j] - bt[i]
        if (drop >= minDropC) return j
    }
    return null
}
