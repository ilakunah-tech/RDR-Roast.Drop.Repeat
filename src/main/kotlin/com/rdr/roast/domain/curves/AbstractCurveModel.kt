package com.rdr.roast.domain.curves

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Base implementation of CurveModel providing thread-safe listener management.
 * Modeled after Cropster's AbstractCurveModel.
 */
abstract class AbstractCurveModel : CurveModel {

    private val listeners = CopyOnWriteArrayList<CurveModelListener>()

    override fun addListener(listener: CurveModelListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: CurveModelListener) {
        listeners.remove(listener)
    }

    override fun fireCurveChanged(timeMs: Long) {
        for (l in listeners) l.notifyCurveChanged(this, timeMs)
    }

    override fun fireCurveChanged() {
        for (l in listeners) l.notifyCurveChanged(this)
    }
}
