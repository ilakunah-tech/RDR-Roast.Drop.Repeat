package com.rdr.roast.domain.curves

import java.util.Collections
import java.util.SortedMap
import java.util.TreeMap

/**
 * Raw curve model backed by a TreeMap.
 * Accepts incoming data points via [put] and fires listeners after each update.
 *
 * Modeled after Cropster's StandardCurveModel.
 */
class StandardCurveModel(override val name: String) : AbstractCurveModel() {

    private val data = TreeMap<Long, Double>()

    /** Add or update a value at [timeMs] (milliseconds). Fires an incremental notification. */
    fun put(timeMs: Long, value: Double) {
        synchronized(data) { data[timeMs] = value }
        fireCurveChanged(timeMs)
    }

    /** Replace the entire series at once. Fires a full-series notification. */
    fun putAll(values: Map<Long, Double>) {
        synchronized(data) {
            data.clear()
            data.putAll(values)
        }
        fireCurveChanged()
    }

    fun clear() {
        synchronized(data) { data.clear() }
        fireCurveChanged()
    }

    override fun getValue(timeMs: Long): Double? = synchronized(data) { data[timeMs] }

    override fun getValues(): SortedMap<Long, Double> =
        synchronized(data) { Collections.unmodifiableSortedMap(TreeMap(data)) }
}
