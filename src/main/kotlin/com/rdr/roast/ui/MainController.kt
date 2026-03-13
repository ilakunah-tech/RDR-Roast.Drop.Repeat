package com.rdr.roast.ui

import com.rdr.roast.app.DataSourceFactory
import com.rdr.roast.app.DetectedRoaster
import com.rdr.roast.app.ProfileStorage
import com.rdr.roast.app.ConnectionTester
import com.rdr.roast.app.MachineConfig
import com.rdr.roast.app.RoasterDiscovery
import com.rdr.roast.app.RecorderState
import com.rdr.roast.app.ReferenceApi
import com.rdr.roast.app.ErrorLogBuffer
import com.rdr.roast.app.ServerApi
import com.rdr.roast.app.ServerApiException
import com.rdr.roast.app.ServerConfig
import com.rdr.roast.app.buildAroastPayload
import com.rdr.roast.app.CustomButtonConfig
import com.rdr.roast.app.RoastRecorder
import com.rdr.roast.app.AppearanceSupport
import com.rdr.roast.app.SettingsManager
import com.rdr.roast.app.EventQuantifiersConfig
import com.rdr.roast.app.SliderPanelLayoutMode
import com.rdr.roast.domain.ControlEventType
import com.rdr.roast.domain.EventType
import com.rdr.roast.domain.ProtocolComment
import com.rdr.roast.domain.RoastProfile
import com.rdr.roast.domain.TemperatureSample
import com.rdr.roast.domain.curves.MovingAverageCurveModel
import com.rdr.roast.domain.metrics.findTurningPointIndex
import com.rdr.roast.domain.curves.RorCurveModel
import com.rdr.roast.domain.curves.StandardCurveModel
import com.rdr.roast.driver.ConnectionState
import com.rdr.roast.driver.RoastControl
import com.rdr.roast.driver.modbus.core.ModbusCommandExecutor
import com.rdr.roast.driver.modbus.core.ModbusCommandRunner
import com.rdr.roast.driver.ControlSpec
import com.rdr.roast.driver.simulator.SimulatorSource
import com.rdr.roast.ui.chart.ChartPanelFx
import com.rdr.roast.ui.chart.ChartThemeAdapter
import com.rdr.roast.ui.chart.ChartPopupCommentResult
import com.rdr.roast.ui.chart.ChartPopupEventResult
import com.rdr.roast.ui.chart.ChartPopupMode
import com.rdr.roast.ui.chart.ControlChartFx
import com.rdr.roast.ui.chart.CurveChartFx
import com.rdr.roast.ui.control.ControlSliderPanel
import com.rdr.roast.ui.control.DetachableSliderWindow
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.Node
import javafx.scene.control.ContextMenu
import javafx.scene.control.CheckMenuItem
import javafx.scene.control.MenuItem
import javafx.scene.control.RadioMenuItem
import javafx.scene.control.SeparatorMenuItem
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.Tooltip
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.control.Slider
import javafx.scene.control.TextField
import javafx.scene.control.ScrollPane
import javafx.scene.control.SelectionMode
import javafx.scene.control.SplitPane
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.animation.FadeTransition
import javafx.animation.ParallelTransition
import javafx.animation.ScaleTransition
import javafx.animation.TranslateTransition
import javafx.scene.layout.BorderPane
import javafx.scene.layout.FlowPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.shape.Rectangle
import javafx.stage.FileChooser
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid
import org.kordamp.ikonli.javafx.FontIcon
import javafx.stage.Modality
import javafx.stage.Stage
import javafx.util.Duration
import javafx.stage.WindowEvent
import org.jfree.chart.fx.ChartViewer
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Paths
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javafx.collections.FXCollections

class MainController {

    @FXML lateinit var centerPane: BorderPane
    @FXML lateinit var chartContainer: Pane
    @FXML lateinit var controlChartContainer: Pane
    @FXML lateinit var lblBTValue: Label
    @FXML lateinit var lblBTUnit: Label
    @FXML lateinit var lblRoRBTValue: Label
    @FXML lateinit var lblRoRBTUnit: Label
    @FXML lateinit var lblETValue: Label
    @FXML lateinit var lblETUnit: Label
    @FXML lateinit var lblRoRETValue: Label
    @FXML lateinit var lblRoRETUnit: Label
    @FXML lateinit var lblDuration: Label
    @FXML lateinit var btnStart: Button
    @FXML lateinit var btnStop: Button
    @FXML lateinit var btnAbort: Button
    @FXML lateinit var btnRestartBbp: Button
    @FXML lateinit var btnStopBbp: Button
    @FXML lateinit var btnShortcuts: Button
    @FXML lateinit var btnSettings: Button
    @FXML lateinit var centerSplit: SplitPane
    @FXML lateinit var rightPanel: HBox
    @FXML lateinit var sidebarPanelContainer: StackPane
    @FXML lateinit var btnSidebarPlan: ToggleButton
    @FXML lateinit var btnSidebarProduction: ToggleButton
    @FXML lateinit var btnSidebarSupport: ToggleButton
    @FXML lateinit var btnSidebarAccount: ToggleButton
    @FXML lateinit var btnSync: Button
    @FXML lateinit var lblReferenceLabel: Label
    @FXML lateinit var referenceSummaryBox: VBox
    @FXML lateinit var lblRefDuration: Label
    @FXML lateinit var lblRefChargeTemp: Label
    @FXML lateinit var lblRefDropTemp: Label
    @FXML lateinit var lblRefDevTime: Label
    @FXML lateinit var lblRefDevTimeRatio: Label
    @FXML lateinit var referencePanel: VBox
    private enum class CommentListSource { REFERENCE, LIVE, BBP }
    private data class CommentListEntry(
        val title: String,
        val timeSec: Double?,
        val bt: Double?,
        val value: String? = null,
        val source: CommentListSource = CommentListSource.REFERENCE
    )
    @FXML lateinit var bbpPanel: VBox
    @FXML lateinit var lblBbpStatus: Label
    @FXML lateinit var lblBbpReference: Label
    @FXML lateinit var lblBbpMinBt: Label
    @FXML lateinit var lblBbpMaxBt: Label
    @FXML lateinit var lblBbpHint: Label
    @FXML lateinit var centerHBox: HBox
    @FXML lateinit var sliderDrawerPanel: VBox
    @FXML lateinit var btnShowSliders: Button
    @FXML lateinit var sliderLayoutCombo: ComboBox<SliderPanelLayoutMode>
    @FXML lateinit var customButtonsPanel: VBox
    @FXML lateinit var presetButtonsPanel: FlowPane
    @FXML lateinit var extraSensorReadouts: FlowPane
    @FXML lateinit var commentsBlock: VBox
    @FXML lateinit var valuesPanel: VBox
    @FXML lateinit var commentsListView: ListView<*>
    @FXML lateinit var lblCommentsHeader: Label
    @FXML lateinit var btnCommentsCollapse: Button
    @FXML lateinit var btnCommentsSettings: Button
    @FXML lateinit var rightPanelContent: VBox
    @FXML lateinit var timerHeroPanel: HBox
    @FXML lateinit var titleBar: HBox
    @FXML lateinit var roasterConnectionBox: HBox
    @FXML lateinit var roasterConnectionDot: Region
    @FXML lateinit var lblRoasterName: Label
    @FXML lateinit var btnMinimize: Button
    @FXML lateinit var btnMaximize: Button
    @FXML lateinit var btnClose: Button

    /** Auth UI in Account drawer (set in setupSidebarPanels). */
    private var authStatusLabel: Label? = null
    private var authButton: Button? = null
    private var drawerLogoutButton: Button? = null

    /** Roast Properties form in Production drawer (set in setupSidebarPanels). */
    private var roastPropertiesController: RoastPropertiesController? = null
    /** When user selects a schedule row, we complete it after upload. Cleared after complete or on clear. */
    private var selectedScheduleId: UUID? = null
    /** Callback to refresh schedule list (e.g. after complete). Set when building Production panel. */
    private var refreshScheduleList: (() -> Unit)? = null
    private var currentDrawerWidth: Double = 0.0
    private var drawerHost: StackPane? = null
    private var drawerCard: StackPane? = null
    private var drawerBackdrop: Region? = null
    private var drawerAnimation: ParallelTransition? = null
    private enum class RefCommentsSortMode { TIME, BT }
    private var refCommentsSortMode: RefCommentsSortMode = RefCommentsSortMode.TIME
    private var showRefCommentTime: Boolean = true
    private var showRefCommentValue: Boolean = true
    private var referenceCommentsCollapsed: Boolean = false

    /** Current reference/background profile (from file or server). Null = none. */
    private var referenceProfile: RoastProfile? = null

    /** Cropster-style: single observable list bound to comments ListView; update content with setAll(), never replace list reference. */
    private val commentsObservableList = FXCollections.observableArrayList<CommentListEntry>()

    // ── CurveModel pipeline ──────────────────────────────────────────────────
    private val btRaw = StandardCurveModel("BT")
    private val etRaw = StandardCurveModel("ET")

    private val initSmoothing = SettingsManager.load().rorSmoothing
    private var btSmooth = MovingAverageCurveModel(btRaw, windowSize = initSmoothing.movingAvgWindow)
    private var etSmooth = MovingAverageCurveModel(etRaw, windowSize = initSmoothing.movingAvgWindow)
    private var rorBt = RorCurveModel(btSmooth, windowMs = initSmoothing.rorWindowMs)
    private var rorEt = RorCurveModel(etSmooth, windowMs = initSmoothing.rorWindowMs)

    // ── Chart ────────────────────────────────────────────────────────────────
    private val curveChart = CurveChartFx()
    private val chartPanel = ChartPanelFx(curveChart)
    private val controlChart = ControlChartFx()
    /** Set when BBP annotation is applied for current roast (profile has betweenBatchLog); reset on clearChartAndBbpState(). */
    private var bbpAnnotationSetForCurrentRoast = false

    // ── App ──────────────────────────────────────────────────────────────────
    private val recorder = RoastRecorder(DataSourceFactory.create(SettingsManager.load().machineConfig))
    private val scope = CoroutineScope(Dispatchers.JavaFx + SupervisorJob())
    private var shouldAutoStartAfterConnect = false
    private var connectionStateJob: Job? = null
    private var reconnectJob: Job? = null
    private val controlDebounceJobs = mutableMapOf<String, Job>()
    private var sliderPanelVisible: Boolean = true
    private var detachableSliderWindow: DetachableSliderWindow? = null
    private var currentSliderPanel: ControlSliderPanel? = null
    private var lastConnectionState: ConnectionState? = null

