package com.rdr.roast.ui.graph

import com.rdr.roast.app.AppSettings
import com.rdr.roast.app.ChartConfig
import com.rdr.roast.app.GridStyle
import com.rdr.roast.app.LegendLocation
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.control.TextField
import java.net.URL
import java.util.ResourceBundle

/**
 * Controller for the Axes tab in Settings → Chart.
 * Ports Artisan Axes dialog: Time Axis, Temperature Axis, Legend Location, Grid, Δ Axis.
 * Loads from [AppSettings.chartConfig] and returns [ChartConfig] via [getResult].
 */
class GraphAxesTabController : Initializable {

    @FXML lateinit var chkTimeAxisAuto: CheckBox
    @FXML lateinit var cmbTimeAxisMode: ComboBox<String>
    @FXML lateinit var chkTimeAxisLock: CheckBox
    @FXML lateinit var txtTimeAxisMin: TextField
    @FXML lateinit var txtTimeAxisMax: TextField
    @FXML lateinit var cmbTimeAxisStep: ComboBox<String>
    @FXML lateinit var txtRecordMinSec: TextField
    @FXML lateinit var txtRecordMaxSec: TextField
    @FXML lateinit var chkTimeAxisExpand: CheckBox

    @FXML lateinit var txtTempMin: TextField
    @FXML lateinit var txtTempMax: TextField
    @FXML lateinit var txtTempStep: TextField
    @FXML lateinit var txtStep100Event: TextField

    @FXML lateinit var cmbLegendLocation: ComboBox<String>

    @FXML lateinit var cmbGridStyle: ComboBox<String>
    @FXML lateinit var txtGridWidth: TextField
    @FXML lateinit var txtGridOpaqueness: TextField
    @FXML lateinit var chkGridTime: CheckBox
    @FXML lateinit var chkGridTemp: CheckBox

    @FXML lateinit var chkDeltaAxisAuto: CheckBox
    @FXML lateinit var chkDeltaET: CheckBox
    @FXML lateinit var chkDeltaBT: CheckBox
    @FXML lateinit var txtDeltaMin: TextField
    @FXML lateinit var txtDeltaMax: TextField
    @FXML lateinit var txtDeltaStep: TextField

    /** Artisan time step options: display label -> seconds. Index matches Artisan timeconversion. */
    private val timeStepOptions = listOf(
        "" to 0,
        "1 minute" to 60,
        "2 minutes" to 120,
        "3 minutes" to 180,
        "4 minutes" to 240,
        "5 minutes" to 300,
        "10 minutes" to 600,
        "30 minutes" to 1800,
        "1 hour" to 3600,
        "1 day" to 86400
    )

