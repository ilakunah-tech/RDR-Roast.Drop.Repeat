package com.rdr.roast.domain

import kotlin.jvm.Synchronized
import java.util.Collections

/**
 * Temperature unit for roast profiles and samples.
 */
enum class TemperatureUnit {
    CELSIUS,
    FAHRENHEIT
}

/**
 * Roasting event types: charge, turning point, first crack, drop.
 * CC covers both "Color Change" and "Dry End" (DE) concepts.
 */
enum class EventType {
    CHARGE,
    TP,
    CC,
    FC,   // First Crack
    DROP
}

/**
 * Control event types from Artisan .alog (specialevents / etypes).
 * 0=gas, 1=air, 2=drum, 3=damper.
 */
enum class ControlEventType {
    GAS,
    AIR,
    DRUM,
    DAMPER
}

/**
 * A control event at a specific time (gas/air/drum/damper setting).
 * [displayString] from .alog specialeventsStrings (e.g. "20mbar", "Pilot") shown on chart when set.
 */
data class ControlEvent(
    val timeSec: Double,
    val type: ControlEventType,
    val value: Double,
    val displayString: String? = null
)

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
 * Free-text chart comment, optionally enriched with BT / gas / airflow values.
 * Used for roast comments and Between Batch Protocol (BBP) comments.
 */
data class ProtocolComment(
    val timeSec: Double,
    val text: String,
    val tempBT: Double? = null,
    val gas: Double? = null,
    val airflow: Double? = null
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
class RoastProfile(
    val timex: MutableList<Double> = mutableListOf(),
    val temp1: MutableList<Double> = mutableListOf(),
    val temp2: MutableList<Double> = mutableListOf(),
    events: MutableList<RoastEvent> = mutableListOf(),
    comments: MutableList<ProtocolComment> = mutableListOf(),
    /** Gas/air/drum/damper events from .alog specialevents/etypes/specialeventsvalue. */
    controlEvents: List<ControlEvent> = emptyList(),
    val mode: TemperatureUnit = TemperatureUnit.CELSIUS,
    /** BBP recorded before this roast (between previous Stop and this Start). Cropster-style. */
    val betweenBatchLog: BetweenBatchLog? = null
) {
    val events: MutableList<RoastEvent> = Collections.synchronizedList(events.toMutableList())
    val comments: MutableList<ProtocolComment> = Collections.synchronizedList(comments.toMutableList())
    val controlEvents: MutableList<ControlEvent> = controlEvents.toMutableList()

    @Synchronized
    fun addControlEvent(timeSec: Double, type: ControlEventType, value: Double, displayString: String? = null) {
        controlEvents.add(ControlEvent(timeSec, type, value, displayString))
    }

    @Synchronized
    fun addSample(sample: TemperatureSample) {
        timex.add(sample.timeSec)
        temp1.add(sample.bt)
        temp2.add(sample.et)
    }

    @Synchronized
    fun addEvent(event: RoastEvent) {
        events.add(event)
    }

    @Synchronized
    fun addComment(comment: ProtocolComment) {
        comments.add(comment)
    }

    /**
     * Returns the first event of the given type, or null if none.
     */
    fun eventByType(type: EventType): RoastEvent? =
        synchronized(events) { events.firstOrNull { it.type == type } }

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

    /**
     * Returns a deep copy of this profile with copied lists and events.
     */
    @Synchronized
    fun deepCopy(): RoastProfile {
        val eventsCopy = synchronized(events) { events.toMutableList() }
        val commentsCopy = synchronized(comments) { comments.toMutableList() }
        val controlEventsCopy = controlEvents.toMutableList()
        return RoastProfile(
            timex = timex.toMutableList(),
            temp1 = temp1.toMutableList(),
            temp2 = temp2.toMutableList(),
            events = eventsCopy,
            comments = commentsCopy,
            controlEvents = controlEventsCopy,
            mode = mode,
            betweenBatchLog = betweenBatchLog
        )
    }
}
