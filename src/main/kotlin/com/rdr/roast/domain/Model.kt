package com.rdr.roast.domain

/**
 * Temperature unit for roast profiles and samples.
 */
enum class TemperatureUnit {
    CELSIUS,
    FAHRENHEIT
}

/**
 * Roasting event types: charge, turning point, first crack, drop.
 */
enum class EventType {
    CHARGE,
    TP,
    CC,
    DE,   // Dry End (same as CC / color change)
    FC,   // First Crack
    DROP
}

/**
 * A roast event at a specific time, with optional bean/environment temperatures.
 */
data class RoastEvent(
    val timeSec: Double,
    val type: EventType,
    val tempBT: Double? = null,
    val tempET: Double? = null
)

/**
 * A single temperature reading (bean temp, environment temp).
 */
data class TemperatureSample(
    val timeSec: Double,
    val bt: Double,
    val et: Double
)

/**
 * Duration of a roast phase with computed duration and percentage.
 */
data class PhaseDuration(
    val name: String,
    val startSec: Double,
    val endSec: Double
) {
    val durationSec: Double get() = endSec - startSec

    /**
     * Returns the phase duration as a percentage of total roast duration (0..100).
     */
    fun percent(totalDurationSec: Double): Double =
        if (totalDurationSec <= 0.0) 0.0 else (durationSec / totalDurationSec) * 100.0
}

/**
 * Roast profile with time-ordered temperature series and events.
 * Uses mutable lists for live recording; samples and events are appended during a roast.
 */
data class RoastProfile(
    val timex: MutableList<Double> = mutableListOf(),
    val temp1: MutableList<Double> = mutableListOf(),
    val temp2: MutableList<Double> = mutableListOf(),
    val events: MutableList<RoastEvent> = mutableListOf(),
    val mode: TemperatureUnit = TemperatureUnit.CELSIUS
) {
    fun addSample(sample: TemperatureSample) {
        timex.add(sample.timeSec)
        temp1.add(sample.bt)
        temp2.add(sample.et)
    }

    fun addEvent(event: RoastEvent) {
        events.add(event)
    }

    /**
     * Returns the first event of the given type, or null if none.
     */
    fun eventByType(type: EventType): RoastEvent? =
        events.firstOrNull { it.type == type }

    /**
     * Returns the index in timex closest to the event time for the given type, or -1 if no such event or empty timex.
     */
    fun eventIndex(type: EventType): Int {
        val event = eventByType(type) ?: return -1
        if (timex.isEmpty()) return -1
        return timex.withIndex()
            .minByOrNull { (_, t) -> kotlin.math.abs(t - event.timeSec) }
            ?.index ?: -1
    }
}
