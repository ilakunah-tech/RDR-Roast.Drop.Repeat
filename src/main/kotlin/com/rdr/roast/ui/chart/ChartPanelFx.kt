package com.rdr.roast.ui.chart

import javafx.geometry.Insets
import javafx.scene.control.Label
import javafx.scene.input.ContextMenuEvent
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.input.ScrollEvent
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import org.jfree.chart.fx.ChartViewer
import org.jfree.chart.ui.RectangleEdge

/**
 * JavaFX container for [CurveChartFx], modeled after Cropster's ChartWrapper.
 *
 * Layout:
 *   - Top:    toolbar (spacer · live mouse-coordinates label). Reset axes — по ПКМ по графику.
 *   - Center: ChartViewer (jfreechart-fx / fxgraphics2d bridge)
 *
 * Mouse-click behaviour (Cropster "Comment at" card):
 *   A single primary-button click inside the chart data area opens a [ChartEventPopup]
 *   at the cursor position.  The popup resolves the click X-coordinate to a MM:SS
 *   timestamp and lets the user choose a predefined event type or enter custom text.
 *
 * [onPopupResult] optional callback fired when the popup confirms an annotation.
 */
class ChartPanelFx(val curveChart: CurveChartFx) : BorderPane() {

    val chartViewer = ChartViewer(curveChart.chart).apply {
        minWidth  = 300.0
        minHeight = 200.0
    }

    /** Called after the user adds an annotation via the chart popup. */
    var onPopupResult: ((ChartPopupResult) -> Unit)? = null
    /** Determines whether the popup should behave like roast comments or BBP comments. */
    var popupModeProvider: () -> ChartPopupMode = { ChartPopupMode.ROAST }

    /**
     * Time (ms) of the last built data point on the chart.
     * During roast: set from MainController on each sample.
     * If the user clicks in the "future" zone (right of the curve), the popup uses this time (Cropster logic).
     */
    var lastDataTimeMs: Long? = null

    private val lblCoords = Label("").apply {
        style = "-fx-font-size: 10px; -fx-text-fill: #555;"
    }

    init {
        val spacer = Region().apply { HBox.setHgrow(this, Priority.ALWAYS) }
        val toolbar = HBox(4.0, spacer, lblCoords).apply {
            padding = Insets(4.0, 8.0, 4.0, 8.0)
            style = "-fx-background-color: #f8f8f8;" +
                    "-fx-border-color: #ddd; -fx-border-width: 0 0 1 0;"
        }

        val chartAnchor = AnchorPane(chartViewer).apply {
            AnchorPane.setTopAnchor(chartViewer,    0.0)
            AnchorPane.setBottomAnchor(chartViewer, 0.0)
            AnchorPane.setLeftAnchor(chartViewer,   0.0)
            AnchorPane.setRightAnchor(chartViewer,  0.0)
        }

        top    = toolbar
        center = chartAnchor

        // ── Live mouse-coordinate display ─────────────────────────────────────
        chartViewer.canvas.setOnMouseMoved { e ->
            val dataArea = chartViewer.canvas.renderingInfo
                ?.plotInfo?.dataArea ?: return@setOnMouseMoved
            val xMs   = curveChart.plot.domainAxis.java2DToValue(
                e.x, dataArea, RectangleEdge.BOTTOM)
            val yTemp = curveChart.plot.getRangeAxis(0).java2DToValue(
                e.y, dataArea, RectangleEdge.LEFT)
            if (!xMs.isNaN() && !yTemp.isNaN()) {
                val s = (xMs / 1000).toLong().coerceAtLeast(0)
                lblCoords.text = "%02d:%02d  %.1f °C".format(s / 60, s % 60, yTemp)
            }
        }
        chartViewer.canvas.setOnMouseExited { lblCoords.text = "" }

        // ПКМ по графику → reset axes (потребляем, чтобы не показывалось меню "Export As")
        chartViewer.canvas.setOnContextMenuRequested { e -> e.consume(); curveChart.resetAxes() }

        // ── Click → "Comment at" popup (Cropster-style) ───────────────────────
        // Track pressed position to distinguish a click from a drag.
        // Using addEventHandler so jfreechart-fx's own setOnMouseXxx handlers
        // are NOT replaced.
        var pressX = 0.0
        var pressY = 0.0

        chartViewer.canvas.addEventHandler(MouseEvent.MOUSE_PRESSED) { e ->
            pressX = e.x
            pressY = e.y
            // ПКМ: потребляем, чтобы не показывалось контекстное меню библиотеки
            if (e.button == MouseButton.SECONDARY) e.consume()
        }

        chartViewer.canvas.addEventHandler(MouseEvent.MOUSE_CLICKED) { e ->
            // ПКМ (right-click) → Reset axes
            if (e.button == MouseButton.SECONDARY) {
                curveChart.resetAxes()
                return@addEventHandler
            }
            if (e.button     != MouseButton.PRIMARY) return@addEventHandler
            if (e.clickCount != 1)                   return@addEventHandler

            // Ignore if the mouse travelled more than ~6 px (it was a drag/zoom)
            val dx = e.x - pressX
            val dy = e.y - pressY
            if (dx * dx + dy * dy > 36.0) return@addEventHandler

            val info     = chartViewer.canvas.renderingInfo ?: return@addEventHandler
            val dataArea = info.plotInfo.dataArea            ?: return@addEventHandler

            // Only show popup if the click landed inside the data area
            if (!dataArea.contains(e.x, e.y)) return@addEventHandler

            val popupMode = popupModeProvider()
            val clickedTimeMs = curveChart.plot.domainAxis
                .java2DToValue(e.x, dataArea, RectangleEdge.BOTTOM).toLong()
            if (clickedTimeMs < 0) return@addEventHandler

            // Cropster logic: click in zone where graph not yet drawn → use last built point time
            val effectiveTimeMs = lastDataTimeMs?.let { last ->
                if (clickedTimeMs > last) last else clickedTimeMs
            } ?: clickedTimeMs

            val btAtTime = curveChart.getBtAtTime(effectiveTimeMs)
            val popup = ChartEventPopup(popupMode, effectiveTimeMs, btAtTime) { result ->
                val rawTimeMs = if (popupMode == ChartPopupMode.BBP) result.timeMs else curveChart.toRawTime(result.timeMs)
                val adjusted = when (result) {
                    is ChartPopupEventResult -> result.copy(timeMs = rawTimeMs)
                    is ChartPopupCommentResult -> result.copy(timeMs = rawTimeMs)
                }
                onPopupResult?.invoke(adjusted)
            }

            val window = chartViewer.scene?.window ?: return@addEventHandler
            // Offset slightly so the popup doesn't sit directly under the cursor
            popup.show(window, e.screenX + 8, e.screenY - 8)
        }

        // ── Ctrl+Wheel → zoom domain axis ───────────────────────────────────
        chartViewer.addEventFilter(ScrollEvent.SCROLL) { event ->
            if (event.isShortcutDown) {
                event.consume()
                val zoomFactor = 0.1
                val domainAxis = curveChart.plot.domainAxis
                val currentRange = domainAxis.range
                val rangeLenMs = currentRange.length

                val zf = if (event.deltaY > 0) 1.0 / (1.0 + zoomFactor) else (1.0 + zoomFactor)
                val newRange = rangeLenMs * zf
                val anchor = currentRange.lowerBound + rangeLenMs / 2.0
                val newLower = anchor - newRange / 2.0
                val newUpper = anchor + newRange / 2.0
                domainAxis.setRange(newLower.coerceAtLeast(0.0), newUpper)
            }
        }
    }
}
