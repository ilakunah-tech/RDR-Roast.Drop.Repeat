package com.rdr.roast.ui

import com.rdr.roast.app.AppSettings
import com.rdr.roast.app.ChartConfig
import com.rdr.roast.app.ChartColors
import com.rdr.roast.app.ConnectionPreset
import com.rdr.roast.app.EventQuantifierConfig
import com.rdr.roast.app.EventQuantifiersConfig
import com.rdr.roast.app.QuantifierSource
import com.rdr.roast.app.ConnectionTester
import com.rdr.roast.app.DeviceAssignmentConfig
import com.rdr.roast.app.MachineConfig
import com.rdr.roast.app.MachineType
import com.rdr.roast.app.RorSmoothing
import com.rdr.roast.app.ModbusInputConfig
import com.rdr.roast.app.ModbusPidConfig
import com.rdr.roast.app.ModbusTransportType
import com.rdr.roast.app.AppearanceSupport
import com.rdr.roast.app.SerialParity
import com.rdr.roast.app.SettingsManager
import com.rdr.roast.app.ThemeSupport
import com.rdr.roast.app.Transport
import com.rdr.roast.driver.ConnectionState
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ColorPicker
import javafx.scene.control.ComboBox
import javafx.scene.control.RadioButton
import javafx.scene.control.Slider
import javafx.scene.control.TabPane
import javafx.scene.control.TextField
import javafx.scene.control.TextInputDialog
import javafx.scene.control.ToggleGroup
import javafx.scene.control.Tooltip
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.DirectoryChooser
import javafx.stage.Stage
import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsController {
    private data class PortsAdvanced(
        val modbusType: ModbusTransportType,
        val byteSize: Int,
        val parity: SerialParity,
        val stopBits: Int,
        val serialTimeoutSec: Double,
        val ipTimeoutSec: Double,
        val ipRetries: Int
    )

    /** Set when user clicks Save; MainController uses this to apply and reconnect. */
    var savedSettings: AppSettings? = null
        private set

    /** When set (e.g. when settings are shown in a drawer), called on Save/Cancel instead of closing a stage. */
    var onCloseDrawer: ((SettingsController) -> Unit)? = null

    private val scope = CoroutineScope(Dispatchers.Default)
    private var portsAdvanced: PortsAdvanced? = null
    /** Set when user clicks OK in Ports Configuration (Artisan pattern: caller applies). */
    private var portsModbusInputs: List<ModbusInputConfig>? = null
    private var portsModbusPid: ModbusPidConfig? = null
    private var deviceAssignmentConfig: DeviceAssignmentConfig? = null
    /** Set when user clicks OK/Apply in Event Buttons dialog (caller applies on Save). */
    private var eventButtonsDialogResult: EventButtonsDialogResult? = null
    /** Separate Axes dialog state; merged into ChartConfig on Save. */
    private var axesConfigDraft: ChartConfig? = null

    @FXML
    lateinit var cmbSource: ComboBox<String>

    @FXML
    lateinit var cmbPort: ComboBox<String>

    @FXML
    lateinit var btnRefreshPorts: Button

    @FXML
    lateinit var txtBaudRate: TextField

    @FXML
    lateinit var txtSlaveId: TextField

    @FXML
    lateinit var tglUnit: ToggleGroup

    @FXML
    lateinit var rbCelsius: RadioButton

    @FXML
    lateinit var rbFahrenheit: RadioButton

    @FXML
    lateinit var txtSavePath: TextField

    @FXML
    lateinit var txtSamplingIntervalSec: TextField

    @FXML
    lateinit var btnBrowse: Button

    @FXML
    lateinit var btnSave: Button

    @FXML
    lateinit var btnCancel: Button

    @FXML
    lateinit var portFieldsContainer: VBox

    @FXML
    lateinit var cmbPreset: ComboBox<String>

    @FXML
    lateinit var btnLoadPreset: Button

    @FXML
    lateinit var btnSavePreset: Button

    @FXML
    lateinit var cmbTransport: ComboBox<String>

    @FXML
    lateinit var txtHost: TextField

    @FXML
    lateinit var txtTcpPort: TextField

    @FXML
    lateinit var txtPhidgetEtChannel: TextField

    @FXML
    lateinit var txtPhidgetBtChannel: TextField

    @FXML
    lateinit var lblHost: javafx.scene.control.Label

    @FXML
    lateinit var lblTcpPort: javafx.scene.control.Label

    @FXML
    lateinit var lblComPort: javafx.scene.control.Label

    @FXML
    lateinit var lblBaudRate: javafx.scene.control.Label

    @FXML
    lateinit var lblSlaveId: javafx.scene.control.Label

    @FXML
    lateinit var txtBtRegister: TextField

    @FXML
    lateinit var txtEtRegister: TextField

    @FXML
    lateinit var txtDivider: TextField

    @FXML
    lateinit var lblBtRegister: javafx.scene.control.Label

    @FXML
    lateinit var lblEtRegister: javafx.scene.control.Label

    @FXML
    lateinit var lblDivider: javafx.scene.control.Label

    @FXML
    lateinit var lblPhidgetEt: javafx.scene.control.Label

    @FXML
    lateinit var lblPhidgetBt: javafx.scene.control.Label

    @FXML
    lateinit var lblEtBtSource: javafx.scene.control.Label

    @FXML
    lateinit var chkBetweenBatchProtocol: CheckBox

    @FXML
    lateinit var chkAutoDetectRoaster: CheckBox

    @FXML
    lateinit var chkRememberLastRoaster: CheckBox

    @FXML
    lateinit var txtDiscoveryHosts: TextField

    @FXML
    lateinit var btnPortsConfig: javafx.scene.control.Button

    @FXML
    lateinit var btnEventButtons: Button

    @FXML
    lateinit var btnTestConnection: Button

    @FXML
    lateinit var txtEventCharge: TextField

    @FXML
    lateinit var txtEventDrop: TextField

    @FXML
    lateinit var txtEventDryEnd: TextField

    @FXML
    lateinit var txtEventFcStart: TextField

    @FXML
    lateinit var txtEventCoolEnd: TextField

    @FXML
    lateinit var chkVerifyBeforeSave: CheckBox

    @FXML
    lateinit var txtColorLiveBt: TextField

    @FXML
    lateinit var txtColorLiveEt: TextField

    @FXML
    lateinit var txtColorRefBt: TextField

    @FXML
    lateinit var txtColorRefEt: TextField

    @FXML
    lateinit var txtColorRefAlpha: TextField

    @FXML
    lateinit var btnResetColors: Button

    @FXML
    lateinit var txtChartTimeRange: TextField

    @FXML
    lateinit var chkChartShowGrid: CheckBox
    @FXML
    lateinit var chkChartShowBt: CheckBox
    @FXML
    lateinit var chkChartShowEt: CheckBox
    @FXML
    lateinit var chkChartShowRorBt: CheckBox
    @FXML
    lateinit var chkChartShowRorEt: CheckBox
    @FXML
    lateinit var chkChartShowReferenceCurves: CheckBox
    @FXML
    lateinit var chkChartShowReferenceEvents: CheckBox
    @FXML
    lateinit var chkChartShowPhaseStrips: CheckBox

    @FXML
    lateinit var txtChartBtLineWidth: TextField

    @FXML
    lateinit var txtChartEtLineWidth: TextField

    @FXML
    lateinit var txtChartRorLineWidth: TextField

    @FXML
    lateinit var txtChartRefLineWidth: TextField

    @FXML
    lateinit var txtChartBackground: TextField

    @FXML
    lateinit var txtChartGridColor: TextField

    @FXML
    lateinit var btnAxesConfig: Button

    @FXML
    lateinit var cmbRorSmoothing: ComboBox<String>

    @FXML
    lateinit var settingsTabPane: TabPane

    @FXML
    lateinit var cmbTheme: ComboBox<String>

    @FXML
    lateinit var btnApplyTheme: Button

    @FXML
    lateinit var cmbScale: ComboBox<String>

    @FXML
    lateinit var cmbFontSize: ComboBox<String>

    @FXML
    lateinit var cmbDensity: ComboBox<String>

    @FXML
    lateinit var colorAccent: ColorPicker

    @FXML
    lateinit var btnAccentDefault: Button

    @FXML
    lateinit var sldRadius: Slider

    @FXML
    lateinit var lblRadiusValue: javafx.scene.control.Label

    @FXML
    lateinit var btnRadiusReset: Button

    @FXML
    lateinit var btnPresetPeach: Button

    @FXML
    lateinit var btnPresetTeal: Button

    @FXML
    lateinit var btnPresetIndigo: Button

    @FXML
    lateinit var btnPresetSage: Button

    @FXML
    lateinit var btnPresetRose: Button

    @FXML
    lateinit var btnRestoreAppearance: Button

    @FXML
    lateinit var colorPanelBg: ColorPicker

    @FXML
    lateinit var btnPanelBgDefault: Button

    @FXML
    lateinit var colorLiveBt: ColorPicker

    @FXML
    lateinit var colorLiveEt: ColorPicker

    @FXML
    lateinit var colorLiveRorBt: ColorPicker

    @FXML
    lateinit var colorLiveRorEt: ColorPicker

    @FXML
    lateinit var colorRefBt: ColorPicker

    @FXML
    lateinit var colorRefEt: ColorPicker

    @FXML
    lateinit var sldRefAlpha: Slider

    @FXML
    lateinit var txtColorLiveRorBt: TextField

    @FXML
    lateinit var txtColorLiveRorEt: TextField

    @FXML
    lateinit var cmbQuantAirSource: ComboBox<String>
    @FXML
    lateinit var txtQuantAirSv: TextField
    @FXML
    lateinit var txtQuantAirMin: TextField
    @FXML
    lateinit var txtQuantAirMax: TextField
    @FXML
    lateinit var txtQuantAirStep: TextField
    @FXML
    lateinit var chkQuantAirAction: CheckBox
    @FXML
    lateinit var cmbQuantDrumSource: ComboBox<String>
    @FXML
    lateinit var txtQuantDrumSv: TextField
    @FXML
    lateinit var txtQuantDrumMin: TextField
    @FXML
    lateinit var txtQuantDrumMax: TextField
    @FXML
    lateinit var txtQuantDrumStep: TextField
    @FXML
    lateinit var chkQuantDrumAction: CheckBox
    @FXML
    lateinit var cmbQuantDamperSource: ComboBox<String>
    @FXML
    lateinit var txtQuantDamperSv: TextField
    @FXML
    lateinit var txtQuantDamperMin: TextField
    @FXML
    lateinit var txtQuantDamperMax: TextField
    @FXML
    lateinit var txtQuantDamperStep: TextField
    @FXML
    lateinit var chkQuantDamperAction: CheckBox
    @FXML
    lateinit var cmbQuantBurnerSource: ComboBox<String>
    @FXML
    lateinit var txtQuantBurnerSv: TextField
    @FXML
    lateinit var txtQuantBurnerMin: TextField
    @FXML
    lateinit var txtQuantBurnerMax: TextField
    @FXML
    lateinit var txtQuantBurnerStep: TextField
    @FXML
    lateinit var chkQuantBurnerAction: CheckBox

    @FXML
    fun initialize() {
        settingsTabPane.tabClosingPolicy = TabPane.TabClosingPolicy.UNAVAILABLE

        cmbTheme.items.setAll(ThemeSupport.themeEntries.map { it.second })
        cmbTheme.value = ThemeSupport.themeIdToDisplayName(ThemeSupport.loadThemeId()) ?: "Cupertino Light"
        cmbTheme.valueProperty().addListener { _, _, displayName ->
            ThemeSupport.displayNameToThemeId(displayName ?: return@addListener)?.let { id ->
                ThemeSupport.applyTheme(id)
                ThemeSupport.saveTheme(id)
            }
        }

        cmbScale.items.setAll(AppearanceSupport.scaleDisplayValues())
        cmbScale.value = AppearanceSupport.displayFromScale(AppearanceSupport.loadScale())
        cmbScale.valueProperty().addListener { _, _, display ->
            val scale = AppearanceSupport.scaleFromDisplay(display ?: return@addListener)
            AppearanceSupport.saveScale(scale)
        }

        cmbFontSize.items.setAll(AppearanceSupport.fontSizeDisplayValues())
        cmbFontSize.value = AppearanceSupport.displayFromFontSize(AppearanceSupport.loadFontSize())
        cmbFontSize.valueProperty().addListener { _, _, display ->
            AppearanceSupport.fontSizeFromDisplay(display ?: return@addListener).let { AppearanceSupport.saveFontSize(it) }
        }

        cmbDensity.items.setAll(AppearanceSupport.densityDisplayValues())
        cmbDensity.value = AppearanceSupport.displayFromDensity(AppearanceSupport.loadDensity())
        cmbDensity.valueProperty().addListener { _, _, display ->
            AppearanceSupport.densityFromDisplay(display ?: return@addListener).let { AppearanceSupport.saveDensity(it) }
        }

        val accentHex = AppearanceSupport.loadAccentColor()
        if (accentHex.isNotBlank()) {
            try { colorAccent.value = Color.web(accentHex) } catch (_: Exception) { }
        }
        colorAccent.valueProperty().addListener { _, _, c ->
            val hex = AppearanceSupport.colorToHex(c)
            AppearanceSupport.saveAccentColor(hex)
            (colorAccent.scene?.window as? Stage)?.scene?.let { AppearanceSupport.setAccent(hex, it) }
        }
        btnAccentDefault.setOnAction {
            AppearanceSupport.saveAccentColor("")
            (btnAccentDefault.scene?.window as? Stage)?.scene?.let { AppearanceSupport.setAccent("", it) }
        }

        sldRadius.value = AppearanceSupport.loadRadius().toDouble()
        lblRadiusValue.text = "${AppearanceSupport.loadRadius()}px"
        sldRadius.valueProperty().addListener { _, _, v ->
            val px = v.toInt().coerceIn(0, 20)
            lblRadiusValue.text = "${px}px"
            (sldRadius.scene?.window as? Stage)?.scene?.let { AppearanceSupport.setRadius(px, it) }
        }
        btnRadiusReset.setOnAction {
            sldRadius.value = 10.0
            lblRadiusValue.text = "10px"
            AppearanceSupport.saveRadius(10)
            (btnRadiusReset.scene?.window as? Stage)?.scene?.let { AppearanceSupport.setRadius(10, it) }
        }

        listOf(
            btnPresetPeach to "#E8896A",
            btnPresetTeal to "#4AABA8",
            btnPresetIndigo to "#5C6BC0",
            btnPresetSage to "#7BAE7F",
            btnPresetRose to "#C2666E"
        ).forEach { (btn, hex) ->
            btn.setOnAction {
                colorAccent.value = Color.web(hex)
                AppearanceSupport.saveAccentColor(hex)
                (btn.scene?.window as? Stage)?.scene?.let { AppearanceSupport.setAccent(hex, it) }
            }
        }

        val panelBgHex = AppearanceSupport.loadPanelBackground()
        try { colorPanelBg.value = Color.web(panelBgHex) } catch (_: Exception) { }
        colorPanelBg.valueProperty().addListener { _, _, c ->
            val hex = AppearanceSupport.colorToHex(c)
            AppearanceSupport.savePanelBackground(hex)
            (colorPanelBg.scene?.window as? Stage)?.scene?.let { AppearanceSupport.applyToScene(it) }
        }
        btnPanelBgDefault.setOnAction {
            AppearanceSupport.savePanelBackground("#f5f5f5")
            colorPanelBg.value = Color.web("#f5f5f5")
            (btnPanelBgDefault.scene?.window as? Stage)?.scene?.let { AppearanceSupport.applyToScene(it) }
        }

        btnApplyTheme.setOnAction { applyAndSaveTheme() }
        btnRestoreAppearance.setOnAction { restoreAppearanceDefaults() }

        val settings = SettingsManager.load()
        deviceAssignmentConfig = settings.deviceAssignment
        portsAdvanced = PortsAdvanced(
            modbusType = settings.machineConfig.modbusTransportType,
            byteSize = settings.machineConfig.byteSize,
            parity = settings.machineConfig.parity,
            stopBits = settings.machineConfig.stopBits,
            serialTimeoutSec = settings.machineConfig.serialTimeoutSec,
            ipTimeoutSec = settings.machineConfig.ipTimeoutSec,
            ipRetries = settings.machineConfig.ipRetries
        )

        cmbSource.items.setAll("Simulator", "Besca", "Diedrich")
        cmbSource.value = when (settings.machineConfig.machineType) {
            MachineType.SIMULATOR -> "Simulator"
            MachineType.BESCA -> "Besca"
            MachineType.DIEDRICH -> "Diedrich"
        }

        refreshPresetList()
        cmbTransport.valueProperty().addListener { _, _, _ -> updateTransportFieldsVisibility() }

        refreshSerialPorts()
        cmbPort.value = settings.machineConfig.port.ifBlank { null } ?: settings.machineConfig.port
        txtBaudRate.text = settings.machineConfig.baudRate.toString()
        txtSlaveId.text = settings.machineConfig.slaveId.toString()
        txtHost.text = settings.machineConfig.host?.takeIf { it.isNotBlank() } ?: ""
        txtTcpPort.text = settings.machineConfig.tcpPort.toString()
        txtPhidgetEtChannel.text = settings.machineConfig.phidgetEtChannel.toString()
        txtPhidgetBtChannel.text = settings.machineConfig.phidgetBtChannel.toString()
        txtBtRegister.text = settings.machineConfig.btRegister.toString()
        txtEtRegister.text = settings.machineConfig.etRegister.toString()
        txtDivider.text = settings.machineConfig.divisionFactor.toString()
        txtSavePath.text = settings.savePath
        txtSamplingIntervalSec.text = (settings.machineConfig.pollingIntervalMs / 1000.0).toString()
        chkBetweenBatchProtocol.isSelected = settings.betweenBatchProtocolEnabled
        chkAutoDetectRoaster.isSelected = settings.autoDetectRoaster
        chkRememberLastRoaster.isSelected = settings.rememberLastDetectedRoaster
        txtDiscoveryHosts.text = settings.discoveryTcpHosts?.joinToString(", ") ?: ""

        val ec = settings.machineConfig.eventCommands
        txtEventCharge.text = ec["CHARGE"] ?: ""
        txtEventDrop.text = ec["DROP"] ?: ""
        txtEventDryEnd.text = ec["DRY_END"] ?: ""
        txtEventFcStart.text = ec["FC_START"] ?: ""
        txtEventCoolEnd.text = ec["COOL_END"] ?: ""

        when (settings.unit.uppercase()) {
            "F" -> rbFahrenheit.isSelected = true
            else -> rbCelsius.isSelected = true
        }

        val colors = settings.chartColors
        txtColorLiveBt.text = colors.liveBt
        txtColorLiveEt.text = colors.liveEt
        txtColorLiveRorBt.text = colors.liveRorBt
        txtColorLiveRorEt.text = colors.liveRorEt
        txtColorRefBt.text = colors.refBt
        txtColorRefEt.text = colors.refEt
        txtColorRefAlpha.text = colors.refAlpha.toString()
        sldRefAlpha.value = colors.refAlpha.toDouble()
        bindColorPickerToHex(colorLiveBt, txtColorLiveBt)
        bindColorPickerToHex(colorLiveEt, txtColorLiveEt)
        bindColorPickerToHex(colorLiveRorBt, txtColorLiveRorBt)
        bindColorPickerToHex(colorLiveRorEt, txtColorLiveRorEt)
        bindColorPickerToHex(colorRefBt, txtColorRefBt)
        bindColorPickerToHex(colorRefEt, txtColorRefEt)
        setColorPickerFromHex(colorLiveBt, colors.liveBt)
        setColorPickerFromHex(colorLiveEt, colors.liveEt)
        setColorPickerFromHex(colorLiveRorBt, colors.liveRorBt)
        setColorPickerFromHex(colorLiveRorEt, colors.liveRorEt)
        setColorPickerFromHex(colorRefBt, colors.refBt)
        setColorPickerFromHex(colorRefEt, colors.refEt)
        sldRefAlpha.valueProperty().addListener { _, _, v -> txtColorRefAlpha.text = v.toInt().toString() }
        txtColorRefAlpha.textProperty().addListener { _, _, s ->
            s.toIntOrNull()?.coerceIn(0, 255)?.let { sldRefAlpha.value = it.toDouble() }
        }
        val config = settings.chartConfig
        axesConfigDraft = config
        txtChartTimeRange.text = config.timeRangeMin.toString()
        chkChartShowGrid.isSelected = config.showGrid
        chkChartShowBt.isSelected = config.showBt
        chkChartShowEt.isSelected = config.showEt
        chkChartShowRorBt.isSelected = config.showRorBt
        chkChartShowRorEt.isSelected = config.showRorEt
        chkChartShowReferenceCurves.isSelected = config.showReferenceCurves
        chkChartShowReferenceEvents.isSelected = config.showReferenceEvents
        chkChartShowPhaseStrips.isSelected = config.showPhaseStrips
        txtChartBtLineWidth.text = config.btLineWidth.toString()
        txtChartEtLineWidth.text = config.etLineWidth.toString()
        txtChartRorLineWidth.text = config.rorLineWidth.toString()
        txtChartRefLineWidth.text = config.refLineWidth.toString()
        txtChartBackground.text = config.backgroundColor
        txtChartGridColor.text = config.gridColor

        if (::cmbRorSmoothing.isInitialized) {
            cmbRorSmoothing.items.setAll("Sensitive", "Recommended", "Noise Resistant")
            cmbRorSmoothing.value = when (settings.rorSmoothing) {
                RorSmoothing.SENSITIVE -> "Sensitive"
                RorSmoothing.RECOMMENDED -> "Recommended"
                RorSmoothing.NOISE_RESISTANT -> "Noise Resistant"
            }
            cmbRorSmoothing.tooltip = Tooltip("RoR smoothing level: Sensitive (fast), Recommended, or Noise Resistant (heavy smoothing)")
        }

        val quantifiers = settings.eventQuantifiers
        val sourceItems = listOf("None", "ET", "BT")
        listOf(
            cmbQuantAirSource to quantifiers.air,
            cmbQuantDrumSource to quantifiers.drum,
            cmbQuantDamperSource to quantifiers.damper,
            cmbQuantBurnerSource to quantifiers.burner
        ).forEach { (combo, q) ->
            combo.items.setAll(sourceItems)
            combo.value = when (q.source) {
                QuantifierSource.NONE -> "None"
                QuantifierSource.ET -> "ET"
                QuantifierSource.BT -> "BT"
            }
        }
        fun setQuantFields(sv: TextField, min: TextField, max: TextField, step: TextField, action: CheckBox, q: EventQuantifierConfig) {
            sv.text = q.sv.toString()
            min.text = q.min.toString()
            max.text = q.max.toString()
            step.text = q.step.toString()
            action.isSelected = q.actionEnabled
        }
        setQuantFields(txtQuantAirSv, txtQuantAirMin, txtQuantAirMax, txtQuantAirStep, chkQuantAirAction, quantifiers.air)
        setQuantFields(txtQuantDrumSv, txtQuantDrumMin, txtQuantDrumMax, txtQuantDrumStep, chkQuantDrumAction, quantifiers.drum)
        setQuantFields(txtQuantDamperSv, txtQuantDamperMin, txtQuantDamperMax, txtQuantDamperStep, chkQuantDamperAction, quantifiers.damper)
        setQuantFields(txtQuantBurnerSv, txtQuantBurnerMin, txtQuantBurnerMax, txtQuantBurnerStep, chkQuantBurnerAction, quantifiers.burner)

        btnResetColors.setOnAction {
            val d = ChartColors()
            txtColorLiveBt.text = d.liveBt
            txtColorLiveEt.text = d.liveEt
            txtColorLiveRorBt.text = d.liveRorBt
            txtColorLiveRorEt.text = d.liveRorEt
            txtColorRefBt.text = d.refBt
            txtColorRefEt.text = d.refEt
            txtColorRefAlpha.text = d.refAlpha.toString()
            sldRefAlpha.value = d.refAlpha.toDouble()
            setColorPickerFromHex(colorLiveBt, d.liveBt)
            setColorPickerFromHex(colorLiveEt, d.liveEt)
            setColorPickerFromHex(colorLiveRorBt, d.liveRorBt)
            setColorPickerFromHex(colorLiveRorEt, d.liveRorEt)
            setColorPickerFromHex(colorRefBt, d.refBt)
            setColorPickerFromHex(colorRefEt, d.refEt)
        }

        btnRefreshPorts.setOnAction { refreshSerialPorts() }
        btnPortsConfig.setOnAction { openPortsConfigDialog() }
        btnAxesConfig.setOnAction { openAxesConfigDialog() }
        btnEventButtons.setOnAction { openEventButtonsDialog() }
        btnTestConnection.setOnAction { runTestConnection() }
        updatePortFieldsVisibility()
        updateTransportCombo()
        cmbTransport.value = when {
            settings.machineConfig.machineType == MachineType.BESCA && settings.machineConfig.transport == Transport.TCP -> "TCP (Ethernet)"
            settings.machineConfig.machineType == MachineType.DIEDRICH && settings.machineConfig.transport == Transport.PHIDGET -> "Phidget 1048 (USB)"
            else -> cmbTransport.items.firstOrNull()
        }
        updateTransportFieldsVisibility()
        setupConnectionTooltips()

        cmbSource.valueProperty().addListener { _, _, _ ->
            updatePortFieldsVisibility()
            updateTransportCombo()
            updateTransportFieldsVisibility()
        }

        btnLoadPreset.setOnAction { loadSelectedPreset() }
        btnSavePreset.setOnAction { saveCurrentAsPreset() }

        btnBrowse.setOnAction {
            val chooser = DirectoryChooser()
            chooser.title = "Выберите папку сохранения"
            val dir = chooser.showDialog((btnBrowse.scene?.window) as? Stage)
            dir?.let { txtSavePath.text = it.absolutePath }
        }

        btnSave.setOnAction {
            if (chkVerifyBeforeSave.isSelected) {
                val mc = buildMachineConfigFromFields()
                scope.launch {
                    val result = ConnectionTester.test(mc)
                    Platform.runLater {
                        result.fold(
                            onSuccess = { performSave() },
                            onFailure = { e ->
                                val alert = Alert(Alert.AlertType.CONFIRMATION)
                                alert.title = "Проверка подключения"
                                alert.headerText = "Не удалось подключиться: ${e.message}"
                                alert.contentText = "Всё равно сохранить настройки?"
                                if (alert.showAndWait().orElse(null) == javafx.scene.control.ButtonType.OK) performSave()
                            }
                        )
                    }
                }
                return@setOnAction
            }
            performSave()
        }

        btnCancel.setOnAction { closeWindow() }
    }

    private fun parseQuantifierRow(
        sourceCombo: ComboBox<String>,
        sv: TextField,
        min: TextField,
        max: TextField,
        step: TextField,
        action: CheckBox
    ): EventQuantifierConfig {
        val src = when (sourceCombo.value?.uppercase()) {
            "ET" -> QuantifierSource.ET
            "BT" -> QuantifierSource.BT
            else -> QuantifierSource.NONE
        }
        return EventQuantifierConfig(
            source = src,
            sv = sv.text.toDoubleOrNull() ?: 0.0,
            min = min.text.toIntOrNull()?.coerceIn(0, 10000) ?: 0,
            max = max.text.toIntOrNull()?.coerceIn(0, 10000) ?: 100,
            step = step.text.toDoubleOrNull()?.coerceIn(0.1, 1000.0) ?: 5.0,
            actionEnabled = action.isSelected
        )
    }

    private fun buildEventQuantifiersFromFields(): EventQuantifiersConfig {
        return EventQuantifiersConfig(
            air = parseQuantifierRow(cmbQuantAirSource, txtQuantAirSv, txtQuantAirMin, txtQuantAirMax, txtQuantAirStep, chkQuantAirAction),
            drum = parseQuantifierRow(cmbQuantDrumSource, txtQuantDrumSv, txtQuantDrumMin, txtQuantDrumMax, txtQuantDrumStep, chkQuantDrumAction),
            damper = parseQuantifierRow(cmbQuantDamperSource, txtQuantDamperSv, txtQuantDamperMin, txtQuantDamperMax, txtQuantDamperStep, chkQuantDamperAction),
            burner = parseQuantifierRow(cmbQuantBurnerSource, txtQuantBurnerSv, txtQuantBurnerMin, txtQuantBurnerMax, txtQuantBurnerStep, chkQuantBurnerAction)
        )
    }

    private fun buildMachineConfigFromFields(): MachineConfig {
        val settings = SettingsManager.load()
        val machineType = when (cmbSource.value) {
            "Besca" -> MachineType.BESCA
            "Diedrich" -> MachineType.DIEDRICH
            else -> MachineType.SIMULATOR
        }
        val transport = when {
            machineType == MachineType.BESCA && cmbTransport.value == "TCP (Ethernet)" -> Transport.TCP
            machineType == MachineType.DIEDRICH && cmbTransport.value == "Phidget 1048 (USB)" -> Transport.PHIDGET
            else -> Transport.SERIAL
        }
        val portRaw = (cmbPort.value ?: cmbPort.editor?.text)?.trim().orEmpty()
        val port = (if (portRaw.startsWith("Другой: ")) portRaw.removePrefix("Другой: ").trim() else portRaw).ifBlank { "COM4" }
        val pollingIntervalMs = (txtSamplingIntervalSec.text.toDoubleOrNull()?.times(1000)?.toLong()?.coerceIn(100L, 3000L)) ?: 1000L
        val advanced = portsAdvanced ?: PortsAdvanced(
            modbusType = settings.machineConfig.modbusTransportType,
            byteSize = settings.machineConfig.byteSize,
            parity = settings.machineConfig.parity,
            stopBits = settings.machineConfig.stopBits,
            serialTimeoutSec = settings.machineConfig.serialTimeoutSec,
            ipTimeoutSec = settings.machineConfig.ipTimeoutSec,
            ipRetries = settings.machineConfig.ipRetries
        )
        // Для TCP: если Host пуст, использовать первый IP из "IP для сканирования" (fallback)
        val hostForTcp = txtHost.text?.trim()?.takeIf { it.isNotBlank() }
            ?: txtDiscoveryHosts.text?.trim()?.split(",")?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }
        return settings.machineConfig.copy(
            machineType = machineType,
            transport = transport,
            modbusTransportType = if (transport == Transport.TCP) ModbusTransportType.TCP else advanced.modbusType,
            host = hostForTcp,
            tcpPort = txtTcpPort.text.toIntOrNull() ?: 502,
            port = port,
            baudRate = txtBaudRate.text.toIntOrNull() ?: 9600,
            byteSize = advanced.byteSize,
            parity = advanced.parity,
            stopBits = advanced.stopBits,
            serialTimeoutSec = advanced.serialTimeoutSec,
            ipTimeoutSec = advanced.ipTimeoutSec,
            ipRetries = advanced.ipRetries,
            slaveId = txtSlaveId.text.toIntOrNull() ?: 1,
            btRegister = txtBtRegister.text.toIntOrNull() ?: 6,
            etRegister = txtEtRegister.text.toIntOrNull() ?: 7,
            divisionFactor = txtDivider.text.toDoubleOrNull() ?: 10.0,
            phidgetEtChannel = txtPhidgetEtChannel.text.toIntOrNull()?.coerceIn(1, 4) ?: 1,
            phidgetBtChannel = txtPhidgetBtChannel.text.toIntOrNull()?.coerceIn(1, 4) ?: 2,
            pollingIntervalMs = pollingIntervalMs,
            modbusInputs = portsModbusInputs ?: settings.machineConfig.modbusInputs,
            modbusPid = portsModbusPid ?: settings.machineConfig.modbusPid,
            eventCommands = buildEventCommandsFromFields()
        )
    }

    private fun buildEventCommandsFromFields(): Map<String, String> {
        eventButtonsDialogResult?.let { return it.eventCommands }
        val map = mutableMapOf<String, String>()
        listOf(
            "CHARGE" to (txtEventCharge.text?.trim() ?: ""),
            "DROP" to (txtEventDrop.text?.trim() ?: ""),
            "DRY_END" to (txtEventDryEnd.text?.trim() ?: ""),
            "FC_START" to (txtEventFcStart.text?.trim() ?: ""),
            "COOL_END" to (txtEventCoolEnd.text?.trim() ?: "")
        ).forEach { (k, v) -> if (v.isNotEmpty()) map[k] = v }
        return map
    }

    private fun performSave() {
            val settings = SettingsManager.load()
            val machineType = when (cmbSource.value) {
                "Besca" -> MachineType.BESCA
                "Diedrich" -> MachineType.DIEDRICH
                else -> MachineType.SIMULATOR
            }
            val transport = when {
                machineType == MachineType.BESCA && cmbTransport.value == "TCP (Ethernet)" -> Transport.TCP
                machineType == MachineType.DIEDRICH && cmbTransport.value == "Phidget 1048 (USB)" -> Transport.PHIDGET
                else -> Transport.SERIAL
            }
            // Для TCP: если Host пуст, использовать первый IP из "IP для сканирования" (fallback)
            val host = txtHost.text?.trim()?.takeIf { it.isNotBlank() }
                ?: txtDiscoveryHosts.text?.trim()?.split(",")?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }
            val tcpPort = txtTcpPort.text.toIntOrNull() ?: 502
            val portRaw = (cmbPort.value ?: cmbPort.editor?.text)?.trim().orEmpty()
            val port = (if (portRaw.startsWith("Другой: ")) portRaw.removePrefix("Другой: ").trim() else portRaw).ifBlank { "COM4" }
            val baudRate = txtBaudRate.text.toIntOrNull() ?: 9600
            val slaveId = txtSlaveId.text.toIntOrNull() ?: 1
            val phidgetEt = txtPhidgetEtChannel.text.toIntOrNull()?.coerceIn(1, 4) ?: 1
            val phidgetBt = txtPhidgetBtChannel.text.toIntOrNull()?.coerceIn(1, 4) ?: 2
            val unit = if (rbFahrenheit.isSelected) "F" else "C"
            val savePath = txtSavePath.text.ifBlank { System.getProperty("user.home") + "/roasts" }

            val def = ChartColors()
            val chartColors = ChartColors(
                liveBt = txtColorLiveBt.text?.trim()?.takeIf { it.isNotBlank() } ?: def.liveBt,
                liveEt = txtColorLiveEt.text?.trim()?.takeIf { it.isNotBlank() } ?: def.liveEt,
                liveRorBt = txtColorLiveRorBt.text?.trim()?.takeIf { it.isNotBlank() } ?: def.liveRorBt,
                liveRorEt = txtColorLiveRorEt.text?.trim()?.takeIf { it.isNotBlank() } ?: def.liveRorEt,
                refBt = txtColorRefBt.text?.trim()?.takeIf { it.isNotBlank() } ?: def.refBt,
                refEt = txtColorRefEt.text?.trim()?.takeIf { it.isNotBlank() } ?: def.refEt,
                refRorBt = def.refRorBt,
                refRorEt = def.refRorEt,
                refAlpha = sldRefAlpha.value.toInt().coerceIn(0, 255)
            )
            val defC = ChartConfig()
            val baseAxes = axesConfigDraft ?: settings.chartConfig
            val chartConfig = baseAxes.copy(
                timeRangeMin = txtChartTimeRange.text.toIntOrNull()?.coerceIn(1, 60) ?: defC.timeRangeMin,
                showGrid = chkChartShowGrid.isSelected,
                showBt = chkChartShowBt.isSelected,
                showEt = chkChartShowEt.isSelected,
                showRorBt = chkChartShowRorBt.isSelected,
                showRorEt = chkChartShowRorEt.isSelected,
                showReferenceCurves = chkChartShowReferenceCurves.isSelected,
                showReferenceEvents = chkChartShowReferenceEvents.isSelected,
                showPhaseStrips = chkChartShowPhaseStrips.isSelected,
                btLineWidth = txtChartBtLineWidth.text.toFloatOrNull()?.coerceIn(0.5f, 5f) ?: defC.btLineWidth,
                etLineWidth = txtChartEtLineWidth.text.toFloatOrNull()?.coerceIn(0.5f, 5f) ?: defC.etLineWidth,
                rorLineWidth = txtChartRorLineWidth.text.toFloatOrNull()?.coerceIn(0.5f, 5f) ?: defC.rorLineWidth,
                refLineWidth = txtChartRefLineWidth.text.toFloatOrNull()?.coerceIn(0.5f, 5f) ?: defC.refLineWidth,
                backgroundColor = txtChartBackground.text?.trim()?.takeIf { it.isNotBlank() } ?: defC.backgroundColor,
                gridColor = txtChartGridColor.text?.trim()?.takeIf { it.isNotBlank() } ?: defC.gridColor
            )

            val pollingIntervalMs = (txtSamplingIntervalSec.text.toDoubleOrNull()?.times(1000)?.toLong()?.coerceIn(100L, 3000L)) ?: 1000L
            val advanced = portsAdvanced ?: PortsAdvanced(
                modbusType = settings.machineConfig.modbusTransportType,
                byteSize = settings.machineConfig.byteSize,
                parity = settings.machineConfig.parity,
                stopBits = settings.machineConfig.stopBits,
                serialTimeoutSec = settings.machineConfig.serialTimeoutSec,
                ipTimeoutSec = settings.machineConfig.ipTimeoutSec,
                ipRetries = settings.machineConfig.ipRetries
            )
            val mc = settings.machineConfig.copy(
                machineType = machineType,
                transport = transport,
                modbusTransportType = if (transport == Transport.TCP) ModbusTransportType.TCP else advanced.modbusType,
                host = host,
                tcpPort = tcpPort,
                port = port,
                baudRate = baudRate,
                byteSize = advanced.byteSize,
                parity = advanced.parity,
                stopBits = advanced.stopBits,
                serialTimeoutSec = advanced.serialTimeoutSec,
                ipTimeoutSec = advanced.ipTimeoutSec,
                ipRetries = advanced.ipRetries,
                slaveId = slaveId,
                btRegister = txtBtRegister.text.toIntOrNull() ?: 6,
                etRegister = txtEtRegister.text.toIntOrNull() ?: 7,
                divisionFactor = txtDivider.text.toDoubleOrNull() ?: 10.0,
                phidgetEtChannel = phidgetEt,
                phidgetBtChannel = phidgetBt,
                pollingIntervalMs = pollingIntervalMs,
                modbusInputs = portsModbusInputs ?: settings.machineConfig.modbusInputs,
                modbusPid = portsModbusPid ?: settings.machineConfig.modbusPid,
                eventCommands = buildEventCommandsFromFields()
            )
            val discoveryHosts = txtDiscoveryHosts.text?.trim()?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.takeIf { it.isNotEmpty() }
            val newSettings = AppSettings(
                machineConfig = mc,
                unit = unit,
                savePath = savePath,
                serverBaseUrl = com.rdr.roast.app.ServerConfig.API_BASE_URL,
                serverToken = settings.serverToken,
                serverRefreshToken = settings.serverRefreshToken,
                serverRememberEmail = settings.serverRememberEmail,
                chartColors = chartColors,
                chartConfig = chartConfig,
                layoutDividerCenterRight = settings.layoutDividerCenterRight,
                layoutDividerReferenceChannels = settings.layoutDividerReferenceChannels,
                betweenBatchProtocolEnabled = chkBetweenBatchProtocol.isSelected,
                autoDetectRoaster = chkAutoDetectRoaster.isSelected,
                discoveryTcpHosts = discoveryHosts,
                rememberLastDetectedRoaster = chkRememberLastRoaster.isSelected,
                lastDetectedConfig = settings.lastDetectedConfig,
                roastPropertiesTitle = settings.roastPropertiesTitle,
                roastPropertiesReferenceId = settings.roastPropertiesReferenceId,
                roastPropertiesStockId = settings.roastPropertiesStockId,
                roastPropertiesBlendId = settings.roastPropertiesBlendId,
                roastPropertiesWeightInKg = settings.roastPropertiesWeightInKg,
                roastPropertiesWeightOutKg = settings.roastPropertiesWeightOutKg,
                roastPropertiesBeansNotes = settings.roastPropertiesBeansNotes,
                sliderStepConfig = eventButtonsDialogResult?.sliderStepConfig ?: settings.sliderStepConfig,
                commentsConfig = settings.commentsConfig,
                eventQuantifiers = eventButtonsDialogResult?.eventQuantifiers ?: buildEventQuantifiersFromFields(),
                customButtons = eventButtonsDialogResult?.customButtons ?: settings.customButtons,
                eventButtonsConfig = eventButtonsDialogResult?.eventButtonsConfig ?: settings.eventButtonsConfig,
                eventSliders = eventButtonsDialogResult?.eventSliders ?: settings.eventSliders,
                deviceAssignment = deviceAssignmentConfig ?: settings.deviceAssignment,
                rorSmoothing = if (::cmbRorSmoothing.isInitialized) {
                    when (cmbRorSmoothing.value) {
                        "Sensitive" -> RorSmoothing.SENSITIVE
                        "Noise Resistant" -> RorSmoothing.NOISE_RESISTANT
                        else -> RorSmoothing.RECOMMENDED
                    }
                } else settings.rorSmoothing
            )
            savedSettings = newSettings
            SettingsManager.save(newSettings)
            closeWindow()
    }

    private fun runTestConnection() {
        val mc = buildMachineConfigFromFields()
        btnTestConnection.isDisable = true
        scope.launch {
            val result = ConnectionTester.test(mc)
            Platform.runLater {
                btnTestConnection.isDisable = false
                result.fold(
                    onSuccess = { state ->
                        val name = (state as? ConnectionState.Connected)?.deviceName ?: "Устройство"
                        Alert(Alert.AlertType.INFORMATION).apply {
                            title = "Проверка подключения"
                            headerText = "Подключено"
                            contentText = "Успешное подключение: $name"
                        }.showAndWait()
                    },
                    onFailure = { e ->
                        Alert(Alert.AlertType.ERROR).apply {
                            title = "Проверка подключения"
                            headerText = "Ошибка подключения"
                            contentText = e.message ?: "Не удалось подключиться"
                        }.showAndWait()
                    }
                )
            }
        }
    }

    /**
     * Opens Artisan-style "Ports Configuration" dialog. On OK, applies MODBUS tab values
     * to the Connection tab (caller-applies pattern like Artisan main.py after comportDlg).
     */
    private fun openPortsConfigDialog() {
        val url = SettingsController::class.java.getResource("/com/rdr/roast/ui/PortsConfigView.fxml") ?: return
        val loader = FXMLLoader(url)
        val root = loader.load() as? javafx.scene.Parent ?: return
        val portsController = loader.getController<PortsConfigController>() ?: return
        val stage = Stage().apply {
            title = "Device Assignment"
            scene = Scene(root)
            isResizable = true
            initModality(javafx.stage.Modality.APPLICATION_MODAL)
            initOwner(btnPortsConfig.scene?.window)
        }
        stage.showAndWait()
        if (!portsController.applied) return
        // Apply MODBUS tab to Connection tab (Artisan: main.py reads dialog and sets modbus.host/port etc.)
        val typeIndex = portsController.getModbusTypeIndex()
        val isTcp = typeIndex == 3
        cmbSource.selectionModel.select("Besca")
        updateTransportCombo()
        cmbTransport.selectionModel.select(if (isTcp) "TCP (Ethernet)" else "Serial (COM port)")
        updateTransportFieldsVisibility()
        txtHost.text = portsController.getModbusHost()
        txtTcpPort.text = portsController.getModbusPort().toString()
        val modbusPort = portsController.getModbusComPort()
        if (modbusPort in cmbPort.items) cmbPort.selectionModel.select(modbusPort)
        else {
            cmbPort.items.add(modbusPort)
            cmbPort.selectionModel.select(modbusPort)
        }
        txtBaudRate.text = portsController.getModbusBaudRate().toString()
        txtSlaveId.text = portsController.getSlaveId().toString()
        portsAdvanced = PortsAdvanced(
            modbusType = when (typeIndex) {
                1 -> ModbusTransportType.SERIAL_ASCII
                3 -> ModbusTransportType.TCP
                4 -> ModbusTransportType.UDP
                else -> ModbusTransportType.SERIAL_RTU
            },
            byteSize = portsController.getModbusByteSize(),
            parity = when (portsController.getModbusParity()) {
                "O" -> SerialParity.ODD
                "E" -> SerialParity.EVEN
                else -> SerialParity.NONE
            },
            stopBits = portsController.getModbusStopbits(),
            serialTimeoutSec = portsController.getModbusTimeout(),
            ipTimeoutSec = portsController.getModbusTimeout(),
            ipRetries = 1
        )
        portsModbusInputs = portsController.getModbusInputs()
        portsModbusPid = portsController.getModbusPid()
        val da = portsController.getDeviceAssignmentConfig()
        deviceAssignmentConfig = da
        txtPhidgetEtChannel.text = da.phidgets.etChannel.toString()
        txtPhidgetBtChannel.text = da.phidgets.btChannel.toString()
    }

    /** Opens Event Buttons dialog. On OK/Apply stores state; caller persists on Save. */
    private fun openEventButtonsDialog() {
        val url = SettingsController::class.java.getResource("/com/rdr/roast/ui/EventButtonsView.fxml") ?: return
        val loader = FXMLLoader(url)
        val root = loader.load() as? javafx.scene.Parent ?: return
        val controller = loader.getController<EventButtonsController>() ?: return
        val settings = SettingsManager.load()
        val merged = if (eventButtonsDialogResult == null) {
            settings
        } else {
            settings.copy(
                machineConfig = settings.machineConfig.copy(eventCommands = eventButtonsDialogResult!!.eventCommands),
                sliderStepConfig = eventButtonsDialogResult!!.sliderStepConfig,
                eventQuantifiers = eventButtonsDialogResult!!.eventQuantifiers,
                customButtons = eventButtonsDialogResult!!.customButtons,
                eventButtonsConfig = eventButtonsDialogResult!!.eventButtonsConfig,
                eventSliders = eventButtonsDialogResult!!.eventSliders
            )
        }
        controller.loadFrom(merged)
        val stage = Stage().apply {
            title = "Event Buttons"
            scene = Scene(root)
            isResizable = true
            initModality(javafx.stage.Modality.APPLICATION_MODAL)
            initOwner(btnEventButtons.scene?.window)
        }
        stage.showAndWait()
        if (controller.applied) {
            controller.getResult()?.let { eventButtonsDialogResult = it }
        }
    }

    /** Opens separate Artisan-style Axes dialog (Config -> Axes path). */
    private fun openAxesConfigDialog() {
        val url = SettingsController::class.java.getResource("/com/rdr/roast/ui/AxesConfigView.fxml") ?: return
        val loader = FXMLLoader(url)
        val root = loader.load() as? javafx.scene.Parent ?: return
        val controller = loader.getController<AxesConfigController>() ?: return
        controller.loadFrom(axesConfigDraft ?: SettingsManager.load().chartConfig)
        val stage = Stage().apply {
            title = "Axes"
            scene = Scene(root)
            isResizable = false
            initModality(javafx.stage.Modality.APPLICATION_MODAL)
            initOwner(btnAxesConfig.scene?.window)
        }
        stage.showAndWait()
        if (controller.applied) {
            controller.getResult()?.let { axesConfigDraft = it }
        }
    }

    private fun setupConnectionTooltips() {
        cmbTransport.tooltip = Tooltip("Способ подключения: COM-порт (Modbus RTU), TCP или Phidget USB")
        txtHost.tooltip = Tooltip("IP-адрес ростера при подключении по TCP (Ethernet)")
        txtTcpPort.tooltip = Tooltip("Порт Modbus TCP (обычно 502)")
        cmbPort.tooltip = Tooltip("Последовательный порт (COM). Нажмите «Обновить порты» для обновления списка.")
        txtBaudRate.tooltip = Tooltip("Скорость порта (например 9600, 19200)")
        txtSlaveId.tooltip = Tooltip("Modbus Slave ID устройства (обычно 1)")
        txtPhidgetEtChannel.tooltip = Tooltip("Номер канала Phidget 1048 для датчика ET (Environment Temperature)")
        txtPhidgetBtChannel.tooltip = Tooltip("Номер канала Phidget 1048 для датчика BT (Bean Temperature)")
        chkBetweenBatchProtocol.tooltip = Tooltip("When enabled, after you stop a roast the Between Batch timer and BT/ET curves are shown during pre- and post-roast (up to 15 min). Cropster: Roasting \u2192 Interface.")
        chkAutoDetectRoaster.tooltip = Tooltip("При нажатии «Подключить» приложение попробует найти ростер по сети и COM-портам")
        chkRememberLastRoaster.tooltip = Tooltip("При следующем подключении сначала пробовать последний успешно определённый ростер")
        txtDiscoveryHosts.tooltip = Tooltip("IP-адреса для сканирования (Modbus TCP). Пусто = 10.0.0.9, 192.168.1.100, 127.0.0.1")
    }

    private fun updatePortFieldsVisibility() {
        val isSimulator = cmbSource.value == "Simulator"
        portFieldsContainer.isVisible = !isSimulator
        portFieldsContainer.isManaged = !isSimulator
    }

    private fun updateTransportCombo() {
        val items = when (cmbSource.value) {
            "Besca" -> listOf("Serial (COM port)", "TCP (Ethernet)")
            "Diedrich" -> listOf("Serial (Modbus)", "Phidget 1048 (USB)")
            else -> listOf("Serial (COM port)")
        }
        cmbTransport.items.setAll(items)
        if (cmbTransport.value !in items) cmbTransport.value = items.firstOrNull()
    }

    private fun updateTransportFieldsVisibility() {
        val isBescaTcp = cmbSource.value == "Besca" && cmbTransport.value == "TCP (Ethernet)"
        val isDiedrichPhidget = cmbSource.value == "Diedrich" && cmbTransport.value == "Phidget 1048 (USB)"
        lblHost.isVisible = isBescaTcp
        lblHost.isManaged = isBescaTcp
        txtHost.isVisible = isBescaTcp
        txtHost.isManaged = isBescaTcp
        lblTcpPort.isVisible = isBescaTcp
        lblTcpPort.isManaged = isBescaTcp
        txtTcpPort.isVisible = isBescaTcp
        txtTcpPort.isManaged = isBescaTcp
        lblComPort.isVisible = !isDiedrichPhidget
        lblComPort.isManaged = !isDiedrichPhidget
        cmbPort.isVisible = !isDiedrichPhidget
        cmbPort.isManaged = !isDiedrichPhidget
        btnRefreshPorts.isVisible = !isDiedrichPhidget
        btnRefreshPorts.isManaged = !isDiedrichPhidget
        lblBaudRate.isVisible = !isDiedrichPhidget
        lblBaudRate.isManaged = !isDiedrichPhidget
        lblSlaveId.isVisible = !isDiedrichPhidget
        lblSlaveId.isManaged = !isDiedrichPhidget
        txtBaudRate.isVisible = !isDiedrichPhidget
        txtBaudRate.isManaged = !isDiedrichPhidget
        txtSlaveId.isVisible = !isDiedrichPhidget
        txtSlaveId.isManaged = !isDiedrichPhidget
        val isModbus = !isDiedrichPhidget
        lblBtRegister.isVisible = isModbus
        lblBtRegister.isManaged = isModbus
        txtBtRegister.isVisible = isModbus
        txtBtRegister.isManaged = isModbus
        lblEtRegister.isVisible = isModbus
        lblEtRegister.isManaged = isModbus
        txtEtRegister.isVisible = isModbus
        txtEtRegister.isManaged = isModbus
        lblDivider.isVisible = isModbus
        lblDivider.isManaged = isModbus
        txtDivider.isVisible = isModbus
        txtDivider.isManaged = isModbus
        lblPhidgetEt.isVisible = isDiedrichPhidget
        lblPhidgetEt.isManaged = isDiedrichPhidget
        txtPhidgetEtChannel.isVisible = isDiedrichPhidget
        txtPhidgetEtChannel.isManaged = isDiedrichPhidget
        lblPhidgetBt.isVisible = isDiedrichPhidget
        lblPhidgetBt.isManaged = isDiedrichPhidget
        txtPhidgetBtChannel.isVisible = isDiedrichPhidget
        txtPhidgetBtChannel.isManaged = isDiedrichPhidget
        updateEtBtSourceLabel()
    }

    private fun updateEtBtSourceLabel() {
        lblEtBtSource.text = when {
            cmbSource.value == "Simulator" -> "ET/BT source: Simulator"
            cmbSource.value == "Diedrich" && cmbTransport.value == "Phidget 1048 (USB)" -> "ET/BT source: Phidget"
            cmbSource.value == "Besca" -> "ET/BT source: Modbus (Host, Port, Registers from below)"
            cmbSource.value == "Diedrich" -> "ET/BT source: Modbus (Serial)"
            else -> "ET/BT source: —"
        }
    }

    private fun refreshSerialPorts() {
        val portNames = SerialPort.getCommPorts().map { it.systemPortName }
        val current = (cmbPort.value ?: cmbPort.editor?.text)?.trim().orEmpty()
        val portOnly = if (current.startsWith("Другой: ")) current.removePrefix("Другой: ").trim() else current
        cmbPort.items.setAll(portNames)
        if (portOnly.isNotBlank()) {
            if (portOnly in portNames) cmbPort.value = portOnly
            else if (portOnly !in portNames) {
                cmbPort.items.add("Другой: $portOnly")
                cmbPort.value = "Другой: $portOnly"
            }
        }
    }

    private fun refreshPresetList() {
        val presets = SettingsManager.loadPresets()
        cmbPreset.items.setAll(presets.map { it.name })
        cmbPreset.value = null
    }

    private fun loadSelectedPreset() {
        val name = cmbPreset.value ?: return
        val presets = SettingsManager.loadPresets()
        val preset = presets.find { it.name == name } ?: return
        val c = preset.config
        cmbSource.value = when (c.machineType) {
            MachineType.SIMULATOR -> "Simulator"
            MachineType.BESCA -> "Besca"
            MachineType.DIEDRICH -> "Diedrich"
        }
        updateTransportCombo()
        cmbTransport.value = when {
            c.machineType == MachineType.BESCA && c.transport == Transport.TCP -> "TCP (Ethernet)"
            c.machineType == MachineType.DIEDRICH && c.transport == Transport.PHIDGET -> "Phidget 1048 (USB)"
            else -> cmbTransport.items.firstOrNull()
        }
        txtHost.text = c.host ?: ""
        txtTcpPort.text = c.tcpPort.toString()
        cmbPort.value = c.port
        txtBaudRate.text = c.baudRate.toString()
        txtSlaveId.text = c.slaveId.toString()
        txtPhidgetEtChannel.text = c.phidgetEtChannel.toString()
        txtPhidgetBtChannel.text = c.phidgetBtChannel.toString()
        txtSamplingIntervalSec.text = (c.pollingIntervalMs / 1000.0).toString()
        updateTransportFieldsVisibility()
    }

    private fun saveCurrentAsPreset() {
        val dialog = TextInputDialog("")
        dialog.title = "Сохранить профиль"
        dialog.headerText = "Введите имя профиля подключения"
        dialog.contentText = "Имя:"
        val name = dialog.showAndWait().orElse(null)?.trim() ?: return
        if (name.isBlank()) return
        val settings = SettingsManager.load()
        val machineType = when (cmbSource.value) {
            "Besca" -> MachineType.BESCA
            "Diedrich" -> MachineType.DIEDRICH
            else -> MachineType.SIMULATOR
        }
        val transport = when {
            machineType == MachineType.BESCA && cmbTransport.value == "TCP (Ethernet)" -> Transport.TCP
            machineType == MachineType.DIEDRICH && cmbTransport.value == "Phidget 1048 (USB)" -> Transport.PHIDGET
            else -> Transport.SERIAL
        }
        val pollingIntervalMs = (txtSamplingIntervalSec.text.toDoubleOrNull()?.times(1000)?.toLong()?.coerceIn(100L, 3000L)) ?: 1000L
        val config = settings.machineConfig.copy(
            machineType = machineType,
            transport = transport,
            host = txtHost.text?.trim()?.takeIf { it.isNotBlank() },
            tcpPort = txtTcpPort.text.toIntOrNull() ?: 502,
            port = (run {
                val raw = (cmbPort.value ?: cmbPort.editor?.text)?.trim().orEmpty()
                if (raw.startsWith("Другой: ")) raw.removePrefix("Другой: ").trim() else raw
            }).ifBlank { "COM4" },
            baudRate = txtBaudRate.text.toIntOrNull() ?: 9600,
            slaveId = txtSlaveId.text.toIntOrNull() ?: 1,
            phidgetEtChannel = txtPhidgetEtChannel.text.toIntOrNull()?.coerceIn(1, 4) ?: 1,
            phidgetBtChannel = txtPhidgetBtChannel.text.toIntOrNull()?.coerceIn(1, 4) ?: 2,
            pollingIntervalMs = pollingIntervalMs
        )
        var presets = SettingsManager.loadPresets().toMutableList()
        presets.removeAll { it.name == name }
        presets.add(ConnectionPreset(name, config))
        SettingsManager.savePresets(presets)
        refreshPresetList()
        cmbPreset.value = name
        Alert(Alert.AlertType.INFORMATION).apply {
            this.title = "Профиль сохранён"
            headerText = "Профиль «$name» сохранён."
        }.showAndWait()
    }

    private fun setColorPickerFromHex(picker: ColorPicker, hex: String?) {
        if (!hex.isNullOrBlank()) {
            try {
                picker.value = Color.web(hex.trim())
            } catch (_: Exception) { }
        }
    }

    private fun colorToHex(c: Color): String {
        return String.format("#%02x%02x%02x",
            (c.red * 255).toInt().coerceIn(0, 255),
            (c.green * 255).toInt().coerceIn(0, 255),
            (c.blue * 255).toInt().coerceIn(0, 255))
    }

    private fun bindColorPickerToHex(picker: ColorPicker, field: TextField) {
        picker.valueProperty().addListener { _, _, c -> field.text = colorToHex(c) }
        field.focusedProperty().addListener { _, _, focused ->
            if (!focused) field.text?.trim()?.takeIf { it.isNotBlank() }?.let { setColorPickerFromHex(picker, it) }
        }
    }

    private fun applyAndSaveTheme() {
        ThemeSupport.displayNameToThemeId(cmbTheme.value ?: return)?.let { id ->
            ThemeSupport.applyTheme(id)
            ThemeSupport.saveTheme(id)
        }
        saveAppearanceFromFields()
        applyAppearanceToMainScene()
    }

    private fun saveAppearanceFromFields() {
        cmbScale.value?.let { AppearanceSupport.saveScale(AppearanceSupport.scaleFromDisplay(it)) }
        cmbFontSize.value?.let { AppearanceSupport.saveFontSize(AppearanceSupport.fontSizeFromDisplay(it)) }
        cmbDensity.value?.let { AppearanceSupport.saveDensity(AppearanceSupport.densityFromDisplay(it)) }
        AppearanceSupport.saveRadius(sldRadius.value.toInt().coerceIn(0, 20))
        AppearanceSupport.saveAccentColor(AppearanceSupport.colorToHex(colorAccent.value))
        AppearanceSupport.savePanelBackground(AppearanceSupport.colorToHex(colorPanelBg.value))
    }

    private fun applyAppearanceToMainScene() {
        val stage = btnApplyTheme.scene?.window as? Stage ?: return
        stage.scene?.let { AppearanceSupport.applyToScene(it) }
    }

    private fun restoreAppearanceDefaults() {
        ThemeSupport.saveTheme("CupertinoLight")
        ThemeSupport.applyTheme("CupertinoLight")
        AppearanceSupport.restoreDefaults()
        cmbTheme.value = "Cupertino Light"
        cmbScale.value = "100%"
        cmbFontSize.value = "Обычный (14px)"
        cmbDensity.value = "Обычный"
        sldRadius.value = 10.0
        lblRadiusValue.text = "10px"
        colorAccent.value = Color.web("#E8896A")
        colorPanelBg.value = Color.web("#f5f5f5")
        AppearanceSupport.saveAccentColor("")
        AppearanceSupport.savePanelBackground("#f5f5f5")
        applyAppearanceToMainScene()
    }

    private fun closeWindow() {
        val callback = onCloseDrawer
        if (callback != null) {
            callback(this)
        } else {
            (btnSave.scene?.window as? Stage)?.close()
        }
    }
}
