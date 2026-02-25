package com.rdr.roast.domain.metrics

import com.rdr.roast.domain.RoastProfile

/**
 * Turning point (TP): the index at which bean temperature reaches its minimum
 * in the early phase of the roast (dip after charge).
 *
 * Search is limited to the first [searchWindowSec] seconds (default 3 min).
 * Returns null if there are fewer than 2 points.
 */
fun findTurningPointIndex(profile: RoastProfile, searchWindowSec: Double = 180.0): Int? {
    val timex = profile.timex
    val bt = profile.temp1
    if (timex.size < 2 || bt.size != timex.size) return null
    var minIdx = 0
    var minBt = bt[0]
    for (i in 1 until timex.size) {
        if (timex[i] > searchWindowSec) break
        if (bt[i] < minBt) {
            minBt = bt[i]
            minIdx = i
        }
    }
    return minIdx
}
