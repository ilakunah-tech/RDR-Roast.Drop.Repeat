package com.rdr.roast.ui.graph

import com.rdr.roast.app.CurvesConfig
import com.rdr.roast.app.CurvesUiConfig
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.control.Spinner
import javafx.scene.control.SpinnerValueFactory
import javafx.scene.control.TextField
import javafx.stage.FileChooser
import javafx.stage.Window
import java.io.File
import java.net.URL
import java.util.ResourceBundle

/**
 * Controller for the Curves UI tab (Artisan tab5): path effects, glow, style, font,
 * notifications, beep, graph resolution, decimal places, rename ET/BT, logo, WebLCDs.
 * Loads from [CurvesConfig.ui] and returns [CurvesUiConfig] via [getResult].
 */
class GraphUiTabController : Initializable {

    @FXML lateinit var spinPathEffects: Spinner<Int>
    @FXML lateinit var chkGlow: CheckBox
    @FXML lateinit var cmbGraphStyle: ComboBox<String>
    @FXML lateinit var cmbGraphFont: ComboBox<String>
    @FXML lateinit var chkNotifications: CheckBox
    @FXML lateinit var chkBeep: CheckBox
    @FXML lateinit var spinGraphResolution: Spinner<Int>
    @FXML lateinit var btnSetResolution: Button
    @FXML lateinit var cmbTempDecimals: ComboBox<String>
    @FXML lateinit var cmbPercentDecimals: ComboBox<String>
    @FXML lateinit var txtRenameET: TextField
    @FXML lateinit var txtRenameBT: TextField
    @FXML lateinit var txtLogoImagePath: TextField
    @FXML lateinit var btnLogoLoad: Button
    @FXML lateinit var btnLogoDelete: Button
    @FXML lateinit var spinLogoOpacity: Spinner<Double>
    @FXML lateinit var chkHideLogoDuringRoast: CheckBox
    @FXML lateinit var chkWebLcdEnabled: CheckBox
    @FXML lateinit var txtWebLcdPort: TextField
    @FXML lateinit var chkAlarmPopups: CheckBox

    companion object {
        private val GRAPH_STYLE_OPTIONS = listOf("classic", "xkcd")
        private val GRAPH_FONT_OPTIONS = listOf(
            "Default", "Humor", "Comic", "WenQuanYi Zen Hei",
            "Source Han Sans CN", "Source Han Sans TW", "Source Han Sans HK",
            "Source Han Sans KR", "Source Han Sans JP", "Dijkstra",
            "xkcd Script", "Comic Neue"
        )
        private val TEMP_DECIMALS_LABELS = listOf("181°C", "180.9°C", "180.87°C")
        private val PERCENT_DECIMALS_LABELS = listOf("15%", "14.6%", "14.57%")
    }

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        spinPathEffects.valueFactory = javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory(0, 5, 1)
        spinGraphResolution.valueFactory = javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory(40, 300, 100)
        spinLogoOpacity.valueFactory = javafx.scene.control.SpinnerValueFactory.DoubleSpinnerValueFactory(0.0, 10.0, 2.0, 0.5)

        cmbGraphStyle.items.setAll(GRAPH_STYLE_OPTIONS)
        cmbGraphFont.items.setAll(GRAPH_FONT_OPTIONS)
        cmbTempDecimals.items.setAll(TEMP_DECIMALS_LABELS)
        cmbPercentDecimals.items.setAll(PERCENT_DECIMALS_LABELS)

        btnSetResolution.setOnAction { /* Apply resolution; actual apply can be done by parent when saving */ }
        btnLogoLoad.setOnAction { onLogoLoad() }
        btnLogoDelete.setOnAction { txtLogoImagePath.text = "" }

