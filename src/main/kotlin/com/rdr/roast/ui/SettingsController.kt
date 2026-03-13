package com.rdr.roast.ui

import com.rdr.roast.app.AppSettings
import com.rdr.roast.app.ChartConfig
import com.rdr.roast.app.RoastPhasesConfig
import com.rdr.roast.app.ChartColors
import com.rdr.roast.app.CurvesConfig
import com.rdr.roast.ui.graph.GraphAxesTabController
import com.rdr.roast.ui.graph.GraphAnalyzeTabController
import com.rdr.roast.ui.graph.GraphFiltersTabController
import com.rdr.roast.ui.graph.GraphMathTabController
import com.rdr.roast.ui.graph.GraphPlotterTabController
import com.rdr.roast.ui.graph.GraphRoRTabController
import com.rdr.roast.ui.graph.GraphUiTabController
import com.rdr.roast.app.ConnectionPreset
import com.rdr.roast.app.EventQuantifierConfig
import com.rdr.roast.app.EventQuantifiersConfig
import com.rdr.roast.app.QuantifierSource
import com.rdr.roast.app.ConnectionTester
import com.rdr.roast.app.DeviceAssignmentConfig
import com.rdr.roast.app.MachineConfig
import com.rdr.roast.app.MachineType
import com.rdr.roast.app.MachinePresetApplier
import com.rdr.roast.app.MachinePresetEntry
import com.rdr.roast.app.MachinePresetRegistry
import com.rdr.roast.app.RorSmoothing
import com.rdr.roast.app.ModbusInputConfig
import com.rdr.roast.app.ModbusPidConfig
import com.rdr.roast.app.ModbusTransportType
import com.rdr.roast.app.AppearanceSupport
import com.rdr.roast.app.SerialParity
import com.rdr.roast.app.SettingsManager
import com.rdr.roast.app.ThemePreset
import com.rdr.roast.app.ThemeService
import com.rdr.roast.app.ThemeSettings
import com.rdr.roast.app.Transport
import com.rdr.roast.driver.ConnectionState
import javafx.animation.Animation
import javafx.animation.FadeTransition
import javafx.animation.KeyFrame
import javafx.animation.KeyValue
import javafx.animation.ParallelTransition
import javafx.animation.Timeline
import javafx.application.Platform
import javafx.beans.property.SimpleDoubleProperty
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.Alert
import javafx.scene.image.Image
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ColorPicker
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.RadioButton
import javafx.scene.control.ScrollPane
import javafx.scene.control.Slider
import javafx.scene.control.TextField
import javafx.scene.control.TextInputDialog
import javafx.scene.control.ToggleButton
import javafx.scene.control.TreeItem
import javafx.scene.control.TreeView
import javafx.scene.control.ToggleGroup
import javafx.scene.control.Tooltip
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.DirectoryChooser
import javafx.stage.Modality
import javafx.stage.Stage
import javafx.event.ActionEvent
import javafx.stage.Window
import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javafx.util.Duration
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid
import org.kordamp.ikonli.javafx.FontIcon

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

    private enum class SettingsSection(
        val title: String,
        val subtitle: String
    ) {
        APPEARANCE(
            title = "Общий вид",
            subtitle = "Управляйте темой, масштабом, плотностью интерфейса и акцентными цветами."
        ),
        CONNECTION(
            title = "Подключение",
            subtitle = "Настройте источник данных, транспорт, автопоиск ростера и профиль подключения."
        ),
        CHART(
            title = "График",
            subtitle = "Настройки кривых по образцу Artisan: RoR, Filters, Plotter, Math, Analyze, UI."
        ),
        COLORS(
            title = "Цвета",
            subtitle = "Точно настройте палитру живых и reference-кривых для читаемого графика."
        ),
        EVENTS(
            title = "События",
            subtitle = "Сконфигурируйте event buttons, команды Modbus и квантайзеры для управляющих событий."
        ),
        ROAST_PHASES(
            title = "Roast Phases",
            subtitle = "Границы фаз Drying, Maillard, Finishing; Auto DRY/FCs, watermarks и Phases LCDs (как в Artisan)."
        )
    }

    /** Set when user clicks Save; MainController uses this to apply and reconnect. */
    var savedSettings: AppSettings? = null
        private set

    /** When set (e.g. when settings are shown in a drawer), called on Save/Cancel instead of closing a stage. */
    var onCloseDrawer: ((SettingsController) -> Unit)? = null
    /** Optional live-preview callback for theme changes in the main window. */
    var onThemePreviewChanged: ((ThemeSettings) -> Unit)? = null

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
    /** Curves tab controllers (RoR, Filters, Plotter, Math, Analyze, UI); set when chart section tabs are loaded. */
    private var rorTabController: GraphRoRTabController? = null
    private var filtersTabController: GraphFiltersTabController? = null
    private var plotterTabController: GraphPlotterTabController? = null
    private var mathTabController: GraphMathTabController? = null
    private var analyzeTabController: GraphAnalyzeTabController? = null
    private var uiTabController: GraphUiTabController? = null
    private var axesTabController: GraphAxesTabController? = null
    private var activeSection: SettingsSection? = null
    private var selectedPresetEntry: MachinePresetEntry? = null
    private var appliedPresetSettings: AppSettings? = null
    private var allTreeEntries: List<MachinePresetEntry> = emptyList()

    private val sidebarWidthExpanded = 220.0
    private var isSidebarExpanded = true
    private val sidebarWidth = SimpleDoubleProperty(sidebarWidthExpanded)
    private var sidebarTimeline: Timeline? = null

    @FXML
    lateinit var machineTree: TreeView<String>

    @FXML
    lateinit var txtMachineFilter: TextField

    @FXML
    lateinit var lblPresetSummary: Label

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
    lateinit var devicesTabsIncludeController: PortsConfigController

    @FXML
    lateinit var portTabsIncludeController: PortsConfigController

    @FXML
    var axesTabIncludeController: GraphAxesTabController? = null

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
    lateinit var colorRefRorEt: ColorPicker

    @FXML
    lateinit var colorRefRorBt: ColorPicker

    @FXML
    lateinit var txtColorRefRorEt: TextField

    @FXML
    lateinit var txtColorRefRorBt: TextField

    @FXML
    lateinit var colorRefExtra1: ColorPicker

    @FXML
    lateinit var colorRefExtra2: ColorPicker

    @FXML
    lateinit var txtColorRefExtra1: TextField

    @FXML
    lateinit var txtColorRefExtra2: TextField

    @FXML
    lateinit var colorGraphCanvas: ColorPicker

    @FXML
    lateinit var colorGraphBackground: ColorPicker

    @FXML
    lateinit var colorGraphTitle: ColorPicker

    @FXML
    lateinit var colorGraphGrid: ColorPicker

    @FXML
    lateinit var colorGraphYLabel: ColorPicker

    @FXML
    lateinit var colorGraphXLabel: ColorPicker

    @FXML
    lateinit var colorGraphMarkers: ColorPicker

    @FXML
    lateinit var colorGraphText: ColorPicker

    @FXML
    lateinit var colorGraphDrying: ColorPicker

    @FXML
    lateinit var colorGraphMaillard: ColorPicker

    @FXML
    lateinit var colorGraphFinishing: ColorPicker

    @FXML
    lateinit var colorGraphCooling: ColorPicker

    @FXML
    lateinit var colorGraphLegendBg: ColorPicker

    @FXML
    lateinit var colorGraphLegendBorder: ColorPicker

    @FXML
    lateinit var btnLcdEtDigits: Button

    @FXML
    lateinit var btnLcdEtBg: Button

    @FXML
    lateinit var btnLcdDeltaEtDigits: Button

    @FXML
    lateinit var btnLcdDeltaEtBg: Button

    @FXML
    lateinit var btnLcdBtDigits: Button

    @FXML
    lateinit var btnLcdBtBg: Button

    @FXML
    lateinit var btnLcdDeltaBtDigits: Button

    @FXML
    lateinit var btnLcdDeltaBtBg: Button

    @FXML
    lateinit var btnLcdTimerDigits: Button

    @FXML
    lateinit var btnLcdTimerBg: Button

    @FXML
    lateinit var btnLcdRampDigits: Button

    @FXML
    lateinit var btnLcdRampBg: Button

    @FXML
    lateinit var btnLcdExtraDigits: Button

    @FXML
    lateinit var btnLcdExtraBg: Button

    @FXML
    lateinit var btnLcdSlowCoolDigits: Button

    @FXML
    lateinit var btnLcdSlowCoolBg: Button

    @FXML
    lateinit var btnLcdBw: Button

    @FXML
    lateinit var btnColorsGrey: Button

    @FXML
    lateinit var btnResetColors: Button

    @FXML
    lateinit var phasesDryMin: TextField
    @FXML
    lateinit var phasesDryMax: TextField
    @FXML
    lateinit var phasesMaillardMax: TextField
    @FXML
    lateinit var phasesFinishMax: TextField
    @FXML
    lateinit var phasesLcdModeDry: ComboBox<String>
    @FXML
    lateinit var phasesLcdModeMaillard: ComboBox<String>
    @FXML
    lateinit var phasesLcdModeFinish: ComboBox<String>
    @FXML
    lateinit var phasesLcdAllFinish: CheckBox
    @FXML
    lateinit var lblPhasesMaillardMin: Label
    @FXML
    lateinit var lblPhasesFinishMin: Label
    @FXML
    lateinit var phasesAutoAdjusted: CheckBox
    @FXML
    lateinit var phasesFromBackground: CheckBox
    @FXML
    lateinit var phasesWatermarks: CheckBox
    @FXML
    lateinit var phasesLcds: CheckBox
    @FXML
    lateinit var phasesAutoDry: CheckBox
    @FXML
    lateinit var phasesAutoFcs: CheckBox
    @FXML
    lateinit var btnRoastPhasesRestoreDefaults: Button

    @FXML
    lateinit var chartCurvesTabPane: javafx.scene.control.TabPane

    @FXML
    lateinit var btnNavAppearance: ToggleButton

    @FXML
    lateinit var btnNavConnection: ToggleButton

    @FXML
    lateinit var btnNavChart: ToggleButton

    @FXML
    lateinit var btnNavColors: ToggleButton

    @FXML
    lateinit var btnNavEvents: ToggleButton

    @FXML
    lateinit var btnNavRoastPhases: ToggleButton

    @FXML
    lateinit var settingsSidebar: VBox

    @FXML
    lateinit var btnSidebarToggle: Button

    @FXML
    lateinit var appearanceSection: ScrollPane

    @FXML
    lateinit var connectionSection: ScrollPane

    @FXML
    lateinit var chartSection: ScrollPane

    @FXML
    lateinit var roastPhasesSection: ScrollPane

    @FXML
    lateinit var colorsSection: ScrollPane

    @FXML
    lateinit var colorsTabPane: javafx.scene.control.TabPane

    @FXML
    lateinit var eventsSection: ScrollPane

    @FXML
    lateinit var lblSettingsHeroEyebrow: Label

    @FXML
    lateinit var lblSettingsHeroTitle: Label

    @FXML
    lateinit var lblSettingsHeroSubtitle: Label

    @FXML
    lateinit var lblThemePreviewTitle: Label

    @FXML
    lateinit var lblThemePreviewMeta: Label

    @FXML
    lateinit var lblThemePreviewMode: Label

    @FXML
    lateinit var lblThemePreviewRadius: Label

    @FXML
    lateinit var themePreviewAccentSwatch: Region

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
    lateinit var btnThemeLight: Button

    @FXML
    lateinit var btnThemeDark: Button

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
        settingsSidebar.prefWidthProperty().bind(sidebarWidth)
        settingsSidebar.minWidthProperty().bind(sidebarWidth)
        settingsSidebar.maxWidthProperty().bind(sidebarWidth)
        decorateNavigationButtons()
        setupSidebarToggle()
        setupSectionNavigation()
        showSection(SettingsSection.APPEARANCE)

        val settings = SettingsManager.load()
        val theme = settings.themeSettings

        // Reuse a single PortsConfig view in two contexts:
        // - Devices tab keeps only device-assignment subtabs
        // - Port tab keeps only port communication subtabs
        devicesTabsIncludeController.configureViewMode(PortsConfigController.ViewMode.DEVICES_ONLY)
        portTabsIncludeController.configureViewMode(PortsConfigController.ViewMode.PORT_ONLY)

        cmbTheme.items.setAll(ThemeService.themeEntries().map { it.second })
        cmbTheme.value = ThemeService.themeIdToDisplayName(theme.atlantafxThemeId) ?: "Cupertino Light"
        cmbTheme.valueProperty().addListener { _, _, _ -> applyThemeDraftToScene() }

        cmbScale.items.setAll(AppearanceSupport.scaleDisplayValues())
        cmbScale.value = AppearanceSupport.displayFromScale(theme.scale)
        cmbScale.valueProperty().addListener { _, _, _ -> applyThemeDraftToScene() }

        cmbFontSize.items.setAll(AppearanceSupport.fontSizeDisplayValues())
        cmbFontSize.value = AppearanceSupport.displayFromFontSize(theme.fontSize)
        cmbFontSize.valueProperty().addListener { _, _, _ -> applyThemeDraftToScene() }

        cmbDensity.items.setAll(AppearanceSupport.densityDisplayValues())
        cmbDensity.value = AppearanceSupport.displayFromDensity(theme.density)
        cmbDensity.valueProperty().addListener { _, _, _ -> applyThemeDraftToScene() }

        if (theme.accentHex.isNotBlank()) {
            try { colorAccent.value = Color.web(theme.accentHex) } catch (_: Exception) { }
        }
        colorAccent.valueProperty().addListener { _, _, _ -> applyThemeDraftToScene() }
        btnAccentDefault.setOnAction {
            val base = buildThemeSettingsFromTab(SettingsManager.load().themeSettings).copy(accentHex = "")
            colorAccent.value = Color.web(base.effectiveAccent())
            applyThemeDraftToScene()
        }

        sldRadius.value = theme.radiusPx.toDouble().coerceIn(0.0, 20.0)
        lblRadiusValue.text = "${theme.radiusPx}px"
        sldRadius.valueProperty().addListener { _, _, v ->
            lblRadiusValue.text = "${v.toInt().coerceIn(0, 20)}px"
            applyThemeDraftToScene()
        }
        btnRadiusReset.setOnAction {
            sldRadius.value = 10.0
            lblRadiusValue.text = "10px"
            applyThemeDraftToScene()
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
                applyThemeDraftToScene()
            }
        }

        try { colorPanelBg.value = Color.web(theme.panelBackground) } catch (_: Exception) { }
        colorPanelBg.valueProperty().addListener { _, _, _ -> applyThemeDraftToScene() }
        btnPanelBgDefault.setOnAction {
            colorPanelBg.value = Color.web("#f5f5f5")
            applyThemeDraftToScene()
        }

        btnApplyTheme.setOnAction { applyAndSaveTheme() }
        btnRestoreAppearance.setOnAction { restoreAppearanceDefaults() }
        btnThemeLight.setOnAction { applyPresetAndRefresh(ThemeSettings.lightDefault()) }
        btnThemeDark.setOnAction { applyPresetAndRefresh(ThemeSettings.darkDefault()) }
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

        setupMachineTree(settings)

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
        val config = settings.chartConfig
        txtColorLiveBt.text = colors.liveBt
        txtColorLiveEt.text = colors.liveEt
        txtColorLiveRorBt.text = colors.liveRorBt
        txtColorLiveRorEt.text = colors.liveRorEt
        txtColorRefBt.text = colors.refBt
        txtColorRefEt.text = colors.refEt
        txtColorRefRorEt.text = colors.refRorEt
        txtColorRefRorBt.text = colors.refRorBt
        txtColorRefExtra1.text = colors.refExtra1
        txtColorRefExtra2.text = colors.refExtra2
        txtColorRefAlpha.text = colors.refAlpha.toString()
        sldRefAlpha.value = colors.refAlpha.toDouble()
        bindColorPickerToHex(colorLiveBt, txtColorLiveBt)
        bindColorPickerToHex(colorLiveEt, txtColorLiveEt)
        bindColorPickerToHex(colorLiveRorBt, txtColorLiveRorBt)
        bindColorPickerToHex(colorLiveRorEt, txtColorLiveRorEt)
        bindColorPickerToHex(colorRefBt, txtColorRefBt)
        bindColorPickerToHex(colorRefEt, txtColorRefEt)
        bindColorPickerToHex(colorRefRorEt, txtColorRefRorEt)
        bindColorPickerToHex(colorRefRorBt, txtColorRefRorBt)
        bindColorPickerToHex(colorRefExtra1, txtColorRefExtra1)
        bindColorPickerToHex(colorRefExtra2, txtColorRefExtra2)
        setColorPickerFromHex(colorLiveBt, colors.liveBt)
        setColorPickerFromHex(colorLiveEt, colors.liveEt)
        setColorPickerFromHex(colorLiveRorBt, colors.liveRorBt)
        setColorPickerFromHex(colorLiveRorEt, colors.liveRorEt)
        setColorPickerFromHex(colorRefBt, colors.refBt)
        setColorPickerFromHex(colorRefEt, colors.refEt)
        setColorPickerFromHex(colorRefRorEt, colors.refRorEt)
        setColorPickerFromHex(colorRefRorBt, colors.refRorBt)
        setColorPickerFromHex(colorRefExtra1, colors.refExtra1)
        setColorPickerFromHex(colorRefExtra2, colors.refExtra2)
        setColorPickerFromHex(colorGraphCanvas, config.backgroundColor)
        setColorPickerFromHex(colorGraphBackground, config.backgroundColor)
        setColorPickerFromHex(colorGraphTitle, config.axisLabelColor)
        setColorPickerFromHex(colorGraphGrid, config.gridColor)
        setColorPickerFromHex(colorGraphYLabel, config.axisLabelColor)
        setColorPickerFromHex(colorGraphXLabel, config.axisLabelColor)
        setColorPickerFromHex(colorGraphMarkers, config.markerColor)
        setColorPickerFromHex(colorGraphText, config.phaseLabelColor)
        setColorPickerFromHex(colorGraphDrying, config.phaseDryingColor)
        setColorPickerFromHex(colorGraphMaillard, config.phaseMaillardColor)
        setColorPickerFromHex(colorGraphFinishing, config.phaseDevelopmentColor)
        setColorPickerFromHex(colorGraphCooling, config.phaseCoolingColor)
        setColorPickerFromHex(colorGraphLegendBg, config.markerLabelBackgroundColor)
        setColorPickerFromHex(colorGraphLegendBorder, config.gridColor)
        sldRefAlpha.valueProperty().addListener { _, _, v -> txtColorRefAlpha.text = v.toInt().toString() }
        txtColorRefAlpha.textProperty().addListener { _, _, s ->
            s.toIntOrNull()?.coerceIn(0, 255)?.let { sldRefAlpha.value = it.toDouble() }
        }
        axesConfigDraft = config
        axesTabController = axesTabIncludeController
        loadGraphCurvesTabs(settings)
        loadRoastPhasesSection(settings)

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
            val defConfig = ChartConfig()
            txtColorLiveBt.text = d.liveBt
            txtColorLiveEt.text = d.liveEt
            txtColorLiveRorBt.text = d.liveRorBt
            txtColorLiveRorEt.text = d.liveRorEt
            txtColorRefBt.text = d.refBt
            txtColorRefEt.text = d.refEt
            txtColorRefRorEt.text = d.refRorEt
            txtColorRefRorBt.text = d.refRorBt
            txtColorRefExtra1.text = d.refExtra1
            txtColorRefExtra2.text = d.refExtra2
            txtColorRefAlpha.text = d.refAlpha.toString()
            sldRefAlpha.value = d.refAlpha.toDouble()
            setColorPickerFromHex(colorLiveBt, d.liveBt)
            setColorPickerFromHex(colorLiveEt, d.liveEt)
            setColorPickerFromHex(colorLiveRorBt, d.liveRorBt)
            setColorPickerFromHex(colorLiveRorEt, d.liveRorEt)
            setColorPickerFromHex(colorRefBt, d.refBt)
            setColorPickerFromHex(colorRefEt, d.refEt)
            setColorPickerFromHex(colorRefRorEt, d.refRorEt)
            setColorPickerFromHex(colorRefRorBt, d.refRorBt)
            setColorPickerFromHex(colorRefExtra1, d.refExtra1)
            setColorPickerFromHex(colorRefExtra2, d.refExtra2)
            setColorPickerFromHex(colorGraphCanvas, defConfig.backgroundColor)
            setColorPickerFromHex(colorGraphBackground, defConfig.backgroundColor)
            setColorPickerFromHex(colorGraphTitle, defConfig.axisLabelColor)
            setColorPickerFromHex(colorGraphGrid, defConfig.gridColor)
            setColorPickerFromHex(colorGraphYLabel, defConfig.axisLabelColor)
            setColorPickerFromHex(colorGraphXLabel, defConfig.axisLabelColor)
            setColorPickerFromHex(colorGraphMarkers, defConfig.markerColor)
            setColorPickerFromHex(colorGraphText, defConfig.phaseLabelColor)
            setColorPickerFromHex(colorGraphDrying, defConfig.phaseDryingColor)
            setColorPickerFromHex(colorGraphMaillard, defConfig.phaseMaillardColor)
            setColorPickerFromHex(colorGraphFinishing, defConfig.phaseDevelopmentColor)
            setColorPickerFromHex(colorGraphCooling, defConfig.phaseCoolingColor)
            setColorPickerFromHex(colorGraphLegendBg, defConfig.markerLabelBackgroundColor)
            setColorPickerFromHex(colorGraphLegendBorder, defConfig.gridColor)
        }
        btnColorsGrey.setOnAction {
            val g = "#808080"
            listOf(
                colorGraphCanvas, colorGraphBackground, colorGraphTitle, colorGraphGrid,
                colorGraphYLabel, colorGraphXLabel, colorGraphMarkers, colorGraphText,
                colorGraphLegendBg, colorGraphLegendBorder
            ).forEach { setColorPickerFromHex(it, g) }
            setColorPickerFromHex(colorGraphDrying, "#e5e5e5")
            setColorPickerFromHex(colorGraphMaillard, "#b2b2b2")
            setColorPickerFromHex(colorGraphFinishing, "#e5e5e5")
            setColorPickerFromHex(colorGraphCooling, "#bde0ee")
        }

        btnRefreshPorts.setOnAction { refreshSerialPorts() }
        btnPortsConfig.setOnAction { openPortsConfigDialog() }
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

        // Machine tree selection handled in setupMachineTree()

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
        refreshThemePreviewCard(buildThemeSettingsFromTab(theme))
    }

    private fun setupMachineTree(settings: AppSettings) {
        allTreeEntries = MachinePresetRegistry.allEntries()
        populateMachineTree(null)
        machineTree.isShowRoot = false

        txtMachineFilter.textProperty().addListener { _, _, newVal ->
            populateMachineTree(newVal?.trim()?.takeIf { it.isNotBlank() })
        }

        machineTree.selectionModel.selectedItemProperty().addListener { _, _, item ->
            if (item == null || !item.isLeaf) {
                selectedPresetEntry = null
                lblPresetSummary.text = ""
                return@addListener
            }
            val brand = item.parent?.value ?: return@addListener
            val model = item.value
            val entry = MachinePresetRegistry.models(brand).find { it.model == model }
            selectedPresetEntry = entry
            if (entry != null) {
                applyMachinePreset(entry, settings)
            }
        }

        if (settings.presetBrand.isNotBlank() && settings.presetModel.isNotBlank()) {
            lblPresetSummary.text = "Currently loaded: ${settings.presetBrand} ${settings.presetModel}"
            selectTreeNode(settings.presetBrand, settings.presetModel)
        }
    }

    private fun populateMachineTree(filter: String?) {
        val root = TreeItem<String>("Machines")
        val entries = if (filter == null) allTreeEntries
        else allTreeEntries.filter {
            it.brand.contains(filter, ignoreCase = true) || it.model.contains(filter, ignoreCase = true)
        }
        val grouped = entries.groupBy { it.brand }
        for ((brand, models) in grouped.toSortedMap()) {
            val brandItem = TreeItem(brand)
            for (m in models.sortedBy { it.model }) {
                brandItem.children.add(TreeItem(m.model))
            }
            if (filter != null) brandItem.isExpanded = true
            root.children.add(brandItem)
        }
        machineTree.root = root
    }

    private fun selectTreeNode(brand: String, model: String) {
        val root = machineTree.root ?: return
        for (brandItem in root.children) {
            if (brandItem.value == brand) {
                brandItem.isExpanded = true
                for (modelItem in brandItem.children) {
                    if (modelItem.value == model) {
                        machineTree.selectionModel.select(modelItem)
                        return
                    }
                }
            }
        }
    }

    private fun applyMachinePreset(entry: MachinePresetEntry, currentSettings: AppSettings) {
        try {
            val asetPreset = MachinePresetRegistry.getPreset(entry)
            val newSettings = MachinePresetApplier.apply(asetPreset, entry.brand, entry.model, currentSettings)
            appliedPresetSettings = newSettings

            val mc = newSettings.machineConfig
            txtHost.text = mc.host ?: ""
            txtTcpPort.text = mc.tcpPort.toString()
            txtBaudRate.text = mc.baudRate.toString()
            txtSlaveId.text = mc.slaveId.toString()
            txtBtRegister.text = mc.btRegister.toString()
            txtEtRegister.text = mc.etRegister.toString()
            txtDivider.text = mc.divisionFactor.toString()
            txtSamplingIntervalSec.text = (mc.pollingIntervalMs / 1000.0).toString()

            if (::portTabsIncludeController.isInitialized) {
                portTabsIncludeController.loadPortConfig(newSettings.portConfig)
            }
            if (::devicesTabsIncludeController.isInitialized) {
                devicesTabsIncludeController.loadDeviceAssignment(newSettings.deviceAssignment, mc.modbusInputs, mc.modbusPid)
            }

            updateTransportCombo()
            val transport = when (mc.transport) {
                Transport.TCP -> "TCP (Ethernet)"
                Transport.PHIDGET -> "Phidget 1048 (USB)"
                Transport.SERIAL -> cmbTransport.items.firstOrNull() ?: "Serial (COM port)"
            }
            if (transport in cmbTransport.items) cmbTransport.value = transport
            updatePortFieldsVisibility()
            updateTransportFieldsVisibility()

            val protocol = when (mc.machineType) {
                MachineType.MODBUS_GENERIC -> "Modbus"
                MachineType.S7_GENERIC -> "S7"
                MachineType.SERIAL_GENERIC -> "Serial"
                MachineType.WEBSOCKET_GENERIC -> "WebSocket"
                MachineType.BESCA -> "Modbus (Besca)"
                MachineType.DIEDRICH -> "Modbus (Diedrich)"
                MachineType.SIMULATOR -> "Simulator"
            }
            val sliderCount = newSettings.presetSliders.size
            val extraCount = newSettings.extraSensors.size
            val buttonCount = newSettings.customButtons.size
            lblPresetSummary.text = "${entry.brand} ${entry.model}: $protocol | " +
                    "${mc.host ?: mc.port}:${mc.tcpPort} | " +
                    "$sliderCount slider(s), $buttonCount button(s), $extraCount extra sensor(s)"
        } catch (e: Exception) {
            lblPresetSummary.text = "Error loading preset: ${e.message}"
        }
    }

    private fun decorateNavigationButtons() {
        btnNavAppearance.graphic = createNavIcon(FontAwesomeSolid.PALETTE)
        btnNavConnection.graphic = createNavIcon(FontAwesomeSolid.PLUG)
        btnNavChart.graphic = createNavIcon(FontAwesomeSolid.CHART_LINE)
        btnNavRoastPhases.graphic = createNavIcon(FontAwesomeSolid.FIRE)
        btnNavColors.graphic = createNavIcon(FontAwesomeSolid.TINT)
        btnNavEvents.graphic = createNavIcon(FontAwesomeSolid.BOLT)
    }

    private fun createNavIcon(iconCode: FontAwesomeSolid): FontIcon {
        return FontIcon(iconCode).apply { iconSize = 13 }
    }

    private fun setupSectionNavigation() {
        btnNavAppearance.setOnAction { showSection(SettingsSection.APPEARANCE) }
        btnNavConnection.setOnAction { showSection(SettingsSection.CONNECTION) }
        btnNavChart.setOnAction { showSection(SettingsSection.CHART) }
        btnNavRoastPhases.setOnAction { showSection(SettingsSection.ROAST_PHASES) }
        btnNavColors.setOnAction { showSection(SettingsSection.COLORS) }
        btnNavEvents.setOnAction { showSection(SettingsSection.EVENTS) }
    }

    private fun setupSidebarToggle() {
        updateSidebarToggleButton()
        btnSidebarToggle.setOnAction { toggleSidebar() }
    }

    private fun updateSidebarToggleButton() {
        btnSidebarToggle.graphic = FontIcon(
            if (isSidebarExpanded) FontAwesomeSolid.CHEVRON_LEFT else FontAwesomeSolid.CHEVRON_RIGHT
        ).apply { iconSize = 14 }
        btnSidebarToggle.tooltip = Tooltip(
            if (isSidebarExpanded) "Скрыть разделы" else "Показать разделы"
        )
    }

    private fun toggleSidebar() {
        sidebarTimeline?.let { if (it.status == Animation.Status.RUNNING) return }
        val targetWidth = if (isSidebarExpanded) 0.0 else sidebarWidthExpanded
        val durationMs = 220.0
        sidebarTimeline = Timeline(
            KeyFrame(
                Duration.millis(durationMs),
                KeyValue(sidebarWidth, targetWidth)
            )
        ).apply {
            setOnFinished { _: ActionEvent ->
                settingsSidebar.isManaged = targetWidth > 0
                isSidebarExpanded = targetWidth > 0
                updateSidebarToggleButton()
                sidebarTimeline = null
            }
            play()
        }
    }

    private fun showSection(section: SettingsSection) {
        val previous = activeSection
        val nextNode = nodeForSection(section)

        if (previous == null) {
            allSectionNodes().forEach { setSectionVisible(it, it == nextNode) }
        } else if (previous != section) {
            animateSectionSwitch(nodeForSection(previous), nextNode)
        }

        btnNavAppearance.isSelected = section == SettingsSection.APPEARANCE
        btnNavConnection.isSelected = section == SettingsSection.CONNECTION
        btnNavChart.isSelected = section == SettingsSection.CHART
        btnNavRoastPhases.isSelected = section == SettingsSection.ROAST_PHASES
        btnNavColors.isSelected = section == SettingsSection.COLORS
        btnNavEvents.isSelected = section == SettingsSection.EVENTS

        lblSettingsHeroEyebrow.text = "SETTINGS"
        lblSettingsHeroTitle.text = section.title
        lblSettingsHeroSubtitle.text = section.subtitle
        activeSection = section
    }

    private fun setSectionVisible(sectionNode: ScrollPane, visible: Boolean) {
        sectionNode.isVisible = visible
        sectionNode.isManaged = visible
        if (visible) {
            sectionNode.vvalue = 0.0
        }
    }

    private fun nodeForSection(section: SettingsSection): ScrollPane {
        return when (section) {
            SettingsSection.APPEARANCE -> appearanceSection
            SettingsSection.CONNECTION -> connectionSection
            SettingsSection.CHART -> chartSection
            SettingsSection.ROAST_PHASES -> roastPhasesSection
            SettingsSection.COLORS -> colorsSection
            SettingsSection.EVENTS -> eventsSection
        }
    }

    private fun allSectionNodes(): List<ScrollPane> {
        return listOf(appearanceSection, connectionSection, chartSection, roastPhasesSection, colorsSection, eventsSection)
    }

    private fun animateSectionSwitch(from: ScrollPane, to: ScrollPane) {
        if (from == to) return
        to.opacity = 0.0
        setSectionVisible(to, true)

        val fadeOut = FadeTransition(Duration.millis(140.0), from).apply {
            fromValue = 1.0
            toValue = 0.0
        }
        val fadeIn = FadeTransition(Duration.millis(220.0), to).apply {
            fromValue = 0.0
            toValue = 1.0
        }
        fadeOut.setOnFinished {
            setSectionVisible(from, false)
            from.opacity = 1.0
        }
        ParallelTransition(fadeOut, fadeIn).play()
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

    private fun resolveMachineType(): MachineType {
        val applied = appliedPresetSettings
        if (applied != null) return applied.machineConfig.machineType
        val entry = selectedPresetEntry ?: return MachineType.SIMULATOR
        return try {
            val preset = MachinePresetRegistry.getPreset(entry)
            MachinePresetApplier.resolveDeviceId(preset.device.id)
        } catch (_: Exception) { MachineType.MODBUS_GENERIC }
    }

    private fun modbusTransportTypeFromIndex(typeIndex: Int): ModbusTransportType = when (typeIndex) {
        1 -> ModbusTransportType.SERIAL_ASCII
        3 -> ModbusTransportType.TCP
        4 -> ModbusTransportType.UDP
        else -> ModbusTransportType.SERIAL_RTU
    }

    private fun serialParityFromCode(code: String): SerialParity = when (code) {
        "O" -> SerialParity.ODD
        "E" -> SerialParity.EVEN
        else -> SerialParity.NONE
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
        val machineType = resolveMachineType()
        val transport = when {
            machineType == MachineType.BESCA && cmbTransport.value == "TCP (Ethernet)" -> Transport.TCP
            machineType == MachineType.DIEDRICH && cmbTransport.value == "Phidget 1048 (USB)" -> Transport.PHIDGET
            else -> Transport.SERIAL
        }
        val portRaw = (cmbPort.value ?: cmbPort.editor?.text)?.trim().orEmpty()
        val port = (if (portRaw.startsWith("Другой: ")) portRaw.removePrefix("Другой: ").trim() else portRaw).ifBlank { "COM4" }
        val pollingIntervalMs = (txtSamplingIntervalSec.text.toDoubleOrNull()?.times(1000)?.toLong()?.coerceIn(100L, 3000L)) ?: 1000L
        val fromPortTabs = if (::portTabsIncludeController.isInitialized) {
            PortsAdvanced(
                modbusType = modbusTransportTypeFromIndex(portTabsIncludeController.getModbusTypeIndex()),
                byteSize = portTabsIncludeController.getModbusByteSize(),
                parity = serialParityFromCode(portTabsIncludeController.getModbusParity()),
                stopBits = portTabsIncludeController.getModbusStopbits(),
                serialTimeoutSec = portTabsIncludeController.getModbusTimeout(),
                ipTimeoutSec = portTabsIncludeController.getModbusIpTimeout(),
                ipRetries = portTabsIncludeController.getModbusIpRetries()
            )
        } else null
        val advanced = fromPortTabs ?: portsAdvanced ?: PortsAdvanced(
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
            host = if (::portTabsIncludeController.isInitialized) portTabsIncludeController.getModbusHost() else hostForTcp,
            tcpPort = if (::portTabsIncludeController.isInitialized) portTabsIncludeController.getModbusPort() else (txtTcpPort.text.toIntOrNull() ?: 502),
            port = if (::portTabsIncludeController.isInitialized) portTabsIncludeController.getCommPort() else port,
            baudRate = if (::portTabsIncludeController.isInitialized) portTabsIncludeController.getBaudRate() else (txtBaudRate.text.toIntOrNull() ?: 9600),
            byteSize = advanced.byteSize,
            parity = advanced.parity,
            stopBits = advanced.stopBits,
            serialTimeoutSec = advanced.serialTimeoutSec,
            ipTimeoutSec = advanced.ipTimeoutSec,
            ipRetries = advanced.ipRetries,
            slaveId = if (::portTabsIncludeController.isInitialized) portTabsIncludeController.getSlaveId() else (txtSlaveId.text.toIntOrNull() ?: 1),
            btRegister = txtBtRegister.text.toIntOrNull() ?: 6,
            etRegister = txtEtRegister.text.toIntOrNull() ?: 7,
            divisionFactor = txtDivider.text.toDoubleOrNull() ?: 10.0,
            phidgetEtChannel = txtPhidgetEtChannel.text.toIntOrNull()?.coerceIn(1, 4) ?: 1,
            phidgetBtChannel = txtPhidgetBtChannel.text.toIntOrNull()?.coerceIn(1, 4) ?: 2,
            pollingIntervalMs = pollingIntervalMs,
            modbusInputs = if (::portTabsIncludeController.isInitialized) portTabsIncludeController.getModbusInputs() else (portsModbusInputs ?: settings.machineConfig.modbusInputs),
            modbusPid = if (::portTabsIncludeController.isInitialized) portTabsIncludeController.getModbusPid() else (portsModbusPid ?: settings.machineConfig.modbusPid),
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
            val portConfigFromUi = if (::portTabsIncludeController.isInitialized) portTabsIncludeController.getPortConfig() else settings.portConfig
            val deviceAssignmentFromUi = if (::devicesTabsIncludeController.isInitialized) devicesTabsIncludeController.getDeviceAssignmentConfig() else (deviceAssignmentConfig ?: settings.deviceAssignment)
            val usePopupDraft = portsAdvanced != null || portsModbusInputs != null || portsModbusPid != null
            val machineType = resolveMachineType()
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
                refRorBt = txtColorRefRorBt.text?.trim()?.takeIf { it.isNotBlank() } ?: def.refRorBt,
                refRorEt = txtColorRefRorEt.text?.trim()?.takeIf { it.isNotBlank() } ?: def.refRorEt,
                refExtra1 = txtColorRefExtra1.text?.trim()?.takeIf { it.isNotBlank() } ?: def.refExtra1,
                refExtra2 = txtColorRefExtra2.text?.trim()?.takeIf { it.isNotBlank() } ?: def.refExtra2,
                refAlpha = sldRefAlpha.value.toInt().coerceIn(0, 255)
            )
            val baseConfig = axesTabController?.getResult() ?: axesConfigDraft ?: settings.chartConfig
            val chartConfig = baseConfig.copy(
                backgroundColor = colorToHex(colorGraphBackground.value),
                gridColor = colorToHex(colorGraphGrid.value),
                axisLabelColor = colorToHex(colorGraphXLabel.value),
                markerColor = colorToHex(colorGraphMarkers.value),
                markerLabelBackgroundColor = colorToHex(colorGraphLegendBg.value),
                phaseDryingColor = colorToHex(colorGraphDrying.value),
                phaseMaillardColor = colorToHex(colorGraphMaillard.value),
                phaseDevelopmentColor = colorToHex(colorGraphFinishing.value),
                phaseCoolingColor = colorToHex(colorGraphCooling.value),
                phaseLabelColor = colorToHex(colorGraphText.value)
            )

            val pollingIntervalMs = (txtSamplingIntervalSec.text.toDoubleOrNull()?.times(1000)?.toLong()?.coerceIn(100L, 3000L)) ?: 1000L
            val advanced = if (usePopupDraft) {
                portsAdvanced ?: PortsAdvanced(
                    modbusType = settings.machineConfig.modbusTransportType,
                    byteSize = settings.machineConfig.byteSize,
                    parity = settings.machineConfig.parity,
                    stopBits = settings.machineConfig.stopBits,
                    serialTimeoutSec = settings.machineConfig.serialTimeoutSec,
                    ipTimeoutSec = settings.machineConfig.ipTimeoutSec,
                    ipRetries = settings.machineConfig.ipRetries
                )
            } else {
                PortsAdvanced(
                    modbusType = modbusTransportTypeFromIndex(portTabsIncludeController.getModbusTypeIndex()),
                    byteSize = portTabsIncludeController.getModbusByteSize(),
                    parity = serialParityFromCode(portTabsIncludeController.getModbusParity()),
                    stopBits = portTabsIncludeController.getModbusStopbits(),
                    serialTimeoutSec = portTabsIncludeController.getModbusTimeout(),
                    ipTimeoutSec = portTabsIncludeController.getModbusIpTimeout(),
                    ipRetries = portTabsIncludeController.getModbusIpRetries()
                )
            }
            val mc = settings.machineConfig.copy(
                machineType = machineType,
                transport = transport,
                modbusTransportType = if (transport == Transport.TCP) ModbusTransportType.TCP else advanced.modbusType,
                host = if (usePopupDraft) host else portTabsIncludeController.getModbusHost().trim().ifBlank { host.orEmpty() }.takeIf { it.isNotBlank() },
                tcpPort = if (usePopupDraft) tcpPort else portTabsIncludeController.getModbusPort(),
                port = if (usePopupDraft) port else portTabsIncludeController.getCommPort().ifBlank { port },
                baudRate = if (usePopupDraft) baudRate else portTabsIncludeController.getBaudRate(),
                byteSize = advanced.byteSize,
                parity = advanced.parity,
                stopBits = advanced.stopBits,
                serialTimeoutSec = advanced.serialTimeoutSec,
                ipTimeoutSec = advanced.ipTimeoutSec,
                ipRetries = advanced.ipRetries,
                slaveId = if (usePopupDraft) slaveId else portTabsIncludeController.getSlaveId(),
                btRegister = txtBtRegister.text.toIntOrNull() ?: 6,
                etRegister = txtEtRegister.text.toIntOrNull() ?: 7,
                divisionFactor = txtDivider.text.toDoubleOrNull() ?: 10.0,
                phidgetEtChannel = phidgetEt,
                phidgetBtChannel = phidgetBt,
                pollingIntervalMs = pollingIntervalMs,
                modbusInputs = if (usePopupDraft) (portsModbusInputs ?: settings.machineConfig.modbusInputs) else portTabsIncludeController.getModbusInputs(),
                modbusPid = if (usePopupDraft) (portsModbusPid ?: settings.machineConfig.modbusPid) else portTabsIncludeController.getModbusPid(),
                eventCommands = buildEventCommandsFromFields()
            )
            val discoveryHosts = txtDiscoveryHosts.text?.trim()?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.takeIf { it.isNotEmpty() }
            val themeSettings = buildThemeSettingsFromTab(settings.themeSettings)
            val newSettings = AppSettings(
                machineConfig = mc,
                unit = unit,
                savePath = savePath,
                serverBaseUrl = com.rdr.roast.app.ServerConfig.API_BASE_URL,
                serverToken = settings.serverToken,
                serverRefreshToken = settings.serverRefreshToken,
                serverRememberEmail = settings.serverRememberEmail,
                themeSettings = themeSettings,
                chartColors = chartColors,
                chartConfig = chartConfig,
                curvesConfig = buildCurvesConfigFromTabs() ?: settings.curvesConfig,
                roastPhasesConfig = buildRoastPhasesFromTab(),
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
                customButtons = eventButtonsDialogResult?.customButtons ?: appliedPresetSettings?.customButtons ?: settings.customButtons,
                eventButtonsConfig = eventButtonsDialogResult?.eventButtonsConfig ?: settings.eventButtonsConfig,
                eventSliders = eventButtonsDialogResult?.eventSliders ?: appliedPresetSettings?.eventSliders ?: settings.eventSliders,
                deviceAssignment = deviceAssignmentFromUi,
                portConfig = portConfigFromUi,
                presetSliders = appliedPresetSettings?.presetSliders ?: settings.presetSliders,
                extraSensors = appliedPresetSettings?.extraSensors ?: settings.extraSensors,
                presetBrand = appliedPresetSettings?.presetBrand ?: settings.presetBrand,
                presetModel = appliedPresetSettings?.presetModel ?: settings.presetModel,
                rorSmoothing = settings.rorSmoothing
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

    /** Creates a modal child Stage with main stylesheets and appearance applied (theme, accent, icon). */
    private fun createChildStage(root: Parent, title: String, owner: Window?, resizable: Boolean = true): Stage {
        val scene = Scene(root)
        owner?.scene?.stylesheets?.let { scene.stylesheets.setAll(it) }
        if (scene.stylesheets.isEmpty()) {
            SettingsController::class.java.getResource("/com/rdr/roast/ui/main.css")?.toExternalForm()?.let { scene.stylesheets.add(it) }
            SettingsController::class.java.getResource("/css/appearance.css")?.toExternalForm()?.let { scene.stylesheets.add(it) }
        }
        com.rdr.roast.app.AppearanceSupport.applyToScene(scene)
        return Stage().apply {
            this.title = title
            this.scene = scene
            isResizable = resizable
            initModality(Modality.APPLICATION_MODAL)
            initOwner(owner)
            SettingsController::class.java.getResource("/com/rdr/roast/app-icon.png")?.toExternalForm()?.let { icons.add(Image(it)) }
        }
    }

    /**
     * Opens Artisan-style "Ports Configuration" dialog. On OK, applies MODBUS tab values
     * to the Connection tab (caller-applies pattern like Artisan main.py after comportDlg).
     */
    private fun openPortsConfigDialog() {
        val url = SettingsController::class.java.getResource("/com/rdr/roast/ui/PortsConfigView.fxml") ?: return
        val loader = FXMLLoader(url)
        val root = loader.load() as? Parent ?: return
        val portsController = loader.getController<PortsConfigController>() ?: return
        val stage = createChildStage(root, "Device Assignment", btnPortsConfig.scene?.window, resizable = true)
        stage.showAndWait()
        if (!portsController.applied) return
        // Apply MODBUS tab to Connection tab (Artisan: main.py reads dialog and sets modbus.host/port etc.)
        val typeIndex = portsController.getModbusTypeIndex()
        val isTcp = typeIndex == 3
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
            modbusType = modbusTransportTypeFromIndex(typeIndex),
            byteSize = portsController.getModbusByteSize(),
            parity = serialParityFromCode(portsController.getModbusParity()),
            stopBits = portsController.getModbusStopbits(),
            serialTimeoutSec = portsController.getModbusTimeout(),
            ipTimeoutSec = portsController.getModbusIpTimeout(),
            ipRetries = portsController.getModbusIpRetries()
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
        val root = loader.load() as? Parent ?: return
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
        val stage = createChildStage(root, "Event Buttons", btnEventButtons.scene?.window, resizable = true)
        stage.showAndWait()
        if (controller.applied) {
            controller.getResult()?.let { eventButtonsDialogResult = it }
        }
    }

    private val phasesLcdModeOptions = listOf("Time", "Percentage", "Temp")

    private fun loadRoastPhasesSection(settings: AppSettings) {
        val rp = settings.roastPhasesConfig
        phasesDryMin.text = rp.dryMin().toString()
        phasesDryMax.text = rp.dryMax().toString()
        phasesMaillardMax.text = rp.maillardMax().toString()
        phasesFinishMax.text = rp.finishMax().toString()
        lblPhasesMaillardMin.text = rp.dryMax().toString()
        lblPhasesFinishMin.text = rp.maillardMax().toString()
        phasesLcdModeDry.items.setAll(phasesLcdModeOptions)
        phasesLcdModeMaillard.items.setAll(phasesLcdModeOptions)
        phasesLcdModeFinish.items.setAll(phasesLcdModeOptions)
        phasesLcdModeDry.value = phasesLcdModeOptions.getOrNull(rp.phasesLCDmodeL.getOrElse(0) { 1 }) ?: "Percentage"
        phasesLcdModeMaillard.value = phasesLcdModeOptions.getOrNull(rp.phasesLCDmodeL.getOrElse(1) { 1 }) ?: "Percentage"
        phasesLcdModeFinish.value = phasesLcdModeOptions.getOrNull(rp.phasesLCDmodeL.getOrElse(2) { 1 }) ?: "Percentage"
        phasesLcdAllFinish.isSelected = rp.phasesLCDmodeAll.getOrElse(2) { true }
        phasesAutoAdjusted.isSelected = rp.phasesButtonFlag
        phasesFromBackground.isSelected = rp.phasesFromBackgroundFlag
        phasesWatermarks.isSelected = rp.watermarksFlag
        phasesLcds.isSelected = rp.phasesLCDflag
        phasesAutoDry.isSelected = rp.autoDRYflag
        phasesAutoFcs.isSelected = rp.autoFCsFlag
        phasesDryMax.textProperty().addListener { _, _, _ -> lblPhasesMaillardMin.text = phasesDryMax.text.ifBlank { "157" } }
        phasesMaillardMax.textProperty().addListener { _, _, _ -> lblPhasesFinishMin.text = phasesMaillardMax.text.ifBlank { "197" } }
        btnRoastPhasesRestoreDefaults.setOnAction {
            val def = RoastPhasesConfig()
            phasesDryMin.text = def.dryMin().toString()
            phasesDryMax.text = def.dryMax().toString()
            phasesMaillardMax.text = def.maillardMax().toString()
            phasesFinishMax.text = def.finishMax().toString()
            lblPhasesMaillardMin.text = def.dryMax().toString()
            lblPhasesFinishMin.text = def.maillardMax().toString()
            phasesLcdModeDry.value = "Percentage"
            phasesLcdModeMaillard.value = "Percentage"
            phasesLcdModeFinish.value = "Percentage"
            phasesLcdAllFinish.isSelected = true
            phasesAutoAdjusted.isSelected = true
            phasesFromBackground.isSelected = false
            phasesWatermarks.isSelected = true
            phasesLcds.isSelected = true
            phasesAutoDry.isSelected = false
            phasesAutoFcs.isSelected = false
        }
    }

    private fun buildRoastPhasesFromTab(): RoastPhasesConfig {
        val dryMin = phasesDryMin.text.toIntOrNull()?.coerceIn(0, 1000) ?: 150
        val dryMax = phasesDryMax.text.toIntOrNull()?.coerceIn(0, 1000) ?: 150
        val maillardMax = phasesMaillardMax.text.toIntOrNull()?.coerceIn(0, 1000) ?: 200
        val finishMax = phasesFinishMax.text.toIntOrNull()?.coerceIn(0, 1000) ?: 230
        val modeDry = phasesLcdModeOptions.indexOf(phasesLcdModeDry.value).takeIf { it >= 0 } ?: 1
        val modeMid = phasesLcdModeOptions.indexOf(phasesLcdModeMaillard.value).takeIf { it >= 0 } ?: 1
        val modeFin = phasesLcdModeOptions.indexOf(phasesLcdModeFinish.value).takeIf { it >= 0 } ?: 1
        return RoastPhasesConfig(
            phasesCelsius = listOf(dryMin, dryMax, maillardMax, finishMax),
            phasesButtonFlag = phasesAutoAdjusted.isSelected,
            phasesFromBackgroundFlag = phasesFromBackground.isSelected,
            watermarksFlag = phasesWatermarks.isSelected,
            phasesLCDflag = phasesLcds.isSelected,
            autoDRYflag = phasesAutoDry.isSelected,
            autoFCsFlag = phasesAutoFcs.isSelected,
            phasesLCDmodeL = listOf(modeDry, modeMid, modeFin),
            phasesLCDmodeAll = listOf(false, false, phasesLcdAllFinish.isSelected)
        )
    }

    /** Loads the 6 Curves tab FXMLs into chartCurvesTabPane and fills them from [settings].curvesConfig. On failure of a tab, it is skipped so settings still open. */
    private fun loadGraphCurvesTabs(settings: AppSettings) {
        if (!::chartCurvesTabPane.isInitialized || chartCurvesTabPane.tabs.size < 6) return
        val tabs = chartCurvesTabPane.tabs
        val prefix = "/com/rdr/roast/ui/graph/"
        fun loadTab(path: String): Pair<Parent, Any?>? {
            return try {
                val url = SettingsController::class.java.getResource(path) ?: return null
                val loader = FXMLLoader(url)
                val root = loader.load() as? Parent ?: return null
                root to loader.getController<Any>()
            } catch (e: Exception) {
                System.err.println("Failed to load graph tab $path: ${e.message}")
                null
            }
        }
        loadTab("${prefix}GraphRoRTab.fxml")?.let { (root, ctrl) ->
            tabs[0].content = root
            (ctrl as? GraphRoRTabController)?.let { rorTabController = it; it.loadFrom(settings.curvesConfig) }
        }
        loadTab("${prefix}GraphFiltersTab.fxml")?.let { (root, ctrl) ->
            tabs[1].content = root
            (ctrl as? GraphFiltersTabController)?.let { filtersTabController = it; it.loadFrom(settings.curvesConfig) }
        }
        loadTab("${prefix}GraphPlotterTab.fxml")?.let { (root, ctrl) ->
            tabs[2].content = root
            (ctrl as? GraphPlotterTabController)?.let { plotterTabController = it; it.loadFrom(settings.curvesConfig) }
        }
        loadTab("${prefix}GraphMathTab.fxml")?.let { (root, ctrl) ->
            tabs[3].content = root
            (ctrl as? GraphMathTabController)?.let { mathTabController = it; it.loadFrom(settings.curvesConfig) }
        }
        loadTab("${prefix}GraphAnalyzeTab.fxml")?.let { (root, ctrl) ->
            tabs[4].content = root
            (ctrl as? GraphAnalyzeTabController)?.let { analyzeTabController = it; it.loadFrom(settings.curvesConfig) }
        }
        loadTab("${prefix}GraphUiTab.fxml")?.let { (root, ctrl) ->
            tabs[5].content = root
            (ctrl as? GraphUiTabController)?.let { uiTabController = it; it.loadFrom(settings.curvesConfig) }
        }
        // Axes tab (index 6) is loaded via fx:include in SettingsView; controller is set by FXML
        axesTabController?.loadFrom(settings)
    }

    /** Builds CurvesConfig from the 6 tab controllers; returns null if any tab failed to load. */
    private fun buildCurvesConfigFromTabs(): CurvesConfig? {
        val r = rorTabController?.getResult() ?: return null
        val f = filtersTabController?.getResult() ?: return null
        val p = plotterTabController?.getResult() ?: return null
        val m = mathTabController?.getResult() ?: return null
        val a = analyzeTabController?.getResult() ?: return null
        val u = uiTabController?.getResult() ?: return null
        return CurvesConfig(ror = r, filters = f, plotter = p, math = m, analyze = a, ui = u)
    }

    /** Opens separate Artisan-style Axes dialog (Config -> Axes path). */
    private fun openAxesConfigDialog() {
        val url = SettingsController::class.java.getResource("/com/rdr/roast/ui/AxesConfigView.fxml") ?: return
        val loader = FXMLLoader(url)
        val root = loader.load() as? Parent ?: return
        val controller = loader.getController<AxesConfigController>() ?: return
        controller.loadFrom(axesConfigDraft ?: SettingsManager.load().chartConfig)
        val stage = createChildStage(root, "Axes", btnSave.scene?.window, resizable = true)
        stage.minWidth = 900.0
        stage.minHeight = 640.0
        stage.width = 980.0
        stage.height = 780.0
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
        val mt = resolveMachineType()
        val show = mt == MachineType.SIMULATOR || mt == MachineType.BESCA || mt == MachineType.DIEDRICH
        portFieldsContainer.isVisible = show
        portFieldsContainer.isManaged = show
    }

    private fun updateTransportCombo() {
        val mt = resolveMachineType()
        val items = when (mt) {
            MachineType.BESCA -> listOf("Serial (COM port)", "TCP (Ethernet)")
            MachineType.DIEDRICH -> listOf("Serial (Modbus)", "Phidget 1048 (USB)")
            MachineType.SIMULATOR -> listOf("Serial (COM port)")
            else -> listOf("Auto (from preset)")
        }
        cmbTransport.items.setAll(items)
        if (cmbTransport.value !in items) cmbTransport.value = items.firstOrNull()
    }

    private fun updateTransportFieldsVisibility() {
        val mt = resolveMachineType()
        val isBescaTcp = mt == MachineType.BESCA && cmbTransport.value == "TCP (Ethernet)"
        val isDiedrichPhidget = mt == MachineType.DIEDRICH && cmbTransport.value == "Phidget 1048 (USB)"
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
        val mt = resolveMachineType()
        lblEtBtSource.text = when {
            mt == MachineType.SIMULATOR -> "ET/BT source: Simulator"
            mt == MachineType.DIEDRICH && cmbTransport.value == "Phidget 1048 (USB)" -> "ET/BT source: Phidget"
            mt == MachineType.BESCA -> "ET/BT source: Modbus (Host, Port, Registers from below)"
            mt == MachineType.DIEDRICH -> "ET/BT source: Modbus (Serial)"
            mt == MachineType.S7_GENERIC -> "ET/BT source: S7 (from preset)"
            else -> "ET/BT source: Modbus (from preset)"
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
        // Connection preset loaded; update transport UI
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
        val machineType = resolveMachineType()
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

    private fun buildThemeSettingsFromTab(current: ThemeSettings): ThemeSettings {
        val themeId = ThemeService.displayNameToThemeId(cmbTheme.value ?: "") ?: current.atlantafxThemeId
        val dark = themeId.contains("Dark", ignoreCase = true)
        return current.copy(
            preset = if (dark) ThemePreset.DARK else ThemePreset.LIGHT,
            atlantafxThemeId = themeId,
            scale = AppearanceSupport.scaleFromDisplay(cmbScale.value ?: "100%"),
            fontSize = AppearanceSupport.fontSizeFromDisplay(cmbFontSize.value ?: "Обычный (14px)"),
            density = AppearanceSupport.densityFromDisplay(cmbDensity.value ?: "Обычный"),
            accentHex = AppearanceSupport.colorToHex(colorAccent.value).trim(),
            radiusPx = sldRadius.value.toInt().coerceIn(0, 20),
            panelBackground = AppearanceSupport.colorToHex(colorPanelBg.value),
            darkMode = dark
        )
    }

    private fun applyThemeDraftToScene() {
        val draft = buildThemeSettingsFromTab(SettingsManager.load().themeSettings)
        val settingsStage = colorAccent.scene?.window as? Stage ?: return
        ThemeService.applyTheme(draft, null)
        ThemeService.applyToScene(draft, settingsStage.scene)
        (settingsStage.owner as? Stage)?.scene?.let { ThemeService.applyToScene(draft, it) }
        refreshThemePreviewCard(draft)
        onThemePreviewChanged?.invoke(draft)
    }

    private fun refreshThemePreviewCard(theme: ThemeSettings) {
        lblThemePreviewTitle.text = ThemeService.themeIdToDisplayName(theme.atlantafxThemeId) ?: theme.atlantafxThemeId
        lblThemePreviewMeta.text = listOf(
            AppearanceSupport.displayFromScale(theme.scale),
            AppearanceSupport.displayFromFontSize(theme.fontSize),
            AppearanceSupport.displayFromDensity(theme.density)
        ).joinToString(" • ")
        lblThemePreviewMode.text = if (theme.darkMode) "DARK" else "LIGHT"
        lblThemePreviewRadius.text = "Radius ${theme.radiusPx}px"
        themePreviewAccentSwatch.style = "-fx-background-color: ${theme.effectiveAccent()};"
    }

    private fun applyPresetAndRefresh(theme: ThemeSettings) {
        val settings = SettingsManager.load()
        SettingsManager.save(settings.copy(themeSettings = theme))
        cmbTheme.value = ThemeService.themeIdToDisplayName(theme.atlantafxThemeId)
        cmbScale.value = AppearanceSupport.displayFromScale(theme.scale)
        cmbFontSize.value = AppearanceSupport.displayFromFontSize(theme.fontSize)
        cmbDensity.value = AppearanceSupport.displayFromDensity(theme.density)
        sldRadius.value = theme.radiusPx.toDouble().coerceIn(0.0, 20.0)
        lblRadiusValue.text = "${theme.radiusPx}px"
        try {
            colorAccent.value = Color.web(theme.effectiveAccent())
            colorPanelBg.value = Color.web(theme.panelBackground.ifBlank { "#f5f5f5" })
        } catch (_: Exception) { }
        val settingsStage = colorAccent.scene?.window as? Stage ?: return
        ThemeService.applyTheme(theme, null)
        ThemeService.applyToScene(theme, settingsStage.scene)
        (settingsStage.owner as? Stage)?.scene?.let { ThemeService.applyToScene(theme, it) }
        refreshThemePreviewCard(theme)
        onThemePreviewChanged?.invoke(theme)
    }

    private fun applyAndSaveTheme() {
        val settings = SettingsManager.load()
        val themeSettings = buildThemeSettingsFromTab(settings.themeSettings)
        SettingsManager.save(settings.copy(themeSettings = themeSettings))
        applyAppearanceToMainScene()
    }

    /** Apply current theme to both the settings window and the main app window (owner). */
    private fun applyAppearanceToMainScene() {
        val settingsStage = btnApplyTheme.scene?.window as? Stage ?: return
        val theme = SettingsManager.load().themeSettings
        ThemeService.applyTheme(theme, null)
        ThemeService.applyToScene(theme, settingsStage.scene)
        val mainStage = settingsStage.owner as? Stage
        mainStage?.scene?.let { ThemeService.applyToScene(theme, it) }
        onThemePreviewChanged?.invoke(theme)
    }

    private fun restoreAppearanceDefaults() {
        val light = ThemeSettings.lightDefault()
        cmbTheme.value = ThemeService.themeIdToDisplayName(light.atlantafxThemeId)
        cmbScale.value = "100%"
        cmbFontSize.value = "Обычный (14px)"
        cmbDensity.value = "Обычный"
        sldRadius.value = light.radiusPx.toDouble()
        lblRadiusValue.text = "${light.radiusPx}px"
        colorAccent.value = Color.web(light.effectiveAccent())
        colorPanelBg.value = Color.web(light.panelBackground)
        val settings = SettingsManager.load()
        SettingsManager.save(settings.copy(themeSettings = light))
        refreshThemePreviewCard(light)
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