    @FXML
    fun initialize() {
        // Bind CurveModels to chart series once; apply saved chart settings
        curveChart.bindDefaultCurves(btSmooth, etSmooth, rorBt, rorEt)
        val settings = SettingsManager.load()
        ChartThemeAdapter.apply(curveChart, controlChart, settings)
        curveChart.configureExtraSensors(settings.extraSensors)
        configureExtraSensorReadouts(settings.extraSensors)
        refreshCustomButtonsPanel()
        updateControlPanel(ConnectionState.Disconnected, recorder.dataSource)
        updateRoasterConnectionIndicator(ConnectionState.Disconnected)

        // Add ChartPanelFx and sidebar overlay to container (chart behind, drawer on top)
        chartPanel.prefWidthProperty().bind(chartContainer.widthProperty())
        chartPanel.prefHeightProperty().bind(chartContainer.heightProperty())
        chartContainer.children.setAll(chartPanel, sidebarPanelContainer)
        // Pane does not stretch children, so the overlay drawer follows the chart size explicitly.
        sidebarPanelContainer.layoutX = 0.0
        sidebarPanelContainer.layoutY = 0.0
        sidebarPanelContainer.prefWidthProperty().bind(chartContainer.widthProperty())
        sidebarPanelContainer.maxWidthProperty().bind(chartContainer.widthProperty())
        sidebarPanelContainer.prefHeightProperty().bind(chartContainer.heightProperty())
        sidebarPanelContainer.maxHeightProperty().bind(chartContainer.heightProperty())
        sidebarPanelContainer.isMouseTransparent = true

        // Control chart (Gas/Air/Drum step curves) under main chart
        if (::controlChartContainer.isInitialized) {
            val controlViewer = ChartViewer(controlChart.chart).apply {
                minWidth = 0.0
                minHeight = 0.0
                prefWidthProperty().bind(controlChartContainer.widthProperty())
                prefHeightProperty().bind(controlChartContainer.heightProperty())
            }
            controlChartContainer.children.setAll(controlViewer)
        }

        recorder.betweenBatchProtocolEnabled = SettingsManager.load().betweenBatchProtocolEnabled

        scope.launch {
            recorder.stateFlow.collect { state ->
                Platform.runLater { updateButtonStates(state) }
            }
        }

        // Feed each incoming sample into the CurveModel pipeline
        scope.launch {
            recorder.currentSample.collect { sample ->
                Platform.runLater { onSample(sample) }
            }
        }

        // Elapsed-time label: from Charge (00:00) after C, or "Preheat" before; in BBP show BBP timer
        scope.launch {
            kotlinx.coroutines.flow.combine(
                recorder.stateFlow,
                recorder.elapsedSec,
                recorder.bbpElapsedSec
            ) { state, elapsed, bbpSec -> Triple(state, elapsed, bbpSec) }.collect { (state, sec, bbpSec) ->
                Platform.runLater {
                    if (state == RecorderState.BBP) {
                        val session = recorder.currentBbpSession
                        if (session != null) {
                            curveChart.updateBbpCurves(session.timex.toList(), session.temp1.toList(), session.temp2.toList())
                            chartPanel.lastDataTimeMs = session.timex.lastOrNull()?.times(1000)?.toLong()
                        }
                    }
                    lblDuration.text = when (state) {
                        RecorderState.BBP -> {
                            val m = (bbpSec / 60).toInt()
                            val s = (bbpSec % 60).toInt()
                            "%02d:%02d".format(m, s)
                        }
                        else -> {
                            val profile = recorder.currentProfile.value
                            val chargeTimeSec = profile.eventByType(EventType.CHARGE)?.timeSec
                            if (chargeTimeSec != null) {
                                val fromCharge = (sec - chargeTimeSec).coerceAtLeast(0.0)
                                val m = (fromCharge / 60).toInt()
                                val s = (fromCharge % 60).toInt()
                                "%02d:%02d".format(m, s)
                            } else {
                                "Preheat"
                            }
                        }
                    }
                    updateBbpPanel()
                    updateTimerHeroState()
                    // Do not refresh comments here: elapsedSec/bbpElapsedSec tick often and would cause
                    // constant opacity=0 + FadeTransition flicker. Comments are refreshed on profile/state/reference changes.
                }
            }
        }

        // Control chart (Gas/Air/Drum): show roast control events; hide or clear in BBP mode
        scope.launch {
            combine(recorder.currentProfile, recorder.stateFlow) { p, s -> Pair(p, s) }.collect { (profile, state) ->
                Platform.runLater { updateControlChart(profile, state) }
            }
        }

        // Auth button and status are in the Account drawer panel (setupSidebarPanels)
        setupButtonHandlers()
        setupSettingsButton()
        if (::btnShowSliders.isInitialized) {
            btnShowSliders.setOnAction { toggleSliderPanelVisibility() }
            btnShowSliders.tooltip = Tooltip("Show/hide control sliders")
        }
        updateAuthUi()
        btnShortcuts.setOnAction { showShortcutsHelp() }
        setupSpaceHotkey()
        restoreDividerPositions()
        chartContainer.sceneProperty().addListener { _, _, scene ->
            if (scene != null) ensureTitleBarReady(scene)
        }
        setupSidebarPanels()
        updateAuthUi()
        updateReadoutUnits()
        chartContainer.sceneProperty().addListener { _, _, scene ->
            if (scene != null) ensureTitleBarReady(scene)
        }

        // Connect to configured roaster on startup (or run discovery if auto-detect enabled)
        connectOrDiscover()

        chartPanel.popupModeProvider = {
            if (recorder.stateFlow.value == RecorderState.BBP) ChartPopupMode.BBP else ChartPopupMode.ROAST
        }
        chartPanel.onPopupResult = { result ->
            when (result) {
                is ChartPopupEventResult -> {
                    val timeSec = result.timeMs / 1000.0
                    val profile = recorder.currentProfile.value
                    val idx = profile.timex.indexOfLast { it <= timeSec }.coerceAtLeast(0)
                    val btAtTime = profile.temp1.getOrNull(idx)
                    when {
                        result.label.startsWith("DE @") -> {
                            runEventCommand("DRY_END")
                            recorder.markEventAt(timeSec, EventType.CC)
                            curveChart.addEventMarker(result.timeMs, formatMarkerLabel(result.label, btAtTime))
                        }
                        result.label.startsWith("FC @") -> {
                            runEventCommand("FC_START")
                            recorder.markEventAt(timeSec, EventType.FC)
                            curveChart.addEventMarker(result.timeMs, formatMarkerLabel(result.label, btAtTime))
                        }
                        result.label.startsWith("SC @") -> {
                            runEventCommand("SC_START")
                            recorder.markEventAt(timeSec, EventType.SC)
                            curveChart.addEventMarker(result.timeMs, formatMarkerLabel(result.label, btAtTime))
                        }
                    }
                    updatePhaseStrip()
                    refreshCommentsList()
                }
                is ChartPopupCommentResult -> {
                    recorder.addCommentAt(
                        timeSec = result.timeMs / 1000.0,
                        text = result.text,
                        tempBT = result.tempBT,
                        gas = result.gas,
                        airflow = result.airflow
                    )
                    val displayTimeMs = if (recorder.stateFlow.value == RecorderState.BBP) {
                        result.timeMs
                    } else {
                        result.timeMs - curveChart.getChargeOffsetMs()
                    }
                    curveChart.addEventMarker(result.timeMs, buildCommentMarkerLabel(result, displayTimeMs))
                    refreshCommentsList()
                    updateBbpPanel()
                }
            }
        }
        if (::btnCommentsSettings.isInitialized) {
            btnCommentsSettings.graphic = FontIcon(FontAwesomeSolid.COG).apply { iconSize = 10 }
            btnCommentsSettings.setOnAction { openReferenceCommentsMenu() }
        }
        if (::btnCommentsCollapse.isInitialized && ::commentsListView.isInitialized) {
            referenceCommentsCollapsed = SettingsManager.load().referenceCommentsCollapsed
            updateCommentsCollapseState()
            btnCommentsCollapse.graphic = iconForCommentsCollapse()
            btnCommentsCollapse.tooltip = Tooltip(if (referenceCommentsCollapsed) "Expand" else "Collapse")
            btnCommentsCollapse.setOnAction {
                referenceCommentsCollapsed = !referenceCommentsCollapsed
                updateCommentsCollapseState()
                btnCommentsCollapse.graphic = iconForCommentsCollapse()
                btnCommentsCollapse.tooltip = Tooltip(if (referenceCommentsCollapsed) "Expand" else "Collapse")
                val s = SettingsManager.load()
                SettingsManager.save(s.copy(referenceCommentsCollapsed = referenceCommentsCollapsed))
            }
            setupCommentsListView()
            refreshCommentsList()
        }
        playRightPanelIntroAnimations()
        updateTimerHeroState()
    }

    private fun playRightPanelIntroAnimations() {
        val cards = buildList {
            add(commentsBlock)
            add(valuesPanel)
            if (::customButtonsPanel.isInitialized && customButtonsPanel.isManaged) add(customButtonsPanel)
        }
        cards.forEachIndexed { index, node ->
            node.opacity = 0.0
            node.translateY = 12.0
            ParallelTransition(
                FadeTransition(Duration.millis(260.0), node).apply {
                    delay = Duration.millis(index * 70.0)
                    fromValue = 0.0
                    toValue = 1.0
                },
                TranslateTransition(Duration.millis(260.0), node).apply {
                    delay = Duration.millis(index * 70.0)
                    fromY = 12.0
                    toY = 0.0
                }
            ).play()
        }
    }

    private fun updateTimerHeroState() {
        if (!::timerHeroPanel.isInitialized) return
        val styleClasses = timerHeroPanel.styleClass
        styleClasses.removeAll("timer-hero-preheat", "timer-hero-live", "timer-hero-bbp", "timer-hero-finished")
        val stateClass = when {
            recorder.stateFlow.value == RecorderState.BBP -> "timer-hero-bbp"
            recorder.stateFlow.value == RecorderState.STOPPED -> "timer-hero-finished"
            recorder.currentProfile.value.eventByType(EventType.CHARGE) == null -> "timer-hero-preheat"
            else -> "timer-hero-live"
        }
        styleClasses.add(stateClass)
    }

    private fun pulseTimerHero() {
        if (!::timerHeroPanel.isInitialized) return
        ScaleTransition(Duration.millis(180.0), timerHeroPanel).apply {
            fromX = 1.0
            fromY = 1.0
            toX = 1.025
            toY = 1.025
            cycleCount = 2
            isAutoReverse = true
            play()
        }
    }

    private fun iconForCommentsCollapse() = FontIcon(
        if (referenceCommentsCollapsed) FontAwesomeSolid.CHEVRON_DOWN else FontAwesomeSolid.CHEVRON_UP
    ).apply { iconSize = 10 }

