package com.rdr.roast.ui.chart

import com.rdr.roast.domain.curves.CurveModel
import com.rdr.roast.domain.curves.CurveModelListener
import javafx.application.Platform
import org.jfree.chart.JFreeChart
import org.jfree.chart.annotations.XYShapeAnnotation
import org.jfree.chart.axis.AxisLocation
import org.jfree.chart.axis.NumberAxis
import org.jfree.chart.axis.NumberTickUnit
import org.jfree.chart.axis.TickUnits
import org.jfree.chart.plot.Marker
import org.jfree.chart.plot.ValueMarker
import org.jfree.chart.plot.XYPlot
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer
import org.jfree.chart.ui.Layer
import org.jfree.chart.ui.RectangleAnchor
import org.jfree.chart.ui.RectangleInsets
import org.jfree.chart.ui.TextAnchor
import org.jfree.data.xy.XYSeries
import org.jfree.data.xy.XYSeriesCollection
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.geom.Rectangle2D
import java.text.DecimalFormat
import java.text.FieldPosition
import java.text.NumberFormat
import java.text.ParsePosition

/**
 * JFreeChart-based roast chart, modeled after Cropster's CurveChart.
 *
 * Layout — single XYPlot with two Y-axes (matching Cropster's putSecondaryCurveModel pattern):
 *   - Left  Y axis  (rangeAxis 0): temperature °C, fixed 50–300
 *   - Right Y axis  (rangeAxis 1): RoR °C/min,     fixed -5..30
 *   - X axis: fixed 0–15 min (0..900 000 ms), MM:SS labels
 *
 * Datasets:
 *   0 → BT  (left axis)
 *   1 → ET  (left axis)
 *   2 → RoR BT (right axis)
 *   3 → RoR ET (right axis)
 */
class CurveChartFx : CurveModelListener {

    companion object {
        const val MAX_ROR        = 30.0
        const val MIN_ROR        = -5.0
        const val TEMP_MIN       = 50.0
        const val TEMP_MAX       = 300.0
        /** Default visible time window in milliseconds (15 min). */
        const val TIME_RANGE_MS  = 15 * 60 * 1000.0   // 900 000 ms

        // Cropster-style colours
        val COLOR_BT        = Color(212, 72, 59)        // red-brown
        val COLOR_ET        = Color(52, 152, 219)        // blue
        val COLOR_ROR_BT    = Color(44, 62, 80)          // dark slate
        val COLOR_ROR_ET    = Color(149, 165, 166)       // gray
        val COLOR_MARKER    = Color.BLACK
        val COLOR_EVENT     = Color(230, 126, 34)
        val COLOR_TRANSPARENT = Color(0, 0, 0, 0)

        val STROKE_CURVE    = BasicStroke(2f)
        val STROKE_ROR      = BasicStroke(1.5f)
        val STROKE_MARKER   = BasicStroke(1f)

        private val CHART_FONT = Font(null, Font.PLAIN, 11)
    }

    // ── Series ────────────────────────────────────────────────────────────────
    private val btSeries    = XYSeries("BT",     false, true)
    private val etSeries    = XYSeries("ET",     false, true)
    private val rorBtSeries = XYSeries("RoR BT", false, true)
    private val rorEtSeries = XYSeries("RoR ET", false, true)

    private val seriesMap = mutableMapOf<CurveModel, XYSeries>()

    // ── The single plot (exposed for ChartPanelFx toolbar/reset) ─────────────
    val plot: XYPlot
    val chart: JFreeChart

    // ── Marker label stagger offset ───────────────────────────────────────────
    private var markerOffset = 20.0

    /** Single-marker event types: adding the same type again removes the previous marker. */
    private val markersByType = mutableMapOf<String, ValueMarker>()

