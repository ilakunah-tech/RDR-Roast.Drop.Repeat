package com.rdr.roast.ui.graph

import com.rdr.roast.app.CurvesConfig
import com.rdr.roast.app.CurvesPlotterConfig
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.ColorPicker
import javafx.scene.control.TextField
import javafx.scene.paint.Color

/**
 * Controller for the Plotter tab (tab2) of the Curves dialog.
 * Binds P1–P9 equation fields and colors, Background equation, ET/BT color, and Plot/Data/Save Image/Help buttons.
 */
class GraphPlotterTabController {

    @FXML lateinit var txtP1: TextField
    @FXML lateinit var txtP2: TextField
    @FXML lateinit var txtP3: TextField
    @FXML lateinit var txtP4: TextField
    @FXML lateinit var txtP5: TextField
    @FXML lateinit var txtP6: TextField
    @FXML lateinit var txtP7: TextField
    @FXML lateinit var txtP8: TextField
    @FXML lateinit var txtP9: TextField
    @FXML lateinit var txtBackgroundEqu: TextField

    @FXML lateinit var colorP1: ColorPicker
    @FXML lateinit var colorP2: ColorPicker
    @FXML lateinit var colorP3: ColorPicker
    @FXML lateinit var colorP4: ColorPicker
    @FXML lateinit var colorP5: ColorPicker
    @FXML lateinit var colorP6: ColorPicker
    @FXML lateinit var colorP7: ColorPicker
    @FXML lateinit var colorP8: ColorPicker
    @FXML lateinit var colorP9: ColorPicker
    @FXML lateinit var colorEtBt: ColorPicker

    @FXML lateinit var btnColorP1: Button
    @FXML lateinit var btnColorP2: Button
    @FXML lateinit var btnColorP3: Button
    @FXML lateinit var btnColorP4: Button
    @FXML lateinit var btnColorP5: Button
    @FXML lateinit var btnColorP6: Button
    @FXML lateinit var btnColorP7: Button
    @FXML lateinit var btnColorP8: Button
    @FXML lateinit var btnColorP9: Button

    @FXML lateinit var btnPlot: Button
    @FXML lateinit var btnData: Button
    @FXML lateinit var btnSaveImage: Button
    @FXML lateinit var btnHelp: Button

    private val equationFields: List<TextField> by lazy {
        listOf(txtP1, txtP2, txtP3, txtP4, txtP5, txtP6, txtP7, txtP8, txtP9)
    }

    private val curveColorPickers: List<ColorPicker> by lazy {
        listOf(colorP1, colorP2, colorP3, colorP4, colorP5, colorP6, colorP7, colorP8, colorP9)
    }

    private val colorButtons: List<Button> by lazy {
        listOf(btnColorP1, btnColorP2, btnColorP3, btnColorP4, btnColorP5, btnColorP6, btnColorP7, btnColorP8, btnColorP9)
    }

    @FXML
    fun initialize() {
        curveColorPickers.zip(colorButtons).forEach { (picker, btn) ->
            btn.setOnAction { picker.show() }
        }
        // Plot, Data, Save Image, Help: no-op in controller; parent can set actions
    }

    /**
     * Loads state from the full Curves config (plotter tab uses config.plotter).
     */
    fun loadFrom(config: CurvesConfig) {
        val p = config.plotter
        val curves = p.plotcurves
        val colors = p.plotcurvecolor
        equationFields.forEachIndexed { i, field ->
            field.text = curves.getOrElse(i) { "" }
        }
        curveColorPickers.forEachIndexed { i, picker ->
            setColor(picker, colors.getOrElse(i) { "#000000" })
        }
        txtBackgroundEqu.text = p.backgroundEqu
        setColor(colorEtBt, p.etBtColor)
    }

    /**
     * Returns the current plotter config from the form, or null if not yet applied.
     * Caller typically reads form values when saving; here we always build from current controls.
     */
    fun getResult(): CurvesPlotterConfig {
        val plotcurves = equationFields.map { it.text?.trim().orEmpty() }
        val plotcurvecolor = curveColorPickers.map { colorToHex(it.value) }
        return CurvesPlotterConfig(
            plotcurves = plotcurves,
            plotcurvecolor = plotcurvecolor,
            backgroundEqu = txtBackgroundEqu.text?.trim().orEmpty(),
            etBtColor = colorToHex(colorEtBt.value)
        )
    }

    private fun setColor(picker: ColorPicker, hex: String?) {
        val raw = hex?.trim().orEmpty()
        if (raw.isBlank()) return
        try {
            picker.value = Color.web(if (raw.startsWith("#")) raw else "#$raw")
        } catch (_: Exception) {
        }
    }

    private fun colorToHex(color: Color): String =
        "#%02x%02x%02x".format(
            (color.red * 255).toInt().coerceIn(0, 255),
            (color.green * 255).toInt().coerceIn(0, 255),
            (color.blue * 255).toInt().coerceIn(0, 255)
        )
}
