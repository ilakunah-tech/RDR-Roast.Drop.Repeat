package com.rdr.roast.domain

data class BbpStatistics(
    /** Total BBP duration in seconds. */
    val durationSec: Double,
    /** Minimum BT during BBP. */
    val minTemp: Double?,
    /** Time (seconds from BBP start) when min BT occurred. */
    val minTempTimeSec: Double?,
    /** Maximum BT during BBP. */
    val maxTemp: Double?,
    /** Time (seconds from BBP start) when max BT occurred. */
    val maxTempTimeSec: Double?,
    /** Recovery rate: °C/min from min BT to end of BBP. */
    val recoveryRate: Double?,
    /** Number of gas change comments. */
    val gasChanges: Int,
    /** Number of airflow change comments. */
    val airChanges: Int,
    /** Seconds from last gas change to BBP end (time to reach start temp). */
    val timeFromLastGasChangeSec: Double?
)

data class BbpComparison(
    val durationDeltaSec: Double,
    val minTempDelta: Double?,
    val recoveryRateDelta: Double?,
    val gasChangesDelta: Int,
    val airChangesDelta: Int
)

object BbpAnalytics {

    fun computeStatistics(log: BetweenBatchLog): BbpStatistics {
        val durationSec = log.durationMs / 1000.0
        if (log.temp1.isEmpty() || log.timex.isEmpty()) {
            return BbpStatistics(
                durationSec = durationSec,
                minTemp = null, minTempTimeSec = null,
                maxTemp = null, maxTempTimeSec = null,
                recoveryRate = null,
                gasChanges = log.gasChanges,
                airChanges = log.airChanges,
                timeFromLastGasChangeSec = null
            )
        }
        val minIdx = log.temp1.indices.minByOrNull { log.temp1[it] }!!
        val maxIdx = log.temp1.indices.maxByOrNull { log.temp1[it] }!!
        val minTemp = log.temp1[minIdx]
        val maxTemp = log.temp1[maxIdx]
        val minTempTimeSec = log.timex.getOrNull(minIdx)
        val maxTempTimeSec = log.timex.getOrNull(maxIdx)

        val endTemp = log.temp1.last()
        val recoveryRate = if (minTempTimeSec != null && durationSec > minTempTimeSec && durationSec > 0) {
            val dtMin = (durationSec - minTempTimeSec) / 60.0
            if (dtMin > 0) (endTemp - minTemp) / dtMin else null
        } else null

        val lastGasComment = log.comments.filter { it.gas != null }.maxByOrNull { it.timeSec }
        val timeFromLastGas = lastGasComment?.let { durationSec - it.timeSec }

        return BbpStatistics(
            durationSec = durationSec,
            minTemp = minTemp,
            minTempTimeSec = minTempTimeSec,
            maxTemp = maxTemp,
            maxTempTimeSec = maxTempTimeSec,
            recoveryRate = recoveryRate,
            gasChanges = log.gasChanges,
            airChanges = log.airChanges,
            timeFromLastGasChangeSec = timeFromLastGas
        )
    }

    fun compare(current: BbpStatistics, reference: BbpStatistics): BbpComparison {
        return BbpComparison(
            durationDeltaSec = current.durationSec - reference.durationSec,
            minTempDelta = if (current.minTemp != null && reference.minTemp != null) current.minTemp - reference.minTemp else null,
            recoveryRateDelta = if (current.recoveryRate != null && reference.recoveryRate != null) current.recoveryRate - reference.recoveryRate else null,
            gasChangesDelta = current.gasChanges - reference.gasChanges,
            airChangesDelta = current.airChanges - reference.airChanges
        )
    }
}
