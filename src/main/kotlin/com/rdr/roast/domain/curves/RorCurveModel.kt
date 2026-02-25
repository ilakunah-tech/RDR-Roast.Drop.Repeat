package com.rdr.roast.domain.curves

import org.apache.commons.math3.fitting.PolynomialCurveFitter
import org.apache.commons.math3.fitting.WeightedObservedPoints
import java.util.Collections
import java.util.SortedMap
import java.util.TreeMap

/**
 * Decorator that computes Rate of Rise (°C/min) over a source CurveModel using a
 * polynomial (degree-1 linear) fit over a sliding window.
 *
 * [windowMs]  — look-back window in milliseconds (default 30 s).
 *
 * The output timestamps match those of the source. At each point, a linear regression
 * is fitted over the previous [windowMs] ms of source data; the slope (°C/ms) is
 * converted to °C/min.
 *
 * Modeled after Cropster's RorCurveModel / RorPolyFitCalculator.
 */
class RorCurveModel(
    private val source: CurveModel,
    val windowMs: Long = 30_000L
) : AbstractCurveModel(), CurveModelListener {

    override val name: String get() = source.name + "RoR"

    private var values = TreeMap<Long, Double>()

    private val fitter = PolynomialCurveFitter.create(1)

    init {
        source.addListener(this)
        notifyCurveChanged(source)
    }

    override fun getValue(timeMs: Long): Double? = synchronized(this) { values[timeMs] }

    override fun getValues(): SortedMap<Long, Double> =
        synchronized(this) { Collections.unmodifiableSortedMap(TreeMap(values)) }

    override fun notifyCurveChanged(source: CurveModel, timeMs: Long) {
        val sourceValues = source.getValues()
        val newEntries = mutableMapOf<Long, Double>()
        for (t in sourceValues.subMap(timeMs, timeMs + windowMs + 1000L).keys) {
            val ror = calcRor(sourceValues, t)
            if (ror != null) newEntries[t] = ror
        }
        val rorAtTime = calcRor(sourceValues, timeMs)
        synchronized(this) {
            if (rorAtTime != null) values[timeMs] = rorAtTime else values.remove(timeMs)
            newEntries.forEach { (t, v) -> values[t] = v }
        }
        fireCurveChanged(timeMs)
    }

    override fun notifyCurveChanged(source: CurveModel) {
        val sourceValues = source.getValues()
        val newValues = TreeMap<Long, Double>()
        for (t in sourceValues.keys) {
            val ror = calcRor(sourceValues, t)
            if (ror != null) newValues[t] = ror
        }
        synchronized(this) { values = newValues }
        fireCurveChanged()
    }

    /**
     * Fits a degree-1 polynomial over source data in [timeMs - windowMs .. timeMs]
     * and returns the slope converted to °C/min, or null if insufficient data.
     */
    private fun calcRor(sourceValues: SortedMap<Long, Double>, timeMs: Long): Double? {
        val windowStart = timeMs - windowMs
        val window = sourceValues.subMap(windowStart, timeMs + 1L)
        if (window.size < 2) return null

        val obs = WeightedObservedPoints()
        val t0 = window.firstKey().toDouble()
        for ((t, v) in window) {
            obs.add((t - t0).toDouble(), v)
        }
        return try {
            val coeffs = fitter.fit(obs.toList())
            // coeffs[0] = intercept, coeffs[1] = slope in units/ms → convert to /min
            coeffs[1] * 60_000.0
        } catch (_: Exception) {
            null
        }
    }
}