    init {
        // ── X axis — fixed 0..TIME_RANGE_MS, MM:SS labels ─────────────────────
        val timeAxis = NumberAxis("Time").apply {
            isAxisLineVisible = false
            isAutoRange       = false
            setRange(0.0, TIME_RANGE_MS)
            standardTickUnits = buildTimeTickUnits()
            tickLabelFont     = CHART_FONT
            labelFont         = CHART_FONT
        }

        // ── Left Y axis — temperature ──────────────────────────────────────────
        val tempAxis = NumberAxis("°C").apply {
            isAxisLineVisible = false
            isAutoRange       = false
            setRange(TEMP_MIN, TEMP_MAX)
            tickLabelFont     = CHART_FONT
            labelFont         = CHART_FONT
        }

        // ── Right Y axis — RoR ────────────────────────────────────────────────
        val rorAxis = NumberAxis("°C/min").apply {
            isAxisLineVisible = false
            isAutoRange       = false
            setRange(MIN_ROR, MAX_ROR)
            standardTickUnits = buildRorTickUnits()
            tickLabelFont     = CHART_FONT
            labelFont         = CHART_FONT
        }

        // ── Renderers ─────────────────────────────────────────────────────────
        val btRenderer = XYLineAndShapeRenderer(true, false).apply {
            setSeriesPaint(0, COLOR_BT)
            setSeriesStroke(0, STROKE_CURVE)
        }
        val etRenderer = XYLineAndShapeRenderer(true, false).apply {
            setSeriesPaint(0, COLOR_ET)
            setSeriesStroke(0, STROKE_CURVE)
        }
        val rorBtRenderer = XYLineAndShapeRenderer(true, false).apply {
            setSeriesPaint(0, COLOR_ROR_BT)
            setSeriesStroke(0, STROKE_ROR)
        }
        val rorEtRenderer = XYLineAndShapeRenderer(true, false).apply {
            setSeriesPaint(0, COLOR_ROR_ET)
            setSeriesStroke(0, STROKE_ROR)
        }

        // ── Single XYPlot with 4 datasets ─────────────────────────────────────
        plot = XYPlot().apply {
            domainAxis     = timeAxis
            setRangeAxis(0, tempAxis)
            setRangeAxisLocation(0, AxisLocation.BOTTOM_OR_LEFT)
            setRangeAxis(1, rorAxis)
            setRangeAxisLocation(1, AxisLocation.BOTTOM_OR_RIGHT)

            // dataset 0 → BT  → left axis
            setDataset(0, XYSeriesCollection(btSeries))
            setRenderer(0, btRenderer)
            mapDatasetToRangeAxis(0, 0)

            // dataset 1 → ET  → left axis
            setDataset(1, XYSeriesCollection(etSeries))
            setRenderer(1, etRenderer)
            mapDatasetToRangeAxis(1, 0)

            // dataset 2 → RoR BT  → right axis
            setDataset(2, XYSeriesCollection(rorBtSeries))
            setRenderer(2, rorBtRenderer)
            mapDatasetToRangeAxis(2, 1)

            // dataset 3 → RoR ET  → right axis
            setDataset(3, XYSeriesCollection(rorEtSeries))
            setRenderer(3, rorEtRenderer)
            mapDatasetToRangeAxis(3, 1)

            isOutlineVisible = false
            setBackgroundPaint(Color.WHITE)
            setDomainGridlinePaint(Color.LIGHT_GRAY)
            setRangeGridlinePaint(Color.LIGHT_GRAY)
            insets = RectangleInsets(15.0, 5.0, 5.0, 5.0)
        }

        chart = JFreeChart(plot).apply {
            setTextAntiAlias(true)
            setAntiAlias(true)
            removeLegend()
            setBackgroundPaint(Color.WHITE)
        }
    }

    // ── CurveModel binding ────────────────────────────────────────────────────

    fun bindDefaultCurves(btModel: CurveModel, etModel: CurveModel,
                          rorBtModel: CurveModel, rorEtModel: CurveModel) {
        seriesMap[btModel]    = btSeries
        seriesMap[etModel]    = etSeries
        seriesMap[rorBtModel] = rorBtSeries
        seriesMap[rorEtModel] = rorEtSeries

        for (model in listOf(btModel, etModel, rorBtModel, rorEtModel)) {
            model.addListener(this)
            model.fireCurveChanged()
        }
    }

    // ── CurveModelListener ────────────────────────────────────────────────────

    override fun notifyCurveChanged(source: CurveModel, timeMs: Long) {
        val series = seriesMap[source] ?: return
        Platform.runLater {
            chart.isNotify = false
            val value = source.getValue(timeMs)
            if (value != null) {
                series.addOrUpdate(timeMs as Number, value as Number)
            } else {
                val idx = series.indexOf(timeMs as Number)
                if (idx >= 0) series.remove(idx)
            }
            chart.isNotify = true
        }
    }

    override fun notifyCurveChanged(source: CurveModel) {
        val series = seriesMap[source] ?: return
        Platform.runLater {
            chart.isNotify = false
            series.clear()
            for ((t, v) in source.getValues()) {
                series.addOrUpdate(t as Number, v as Number)
            }
            chart.isNotify = true
        }
    }

    // ── Event markers (Cropster ValueMarker pattern) ──────────────────────────

    /** Event type keys that allow only one marker each (re-adding replaces the previous). */
    private fun eventTypeKey(label: String): String? = when {
        label.startsWith("Charge @") -> "Charge"
        label.startsWith("TP @")     -> "TP"
        label.startsWith("DE @")     -> "DE"
        label.startsWith("FC @")     -> "FC"
        label.startsWith("Drop @")   -> "Drop"
        else -> null
    }