    private var baseConfig: ChartConfig = ChartConfig()

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        cmbTimeAxisMode.items.setAll("Roast", "BBP+Roast", "BBP")
        cmbTimeAxisStep.items.setAll(timeStepOptions.map { it.first })
        cmbLegendLocation.items.setAll(
            "",
            "upper right",
            "upper left",
            "lower left",
            "lower right",
            "right",
            "center left",
            "center right",
            "lower center",
            "upper center",
            "center"
        )
        cmbGridStyle.items.setAll("solid", "dashed", "dashed-dot", "dotted")
    }

    fun loadFrom(settings: AppSettings) {
        baseConfig = settings.chartConfig
        val c = baseConfig

        chkTimeAxisAuto.isSelected = c.timeAxisAuto
        cmbTimeAxisMode.selectionModel.select(c.timeAxisAutoMode.coerceIn(0, 2))
        chkTimeAxisLock.isSelected = c.timeAxisLock
        txtTimeAxisMin.text = formatTimeAxisMin(c.timeAxisMin)
        txtTimeAxisMax.text = formatTimeAxisMax(c.timeAxisMax)
        val stepIndex = timeStepOptions.indexOfFirst { it.second == c.timeAxisStepSec }.coerceAtLeast(0)
        cmbTimeAxisStep.selectionModel.select(stepIndex.coerceIn(0, timeStepOptions.lastIndex))
        txtRecordMinSec.text = c.recordMinSec.toString()
        txtRecordMaxSec.text = c.recordMaxSec.toString()
        chkTimeAxisExpand.isSelected = c.timeAxisExpand

        txtTempMin.text = c.tempMin.toInt().toString()
        txtTempMax.text = c.tempMax.toInt().toString()
        txtTempStep.text = c.tempAxisStep.toInt().toString()
        txtStep100Event.text = c.step100EventTemp?.toInt()?.toString() ?: ""

        cmbLegendLocation.selectionModel.select(c.legendLocation.index.coerceIn(0, 10))

        cmbGridStyle.value = when (c.gridStyle) {
            GridStyle.SOLID -> "solid"
            GridStyle.DASHED -> "dashed"
            GridStyle.DASHED_DOT -> "dashed-dot"
            GridStyle.DOTTED -> "dotted"
        }
        txtGridWidth.text = c.gridWidth.toString()
        txtGridOpaqueness.text = c.gridOpaqueness.toString()
        chkGridTime.isSelected = c.gridTime
        chkGridTemp.isSelected = c.gridTemp

        chkDeltaAxisAuto.isSelected = c.deltaAxisAuto
        chkDeltaET.isSelected = c.deltaET
        chkDeltaBT.isSelected = c.deltaBT
        txtDeltaMin.text = c.deltaMin.toInt().toString()
        txtDeltaMax.text = c.deltaMax.toInt().toString()
        txtDeltaStep.text = c.deltaStep.toInt().toString()
    }

    fun getResult(): ChartConfig {
        val timeStepSec = timeStepOptions.getOrNull(cmbTimeAxisStep.selectionModel.selectedIndex.coerceIn(0, timeStepOptions.lastIndex))?.second ?: 60
        val gridStyle = when (cmbGridStyle.value) {
            "dashed" -> GridStyle.DASHED
            "dashed-dot" -> GridStyle.DASHED_DOT
            "dotted" -> GridStyle.DOTTED
            else -> GridStyle.SOLID
        }
        val legendLocation = LegendLocation.fromIndex(cmbLegendLocation.selectionModel.selectedIndex.coerceIn(0, 10))

        val tempMin = txtTempMin.text.toDoubleOrNull() ?: baseConfig.tempMin
        val tempMax = txtTempMax.text.toDoubleOrNull() ?: baseConfig.tempMax
        val tempStep = txtTempStep.text.toDoubleOrNull()?.coerceIn(1.0, 100.0) ?: baseConfig.tempAxisStep
        val deltaMin = txtDeltaMin.text.toDoubleOrNull() ?: baseConfig.deltaMin
        val deltaMax = txtDeltaMax.text.toDoubleOrNull() ?: baseConfig.deltaMax
        val deltaStep = txtDeltaStep.text.toDoubleOrNull()?.coerceIn(0.5, 50.0) ?: baseConfig.deltaStep

        val timeAxisMin = parseTimeAxisMin(txtTimeAxisMin.text)
        val timeAxisMax = parseTimeAxisMax(txtTimeAxisMax.text)

        return baseConfig.copy(
            timeAxisAuto = chkTimeAxisAuto.isSelected,
            timeAxisAutoMode = cmbTimeAxisMode.selectionModel.selectedIndex.coerceIn(0, 2),
            timeAxisLock = chkTimeAxisLock.isSelected,
            timeAxisMin = timeAxisMin,
            timeAxisMax = timeAxisMax,
            timeAxisStepSec = timeStepSec,
            recordMinSec = txtRecordMinSec.text.toIntOrNull() ?: baseConfig.recordMinSec,
            recordMaxSec = txtRecordMaxSec.text.toIntOrNull()?.coerceIn(1, 86_400) ?: baseConfig.recordMaxSec,
            timeAxisExpand = chkTimeAxisExpand.isSelected,
            tempMin = minOf(tempMin, tempMax),
            tempMax = maxOf(tempMin, tempMax),
            tempAxisStep = tempStep,
            step100EventTemp = txtStep100Event.text.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull(),
            legendLocation = legendLocation,
            gridStyle = gridStyle,
            gridTime = chkGridTime.isSelected,
            gridTemp = chkGridTemp.isSelected,
            showGrid = chkGridTime.isSelected || chkGridTemp.isSelected,
            gridWidth = txtGridWidth.text.toIntOrNull()?.coerceIn(1, 5) ?: baseConfig.gridWidth,
            gridOpaqueness = txtGridOpaqueness.text.toDoubleOrNull()?.coerceIn(0.1, 1.0) ?: baseConfig.gridOpaqueness,
            deltaAxisAuto = chkDeltaAxisAuto.isSelected,
            deltaET = chkDeltaET.isSelected,
            deltaBT = chkDeltaBT.isSelected,
            deltaMin = minOf(deltaMin, deltaMax),
            deltaMax = maxOf(deltaMin, deltaMax),
            deltaStep = deltaStep
        )
    }

    /** Format minutes as "M:SS" or "-M:SS" for display (Artisan-style). */
    private fun formatTimeAxisMin(minutes: Double): String {
        val totalSec = (minutes * 60).toInt()
        val sign = if (totalSec < 0) "-" else ""
        val abs = kotlin.math.abs(totalSec)
        val m = abs / 60
        val s = abs % 60
        return "$sign$m:${s.toString().padStart(2, '0')}"
    }

    private fun formatTimeAxisMax(minutes: Double): String {
        val totalSec = (minutes * 60).toInt().coerceAtLeast(0)
        val m = totalSec / 60
        val s = totalSec % 60
        return "$m:${s.toString().padStart(2, '0')}"
    }

    /** Parse "M:SS" or "-M:SS" to minutes (Double). */
    private fun parseTimeAxisMin(s: String?): Double {
        if (s.isNullOrBlank()) return baseConfig.timeAxisMin
        val trimmed = s.trim()
        val negative = trimmed.startsWith("-")
        val part = if (negative) trimmed.drop(1) else trimmed
        val (m, sec) = part.split(":").let { 
            if (it.size >= 2) it[0].toIntOrNull() to it[1].toIntOrNull()
            else return@let null to null
        }
        if (m == null || sec == null) return baseConfig.timeAxisMin
        val totalSec = (m * 60 + sec) * if (negative) -1 else 1
        return totalSec / 60.0
    }

    private fun parseTimeAxisMax(s: String?): Double {
        if (s.isNullOrBlank()) return baseConfig.timeAxisMax
        val trimmed = s.trim()
        val (m, sec) = trimmed.split(":").let {
            if (it.size >= 2) it[0].toIntOrNull() to it[1].toIntOrNull()
            else return@let null to null
        }
        if (m == null || sec == null) return baseConfig.timeAxisMax
        return (m * 60 + sec) / 60.0
    }
}
