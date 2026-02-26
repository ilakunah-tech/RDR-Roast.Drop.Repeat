package com.rdr.roast.ui

import com.rdr.roast.app.AppSettings
import com.rdr.roast.app.ChartConfig
import com.rdr.roast.app.ChartColors
import com.rdr.roast.app.ConnectionPreset
import com.rdr.roast.app.MachineConfig
import com.rdr.roast.app.MachineType
import com.rdr.roast.app.SettingsManager
import com.rdr.roast.app.Transport
import javafx.fxml.FXML
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.control.RadioButton
import javafx.scene.control.TextField
import javafx.scene.control.TextInputDialog
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.VBox
import javafx.stage.DirectoryChooser
import javafx.stage.Stage

class SettingsController {

    /** Set when user clicks Save; MainController uses this to apply and reconnect. */
    var savedSettings: AppSettings? = null
        private set

    @FXML
    lateinit var cmbSource: ComboBox<String>

    @FXML
    lateinit var txtPort: TextField

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
    lateinit var txtServerBaseUrl: TextField

    @FXML
    lateinit var txtServerToken: TextField

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
    lateinit var lblPhidgetEt: javafx.scene.control.Label

    @FXML
    lateinit var lblPhidgetBt: javafx.scene.control.Label

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
    lateinit var txtChartTempMin: TextField

    @FXML
    lateinit var txtChartTempMax: TextField

    @FXML
    lateinit var txtChartRorMin: TextField

    @FXML
    lateinit var txtChartRorMax: TextField

    @FXML
    lateinit var txtChartTimeRange: TextField

