package com.rdr.roast.ui.chart

import com.rdr.roast.app.ChartConfig
import com.rdr.roast.app.SettingsManager
import com.rdr.roast.app.ThemeSettings
import com.rdr.roast.domain.ControlEvent
import com.rdr.roast.domain.ControlEventType
import javafx.application.Platform
import org.jfree.chart.JFreeChart
import org.jfree.chart.axis.NumberAxis
import org.jfree.chart.axis.NumberTickUnit
import org.jfree.chart.axis.TickUnits
import org.jfree.chart.plot.XYPlot
import org.jfree.chart.renderer.xy.XYStepRenderer
import org.jfree.data.xy.XYSeries
import org.jfree.data.xy.XYSeriesCollection
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.text.DecimalFormat
import java.text.FieldPosition
import java.text.NumberFormat
import java.text.ParsePosition

/**
 * Small chart under the main roast chart showing Gas, Air, and Drum as step curves (Cropster RI5-style).
 * X axis = time from Charge (ms). Y axis = auto range from data (0..max value); when empty, fallback 0–100.
 * Data from [ControlEvent] list.
 */
class ControlChartFx {

    private val gasSeries = XYSeries("Gas", false, true)
    private val airSeries = XYSeries("Air", false, true)
    private val drumSeries = XYSeries("Drum", false, true)

    private val collection = XYSeriesCollection().apply {
        addSeries(gasSeries)
        addSeries(airSeries)
        addSeries(drumSeries)
    }

    private val timeRangeMs: Double
        get() = (SettingsManager.load().chartConfig.timeRangeMin * 60 * 1000).toDouble()

    val plot: XYPlot
    val chart: JFreeChart

    companion object {
        private val CHART_FONT = Font(null, Font.PLAIN, 10)
        private val COLOR_GAS = Color(200, 80, 60)
        private val COLOR_AIR = Color(60, 120, 180)
        private val COLOR_DRUM = Color(100, 140, 80)
    }

    private fun decodeColor(hex: String?, fallback: Color): Color {
        val value = hex?.trim().orEmpty()
        return try {
            Color.decode(if (value.startsWith("#")) value else "#$value")
        } catch (_: Exception) {
            fallback
        }
    }

    private fun chartFont(config: ChartConfig): Font {
        val family = config.fontFamily.trim()
        val size = config.axisFontSize.toInt().coerceAtLeast(8)
        return if (family.isBlank()) Font(null, Font.PLAIN, size) else Font(family, Font.PLAIN, size)
    }

    init {
        val config = SettingsManager.load().chartConfig
        val timeAxis = NumberAxis("Time").apply {
            isAxisLineVisible = false
            isAutoRange = false
            setRange(0.0, timeRangeMs)
            standardTickUnits = buildTimeTickUnits()
            tickLabelFont = chartFont(config)
            labelFont = chartFont(config)
            tickLabelPaint = decodeColor(config.axisLabelColor, Color.DARK_GRAY)
            labelPaint = decodeColor(config.axisLabelColor, Color.DARK_GRAY)
        }
        val valueFormat = DecimalFormat("0.#")
        val valueAxis = NumberAxis("").apply {
            isAxisLineVisible = false
            isAutoRange = true
            autoRangeIncludesZero = true
            autoRangeMinimumSize = 1.0
            standardTickUnits = TickUnits().apply {
                listOf(0.1, 0.5, 1.0, 5.0, 10.0, 25.0, 50.0, 100.0).forEach {
                    add(NumberTickUnit(it, valueFormat))
                }
            }
            tickLabelFont = chartFont(config)
            labelFont = chartFont(config)
            tickLabelPaint = decodeColor(config.axisLabelColor, Color.DARK_GRAY)
            labelPaint = decodeColor(config.axisLabelColor, Color.DARK_GRAY)
        }

        val stepRenderer = XYStepRenderer().apply {
            setSeriesPaint(0, COLOR_GAS)
            setSeriesPaint(1, COLOR_AIR)
            setSeriesPaint(2, COLOR_DRUM)
            setSeriesStroke(0, BasicStroke(1.5f))
            setSeriesStroke(1, BasicStroke(1.5f))
            setSeriesStroke(2, BasicStroke(1.5f))
            setSeriesShapesVisible(0, false)
            setSeriesShapesVisible(1, false)
            setSeriesShapesVisible(2, false)
        }

        plot = XYPlot(collection, timeAxis, valueAxis, stepRenderer).apply {
            backgroundPaint = decodeColor(config.backgroundColor, Color.WHITE)
            domainGridlinePaint = decodeColor(config.gridColor, Color(220, 220, 220))
            rangeGridlinePaint = decodeColor(config.gridColor, Color(220, 220, 220))
            isDomainGridlinesVisible = true
            isRangeGridlinesVisible = true
        }

        chart = JFreeChart(plot).apply {
            isBorderVisible = false
            backgroundPaint = decodeColor(config.backgroundColor, Color.WHITE)
            title = null
        }
    }

