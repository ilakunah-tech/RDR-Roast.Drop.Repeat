package com.rdr.roast.ui.graph

import com.rdr.roast.app.CurvesAnalyzeConfig
import com.rdr.roast.app.CurvesConfig
import javafx.fxml.FXML
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.TextField

/**
 * Controller for the Curves dialog Analyze tab (tab4).
 * Curve Fit window start, Analyze interval start, segment sample/delta thresholds.
 */
class GraphAnalyzeTabController {

    @FXML lateinit var curveFitStartCombo: ComboBox<String>
    @FXML lateinit var curveFitOffsetLabel: Label
    @FXML lateinit var curveFitOffsetField: TextField
    @FXML lateinit var analysisStartCombo: ComboBox<String>
    @FXML lateinit var analysisOffsetLabel: Label
    @FXML lateinit var analysisOffsetField: TextField
    @FXML lateinit var segmentSamplesThresholdField: TextField
    @FXML lateinit var segmentDeltaThresholdField: TextField

    private val startChoices = listOf("DRY END", "120 secs before FCs", "Custom")

    @FXML
    fun initialize() {
        curveFitStartCombo.items.setAll(startChoices)
        analysisStartCombo.items.setAll(startChoices)

        curveFitStartCombo.selectionModel.selectedIndexProperty().addListener { _, _, _ -> updateCurveFitOffsetVisibility() }
        analysisStartCombo.selectionModel.selectedIndexProperty().addListener { _, _, _ -> updateAnalysisOffsetVisibility() }
    }

    fun loadFrom(config: CurvesConfig) {
        val a = config.analyze
        curveFitStartCombo.selectionModel.select(a.curvefitstartchoice.coerceIn(0, 2))
        curveFitOffsetField.text = a.curvefitoffset.toString()
        analysisStartCombo.selectionModel.select(a.analysisstartchoice.coerceIn(0, 2))
        analysisOffsetField.text = a.analysisoffset.toString()
        segmentSamplesThresholdField.text = a.segmentsamplesthreshold.toString()
        segmentDeltaThresholdField.text = a.segmentdeltathreshold.toString()
        updateCurveFitOffsetVisibility()
        updateAnalysisOffsetVisibility()
    }

    fun getResult(): CurvesAnalyzeConfig {
        val curveFitChoice = curveFitStartCombo.selectionModel.selectedIndex.coerceIn(0, 2)
        val analysisChoice = analysisStartCombo.selectionModel.selectedIndex.coerceIn(0, 2)
        return CurvesAnalyzeConfig(
            curvefitstartchoice = curveFitChoice,
            curvefitoffset = curveFitOffsetField.text.toIntOrNull()?.coerceIn(0, 9999) ?: 5,
            analysisstartchoice = analysisChoice,
            analysisoffset = analysisOffsetField.text.toIntOrNull()?.coerceIn(0, 9999) ?: 180,
            segmentsamplesthreshold = segmentSamplesThresholdField.text.toIntOrNull()?.coerceIn(0, 50) ?: 3,
            segmentdeltathreshold = segmentDeltaThresholdField.text.toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 0.6
        )
    }

    private fun updateCurveFitOffsetVisibility() {
        val isCustom = curveFitStartCombo.selectionModel.selectedIndex == 2
        curveFitOffsetLabel.isDisable = !isCustom
        curveFitOffsetField.isDisable = !isCustom
    }

    private fun updateAnalysisOffsetVisibility() {
        val isCustom = analysisStartCombo.selectionModel.selectedIndex == 2
        analysisOffsetLabel.isDisable = !isCustom
        analysisOffsetField.isDisable = !isCustom
    }
}
