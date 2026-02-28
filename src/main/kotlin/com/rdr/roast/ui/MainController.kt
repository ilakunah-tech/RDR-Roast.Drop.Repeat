package com.rdr.roast.ui

import com.rdr.roast.app.DataSourceFactory
import com.rdr.roast.app.DetectedRoaster
import com.rdr.roast.app.ProfileStorage
import com.rdr.roast.app.ConnectionTester
import com.rdr.roast.app.MachineConfig
import com.rdr.roast.app.RoasterDiscovery
import com.rdr.roast.app.RecorderState
import com.rdr.roast.app.ReferenceApi
import com.rdr.roast.app.ServerApi
import com.rdr.roast.app.ServerApiException
import com.rdr.roast.app.ServerConfig
import com.rdr.roast.app.buildAroastPayload
import com.rdr.roast.app.RoastRecorder
import com.rdr.roast.app.SettingsManager
import com.rdr.roast.domain.EventType
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
import com.rdr.roast.ui.chart.CurveChartFx
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.geometry.Insets
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
import javafx.scene.control.ScrollPane
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
import javafx.util.Duration
import javafx.stage.Modality
import javafx.stage.Stage
import javafx.stage.WindowEvent
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
import java.util.UUID

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
    @FXML lateinit var rightSplit: SplitPane
    @FXML lateinit var sidebarPanelContainer: StackPane
    @FXML lateinit var btnAuth: Button
    @FXML lateinit var btnSidebarSystem: ToggleButton
    @FXML lateinit var btnSidebarProduction: ToggleButton
    @FXML lateinit var btnSidebarSupport: ToggleButton
    @FXML lateinit var btnSync: Button
    @FXML lateinit var lblReferenceLabel: Label
    @FXML lateinit var controlPanelContainer: VBox

    /** Auth UI in system sidebar (set in setupSidebarPanels). */
    private var authStatusLabel: Label? = null
    private var authButton: Button? = null

    /** Roast Properties form in Production drawer (set in setupSidebarPanels). */
    private var roastPropertiesController: RoastPropertiesController? = null
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

        // Elapsed-time label: from Charge (00:00) after C, or "Pre-heat" before; in BBP show BBP timer
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
                                "Pre-heat"
                            }
                        }
                    }
                }
            }
        }

        authButton = btnAuth
        btnAuth.graphic = Label("👤").apply { style = "-fx-font-size: 14;" }
        btnAuth.setOnAction { onAuthButtonClick() }
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
        updateReadoutUnits()

        // Connect to configured roaster on startup (or run discovery if auto-detect enabled)
        connectOrDiscover()

        // When user adds DE/FC from chart popup, store in profile and refresh phase strip
        chartPanel.onEventAdded = { timeMs, label ->
            val timeSec = timeMs / 1000.0
            when {
                label.startsWith("DE @") -> recorder.markEventAt(timeSec, EventType.DE)
                label.startsWith("FC @") -> recorder.markEventAt(timeSec, EventType.FC)
            }
            updatePhaseStrip()
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
                curveChart.addEventMarker((tpSec * 1000).toLong(), "TP @ %s · %.1f °C".format(formatSec(tpSec), tpBt),
                    com.rdr.roast.ui.chart.CurveChartFx.COLOR_MARKER)
            }
        }

        updatePhaseStrip()
    }

    private fun updatePhaseStrip() {
        val profile = recorder.currentProfile.value
        if (profile.timex.isEmpty()) return
        if (recorder.stateFlow.value == RecorderState.RECORDING && profile.betweenBatchLog != null && !bbpAnnotationSetForCurrentRoast) {
            curveChart.setBetweenBatchAnnotation(profile.betweenBatchLog!!.durationMs)
            bbpAnnotationSetForCurrentRoast = true
        }
        val endMs = (profile.timex.maxOrNull()!! * 1000).toLong()
        val deMs = (profile.eventByType(EventType.DE) ?: profile.eventByType(EventType.CC))?.timeSec?.times(1000)?.toLong()
        val fcMs = profile.eventByType(EventType.FC)?.timeSec?.times(1000)?.toLong()
        curveChart.updatePhaseStrip(endMs, deMs, fcMs)
    }

    private var tpMarkerPlaced = false

    private fun clearChartAndBbpState() {
        curveChart.clearAll()
        bbpAnnotationSetForCurrentRoast = false
        chartPanel.lastDataTimeMs = null
        tpMarkerPlaced = false
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
            is ConnectionState.Disconnected -> "○ Отключено" to "-fx-text-fill: #888888; -fx-font-size: 12px; -fx-min-width: 160;"
            is ConnectionState.Connecting -> "⟳ Подключение…" to "-fx-text-fill: #e67e22; -fx-font-size: 12px; -fx-min-width: 160;"
            is ConnectionState.Connected -> "● ${state.deviceName}" to "-fx-text-fill: #27ae60; -fx-font-size: 12px; -fx-min-width: 160;"
            is ConnectionState.Error -> "⚠ ${state.message.take(40)}${if (state.message.length > 40) "…" else ""}" to "-fx-text-fill: #c0392b; -fx-font-size: 12px; -fx-min-width: 160;"
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
        for (spec in control.controlSpecs()) {
            when (spec.type) {
                ControlSpec.ControlType.SLIDER -> {
                    val label = Label(spec.displayName).apply {
                        style = "-fx-font-size: 10; -fx-text-fill: #555555;"
                    }
                    val valueLabel = Label("${spec.min.toInt()} ${spec.unit}").apply {
                        style = "-fx-font-size: 9; -fx-text-fill: #888888;"
                    }
                    val slider = Slider(spec.min, spec.max, spec.min).apply {
                        prefWidth = 160.0
                        blockIncrement = 5.0
                        majorTickUnit = 25.0
                        minorTickCount = 4
                        isShowTickLabels = false
                        valueProperty().addListener { _, _, newVal ->
                            valueLabel.text = "%.0f %s".format(newVal.toDouble(), spec.unit)
                            controlDebounceJobs[spec.id]?.cancel()
                            controlDebounceJobs[spec.id] = scope.launch {
                                delay(1000L)
                                controlDebounceJobs.remove(spec.id)
                                control.setControl(spec.id, newVal.toDouble())
                            }
                        }
                    }
                    controlPanelContainer.children.addAll(label, HBox(10.0).apply {
                        children.addAll(slider, valueLabel)
                        alignment = Pos.CENTER_LEFT
                    })
                }
                ControlSpec.ControlType.BUTTON -> {
                    val btn = Button(spec.displayName).apply {
                        setOnAction { control.setControlState(spec.id, true) }
                    }
                    controlPanelContainer.children.add(btn)
                }
            }
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
                btnStart.text = "Start"
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
                btnStart.text = "New Roast"
                btnStart.isDisable = false
                btnStop.isDisable = true
                btnAbort.isDisable = true
                setBbpButtonsVisible(false)
            }
            RecorderState.BBP -> {
                curveChart.showBbpMode()
                btnStart.text = "Start"
                btnStart.isDisable = false
                btnStop.isDisable = true
                btnAbort.isDisable = true
                setBbpButtonsVisible(true)
            }
        }
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
            btnRestartBbp.tooltip = Tooltip("Restart BBP recording (15 min limit)")
        }
        if (::btnStopBbp.isInitialized) {
            btnStopBbp.setOnAction { recorder.stopBbpRecording() }
            btnStopBbp.tooltip = Tooltip("Stop BBP recording (data kept until Start)")
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
            // Artisan-style: do not rebase; keep live in raw time and shift reference so Ref Charge aligns with live Charge
            curveChart.addEventMarker(chargeTimeMs, "Charge @ %.1f °C".format(sample.bt),
                com.rdr.roast.ui.chart.CurveChartFx.COLOR_MARKER)
            curveChart.setReferenceProfile(referenceProfile, chargeTimeMs)
        } else {
            curveChart.rebaseAllSeries(chargeTimeMs)
            curveChart.addEventMarker(chargeTimeMs, "Charge @ %.1f °C".format(sample.bt),
                com.rdr.roast.ui.chart.CurveChartFx.COLOR_MARKER)
        }
    }

    private fun triggerDrop() {
        val sample = recorder.currentSample.value ?: return
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
                // TODO: show error dialog
            }
            hideFinishPanel()
            recorder.reset()
            resetCurvePipeline()
            clearChartAndBbpState()
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
        val roastId = UUID.randomUUID().toString()
        val dateIso8601 = Instant.now().toString()
        val payload = buildAroastPayload(
            profile = profile,
            title = settings.roastPropertiesTitle,
            weightInKg = settings.roastPropertiesWeightInKg,
            weightOutKg = settings.roastPropertiesWeightOutKg,
            notes = settings.roastPropertiesBeansNotes,
            roastId = roastId,
            dateIso8601 = dateIso8601,
            coffeeId = settings.roastPropertiesStockId.takeIf { it.isNotBlank() },
            blendId = settings.roastPropertiesBlendId.takeIf { it.isNotBlank() }
        )
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ServerApi.uploadRoast(baseUrl, token, payload)
                }
                Platform.runLater {
                    Alert(Alert.AlertType.INFORMATION).apply {
                        title = "Сервер"
                        headerText = "Обжарка загружена"
                        contentText = "Профиль успешно отправлен на сервер."
                    }.show()
                }
            } catch (e: ServerApiException) {
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
            } catch (_: Exception) {
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
        val loadFile = MenuItem("Load from file...")
        val fromServer = MenuItem("From server...")
        val clearRef = MenuItem("Clear reference")
        loadFile.setOnAction { loadReferenceFromFile() }
        fromServer.setOnAction { loadReferenceFromServer() }
        clearRef.setOnAction { clearReference() }
        val menu = ContextMenu(loadFile, fromServer, clearRef)
        btnReference.setOnAction {
            val bounds = btnReference.localToScreen(btnReference.layoutBounds)
            menu.show(btnReference, bounds.minX, bounds.minY + bounds.height)
        }
    }

    private fun loadReferenceFromFile() {
        val chooser = FileChooser().apply {
            title = "Load reference profile"
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
                        headerText = "No reference profiles found"
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
                    title = "Select reference"
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
                    setReference(profile, "Server: $roastId")
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
        if (::lblReferenceLabel.isInitialized) lblReferenceLabel.text = "Placeholder"
        curveChart.setReferenceProfile(null)
    }

    private fun setReference(profile: RoastProfile, label: String) {
        referenceProfile = profile
        referenceLabel = label
        if (::lblReferenceLabel.isInitialized) lblReferenceLabel.text = label
        // If live was already rebased (Charge pressed without ref), 0 = Charge so align ref at 0; else ref at 0 until user presses C
        val alignMs = if (curveChart.getChargeOffsetMs() > 0) 0L else null
        curveChart.setReferenceProfile(profile, alignMs)
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
            initOwner(btnAuth.scene?.window)
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

    private fun restoreDividerPositions() {
        val s = SettingsManager.load()
        s.layoutDividerCenterRight?.let { p ->
            if (p in 0.01..0.99) centerSplit.setDividerPosition(0, p)
        }
        s.layoutDividerReferenceChannels?.let { p ->
            if (p in 0.01..0.99) rightSplit.setDividerPosition(0, p)
        }
    }

    private fun saveDividerPositions() {
        val positions = centerSplit.dividerPositions
        val rightPositions = rightSplit.dividerPositions
        val centerRight = if (positions.isNotEmpty()) positions[0] else null
        val refChannels = if (rightPositions.isNotEmpty()) rightPositions[0] else null
        val current = SettingsManager.load()
        SettingsManager.save(current.copy(
            layoutDividerCenterRight = centerRight,
            layoutDividerReferenceChannels = refChannels
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
        val settingsDrawerWidth = 440.0
        val productionDrawerWidth = 360.0
        val defaultDrawerWidth = 160.0
        val drawerWidths = listOf(settingsDrawerWidth, productionDrawerWidth, defaultDrawerWidth)
        val authLabel = Label("").apply {
            style = "-fx-font-size: 10; -fx-text-fill: #666666; -fx-wrap-text: true;"
            maxWidth = settingsDrawerWidth - 24
        }
        authStatusLabel = authLabel
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
            }
            val updated = SettingsManager.load()
            curveChart.applySettings(updated.chartColors, updated.chartConfig)
            updateReadoutUnits()
        }
        val systemPanel = VBox().apply {
            minWidth = settingsDrawerWidth
            prefWidth = settingsDrawerWidth
            maxWidth = settingsDrawerWidth
            padding = Insets(8.0)
            children.add(VBox(4.0).apply {
                children.add(Label("Вход").apply { style = "-fx-font-weight: bold; -fx-font-size: 11;" })
                children.add(authLabel)
            })
            val scroll = ScrollPane(settingsRoot).apply {
                isFitToWidth = true
                isPannable = false
                VBox.setMargin(this, Insets(8.0, 0.0, 0.0, 0.0))
            }
            children.add(scroll)
            VBox.setVgrow(scroll, Priority.ALWAYS)
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
        val productionPanel = VBox().apply {
            minWidth = productionDrawerWidth
            prefWidth = productionDrawerWidth
            maxWidth = productionDrawerWidth
            val scroll = ScrollPane(roastPropsRoot).apply {
                isFitToWidth = true
                isPannable = false
            }
            children.add(scroll)
            VBox.setVgrow(scroll, Priority.ALWAYS)
        }
        val supportPanel = VBox(10.0).apply {
            padding = Insets(12.0)
            minWidth = defaultDrawerWidth
            prefWidth = defaultDrawerWidth
            maxWidth = defaultDrawerWidth
            children.add(Label("Support").apply { style = "-fx-font-weight: bold; -fx-font-size: 12;" })
            children.add(Button("⌨ Горячие клавиши (F1)").apply {
                style = "-fx-font-size: 11; -fx-cursor: hand; -fx-wrap-text: true;"
                setOnAction { btnShortcuts.fire() }
            })
        }
        val panels = listOf(systemPanel, productionPanel, supportPanel)
        listOf(systemPanel, supportPanel).forEach { vbox ->
            vbox.children.filterIsInstance<Button>().forEach { it.maxWidth = Double.MAX_VALUE }
        }
        val toggles = listOf(btnSidebarSystem, btnSidebarProduction, btnSidebarSupport)
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
                    if (idx == 1) {
                        roastPropertiesController?.loadFromSettings()
                        roastPropertiesController?.loadReferencesAndSelect()
                        roastPropertiesController?.loadStockAndBlends()
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
