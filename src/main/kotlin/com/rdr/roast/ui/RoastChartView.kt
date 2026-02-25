package com.rdr.roast.ui

import com.rdr.roast.domain.PhaseDuration
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.layout.Region
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.FontWeight

/**
 * Canvas-based roast chart for real-time temperature and RoR curves.
 * Renders BT, ET, RoR BT, RoR ET, event markers, and phase bars.
 */
class RoastChartView : Canvas() {

    // Data series (timeSec, value)
    val btPoints: MutableList<Pair<Double, Double>> = mutableListOf()
    val etPoints: MutableList<Pair<Double, Double>> = mutableListOf()
    val rorBtPoints: MutableList<Pair<Double, Double>> = mutableListOf()
    val rorEtPoints: MutableList<Pair<Double, Double>> = mutableListOf()
    val eventMarkers: MutableList<Triple<Double, String, String>> = mutableListOf()
    val phases: MutableList<PhaseDuration> = mutableListOf()

    var maxTimeSec: Double = 600.0
        set(value) {
            field = value.coerceAtLeast(60.0)
            safeRedraw()
        }

    // Axis ranges
    private val tempMin = 50.0
    private val tempMax = 300.0
    private val rorMin = -5.0
    private val rorMax = 30.0

    // Margins
    private val leftMargin = 60.0
    private val rightMargin = 60.0
    private val topMargin = 20.0
    private val bottomMargin = 80.0

    // Colors -- light theme matching Cropster RI
    private val backgroundColor = Color.WHITE
    private val btColor = Color.web("#d4483b")       // red-brown, Cropster BT
    private val etColor = Color.web("#3498db")        // blue, Cropster ET
    private val rorBtColor = Color.web("#2c3e50")     // dark, Cropster RoR BT
    private val rorEtColor = Color.web("#95a5a6")     // gray, Cropster RoR ET
    private val gridColor = Color.gray(0.85)
    private val labelColor = Color.gray(0.3)
    private val eventLineColor = Color.web("#e67e22", 0.7)
    private val dryingColor = Color.web("#f39c12", 0.25)
    private val maillardColor = Color.web("#e67e22", 0.25)
    private val developmentColor = Color.web("#27ae60", 0.25)

    init {
        widthProperty().addListener { _, _, _ -> safeRedraw() }
        heightProperty().addListener { _, _, _ -> safeRedraw() }
    }

    private fun safeRedraw() {
        if (width > 10 && height > 10 && width < 4096 && height < 4096) {
            redraw()
        }
    }

    fun addSample(timeSec: Double, bt: Double, et: Double, rorBt: Double, rorEt: Double) {
        btPoints.add(timeSec to bt)
        etPoints.add(timeSec to et)
        rorBtPoints.add(timeSec to rorBt)
        rorEtPoints.add(timeSec to rorEt)
        if (timeSec > maxTimeSec) maxTimeSec = (timeSec + 60.0).coerceAtLeast(maxTimeSec)
        safeRedraw()
    }

    fun addEventMarker(timeSec: Double, label: String, tempStr: String) {
        eventMarkers.add(Triple(timeSec, label, tempStr))
        safeRedraw()
    }

    fun updatePhases(newPhases: List<PhaseDuration>) {
        phases.clear()
        phases.addAll(newPhases)
        safeRedraw()
    }

    fun clear() {
        btPoints.clear()
        etPoints.clear()
        rorBtPoints.clear()
        rorEtPoints.clear()
        eventMarkers.clear()
        phases.clear()
        maxTimeSec = 600.0
        safeRedraw()
    }

