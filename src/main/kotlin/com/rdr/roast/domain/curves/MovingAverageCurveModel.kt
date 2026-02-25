package com.rdr.roast.domain.curves

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import java.util.Collections
import java.util.SortedMap
import java.util.TreeMap

/**
 * Decorator that applies a sliding-window moving average over a source CurveModel.
 *
 * [windowSize] is the number of samples (not milliseconds) to include in the average.
 * The smoothed output uses the same timestamps as the source.
 *
 * Modeled after Cropster's MovingAverageCurveModel.
 */
class MovingAverageCurveModel(
    private val source: CurveModel,
    private val windowSize: Int
) : AbstractCurveModel(), CurveModelListener {

    override val name: String get() = source.name

    private var values = TreeMap<Long, Double>()

    init {
        source.addListener(this)
        notifyCurveChanged(source)
    }

    override fun getValue(timeMs: Long): Double? = synchronized(this) { values[timeMs] }

    override fun getValues(): SortedMap<Long, Double> =
        synchronized(this) { Collections.unmodifiableSortedMap(TreeMap(values)) }

    override fun notifyCurveChanged(source: CurveModel, timeMs: Long) {
        val windowStart = timeMs - windowSize.toLong() * 1000L
        val subMap = source.getValues().subMap(windowStart, timeMs + 1000L)
        synchronized(this) {
            if (subMap.isNotEmpty()) {
                val stats = DescriptiveStatistics(windowSize)
                for (v in subMap.values) stats.addValue(v)
                values[timeMs] = stats.mean
            } else {
                values.remove(timeMs)
            }
        }
        fireCurveChanged(timeMs)
    }

    override fun notifyCurveChanged(source: CurveModel) {
        val sourceValues = source.getValues()
        val newValues = TreeMap<Long, Double>()
        if (sourceValues.isNotEmpty()) {
            val stats = DescriptiveStatistics(windowSize)
            for ((t, v) in sourceValues) {
                stats.addValue(v)
                newValues[t] = stats.mean
            }
        }
        synchronized(this) { values = newValues }
        fireCurveChanged()
    }
}
