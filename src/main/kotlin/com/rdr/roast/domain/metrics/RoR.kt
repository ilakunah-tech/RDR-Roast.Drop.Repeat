package com.rdr.roast.domain.metrics

/**
 * Computes Rate of Rise (derivative) at the given index using a sliding window.
 *
 * @param series Temperature series (e.g. bean temp)
 * @param timex Time series in seconds
 * @param atIndex Index at which to compute RoR
 * @param windowSec Window size in seconds (look backwards from atIndex)
 * @return RoR in degrees per minute, or 0.0 if not enough data
 */
fun computeRoR(
    series: List<Double>,
    timex: List<Double>,
    atIndex: Int,
    windowSec: Double = 30.0
): Double {
    if (series.isEmpty() || timex.isEmpty()) return 0.0
    if (series.size != timex.size) return 0.0
    if (atIndex < 0 || atIndex >= series.size) return 0.0
    if (atIndex == 0) return 0.0

    val targetTime = timex[atIndex]
    val minTime = targetTime - windowSec

    var startIndex = atIndex - 1
    while (startIndex >= 0 && timex[startIndex] > minTime) {
        startIndex--
    }
    if (startIndex < 0) return 0.0

    val dt = targetTime - timex[startIndex]
    if (dt <= 0.0) return 0.0

    val dTemp = series[atIndex] - series[startIndex]
    return (dTemp / dt) * 60.0
}
