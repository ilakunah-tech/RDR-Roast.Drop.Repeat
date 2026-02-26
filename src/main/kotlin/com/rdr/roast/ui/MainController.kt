package com.rdr.roast.ui

import com.rdr.roast.app.ProfileStorage
import com.rdr.roast.app.RecorderState
import com.rdr.roast.app.ReferenceApi
import com.rdr.roast.app.RoastRecorder
import com.rdr.roast.app.SettingsManager
import com.rdr.roast.domain.EventType
import com.rdr.roast.domain.RoastProfile
import com.rdr.roast.domain.TemperatureSample
import com.rdr.roast.domain.curves.MovingAverageCurveModel
import com.rdr.roast.domain.metrics.findChargeDropIndex
import com.rdr.roast.domain.metrics.findTurningPointIndex
import com.rdr.roast.domain.curves.RorCurveModel
import com.rdr.roast.domain.curves.StandardCurveModel
import com.rdr.roast.driver.simulator.AlogReplaySource
import com.rdr.roast.driver.simulator.SimulatorSource
import com.rdr.roast.ui.chart.ChartPanelFx
import com.rdr.roast.ui.chart.CurveChartFx
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.ContextMenu
import javafx.scene.control.MenuItem
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.StackPane
import javafx.stage.FileChooser
import javafx.stage.Modality
import javafx.stage.Stage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Paths

class MainController {

    @FXML lateinit var centerPane: BorderPane
    @FXML lateinit var chartContainer: StackPane
    @FXML lateinit var lblBT: Label
    @FXML lateinit var lblET: Label
    @FXML lateinit var lblRoRBT: Label
    @FXML lateinit var lblRoRET: Label
    @FXML lateinit var lblDuration: Label
    @FXML lateinit var btnStart: Button
    @FXML lateinit var btnStop: Button
    @FXML lateinit var btnAbort: Button
    @FXML lateinit var btnReference: Button
    @FXML lateinit var btnShortcuts: Button
    @FXML lateinit var btnSettings: Button

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

    // ── App ──────────────────────────────────────────────────────────────────
    private val recorder = RoastRecorder(createDemoDataSource())

    /** Demo/simulator: replay real .alog if present, else synthetic S-curve. */
    private fun createDemoDataSource() =
        if (Files.exists(DEMO_PROFILE_PATH)) AlogReplaySource(DEMO_PROFILE_PATH)
        else SimulatorSource()
    private val scope = CoroutineScope(Dispatchers.JavaFx + SupervisorJob())
    private var shouldAutoStartAfterConnect = false

