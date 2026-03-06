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
import com.rdr.roast.ui.chart.ChartPopupCommentResult
import com.rdr.roast.ui.chart.ChartPopupEventResult
import com.rdr.roast.ui.chart.ChartPopupMode
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
import javafx.animation.TranslateTransition
import javafx.scene.layout.BorderPane
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
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.input.MouseButton
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
    @FXML lateinit var btnReference: Button
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
    @FXML lateinit var customButtonsPanel: VBox
    @FXML lateinit var commentsBlock: VBox
    @FXML lateinit var commentsListView: ListView<*>
    @FXML lateinit var lblCommentsHeader: Label
    @FXML lateinit var btnCommentsCollapse: Button
    @FXML lateinit var btnCommentsSettings: Button
    @FXML lateinit var titleBar: HBox
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
    private enum class RefCommentsSortMode { TIME, BT }
    private var refCommentsSortMode: RefCommentsSortMode = RefCommentsSortMode.TIME
    private var showRefCommentTime: Boolean = true
    private var showRefCommentValue: Boolean = true
    private var referenceCommentsCollapsed: Boolean = false

    /** Current reference/background profile (from file or server). Null = none. */
    private var referenceProfile: RoastProfile? = null

    // ── CurveModel pipeline ──────────────────────────────────────────────────
    private val btRaw = StandardCurveModel("BT")
    private val etRaw = StandardCurveModel("ET")

    // Smooth raw readings with a 5-sample moving average before RoR calculation
    private val btSmooth = MovingAverageCurveModel(btRaw, windowSize = 5)
    private val etSmooth = MovingAverageCurveModel(etRaw, windowSize = 5)

    // RoR over the smoothed signal with a 30-second window
    private val rorBt = RorCurveModel(btSmooth, windowMs = 30_000L)
    private val rorEt = RorCurveModel(etSmooth, windowMs = 30_000L)

    // ── Chart ────────────────────────────────────────────────────────────────
    private val curveChart = CurveChartFx()
    private val chartPanel = ChartPanelFx(curveChart)
    /** Set when BBP annotation is applied for current roast (profile has betweenBatchLog); reset on clearChartAndBbpState(). */
    private var bbpAnnotationSetForCurrentRoast = false

    // ── App ──────────────────────────────────────────────────────────────────
    private val recorder = RoastRecorder(DataSourceFactory.create(SettingsManager.load().machineConfig))
    private val scope = CoroutineScope(Dispatchers.JavaFx + SupervisorJob())
    private var shouldAutoStartAfterConnect = false
    private var connectionStateJob: Job? = null
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
        curveChart.applySettings(settings.chartColors, settings.chartConfig)
        refreshCustomButtonsPanel()

        // Add ChartPanelFx and sidebar overlay to container (chart behind, drawer on top)
        chartPanel.prefWidthProperty().bind(chartContainer.widthProperty())
        chartPanel.prefHeightProperty().bind(chartContainer.heightProperty())
        chartContainer.children.setAll(chartPanel, sidebarPanelContainer)
        // Pane does not stretch children; sidebar uses pref width (0 when closed)
        sidebarPanelContainer.layoutX = 0.0
        sidebarPanelContainer.layoutY = 0.0
        sidebarPanelContainer.prefHeightProperty().bind(chartContainer.heightProperty())

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
                    refreshCommentsList()
                }
            }
        }

        // Auth button and status are in the Account drawer panel (setupSidebarPanels)
        setupButtonHandlers()
        setupSettingsButton()
        setupReferenceButton()
        if (::btnShowSliders.isInitialized) {
            btnShowSliders.setOnAction { toggleSliderPanelVisibility() }
            btnShowSliders.tooltip = Tooltip("Show/hide control sliders")
        }
        updateAuthUi()
        btnShortcuts.setOnAction { showShortcutsHelp() }
        setupSpaceHotkey()
        restoreDividerPositions()
        chartContainer.sceneProperty().addListener { _, _, scene ->
            scene?.window?.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST) { saveDividerPositions() }
        }
        setupSidebarPanels()
        updateAuthUi()
        updateReadoutUnits()
        chartContainer.sceneProperty().addListener { _, _, scene ->
            scene?.window?.let { w ->
                if (w is Stage) setupTitleBar(scene, w)
            }
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
        list.placeholder = Label("No reference roast selected.").apply {
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
            }
            val display = ce.displayString?.takeIf { it.isNotBlank() } ?: formatNumeric(ce.value)
            val idx = profile.timex.indexOfLast { it <= ce.timeSec }.coerceAtLeast(0)
            val bt = profile.temp1.getOrNull(idx)
            out += CommentListEntry(title = title, timeSec = relSec, bt = bt, value = display, source = CommentListSource.REFERENCE)
        }
        synchronized(profile.comments) {
            profile.comments.forEach { comment ->
                val relSec = (comment.timeSec - charge).coerceAtLeast(0.0)
                out += CommentListEntry(
                    title = "Comment",
                    timeSec = relSec,
                    bt = comment.tempBT,
                    value = formatCommentEntry(comment),
                    source = CommentListSource.REFERENCE
                )
            }
        }
        return when (refCommentsSortMode) {
            RefCommentsSortMode.TIME -> out.sortedBy { it.timeSec ?: Double.MAX_VALUE }
            RefCommentsSortMode.BT -> out.sortedBy { it.bt ?: Double.MAX_VALUE }
        }
    }

    private fun refreshCommentsList() {
        if (!::commentsListView.isInitialized) return
        val list = commentsList()
        val rows = referenceProfile?.let { buildReferenceComments(it) } ?: emptyList()
        lblCommentsHeader.text = "Events & comments"
        if (::btnCommentsSettings.isInitialized) {
            btnCommentsSettings.isDisable = referenceProfile == null
        }
        list.items = FXCollections.observableArrayList(rows)
        list.opacity = 0.0
        FadeTransition(Duration.millis(180.0), list).apply {
            fromValue = 0.0
            toValue = 1.0
            play()
        }
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
        val referenceHasBbp = referenceProfile?.betweenBatchLog != null
        lblBbpStatus.text = if (session?.isStopped() == true) {
            "Stopped. Click Start roast to continue."
        } else {
            "Recording between batch data"
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
        lblBbpHint.text = "Click the chart to add a comment, gas or airflow."
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

        updatePhaseStrip()
        updateBbpPanel()
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

    private fun clearChartAndBbpState() {
        curveChart.clearAll()
        bbpAnnotationSetForCurrentRoast = false
        chartPanel.lastDataTimeMs = null
        tpMarkerPlaced = false
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

    /** Shows dialog to choose one of the detected roasters. Returns true if user selected one and we connected. */
    private fun showDetectedChoiceDialog(detected: List<DetectedRoaster>): Boolean {
        var applied = false
        val listView = ListView<String>().apply {
            items.addAll(detected.map { it.displayLabel() })
            prefHeight = 220.0
            prefWidth = 340.0
        }
        val stage = Stage().apply {
            title = "Выберите ростер"
            scene = Scene(listView, 340.0, 220.0)
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
    }

    private val sliderDrawerWidth = 220.0

    /** Slider drawer is shown/hidden by width and visibility only (no overlay or TranslateTransition). */
    private fun showDrawer() {
        if (!::sliderDrawerPanel.isInitialized) return
        sliderDrawerPanel.prefWidth = sliderDrawerWidth
        sliderDrawerPanel.minWidth = sliderDrawerWidth
        sliderDrawerPanel.isVisible = true
        sliderDrawerPanel.isManaged = true
    }

    private fun hideDrawer() {
        if (!::sliderDrawerPanel.isInitialized) return
        sliderDrawerPanel.prefWidth = 0.0
        sliderDrawerPanel.minWidth = 0.0
        sliderDrawerPanel.isVisible = false
        sliderDrawerPanel.isManaged = false
    }

    private fun updateControlPanel(state: ConnectionState, source: com.rdr.roast.driver.RoastDataSource) {
        if (!::sliderDrawerPanel.isInitialized) return
        controlDebounceJobs.values.forEach { it.cancel() }
        controlDebounceJobs.clear()
        sliderDrawerPanel.children.clear()
        currentSliderPanel = null
        val showControls = state is ConnectionState.Connected &&
            source is RoastControl &&
            source.supportsControl()
        if (!showControls) {
            detachableSliderWindow?.hideWindow()
            hideDrawer()
            if (::btnShowSliders.isInitialized) btnShowSliders.isVisible = false
            return
        }
        if (::btnShowSliders.isInitialized) {
            btnShowSliders.isVisible = true
            btnShowSliders.isManaged = true
        }
        val control = source as RoastControl
        val sliderSpecs = control.controlSpecs().filter { it.type == ControlSpec.ControlType.SLIDER }
        if (sliderSpecs.isEmpty()) {
            hideDrawer()
            return
        }
        val settings = SettingsManager.load()
        val eventQuantifiers = settings.eventQuantifiers
        val layoutMode = settings.sliderPanelLayoutMode
        val sliderStepConfig = settings.sliderStepConfig
        val panel = ControlSliderPanel(
            control = control,
            eventQuantifiers = eventQuantifiers,
            layoutMode = layoutMode,
            sliderStepConfig = sliderStepConfig,
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
                    }
                }
            }
        )
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
            val header = HBox(8.0).apply {
                alignment = javafx.geometry.Pos.CENTER_LEFT
                padding = Insets(4.0, 0.0, 4.0, 0.0)
                children.add(javafx.scene.control.Label("Sliders").apply { styleClass.add("control-label") })
                val layoutCombo = ComboBox<SliderPanelLayoutMode>().apply {
                    items.setAll(SliderPanelLayoutMode.SINGLE_COLUMN_TOGGLE, SliderPanelLayoutMode.GRID_ALL)
                    value = settings.sliderPanelLayoutMode
                    converter = object : javafx.util.StringConverter<SliderPanelLayoutMode>() {
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
                    tooltip = Tooltip("Slider layout: one at a time or all three visible")
                    setOnAction {
                        val v = value ?: return@setOnAction
                        SettingsManager.save(SettingsManager.load().copy(sliderPanelLayoutMode = v))
                        lastConnectionState?.let { updateControlPanel(it, recorder.dataSource) }
                    }
                }
                children.add(layoutCombo)
                val region = Region()
                HBox.setHgrow(region, Priority.ALWAYS)
                children.add(region)
                val btnCloseDrawer = Button().apply {
                    graphic = FontIcon(FontAwesomeSolid.TIMES).apply { iconSize = 10 }
                    tooltip = Tooltip("Close")
                    styleClass.add("detach-attach-btn")
                    setOnAction {
                        sliderPanelVisible = false
                        hideDrawer()
                    }
                }
                children.add(btnCloseDrawer)
                val btnDetach = Button().apply {
                    graphic = FontIcon(FontAwesomeSolid.EXTERNAL_LINK_ALT).apply { iconSize = 10 }
                    tooltip = Tooltip("Detach to separate window")
                    styleClass.add("detach-attach-btn")
                    setOnAction {
                        SettingsManager.save(SettingsManager.load().copy(sliderPanelDetached = true))
                        hideDrawer()
                        detachableSliderWindow!!.showDetached(chartContainer.scene?.window)
                        detachableSliderWindow!!.updateContent(panel)
                        chartContainer.scene?.stylesheets?.let { detachableSliderWindow!!.applyStylesheets(it) }
                    }
                }
                children.add(btnDetach)
            }
            sliderDrawerPanel.children.setAll(header, panel)
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

    /** Build custom event buttons from settings (visibility = true). On click run command via ModbusCommandExecutor on background thread. */
    private fun refreshCustomButtonsPanel() {
        if (!::customButtonsPanel.isInitialized) return
        val buttons = SettingsManager.load().customButtons.filter { it.visibility }
        customButtonsPanel.children.clear()
        if (buttons.isEmpty()) {
            customButtonsPanel.isVisible = false
            customButtonsPanel.isManaged = false
            return
        }
        customButtonsPanel.isVisible = true
        customButtonsPanel.isManaged = true
        val flow = HBox(6.0).apply { alignment = Pos.CENTER }
        for (cfg in buttons) {
            val cmd = cfg.commandString.trim()
            val btn = Button(cfg.label.ifBlank { "…" }).apply {
                style = "-fx-background-color: ${cfg.backgroundColor}; -fx-text-fill: ${cfg.textColor}; -fx-padding: 6 12; -fx-background-radius: 4;"
                tooltip = Tooltip(
                    (if (cfg.description.isNotBlank()) cfg.description + "\n" else "") +
                        "Command: ${if (cmd.isNotBlank()) cmd else "(none)"}"
                )
                setOnAction {
                    if (cmd.isEmpty()) return@setOnAction
                    val runner = recorder.dataSource as? ModbusCommandRunner
                    if (runner == null) {
                        ErrorLogBuffer.append(IllegalStateException("Not connected to Modbus"), "Custom button \"${cfg.label}\"")
                        return@setOnAction
                    }
                    scope.launch(Dispatchers.IO) {
                        try {
                            ModbusCommandExecutor.execute(runner, cmd)
                        } catch (e: Exception) {
                            Platform.runLater { ErrorLogBuffer.append(e, "Custom button \"${cfg.label}\"") }
                        }
                    }
                }
            }
            flow.children.add(btn)
        }
        customButtonsPanel.children.add(flow)
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
        centerPane.left = root
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
            scene = javafx.scene.Scene(
                com.rdr.roast.ui.HotkeysHelpView.createContent(),
                420.0, 320.0
            )
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

    private fun setupReferenceButton() {
        val loadFile = MenuItem("Load reference file...")
        val fromServer = MenuItem("Load from server...")
        val clearRef = MenuItem("Clear reference roast")
        loadFile.setOnAction { loadReferenceFromFile() }
        fromServer.setOnAction { loadReferenceFromServer() }
        clearRef.setOnAction { clearReference() }
        val menu = ContextMenu(loadFile, fromServer, clearRef)
        btnReference.tooltip = Tooltip("Load, change or clear reference roast")
        btnReference.setOnAction {
            val bounds = btnReference.localToScreen(btnReference.layoutBounds)
            menu.show(btnReference, bounds.minX, bounds.minY + bounds.height)
        }
    }

    private fun loadReferenceFromFile() {
        val chooser = FileChooser().apply {
            title = "Load reference roast"
            extensionFilters.add(FileChooser.ExtensionFilter("Artisan profile", "*.alog"))
        }
        val file = chooser.showOpenDialog(btnReference.scene?.window)
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
                    this.items.clear()
                    this.items.addAll(labels)
                    prefHeight = 280.0
                    prefWidth = 360.0
                }
                val stage = Stage().apply {
                    title = "Select reference roast"
                    scene = Scene(listView, 360.0, 280.0)
                    initModality(Modality.APPLICATION_MODAL)
                    initOwner(btnReference.scene?.window)
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
        // If live was already rebased (Charge pressed without ref), 0 = Charge so align ref at 0; else ref at 0 until user presses C
        val alignMs = if (curveChart.getChargeOffsetMs() > 0) 0L else null
        curveChart.setReferenceProfile(profile, alignMs)
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
        val stage = Stage().apply {
            title = "Вход на сервер"
            scene = Scene(root, 340.0, 320.0)
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
            scene = javafx.scene.Scene(root, 460.0, 520.0)
            initModality(Modality.APPLICATION_MODAL)
            initOwner(btnSettings.scene?.window)
        }
        chartContainer.scene?.stylesheets?.let { stage.scene.stylesheets.addAll(it) }
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
            curveChart.applySettings(updated.chartColors, updated.chartConfig)
            updateReadoutUnits()
            refreshCustomButtonsPanel()
        }
        stage.showAndWait()
    }

    private var dragStartX = 0.0
    private var dragStartY = 0.0
    private var dragStartStageX = 0.0
    private var dragStartStageY = 0.0

    private fun setupTitleBar(scene: Scene, stage: Stage) {
        ResizeHelper.enableResize(scene, stage)

        titleBar.setOnMousePressed { e ->
            if (e.button == MouseButton.PRIMARY && !stage.isMaximized) {
                dragStartX = e.screenX
                dragStartY = e.screenY
                dragStartStageX = stage.x
                dragStartStageY = stage.y
            }
        }
        titleBar.setOnMouseDragged { e ->
            if (e.button == MouseButton.PRIMARY && !stage.isMaximized) {
                stage.x = dragStartStageX + (e.screenX - dragStartX)
                stage.y = dragStartStageY + (e.screenY - dragStartY)
            }
        }
        titleBar.setOnMouseClicked { e ->
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
        btnClose.setOnAction { stage.fireEvent(WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST)) }

        updateMaximizeButtonIcon()
        setupTitleBarIcons()
        setupPlayerBarIcons()
        setupSidebarIcons()
    }

    private fun setupSidebarIcons() {
        val size = 18
        btnSidebarAccount.graphic = FontIcon(FontAwesomeSolid.USER).apply { iconSize = size }
        btnSidebarPlan.graphic = FontIcon(FontAwesomeSolid.TASKS).apply { iconSize = size }
        btnSidebarProduction.graphic = FontIcon(FontAwesomeSolid.CHART_LINE).apply { iconSize = size }
        btnSidebarSupport.graphic = FontIcon(FontAwesomeSolid.QUESTION_CIRCLE).apply { iconSize = size }
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
        val authDrawerWidth = 380.0
        val planDrawerWidth = 320.0
        val productionDrawerWidth = 360.0
        val supportDrawerWidth = 320.0
        val drawerWidths = listOf(authDrawerWidth, planDrawerWidth, productionDrawerWidth, supportDrawerWidth)
        val accountAuthLabel = Label("").apply {
            style = "-fx-font-size: 11; -fx-text-fill: #333; -fx-wrap-text: true;"
            maxWidth = authDrawerWidth - 24
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
            minWidth = authDrawerWidth
            prefWidth = authDrawerWidth
            maxWidth = authDrawerWidth
            padding = Insets(12.0)
            children.add(Label("Вход").apply { style = "-fx-font-weight: bold; -fx-font-size: 12;" })
            children.add(accountAuthLabel)
            val loginScroll = ScrollPane(loginRoot).apply {
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
            items = scheduleItems
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
            minWidth = planDrawerWidth
            prefWidth = planDrawerWidth
            maxWidth = planDrawerWidth
            minHeight = 0.0
            padding = Insets(12.0)
            children.add(Label("План (сегодня)").apply { style = "-fx-font-weight: bold; -fx-font-size: 12;" })
            children.add(javafx.scene.control.Button("Обновить").apply {
                setOnAction { loadScheduleList() }
                maxWidth = Double.MAX_VALUE
            })
            children.add(scheduleListView)
            VBox.setVgrow(scheduleListView, Priority.ALWAYS)
        }
        val productionPanel = VBox().apply {
            minWidth = productionDrawerWidth
            prefWidth = productionDrawerWidth
            maxWidth = productionDrawerWidth
            minHeight = 0.0
            padding = Insets(8.0)
            val scroll = ScrollPane(roastPropsRoot).apply {
                isFitToWidth = true
                isPannable = false
                minHeight = 0.0
            }
            children.add(scroll)
            VBox.setVgrow(scroll, Priority.ALWAYS)
        }
        val logLines = ErrorLogBuffer.getObservableLines()
        val logListView = ListView<String>().apply {
            items = logLines
            selectionModel.selectionMode = SelectionMode.MULTIPLE
            prefHeight = 200.0
            minHeight = 80.0
        }
        logLines.addListener(javafx.collections.ListChangeListener { _ ->
            if (logLines.isNotEmpty()) logListView.scrollTo(logLines.size - 1)
        })
        val supportPanel = VBox(10.0).apply {
            padding = Insets(12.0)
            minWidth = supportDrawerWidth
            prefWidth = supportDrawerWidth
            maxWidth = supportDrawerWidth
            children.add(Label("Support").apply { style = "-fx-font-weight: bold; -fx-font-size: 12;" })
            children.add(Button("Горячие клавиши (F1)").apply {
                graphic = FontIcon(FontAwesomeSolid.KEYBOARD).apply { iconSize = 18 }
                style = "-fx-font-size: 11; -fx-cursor: hand; -fx-wrap-text: true;"
                setOnAction { btnShortcuts.fire() }
                maxWidth = Double.MAX_VALUE
            })
            children.add(Label("Логи").apply { style = "-fx-font-weight: bold; -fx-font-size: 11;" })
            children.add(logListView)
            children.add(HBox(8.0).apply {
                children.add(Button("Копировать").apply {
                    setOnAction {
                        val sel = logListView.selectionModel.selectedItems
                        val text = if (sel.isEmpty()) logLines.joinToString("\n") else sel.joinToString("\n")
                        Clipboard.getSystemClipboard().setContent(ClipboardContent().apply { putString(text) })
                    }
                    maxWidth = Double.MAX_VALUE
                    HBox.setHgrow(this, Priority.ALWAYS)
                })
                children.add(Button("Очистить").apply {
                    setOnAction { ErrorLogBuffer.clear() }
                    maxWidth = Double.MAX_VALUE
                    HBox.setHgrow(this, Priority.ALWAYS)
                })
                children.add(Button("Сохранить").apply {
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

    private fun openDrawer(panelContent: VBox, drawerWidth: Double) {
        currentDrawerWidth = drawerWidth
        val existingWrapper = sidebarPanelContainer.children.firstOrNull() as? Pane
        if (existingWrapper != null) {
            existingWrapper.children.setAll(panelContent)
            existingWrapper.translateX = -drawerWidth
            TranslateTransition(Duration.millis(200.0), existingWrapper).apply {
                toX = 0.0
                play()
            }
            return
        }
        val wrapper = Pane().apply {
            minWidth = drawerWidth
            prefWidth = drawerWidth
            maxWidth = drawerWidth
            children.add(panelContent)
            translateX = -drawerWidth
        }
        sidebarPanelContainer.prefWidth = drawerWidth
        sidebarPanelContainer.minWidth = drawerWidth
        sidebarPanelContainer.children.setAll(wrapper)
        TranslateTransition(Duration.millis(200.0), wrapper).apply {
            toX = 0.0
            play()
        }
    }

    private fun closeDrawer() {
        val w = currentDrawerWidth
        val wrapper = sidebarPanelContainer.children.firstOrNull() ?: run {
            sidebarPanelContainer.prefWidth = 0.0
            sidebarPanelContainer.minWidth = 0.0
            sidebarPanelContainer.children.clear()
            return
        }
        TranslateTransition(Duration.millis(180.0), wrapper).apply {
            toX = -w
            setOnFinished {
                sidebarPanelContainer.prefWidth = 0.0
                sidebarPanelContainer.minWidth = 0.0
                sidebarPanelContainer.children.clear()
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
            children.add(Label(text).apply { style = "-fx-text-fill: #888888; -fx-font-size: 11;" })
        }
    }
}