    private fun updateCommentsCollapseState() {
        if (::commentsListView.isInitialized) {
            commentsListView.isVisible = !referenceCommentsCollapsed
            commentsListView.isManaged = !referenceCommentsCollapsed
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun commentsList(): ListView<CommentListEntry> = commentsListView as ListView<CommentListEntry>

    private fun setupCommentsListView() {
        val list = commentsList()
        list.placeholder = Label("No events or comments.").apply {
            styleClass.add("comments-placeholder")
        }
        list.setCellFactory {
            object : javafx.scene.control.ListCell<CommentListEntry>() {
                override fun updateItem(item: CommentListEntry?, empty: Boolean) {
                    super.updateItem(item, empty)
                    if (item == null || empty) {
                        graphic = null
                        text = null
                        styleClass.removeAll("ref-comment-card", "selected")
                    } else {
                        graphic = buildCommentCard(item)
                        text = null
                        setOnMouseClicked { _ ->
                            list.selectionModel.select(item)
                        }
                    }
                }
            }
        }
        list.selectionModel.selectionMode = SelectionMode.SINGLE
        list.items = commentsObservableList
    }

    private fun buildCommentCard(row: CommentListEntry): HBox {
        val isReference = row.source == CommentListSource.REFERENCE
        val timeText = when {
            isReference && !showRefCommentTime -> ""
            isReference && refCommentsSortMode == RefCommentsSortMode.BT -> row.bt?.let { "%.1f °C".format(it) } ?: "--"
            else -> row.timeSec?.let { formatSec(it) } ?: "--:--"
        }
        val btText = row.bt?.let { "%.1f °C".format(it) } ?: ""
        val valueText = when {
            !isReference -> row.value ?: ""
            showRefCommentValue -> row.value ?: ""
            else -> ""
        }
        val icon = when {
            row.title.startsWith("Gas", ignoreCase = true) -> FontIcon(FontAwesomeSolid.FIRE)
            row.title.startsWith("Air", ignoreCase = true) -> FontIcon(FontAwesomeSolid.FAN)
            row.title.startsWith("Drum", ignoreCase = true) -> FontIcon(FontAwesomeSolid.COG)
            row.title.startsWith("Damper", ignoreCase = true) -> FontIcon(FontAwesomeSolid.SLIDERS_H)
            row.title.startsWith("Dry", ignoreCase = true) -> FontIcon(FontAwesomeSolid.SEEDLING)
            row.title.startsWith("First crack", ignoreCase = true) -> FontIcon(FontAwesomeSolid.BELL)
            row.title.startsWith("Drop", ignoreCase = true) -> FontIcon(FontAwesomeSolid.STOP)
            else -> FontIcon(FontAwesomeSolid.COFFEE)
        }.apply { iconSize = 12; styleClass.add("ref-comment-icon") }
        val timeLabel = Label(timeText).apply { styleClass.add("ref-comment-time") }
        val tempLabel = Label(btText).apply { styleClass.add("ref-comment-temp") }
        val valueLabel = Label(valueText).apply { styleClass.add("ref-comment-value") }
        val kindLabelNode = Label(row.title).apply { styleClass.add("ref-comment-kind") }
        return HBox(4.0, icon, timeLabel, tempLabel, valueLabel, kindLabelNode).apply {
            alignment = Pos.CENTER_LEFT
            styleClass.add("ref-comment-card")
            padding = Insets(4.0, 6.0, 4.0, 6.0)
        }
    }

    private fun buildReferenceComments(profile: RoastProfile): List<CommentListEntry> {
        val charge = profile.eventByType(EventType.CHARGE)?.timeSec ?: 0.0
        val out = mutableListOf<CommentListEntry>()
        profile.events.forEach { event ->
            val relSec = (event.timeSec - charge).coerceAtLeast(0.0)
            val title = when (event.type) {
                EventType.CHARGE -> "Charge"
                EventType.TP -> "Turning point"
                EventType.CC -> "Dry end"
                EventType.FC -> "First crack"
                EventType.SC -> "Second crack"
                EventType.DROP -> "Drop"
            }
            out += CommentListEntry(title = title, timeSec = relSec, bt = event.tempBT, source = CommentListSource.REFERENCE)
        }
        profile.controlEvents.forEach { ce ->
            val relSec = (ce.timeSec - charge).coerceAtLeast(0.0)
            val title = when (ce.type) {
                ControlEventType.GAS -> "Gas"
                ControlEventType.AIR -> "Airflow"
                ControlEventType.DRUM -> "Drum"
                ControlEventType.DAMPER -> "Damper"
                ControlEventType.BURNER -> "Burner"
            }
            val display = ce.displayString?.takeIf { it.isNotBlank() } ?: formatNumeric(ce.value)
            val idx = profile.timex.indexOfLast { it <= ce.timeSec }.coerceAtLeast(0)
            val bt = profile.temp1.getOrNull(idx)
            out += CommentListEntry(title = title, timeSec = relSec, bt = bt, value = display, source = CommentListSource.REFERENCE)
        }
        return when (refCommentsSortMode) {
            RefCommentsSortMode.TIME -> out.sortedBy { it.timeSec ?: Double.MAX_VALUE }
            RefCommentsSortMode.BT -> out.sortedBy { it.bt ?: Double.MAX_VALUE }
        }
    }

    private fun buildRoastCommentEntries(profile: RoastProfile): List<CommentListEntry> {
        val eventEntries = synchronized(profile.events) {
            profile.events.map { event ->
                val title = when (event.type) {
                    EventType.CHARGE -> "Charge"
                    EventType.TP -> "Turning point"
                    EventType.CC -> "Dry end"
                    EventType.FC -> "First crack"
                    EventType.SC -> "Second crack"
                    EventType.DROP -> "Drop"
                }
                CommentListEntry(title = title, timeSec = event.timeSec, bt = event.tempBT, source = CommentListSource.LIVE)
            }
        }
        val commentEntries = synchronized(profile.comments) {
            profile.comments.map { comment ->
                CommentListEntry(
                    title = "Comment",
                    timeSec = comment.timeSec,
                    bt = comment.tempBT,
                    value = formatCommentEntry(comment),
                    source = CommentListSource.LIVE
                )
            }
        }
        return (eventEntries + commentEntries).sortedBy { it.timeSec ?: Double.MAX_VALUE }
    }

    private fun buildBbpCommentEntries(): List<CommentListEntry> {
        val session = recorder.currentBbpSession ?: return emptyList()
        return session.comments.sortedBy { it.timeSec }.map { comment ->
            CommentListEntry(
                title = "Between batch",
                timeSec = comment.timeSec,
                bt = comment.tempBT,
                value = formatCommentEntry(comment),
                source = CommentListSource.BBP
            )
        }
    }

    private fun refreshCommentsList() {
        if (!::commentsListView.isInitialized) return
        val showCommentsBlock = referenceProfile != null
        if (::commentsBlock.isInitialized) {
            commentsBlock.isVisible = showCommentsBlock
            commentsBlock.isManaged = showCommentsBlock
        }
        if (!showCommentsBlock) return
        val list = commentsList()
        val state = recorder.stateFlow.value
        val rows = when {
            state == RecorderState.BBP -> {
                lblCommentsHeader.text = "Events & comments"
                buildBbpCommentEntries().takeLast(10)
            }
            referenceProfile != null -> {
                lblCommentsHeader.text = "Reference comments"
                buildReferenceComments(referenceProfile!!)
            }
            state == RecorderState.RECORDING || state == RecorderState.STOPPED -> {
                lblCommentsHeader.text = "Events & comments"
                buildRoastCommentEntries(recorder.currentProfile.value).takeLast(10)
            }
            else -> {
                lblCommentsHeader.text = "Events & comments"
                emptyList()
            }
        }
        if (::btnCommentsSettings.isInitialized) {
            btnCommentsSettings.isDisable = referenceProfile == null || state == RecorderState.RECORDING || state == RecorderState.BBP
        }
        // Cropster-style: update the same observable list (no new list reference), so ListView does not re-bind and does not flicker.
        commentsObservableList.setAll(rows)
    }

    private fun formatCommentEntry(comment: ProtocolComment): String {
        val parts = mutableListOf<String>()
        comment.gas?.let { parts += "Gas ${formatNumeric(it)}" }
        comment.airflow?.let { parts += "Airflow ${formatNumeric(it)}" }
        comment.text.takeIf { it.isNotBlank() }?.let { parts += it }
        return parts.joinToString(" · ").ifBlank { "Comment" }
    }

    private fun formatNumeric(value: Double): String {
        val whole = value.toInt().toDouble()
        return if (kotlin.math.abs(value - whole) < 0.001) whole.toInt().toString() else "%.1f".format(value)
    }

    private fun formatMarkerLabel(base: String, btAtTime: Double?): String =
        btAtTime?.let { "$base · %.1f °C".format(it) } ?: base

    private fun buildCommentMarkerLabel(comment: ChartPopupCommentResult, displayTimeMs: Long): String {
        val parts = mutableListOf<String>()
        comment.gas?.let { parts += "Gas ${formatNumeric(it)}" }
        comment.airflow?.let { parts += "Airflow ${formatNumeric(it)}" }
        comment.text.takeIf { it.isNotBlank() }?.let { parts += it }
        val suffix = parts.joinToString(" · ").ifBlank { "Comment" }
        return "Comment @ ${formatSec(displayTimeMs / 1000.0)} · $suffix"
    }

    private fun updateBbpPanel() {
        if (!::bbpPanel.isInitialized) return
        val state = recorder.stateFlow.value
        val visible = state == RecorderState.BBP
        bbpPanel.isVisible = visible
        bbpPanel.isManaged = visible
        if (!visible) return
        val session = recorder.currentBbpSession
        val bbpState = recorder.bbpService.stateFlow.value
        val referenceHasBbp = referenceProfile?.betweenBatchLog != null
        lblBbpStatus.text = when (bbpState) {
            com.rdr.roast.app.BbpState.STOPPED -> "Stopped. Click Start roast to continue."
            com.rdr.roast.app.BbpState.RECORDING -> "Recording between batch data"
            else -> "Between batch protocol"
        }
        lblBbpReference.text = if (referenceHasBbp) {
            "Reference between batch shown."
        } else {
            "No reference between batch data."
        }
        if (session != null && session.temp1.isNotEmpty()) {
            val lowIdx = session.temp1.indices.minByOrNull { session.temp1[it] }
            val highIdx = session.temp1.indices.maxByOrNull { session.temp1[it] }
            lblBbpMinBt.text = lowIdx?.let { idx ->
                "Lowest BT: %.1f °C @ %s".format(session.temp1[idx], formatSec(session.timex.getOrElse(idx) { 0.0 }))
            } ?: "Lowest BT: —"
            lblBbpMaxBt.text = highIdx?.let { idx ->
                "Highest BT: %.1f °C @ %s".format(session.temp1[idx], formatSec(session.timex.getOrElse(idx) { 0.0 }))
            } ?: "Highest BT: —"
        } else {
            lblBbpMinBt.text = "Lowest BT: —"
            lblBbpMaxBt.text = "Highest BT: —"
        }
        val gasCount = session?.comments?.count { it.gas != null } ?: 0
        val airCount = session?.comments?.count { it.airflow != null } ?: 0
        val stats = buildList {
            if (gasCount > 0) add("Gas changes: $gasCount")
            if (airCount > 0) add("Air changes: $airCount")
        }
        lblBbpHint.text = if (stats.isNotEmpty()) {
            stats.joinToString(" · ") + "\nClick the chart to add a comment, gas or airflow."
        } else {
            "Click the chart to add a comment, gas or airflow."
        }
    }

    // ── Sample handling ───────────────────────────────────────────────────────

    private fun tempForDisplay(celsius: Double): Pair<Double, String> {
        val unit = SettingsManager.load().unit.uppercase()
        return if (unit == "F") (celsius * 9 / 5 + 32) to "°F" else celsius to "°C"
    }

    private fun onSample(sample: TemperatureSample?) {
        if (sample == null) return
        val timeMs = (sample.timeSec * 1000).toLong()
        val profile = recorder.currentProfile.value

        btRaw.put(timeMs, sample.bt)
        etRaw.put(timeMs, sample.et)

        for ((ch, value) in sample.extras) {
            curveChart.addExtraSensorPoint(ch, timeMs, value)
            extraSensorLabels[ch]?.text = "%.1f".format(value)
        }

        val (btD, u) = tempForDisplay(sample.bt)
        val (etD, _) = tempForDisplay(sample.et)
        val rorBtVal = rorBt.getValue(timeMs) ?: 0.0
        val rorEtVal = rorEt.getValue(timeMs) ?: 0.0
        val isF = u == "°F"
        val rorMul = if (isF) 9.0 / 5.0 else 1.0
        val rorUnit = if (isF) " °F/min" else " °C/min"
        lblBTValue.text = "%.1f".format(btD)
        lblBTUnit.text = " $u"
        lblETValue.text = "%.1f".format(etD)
        lblETUnit.text = " $u"
        lblRoRBTValue.text = "%.1f".format(rorBtVal * rorMul)
        lblRoRBTUnit.text = rorUnit
        lblRoRETValue.text = "%.1f".format(rorEtVal * rorMul)
        lblRoRETUnit.text = rorUnit
        chartPanel.lastDataTimeMs = timeMs

        // TP once: min BT after charge in [charge, charge+120s], or [0, 180s] if no charge
        val chargeTimeSec = profile.eventByType(EventType.CHARGE)?.timeSec
        val tpSearchDone = chargeTimeSec != null && profile.timex.lastOrNull() != null &&
            profile.timex.last() >= chargeTimeSec + TP_WINDOW_AFTER_CHARGE_SEC
        val tpSearchDoneNoCharge = chargeTimeSec == null && profile.timex.isNotEmpty() && profile.timex.last() >= TP_WINDOW_SEC
        if (!tpMarkerPlaced && (tpSearchDone || tpSearchDoneNoCharge)) {
            val tpIdx = findTurningPointIndex(
                profile,
                chargeTimeSec = chargeTimeSec,
                searchWindowAfterChargeSec = TP_WINDOW_AFTER_CHARGE_SEC,
                fallbackWindowSec = TP_WINDOW_SEC
            )
            if (tpIdx != null && profile.temp1.size > tpIdx) {
                tpMarkerPlaced = true
                val tpSec = profile.timex[tpIdx]
                val tpBt = profile.temp1[tpIdx]
                recorder.markEventAt(tpSec, EventType.TP)
                curveChart.addEventMarker((tpSec * 1000).toLong(), "TP @ %s · %.1f °C".format(formatSec(tpSec), tpBt),
                    com.rdr.roast.ui.chart.CurveChartFx.COLOR_MARKER)
                refreshCommentsList()
            }
        }

        // Auto-Charge detection (throttled every 5 samples)
        val settings = SettingsManager.load()
        if (settings.eventButtonsConfig.autoMarkCharge &&
            profile.eventByType(EventType.CHARGE) == null &&
            profile.timex.size > 30 &&
            autoChargeCheckCounter++ % 5 == 0) {
            val chargeIdx = com.rdr.roast.domain.metrics.findChargeDropIndex(profile)
            if (chargeIdx != null) {
                triggerCharge()
            }
        }

        // Auto Dry End by temperature threshold
        if (settings.eventButtonsConfig.autoMarkDryEnd &&
            settings.eventButtonsConfig.autoMarkDryEndTemp != null &&
            profile.eventByType(EventType.CHARGE) != null &&
            profile.eventByType(EventType.CC) == null &&
            sample.bt >= settings.eventButtonsConfig.autoMarkDryEndTemp!!) {
            triggerDryEnd()
        }

        // Auto First Crack by temperature threshold
        if (settings.eventButtonsConfig.autoMarkFirstCrack &&
            settings.eventButtonsConfig.autoMarkFirstCrackTemp != null &&
            profile.eventByType(EventType.CC) != null &&
            profile.eventByType(EventType.FC) == null &&
            sample.bt >= settings.eventButtonsConfig.autoMarkFirstCrackTemp!!) {
            triggerFirstCrack()
        }

        // Hint: if BT > 200°C and Charge not yet marked
        if (!chargeHintShown &&
            sample.bt > 200.0 &&
            profile.eventByType(EventType.CHARGE) == null &&
            recorder.stateFlow.value == RecorderState.RECORDING) {
            chargeHintShown = true
            lblDuration.text = "Press C for Charge"
        }

        updatePhaseStrip()
        updateBbpPanel()
        updateControlChart(recorder.currentProfile.value, recorder.stateFlow.value)
    }

    private fun updateControlChart(profile: RoastProfile, state: RecorderState) {
        if (state == RecorderState.BBP) {
            curveChart.updateControlEvents(emptyList(), 0.0)
        } else {
            val chargeOffsetSec = profile.eventByType(EventType.CHARGE)?.timeSec ?: 0.0
            curveChart.updateControlEvents(profile.controlEvents, chargeOffsetSec)
        }
    }

    private fun updatePhaseStrip() {
        val profile = recorder.currentProfile.value
        if (profile.timex.isEmpty()) return
        // Не строим live phase strip, пока нет события CHARGE — до этого показывается только reference-полоса.
        if (profile.eventByType(EventType.CHARGE) == null) return
        if (recorder.stateFlow.value == RecorderState.RECORDING && profile.betweenBatchLog != null && !bbpAnnotationSetForCurrentRoast) {
            curveChart.setBetweenBatchAnnotation(profile.betweenBatchLog!!.durationMs)
            bbpAnnotationSetForCurrentRoast = true
        }
        val endMs = (profile.timex.maxOrNull()!! * 1000).toLong()
        val deMs = profile.eventByType(EventType.CC)?.timeSec?.times(1000)?.toLong()
        val fcMs = profile.eventByType(EventType.FC)?.timeSec?.times(1000)?.toLong()
        val dropMs = profile.eventByType(EventType.DROP)?.timeSec?.times(1000)?.toLong()
        curveChart.updatePhaseStrip(endMs, deMs, fcMs, dropMs)
    }

    private var tpMarkerPlaced = false
    private var autoChargeCheckCounter = 0
    private var chargeHintShown = false

    /** Maps extra sensor channel index → value Label for dynamic LCD readouts. */
    private val extraSensorLabels = mutableMapOf<Int, Label>()

    private fun clearChartAndBbpState() {
        curveChart.clearAll()
        bbpAnnotationSetForCurrentRoast = false
        chartPanel.lastDataTimeMs = null
        tpMarkerPlaced = false
        autoChargeCheckCounter = 0
        chargeHintShown = false
        updateControlChart(recorder.currentProfile.value, recorder.stateFlow.value)
        refreshCommentsList()
        updateBbpPanel()
    }

    private companion object {
        const val TP_WINDOW_SEC = 180.0
        const val TP_WINDOW_AFTER_CHARGE_SEC = 120.0
    }

    private fun startConnectionStateCollector() {
        connectionStateJob?.cancel()
        connectionStateJob = scope.launch {
            recorder.dataSource.connectionState().collect { state ->
                Platform.runLater { updateConnectionStatus(state) }
                if (state is ConnectionState.Disconnected &&
                    lastConnectionState is ConnectionState.Connected) {
                    reconnectJob?.cancel()
                    reconnectJob = scope.launch {
                        repeat(10) { attempt ->
                            delay(3000)
                            if (recorder.dataSource.connectionState().value is ConnectionState.Connected) return@launch
                            try { recorder.connect() } catch (_: Exception) {}
                        }
                    }
                }
            }
        }
    }

    private fun connectOrDiscover() {
        // Big-bang cutover: old auto-discovery flow removed; use canonical configured runtime.
        recorder.connect()
        startConnectionStateCollector()
    }

    private fun applyDetectedConfig(config: MachineConfig) {
        recorder.dataSource = DataSourceFactory.create(config)
    }

    private fun persistLastDetected(config: MachineConfig) {
        val s = SettingsManager.load()
        SettingsManager.save(s.copy(lastDetectedConfig = config))
    }

    /** Applies main stylesheets and appearance (accent, scale, etc.) to a scene used by a secondary window. */
    private fun applySceneStyle(scene: Scene) {
        chartContainer.scene?.stylesheets?.let { scene.stylesheets.setAll(it) }
        if (scene.stylesheets.isEmpty()) {
            javaClass.getResource("/com/rdr/roast/ui/main.css")?.toExternalForm()?.let { scene.stylesheets.add(it) }
            javaClass.getResource("/css/appearance.css")?.toExternalForm()?.let { scene.stylesheets.add(it) }
        }
        AppearanceSupport.applyToScene(scene)
    }

    /** Shows dialog to choose one of the detected roasters. Returns true if user selected one and we connected. */
    private fun showDetectedChoiceDialog(detected: List<DetectedRoaster>): Boolean {
        var applied = false
        val listView = ListView<String>().apply {
            styleClass.add("secondary-list")
            items.addAll(detected.map { it.displayLabel() })
            prefHeight = 220.0
            prefWidth = 340.0
        }
        val stage = Stage().apply {
            title = "Выберите ростер"
            scene = Scene(listView, 340.0, 220.0)
            applySceneStyle(scene)
            initModality(Modality.APPLICATION_MODAL)
            initOwner(btnStart.scene?.window)
        }
        listView.setOnMouseClicked { ev ->
            if (ev.clickCount == 2) {
                val idx = listView.selectionModel.selectedIndex
                if (idx in detected.indices) {
                    applied = true
                    val config = detected[idx].config
                    applyDetectedConfig(config)
                    recorder.connect()
                    startConnectionStateCollector()
                    persistLastDetected(config)
                    stage.close()
                }
            }
        }
        stage.showAndWait()
        return applied
    }

    private fun updateConnectionStatus(state: ConnectionState) {
        lastConnectionState = state
        updateControlPanel(state, recorder.dataSource)
        updateRoasterConnectionIndicator(state)
    }

    private fun updateRoasterConnectionIndicator(state: ConnectionState?) {
        if (!::roasterConnectionBox.isInitialized) return
        val settings = SettingsManager.load()
        val name = listOf(settings.presetBrand, settings.presetModel).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "—" }
        lblRoasterName.text = name
        if (state is ConnectionState.Connected) {
            if (!roasterConnectionBox.styleClass.contains("connected")) roasterConnectionBox.styleClass.add("connected")
        } else {
            roasterConnectionBox.styleClass.removeAll(listOf("connected"))
        }
    }

    private val sliderDrawerWidthSingle = 200.0
    private val sliderDrawerWidthAll = 300.0

    private fun setupSliderLayoutCombo(currentMode: SliderPanelLayoutMode) {
        if (!::sliderLayoutCombo.isInitialized) return
        sliderLayoutCombo.items.setAll(SliderPanelLayoutMode.SINGLE_COLUMN_TOGGLE, SliderPanelLayoutMode.GRID_ALL)
        sliderLayoutCombo.value = currentMode
        sliderLayoutCombo.converter = object : javafx.util.StringConverter<SliderPanelLayoutMode>() {
            override fun toString(mode: SliderPanelLayoutMode?) = when (mode) {
                SliderPanelLayoutMode.SINGLE_COLUMN_TOGGLE -> "One at a time"
                SliderPanelLayoutMode.GRID_ALL -> "All visible"
                null -> ""
            }
            override fun fromString(s: String?) = when (s) {
                "All visible" -> SliderPanelLayoutMode.GRID_ALL
                else -> SliderPanelLayoutMode.SINGLE_COLUMN_TOGGLE
            }
        }
        sliderLayoutCombo.tooltip = Tooltip("Slider layout: one at a time or all three visible")
        sliderLayoutCombo.setOnAction {
            val v = sliderLayoutCombo.value ?: return@setOnAction
            SettingsManager.save(SettingsManager.load().copy(sliderPanelLayoutMode = v))
            lastConnectionState?.let { updateControlPanel(it, recorder.dataSource) }
        }
    }

    private val rightPanelBaseWidth = 302.0

    /** Slider drawer is inside the rightPanel HBox. Opening it expands rightPanel to make room; the SplitPane divider pushes left naturally. */
    private fun showDrawer() {
        if (!::sliderDrawerPanel.isInitialized) return
        val layoutMode = SettingsManager.load().sliderPanelLayoutMode
        val w = if (layoutMode == SliderPanelLayoutMode.SINGLE_COLUMN_TOGGLE) sliderDrawerWidthSingle else sliderDrawerWidthAll
        sliderDrawerPanel.prefWidth = w
        sliderDrawerPanel.minWidth = w
        sliderDrawerPanel.isVisible = true
        sliderDrawerPanel.isManaged = true
        if (::rightPanel.isInitialized) {
            val totalWidth = rightPanelBaseWidth + w
            rightPanel.minWidth = totalWidth
            rightPanel.prefWidth = totalWidth
            rightPanel.maxWidth = totalWidth
        }
    }

    private fun hideDrawer() {
        if (!::sliderDrawerPanel.isInitialized) return
        sliderDrawerPanel.prefWidth = 0.0
        sliderDrawerPanel.minWidth = 0.0
        sliderDrawerPanel.isVisible = false
        sliderDrawerPanel.isManaged = false
        if (::rightPanel.isInitialized) {
            rightPanel.minWidth = rightPanelBaseWidth
            rightPanel.prefWidth = rightPanelBaseWidth
            rightPanel.maxWidth = rightPanelBaseWidth
        }
    }

    private fun updateControlPanel(state: ConnectionState, source: com.rdr.roast.driver.RoastDataSource) {
        if (!::sliderDrawerPanel.isInitialized) return
        controlDebounceJobs.values.forEach { it.cancel() }
        controlDebounceJobs.clear()
        sliderDrawerPanel.children.clear()
        currentSliderPanel = null

        val settings = SettingsManager.load()
        val driverHasControl = state is ConnectionState.Connected &&
            source is RoastControl &&
            source.supportsControl()
        val hasPresetSliders = settings.presetSliders.any { it.visible && it.label.isNotBlank() }

        if (!driverHasControl && !hasPresetSliders) {
            detachableSliderWindow?.hideWindow()
            hideDrawer()
            if (::btnShowSliders.isInitialized) btnShowSliders.isVisible = false
            if (::sliderLayoutCombo.isInitialized) {
                sliderLayoutCombo.isVisible = false
                sliderLayoutCombo.isManaged = false
            }
            return
        }
        if (::btnShowSliders.isInitialized) {
            btnShowSliders.isVisible = true
            btnShowSliders.isManaged = true
        }
        if (::sliderLayoutCombo.isInitialized) {
            sliderLayoutCombo.isVisible = true
            sliderLayoutCombo.isManaged = true
        }

        if (::sliderLayoutCombo.isInitialized) setupSliderLayoutCombo(settings.sliderPanelLayoutMode)
        val layoutMode = settings.sliderPanelLayoutMode

        val panel: ControlSliderPanel?

        if (hasPresetSliders) {
            panel = ControlSliderPanel.fromPreset(
                presetSliders = settings.presetSliders,
                layoutMode = layoutMode,
                onCommand = { command, _ ->
                    val runner = recorder.dataSource as? ModbusCommandRunner
                    if (runner != null) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                ModbusCommandExecutor.execute(runner, command)
                            } catch (e: Exception) {
                                Platform.runLater { ErrorLogBuffer.append(e, "Preset slider command") }
                            }
                        }
                    }
                }
            )
        } else if (driverHasControl) {
            val control = source as RoastControl
            val sliderSpecs = control.controlSpecs().filter { it.type == ControlSpec.ControlType.SLIDER }
            if (sliderSpecs.isEmpty()) {
                hideDrawer()
                return
            }
            panel = ControlSliderPanel(
                control = control,
                eventQuantifiers = settings.eventQuantifiers,
                layoutMode = layoutMode,
                sliderStepConfig = settings.sliderStepConfig,
                onValueChanged = { specId, value, eventType, displayString ->
                    val timeAtAction = when (recorder.stateFlow.value) {
                        RecorderState.BBP -> recorder.bbpElapsedSec.value
                        else -> recorder.elapsedSec.value
                    }
                    controlDebounceJobs[specId]?.cancel()
                    controlDebounceJobs[specId] = scope.launch {
                        delay(300L)
                        controlDebounceJobs.remove(specId)
                        (recorder.dataSource as? RoastControl)?.let { rc ->
                            scope.launch(Dispatchers.IO) {
                                try { rc.setControl(specId, value) } catch (_: Exception) { }
                            }
                        }
                        if (recorder.stateFlow.value == RecorderState.RECORDING || recorder.stateFlow.value == RecorderState.BBP) {
                            recorder.addControlEvent(timeAtAction, eventType, value, displayString)
                            curveChart.addControlEventMarker(
                                (timeAtAction * 1000).toLong(),
                                eventType, value, displayString
                            )
                        }
                    }
                }
            )
        } else {
            hideDrawer()
            return
        }

        if (panel == null) {
            hideDrawer()
            return
        }
        currentSliderPanel = panel
        if (detachableSliderWindow == null) {
            detachableSliderWindow = DetachableSliderWindow(
                panelProvider = { currentSliderPanel ?: panel },
                onAttachRequest = {
                    SettingsManager.save(SettingsManager.load().copy(sliderPanelDetached = false))
                    lastConnectionState?.let { updateControlPanel(it, recorder.dataSource) }
                },
                onHidden = { sliderPanelVisible = false }
            )
        }
        val detached = settings.sliderPanelDetached
        if (detached) {
            hideDrawer()
            if (sliderPanelVisible) {
                detachableSliderWindow!!.showDetached(chartContainer.scene?.window)
                detachableSliderWindow!!.updateContent(panel)
                chartContainer.scene?.stylesheets?.let { detachableSliderWindow!!.applyStylesheets(it) }
            }
        } else {
            sliderDrawerPanel.children.setAll(panel)
            VBox.setVgrow(panel, Priority.ALWAYS)
            if (sliderPanelVisible) {
                showDrawer()
            } else {
                hideDrawer()
            }
        }
    }

    private fun toggleSliderPanelVisibility() {
        if (!::sliderDrawerPanel.isInitialized) return
        sliderPanelVisible = !sliderPanelVisible
        val settings = SettingsManager.load()
        if (settings.sliderPanelDetached) {
            if (sliderPanelVisible) {
                // Always refresh so detached window gets current panel content.
                lastConnectionState?.let { updateControlPanel(it, recorder.dataSource) }
                detachableSliderWindow?.showDetached(chartContainer.scene?.window)
                chartContainer.scene?.stylesheets?.let { detachableSliderWindow?.applyStylesheets(it) }
            } else {
                detachableSliderWindow?.hideWindow()
            }
        } else {
            if (sliderPanelVisible) {
                if (sliderDrawerPanel.children.isEmpty()) {
                    lastConnectionState?.let { updateControlPanel(it, recorder.dataSource) }
                } else {
                    showDrawer()
                }
            } else {
                hideDrawer()
            }
        }
    }

    /**
     * Create LCD readout cards for each extra sensor channel that has lcdVisible=true.
     * Each card shows: label + value. Updated on each sample via [extraSensorLabels].
     */
    private fun configureExtraSensorReadouts(configs: List<com.rdr.roast.app.ExtraSensorChannelConfig>) {
        if (!::extraSensorReadouts.isInitialized) return
        extraSensorReadouts.children.clear()
        extraSensorLabels.clear()

        var channelOffset = 0
        for (cfg in configs) {
            if (cfg.lcdVisible1 && cfg.label1.isNotBlank()) {
                val ch = channelOffset
                val valueLabel = Label("--.-").apply {
                    styleClass.add("readout-value-extra")
                    style = "-fx-text-fill: ${cfg.color1};"
                }
                val card = VBox(3.0).apply {
                    styleClass.addAll("metric-card", "metric-card-extra")
                    alignment = Pos.CENTER_LEFT
                    children.addAll(
                        HBox(2.0).apply {
                            alignment = Pos.BASELINE_LEFT
                            children.addAll(valueLabel, Label(" °C").apply { styleClass.add("readout-unit-extra") })
                        },
                        Label(cfg.label1).apply { styleClass.add("readout-label-extra") }
                    )
                }
                extraSensorReadouts.children.add(card)
                extraSensorLabels[ch] = valueLabel
            }
            if (cfg.lcdVisible2 && cfg.label2.isNotBlank()) {
                val ch = channelOffset + 1
                val valueLabel = Label("--.-").apply {
                    styleClass.add("readout-value-extra")
                    style = "-fx-text-fill: ${cfg.color2};"
                }
                val card = VBox(3.0).apply {
                    styleClass.addAll("metric-card", "metric-card-extra")
                    alignment = Pos.CENTER_LEFT
                    children.addAll(
                        HBox(2.0).apply {
                            alignment = Pos.BASELINE_LEFT
                            children.addAll(valueLabel, Label(" °C").apply { styleClass.add("readout-unit-extra") })
                        },
                        Label(cfg.label2).apply { styleClass.add("readout-label-extra") }
                    )
                }
                extraSensorReadouts.children.add(card)
                extraSensorLabels[ch] = valueLabel
            }
            channelOffset += 2
        }
        val hasReadouts = extraSensorReadouts.children.isNotEmpty()
        extraSensorReadouts.isVisible = hasReadouts
        extraSensorReadouts.isManaged = hasReadouts
    }

    /** Build custom event buttons from settings. Populates both the under-chart FlowPane (presetButtonsPanel) and legacy right-panel VBox. */
    private data class ButtonPair(
        val baseName: String,
        val onConfig: com.rdr.roast.app.CustomButtonConfig,
        val offConfig: com.rdr.roast.app.CustomButtonConfig
    )

    private fun normalizeButtonLabel(label: String): String {
        return label
            .replace("\n", " ")
            .replace(Regex("(?i)\\s*(ON|OFF|OPEN|CLOSE)\\s*"), " ")
            .trim()
    }

    /** Capitalize first letter for display: "cooler" -> "Cooler". */
    private fun capitalizeName(name: String): String {
        return name.replaceFirstChar { if (it.isLowerCase()) it.uppercase() else it.toString() }
    }

    /** Font size so long labels fit; minWidth in px so "Name Off"/"Name On" is not clipped. */
    private fun buttonFontSizeAndMinWidth(baseName: String): Pair<Int, Double> {
        val len = baseName.length + 4
        val fontSize = when {
            len > 20 -> 8
            len > 14 -> 9
            else -> 10
        }
        val minWidth = (len * 7.2).coerceIn(72.0, 220.0)
        return fontSize to minWidth
    }

    /** Play a short "rocker" scale animation on toggle. */
    private fun playRockerAnimation(node: javafx.scene.Node) {
        ScaleTransition(Duration.millis(120.0), node).apply {
            fromX = 1.0; fromY = 1.0
            toX = 0.96; toY = 0.96
            cycleCount = 2
            isAutoReverse = true
            play()
        }
    }

    private fun isOnButton(label: String): Boolean {
        return label.contains(Regex("(?i)\\bON\\b")) || label.contains(Regex("(?i)\\bOPEN\\b"))
    }

    private fun isOffButton(label: String): Boolean {
        return label.contains(Regex("(?i)\\bOFF\\b")) || label.contains(Regex("(?i)\\bCLOSE\\b"))
    }

    private fun refreshCustomButtonsPanel() {
        val hasPresetPanel = ::presetButtonsPanel.isInitialized
        val hasLegacyPanel = ::customButtonsPanel.isInitialized
        if (!hasPresetPanel && !hasLegacyPanel) return

        val settings = SettingsManager.load()
        val buttons = settings.customButtons.filter { it.visibility }

        if (hasLegacyPanel) {
            customButtonsPanel.children.clear()
            customButtonsPanel.isVisible = false
            customButtonsPanel.isManaged = false
        }
        if (hasPresetPanel) {
            presetButtonsPanel.children.clear()
        }

        if (buttons.isEmpty() || !settings.eventButtonsConfig.eventButtonEnabled) {
            if (hasPresetPanel) {
                presetButtonsPanel.isVisible = false
                presetButtonsPanel.isManaged = false
            }
            return
        }

        val showTooltip = settings.eventButtonsConfig.tooltips

        val pairs = mutableListOf<ButtonPair>()
        val standalone = mutableListOf<com.rdr.roast.app.CustomButtonConfig>()
        val consumed = mutableSetOf<Int>()

        for (i in buttons.indices) {
            if (i in consumed) continue
            val cfg = buttons[i]
            val baseName = normalizeButtonLabel(cfg.label)
            val isOn = isOnButton(cfg.label)

            if (isOn && i + 1 < buttons.size) {
                val nextCfg = buttons[i + 1]
                val nextBase = normalizeButtonLabel(nextCfg.label)
                if (nextBase.equals(baseName, ignoreCase = true) && isOffButton(nextCfg.label)) {
                    pairs.add(ButtonPair(baseName, cfg, nextCfg))
                    consumed.add(i)
                    consumed.add(i + 1)
                    continue
                }
            }
            if (!isOn && isOffButton(cfg.label) && i + 1 < buttons.size) {
                val nextCfg = buttons[i + 1]
                val nextBase = normalizeButtonLabel(nextCfg.label)
                if (nextBase.equals(baseName, ignoreCase = true) && isOnButton(nextCfg.label)) {
                    pairs.add(ButtonPair(baseName, nextCfg, cfg))
                    consumed.add(i)
                    consumed.add(i + 1)
                    continue
                }
            }
            standalone.add(cfg)
        }

        fun executeCommand(cmd: String, label: String) {
            if (cmd.isEmpty()) return
            val runner = recorder.dataSource as? ModbusCommandRunner
            if (runner == null) {
                ErrorLogBuffer.append(IllegalStateException("Not connected to Modbus"), "Button \"$label\"")
                return
            }
            scope.launch(Dispatchers.IO) {
                try {
                    ModbusCommandExecutor.execute(runner, cmd)
                } catch (e: Exception) {
                    Platform.runLater { ErrorLogBuffer.append(e, "Button \"$label\"") }
                }
            }
        }

        for (pair in pairs) {
            val onCmd = (pair.onConfig.documentation.ifBlank { pair.onConfig.commandString }).trim()
            val offCmd = (pair.offConfig.documentation.ifBlank { pair.offConfig.commandString }).trim()
            val displayName = capitalizeName(pair.baseName)
            val (fontSize, minW) = buttonFontSizeAndMinWidth(pair.baseName)
            val offBg = pair.offConfig.backgroundColor
            val offText = pair.offConfig.textColor
            val toggle = ToggleButton(displayName).apply {
                styleClass.add("preset-toggle-btn")
                text = "$displayName Off"
                minWidth = minW
                isMnemonicParsing = false
                style = "-fx-font-size: ${fontSize}px; -fx-font-weight: bold; -fx-background-radius: 12; -fx-border-radius: 12; -fx-border-width: 0 0 4 0; -fx-border-color: derive($offBg, -25%); -fx-background-color: $offBg; -fx-text-fill: $offText; -fx-padding: 8 14; -fx-cursor: hand;"
                if (showTooltip) {
                    tooltip = Tooltip("ON: $onCmd\nOFF: $offCmd")
                }
                selectedProperty().addListener { _, _, isOn ->
                    playRockerAnimation(this)
                    if (isOn) {
                        text = "$displayName On"
                        style = "-fx-font-size: ${fontSize}px; -fx-font-weight: bold; -fx-background-radius: 12; -fx-border-radius: 12; -fx-border-width: 0 0 4 0; -fx-border-color: derive($offText, -25%); -fx-background-color: $offText; -fx-text-fill: $offBg; -fx-padding: 8 14; -fx-cursor: hand;"
                        executeCommand(onCmd, pair.baseName)
                    } else {
                        text = "$displayName Off"
                        style = "-fx-font-size: ${fontSize}px; -fx-font-weight: bold; -fx-background-radius: 12; -fx-border-radius: 12; -fx-border-width: 0 0 4 0; -fx-border-color: derive($offBg, -25%); -fx-background-color: $offBg; -fx-text-fill: $offText; -fx-padding: 8 14; -fx-cursor: hand;"
                        executeCommand(offCmd, pair.baseName)
                    }
                }
            }
            if (hasPresetPanel) presetButtonsPanel.children.add(toggle)
        }

        for (cfg in standalone) {
            val cmd = (cfg.documentation.ifBlank { cfg.commandString }).trim()
            val displayLabel = cfg.label.replace("\n", " ").trim().ifBlank { "…" }
            val (fontSize, minW) = buttonFontSizeAndMinWidth(displayLabel)
            val bg = cfg.backgroundColor
            val fg = cfg.textColor
            val btn = Button(displayLabel).apply {
                styleClass.add("preset-standalone-btn")
                minWidth = minW
                style = "-fx-font-size: ${fontSize}px; -fx-font-weight: bold; -fx-background-radius: 12; -fx-border-radius: 12; -fx-border-width: 0 0 4 0; -fx-border-color: derive($bg, -25%); -fx-background-color: $bg; -fx-text-fill: $fg; -fx-padding: 8 14; -fx-cursor: hand;"
                if (showTooltip) {
                    tooltip = Tooltip(
                        (if (cfg.description.isNotBlank()) cfg.description + "\n" else "") +
                            "Command: ${if (cmd.isNotBlank()) cmd else "(none)"}"
                    )
                }
                setOnAction {
                    playRockerAnimation(this)
                    executeCommand(cmd, cfg.label)
                }
            }
            if (hasPresetPanel) presetButtonsPanel.children.add(btn)
        }

        if (hasPresetPanel && presetButtonsPanel.children.isNotEmpty()) {
            presetButtonsPanel.isVisible = true
            presetButtonsPanel.isManaged = true
        }
    }

    private fun formatControlValue(value: Double): String {
        val rounded = value.toInt()
        return if (kotlin.math.abs(value - rounded.toDouble()) < 0.001) {
            rounded.toString()
        } else {
            "%.1f".format(value)
        }
    }

    private fun formatControlValueInt(value: Double): String = value.toInt().toString()

    private fun updateButtonStates(state: RecorderState) {
        when (state) {
            RecorderState.DISCONNECTED -> {
                btnStart.tooltip = Tooltip("Connect")
                btnStart.isDisable = false
                btnStop.isDisable = true
                btnAbort.isDisable = true
                setBbpButtonsVisible(false)
            }
            RecorderState.MONITORING -> {
                if (shouldAutoStartAfterConnect) {
                    shouldAutoStartAfterConnect = false
                    recorder.startRecording()
                }
                btnStart.tooltip = Tooltip("Start roast")
                btnStart.isDisable = false
                btnStop.isDisable = true
                btnAbort.isDisable = true
                setBbpButtonsVisible(false)
            }
            RecorderState.RECORDING -> {
                btnStart.isDisable = true
                btnStop.isDisable = false
                btnAbort.isDisable = false
                setBbpButtonsVisible(false)
            }
            RecorderState.STOPPED -> {
                btnStart.tooltip = Tooltip("Start new roast")
                btnStart.isDisable = false
                btnStop.isDisable = true
                btnAbort.isDisable = true
                setBbpButtonsVisible(false)
            }
            RecorderState.BBP -> {
                curveChart.showBbpMode()
                btnStart.tooltip = Tooltip("Start roast")
                btnStart.isDisable = false
                btnStop.isDisable = true
                btnAbort.isDisable = true
                setBbpButtonsVisible(true)
            }
        }
        updateBbpPanel()
        updateTimerHeroState()
        refreshCommentsList()
    }

    private fun setBbpButtonsVisible(visible: Boolean) {
        if (::btnRestartBbp.isInitialized) {
            btnRestartBbp.isVisible = visible
            btnRestartBbp.isManaged = visible
        }
        if (::btnStopBbp.isInitialized) {
            btnStopBbp.isVisible = visible
            btnStopBbp.isManaged = visible
        }
    }

    // ── Button handlers ───────────────────────────────────────────────────────

    private fun setupButtonHandlers() {
        btnStart.setOnAction {
            when (recorder.stateFlow.value) {
                RecorderState.DISCONNECTED -> {
                    shouldAutoStartAfterConnect = true
                    connectOrDiscover()
                }
                RecorderState.MONITORING -> recorder.startRecording()
                RecorderState.STOPPED -> {
                    recorder.reset()
                    resetCurvePipeline()
                    clearChartAndBbpState()
                }
                RecorderState.BBP -> {
                    hideFinishPanel()
                    recorder.startRecording()
                    resetCurvePipeline()
                    clearChartAndBbpState()
                }
                RecorderState.RECORDING -> { /* disabled */ }
            }
        }

        if (::btnRestartBbp.isInitialized) {
            btnRestartBbp.setOnAction { recorder.restartBbp() }
            btnRestartBbp.tooltip = Tooltip("Restart recording. Only the last 15 minutes are kept.")
        }
        if (::btnStopBbp.isInitialized) {
            btnStopBbp.setOnAction { recorder.stopBbpRecording() }
            btnStopBbp.tooltip = Tooltip("Stop recording. Data is kept until the next roast starts.")
        }

        btnStop.setOnAction {
            val profile = recorder.stop()
            showFinishRoastDialog(profile)
        }

        btnAbort.setOnAction {
            recorder.abort()
            resetCurvePipeline()
            clearChartAndBbpState()
        }
    }

    /** Hotkeys: Ctrl+Enter / Space = Start; C = Charge (when recording); D = Drop (when recording); Alt+Escape = Abort; F1 = Help. */
    private fun setupSpaceHotkey() {
        chartContainer.sceneProperty().addListener { _, _, scene ->
            scene?.addEventHandler(KeyEvent.KEY_PRESSED) { e ->
                when {
                    e.code == KeyCode.ENTER && e.isShortcutDown -> {
                        e.consume()
                        triggerStart()
                    }
                    e.code == KeyCode.SPACE && !e.isShortcutDown && !e.isAltDown -> {
                        e.consume()
                        if (recorder.stateFlow.value == RecorderState.RECORDING) { /* Space does nothing while recording */ }
                        else triggerStart()
                    }
                    e.code == KeyCode.C && !e.isShortcutDown && !e.isAltDown -> {
                        if (recorder.stateFlow.value == RecorderState.RECORDING) {
                            e.consume()
                            triggerCharge()
                        }
                    }
                    e.code == KeyCode.D && !e.isShortcutDown && !e.isAltDown -> {
                        if (recorder.stateFlow.value == RecorderState.RECORDING) {
                            e.consume()
                            triggerDrop()
                        }
                    }
                    e.code == KeyCode.ESCAPE && e.isAltDown -> {
                        e.consume()
                        if (recorder.stateFlow.value == RecorderState.RECORDING) {
                            recorder.abort()
                            resetCurvePipeline()
                            clearChartAndBbpState()
                        }
                    }
                    e.code == KeyCode.DIGIT1 && e.isShortcutDown -> {
                        if (recorder.stateFlow.value == RecorderState.RECORDING) {
                            e.consume()
                            triggerFirstCrack()
                        }
                    }
                    e.code == KeyCode.DIGIT2 && e.isShortcutDown -> {
                        if (recorder.stateFlow.value == RecorderState.RECORDING) {
                            e.consume()
                            triggerSecondCrack()
                        }
                    }
                    e.code == KeyCode.DIGIT3 && e.isShortcutDown -> {
                        if (recorder.stateFlow.value == RecorderState.RECORDING) {
                            e.consume()
                            triggerDryEnd()
                        }
                    }
                    e.code == KeyCode.R && e.isShortcutDown -> {
                        e.consume()
                        curveChart.resetAxes()
                    }
                    e.code == KeyCode.F1 -> {
                        e.consume()
                        showShortcutsHelp()
                    }
                }
            }
        }
    }

    private fun triggerStart() {
        when (recorder.stateFlow.value) {
            RecorderState.DISCONNECTED -> {
                shouldAutoStartAfterConnect = true
                connectOrDiscover()
            }
            RecorderState.MONITORING -> recorder.startRecording()
            RecorderState.STOPPED -> {
                recorder.reset()
                resetCurvePipeline()
                clearChartAndBbpState()
            }
            RecorderState.BBP -> {
                hideFinishPanel()
                recorder.startRecording()
                resetCurvePipeline()
                clearChartAndBbpState()
            }
            RecorderState.RECORDING -> { }
        }
    }

    /** Run configured Modbus event command for the given key (CHARGE, DROP, DRY_END, FC_START, COOL_END) on background thread. */
    private fun runEventCommand(key: String) {
        val runner = recorder.dataSource as? ModbusCommandRunner ?: return
        val cmd = SettingsManager.load().machineConfig.eventCommands[key]?.trim() ?: return
        if (cmd.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            try {
                ModbusCommandExecutor.execute(runner, cmd)
            } catch (e: Exception) {
                Platform.runLater { ErrorLogBuffer.append(e, "Event command $key") }
            }
        }
    }

    private fun triggerCharge() {
        val profile = recorder.currentProfile.value
        if (profile.eventByType(EventType.CHARGE) != null) return
        val sample = recorder.currentSample.value ?: return
        runEventCommand("CHARGE")
        recorder.markEvent(EventType.CHARGE)
        val chargeTimeMs = (sample.timeSec * 1000).toLong()
        // Always rebase live at Charge so live Charge aligns to reference Charge (00:00).
        // Reference phase strip stays frozen because it is locked after reference selection.
        curveChart.rebaseAllSeries(chargeTimeMs)
        curveChart.addEventMarker(chargeTimeMs, "Charge @ %.1f °C".format(sample.bt),
            com.rdr.roast.ui.chart.CurveChartFx.COLOR_MARKER)
        referenceProfile?.let { curveChart.setReferenceProfile(it, 0L) }
        updateTimerHeroState()
        pulseTimerHero()
        refreshCommentsList()
    }

    private fun triggerDrop() {
        val sample = recorder.currentSample.value ?: return
        runEventCommand("DROP")
        recorder.markEvent(EventType.DROP)
        curveChart.addEventMarker((sample.timeSec * 1000).toLong(), "Drop @ %.1f °C".format(sample.bt),
            com.rdr.roast.ui.chart.CurveChartFx.COLOR_MARKER)
        val profile = recorder.stop()
        showFinishRoastDialog(profile)
    }

    private fun triggerDryEnd() {
        val profile = recorder.currentProfile.value
        if (profile.eventByType(EventType.CC) != null) return
        val sample = recorder.currentSample.value ?: return
        runEventCommand("DRY_END")
        recorder.markEvent(EventType.CC)
        curveChart.addEventMarker(
            (sample.timeSec * 1000).toLong(),
            "DE @ ${formatSec(sample.timeSec)} · %.1f °C".format(sample.bt)
        )
        updatePhaseStrip()
        pulseTimerHero()
        refreshCommentsList()
    }

    private fun triggerFirstCrack() {
        val profile = recorder.currentProfile.value
        if (profile.eventByType(EventType.FC) != null) return
        val sample = recorder.currentSample.value ?: return
        runEventCommand("FC_START")
        recorder.markEvent(EventType.FC)
        curveChart.addEventMarker(
            (sample.timeSec * 1000).toLong(),
            "FC @ ${formatSec(sample.timeSec)} · %.1f °C".format(sample.bt)
        )
        updatePhaseStrip()
        pulseTimerHero()
        refreshCommentsList()
    }

    private fun triggerSecondCrack() {
        val profile = recorder.currentProfile.value
        if (profile.eventByType(EventType.SC) != null) return
        val sample = recorder.currentSample.value ?: return
        runEventCommand("SC_START")
        recorder.markEvent(EventType.SC)
        curveChart.addEventMarker(
            (sample.timeSec * 1000).toLong(),
            "SC @ ${formatSec(sample.timeSec)} · %.1f °C".format(sample.bt)
        )
        pulseTimerHero()
        refreshCommentsList()
    }

    /** Cropster-style: after Stop/Drop show finish panel on the left of the chart (no modal). */
    private fun showFinishRoastDialog(profile: RoastProfile) {
        val loader = FXMLLoader(javaClass.getResource("/com/rdr/roast/ui/FinishRoastView.fxml"))
        val root = loader.load<Parent>()
        val controller = loader.getController<FinishRoastController>()
        controller.setProfile(profile)
        val settings = SettingsManager.load()
        controller.setWeightsFromRoastProperties(settings.roastPropertiesWeightInKg, settings.roastPropertiesWeightOutKg)
        controller.onSave = {
            val savePath = SettingsManager.load().savePath
            val path = Paths.get(savePath).resolve(ProfileStorage.generateFileName())
            try {
                java.nio.file.Files.createDirectories(path.parent)
                ProfileStorage.saveProfile(profile, path)
            } catch (e: Exception) {
                ErrorLogBuffer.append(e, "Save profile")
            }
            hideFinishPanel()
            if (recorder.stateFlow.value == RecorderState.BBP) {
                updateBbpPanel()
                refreshCommentsList()
            } else {
                recorder.reset()
                resetCurvePipeline()
                clearChartAndBbpState()
            }
        }
        controller.onDontSave = { hideFinishPanel() }
        controller.setStage(null) // inline panel, no dialog
        centerPane.left = StackPane(root).apply {
            padding = Insets(14.0, 0.0, 0.0, 14.0)
            styleClass.add("dialog-shell")
        }
        uploadRoastIfConnected(profile)
    }

    private fun hideFinishPanel() {
        centerPane.left = null
    }

    private fun uploadRoastIfConnected(profile: RoastProfile) {
        val settings = SettingsManager.load()
        val baseUrl = ServerConfig.API_BASE_URL.trim().removeSuffix("/")
        val token = settings.serverToken.takeIf { it.isNotBlank() }
        if (baseUrl.isEmpty() || token.isNullOrBlank()) return
        val roastId = UUID.randomUUID()
        val scheduleIdToComplete = selectedScheduleId
        val dateIso8601 = Instant.now().toString()
        val payload = buildAroastPayload(
            profile = profile,
            title = settings.roastPropertiesTitle,
            weightInKg = settings.roastPropertiesWeightInKg,
            weightOutKg = settings.roastPropertiesWeightOutKg,
            notes = settings.roastPropertiesBeansNotes,
            roastId = roastId.toString(),
            dateIso8601 = dateIso8601,
            coffeeId = settings.roastPropertiesStockId.takeIf { it.isNotBlank() },
            blendId = settings.roastPropertiesBlendId.takeIf { it.isNotBlank() }
        )
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ServerApi.uploadRoast(baseUrl, token, payload)
                    if (scheduleIdToComplete != null) {
                        ServerApi.completeSchedule(
                            baseUrl, token, scheduleIdToComplete, roastId,
                            settings.roastPropertiesWeightOutKg.takeIf { it > 0 }, null
                        )
                    }
                }
                Platform.runLater {
                    if (scheduleIdToComplete != null) {
                        selectedScheduleId = null
                        refreshScheduleList?.invoke()
                    }
                    Alert(Alert.AlertType.INFORMATION).apply {
                        title = "Сервер"
                        headerText = "Обжарка загружена"
                        contentText = "Профиль успешно отправлен на сервер."
                    }.show()
                }
            } catch (e: ServerApiException) {
                ErrorLogBuffer.append(e, "Upload roast")
                Platform.runLater {
                    val msg = when (e.statusCode) {
                        409 -> "На сервере уже есть более новая версия этой обжарки."
                        else -> (e.message ?: "Ошибка ${e.statusCode}")
                    }
                    Alert(Alert.AlertType.WARNING).apply {
                        title = "Загрузка на сервер"
                        headerText = "Не удалось загрузить"
                        contentText = msg
                    }.show()
                }
            } catch (e: Exception) {
                ErrorLogBuffer.append(e, "Upload roast")
                Platform.runLater {
                    Alert(Alert.AlertType.WARNING).apply {
                        title = "Загрузка на сервер"
                        headerText = "Нет связи с сервером"
                        contentText = "Проверьте подключение и настройки."
                    }.show()
                }
            }
        }
    }

    private fun showShortcutsHelp() {
        val stage = Stage().apply {
            title = "Keyboard shortcuts"
            scene = Scene(HotkeysHelpView.createContent(), 420.0, 320.0)
            applySceneStyle(scene)
            initModality(Modality.NONE)
            initOwner(chartContainer.scene?.window)
        }
        stage.show()
    }

    private fun formatSec(sec: Double): String {
        val m = (sec / 60).toInt()
        val s = (sec % 60).toInt()
        return "%02d:%02d".format(m, s)
    }

    private fun resetCurvePipeline() {
        btRaw.clear()
        etRaw.clear()
    }

    private fun loadReferenceFromFile() {
        val chooser = FileChooser().apply {
            title = "Load reference roast"
            extensionFilters.add(FileChooser.ExtensionFilter("Artisan profile", "*.alog"))
        }
        val file = chooser.showOpenDialog(titleBar.scene?.window)
        if (file == null) return
        try {
            val profile = ProfileStorage.loadProfile(file.toPath())
            setReference(profile, file.name)
        } catch (e: Exception) {
            Alert(Alert.AlertType.ERROR).apply {
                title = "Load failed"
                headerText = "Could not load profile"
                contentText = e.message ?: e.toString()
            }.showAndWait()
        }
    }

    private fun loadReferenceFromServer() {
        val settings = SettingsManager.load()
        val token = settings.serverToken.takeIf { it.isNotBlank() }
        if (token.isNullOrBlank()) {
            Alert(Alert.AlertType.WARNING).apply {
                title = "Сервер"
                headerText = "Требуется вход"
                contentText = "Выполните вход (кнопка «Войти» в панели System) для доступа к эталонам с сервера."
            }.showAndWait()
            return
        }
        val baseUrl = ServerConfig.API_BASE_URL.trim().removeSuffix("/")
        scope.launch {
            val items = withContext(Dispatchers.IO) {
                try {
                    ReferenceApi.listReferences(
                        baseUrl = baseUrl,
                        token = token
                    )
                } catch (e: Exception) {
                    emptyList()
                }
            }
            Platform.runLater {
                if (items.isEmpty()) {
                    Alert(Alert.AlertType.INFORMATION).apply {
                        title = "No references"
                        headerText = "No reference roasts found"
                        contentText = "Upload reference roasts on the server or check the URL and token."
                    }.showAndWait()
                    return@runLater
                }
                val labels = items.map { "${it.label} (${it.id.take(8)}…)" }
                val listView = ListView<String>().apply {
                    styleClass.add("secondary-list")
                    this.items.clear()
                    this.items.addAll(labels)
                    prefHeight = 280.0
                    prefWidth = 360.0
                }
                val stage = Stage().apply {
                    title = "Select reference roast"
                    scene = Scene(listView, 360.0, 280.0)
                    applySceneStyle(scene)
                    initModality(Modality.APPLICATION_MODAL)
                    initOwner(titleBar.scene?.window)
                }
                listView.setOnMouseClicked { ev ->
                    if (ev.clickCount == 2) {
                        val idx = listView.selectionModel.selectedIndex
                        if (idx in items.indices) {
                            stage.close()
                            fetchAndSetReference(baseUrl, token, items[idx].id)
                        }
                    }
                }
                stage.showAndWait()
            }
        }
    }

    private fun fetchAndSetReference(baseUrl: String, token: String?, roastId: String) {
        scope.launch {
            val profile = withContext(Dispatchers.IO) {
                try {
                    ReferenceApi.getProfileData(baseUrl, roastId, token)
                } catch (e: Exception) {
                    null
                }
            }
            Platform.runLater {
                if (profile != null) {
                    setReference(profile, "Server reference: $roastId")
                } else {
                    Alert(Alert.AlertType.ERROR).apply {
                        title = "Load failed"
                        headerText = "Could not load profile from server"
                        contentText = "Check connection and try again."
                    }.showAndWait()
                }
            }
        }
    }

    private fun clearReference() {
        referenceProfile = null
        if (::lblReferenceLabel.isInitialized) lblReferenceLabel.text = "No reference roast selected."
        if (::referencePanel.isInitialized) {
            referencePanel.isVisible = false
            referencePanel.isManaged = false
        }
        if (::referenceSummaryBox.isInitialized) {
            referenceSummaryBox.isVisible = false
            referenceSummaryBox.isManaged = false
        }
        curveChart.setReferenceProfile(null)
        curveChart.setReferenceBbp(null)
        updateReferenceComments(null)
        updateBbpPanel()
    }

    private fun setReference(profile: RoastProfile, label: String) {
        referenceProfile = profile
        if (::lblReferenceLabel.isInitialized) lblReferenceLabel.text = label
        if (::referencePanel.isInitialized) {
            referencePanel.isVisible = true
            referencePanel.isManaged = true
            referencePanel.opacity = 0.0
            referencePanel.translateY = 12.0
            val fade = FadeTransition(Duration.millis(280.0), referencePanel).apply {
                fromValue = 0.0
                toValue = 1.0
            }
            val slide = TranslateTransition(Duration.millis(280.0), referencePanel).apply {
                fromY = 12.0
                toY = 0.0
            }
            ParallelTransition(fade, slide).play()
        }
        updateReferenceSummary(profile)
        updateReferenceComments(profile)
        // Ref Charge always at X = 0 (Charge-to-Charge alignment); after C live is also at 0.
        curveChart.setReferenceProfile(profile, 0L)
        curveChart.setReferenceBbp(profile.betweenBatchLog)
        updateBbpPanel()
    }

    private fun formatRefMmSs(sec: Double): String {
        val s = sec.coerceAtLeast(0.0).toInt()
        val m = s / 60
        val secPart = s % 60
        return "%d:%02d".format(m, secPart)
    }

    private fun updateReferenceSummary(profile: RoastProfile) {
        if (!::referenceSummaryBox.isInitialized) return
        val charge = profile.eventByType(EventType.CHARGE)?.timeSec ?: 0.0
        val endSec = profile.timex.maxOrNull() ?: charge
        val dropSec = profile.eventByType(EventType.DROP)?.timeSec
        val endSecForDuration = dropSec ?: endSec
        val durationSec = (endSecForDuration - charge).coerceAtLeast(0.0)
        val fc = profile.eventByType(EventType.FC)?.timeSec
        val devTimeSec = if (fc != null && fc >= charge) ((dropSec ?: endSec) - fc).coerceAtLeast(0.0) else 0.0
        val devTimeRatio = if (durationSec > 0) (devTimeSec / durationSec * 100.0) else 0.0
        val chargeIdx = profile.timex.indexOfLast { it <= charge }.coerceAtLeast(0)
        val dropIdx = profile.timex.indexOfLast { it <= (dropSec ?: endSec) }.coerceAtLeast(0)
        val chargeTemp = profile.eventByType(EventType.CHARGE)?.tempBT
            ?: profile.temp1.getOrNull(chargeIdx)
            ?: 0.0
        val dropTemp = profile.eventByType(EventType.DROP)?.tempBT
            ?: profile.temp1.getOrNull(dropIdx)
            ?: 0.0
        lblRefDuration.text = formatRefMmSs(durationSec)
        lblRefChargeTemp.text = "%.1f °C".format(chargeTemp)
        lblRefDropTemp.text = "%.1f °C".format(dropTemp)
        lblRefDevTime.text = formatRefMmSs(devTimeSec)
        lblRefDevTimeRatio.text = "%.1f%%".format(devTimeRatio)
        referenceSummaryBox.isVisible = true
        referenceSummaryBox.isManaged = true
        referenceSummaryBox.opacity = 0.0
        referenceSummaryBox.translateY = 8.0
        val fade = FadeTransition(Duration.millis(220.0), referenceSummaryBox).apply {
            fromValue = 0.0
            toValue = 1.0
        }
        val slide = TranslateTransition(Duration.millis(220.0), referenceSummaryBox).apply {
            fromY = 8.0
            toY = 0.0
        }
        ParallelTransition(fade, slide).play()
    }

    private fun openReferenceCommentsMenu() {
        val menu = ContextMenu()
        val modeGroup = ToggleGroup()
        val byTime = RadioMenuItem("Order by time").apply {
            toggleGroup = modeGroup
            isSelected = refCommentsSortMode == RefCommentsSortMode.TIME
        }
        val byBt = RadioMenuItem("Order by BT").apply {
            toggleGroup = modeGroup
            isSelected = refCommentsSortMode == RefCommentsSortMode.BT
        }
        val showTime = CheckMenuItem("Show time").apply { isSelected = showRefCommentTime }
        val showValue = CheckMenuItem("Show value").apply { isSelected = showRefCommentValue }
        byTime.setOnAction {
            refCommentsSortMode = RefCommentsSortMode.TIME
            updateReferenceComments(referenceProfile)
        }
        byBt.setOnAction {
            refCommentsSortMode = RefCommentsSortMode.BT
            updateReferenceComments(referenceProfile)
        }
        showTime.setOnAction {
            showRefCommentTime = showTime.isSelected
            updateReferenceComments(referenceProfile)
        }
        showValue.setOnAction {
            showRefCommentValue = showValue.isSelected
            updateReferenceComments(referenceProfile)
        }
        menu.items.addAll(byTime, byBt, SeparatorMenuItem(), showTime, showValue)
        menu.show(btnCommentsSettings, javafx.geometry.Side.BOTTOM, 0.0, 0.0)
    }

    private fun updateReferenceComments(profile: RoastProfile?) {
        if (profile == null && recorder.stateFlow.value !in listOf(RecorderState.RECORDING, RecorderState.BBP, RecorderState.STOPPED)) {
            commentsList().items = FXCollections.observableArrayList()
            return
        }
        refreshCommentsList()
    }

    private fun updateAuthUi() {
        val settings = SettingsManager.load()
        val loggedIn = settings.serverToken.isNotBlank()
        authStatusLabel?.text = if (loggedIn) {
            val email = settings.serverRememberEmail.takeIf { it.isNotBlank() } ?: "Вход выполнен"
            "Вход: ${email.take(18)}${if (email.length > 18) "…" else ""}"
        } else {
            "Не выполнен вход"
        }
        authButton?.tooltip = Tooltip(if (loggedIn) "Выйти" else "Войти")
        drawerLogoutButton?.isVisible = loggedIn
        drawerLogoutButton?.isManaged = loggedIn
        if (::btnSidebarAccount.isInitialized) {
            btnSidebarAccount.styleClass.removeAll(listOf("server-connected", "server-disconnected"))
            btnSidebarAccount.styleClass.add(if (loggedIn) "server-connected" else "server-disconnected")
            btnSidebarAccount.tooltip = Tooltip(if (loggedIn) "Аккаунт (подключено к сайту)" else "Аккаунт (не подключено к сайту)")
        }
    }

    private fun onAuthButtonClick() {
        val settings = SettingsManager.load()
        if (settings.serverToken.isNotBlank()) {
            val s = settings.copy(serverToken = "", serverRefreshToken = "")
            SettingsManager.save(s)
            updateAuthUi()
            return
        }
        val loader = FXMLLoader(javaClass.getResource("/com/rdr/roast/ui/LoginView.fxml"))
        val root = loader.load<Parent>()
        val loginController = loader.getController<com.rdr.roast.ui.LoginController>()
        loginController.setInitialEmail(settings.serverRememberEmail.takeIf { it.isNotBlank() })
        loginController.onSuccess = { updateAuthUi() }
        val dialogRoot = StackPane(root).apply {
            styleClass.add("dialog-shell")
        }
        val stage = Stage().apply {
            title = "Вход на сервер"
            scene = Scene(dialogRoot, 392.0, 372.0)
            applySceneStyle(scene)
            initModality(Modality.APPLICATION_MODAL)
            initOwner(authButton?.scene?.window ?: btnSidebarAccount.scene?.window)
        }
        loginController.setStage(stage)
        stage.showAndWait()
    }

    private fun setupSettingsButton() {
        btnSettings.setOnAction { openSettingsDialog() }
    }

    private fun openSettingsDialog() {
        val loader = FXMLLoader(javaClass.getResource("/com/rdr/roast/ui/SettingsView.fxml"))
        val root = loader.load<Parent>()
        val settingsController = loader.getController<SettingsController>()
        val stage = Stage().apply {
            title = "Настройки"
            scene = Scene(root, 1080.0, 780.0)
            applySceneStyle(scene)
            initModality(Modality.APPLICATION_MODAL)
            initOwner(btnSettings.scene?.window)
        }
        settingsController.onThemePreviewChanged = { theme ->
            ChartThemeAdapter.apply(curveChart, controlChart, SettingsManager.load().copy(themeSettings = theme))
            refreshCustomButtonsPanel()
        }
        settingsController.onCloseDrawer = { sc ->
            (sc.btnSave.scene?.window as? Stage)?.close()
            if (sc.savedSettings != null) {
                recorder.disconnect()
                val applied = SettingsManager.load()
                recorder.dataSource = DataSourceFactory.create(applied.machineConfig)
                recorder.betweenBatchProtocolEnabled = applied.betweenBatchProtocolEnabled
                recorder.connect()
                startConnectionStateCollector()
                chartContainer.scene?.let { com.rdr.roast.app.AppearanceSupport.applyToScene(it) }
            }
            val updated = SettingsManager.load()
            ChartThemeAdapter.apply(curveChart, controlChart, updated)
            curveChart.configureExtraSensors(updated.extraSensors)
            configureExtraSensorReadouts(updated.extraSensors)
            updateReadoutUnits()
            refreshCustomButtonsPanel()
            updateControlPanel(lastConnectionState ?: ConnectionState.Disconnected, recorder.dataSource)
            updateRoasterConnectionIndicator(lastConnectionState ?: ConnectionState.Disconnected)
        }
        stage.showAndWait()
    }

    private var dragStartX = 0.0
    private var dragStartY = 0.0
    private var dragStartStageX = 0.0
    private var dragStartStageY = 0.0
    private var titleBarConfigured = false

    private fun setupTitleBar(scene: Scene, stage: Stage) {
        ResizeHelper.enableResize(scene, stage)

        titleBar.addEventFilter(MouseEvent.MOUSE_PRESSED) { e ->
            if (isInteractiveTitleBarTarget(e.target)) return@addEventFilter
            if (e.button == MouseButton.PRIMARY && !stage.isMaximized) {
                dragStartX = e.screenX
                dragStartY = e.screenY
                dragStartStageX = stage.x
                dragStartStageY = stage.y
            }
        }
        titleBar.addEventFilter(MouseEvent.MOUSE_DRAGGED) { e ->
            if (isInteractiveTitleBarTarget(e.target)) return@addEventFilter
            if (e.button == MouseButton.PRIMARY && !stage.isMaximized) {
                stage.x = dragStartStageX + (e.screenX - dragStartX)
                stage.y = dragStartStageY + (e.screenY - dragStartY)
            }
        }
        titleBar.addEventFilter(MouseEvent.MOUSE_CLICKED) { e ->
            if (isInteractiveTitleBarTarget(e.target)) return@addEventFilter
            if (e.button == MouseButton.PRIMARY && e.clickCount == 2) {
                stage.isMaximized = !stage.isMaximized
                updateMaximizeButtonIcon()
            }
        }

        btnMinimize.setOnAction { stage.isIconified = true }
        btnMaximize.setOnAction {
            stage.isMaximized = !stage.isMaximized
            updateMaximizeButtonIcon()
        }
        btnClose.setOnAction { stage.close() }

        updateMaximizeButtonIcon()
        setupTitleBarIcons()
        setupPlayerBarIcons()
        setupSidebarIcons()
    }

    private fun ensureTitleBarReady(scene: Scene) {
        val currentWindow = scene.window
        if (currentWindow is Stage && !titleBarConfigured) {
            titleBarConfigured = true
            currentWindow.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST) { saveDividerPositions() }
            setupTitleBar(scene, currentWindow)
            return
        }
        scene.windowProperty().addListener { _, _, window ->
            if (window is Stage && !titleBarConfigured) {
                titleBarConfigured = true
                window.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST) { saveDividerPositions() }
                setupTitleBar(scene, window)
            }
        }
    }

    private fun isInteractiveTitleBarTarget(target: Any?): Boolean {
        var node = target as? Node ?: return false
        while (node != null) {
            if (node is javafx.scene.control.Control) return true
            val sc = node.styleClass
            if (sc.contains("title-bar-actions") || sc.contains("title-bar-window-controls")) return true
            node = node.parent
        }
        return false
    }

    private fun setupSidebarIcons() {
        val size = 18
        btnSidebarAccount.graphic = FontIcon(FontAwesomeSolid.USER).apply { iconSize = size }
        btnSidebarPlan.graphic = FontIcon(FontAwesomeSolid.TASKS).apply { iconSize = size }
        btnSidebarProduction.graphic = FontIcon(FontAwesomeSolid.CHART_LINE).apply { iconSize = size }
        btnSidebarSupport.graphic = FontIcon(FontAwesomeSolid.QUESTION_CIRCLE).apply { iconSize = size }
        btnSidebarAccount.tooltip = Tooltip("Account")
        btnSidebarPlan.tooltip = Tooltip("Plan")
        btnSidebarProduction.tooltip = Tooltip("Production")
        btnSidebarSupport.tooltip = Tooltip("Support")
    }

    private fun setupPlayerBarIcons() {
        val size = 20
        btnStart.text = ""
        btnStart.graphic = FontIcon(FontAwesomeSolid.PLAY).apply { iconSize = size }
        btnStart.tooltip = Tooltip("Start")
        btnStop.text = ""
        btnStop.graphic = FontIcon(FontAwesomeSolid.STOP).apply { iconSize = size }
        btnStop.tooltip = Tooltip("Stop")
        btnAbort.text = ""
        btnAbort.graphic = FontIcon(FontAwesomeSolid.TIMES).apply { iconSize = size }
        btnAbort.tooltip = Tooltip("Abort")
    }

    private fun setupTitleBarIcons() {
        val size = 12
        btnShowSliders.text = ""
        btnShowSliders.graphic = FontIcon(FontAwesomeSolid.SLIDERS_H).apply { iconSize = 13 }
        btnClose.graphic = FontIcon(FontAwesomeSolid.TIMES).apply { iconSize = size }
        btnMinimize.graphic = FontIcon(FontAwesomeSolid.MINUS).apply { iconSize = size }
        btnSettings.graphic = FontIcon(FontAwesomeSolid.COG).apply { iconSize = size }
        updateMaximizeButtonIcon()
    }

    private fun updateMaximizeButtonIcon() {
        val stage = btnMaximize.scene?.window as? Stage ?: return
        btnMaximize.graphic = FontIcon(
            if (stage.isMaximized) FontAwesomeSolid.COMPRESS else FontAwesomeSolid.EXPAND
        ).apply { iconSize = 12 }
    }

    private fun restoreDividerPositions() {
        val s = SettingsManager.load()
        s.layoutDividerCenterRight?.let { p ->
            if (p in 0.01..0.99) centerSplit.setDividerPosition(0, p)
        }
    }

    private fun saveDividerPositions() {
        val positions = centerSplit.dividerPositions
        val centerRight = if (positions.isNotEmpty()) positions[0] else null
        val current = SettingsManager.load()
        SettingsManager.save(current.copy(
            layoutDividerCenterRight = centerRight
        ))
    }

    private fun updateReadoutUnits() {
        val (_, unitStr) = tempForDisplay(0.0)
        val rorUnitStr = if (unitStr == "°F") " °F/min" else " °C/min"
        lblBTUnit.text = " $unitStr"
        lblETUnit.text = " $unitStr"
        lblRoRBTUnit.text = rorUnitStr
        lblRoRETUnit.text = rorUnitStr
    }

    private fun setupSidebarPanels() {
        val authDrawerWidth = 432.0
        val planDrawerWidth = 372.0
        val productionDrawerWidth = 436.0
        val supportDrawerWidth = 388.0
        val drawerWidths = listOf(authDrawerWidth, planDrawerWidth, productionDrawerWidth, supportDrawerWidth)
        val accountAuthLabel = Label("").apply {
            styleClass.addAll("secondary-hint-label")
            maxWidth = authDrawerWidth - 56
        }
        val loginLoader = FXMLLoader(javaClass.getResource("/com/rdr/roast/ui/LoginView.fxml"))
        val loginRoot = loginLoader.load<Parent>()
        val loginController = loginLoader.getController<LoginController>()
        loginController.setStage(null)
        loginController.setInitialEmail(SettingsManager.load().serverRememberEmail.takeIf { it.isNotBlank() })
        loginController.onSuccess = {
            updateAuthUi()
            closeDrawer()
            btnSidebarAccount.isSelected = false
        }
        loginController.onCancel = {
            closeDrawer()
            btnSidebarAccount.isSelected = false
        }
        val logoutButton = Button("Выйти").apply {
            styleClass.add("btn-secondary")
            graphic = FontIcon(FontAwesomeSolid.USER_MINUS).apply { iconSize = 16 }
            setOnAction {
                val s = SettingsManager.load().copy(serverToken = "", serverRefreshToken = "")
                SettingsManager.save(s)
                updateAuthUi()
            }
            maxWidth = Double.MAX_VALUE
            isVisible = SettingsManager.load().serverToken.isNotBlank()
            isManaged = isVisible
        }
        drawerLogoutButton = logoutButton
        authStatusLabel = accountAuthLabel
        authButton = logoutButton
        val authPanel = VBox(12.0).apply {
            styleClass.addAll("drawer-sheet")
            minWidth = authDrawerWidth
            prefWidth = authDrawerWidth
            maxWidth = authDrawerWidth
            padding = Insets(16.0, 18.0, 18.0, 18.0)
            children.add(Label("Вход").apply { styleClass.add("drawer-sheet-title") })
            children.add(accountAuthLabel)
            val loginScroll = ScrollPane(loginRoot).apply {
                styleClass.add("secondary-scroll")
                isFitToWidth = true
                isPannable = false
                hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
                vbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
                minHeight = 0.0
            }
            children.add(loginScroll)
            VBox.setVgrow(loginScroll, Priority.ALWAYS)
            children.add(logoutButton)
        }
        // Production drawer: Roast Properties form (no separate Reference button)
        val roastPropsLoader = FXMLLoader(javaClass.getResource("/com/rdr/roast/ui/RoastPropertiesView.fxml"))
        val roastPropsRoot = roastPropsLoader.load<Parent>()
        roastPropertiesController = roastPropsLoader.getController()
        roastPropertiesController?.setStage(null)
        roastPropertiesController?.onApply = { _, profile ->
            if (profile != null) setReference(profile, "Server (Roast Properties)")
            else clearReference()
        }
        roastPropertiesController?.onCloseDrawer = {
            closeDrawer()
            btnSidebarProduction.isSelected = false
        }
        val scheduleItems = FXCollections.observableArrayList<ServerApi.ScheduleItem>()
        val scheduleListView = ListView<ServerApi.ScheduleItem>().apply {
            styleClass.addAll("secondary-list", "drawer-log-list")
            items = scheduleItems
            placeholder = Label("На сегодня ничего не запланировано.").apply {
                styleClass.add("secondary-hint-label")
            }
            cellFactory = javafx.util.Callback {
                object : javafx.scene.control.ListCell<ServerApi.ScheduleItem>() {
                    override fun updateItem(item: ServerApi.ScheduleItem?, empty: Boolean) {
                        super.updateItem(item, empty)
                        text = when {
                            item == null || empty -> null
                            else -> "${item.scheduled_date}  ${item.title}  ${item.scheduled_weight_kg?.let { "%.1f kg".format(it) } ?: ""}  ${item.status}"
                        }
                    }
                }
            }
            selectionModel.selectedItemProperty().addListener { _, _, new ->
                if (new != null) {
                    selectedScheduleId = try { UUID.fromString(new.id) } catch (_: Exception) { null }
                    roastPropertiesController?.setFromSchedule(new.title, new.scheduled_weight_kg)
                }
            }
            minHeight = 0.0
            VBox.setVgrow(this, Priority.ALWAYS)
        }
        fun loadScheduleList() {
            val settings = SettingsManager.load()
            val baseUrl = ServerConfig.API_BASE_URL.trim().removeSuffix("/")
            val token = settings.serverToken.takeIf { it.isNotBlank() }
            if (baseUrl.isEmpty() || token.isNullOrBlank()) {
                Platform.runLater { scheduleItems.clear() }
                return
            }
            val today = LocalDate.now()
            scope.launch {
                try {
                    val (items, _) = withContext(Dispatchers.IO) {
                        ServerApi.listSchedule(baseUrl, token, today, today, "pending")
                    }
                    Platform.runLater {
                        scheduleItems.setAll(items)
                    }
                } catch (_: Exception) {
                    Platform.runLater { scheduleItems.clear() }
                }
            }
        }
        refreshScheduleList = { loadScheduleList() }
        val planPanel = VBox(8.0).apply {
            styleClass.addAll("drawer-sheet")
            minWidth = planDrawerWidth
            prefWidth = planDrawerWidth
            maxWidth = planDrawerWidth
            minHeight = 0.0
            padding = Insets(20.0, 22.0, 22.0, 20.0)
            children.add(Label("План (сегодня)").apply { styleClass.add("drawer-sheet-title") })
            children.add(Label("Быстрый доступ к сегодняшним задачам и заготовкам партий.").apply {
                styleClass.add("drawer-sheet-subtitle")
            })
            children.add(VBox(12.0).apply {
                styleClass.addAll("drawer-section-card", "drawer-hero-card")
                children.add(javafx.scene.control.Button("Обновить").apply {
                    styleClass.add("btn-secondary")
                    setOnAction { loadScheduleList() }
                    maxWidth = Double.MAX_VALUE
                })
                children.add(scheduleListView)
                VBox.setVgrow(scheduleListView, Priority.ALWAYS)
            })
        }
        val productionPanel = VBox().apply {
            styleClass.addAll("drawer-sheet")
            minWidth = productionDrawerWidth
            prefWidth = productionDrawerWidth
            maxWidth = productionDrawerWidth
            minHeight = 0.0
            padding = Insets(20.0, 22.0, 22.0, 20.0)
            children.add(roastPropsRoot)
        }
        val logLines = ErrorLogBuffer.getObservableLines()
        val logListView = ListView<String>().apply {
            styleClass.addAll("secondary-list", "drawer-log-list")
            items = logLines
            selectionModel.selectionMode = SelectionMode.MULTIPLE
            prefHeight = 200.0
            minHeight = 80.0
            placeholder = Label("Пока логов нет.").apply {
                styleClass.add("secondary-hint-label")
            }
        }
        logLines.addListener(javafx.collections.ListChangeListener { _ ->
            if (logLines.isNotEmpty()) logListView.scrollTo(logLines.size - 1)
        })
        val supportPanel = VBox(10.0).apply {
            styleClass.addAll("drawer-sheet")
            padding = Insets(20.0, 22.0, 22.0, 20.0)
            minWidth = supportDrawerWidth
            prefWidth = supportDrawerWidth
            maxWidth = supportDrawerWidth
            children.add(Label("Support").apply { styleClass.add("drawer-sheet-title") })
            children.add(Label("Подсказки по управлению и быстрый доступ к внутренним логам приложения.").apply {
                styleClass.add("drawer-sheet-subtitle")
            })
            children.add(VBox(12.0).apply {
                styleClass.addAll("drawer-section-card", "drawer-hero-card")
                children.add(Button("Горячие клавиши (F1)").apply {
                    styleClass.add("btn-secondary")
                    graphic = FontIcon(FontAwesomeSolid.KEYBOARD).apply { iconSize = 18 }
                    setOnAction { btnShortcuts.fire() }
                    maxWidth = Double.MAX_VALUE
                })
            })
            children.add(Label("Логи").apply { styleClass.add("secondary-section-title") })
            children.add(logListView)
            children.add(HBox(8.0).apply {
                children.add(Button("Копировать").apply {
                    styleClass.add("btn-secondary")
                    setOnAction {
                        val sel = logListView.selectionModel.selectedItems
                        val text = if (sel.isEmpty()) logLines.joinToString("\n") else sel.joinToString("\n")
                        Clipboard.getSystemClipboard().setContent(ClipboardContent().apply { putString(text) })
                    }
                    maxWidth = Double.MAX_VALUE
                    HBox.setHgrow(this, Priority.ALWAYS)
                })
                children.add(Button("Очистить").apply {
                    styleClass.add("btn-secondary")
                    setOnAction { ErrorLogBuffer.clear() }
                    maxWidth = Double.MAX_VALUE
                    HBox.setHgrow(this, Priority.ALWAYS)
                })
                children.add(Button("Сохранить").apply {
                    styleClass.add("btn-primary")
                    setOnAction {
                        val chooser = FileChooser().apply {
                            title = "Сохранить логи"
                            extensionFilters.add(FileChooser.ExtensionFilter("Text files (*.txt)", "*.txt"))
                        }
                        val file = chooser.showSaveDialog(logListView.scene?.window)
                        if (file != null) {
                            try {
                                java.nio.file.Files.write(file.toPath(), ErrorLogBuffer.getLines(), Charsets.UTF_8)
                            } catch (_: Exception) { }
                        }
                    }
                    maxWidth = Double.MAX_VALUE
                    HBox.setHgrow(this, Priority.ALWAYS)
                })
            })
        }
        val panels = listOf(authPanel, planPanel, productionPanel, supportPanel)
        listOf(planPanel, supportPanel).forEach { vbox ->
            vbox.children.filterIsInstance<Button>().forEach { it.maxWidth = Double.MAX_VALUE }
        }
        val toggles = listOf(btnSidebarAccount, btnSidebarPlan, btnSidebarProduction, btnSidebarSupport)
        sidebarPanelContainer.clip = Rectangle().apply {
            widthProperty().bind(sidebarPanelContainer.widthProperty())
            heightProperty().bind(sidebarPanelContainer.heightProperty())
        }
        toggles.forEachIndexed { idx, btn ->
            btn.setOnAction {
                if (btn.isSelected) {
                    toggles.forEachIndexed { i, b -> if (i != idx) b.isSelected = false }
                    val w = drawerWidths[idx]
                    openDrawer(panels[idx], w)
                    when (idx) {
                        1 -> refreshScheduleList?.invoke()
                        2 -> {
                            roastPropertiesController?.loadFromSettings()
                            roastPropertiesController?.loadReferencesAndSelect()
                            roastPropertiesController?.loadStockAndBlends()
                        }
                    }
                } else {
                    closeDrawer()
                }
            }
        }
    }

    private fun clearSidebarToggleSelection() {
        listOf(btnSidebarAccount, btnSidebarPlan, btnSidebarProduction, btnSidebarSupport).forEach { it.isSelected = false }
    }

    private fun openDrawer(panelContent: VBox, drawerWidth: Double) {
        currentDrawerWidth = drawerWidth
        drawerAnimation?.stop()
        sidebarPanelContainer.isMouseTransparent = false
        val card = drawerCard ?: StackPane().apply {
            styleClass.add("drawer-card")
        }.also { createdCard ->
            val backdrop = Region().apply {
                styleClass.add("drawer-backdrop")
                opacity = 0.0
                setOnMouseClicked {
                    clearSidebarToggleSelection()
                    closeDrawer()
                }
            }
            val host = StackPane(backdrop, createdCard).apply {
                styleClass.add("drawer-host")
                prefWidthProperty().bind(sidebarPanelContainer.widthProperty())
                prefHeightProperty().bind(sidebarPanelContainer.heightProperty())
            }
            StackPane.setAlignment(createdCard, Pos.TOP_LEFT)
            sidebarPanelContainer.children.setAll(host)
            drawerHost = host
            drawerBackdrop = backdrop
            drawerCard = createdCard
        }
        card.minWidth = drawerWidth
        card.prefWidth = drawerWidth
        card.maxWidth = drawerWidth
        card.children.setAll(panelContent)
        panelContent.minWidth = drawerWidth - 40.0
        panelContent.prefWidth = drawerWidth - 40.0
        panelContent.maxWidth = drawerWidth - 40.0
        val backdrop = drawerBackdrop
        card.opacity = 0.0
        card.translateX = -34.0
        card.scaleX = 0.985
        card.scaleY = 0.985
        drawerAnimation = ParallelTransition().apply {
            backdrop?.let {
                children.add(FadeTransition(Duration.millis(220.0), it).apply {
                    fromValue = it.opacity
                    toValue = 1.0
                })
            }
            children.add(FadeTransition(Duration.millis(250.0), card).apply {
                fromValue = 0.0
                toValue = 1.0
            })
            children.add(TranslateTransition(Duration.millis(250.0), card).apply {
                fromX = -34.0
                toX = 0.0
            })
            children.add(ScaleTransition(Duration.millis(250.0), card).apply {
                fromX = 0.985
                fromY = 0.985
                toX = 1.0
                toY = 1.0
            })
            play()
        }
    }

    private fun closeDrawer() {
        val card = drawerCard ?: run {
            sidebarPanelContainer.children.clear()
            sidebarPanelContainer.isMouseTransparent = true
            return
        }
        drawerAnimation?.stop()
        val backdrop = drawerBackdrop
        drawerAnimation = ParallelTransition().apply {
            backdrop?.let {
                children.add(FadeTransition(Duration.millis(180.0), it).apply {
                    fromValue = it.opacity
                    toValue = 0.0
                })
            }
            children.add(FadeTransition(Duration.millis(180.0), card).apply {
                fromValue = card.opacity
                toValue = 0.0
            })
            children.add(TranslateTransition(Duration.millis(180.0), card).apply {
                fromX = card.translateX
                toX = -30.0
            })
            children.add(ScaleTransition(Duration.millis(180.0), card).apply {
                fromX = card.scaleX
                fromY = card.scaleY
                toX = 0.985
                toY = 0.985
            })
            setOnFinished {
                sidebarPanelContainer.children.clear()
                sidebarPanelContainer.isMouseTransparent = true
                drawerHost = null
                drawerCard = null
                drawerBackdrop = null
            }
            play()
        }
    }

    private fun makePlaceholderPanel(title: String, text: String, drawerWidth: Double): VBox {
        return VBox(8.0).apply {
            padding = Insets(12.0)
            minWidth = drawerWidth
            prefWidth = drawerWidth
            maxWidth = drawerWidth
            children.add(Label(title).apply { style = "-fx-font-weight: bold; -fx-font-size: 12;" })
            children.add(Label(text).apply { style = "-fx-text-fill: -rdr-text-hint; -fx-font-size: 11;" })
        }
    }
}
