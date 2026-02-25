package com.rdr.roast.ui

import com.rdr.roast.app.ProfileStorage
import com.rdr.roast.app.RecorderState
import com.rdr.roast.app.RoastRecorder
import com.rdr.roast.app.SettingsManager
import com.rdr.roast.domain.EventType
import com.rdr.roast.domain.TemperatureSample
import com.rdr.roast.domain.curves.MovingAverageCurveModel
import com.rdr.roast.domain.metrics.findTurningPointIndex
import com.rdr.roast.domain.curves.RorCurveModel
import com.rdr.roast.domain.curves.StandardCurveModel
import com.rdr.roast.driver.simulator.SimulatorSource
import com.rdr.roast.ui.chart.ChartPanelFx
import com.rdr.roast.ui.chart.CurveChartFx
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.StackPane
import javafx.stage.Modality
import javafx.stage.Stage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.launch
import java.nio.file.Paths

class MainController {

    @FXML lateinit var chartContainer: StackPane
    @FXML lateinit var lblBT: Label
    @FXML lateinit var lblET: Label
    @FXML lateinit var lblRoRBT: Label
    @FXML lateinit var lblRoRET: Label
    @FXML lateinit var lblDuration: Label
    @FXML lateinit var btnStart: Button
    @FXML lateinit var btnStop: Button
    @FXML lateinit var btnAbort: Button
    @FXML lateinit var btnShortcuts: Button
    @FXML lateinit var btnSettings: Button

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
    private val recorder = RoastRecorder(SimulatorSource())
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
        btnShortcuts.setOnAction { showShortcutsHelp() }
        setupSpaceHotkey()
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

        // Charge on first sample (Cropster: Start = start recording, charge at first data point)
        if (profile.timex.size == 1 && profile.eventByType(EventType.CHARGE) == null) {
            recorder.markEvent(EventType.CHARGE)
            curveChart.addEventMarker(timeMs, "Charge @ %.1f °C".format(sample.bt),
                com.rdr.roast.ui.chart.CurveChartFx.COLOR_MARKER)
        }

        // TP once: place marker only after the first 3 min have passed (min BT in 0..3 min is then final)
        if (!tpMarkerPlaced && profile.timex.isNotEmpty() && profile.timex.last() >= TP_WINDOW_SEC) {
            val tpIdx = findTurningPointIndex(profile, searchWindowSec = TP_WINDOW_SEC)
            if (tpIdx != null && profile.temp1.size > tpIdx) {
                tpMarkerPlaced = true
                val tpSec = profile.timex[tpIdx]
                val tpBt = profile.temp1[tpIdx]
                curveChart.addEventMarker((tpSec * 1000).toLong(), "TP @ %s · %.1f °C".format(formatSec(tpSec), tpBt),
                    com.rdr.roast.ui.chart.CurveChartFx.COLOR_MARKER)
            }
        }
    }

    private var tpMarkerPlaced = false
    private companion object { const val TP_WINDOW_SEC = 180.0 }

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
            val savePath = SettingsManager.load().savePath
            val path = Paths.get(savePath).resolve(ProfileStorage.generateFileName())
            try {
                java.nio.file.Files.createDirectories(path.parent)
                ProfileStorage.saveProfile(profile, path)
            } catch (_: Exception) {
                // TODO: show error dialog
            }
        }

        btnAbort.setOnAction {
            recorder.abort()
            resetCurvePipeline()
            curveChart.clearAll()
            chartPanel.lastDataTimeMs = null
            tpMarkerPlaced = false
        }
    }

    /** Hotkeys: Cropster-style Ctrl+Enter (Start), Space (Start/Drop), Ctrl+Escape (Abort), F1 (shortcuts help). */
    private fun setupSpaceHotkey() {
        chartContainer.sceneProperty().addListener { _, _, scene ->
            scene?.addEventHandler(KeyEvent.KEY_PRESSED) { e ->
                when {
                    e.code == KeyCode.ENTER && e.isShortcutDown -> {
                        e.consume()
                        triggerStart()
                    }
                    e.code == KeyCode.SPACE -> {
                        e.consume()
                        if (recorder.stateFlow.value == RecorderState.RECORDING) triggerDrop()
                        else triggerStart()
                    }
                    e.code == KeyCode.ESCAPE && e.isShortcutDown -> {
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

    private fun triggerDrop() {
        val sample = recorder.currentSample.value ?: return
        recorder.markEvent(EventType.DROP)
        curveChart.addEventMarker((sample.timeSec * 1000).toLong(), "Drop @ %.1f °C".format(sample.bt),
            com.rdr.roast.ui.chart.CurveChartFx.COLOR_MARKER)
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
