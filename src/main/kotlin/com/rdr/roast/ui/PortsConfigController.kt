package com.rdr.roast.ui

import com.fazecast.jSerialComm.SerialPort
import com.rdr.roast.app.MachineConfig
import com.rdr.roast.app.ModbusInputConfig
import com.rdr.roast.app.ModbusInputDecode
import com.rdr.roast.app.ModbusInputMode
import com.rdr.roast.app.ModbusPidConfig
import com.rdr.roast.app.SettingsManager
import com.rdr.roast.app.Transport
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.geometry.Pos
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.layout.GridPane
import javafx.stage.Stage
import java.net.URL
import java.util.ResourceBundle

/**
 * Artisan-style "Ports Configuration" dialog controller.
 * Tab 1: Comm Port (serial for non-MODBUS). Tab 2: MODBUS (serial params + Type + Host + Port + Slave ID).
 * On OK the dialog closes; the **caller** must read getters and apply to [MachineConfig]/settings (Artisan pattern).
 */
class PortsConfigController : Initializable {

    @FXML lateinit var tabPane: javafx.scene.control.TabPane
    @FXML lateinit var cmbComPort: ComboBox<String>
    @FXML lateinit var cmbBaudRate: ComboBox<String>
    @FXML lateinit var cmbByteSize: ComboBox<String>
    @FXML lateinit var cmbParity: ComboBox<String>
    @FXML lateinit var cmbStopbits: ComboBox<String>
    @FXML lateinit var txtTimeout: TextField
    @FXML lateinit var cmbModbusComPort: ComboBox<String>
    @FXML lateinit var cmbModbusBaudRate: ComboBox<String>
    @FXML lateinit var cmbModbusByteSize: ComboBox<String>
    @FXML lateinit var cmbModbusParity: ComboBox<String>
    @FXML lateinit var cmbModbusStopbits: ComboBox<String>
    @FXML lateinit var txtModbusTimeout: TextField
    @FXML lateinit var cmbModbusType: ComboBox<String>
    @FXML lateinit var txtModbusHost: TextField
    @FXML lateinit var txtModbusPort: TextField
    @FXML lateinit var txtSlaveId: TextField
    @FXML lateinit var modbusInputsGrid: GridPane
    @FXML lateinit var txtPidDevice: TextField
    @FXML lateinit var txtPidSv: TextField
    @FXML lateinit var txtPidP: TextField
    @FXML lateinit var txtPidI: TextField
    @FXML lateinit var txtPidD: TextField
    @FXML lateinit var txtPidOn: TextField
    @FXML lateinit var txtPidOff: TextField
    @FXML lateinit var btnOk: javafx.scene.control.Button
    @FXML lateinit var btnCancel: javafx.scene.control.Button

    /** Input 1–10: Device, Register, Function, Divider, Mode, Decode (filled in initialize). */
    private val inputDeviceEdits = mutableListOf<TextField>()
    private val inputRegisterEdits = mutableListOf<TextField>()
    private val inputFunctionCombos = mutableListOf<ComboBox<String>>()
    private val inputDividerCombos = mutableListOf<ComboBox<String>>()
    private val inputModeCombos = mutableListOf<ComboBox<String>>()
    private val inputDecodeCombos = mutableListOf<ComboBox<String>>()

    /** Set to true when user clicked OK (caller checks this and then reads getters). */
    var applied: Boolean = false
        private set

    // Keep Artisan index mapping: 0=RTU, 1=ASCII, 2=Binary, 3=TCP, 4=UDP
    private val modbusTypeItems = listOf("Serial RTU", "Serial ASCII", "Serial Binary", "TCP", "UDP")
    private val baudRates = listOf("1200", "2400", "4800", "9600", "19200", "38400", "57600", "115200")
    private val byteSizes = listOf("7", "8")
    private val parityItems = listOf("O", "E", "N")
    private val stopbitsItems = listOf("1", "2")
    private val functionCodeItems = listOf("1", "2", "3", "4")
    private val dividerItems = listOf("", "1/10", "1/100")
    private val modeItems = listOf("", "C", "F")
    private val decodeItems = listOf("uInt16", "sInt16", "uInt32", "sInt32", "BCD16", "BCD32", "Float32")

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        buildModbusInputsGrid()
        cmbBaudRate.items.setAll(baudRates)
        cmbByteSize.items.setAll(byteSizes)
        cmbParity.items.setAll(parityItems)
        cmbStopbits.items.setAll(stopbitsItems)

        cmbModbusBaudRate.items.setAll(baudRates)
        cmbModbusByteSize.items.setAll(byteSizes)
        cmbModbusParity.items.setAll(parityItems)
        cmbModbusStopbits.items.setAll(stopbitsItems)
        cmbModbusType.items.setAll(modbusTypeItems)

        refreshPortLists()
        val config = SettingsManager.load().machineConfig
        loadFromConfig(config)

