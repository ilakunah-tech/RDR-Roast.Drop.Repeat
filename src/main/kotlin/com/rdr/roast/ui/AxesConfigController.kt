package com.rdr.roast.ui

import com.rdr.roast.app.ChartConfig
import com.rdr.roast.app.GridStyle
import com.rdr.roast.app.LegendLocation
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ColorPicker
import javafx.scene.control.ComboBox
import javafx.scene.control.TextField
import javafx.scene.paint.Color
import javafx.scene.text.Font
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
    @FXML lateinit var colorChartBackground: ColorPicker
    @FXML lateinit var colorGridColor: ColorPicker
    @FXML lateinit var colorAxisLabelColor: ColorPicker
    @FXML lateinit var colorMarkerColor: ColorPicker
    @FXML lateinit var colorMarkerBgColor: ColorPicker
    @FXML lateinit var colorPhaseDryingColor: ColorPicker
    @FXML lateinit var colorPhaseMaillardColor: ColorPicker
    @FXML lateinit var colorPhaseDevelopmentColor: ColorPicker
    @FXML lateinit var colorPhaseLabelColor: ColorPicker
    @FXML lateinit var txtChartBackground: TextField
    @FXML lateinit var txtGridColor: TextField
    @FXML lateinit var txtAxisLabelColor: TextField
    @FXML lateinit var txtMarkerColor: TextField
    @FXML lateinit var txtMarkerBgColor: TextField
    @FXML lateinit var txtPhaseDryingColor: TextField
    @FXML lateinit var txtPhaseMaillardColor: TextField
    @FXML lateinit var txtPhaseDevelopmentColor: TextField
    @FXML lateinit var txtPhaseLabelColor: TextField
    @FXML lateinit var cmbFontFamily: ComboBox<String>
    @FXML lateinit var txtAxisFontSize: TextField
    @FXML lateinit var txtMarkerFontSize: TextField

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
        cmbFontFamily.items.setAll(Font.getFamilies())
        cmbFontFamily.isEditable = true
        bindColor(colorChartBackground, txtChartBackground)
        bindColor(colorGridColor, txtGridColor)
        bindColor(colorAxisLabelColor, txtAxisLabelColor)
        bindColor(colorMarkerColor, txtMarkerColor)
        bindColor(colorMarkerBgColor, txtMarkerBgColor)
        bindColor(colorPhaseDryingColor, txtPhaseDryingColor)
        bindColor(colorPhaseMaillardColor, txtPhaseMaillardColor)
        bindColor(colorPhaseDevelopmentColor, txtPhaseDevelopmentColor)
        bindColor(colorPhaseLabelColor, txtPhaseLabelColor)

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
        txtChartBackground.text = config.backgroundColor
        txtGridColor.text = config.gridColor
        txtAxisLabelColor.text = config.axisLabelColor
        txtMarkerColor.text = config.markerColor
        txtMarkerBgColor.text = config.markerLabelBackgroundColor
        txtPhaseDryingColor.text = config.phaseDryingColor
        txtPhaseMaillardColor.text = config.phaseMaillardColor
        txtPhaseDevelopmentColor.text = config.phaseDevelopmentColor
        txtPhaseLabelColor.text = config.phaseLabelColor
        setColor(colorChartBackground, config.backgroundColor)
        setColor(colorGridColor, config.gridColor)
        setColor(colorAxisLabelColor, config.axisLabelColor)
        setColor(colorMarkerColor, config.markerColor)
        setColor(colorMarkerBgColor, config.markerLabelBackgroundColor)
        setColor(colorPhaseDryingColor, config.phaseDryingColor)
        setColor(colorPhaseMaillardColor, config.phaseMaillardColor)
        setColor(colorPhaseDevelopmentColor, config.phaseDevelopmentColor)
        setColor(colorPhaseLabelColor, config.phaseLabelColor)
        cmbFontFamily.value = config.fontFamily
        txtAxisFontSize.text = config.axisFontSize.toString()
        txtMarkerFontSize.text = config.markerFontSize.toString()

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
            backgroundColor = txtChartBackground.text.trim().ifBlank { baseConfig.backgroundColor },
            gridColor = txtGridColor.text.trim().ifBlank { baseConfig.gridColor },
            axisLabelColor = txtAxisLabelColor.text.trim().ifBlank { baseConfig.axisLabelColor },
            markerColor = txtMarkerColor.text.trim().ifBlank { baseConfig.markerColor },
            markerLabelBackgroundColor = txtMarkerBgColor.text.trim().ifBlank { baseConfig.markerLabelBackgroundColor },
            phaseDryingColor = txtPhaseDryingColor.text.trim().ifBlank { baseConfig.phaseDryingColor },
            phaseMaillardColor = txtPhaseMaillardColor.text.trim().ifBlank { baseConfig.phaseMaillardColor },
            phaseDevelopmentColor = txtPhaseDevelopmentColor.text.trim().ifBlank { baseConfig.phaseDevelopmentColor },
            phaseLabelColor = txtPhaseLabelColor.text.trim().ifBlank { baseConfig.phaseLabelColor },
            fontFamily = (cmbFontFamily.value ?: cmbFontFamily.editor.text).trim(),
            axisFontSize = txtAxisFontSize.text.toDoubleOrNull()?.coerceIn(8.0, 32.0) ?: baseConfig.axisFontSize,
            markerFontSize = txtMarkerFontSize.text.toDoubleOrNull()?.coerceIn(8.0, 32.0) ?: baseConfig.markerFontSize,
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

    private fun bindColor(picker: ColorPicker, field: TextField) {
        picker.valueProperty().addListener { _, _, color -> field.text = colorToHex(color) }
        field.focusedProperty().addListener { _, _, focused ->
            if (!focused) setColor(picker, field.text)
        }
    }

    private fun setColor(picker: ColorPicker, hex: String?) {
        val raw = hex?.trim().orEmpty()
        if (raw.isBlank()) return
        try {
            picker.value = Color.web(raw)
        } catch (_: Exception) {
        }
    }

    private fun colorToHex(color: Color): String =
        String.format(
            "#%02x%02x%02x",
            (color.red * 255).toInt().coerceIn(0, 255),
            (color.green * 255).toInt().coerceIn(0, 255),
            (color.blue * 255).toInt().coerceIn(0, 255)
        )
}
