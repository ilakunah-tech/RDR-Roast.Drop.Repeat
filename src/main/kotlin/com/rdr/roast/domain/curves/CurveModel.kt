package com.rdr.roast.domain.curves

import java.util.SortedMap

/**
 * A time-indexed series of Double values.
 *
 * Timestamps are in **milliseconds** (Long) — matching Cropster's convention so that
 * JFreeChart time-axis math stays in the same unit.
 *
 * Modeled after Cropster's CurveModel interface.
 */
interface CurveModel {
    val name: String
    fun getValue(timeMs: Long): Double?
    fun getValues(): SortedMap<Long, Double>
    fun addListener(listener: CurveModelListener)
    fun removeListener(listener: CurveModelListener)
    fun fireCurveChanged()
    fun fireCurveChanged(timeMs: Long)
}