    @FXML
    fun initialize() {
        // Bind CurveModels to chart series once
        curveChart.bindDefaultCurves(btSmooth, etSmooth, rorBt, rorEt)

        // Add ChartPanelFx to the container and stretch it to fill
        chartPanel.prefWidthProperty().bind(chartContainer.widthProperty())
        chartPanel.prefHeightProperty().bind(chartContainer.heightProperty())
        chartContainer.children.setAll(chartPanel)

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

        // Elapsed-time label
        scope.launch {
            recorder.elapsedSec.collect { sec ->
                Platform.runLater {
                    val m = (sec / 60).toInt()
                    val s = (sec % 60).toInt()
                    lblDuration.text = "%02d:%02d".format(m, s)
                }
            }
        }

        setupButtonHandlers()
        setupSettingsButton()
        setupReferenceButton()
        btnShortcuts.setOnAction { showShortcutsHelp() }
        setupSpaceHotkey()

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

    private fun onSample(sample: TemperatureSample?) {
        if (sample == null) return
        val timeMs = (sample.timeSec * 1000).toLong()
        val profile = recorder.currentProfile.value

        btRaw.put(timeMs, sample.bt)
        etRaw.put(timeMs, sample.et)

        lblBT.text = "%.1f °C".format(sample.bt)
        lblET.text = "%.1f °C".format(sample.et)
        lblRoRBT.text = "%.1f °C/min".format(rorBt.getValue(timeMs) ?: 0.0)
        lblRoRET.text = "%.1f °C/min".format(rorEt.getValue(timeMs) ?: 0.0)
        chartPanel.lastDataTimeMs = timeMs

        // Auto-detect Charge once: BT drop ≥ 6°C over 30s (Cropster-style); manual C overrides
        if (profile.eventByType(EventType.CHARGE) == null) {
            val chargeIdx = findChargeDropIndex(
                profile,
                windowSec = AUTO_CHARGE_WINDOW_SEC,
                minDropC = AUTO_CHARGE_MIN_DROP_C,
                minElapsedSec = AUTO_CHARGE_MIN_ELAPSED_SEC
            )
            if (chargeIdx != null && chargeIdx < profile.timex.size) {
                val chargeTimeSec = profile.timex[chargeIdx]
                recorder.markEventAt(chargeTimeSec, EventType.CHARGE)
                val chargeBt = profile.temp1[chargeIdx]
                curveChart.addEventMarker((chargeTimeSec * 1000).toLong(), "Charge @ %.1f °C".format(chargeBt),
                    com.rdr.roast.ui.chart.CurveChartFx.COLOR_MARKER)
            }
        }

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
        val endMs = (profile.timex.maxOrNull()!! * 1000).toLong()
        val deMs = (profile.eventByType(EventType.DE) ?: profile.eventByType(EventType.CC))?.timeSec?.times(1000)?.toLong()
        val fcMs = profile.eventByType(EventType.FC)?.timeSec?.times(1000)?.toLong()
        curveChart.updatePhaseStrip(endMs, deMs, fcMs)
    }

    private var tpMarkerPlaced = false
    private companion object {
        const val TP_WINDOW_SEC = 180.0
        const val TP_WINDOW_AFTER_CHARGE_SEC = 120.0
        const val AUTO_CHARGE_WINDOW_SEC = 30.0
        const val AUTO_CHARGE_MIN_DROP_C = 6.0
        const val AUTO_CHARGE_MIN_ELAPSED_SEC = 18.0
        /** Real profile for demo mode; if file exists, replay it instead of synthetic simulator. */
        val DEMO_PROFILE_PATH = Paths.get(
            "C:\\Users\\ilaku\\Documents\\Projects\\BESCA\\16 Ethiopia, Ethiopia Yirgacheffe 213 12.5%.alog"
        )
    }

    private fun updateButtonStates(state: RecorderState) {
        when (state) {
            RecorderState.DISCONNECTED -> {
                btnStart.text = "Connect"
                btnStart.isDisable = false
                btnStop.isDisable = true
                btnAbort.isDisable = true
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
            }
            RecorderState.RECORDING -> {
                btnStart.isDisable = true
                btnStop.isDisable = false
                btnAbort.isDisable = false
            }
            RecorderState.STOPPED -> {
                btnStart.text = "New Roast"
                btnStart.isDisable = false
                btnStop.isDisable = true
                btnAbort.isDisable = true
            }
        }
    }

    // ── Button handlers ───────────────────────────────────────────────────────

    private fun setupButtonHandlers() {
        btnStart.setOnAction {
            when (recorder.stateFlow.value) {
                RecorderState.DISCONNECTED -> {
                    shouldAutoStartAfterConnect = true
                    recorder.connect()
                }
                RecorderState.MONITORING -> recorder.startRecording()
                RecorderState.STOPPED -> {
                    recorder.reset()
                    resetCurvePipeline()
                    curveChart.clearAll()
                    chartPanel.lastDataTimeMs = null
                    tpMarkerPlaced = false
                }
                RecorderState.RECORDING -> { /* disabled */ }
            }
        }

        btnStop.setOnAction {
            val profile = recorder.stop()
            // #region agent log
            val profRef = System.identityHashCode(profile)
            java.io.File("/opt/cursor/logs/debug.log").appendText("""{"id":"log_btnStop","timestamp":${System.currentTimeMillis()},"location":"MainController.kt:btnStop","message":"profile from stop()","data":{"profRef":$profRef,"timexSize":${profile.timex.size},"temp1Size":${profile.temp1.size},"temp2Size":${profile.temp2.size}},"hypothesisId":"E"}""" + "\n")
            // #endregion
            showFinishRoastDialog(profile)
        }

        btnAbort.setOnAction {
            recorder.abort()
            resetCurvePipeline()
            curveChart.clearAll()
            chartPanel.lastDataTimeMs = null
            tpMarkerPlaced = false
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
                            curveChart.clearAll()
                            chartPanel.lastDataTimeMs = null
                            tpMarkerPlaced = false
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
                recorder.connect()
            }
            RecorderState.MONITORING -> recorder.startRecording()
            RecorderState.STOPPED -> {
                recorder.reset()
                resetCurvePipeline()
                curveChart.clearAll()
                chartPanel.lastDataTimeMs = null
                tpMarkerPlaced = false
            }
            RecorderState.RECORDING -> { }
        }
    }

