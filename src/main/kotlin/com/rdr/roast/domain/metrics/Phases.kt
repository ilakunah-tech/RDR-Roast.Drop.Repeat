package com.rdr.roast.domain.metrics

import com.rdr.roast.domain.EventType
import com.rdr.roast.domain.PhaseDuration
import com.rdr.roast.domain.RoastProfile

/**
 * Computes roast phase durations: Drying, Maillard, Development.
 *
 * - Drying: CHARGE → CC (or FC if no CC, or end time if neither).
 * - Maillard: CC → FC (or end time if no FC). Only present when CC exists.
 * - Development: FC → end time. Only present when FC exists.
 *
 * End time is DROP if set, otherwise the last timex value.
 *
 * @return Non-empty list of phases with positive duration; empty if profile has < 2 points or no CHARGE.
 */
fun computePhases(profile: RoastProfile): List<PhaseDuration> {
    if (profile.timex.size < 2) return emptyList()

    val charge = profile.eventByType(EventType.CHARGE)
    val cc = profile.eventByType(EventType.CC)
    val fc = profile.eventByType(EventType.FC)
    val drop = profile.eventByType(EventType.DROP)

    val chargeTime = charge?.timeSec ?: return emptyList()
    val endTime = drop?.timeSec ?: profile.timex.lastOrNull() ?: return emptyList()
    val ccTime = cc?.timeSec
    val fcTime = fc?.timeSec

    val phases = mutableListOf<PhaseDuration>()

    val dryingEnd = ccTime ?: fcTime ?: endTime
    val drying = PhaseDuration("Drying", chargeTime, dryingEnd)
    if (drying.durationSec > 0) phases.add(drying)

    if (ccTime != null) {
        val maillardEnd = fcTime ?: endTime
        val maillard = PhaseDuration("Maillard", ccTime, maillardEnd)
        if (maillard.durationSec > 0) phases.add(maillard)
    }

    if (fcTime != null) {
        val development = PhaseDuration("Development", fcTime, endTime)
        if (development.durationSec > 0) phases.add(development)
    }

    return phases
}
