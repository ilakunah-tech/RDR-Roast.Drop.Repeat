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
import com.rdr.roast.app.RoastRecorder
import com.rdr.roast.app.SettingsManager
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
import com.rdr.roast.driver.ControlSpec
import com.rdr.roast.driver.simulator.SimulatorSource
import com.rdr.roast.ui.chart.ChartPanelFx
import com.rdr.roast.ui.chart.ChartPopupCommentResult
import com.rdr.roast.ui.chart.ChartPopupEventResult
import com.rdr.roast.ui.chart.ChartPopupMode
import com.rdr.roast.ui.chart.CurveChartFx
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.ContextMenu
import javafx.scene.control.MenuItem
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
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.animation.TranslateTransition
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.StackPane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.shape.Rectangle
import javafx.stage.FileChooser
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid
import org.kordamp.ikonli.javafx.FontIcon
import javafx.util.Duration
import javafx.stage.Modality
import javafx.stage.Stage
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
    @FXML lateinit var chartContainer: StackPane
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
    @FXML lateinit var lblConnectionStatus: Label
    @FXML lateinit var centerSplit: SplitPane
    @FXML lateinit var rightPanel: VBox
    @FXML lateinit var sidebarPanelContainer: StackPane
    @FXML lateinit var btnAuth: Button
    @FXML lateinit var btnSidebarSystem: ToggleButton
    @FXML lateinit var btnSidebarPlan: ToggleButton
    @FXML lateinit var btnSidebarProduction: ToggleButton
    @FXML lateinit var btnSidebarSupport: ToggleButton
    @FXML lateinit var btnSidebarAccount: ToggleButton
    @FXML lateinit var btnSync: Button
    @FXML lateinit var lblReferenceLabel: Label
    @FXML lateinit var referenceSummaryBox: VBox
    @FXML lateinit var lblRefDuration: Label
    @FXML lateinit var lblRefDevTime: Label
    @FXML lateinit var lblRefDevTimeRatio: Label
    @FXML lateinit var lblRefEndTemp: Label
    @FXML lateinit var referencePanel: VBox
    /** Unified comments row for roast or BBP. */
    private data class CommentEntry(
        val timeSec: Double,
        val tempBt: Double?,
        val label: String,
        val confirmed: Boolean = true
    )
    @FXML lateinit var bbpPanel: VBox
    @FXML lateinit var lblBbpStatus: Label
    @FXML lateinit var lblBbpReference: Label
    @FXML lateinit var lblBbpMinBt: Label
    @FXML lateinit var lblBbpMaxBt: Label
    @FXML lateinit var lblBbpHint: Label
    @FXML lateinit var lblPlayerBarTitle: Label
    @FXML lateinit var controlPanelContainer: VBox
    @FXML lateinit var commentsBlock: VBox
    @FXML lateinit var commentsGrid: javafx.scene.layout.GridPane
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

    /** Current reference/background profile (from file or server). Null = none. */
    private var referenceProfile: RoastProfile? = null
    private var referenceLabel: String? = null

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

    @FXML
    fun initialize() {
        // Bind CurveModels to chart series once; apply saved chart settings
        curveChart.bindDefaultCurves(btSmooth, etSmooth, rorBt, rorEt)
        val settings = SettingsManager.load()
        curveChart.applySettings(settings.chartColors, settings.chartConfig)

        // Add ChartPanelFx to the container and stretch it to fill
        chartPanel.prefWidthProperty().bind(chartContainer.widthProperty())
        chartPanel.prefHeightProperty().bind(chartContainer.heightProperty())
        chartContainer.children.setAll(chartPanel)

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
                    updateCommentsGrid()
                }
            }
        }

        // Auth button and status are in the Account drawer panel (setupSidebarPanels)
        setupButtonHandlers()
        setupSettingsButton()
        setupReferenceButton()
        updateAuthUi()
        btnShortcuts.setOnAction { showShortcutsHelp() }
        setupSpaceHotkey()
        restoreDividerPositions()
        chartContainer.sceneProperty().addListener { _, _, scene ->
            scene?.window?.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST) { saveDividerPositions() }
        }
        setupSidebarPanels()
        updateAuthUi()
        updatePlayerBarTitle()
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
                            recorder.markEventAt(timeSec, EventType.CC)
                            curveChart.addEventMarker(result.timeMs, formatMarkerLabel(result.label, btAtTime))
                        }
                        result.label.startsWith("FC @") -> {
                            recorder.markEventAt(timeSec, EventType.FC)
                            curveChart.addEventMarker(result.timeMs, formatMarkerLabel(result.label, btAtTime))
                        }
                    }
                    updatePhaseStrip()
                    updateCommentsGrid()
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
                    updateCommentsGrid()
                    updateBbpPanel()
                }
            }
        }
        if (::btnCommentsSettings.isInitialized) {
            btnCommentsSettings.graphic = FontIcon(FontAwesomeSolid.COG).apply { iconSize = 10 }
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
                updateCommentsGrid()
            }
        }

        updatePhaseStrip()
        updateBbpPanel()
    }

    private fun updatePhaseStrip() {
        val profile = recorder.currentProfile.value
        if (profile.timex.isEmpty()) return
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

    private fun updateCommentsGrid() {
        if (!::commentsGrid.isInitialized) return
        commentsGrid.children.clear()
        val profile = recorder.currentProfile.value
        val state = recorder.stateFlow.value
        val chargeTimeSec = profile.eventByType(EventType.CHARGE)?.timeSec ?: 0.0
        val rows = if (state == RecorderState.BBP) {
            recorder.currentBbpSession?.comments
                ?.sortedBy { it.timeSec }
                ?.takeLast(10)
                ?.map { comment ->
                    CommentEntry(
                        timeSec = comment.timeSec,
                        tempBt = comment.tempBT,
                        label = formatCommentEntry(comment),
                        confirmed = true
                    )
                } ?: emptyList()
        } else {
            buildRoastCommentEntries(profile)
                .sortedBy { it.timeSec }
                .takeLast(10)
        }
        rows.forEachIndexed { i, entry ->
            val relTimeSec = if (state == RecorderState.BBP) entry.timeSec else (entry.timeSec - chargeTimeSec).coerceAtLeast(0.0)
            val oddClass = if (i % 2 == 0) "comment-row-odd" else "comment-row-even"
            val timeLabel = Label(formatSec(relTimeSec)).apply {
                styleClass.addAll("comment-time", oddClass)
                maxWidth = Double.MAX_VALUE
            }
            val tempLabel = Label(entry.tempBt?.let { "%.1f °C".format(it) } ?: "").apply {
                styleClass.addAll("comment-temp", oddClass)
                maxWidth = Double.MAX_VALUE
            }
            val eventLabel = Label(entry.label).apply {
                styleClass.addAll("comment-event", oddClass)
                maxWidth = Double.MAX_VALUE
            }
            val checkLabel = Label("✓").apply {
                styleClass.addAll("comment-check", oddClass)
            }
            javafx.scene.layout.GridPane.setRowIndex(timeLabel, i)
            javafx.scene.layout.GridPane.setColumnIndex(timeLabel, 0)
            javafx.scene.layout.GridPane.setRowIndex(tempLabel, i)
            javafx.scene.layout.GridPane.setColumnIndex(tempLabel, 1)
            javafx.scene.layout.GridPane.setRowIndex(eventLabel, i)
            javafx.scene.layout.GridPane.setColumnIndex(eventLabel, 2)
            javafx.scene.layout.GridPane.setRowIndex(checkLabel, i)
            javafx.scene.layout.GridPane.setColumnIndex(checkLabel, 3)
            commentsGrid.children.addAll(timeLabel, tempLabel, eventLabel, checkLabel)
        }
    }

    private fun buildRoastCommentEntries(profile: RoastProfile): List<CommentEntry> {
        val eventEntries = synchronized(profile.events) {
            profile.events.mapNotNull { event ->
                val label = when (event.type) {
                    EventType.CHARGE -> "Charge"
                    EventType.TP -> "Turning point"
                    EventType.CC -> "Dry end"
                    EventType.FC -> "First crack"
                    EventType.DROP -> "Drop"
                }
                CommentEntry(event.timeSec, event.tempBT, label)
            }
        }
        val commentEntries = synchronized(profile.comments) {
            profile.comments.map { comment ->
                CommentEntry(comment.timeSec, comment.tempBT, formatCommentEntry(comment))
            }
        }
        return (eventEntries + commentEntries).sortedBy { it.timeSec }
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
            val lowText = lowIdx?.let { idx ->
                "Lowest BT: %.1f °C @ %s".format(session.temp1[idx], formatSec(session.timex.getOrElse(idx) { 0.0 }))
            } ?: "Lowest BT: —"
            val highText = highIdx?.let { idx ->
                "Highest BT: %.1f °C @ %s".format(session.temp1[idx], formatSec(session.timex.getOrElse(idx) { 0.0 }))
            } ?: "Highest BT: —"
            lblBbpMinBt.text = lowText
            lblBbpMaxBt.text = highText
        } else {
            lblBbpMinBt.text = "Lowest BT: —"
            lblBbpMaxBt.text = "Highest BT: —"
        }
        lblBbpHint.text = "Click the chart to add a comment, gas or airflow."
    }

    private fun clearChartAndBbpState() {
        curveChart.clearAll()
        bbpAnnotationSetForCurrentRoast = false
        chartPanel.lastDataTimeMs = null
        tpMarkerPlaced = false
        updateCommentsGrid()
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
        val settings = SettingsManager.load()
        if (!settings.autoDetectRoaster) {
            recorder.connect()
            startConnectionStateCollector()
            return
        }
        scope.launch(kotlinx.coroutines.Dispatchers.Default) {
            var list: List<DetectedRoaster>
            val lastConfig = settings.lastDetectedConfig
            if (settings.rememberLastDetectedRoaster && lastConfig != null) {
                val result = withTimeoutOrNull(2500L) { ConnectionTester.test(lastConfig) }
                if (result != null && result.isSuccess && result.getOrNull() is ConnectionState.Connected) {
                    Platform.runLater {
                        applyDetectedConfig(lastConfig)
                        recorder.connect()
                        startConnectionStateCollector()
                        persistLastDetected(lastConfig)
                    }
                    return@launch
                }
            }
            list = RoasterDiscovery.discover(settings.discoveryTcpHosts)
            Platform.runLater {
                when {
                    list.isEmpty() -> {
                        recorder.connect()
                        startConnectionStateCollector()
                    }
                    list.size == 1 -> {
                        applyDetectedConfig(list[0].config)
                        recorder.connect()
                        startConnectionStateCollector()
                        persistLastDetected(list[0].config)
                    }
                    else -> {
                        val applied = showDetectedChoiceDialog(list)
                        if (!applied) {
                            recorder.connect()
                            startConnectionStateCollector()
                        }
                    }
                }
            }
        }
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
        if (!::lblConnectionStatus.isInitialized) return
        val (text, style) = when (state) {
            is ConnectionState.Disconnected -> "○ Disconnected" to "-fx-text-fill: #888888; -fx-font-size: 12px; -fx-min-width: 140;"
            is ConnectionState.Connecting -> "⟳ Connecting..." to "-fx-text-fill: #e67e22; -fx-font-size: 12px; -fx-min-width: 140;"
            is ConnectionState.Connected -> "● ${state.deviceName}" to "-fx-text-fill: #27ae60; -fx-font-size: 12px; -fx-min-width: 140;"
            is ConnectionState.Error -> "⚠ ${state.message.take(40)}${if (state.message.length > 40) "..." else ""}" to "-fx-text-fill: #c0392b; -fx-font-size: 12px; -fx-min-width: 140;"
        }
        lblConnectionStatus.text = text
        lblConnectionStatus.style = style
        updateControlPanel(state, recorder.dataSource)
    }

    private fun updateControlPanel(state: ConnectionState, source: com.rdr.roast.driver.RoastDataSource) {
        if (!::controlPanelContainer.isInitialized) return
        controlDebounceJobs.values.forEach { it.cancel() }
        controlDebounceJobs.clear()
        controlPanelContainer.children.clear()
        val showControls = state is ConnectionState.Connected &&
            source is RoastControl &&
            source.supportsControl()
        controlPanelContainer.isVisible = showControls
        controlPanelContainer.isManaged = showControls
        if (!showControls) return
        val control = source as RoastControl
        val sliderSpecs = control.controlSpecs().filter { it.type == ControlSpec.ControlType.SLIDER }
        if (sliderSpecs.isEmpty()) return
        val stepConfig = SettingsManager.load().sliderStepConfig
        val slidersRow = HBox(8.0).apply {
            alignment = Pos.TOP_CENTER
            HBox.setHgrow(this, Priority.ALWAYS)
        }
        for (spec in sliderSpecs) {
            val slider = Slider(spec.min, spec.max, spec.min).apply {
                orientation = Orientation.VERTICAL
                prefHeight = 120.0
                minHeight = 80.0
                blockIncrement = 5.0
                isShowTickLabels = false
                styleClass.add("slider-vertical")
            }
            val valueField = TextField().apply {
                text = formatControlValue(slider.value)
                alignment = Pos.CENTER
                styleClass.add("control-value-field")
                tooltip = Tooltip("${spec.min.toInt()} – ${spec.max.toInt()} ${spec.unit}")
            }
            fun applyFieldValue() {
                val parsed = valueField.text.replace(',', '.').toDoubleOrNull() ?: return
                val clamped = parsed.coerceIn(spec.min, spec.max)
                slider.value = clamped
                valueField.text = formatControlValue(clamped)
            }
            valueField.setOnAction { applyFieldValue() }
            valueField.focusedProperty().addListener { _, _, f -> if (!f) applyFieldValue() }
            slider.valueProperty().addListener { _, _, newVal ->
                if (!valueField.isFocused) valueField.text = formatControlValue(newVal.toDouble())
                controlDebounceJobs[spec.id]?.cancel()
                controlDebounceJobs[spec.id] = scope.launch {
                    delay(1000L)
                    controlDebounceJobs.remove(spec.id)
                    control.setControl(spec.id, newVal.toDouble())
                }
            }
            val leftStepCol = VBox(1.0).apply { alignment = Pos.TOP_CENTER }
            val rightStepCol = VBox(1.0).apply { alignment = Pos.TOP_CENTER }
            for (v in stepConfig.leftSteps.sortedDescending()) {
                leftStepCol.children.add(Button(v.toString()).apply {
                    styleClass.add("control-step-button")
                    isFocusTraversable = false
                    setOnAction { slider.value = v.toDouble().coerceIn(spec.min, spec.max) }
                })
            }
            for (v in stepConfig.rightSteps.sortedDescending()) {
                rightStepCol.children.add(Button(v.toString()).apply {
                    styleClass.add("control-step-button")
                    isFocusTraversable = false
                    setOnAction { slider.value = v.toDouble().coerceIn(spec.min, spec.max) }
                })
            }
            val zeroBtn = Button("0").apply {
                styleClass.add("control-step-button")
                isFocusTraversable = false
                setOnAction { slider.value = 0.0 }
            }
            val sliderWithSteps = HBox(2.0).apply {
                alignment = Pos.TOP_CENTER
                children.addAll(leftStepCol, slider, rightStepCol)
            }
            val column = VBox(2.0).apply {
                alignment = Pos.TOP_CENTER
                children.add(Label(spec.displayName).apply { styleClass.add("control-label") })
                children.add(sliderWithSteps)
                children.add(zeroBtn)
                children.add(valueField)
                HBox.setHgrow(this, Priority.ALWAYS)
            }
            slidersRow.children.add(column)
        }
        controlPanelContainer.children.add(slidersRow)
    }

    private fun formatControlValue(value: Double): String {
        val rounded = value.toInt()
        return if (kotlin.math.abs(value - rounded.toDouble()) < 0.001) {
            rounded.toString()
        } else {
            "%.1f".format(value)
        }
    }

    private fun updateButtonStates(state: RecorderState) {
        when (state) {
            RecorderState.DISCONNECTED -> {
                btnStart.text = "Connect"
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
                btnStart.text = "Start roast"
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
                btnStart.text = "Start new roast"
                btnStart.isDisable = false
                btnStop.isDisable = true
                btnAbort.isDisable = true
                setBbpButtonsVisible(false)
            }
            RecorderState.BBP -> {
                curveChart.showBbpMode()
                btnStart.text = "Start roast"
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

    private fun triggerCharge() {
        val profile = recorder.currentProfile.value
        if (profile.eventByType(EventType.CHARGE) != null) return
        val sample = recorder.currentSample.value ?: return
        recorder.markEvent(EventType.CHARGE)
        val chargeTimeMs = (sample.timeSec * 1000).toLong()
        if (referenceProfile != null) {
            curveChart.addEventMarker(chargeTimeMs, "Charge @ %.1f °C".format(sample.bt),
                com.rdr.roast.ui.chart.CurveChartFx.COLOR_MARKER)
            curveChart.setReferenceProfile(referenceProfile, chargeTimeMs)
        } else {
            curveChart.rebaseAllSeries(chargeTimeMs)
            curveChart.addEventMarker(chargeTimeMs, "Charge @ %.1f °C".format(sample.bt),
                com.rdr.roast.ui.chart.CurveChartFx.COLOR_MARKER)
        }
        updateCommentsGrid()
    }

    private fun triggerDrop() {
        val sample = recorder.currentSample.value ?: return
        recorder.markEvent(EventType.DROP)
        curveChart.addEventMarker((sample.timeSec * 1000).toLong(), "Drop @ %.1f °C".format(sample.bt),
            com.rdr.roast.ui.chart.CurveChartFx.COLOR_MARKER)
        updateCommentsGrid()
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
                updateCommentsGrid()
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
        referenceLabel = null
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
        updatePlayerBarTitle()
        updateBbpPanel()
    }

    private fun setReference(profile: RoastProfile, label: String) {
        referenceProfile = profile
        referenceLabel = label
        if (::lblReferenceLabel.isInitialized) lblReferenceLabel.text = label
        if (::referencePanel.isInitialized) {
            referencePanel.isVisible = true
            referencePanel.isManaged = true
        }
        updateReferenceSummary(profile)
        updateReferenceComments(profile)
        updatePlayerBarTitle()
        // If live was already rebased (Charge pressed without ref), 0 = Charge so align ref at 0; else ref at 0 until user presses C
        val alignMs = if (curveChart.getChargeOffsetMs() > 0) 0L else null
        curveChart.setReferenceProfile(profile, alignMs)
        updateBbpPanel()
    }

    private fun formatRefMmSs(sec: Double): String {
        val s = sec.coerceAtLeast(0.0).toInt()
        val m = s / 60
        val secPart = s % 60
        return "%d:%02d".format(m, secPart)
    }

    private fun updatePlayerBarTitle() {
        if (!::lblPlayerBarTitle.isInitialized) return
        val title = SettingsManager.load().roastPropertiesTitle.trim()
        val ref = referenceLabel ?: ""
        val text = when {
            title.isNotEmpty() && ref.isNotEmpty() -> "$title ($ref)"
            title.isNotEmpty() -> title
            ref.isNotEmpty() -> "($ref)"
            else -> ""
        }
        lblPlayerBarTitle.text = text
        lblPlayerBarTitle.isVisible = text.isNotEmpty()
        lblPlayerBarTitle.isManaged = text.isNotEmpty()
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
        val lastIdx = profile.timex.indexOfLast { it <= endSec }.coerceAtLeast(0)
        val endTemp = profile.eventByType(EventType.DROP)?.tempBT
            ?: profile.temp1.getOrNull(lastIdx)
            ?: 0.0
        lblRefDuration.text = formatRefMmSs(durationSec)
        lblRefDevTime.text = formatRefMmSs(devTimeSec)
        lblRefDevTimeRatio.text = "%.1f%%".format(devTimeRatio)
        lblRefEndTemp.text = "%.1f °C".format(endTemp)
        referenceSummaryBox.isVisible = true
        referenceSummaryBox.isManaged = true
    }

    private fun updateReferenceComments(profile: RoastProfile) {
        // Reference comments are now shown inline in the Comments grid when reference is loaded
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
        // Settings open as drawer from sidebar; no modal window
        btnSettings.setOnAction {
            btnSidebarSystem.isSelected = true
        }
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
        btnSidebarSystem.graphic = FontIcon(FontAwesomeSolid.COG).apply { iconSize = size }
        btnSidebarPlan.graphic = FontIcon(FontAwesomeSolid.TASKS).apply { iconSize = size }
        btnSidebarProduction.graphic = FontIcon(FontAwesomeSolid.CHART_LINE).apply { iconSize = size }
        btnSidebarSupport.graphic = FontIcon(FontAwesomeSolid.QUESTION_CIRCLE).apply { iconSize = size }
    }

    private fun setupPlayerBarIcons() {
        val size = 18
        btnStart.graphic = FontIcon(FontAwesomeSolid.PLAY).apply { iconSize = size }
        btnStop.graphic = FontIcon(FontAwesomeSolid.STOP).apply { iconSize = size }
        btnAbort.graphic = FontIcon(FontAwesomeSolid.TIMES).apply { iconSize = size }
    }

    private fun setupTitleBarIcons() {
        val size = 14
        btnClose.graphic = FontIcon(FontAwesomeSolid.TIMES).apply { iconSize = size }
        btnMinimize.graphic = FontIcon(FontAwesomeSolid.MINUS).apply { iconSize = size }
        updateMaximizeButtonIcon()
    }

    private fun updateMaximizeButtonIcon() {
        val stage = btnMaximize.scene?.window as? Stage ?: return
        btnMaximize.graphic = FontIcon(
            if (stage.isMaximized) FontAwesomeSolid.COMPRESS else FontAwesomeSolid.EXPAND
        ).apply { iconSize = 14 }
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
        val authDrawerWidth = 240.0
        val settingsDrawerWidth = 440.0
        val planDrawerWidth = 320.0
        val productionDrawerWidth = 360.0
        val supportDrawerWidth = 320.0
        val drawerWidths = listOf(authDrawerWidth, settingsDrawerWidth, planDrawerWidth, productionDrawerWidth, supportDrawerWidth)
        val authLabel = Label("").apply {
            style = "-fx-font-size: 10; -fx-text-fill: #666666; -fx-wrap-text: true;"
            maxWidth = settingsDrawerWidth - 24
        }
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
            children.add(loginRoot)
            children.add(logoutButton)
        }
        val settingsLoader = FXMLLoader(javaClass.getResource("/com/rdr/roast/ui/SettingsView.fxml"))
        val settingsRoot = settingsLoader.load<Parent>()
        val settingsController = settingsLoader.getController<SettingsController>()
        settingsController.onCloseDrawer = { sc ->
            closeDrawer()
            btnSidebarSystem.isSelected = false
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
        }
        val systemPanel = VBox().apply {
            minWidth = settingsDrawerWidth
            prefWidth = settingsDrawerWidth
            maxWidth = settingsDrawerWidth
            maxHeight = Double.MAX_VALUE
            padding = Insets(8.0)
            children.add(VBox(4.0).apply {
                children.add(Label("Вход").apply { style = "-fx-font-weight: bold; -fx-font-size: 11;" })
                children.add(authLabel)
            })
            val scroll = ScrollPane(settingsRoot).apply {
                isFitToWidth = true
                isPannable = false
                minHeight = 0.0
                VBox.setMargin(this, Insets(8.0, 0.0, 0.0, 0.0))
            }
            children.add(scroll)
            VBox.setVgrow(scroll, Priority.ALWAYS)
        }
        systemPanel.maxHeightProperty().bind(sidebarPanelContainer.heightProperty())
        // Production drawer: Roast Properties form (no separate Reference button)
        val roastPropsLoader = FXMLLoader(javaClass.getResource("/com/rdr/roast/ui/RoastPropertiesView.fxml"))
        val roastPropsRoot = roastPropsLoader.load<Parent>()
        roastPropertiesController = roastPropsLoader.getController()
        roastPropertiesController?.setStage(null)
        roastPropertiesController?.onApply = { _, profile ->
            if (profile != null) setReference(profile, "Server (Roast Properties)")
            else clearReference()
            updatePlayerBarTitle()
        }
        roastPropertiesController?.onCloseDrawer = {
            closeDrawer()
            btnSidebarProduction.isSelected = false
            updatePlayerBarTitle()
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
        val panels = listOf(authPanel, systemPanel, planPanel, productionPanel, supportPanel)
        listOf(systemPanel, planPanel, supportPanel).forEach { vbox ->
            vbox.children.filterIsInstance<Button>().forEach { it.maxWidth = Double.MAX_VALUE }
        }
        val toggles = listOf(btnSidebarAccount, btnSidebarSystem, btnSidebarPlan, btnSidebarProduction, btnSidebarSupport)
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
                        2 -> refreshScheduleList?.invoke()
                        3 -> {
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
