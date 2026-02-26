package com.rdr.roast.ui.chart

import com.rdr.roast.app.ChartConfig
import com.rdr.roast.app.ChartColors
import com.rdr.roast.domain.EventType
import com.rdr.roast.domain.RoastProfile
import com.rdr.roast.domain.curves.CurveModel
import com.rdr.roast.domain.curves.CurveModelListener
import javafx.application.Platform
import org.jfree.chart.JFreeChart
import org.jfree.chart.annotations.AbstractXYAnnotation
import org.jfree.chart.annotations.XYAnnotation
import org.jfree.chart.annotations.XYShapeAnnotation
import org.jfree.chart.axis.AxisLocation
import org.jfree.chart.axis.ValueAxis
import org.jfree.chart.plot.Plot
import org.jfree.chart.plot.PlotOrientation
import org.jfree.chart.plot.PlotRenderingInfo
import org.jfree.chart.ui.RectangleEdge
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
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Composite
import java.awt.Font
import java.awt.Graphics2D
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
        // Phase strip (Cropster-style)
        val COLOR_DRYING      = Color(173, 209, 143)
        val COLOR_MAILLARD    = Color(245, 224, 148)
        val COLOR_DEVELOPMENT = Color(206, 173, 120)

        val STROKE_CURVE    = BasicStroke(2f)
        val STROKE_ROR      = BasicStroke(1.5f)
        val STROKE_MARKER   = BasicStroke(1f)
        /** Dashed stroke for reference/background curve. */
        val STROKE_REF      = BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, floatArrayOf(8f, 4f), 0f)
        /** Muted reference colors (Artisan/Cropster-style: background less bright than foreground). Alpha ~0.5. */
        val COLOR_REF_BT    = Color(200, 110, 100, 140)
        val COLOR_REF_ET    = Color(120, 150, 210, 140)
        val COLOR_REF_ROR_BT = Color(100, 120, 140, 120)
        val COLOR_REF_ROR_ET = Color(160, 170, 175, 120)
        /** Reference event markers: subtle gray. */
        val COLOR_REF_MARKER = Color(140, 140, 140, 180)

        private val CHART_FONT = Font(null, Font.PLAIN, 11)
        private const val PHASE_STRIP_HEIGHT = 10
        /** RoR window in seconds (Artisan-style). */
        private const val ROR_DELTA_SEC = 30
    }

    // ── Series ────────────────────────────────────────────────────────────────
    private val btSeries    = XYSeries("BT",     false, true)
    private val etSeries    = XYSeries("ET",     false, true)
    private val rorBtSeries = XYSeries("RoR BT", false, true)
    private val rorEtSeries = XYSeries("RoR ET", false, true)
    private val refBtSeries = XYSeries("Ref BT", false, true)
    private val refEtSeries = XYSeries("Ref ET", false, true)
    private val refRorBtSeries = XYSeries("Ref RoR BT", false, true)
    private val refRorEtSeries = XYSeries("Ref RoR ET", false, true)

    private val seriesMap = mutableMapOf<CurveModel, XYSeries>()
    /** Reference profile markers (Charge, TP, DE, FC, Drop) — removed when reference is cleared. */
    private val refMarkers = mutableListOf<Marker>()

    // ── The single plot (exposed for ChartPanelFx toolbar/reset) ─────────────
    val plot: XYPlot
    val chart: JFreeChart

    // ── Marker label stagger offset ───────────────────────────────────────────
    private var markerOffset = 20.0
    /** Stagger offset for reference event markers so labels don't overlap. */
    private var refMarkerOffset = 5.0

    /** Last applied chart config; used by resetAxes(). */
    private var lastChartConfig: ChartConfig? = null

    /** Single-marker event types: adding the same type again removes the previous marker. */
    private val markersByType = mutableMapOf<String, ValueMarker>()

    /** Time of Charge in raw ms (from recording start). Display X = raw - chargeOffsetMs; 0 until Charge is set. */
    private var chargeOffsetMs: Long = 0L

    /** Convert display time (chart X) to raw time for profile/callbacks. */
    fun toRawTime(displayMs: Long): Long = displayMs + chargeOffsetMs

    /** Phase strip annotations (Drying, Maillard, Development) at bottom of chart. */
    private val phaseStripAnnotations = mutableListOf<XYAnnotation>()

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
        val refBtRenderer = XYLineAndShapeRenderer(true, false).apply {
            setSeriesPaint(0, COLOR_REF_BT)
            setSeriesStroke(0, STROKE_REF)
        }
        val refEtRenderer = XYLineAndShapeRenderer(true, false).apply {
            setSeriesPaint(0, COLOR_REF_ET)
            setSeriesStroke(0, STROKE_REF)
        }
        val refRorBtRenderer = XYLineAndShapeRenderer(true, false).apply {
            setSeriesPaint(0, COLOR_REF_ROR_BT)
            setSeriesStroke(0, STROKE_REF)
        }
        val refRorEtRenderer = XYLineAndShapeRenderer(true, false).apply {
            setSeriesPaint(0, COLOR_REF_ROR_ET)
            setSeriesStroke(0, STROKE_REF)
        }

        // ── Single XYPlot with 8 datasets (4 live + 2 ref temp + 2 ref RoR) ──
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
            // dataset 4 → Ref BT  → left axis
            setDataset(4, XYSeriesCollection(refBtSeries))
            setRenderer(4, refBtRenderer)
            mapDatasetToRangeAxis(4, 0)
            // dataset 5 → Ref ET  → left axis
            setDataset(5, XYSeriesCollection(refEtSeries))
            setRenderer(5, refEtRenderer)
            mapDatasetToRangeAxis(5, 0)
            // dataset 6 → Ref RoR BT  → right axis
            setDataset(6, XYSeriesCollection(refRorBtSeries))
            setRenderer(6, refRorBtRenderer)
            mapDatasetToRangeAxis(6, 1)
            // dataset 7 → Ref RoR ET  → right axis
            setDataset(7, XYSeriesCollection(refRorEtSeries))
            setRenderer(7, refRorEtRenderer)
            mapDatasetToRangeAxis(7, 1)

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
            val adjustedMs = timeMs - chargeOffsetMs
            val value = source.getValue(timeMs)
            if (value != null) {
                series.addOrUpdate(adjustedMs as Number, value as Number)
            } else {
                val idx = series.indexOf(adjustedMs as Number)
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
                val adjustedMs = (t as Number).toLong() - chargeOffsetMs
                series.addOrUpdate(adjustedMs as Number, v as Number)
            }
            chart.isNotify = true
        }
    }

    /**
     * Rebase live series and markers so Charge = 00:00 (Cropster-style).
     * Call when user presses C: existing points get new X = rawX - offsetMs; markers are shifted; future points use this offset.
     */
    fun rebaseAllSeries(offsetMs: Long) {
        Platform.runLater {
            chart.isNotify = false
            chargeOffsetMs = offsetMs
            for (series in listOf(btSeries, etSeries, rorBtSeries, rorEtSeries)) {
                val points = (0 until series.itemCount).map { i ->
                    series.getX(i).toDouble() - offsetMs to series.getY(i).toDouble()
                }
                series.clear()
                points.forEach { (x, y) -> series.add(x, y) }
            }
            for ((key, marker) in markersByType.toList()) {
                plot.removeDomainMarker(marker as Marker)
                val newVal = marker.value - offsetMs
                val m = ValueMarker(newVal).apply {
                    stroke = marker.stroke
                    paint = marker.paint
                    labelPaint = marker.labelPaint
                    labelBackgroundColor = marker.labelBackgroundColor
                    label = marker.label
                    labelAnchor = marker.labelAnchor
                    labelTextAnchor = marker.labelTextAnchor
                    labelOffset = marker.labelOffset
                    labelFont = marker.labelFont
                }
                plot.addDomainMarker(m)
                markersByType[key] = m
            }
            val timeRangeMs = lastChartConfig?.timeRangeMin?.times(60)?.times(1000)?.toDouble() ?: TIME_RANGE_MS
            val preHeatMs = 5 * 60 * 1000.0
            plot.domainAxis.setRange(-preHeatMs, timeRangeMs)
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
        val displayMs = timeMs - chargeOffsetMs
        Platform.runLater {
            key?.let { markersByType[it]?.let { old -> plot.removeDomainMarker(old as Marker) } }
            val marker = ValueMarker(displayMs.toDouble()).apply {
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

    // ── Phase strip (Cropster-style bar at bottom of chart) ─────────────────────

    private inner class PhaseStripAnnotation(
        private val x1Ms: Double,
        private val x2Ms: Double,
        private val color: Color
    ) : AbstractXYAnnotation() {
        override fun draw(
            g2: Graphics2D,
            plot: XYPlot,
            dataArea: Rectangle2D,
            domainAxis: ValueAxis,
            rangeAxis: ValueAxis,
            rendererIndex: Int,
            info: PlotRenderingInfo?
        ) {
            val edge = Plot.resolveDomainAxisLocation(plot.domainAxisLocation, PlotOrientation.VERTICAL)
            val j2dx1 = domainAxis.valueToJava2D(x1Ms, dataArea, edge)
            val j2dx2 = domainAxis.valueToJava2D(x2Ms, dataArea, edge)
            val y = dataArea.maxY - PHASE_STRIP_HEIGHT
            val w = j2dx2 - j2dx1
            val savedComposite = g2.composite
            g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f)
            g2.paint = color
            g2.fill(java.awt.geom.Rectangle2D.Double(j2dx1, y, w, PHASE_STRIP_HEIGHT.toDouble()))
            g2.composite = savedComposite
        }
    }

    /**
     * Update the phase strip (Drying / Maillard / Development) from profile events.
     * Boundaries: Drying 0..DE (or FC if no DE), Maillard DE..FC (or end), Development FC..end.
     * [endMs] is roast end time in ms (e.g. last sample or drop).
     */
    fun updatePhaseStrip(endMs: Long, deMs: Long?, fcMs: Long?) {
        Platform.runLater {
            phaseStripAnnotations.forEach { plot.getRenderer(0).removeAnnotation(it) }
            phaseStripAnnotations.clear()
            if (endMs <= 0) return@runLater
            val end = (endMs - chargeOffsetMs).toDouble().coerceAtLeast(0.0)
            val de = deMs?.let { (it - chargeOffsetMs).toDouble() }
            val fc = fcMs?.let { (it - chargeOffsetMs).toDouble() }
            // Drying: 0 .. DE or FC or end (display time)
            val dryingEnd = de?.coerceAtLeast(0.0) ?: fc?.coerceAtLeast(0.0) ?: end
            if (dryingEnd > 0) {
                val a = PhaseStripAnnotation(0.0, dryingEnd, COLOR_DRYING)
                plot.getRenderer(0).addAnnotation(a, Layer.BACKGROUND)
                phaseStripAnnotations.add(a)
            }
            // Maillard: DE (or 0) .. FC or end
            val maillardStart = de?.coerceAtLeast(0.0) ?: 0.0
            val maillardEnd = fc?.coerceAtLeast(0.0) ?: end
            if (maillardEnd > maillardStart) {
                val a = PhaseStripAnnotation(maillardStart, maillardEnd, COLOR_MAILLARD)
                plot.getRenderer(0).addAnnotation(a, Layer.BACKGROUND)
                phaseStripAnnotations.add(a)
            }
            // Development: FC .. end
            if (fc != null && fc >= 0 && end > fc) {
                val a = PhaseStripAnnotation(fc, end, COLOR_DEVELOPMENT)
                plot.getRenderer(0).addAnnotation(a, Layer.BACKGROUND)
                phaseStripAnnotations.add(a)
            }
        }
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    /**
     * Set or clear the reference/background profile curve (Artisan/Cropster-style).
     * Fills ref BT/ET, ref RoR, and reference event markers (Charge, TP, DE, FC, Drop).
     */
    fun setReferenceProfile(profile: RoastProfile?) {
        Platform.runLater {
            chart.isNotify = false
            refMarkers.forEach { plot.removeDomainMarker(it) }
            refMarkers.clear()
            refBtSeries.clear()
            refEtSeries.clear()
            refRorBtSeries.clear()
            refRorEtSeries.clear()
            refMarkerOffset = 5.0
            if (profile != null && profile.timex.isNotEmpty()) {
                val chargeEvent = profile.eventByType(EventType.CHARGE)
                val chargeOffsetSec = chargeEvent?.timeSec ?: 0.0
                val n = minOf(profile.timex.size, profile.temp1.size, profile.temp2.size)
                val timex = profile.timex.take(n)
                val bt = profile.temp1.take(n)
                val et = profile.temp2.take(n)
                // Filter to adjustedTimeSec >= 0 (points from Charge onward)
                val indices = timex.indices.filter { (timex[it] - chargeOffsetSec) >= 0 }
                val adjustedTimex = indices.map { timex[it] - chargeOffsetSec }
                val btFiltered = indices.map { bt[it] }
                val etFiltered = indices.map { et[it] }
                for (i in adjustedTimex.indices) {
                    val timeMs = (adjustedTimex[i] * 1000).toLong()
                    refBtSeries.add(timeMs.toDouble(), btFiltered[i])
                    refEtSeries.add(timeMs.toDouble(), etFiltered[i])
                }
                if (adjustedTimex.size >= 2) {
                    computeAndFillRorSeries(adjustedTimex, btFiltered, refRorBtSeries, ROR_DELTA_SEC)
                    computeAndFillRorSeries(adjustedTimex, etFiltered, refRorEtSeries, ROR_DELTA_SEC)
                }
                profile.events.forEach { event ->
                    val adjustedSec = event.timeSec - chargeOffsetSec
                    if (adjustedSec < 0) return@forEach
                    val timeMs = (adjustedSec * 1000).toLong()
                    val label = refEventLabel(event.type, adjustedSec, event.tempBT, event.tempET)
                    addReferenceEventMarker(timeMs, label)
                }
            }
            chart.isNotify = true
            chart.fireChartChanged()
        }
    }

    private fun refEventLabel(type: com.rdr.roast.domain.EventType, timeSec: Double, bt: Double?, et: Double?): String {
        val t = formatSecMmSs(timeSec)
        return when (type) {
            com.rdr.roast.domain.EventType.CHARGE -> "Ref: Charge @ $t"
            com.rdr.roast.domain.EventType.TP    -> "Ref: TP @ $t · ${bt?.let { "%.1f".format(it) } ?: "-"} °C"
            com.rdr.roast.domain.EventType.DE, com.rdr.roast.domain.EventType.CC -> "Ref: DE @ $t"
            com.rdr.roast.domain.EventType.FC    -> "Ref: FC @ $t"
            com.rdr.roast.domain.EventType.DROP -> "Ref: Drop @ $t"
            else -> "Ref @ $t"
        }
    }

    private fun addReferenceEventMarker(timeMs: Long, label: String) {
        val marker = ValueMarker(timeMs.toDouble()).apply {
            stroke = STROKE_MARKER
            paint = COLOR_REF_MARKER
            labelPaint = COLOR_REF_MARKER
            labelBackgroundColor = COLOR_TRANSPARENT
            this.label = label
            labelAnchor = RectangleAnchor.TOP_RIGHT
            labelTextAnchor = TextAnchor.TOP_LEFT
            labelOffset = RectangleInsets(refMarkerOffset, 0.0, 0.0, 5.0)
            labelFont = CHART_FONT
        }
        plot.addDomainMarker(marker)
        refMarkers.add(marker)
        refMarkerOffset += 14.0
    }

    /** Centred moving average; windowSize 7 smooths reference RoR. */
    private fun smoothMovingAverage(data: List<Double>, windowSize: Int): List<Double> {
        if (data.isEmpty()) return data
        val half = windowSize / 2
        return data.indices.map { i ->
            val from = maxOf(0, i - half)
            val to = minOf(data.size - 1, i + half)
            data.subList(from, to + 1).average()
        }
    }

    /** RoR °C/min over [deltaSec] window (Artisan-style). Uses smoothed temp for reference. Fills series with (timeMs, ror). */
    private fun computeAndFillRorSeries(timex: List<Double>, temp: List<Double>, series: XYSeries, deltaSec: Int) {
        if (timex.size < 2 || temp.size < 2) return
        val smoothed = smoothMovingAverage(temp, 7)
        for (i in timex.indices) {
            val targetT = timex[i] - deltaSec
            var prevIdx = 0
            for (j in i - 1 downTo 0) {
                if (timex[j] <= targetT) {
                    prevIdx = j
                    break
                }
                prevIdx = j
            }
            val ror = when {
                i == 0 || timex[i] == timex[prevIdx] -> 0.0
                else -> {
                    val dtMin = (timex[i] - timex[prevIdx]) / 60.0
                    if (dtMin <= 0) 0.0 else (smoothed[i] - smoothed[prevIdx]) / dtMin
                }
            }
            series.add(timex[i] * 1000, ror.coerceIn(MIN_ROR.toDouble(), MAX_ROR.toDouble()))
        }
    }

    private fun formatSecMmSs(sec: Double): String {
        val totalSec = sec.toLong().coerceAtLeast(0)
        val m = totalSec / 60
        val s = totalSec % 60
        return "%02d:%02d".format(m, s)
    }

    fun clearAll() {
        Platform.runLater {
            chart.isNotify = false
            chargeOffsetMs = 0L
            btSeries.clear()
            etSeries.clear()
            rorBtSeries.clear()
            rorEtSeries.clear()
            refBtSeries.clear()
            refEtSeries.clear()
            refRorBtSeries.clear()
            refRorEtSeries.clear()
            refMarkers.forEach { plot.removeDomainMarker(it) }
            refMarkers.clear()
            plot.clearDomainMarkers()
            phaseStripAnnotations.forEach { plot.getRenderer(0).removeAnnotation(it) }
            phaseStripAnnotations.clear()
            markersByType.clear()
            markerOffset = 20.0
            refMarkerOffset = 5.0
            chart.isNotify = true
        }
    }

    /** Apply chart colours from settings. Ref series use refAlpha for transparency. */
    fun applyChartColors(colors: ChartColors) {
        Platform.runLater {
            val decode = { hex: String -> Color.decode(hex) }
            val decodeRef = { hex: String ->
                val c = Color.decode(hex)
                Color(c.red, c.green, c.blue, colors.refAlpha.coerceIn(0, 255))
            }
            (plot.getRenderer(0) as? XYLineAndShapeRenderer)?.setSeriesPaint(0, decode(colors.liveBt))
            (plot.getRenderer(1) as? XYLineAndShapeRenderer)?.setSeriesPaint(0, decode(colors.liveEt))
            (plot.getRenderer(2) as? XYLineAndShapeRenderer)?.setSeriesPaint(0, decode(colors.liveRorBt))
            (plot.getRenderer(3) as? XYLineAndShapeRenderer)?.setSeriesPaint(0, decode(colors.liveRorEt))
            (plot.getRenderer(4) as? XYLineAndShapeRenderer)?.setSeriesPaint(0, decodeRef(colors.refBt))
            (plot.getRenderer(5) as? XYLineAndShapeRenderer)?.setSeriesPaint(0, decodeRef(colors.refEt))
            (plot.getRenderer(6) as? XYLineAndShapeRenderer)?.setSeriesPaint(0, decodeRef(colors.refRorBt))
            (plot.getRenderer(7) as? XYLineAndShapeRenderer)?.setSeriesPaint(0, decodeRef(colors.refRorEt))
        }
    }

    /** Apply chart config (axes, grid, line widths). */
    fun applyChartConfig(config: ChartConfig) {
        Platform.runLater {
            lastChartConfig = config
            val timeRangeMs = config.timeRangeMin * 60 * 1000.0
            plot.domainAxis.setRange(0.0, timeRangeMs)
            plot.getRangeAxis(0).setRange(config.tempMin, config.tempMax)
            plot.getRangeAxis(1).setRange(config.rorMin, config.rorMax)
            (plot.getRenderer(0) as? XYLineAndShapeRenderer)?.setSeriesStroke(0, BasicStroke(config.btLineWidth))
            (plot.getRenderer(1) as? XYLineAndShapeRenderer)?.setSeriesStroke(0, BasicStroke(config.etLineWidth))
            (plot.getRenderer(2) as? XYLineAndShapeRenderer)?.setSeriesStroke(0, BasicStroke(config.rorLineWidth))
            (plot.getRenderer(3) as? XYLineAndShapeRenderer)?.setSeriesStroke(0, BasicStroke(config.rorLineWidth))
            val refStroke = BasicStroke(config.refLineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, floatArrayOf(8f, 4f), 0f)
            (plot.getRenderer(4) as? XYLineAndShapeRenderer)?.setSeriesStroke(0, refStroke)
            (plot.getRenderer(5) as? XYLineAndShapeRenderer)?.setSeriesStroke(0, refStroke)
            (plot.getRenderer(6) as? XYLineAndShapeRenderer)?.setSeriesStroke(0, refStroke)
            (plot.getRenderer(7) as? XYLineAndShapeRenderer)?.setSeriesStroke(0, refStroke)
            plot.setBackgroundPaint(Color.decode(config.backgroundColor))
            plot.setDomainGridlinePaint(Color.decode(config.gridColor))
            plot.setRangeGridlinePaint(Color.decode(config.gridColor))
            plot.isDomainGridlinesVisible = config.showGrid
            plot.isRangeGridlinesVisible = config.showGrid
        }
    }

    /** Reset X axis to the default 0..15 min window (or saved ChartConfig). Called by toolbar Reset button. */
    fun resetAxes() {
        val config = lastChartConfig
        if (config != null) {
            val timeRangeMs = config.timeRangeMin * 60 * 1000.0
            plot.domainAxis.setRange(0.0, timeRangeMs)
            plot.getRangeAxis(0).setRange(config.tempMin, config.tempMax)
            plot.getRangeAxis(1).setRange(config.rorMin, config.rorMax)
        } else {
            plot.domainAxis.setRange(0.0, TIME_RANGE_MS)
            plot.getRangeAxis(0).setRange(TEMP_MIN, TEMP_MAX)
            plot.getRangeAxis(1).setRange(MIN_ROR, MAX_ROR)
        }
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