    private fun redraw() {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        val gc = graphicsContext2D
        gc.clearRect(0.0, 0.0, w, h)

        // 1. Fill background
        gc.fill = backgroundColor
        gc.fillRect(0.0, 0.0, w, h)

        val chartLeft = leftMargin
        val chartRight = w - rightMargin
        val chartTop = topMargin
        val chartBottom = h - bottomMargin
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop

        if (chartWidth <= 0 || chartHeight <= 0) return

        gc.font = Font.font("System", FontWeight.NORMAL, 11.0)
        gc.fill = labelColor

        // 2. Y axis labels (left - temperature)
        val tempStep = 50.0
        var t = tempMin
        while (t <= tempMax) {
            val y = tempToY(t, chartTop, chartBottom)
            if (y in chartTop..chartBottom) {
                gc.fillText("%.0f".format(t), chartLeft - 35, y + 4)
            }
            t += tempStep
        }
        gc.fillText("°C", chartLeft - 20, chartTop - 5)

        // 3. Y axis labels (right - RoR)
        val rorStep = 5.0
        var r = rorMin
        while (r <= rorMax) {
            val y = rorToY(r, chartTop, chartBottom)
            if (y in chartTop..chartBottom) {
                gc.fillText("%.0f".format(r), chartRight + 5, y + 4)
            }
            r += rorStep
        }
        gc.fillText("RoR °C/min", chartRight - 45, chartTop - 5)

        // 4. X axis labels (time MM:SS every 60s)
        val timeStep = 60.0
        var time = 0.0
        while (time <= maxTimeSec) {
            val x = timeToX(time, chartLeft, chartRight)
            if (x in chartLeft..chartRight) {
                gc.fillText(formatTime(time), x - 18, chartBottom + 18)
            }
            time += timeStep
        }
        gc.fillText("Time", (chartLeft + chartRight) / 2 - 15, h - 5)

        // 5. Horizontal grid lines
        gc.stroke = gridColor
        gc.lineWidth = 1.0
        gc.setLineDashes(4.0, 4.0)
        t = tempMin
        while (t <= tempMax) {
            val y = tempToY(t, chartTop, chartBottom)
            if (y in chartTop..chartBottom) {
                gc.strokeLine(chartLeft, y, chartRight, y)
            }
            t += tempStep
        }
        r = rorMin
        while (r <= rorMax) {
            val y = rorToY(r, chartTop, chartBottom)
            if (y in chartTop..chartBottom) {
                gc.strokeLine(chartLeft, y, chartRight, y)
            }
            r += rorStep
        }
        time = 0.0
        while (time <= maxTimeSec) {
            val x = timeToX(time, chartLeft, chartRight)
            if (x in chartLeft..chartRight) {
                gc.strokeLine(x, chartTop, x, chartBottom)
            }
            time += timeStep
        }
        gc.setLineDashes()

        // 6. Draw curves
        drawCurve(gc, btPoints, chartLeft, chartRight, btColor, 2.0) { tempToY(it, chartTop, chartBottom) }
        drawCurve(gc, etPoints, chartLeft, chartRight, etColor, 2.0) { tempToY(it, chartTop, chartBottom) }
        drawCurve(gc, rorBtPoints, chartLeft, chartRight, rorBtColor, 1.0) { rorToY(it, chartTop, chartBottom) }
        drawCurve(gc, rorEtPoints, chartLeft, chartRight, rorEtColor, 1.0) { rorToY(it, chartTop, chartBottom) }

        // 7. Event markers (vertical dashed lines with labels)
        gc.setLineDashes(4.0, 4.0)
        for ((timeSec, label, tempStr) in eventMarkers) {
            val x = timeToX(timeSec, chartLeft, chartRight)
            if (x in chartLeft..chartRight) {
                gc.stroke = labelColor
                gc.lineWidth = 1.0
                gc.strokeLine(x, chartTop, x, chartBottom)
                val text = "[${formatTime(timeSec)}] $label @ $tempStr"
                gc.fill = labelColor
                gc.font = Font.font("System", FontWeight.NORMAL, 10.0)
                gc.fillText(text, x + 4, chartTop + 12)
            }
        }
        gc.setLineDashes()

        // 8. Phase bars at bottom
        drawPhaseBars(gc, chartLeft, chartRight, chartBottom, h)
    }

    private fun drawCurve(
        gc: GraphicsContext,
        points: List<Pair<Double, Double>>,
        chartLeft: Double,
        chartRight: Double,
        color: Color,
        lineWidth: Double,
        valueToY: (Double) -> Double
    ) {
        if (points.size < 2) return
        gc.stroke = color
        gc.lineWidth = lineWidth
        gc.beginPath()
        val (t0, v0) = points[0]
        val x0 = timeToX(t0, chartLeft, chartRight)
        val y0 = valueToY(v0)
        gc.moveTo(x0, y0)
        for (i in 1 until points.size) {
            val (t, v) = points[i]
            val x = timeToX(t, chartLeft, chartRight)
            val y = valueToY(v)
            gc.lineTo(x, y)
        }
        gc.stroke()
    }

    private fun drawPhaseBars(gc: GraphicsContext, chartLeft: Double, chartRight: Double, chartBottom: Double, totalHeight: Double) {
        if (phases.isEmpty() || maxTimeSec <= 0) return

        val barHeight = 24.0
        val barTop = chartBottom + 8.0
        if (barTop + barHeight > totalHeight - 4) return

        val totalDuration = phases.maxOfOrNull { it.endSec } ?: maxTimeSec
        if (totalDuration <= 0) return

        val chartWidth = chartRight - chartLeft
        for (phase in phases) {
            if (phase.durationSec <= 0) continue
            val startX = chartLeft + (phase.startSec / totalDuration) * chartWidth
            val phaseWidth = (phase.durationSec / totalDuration) * chartWidth
            val color = when (phase.name) {
                "Drying" -> dryingColor
                "Maillard" -> maillardColor
                "Development" -> developmentColor
                else -> Color.gray(0.5, 0.5)
            }
            gc.fill = color
            gc.fillRect(startX, barTop, phaseWidth, barHeight)

            val pct = phase.percent(totalDuration)
            val text = "${phase.name} - ${formatTime(phase.startSec)} / ${formatTime(phase.endSec)}   %.1f%%".format(pct)
            gc.fill = labelColor
            gc.font = Font.font("System", FontWeight.NORMAL, 10.0)
            gc.fillText(text, startX + 4, barTop + 16)
        }
    }

    private fun timeToX(timeSec: Double, chartLeft: Double, chartRight: Double): Double {
        val frac = (timeSec / maxTimeSec).coerceIn(0.0, 1.0)
        return chartLeft + frac * (chartRight - chartLeft)
    }

    private fun tempToY(temp: Double, chartTop: Double, chartBottom: Double): Double {
        val frac = ((temp - tempMin) / (tempMax - tempMin)).coerceIn(0.0, 1.0)
        return chartBottom - frac * (chartBottom - chartTop)
    }

    private fun rorToY(ror: Double, chartTop: Double, chartBottom: Double): Double {
        val frac = ((ror - rorMin) / (rorMax - rorMin)).coerceIn(0.0, 1.0)
        return chartBottom - frac * (chartBottom - chartTop)
    }

    private fun formatTime(sec: Double): String {
        val m = (sec / 60).toInt()
        val s = (sec % 60).toInt()
        return "%02d:%02d".format(m, s)
    }
}