    @FXML
    lateinit var chkChartShowGrid: CheckBox

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
    fun initialize() {
        val settings = SettingsManager.load()

        cmbSource.items.setAll("Simulator", "Besca", "Diedrich")
        cmbSource.value = when (settings.machineConfig.machineType) {
            MachineType.SIMULATOR -> "Simulator"
            MachineType.BESCA -> "Besca"
            MachineType.DIEDRICH -> "Diedrich"
        }

        refreshPresetList()
        cmbTransport.valueProperty().addListener { _, _, _ -> updateTransportFieldsVisibility() }

        txtPort.text = settings.machineConfig.port
        txtBaudRate.text = settings.machineConfig.baudRate.toString()
        txtSlaveId.text = settings.machineConfig.slaveId.toString()
        txtHost.text = settings.machineConfig.host?.takeIf { it.isNotBlank() } ?: ""
        txtTcpPort.text = settings.machineConfig.tcpPort.toString()
        txtPhidgetEtChannel.text = settings.machineConfig.phidgetEtChannel.toString()
        txtPhidgetBtChannel.text = settings.machineConfig.phidgetBtChannel.toString()
        txtSavePath.text = settings.savePath
        txtSamplingIntervalSec.text = (settings.machineConfig.pollingIntervalMs / 1000.0).toString()
        txtServerBaseUrl.text = settings.serverBaseUrl
        txtServerToken.text = settings.serverToken

        when (settings.unit.uppercase()) {
            "F" -> rbFahrenheit.isSelected = true
            else -> rbCelsius.isSelected = true
        }

        val colors = settings.chartColors
        txtColorLiveBt.text = colors.liveBt
        txtColorLiveEt.text = colors.liveEt
        txtColorRefBt.text = colors.refBt
        txtColorRefEt.text = colors.refEt
        txtColorRefAlpha.text = colors.refAlpha.toString()
        val config = settings.chartConfig
        txtChartTempMin.text = config.tempMin.toString()
        txtChartTempMax.text = config.tempMax.toString()
        txtChartRorMin.text = config.rorMin.toString()
        txtChartRorMax.text = config.rorMax.toString()
        txtChartTimeRange.text = config.timeRangeMin.toString()
        chkChartShowGrid.isSelected = config.showGrid
        txtChartBtLineWidth.text = config.btLineWidth.toString()
        txtChartEtLineWidth.text = config.etLineWidth.toString()
        txtChartRorLineWidth.text = config.rorLineWidth.toString()
        txtChartRefLineWidth.text = config.refLineWidth.toString()
        txtChartBackground.text = config.backgroundColor
        txtChartGridColor.text = config.gridColor

        btnResetColors.setOnAction {
            val d = ChartColors()
            txtColorLiveBt.text = d.liveBt
            txtColorLiveEt.text = d.liveEt
            txtColorRefBt.text = d.refBt
            txtColorRefEt.text = d.refEt
            txtColorRefAlpha.text = d.refAlpha.toString()
        }

        updatePortFieldsVisibility()
        updateTransportCombo()
        cmbTransport.value = when {
            settings.machineConfig.machineType == MachineType.BESCA && settings.machineConfig.transport == Transport.TCP -> "TCP (Ethernet)"
            settings.machineConfig.machineType == MachineType.DIEDRICH && settings.machineConfig.transport == Transport.PHIDGET -> "Phidget 1048 (USB)"
            else -> cmbTransport.items.firstOrNull()
        }
        updateTransportFieldsVisibility()

        cmbSource.valueProperty().addListener { _, _, _ ->
            updatePortFieldsVisibility()
            updateTransportCombo()
            updateTransportFieldsVisibility()
        }

        btnLoadPreset.setOnAction { loadSelectedPreset() }
        btnSavePreset.setOnAction { saveCurrentAsPreset() }

        btnBrowse.setOnAction {
            val chooser = DirectoryChooser()
            chooser.title = "Select Save Directory"
            val dir = chooser.showDialog((btnBrowse.scene?.window) as? Stage)
            dir?.let { txtSavePath.text = it.absolutePath }
        }

        btnSave.setOnAction {
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
            val host = txtHost.text?.trim()?.takeIf { it.isNotBlank() }
            val tcpPort = txtTcpPort.text.toIntOrNull() ?: 502
            val port = txtPort.text.ifBlank { "COM4" }
            val baudRate = txtBaudRate.text.toIntOrNull() ?: 9600
            val slaveId = txtSlaveId.text.toIntOrNull() ?: 1
            val phidgetEt = txtPhidgetEtChannel.text.toIntOrNull()?.coerceIn(1, 4) ?: 1
            val phidgetBt = txtPhidgetBtChannel.text.toIntOrNull()?.coerceIn(1, 4) ?: 2
            val unit = if (rbFahrenheit.isSelected) "F" else "C"
            val savePath = txtSavePath.text.ifBlank { System.getProperty("user.home") + "/roasts" }
            val serverBaseUrl = txtServerBaseUrl.text?.trim() ?: ""
            val serverToken = txtServerToken.text?.trim() ?: ""

            val chartColors = ChartColors(
                liveBt = txtColorLiveBt.text?.trim()?.takeIf { it.isNotBlank() } ?: ChartColors().liveBt,
                liveEt = txtColorLiveEt.text?.trim()?.takeIf { it.isNotBlank() } ?: ChartColors().liveEt,
                liveRorBt = ChartColors().liveRorBt,
                liveRorEt = ChartColors().liveRorEt,
                refBt = txtColorRefBt.text?.trim()?.takeIf { it.isNotBlank() } ?: ChartColors().refBt,
                refEt = txtColorRefEt.text?.trim()?.takeIf { it.isNotBlank() } ?: ChartColors().refEt,
                refRorBt = ChartColors().refRorBt,
                refRorEt = ChartColors().refRorEt,
                refAlpha = txtColorRefAlpha.text.toIntOrNull()?.coerceIn(0, 255) ?: ChartColors().refAlpha
            )
            val chartConfig = ChartConfig(
                tempMin = txtChartTempMin.text.toDoubleOrNull() ?: ChartConfig().tempMin,
                tempMax = txtChartTempMax.text.toDoubleOrNull() ?: ChartConfig().tempMax,
                rorMin = txtChartRorMin.text.toDoubleOrNull() ?: ChartConfig().rorMin,
                rorMax = txtChartRorMax.text.toDoubleOrNull() ?: ChartConfig().rorMax,
                timeRangeMin = txtChartTimeRange.text.toIntOrNull()?.coerceIn(1, 60) ?: ChartConfig().timeRangeMin,
                showGrid = chkChartShowGrid.isSelected,
                btLineWidth = txtChartBtLineWidth.text.toFloatOrNull()?.coerceIn(0.5f, 5f) ?: ChartConfig().btLineWidth,
                etLineWidth = txtChartEtLineWidth.text.toFloatOrNull()?.coerceIn(0.5f, 5f) ?: ChartConfig().etLineWidth,
                rorLineWidth = txtChartRorLineWidth.text.toFloatOrNull()?.coerceIn(0.5f, 5f) ?: ChartConfig().rorLineWidth,
                refLineWidth = txtChartRefLineWidth.text.toFloatOrNull()?.coerceIn(0.5f, 5f) ?: ChartConfig().refLineWidth,
                backgroundColor = txtChartBackground.text?.trim()?.takeIf { it.isNotBlank() } ?: ChartConfig().backgroundColor,
                gridColor = txtChartGridColor.text?.trim()?.takeIf { it.isNotBlank() } ?: ChartConfig().gridColor
            )

            val pollingIntervalMs = (txtSamplingIntervalSec.text.toDoubleOrNull()?.times(1000)?.toLong()?.coerceIn(250L, 120_000L)) ?: 1000L
            val mc = settings.machineConfig.copy(
                machineType = machineType,
                transport = transport,
                host = host,
                tcpPort = tcpPort,
                port = port,
                baudRate = baudRate,
                slaveId = slaveId,
                phidgetEtChannel = phidgetEt,
                phidgetBtChannel = phidgetBt,
                pollingIntervalMs = pollingIntervalMs
            )
            val newSettings = AppSettings(
                machineConfig = mc,
                unit = unit,
                savePath = savePath,
                serverBaseUrl = serverBaseUrl,
                serverToken = serverToken,
                chartColors = chartColors,
                chartConfig = chartConfig
            )
            savedSettings = newSettings
            SettingsManager.save(newSettings)
            closeWindow()
        }

        btnCancel.setOnAction { closeWindow() }
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
        txtPort.isVisible = !isDiedrichPhidget
        txtPort.isManaged = !isDiedrichPhidget
        lblBaudRate.isVisible = !isDiedrichPhidget
        lblBaudRate.isManaged = !isDiedrichPhidget
        lblSlaveId.isVisible = !isDiedrichPhidget
        lblSlaveId.isManaged = !isDiedrichPhidget
        txtBaudRate.isVisible = !isDiedrichPhidget
        txtBaudRate.isManaged = !isDiedrichPhidget
        txtSlaveId.isVisible = !isDiedrichPhidget
        txtSlaveId.isManaged = !isDiedrichPhidget
        lblPhidgetEt.isVisible = isDiedrichPhidget
        lblPhidgetEt.isManaged = isDiedrichPhidget
        txtPhidgetEtChannel.isVisible = isDiedrichPhidget
        txtPhidgetEtChannel.isManaged = isDiedrichPhidget
        lblPhidgetBt.isVisible = isDiedrichPhidget
        lblPhidgetBt.isManaged = isDiedrichPhidget
        txtPhidgetBtChannel.isVisible = isDiedrichPhidget
        txtPhidgetBtChannel.isManaged = isDiedrichPhidget
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
        txtPort.text = c.port
        txtBaudRate.text = c.baudRate.toString()
        txtSlaveId.text = c.slaveId.toString()
        txtPhidgetEtChannel.text = c.phidgetEtChannel.toString()
        txtPhidgetBtChannel.text = c.phidgetBtChannel.toString()
        txtSamplingIntervalSec.text = (c.pollingIntervalMs / 1000.0).toString()
        updateTransportFieldsVisibility()
    }

    private fun saveCurrentAsPreset() {
        val dialog = TextInputDialog("")
        dialog.title = "Save preset"
        dialog.headerText = "Enter a name for this connection preset"
        dialog.contentText = "Name:"
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
        val pollingIntervalMs = (txtSamplingIntervalSec.text.toDoubleOrNull()?.times(1000)?.toLong()?.coerceIn(250L, 120_000L)) ?: 1000L
        val config = settings.machineConfig.copy(
            machineType = machineType,
            transport = transport,
            host = txtHost.text?.trim()?.takeIf { it.isNotBlank() },
            tcpPort = txtTcpPort.text.toIntOrNull() ?: 502,
            port = txtPort.text.ifBlank { "COM4" },
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
            this.title = "Preset saved"
            headerText = "Preset \"$name\" saved."
        }.showAndWait()
    }

    private fun closeWindow() {
        (btnSave.scene?.window as? Stage)?.close()
    }
}