        chkWebLcdEnabled.selectedProperty().addListener { _, _, selected ->
            txtWebLcdPort.isDisable = !selected
            if (!selected) chkAlarmPopups.isSelected = false
        }
        chkAlarmPopups.disableProperty().bind(chkWebLcdEnabled.selectedProperty().not())
    }

    private fun onLogoLoad() {
        val window: Window? = txtLogoImagePath.scene?.window
        val chooser = FileChooser().apply {
            title = "Select logo image"
            extensionFilters.add(FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"))
        }
        val file: File? = chooser.showOpenDialog(window)
        if (file != null) {
            txtLogoImagePath.text = file.absolutePath
        }
    }

    fun loadFrom(config: CurvesConfig) {
        val ui = config.ui
        (spinPathEffects.valueFactory as? SpinnerValueFactory.IntegerSpinnerValueFactory)?.value = ui.pathEffects.coerceIn(0, 5)
        chkGlow.isSelected = ui.glow
        cmbGraphStyle.selectionModel.select(ui.graphStyle.coerceIn(0, GRAPH_STYLE_OPTIONS.lastIndex))
        cmbGraphFont.selectionModel.select(ui.graphFont.coerceIn(0, GRAPH_FONT_OPTIONS.lastIndex))
        chkNotifications.isSelected = ui.notifications
        chkBeep.isSelected = ui.beep
        (spinGraphResolution.valueFactory as? SpinnerValueFactory.IntegerSpinnerValueFactory)?.value = ui.graphResolutionPercent.coerceIn(40, 300)
        cmbTempDecimals.selectionModel.select(ui.tempDecimals.coerceIn(0, 2))
        cmbPercentDecimals.selectionModel.select(ui.percentDecimals.coerceIn(0, 2))
        txtRenameET.text = ui.renameET
        txtRenameBT.text = ui.renameBT
        txtLogoImagePath.text = ui.logoImagePath
        (spinLogoOpacity.valueFactory as? SpinnerValueFactory.DoubleSpinnerValueFactory)?.value = ui.logoOpacity.coerceIn(0.0, 10.0)
        chkHideLogoDuringRoast.isSelected = ui.hideLogoDuringRoast
        chkWebLcdEnabled.isSelected = ui.webLcdEnabled
        txtWebLcdPort.text = ui.webLcdPort.toString()
        txtWebLcdPort.isDisable = !ui.webLcdEnabled
        chkAlarmPopups.isSelected = ui.alarmPopups
    }

    fun getResult(): CurvesUiConfig {
        val graphStyleIndex = cmbGraphStyle.selectionModel.selectedIndex.coerceIn(0, GRAPH_STYLE_OPTIONS.lastIndex)
        val graphFontIndex = cmbGraphFont.selectionModel.selectedIndex.coerceIn(0, GRAPH_FONT_OPTIONS.lastIndex)
        val tempDecimals = cmbTempDecimals.selectionModel.selectedIndex.coerceIn(0, 2)
        val percentDecimals = cmbPercentDecimals.selectionModel.selectedIndex.coerceIn(0, 2)
        val port = txtWebLcdPort.text.trim().toIntOrNull()?.coerceIn(1, 65535) ?: 8080

        return CurvesUiConfig(
            pathEffects = spinPathEffects.value.coerceIn(0, 5),
            glow = chkGlow.isSelected,
            graphStyle = graphStyleIndex,
            graphFont = graphFontIndex,
            notifications = chkNotifications.isSelected,
            beep = chkBeep.isSelected,
            graphResolutionPercent = spinGraphResolution.value.coerceIn(40, 300),
            tempDecimals = tempDecimals,
            percentDecimals = percentDecimals,
            renameET = txtRenameET.text.trim().ifEmpty { "ET" },
            renameBT = txtRenameBT.text.trim().ifEmpty { "BT" },
            logoImagePath = txtLogoImagePath.text.trim(),
            logoOpacity = spinLogoOpacity.value.coerceIn(0.0, 10.0),
            hideLogoDuringRoast = chkHideLogoDuringRoast.isSelected,
            webLcdPort = port,
            webLcdEnabled = chkWebLcdEnabled.isSelected,
            alarmPopups = chkAlarmPopups.isSelected
        )
    }
}