    private fun triggerCharge() {
        val profile = recorder.currentProfile.value
        if (profile.eventByType(EventType.CHARGE) != null) return
        val sample = recorder.currentSample.value ?: return
        recorder.markEvent(EventType.CHARGE)
        val timeMs = (sample.timeSec * 1000).toLong()
        curveChart.addEventMarker(timeMs, "Charge @ %.1f °C".format(sample.bt),
            com.rdr.roast.ui.chart.CurveChartFx.COLOR_MARKER)
    }

    private fun triggerDrop() {
        val sample = recorder.currentSample.value ?: return
        recorder.markEvent(EventType.DROP)
        curveChart.addEventMarker((sample.timeSec * 1000).toLong(), "Drop @ %.1f °C".format(sample.bt),
            com.rdr.roast.ui.chart.CurveChartFx.COLOR_MARKER)
        val profile = recorder.stop()
        // #region agent log
        val profRef = System.identityHashCode(profile)
        java.io.File("/opt/cursor/logs/debug.log").appendText("""{"id":"log_triggerDrop","timestamp":${System.currentTimeMillis()},"location":"MainController.kt:triggerDrop","message":"profile from stop()","data":{"profRef":$profRef,"timexSize":${profile.timex.size},"temp1Size":${profile.temp1.size},"temp2Size":${profile.temp2.size},"eventsSize":${profile.events.size}},"hypothesisId":"E"}""" + "\n")
        // #endregion
        showFinishRoastDialog(profile)
    }

    /** Cropster-style: after Stop/Drop show finish panel on the left of the chart (no modal). */
    private fun showFinishRoastDialog(profile: RoastProfile) {
        val loader = FXMLLoader(javaClass.getResource("/com/rdr/roast/ui/FinishRoastView.fxml"))
        val root = loader.load<Parent>()
        val controller = loader.getController<FinishRoastController>()
        controller.setProfile(profile)
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
            curveChart.clearAll()
            chartPanel.lastDataTimeMs = null
            tpMarkerPlaced = false
        }
        controller.onDontSave = { hideFinishPanel() }
        controller.setStage(null) // inline panel, no dialog
        centerPane.left = root
    }

    private fun hideFinishPanel() {
        centerPane.left = null
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
        val baseUrl = settings.serverBaseUrl.trim().removeSuffix("/")
        if (baseUrl.isEmpty()) {
            Alert(Alert.AlertType.WARNING).apply {
                title = "Server not configured"
                headerText = "Set server URL in Settings"
                contentText = "Open Settings and set \"Server (reference)\" to your API base URL (e.g. https://artqqplus.ru/api/v1)."
            }.showAndWait()
            return
        }
        scope.launch {
            val items = withContext(Dispatchers.IO) {
                try {
                    ReferenceApi.listReferences(
                        baseUrl = baseUrl,
                        token = settings.serverToken.takeIf { it.isNotBlank() }
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
                            fetchAndSetReference(baseUrl, settings.serverToken.takeIf { it.isNotBlank() }, items[idx].id)
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
        curveChart.setReferenceProfile(null)
    }

    private fun setReference(profile: RoastProfile, label: String) {
        referenceProfile = profile
        referenceLabel = label
        curveChart.setReferenceProfile(profile)
    }

    private fun setupSettingsButton() {
        btnSettings.setOnAction {
            val loader = FXMLLoader(javaClass.getResource("/com/rdr/roast/ui/SettingsView.fxml"))
            val root = loader.load<Parent>()
            val stage = Stage().apply {
                title = "Settings"
                scene = Scene(root, 480.0, 420.0)
                initModality(Modality.APPLICATION_MODAL)
                initOwner(btnSettings.scene?.window)
            }
            stage.showAndWait()
        }
    }
}
