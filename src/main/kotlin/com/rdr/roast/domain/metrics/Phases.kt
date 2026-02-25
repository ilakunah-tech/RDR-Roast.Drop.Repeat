package com.rdr.roast.domain.metrics

import com.rdr.roast.domain.EventType
import com.rdr.roast.domain.PhaseDuration
import com.rdr.roast.domain.RoastProfile

/**
 * Computes roast phase durations: Drying, Maillard, Development.
 *
 * - Drying: CHARGE to CC. If no CHARGE, uses timex[0]. If no CC, duration 0.
 * - Maillard: CC to DROP (MVP has no separate FC; Maillard is CC→DROP).
 * - Development: 0 duration for MVP (will be refined when FC event is added).
 *
 * @return List of 3 PhaseDuration when profile has >= 2 points; empty list otherwise.
 */
fun computePhases(profile: RoastProfile): List<PhaseDuration> {
    if (profile.timex.size < 2) return emptyList()

    val charge = profile.eventByType(EventType.CHARGE)
    val cc = profile.eventByType(EventType.CC)
    val drop = profile.eventByType(EventType.DROP)

    val dryingStart = charge?.timeSec ?: profile.timex.first()
    val dryingEnd = cc?.timeSec ?: dryingStart
    val drying = PhaseDuration("Drying", dryingStart, dryingEnd)

    val maillardStart = cc?.timeSec ?: 0.0
    val maillardEnd = drop?.timeSec ?: maillardStart
    val maillard = PhaseDuration("Maillard", maillardStart, maillardEnd)

    val development = PhaseDuration("Development", 0.0, 0.0)

    return listOf(drying, maillard, development)
}