    private fun buildTimeTickUnits(): TickUnits {
        val fmt = object : NumberFormat() {
            override fun format(number: Double, toAppendTo: StringBuffer, pos: FieldPosition): StringBuffer {
                val totalSec = (number / 1000).toLong().coerceAtLeast(0)
                val m = totalSec / 60
                val s = totalSec % 60
                toAppendTo.append("%d:%02d".format(m, s))
                return toAppendTo
            }
            override fun format(number: Long, toAppendTo: StringBuffer, pos: FieldPosition): StringBuffer =
                format(number.toDouble(), toAppendTo, pos)
            override fun parse(source: String, pos: ParsePosition): Number? = null
        }
        return TickUnits().apply {
            listOf(60_000.0, 120_000.0, 300_000.0, 600_000.0).forEach { add(NumberTickUnit(it, fmt)) }
        }
    }

    /**
     * Update step series from control events. Time is converted to ms from Charge.
     * Each series (Gas, Air, Drum) is built as step: (0,0) then (timeMs, value) at each event of that type.
     */
    fun updateControlEvents(events: List<ControlEvent>, chargeOffsetSec: Double) {
        Platform.runLater {
            chart.isNotify = false
            gasSeries.clear()
            airSeries.clear()
            drumSeries.clear()

            if (events.isEmpty()) {
                (plot.rangeAxis as? NumberAxis)?.setRange(0.0, 100.0)
                chart.isNotify = true
                chart.fireChartChanged()
                return@runLater
            }

            for (eventType in listOf(ControlEventType.GAS, ControlEventType.AIR, ControlEventType.DRUM)) {
                val series = when (eventType) {
                    ControlEventType.GAS -> gasSeries
                    ControlEventType.AIR -> airSeries
                    ControlEventType.DRUM -> drumSeries
                    ControlEventType.DAMPER, ControlEventType.BURNER -> continue
                }
                val sorted = events.filter { it.type == eventType }.sortedBy { it.timeSec }
                if (sorted.isEmpty()) continue
                var prevTimeMs = 0.0
                series.add(prevTimeMs, 0.0)
                for (e in sorted) {
                    val timeMs = (e.timeSec - chargeOffsetSec) * 1000
                    if (timeMs < 0) continue
                    series.add(timeMs, e.value)
                    prevTimeMs = timeMs
                }
            }

            chart.isNotify = true
            chart.fireChartChanged()
        }
    }

    /** Sync time range with main chart config (e.g. after settings change). */
    fun setTimeRangeMinutes(minutes: Int) {
        val ms = minutes * 60 * 1000.0
        plot.domainAxis.setRange(0.0, ms)
    }

    /** Apply theme colors (sliders + chart background/grid). Called when theme or settings change. */
    fun applyTheme(theme: ThemeSettings, chartConfig: ChartConfig) {
        Platform.runLater {
            val decode = { hex: String -> Color.decode(hex.takeIf { it.startsWith("#") } ?: "#$hex") }
            (plot.renderer as? XYStepRenderer)?.apply {
                setSeriesPaint(0, decode(theme.sliders.gasColor))
                setSeriesPaint(1, decode(theme.sliders.airColor))
                setSeriesPaint(2, decode(theme.sliders.drumColor))
            }
            val bg = decodeColor(chartConfig.backgroundColor, decode(theme.charts.backgroundColor))
            val grid = decodeColor(chartConfig.gridColor, decode(theme.charts.gridColor))
            val axis = decodeColor(chartConfig.axisLabelColor, decode(theme.charts.axisLabelColor))
            val font = chartFont(chartConfig)
            plot.domainAxis.tickLabelFont = font
            plot.domainAxis.labelFont = font
            plot.rangeAxis.tickLabelFont = font
            plot.rangeAxis.labelFont = font
            plot.domainAxis.tickLabelPaint = axis
            plot.domainAxis.labelPaint = axis
            plot.rangeAxis.tickLabelPaint = axis
            plot.rangeAxis.labelPaint = axis
            plot.backgroundPaint = bg
            plot.domainGridlinePaint = grid
            plot.rangeGridlinePaint = grid
            chart.backgroundPaint = bg
        }
    }
}
