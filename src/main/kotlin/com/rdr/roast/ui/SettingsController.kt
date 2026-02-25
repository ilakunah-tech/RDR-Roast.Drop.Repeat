package com.rdr.roast.ui

import com.rdr.roast.app.AppSettings
import com.rdr.roast.app.MachineConfig
import com.rdr.roast.app.MachineType
import com.rdr.roast.app.SettingsManager
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.RadioButton
import javafx.scene.control.TextField
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.VBox
import javafx.stage.DirectoryChooser
import javafx.stage.Stage

class SettingsController {

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
    lateinit var btnBrowse: Button

    @FXML
    lateinit var btnSave: Button

    @FXML
    lateinit var btnCancel: Button

    @FXML
    lateinit var portFieldsContainer: VBox

    @FXML
    fun initialize() {
        val settings = SettingsManager.load()

        cmbSource.items.setAll("Simulator", "Besca", "Diedrich")
        cmbSource.value = when (settings.machineConfig.machineType) {
            MachineType.SIMULATOR -> "Simulator"
            MachineType.BESCA -> "Besca"
            MachineType.DIEDRICH -> "Diedrich"
        }

        txtPort.text = settings.machineConfig.port
        txtBaudRate.text = settings.machineConfig.baudRate.toString()
        txtSlaveId.text = settings.machineConfig.slaveId.toString()
        txtSavePath.text = settings.savePath

        when (settings.unit.uppercase()) {
            "F" -> rbFahrenheit.isSelected = true
            else -> rbCelsius.isSelected = true
        }

        updatePortFieldsVisibility()

        cmbSource.valueProperty().addListener { _, _, _ -> updatePortFieldsVisibility() }

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
            val port = txtPort.text.ifBlank { "COM4" }
            val baudRate = txtBaudRate.text.toIntOrNull() ?: 9600
            val slaveId = txtSlaveId.text.toIntOrNull() ?: 1
            val unit = if (rbFahrenheit.isSelected) "F" else "C"
            val savePath = txtSavePath.text.ifBlank { System.getProperty("user.home") + "/roasts" }

            val mc = settings.machineConfig.copy(
                machineType = machineType,
                port = port,
                baudRate = baudRate,
                slaveId = slaveId
            )
            val newSettings = AppSettings(
                machineConfig = mc,
                unit = unit,
                savePath = savePath
            )
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

    private fun closeWindow() {
        (btnSave.scene?.window as? Stage)?.close()
    }
}
