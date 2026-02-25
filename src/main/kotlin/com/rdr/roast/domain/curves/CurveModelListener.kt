package com.rdr.roast.domain.curves

/**
 * Observer that receives change notifications from a CurveModel.
 * Modeled after Cropster's CurveModelListener.
 *
 * - notifyCurveChanged(source, timeMs) — a single data point was added or updated
 * - notifyCurveChanged(source)         — the entire series was replaced / cleared
 */
interface CurveModelListener {
    fun notifyCurveChanged(source: CurveModel, timeMs: Long)
    fun notifyCurveChanged(source: CurveModel)
}