    /**
     * Add a vertical domain marker with a label.
     * For single-type events (Charge, TP, DE, FC, Drop), the previous marker of that type is removed.
     */
    fun addEventMarker(timeMs: Long, label: String, color: Color = COLOR_EVENT) {
        val key = eventTypeKey(label)
        Platform.runLater {
            key?.let { markersByType[it]?.let { old -> plot.removeDomainMarker(old as Marker) } }
            val marker = ValueMarker(timeMs.toDouble()).apply {
                stroke          = STROKE_MARKER
                paint           = color
                labelPaint      = color
                labelBackgroundColor = COLOR_TRANSPARENT
                this.label      = label
                labelAnchor     = RectangleAnchor.TOP_RIGHT
                labelTextAnchor = TextAnchor.TOP_LEFT
                labelOffset     = RectangleInsets(markerOffset, 0.0, 0.0, 5.0)
                labelFont       = CHART_FONT
            }
            plot.addDomainMarker(marker)
            key?.let { markersByType[it] = marker }
            markerOffset += 14.0
        }
    }

    /** BT value at [timeMs] from the series (nearest point), or null if no data. */
    fun getBtAtTime(timeMs: Long): Double? {
        val n = btSeries.itemCount
        if (n == 0) return null
        val idx = btSeries.indexOf(timeMs.toDouble())
        if (idx >= 0) return btSeries.getY(idx).toDouble()
        var bestIdx = 0
        var bestDiff = kotlin.math.abs(btSeries.getX(0).toDouble() - timeMs)
        for (i in 1 until n) {
            val d = kotlin.math.abs(btSeries.getX(i).toDouble() - timeMs)
            if (d < bestDiff) { bestDiff = d; bestIdx = i }
        }
        return btSeries.getY(bestIdx).toDouble()
    }

    fun setStartMarker(timeMs: Long, label: String = "Charge") =
        addEventMarker(timeMs, label, COLOR_MARKER)

    fun setEndMarker(timeMs: Long, label: String = "Drop") =
        addEventMarker(timeMs, label, COLOR_MARKER)

    // ── Phase area annotations ────────────────────────────────────────────────

    /**
     * Annotate a roast phase as a semi-transparent filled rectangle.
     * [startMs]/[endMs] in milliseconds.
     */
    fun annotatePhase(startMs: Long, endMs: Long,
                      @Suppress("UNUSED_PARAMETER") text: String, color: Color) {
        Platform.runLater {
            val rect = Rectangle2D.Double(
                startMs.toDouble(), TEMP_MIN,
                (endMs - startMs).toDouble(), TEMP_MAX - TEMP_MIN
            )
            val annotation = XYShapeAnnotation(
                rect, BasicStroke(0f), color.withAlpha(0), color.withAlpha(55)
            )
            plot.getRenderer(0).addAnnotation(annotation, Layer.BACKGROUND)
        }
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    fun clearAll() {
        Platform.runLater {
            chart.isNotify = false
            btSeries.clear()
            etSeries.clear()
            rorBtSeries.clear()
            rorEtSeries.clear()
            plot.clearDomainMarkers()
            plot.clearAnnotations()
            markersByType.clear()
            markerOffset = 20.0
            chart.isNotify = true
        }
    }

    /** Reset X axis to the default 0..15 min window (called by toolbar Reset button). */
    fun resetAxes() {
        plot.domainAxis.setRange(0.0, TIME_RANGE_MS)
        plot.getRangeAxis(0).setRange(TEMP_MIN, TEMP_MAX)
        plot.getRangeAxis(1).setRange(MIN_ROR, MAX_ROR)
    }

    // ── Tick-unit builders ────────────────────────────────────────────────────

    private fun buildTimeTickUnits(): TickUnits {
        val fmt = MmSsFormat()
        return TickUnits().also { tu ->
            listOf(10_000, 30_000, 60_000, 120_000, 180_000, 300_000, 600_000)
                .forEach { ms -> tu.add(NumberTickUnit(ms.toDouble(), fmt)) }
        }
    }

    private fun buildRorTickUnits(): TickUnits {
        val fmt = DecimalFormat("0")
        return TickUnits().also { tu ->
            listOf(1, 2, 5, 10).forEach { v ->
                tu.add(NumberTickUnit(v.toDouble(), fmt))
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun Color.withAlpha(alpha: Int) = Color(red, green, blue, alpha)

/** Formats millisecond values as MM:SS for the time axis. */
private class MmSsFormat : NumberFormat() {
    override fun format(number: Double, toAppendTo: StringBuffer, pos: FieldPosition): StringBuffer {
        val totalSec = (number / 1000).toLong().coerceAtLeast(0)
        val m = totalSec / 60
        val s = totalSec % 60
        return toAppendTo.append("%02d:%02d".format(m, s))
    }

    override fun format(number: Long, toAppendTo: StringBuffer, pos: FieldPosition): StringBuffer =
        format(number.toDouble(), toAppendTo, pos)

    override fun parse(source: String, parsePosition: ParsePosition): Number? = null
}
