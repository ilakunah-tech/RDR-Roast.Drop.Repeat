package com.rdr.roast.ui

import com.rdr.roast.app.ProfileStorage
import com.rdr.roast.app.RecorderState
import com.rdr.roast.app.RoastRecorder
import com.rdr.roast.app.SettingsManager
import com.rdr.roast.domain.EventType
import com.rdr.roast.domain.TemperatureSample
import com.rdr.roast.domain.metrics.computePhases
import com.rdr.roast.domain.metrics.computeRoR
import com.rdr.roast.driver.simulator.SimulatorSource
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.StackPane
import javafx.stage.Modality
import javafx.stage.Stage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.launch
import java.nio.file.Paths

class MainController {

    @FXML
    lateinit var chartContainer: StackPane

    @FXML
    lateinit var lblBT: Label

    @FXML
    lateinit var lblET: Label

    @FXML
    lateinit var lblRoRBT: Label

    @FXML
    lateinit var lblRoRET: Label

    @FXML
    lateinit var lblDuration: Label

    @FXML
    lateinit var btnStart: Button

    @FXML
    lateinit var btnStop: Button

    @FXML
    lateinit var btnAbort: Button

    @FXML
    lateinit var btnCharge: Button

    @FXML
    lateinit var btnTP: Button

    @FXML
    lateinit var btnCC: Button

    @FXML
    lateinit var btnDrop: Button

    @FXML
    lateinit var btnSettings: Button

    private val recorder = RoastRecorder(SimulatorSource())
    private val chart = RoastChartView()
    private val scope = CoroutineScope(Dispatchers.JavaFx + SupervisorJob())

    private var shouldAutoStartAfterConnect = false

    @FXML
    fun initialize() {
        // Canvas must not participate in layout -- otherwise it pushes other panels away
        chart.isManaged = false
        chartContainer.children.setAll(chart)

        // Resize canvas to match container's actual layout bounds (after insets)
        val resizeChart = {
            val insets = chartContainer.insets
            val w = chartContainer.width - insets.left - insets.right
            val h = chartContainer.height - insets.top - insets.bottom
            if (w > 10 && h > 10) {
                chart.width = w
                chart.height = h
                chart.layoutX = insets.left
                chart.layoutY = insets.top
            }
        }
        chartContainer.widthProperty().addListener { _, _, _ -> resizeChart() }
        chartContainer.heightProperty().addListener { _, _, _ -> resizeChart() }
        Platform.runLater { resizeChart() }

        // Collect state + profile for button updates
        scope.launch {
            combine(recorder.stateFlow, recorder.currentProfile) { state, profile ->
                Pair(state, profile)
            }.collect { (state, profile) ->
                Platform.runLater { updateButtonStates(state, profile) }
            }
        }

        // Collect and update labels + chart on each sample
        scope.launch {
            recorder.currentSample.collect { sample ->
                Platform.runLater { onSample(sample) }
            }
        }

        // Collect elapsed time for duration label
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

    private fun updateButtonStates(state: RecorderState, profile: com.rdr.roast.domain.RoastProfile) {
        when (state) {
            RecorderState.DISCONNECTED -> {
                btnStart.text = "Connect"
                btnStart.isVisible = true
                btnStart.isDisable = false
                btnStop.isDisable = true
                btnAbort.isDisable = true
                btnCharge.isDisable = true
                btnTP.isDisable = true
                btnCC.isDisable = true
                btnDrop.isDisable = true
            }
            RecorderState.MONITORING -> {
                if (shouldAutoStartAfterConnect) {
                    shouldAutoStartAfterConnect = false
                    recorder.startRecording()
                }
                btnStart.text = "Start"
                btnStart.isVisible = true
                btnStart.isDisable = false
                btnStop.isDisable = true
                btnAbort.isDisable = true
                btnCharge.isDisable = true
                btnTP.isDisable = true
                btnCC.isDisable = true
                btnDrop.isDisable = true
            }
            RecorderState.RECORDING -> {
                btnStart.isDisable = true
                btnStop.isDisable = false
                btnAbort.isDisable = false
                btnCharge.isDisable = profile.eventByType(EventType.CHARGE) != null
                btnTP.isDisable = profile.eventByType(EventType.TP) != null
                btnCC.isDisable = profile.eventByType(EventType.CC) != null
                btnDrop.isDisable = profile.eventByType(EventType.DROP) != null
            }
            RecorderState.STOPPED -> {
                btnStart.text = "New Roast"
                btnStart.isVisible = true
                btnStart.isDisable = false
                btnStop.isDisable = true
                btnAbort.isDisable = true
                btnCharge.isDisable = true
                btnTP.isDisable = true
                btnCC.isDisable = true
                btnDrop.isDisable = true
            }
        }
    }

    private fun onSample(sample: TemperatureSample?) {
        if (sample == null) return
        val profile = recorder.currentProfile.value
        val idx = profile.timex.size - 1
        val rorBt = if (idx >= 0) computeRoR(profile.temp1, profile.timex, idx) else 0.0
        val rorEt = if (idx >= 0) computeRoR(profile.temp2, profile.timex, idx) else 0.0

        chart.addSample(sample.timeSec, sample.bt, sample.et, rorBt, rorEt)

        lblBT.text = "%.1f °C".format(sample.bt)
        lblET.text = "%.1f °C".format(sample.et)
        lblRoRBT.text = "%.1f °C".format(rorBt)
        lblRoRET.text = "%.1f °C".format(rorEt)
    }

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
                    chart.clear()
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
            } catch (e: Exception) {
                // TODO: show error to user
            }
        }

        btnAbort.setOnAction {
            recorder.abort()
            chart.clear()
        }

        btnCharge.setOnAction { markEventAndUpdateChart(EventType.CHARGE) }
        btnTP.setOnAction { markEventAndUpdateChart(EventType.TP) }
        btnCC.setOnAction { markEventAndUpdateChart(EventType.CC) }
        btnDrop.setOnAction { markEventAndUpdateChart(EventType.DROP) }
    }

    private fun markEventAndUpdateChart(type: EventType) {
        val sample = recorder.currentSample.value ?: return
        recorder.markEvent(type)

        val tempStr = "%.1f °C".format(sample.bt)
        val label = when (type) {
            EventType.CHARGE -> "Charge"
            EventType.TP -> "TP"
            EventType.CC -> "CC"
            EventType.DROP -> "Drop"
        }
        chart.addEventMarker(sample.timeSec, label, tempStr)

        val profile = recorder.currentProfile.value
        val phases = computePhases(profile)
        chart.updatePhases(phases)
    }
}
