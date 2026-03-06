package com.rdr.roast.ui

import com.rdr.roast.app.ChartConfig
import com.rdr.roast.app.GridStyle
import com.rdr.roast.app.LegendLocation
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.control.TextField
import javafx.stage.Stage

class AxesConfigController {
    var applied: Boolean = false
        private set

    private var result: ChartConfig? = null
    private var baseConfig: ChartConfig = ChartConfig()

    @FXML lateinit var chkTimeAxisAuto: CheckBox
    @FXML lateinit var cmbTimeAxisMode: ComboBox<String>
    @FXML lateinit var chkTimeAxisLock: CheckBox
    @FXML lateinit var txtTimeAxisMin: TextField
    @FXML lateinit var txtTimeAxisMax: TextField
    @FXML lateinit var txtTimeAxisStep: TextField
    @FXML lateinit var txtRecordMinSec: TextField
    @FXML lateinit var txtRecordMaxSec: TextField
    @FXML lateinit var chkTimeAxisExpand: CheckBox

    @FXML lateinit var txtTempMin: TextField
    @FXML lateinit var txtTempMax: TextField
    @FXML lateinit var txtTempStep: TextField
    @FXML lateinit var txtStep100Event: TextField

    @FXML lateinit var cmbLegendLocation: ComboBox<String>

    @FXML lateinit var cmbGridStyle: ComboBox<String>
    @FXML lateinit var chkGridTime: CheckBox
    @FXML lateinit var chkGridTemp: CheckBox
    @FXML lateinit var txtGridWidth: TextField
    @FXML lateinit var txtGridOpaqueness: TextField

    @FXML lateinit var chkDeltaAxisAuto: CheckBox
    @FXML lateinit var chkDeltaET: CheckBox
    @FXML lateinit var chkDeltaBT: CheckBox
    @FXML lateinit var txtDeltaMin: TextField
    @FXML lateinit var txtDeltaMax: TextField
    @FXML lateinit var txtDeltaStep: TextField

    @FXML lateinit var btnSave: Button
    @FXML lateinit var btnCancel: Button

    @FXML
    fun initialize() {
        cmbTimeAxisMode.items.setAll("Roast mode", "Roast/Record mode")
        cmbLegendLocation.items.setAll(
            "None", "Upper right", "Upper left", "Lower left", "Lower right",
            "Right", "Center left", "Center right", "Lower center", "Upper center", "Center"
        )
        cmbGridStyle.items.setAll("Solid", "Dashed", "Dashed-dot", "Dotted")

        btnSave.setOnAction { applyAndClose() }
        btnCancel.setOnAction { closeWindow() }
    }

    fun loadFrom(config: ChartConfig) {
        baseConfig = config
        chkTimeAxisAuto.isSelected = config.timeAxisAuto
        cmbTimeAxisMode.selectionModel.select(config.timeAxisAutoMode.coerceIn(0, 1))
        chkTimeAxisLock.isSelected = config.timeAxisLock
        txtTimeAxisMin.text = config.timeAxisMin.toString()
        txtTimeAxisMax.text = config.timeAxisMax.toString()
        txtTimeAxisStep.text = config.timeAxisStepSec.toString()
        txtRecordMinSec.text = config.recordMinSec.toString()
        txtRecordMaxSec.text = config.recordMaxSec.toString()
        chkTimeAxisExpand.isSelected = config.timeAxisExpand

        txtTempMin.text = config.tempMin.toString()
        txtTempMax.text = config.tempMax.toString()
        txtTempStep.text = config.tempAxisStep.toString()
        txtStep100Event.text = config.step100EventTemp?.toString() ?: ""

        cmbLegendLocation.selectionModel.select(config.legendLocation.index.coerceIn(0, 10))

        cmbGridStyle.value = when (config.gridStyle) {
            GridStyle.SOLID -> "Solid"
            GridStyle.DASHED -> "Dashed"
            GridStyle.DASHED_DOT -> "Dashed-dot"
            GridStyle.DOTTED -> "Dotted"
        }
        chkGridTime.isSelected = config.gridTime
        chkGridTemp.isSelected = config.gridTemp
        txtGridWidth.text = config.gridWidth.toString()
        txtGridOpaqueness.text = config.gridOpaqueness.toString()

        chkDeltaAxisAuto.isSelected = config.deltaAxisAuto
        chkDeltaET.isSelected = config.deltaET
        chkDeltaBT.isSelected = config.deltaBT
        txtDeltaMin.text = config.deltaMin.toString()
        txtDeltaMax.text = config.deltaMax.toString()
        txtDeltaStep.text = config.deltaStep.toString()
    }

    fun getResult(): ChartConfig? = result

    private fun applyAndClose() {
        val gridStyle = when (cmbGridStyle.value) {
            "Dashed" -> GridStyle.DASHED
            "Dashed-dot" -> GridStyle.DASHED_DOT
            "Dotted" -> GridStyle.DOTTED
            else -> GridStyle.SOLID
        }
        val legendLocation = LegendLocation.fromIndex(cmbLegendLocation.selectionModel.selectedIndex.coerceIn(0, 10))
        val tempMin = txtTempMin.text.toDoubleOrNull() ?: baseConfig.tempMin
        val tempMax = txtTempMax.text.toDoubleOrNull() ?: baseConfig.tempMax
        val tempStep = txtTempStep.text.toDoubleOrNull()?.coerceIn(1.0, 100.0) ?: baseConfig.tempAxisStep
        val deltaMin = txtDeltaMin.text.toDoubleOrNull() ?: baseConfig.deltaMin
        val deltaMax = txtDeltaMax.text.toDoubleOrNull() ?: baseConfig.deltaMax
        val deltaStep = txtDeltaStep.text.toDoubleOrNull()?.coerceIn(0.5, 50.0) ?: baseConfig.deltaStep

        result = baseConfig.copy(
            timeAxisAuto = chkTimeAxisAuto.isSelected,
            timeAxisAutoMode = cmbTimeAxisMode.selectionModel.selectedIndex.coerceIn(0, 1),
            timeAxisLock = chkTimeAxisLock.isSelected,
            timeAxisMin = txtTimeAxisMin.text.toDoubleOrNull() ?: baseConfig.timeAxisMin,
            timeAxisMax = txtTimeAxisMax.text.toDoubleOrNull() ?: baseConfig.timeAxisMax,
            timeAxisStepSec = txtTimeAxisStep.text.toIntOrNull()?.coerceIn(0, 86_400) ?: baseConfig.timeAxisStepSec,
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
        applied = true
        closeWindow()
    }

    private fun closeWindow() {
        (btnSave.scene?.window as? Stage)?.close()
    }
}