        btnOk.setOnAction {
            applied = true
            (btnOk.scene?.window as? Stage)?.close()
        }
        btnCancel.setOnAction {
            (btnCancel.scene?.window as? Stage)?.close()
        }
    }

    private fun buildModbusInputsGrid() {
        val colCount = 7
        modbusInputsGrid.addRow(0,
            Label("Input"),
            Label("Device"),
            Label("Register"),
            Label("Function"),
            Label("Divider"),
            Label("Mode"),
            Label("Decode")
        )
        for (i in 0 until 10) {
            val row = i + 1
            val dev = TextField("0").apply { prefColumnCount = 4 }
            val reg = TextField("0").apply { prefColumnCount = 5 }
            val fcn = ComboBox<String>().apply {
                items.setAll(functionCodeItems)
                selectionModel.select("3")
                prefWidth = 70.0
            }
            val div = ComboBox<String>().apply {
                items.setAll(dividerItems)
                selectionModel.selectFirst()
                prefWidth = 70.0
            }
            val mode = ComboBox<String>().apply {
                items.setAll(modeItems)
                selectionModel.select("C")
                prefWidth = 50.0
            }
            val dec = ComboBox<String>().apply {
                items.setAll(decodeItems)
                selectionModel.selectFirst()
                prefWidth = 80.0
            }
            inputDeviceEdits.add(dev)
            inputRegisterEdits.add(reg)
            inputFunctionCombos.add(fcn)
            inputDividerCombos.add(div)
            inputModeCombos.add(mode)
            inputDecodeCombos.add(dec)
            modbusInputsGrid.addRow(row,
                Label("${i + 1}").apply { alignment = Pos.CENTER_RIGHT },
                dev, reg, fcn, div, mode, dec
            )
        }
    }

    private fun refreshPortLists() {
        val ports = SerialPort.getCommPorts().map { it.systemPortName }
        cmbComPort.items.setAll(ports)
        cmbModbusComPort.items.setAll(ports)
    }

    private fun ensurePortInList(combo: ComboBox<String>, port: String) {
        if (port.isNotBlank() && port !in combo.items) {
            combo.items.add(port)
        }
        combo.selectionModel.select(port)
    }

    /** Load dialog from current [MachineConfig] (e.g. from SettingsManager). */
    fun loadFromConfig(config: MachineConfig) {
        // Comm Port tab
        ensurePortInList(cmbComPort, config.port)
        cmbBaudRate.selectionModel.select(config.baudRate.toString().takeIf { it in baudRates } ?: "9600")
        cmbByteSize.selectionModel.select("8")
        cmbParity.selectionModel.select("N")
        cmbStopbits.selectionModel.select("1")
        txtTimeout.text = "0.5"

        // MODBUS tab: same defaults as Artisan modbusport
        ensurePortInList(cmbModbusComPort, config.port)
        cmbModbusBaudRate.selectionModel.select(config.baudRate.toString().takeIf { it in baudRates } ?: "19200")
        cmbModbusByteSize.selectionModel.select("8")
        cmbModbusParity.selectionModel.select("N")
        cmbModbusStopbits.selectionModel.select("1")
        txtModbusTimeout.text = "0.4"
        val typeIndex = when (config.transport) {
            Transport.TCP -> 3
            Transport.SERIAL -> 0
            else -> 0
        }
        cmbModbusType.selectionModel.select(typeIndex.coerceIn(0, modbusTypeItems.lastIndex))
        txtModbusHost.text = config.host ?: "127.0.0.1"
        txtModbusPort.text = config.tcpPort.toString()
        txtSlaveId.text = config.slaveId.toString()

        // Inputs 1–10
        val inputs = config.modbusInputs
        for (i in 0 until 10) {
            val c = inputs.getOrNull(i) ?: ModbusInputConfig()
            inputDeviceEdits.getOrNull(i)?.text = c.deviceId.toString()
            inputRegisterEdits.getOrNull(i)?.text = c.register.toString()
            inputFunctionCombos.getOrNull(i)?.selectionModel?.select(c.functionCode.toString().takeIf { it in functionCodeItems } ?: "3")
            inputDividerCombos.getOrNull(i)?.selectionModel?.select(c.dividerIndex.coerceIn(0, dividerItems.lastIndex))
            inputModeCombos.getOrNull(i)?.selectionModel?.select(when (c.mode) {
                ModbusInputMode.NONE -> 0
                ModbusInputMode.C -> 1
                ModbusInputMode.F -> 2
            }.coerceIn(0, modeItems.lastIndex))
            inputDecodeCombos.getOrNull(i)?.selectionModel?.select(when (c.decode) {
                ModbusInputDecode.UINT16 -> 0
                ModbusInputDecode.SINT16 -> 1
                ModbusInputDecode.UINT32 -> 2
                ModbusInputDecode.SINT32 -> 3
                ModbusInputDecode.BCD16 -> 4
                ModbusInputDecode.BCD32 -> 5
                ModbusInputDecode.FLOAT32 -> 6
            }.coerceIn(0, decodeItems.lastIndex))
        }

        // PID
        val pid = config.modbusPid
        if (pid != null) {
            txtPidDevice.text = pid.deviceId.toString()
            txtPidSv.text = pid.svRegister.toString()
            txtPidP.text = pid.pRegister.toString()
            txtPidI.text = pid.iRegister.toString()
            txtPidD.text = pid.dRegister.toString()
            txtPidOn.text = pid.onCommand
            txtPidOff.text = pid.offCommand
        } else {
            txtPidDevice.text = "0"
            txtPidSv.text = "0"
            txtPidP.text = "0"
            txtPidI.text = "0"
            txtPidD.text = "0"
            txtPidOn.text = ""
            txtPidOff.text = ""
        }
    }

    // --- Getters for caller (Artisan pattern: caller reads after OK) ---

    fun getCommPort(): String = cmbComPort.selectionModel.selectedItem ?: cmbComPort.editor?.text ?: "COM4"
    fun getBaudRate(): Int = (cmbBaudRate.selectionModel.selectedItem ?: "9600").toIntOrNull() ?: 9600
    fun getByteSize(): Int = (cmbByteSize.selectionModel.selectedItem ?: "8").toIntOrNull() ?: 8
    fun getParity(): String = cmbParity.selectionModel.selectedItem ?: "N"
    fun getStopbits(): Int = (cmbStopbits.selectionModel.selectedItem ?: "1").toIntOrNull() ?: 1
    fun getTimeout(): Double = txtTimeout.text.toDoubleOrNull() ?: 0.5

    fun getModbusComPort(): String = cmbModbusComPort.selectionModel.selectedItem ?: cmbModbusComPort.editor?.text ?: "COM4"
    fun getModbusBaudRate(): Int = (cmbModbusBaudRate.selectionModel.selectedItem ?: "19200").toIntOrNull() ?: 19200
    fun getModbusByteSize(): Int = (cmbModbusByteSize.selectionModel.selectedItem ?: "8").toIntOrNull() ?: 8
    fun getModbusParity(): String = cmbModbusParity.selectionModel.selectedItem ?: "N"
    fun getModbusStopbits(): Int = (cmbModbusStopbits.selectionModel.selectedItem ?: "1").toIntOrNull() ?: 1
    fun getModbusTimeout(): Double = txtModbusTimeout.text.toDoubleOrNull() ?: 0.4
    /** 0=Serial RTU, 1=Serial ASCII, 3=TCP, 4=UDP (Artisan type index). */
    fun getModbusTypeIndex(): Int = cmbModbusType.selectionModel.selectedIndex.coerceIn(0, 4)
    fun getModbusHost(): String = txtModbusHost.text?.trim()?.ifBlank { null } ?: "127.0.0.1"
    fun getModbusPort(): Int = txtModbusPort.text.toIntOrNull() ?: 502
    fun getSlaveId(): Int = txtSlaveId.text.toIntOrNull() ?: 1

    /** Current Input 1–10 config from dialog (caller applies to MachineConfig on OK). */
    fun getModbusInputs(): List<ModbusInputConfig> = (0 until 10).map { i ->
        ModbusInputConfig(
            deviceId = inputDeviceEdits.getOrNull(i)?.text?.toIntOrNull() ?: 0,
            register = inputRegisterEdits.getOrNull(i)?.text?.toIntOrNull() ?: 0,
            functionCode = inputFunctionCombos.getOrNull(i)?.selectionModel?.selectedItem?.toIntOrNull() ?: 3,
            dividerIndex = inputDividerCombos.getOrNull(i)?.selectionModel?.selectedIndex?.coerceIn(0, 2) ?: 0,
            mode = when (inputModeCombos.getOrNull(i)?.selectionModel?.selectedIndex?.coerceIn(0, 2) ?: 1) {
                0 -> ModbusInputMode.NONE
                2 -> ModbusInputMode.F
                else -> ModbusInputMode.C
            },
            decode = when (inputDecodeCombos.getOrNull(i)?.selectionModel?.selectedIndex?.coerceIn(0, decodeItems.lastIndex) ?: 0) {
                1 -> ModbusInputDecode.SINT16
                2 -> ModbusInputDecode.UINT32
                3 -> ModbusInputDecode.SINT32
                4 -> ModbusInputDecode.BCD16
                5 -> ModbusInputDecode.BCD32
                6 -> ModbusInputDecode.FLOAT32
                else -> ModbusInputDecode.UINT16
            }
        )
    }

    /** Current PID config from dialog (caller applies to MachineConfig on OK). */
    fun getModbusPid(): ModbusPidConfig = ModbusPidConfig(
        deviceId = txtPidDevice.text.toIntOrNull() ?: 0,
        svRegister = txtPidSv.text.toIntOrNull() ?: 0,
        pRegister = txtPidP.text.toIntOrNull() ?: 0,
        iRegister = txtPidI.text.toIntOrNull() ?: 0,
        dRegister = txtPidD.text.toIntOrNull() ?: 0,
        onCommand = txtPidOn.text?.trim().orEmpty(),
        offCommand = txtPidOff.text?.trim().orEmpty()
    )
}
