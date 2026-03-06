package com.rdr.roast.ui

import com.fazecast.jSerialComm.SerialPort
import com.rdr.roast.app.MachineConfig
import com.rdr.roast.app.DeviceAssignmentAmbientConfig
import com.rdr.roast.app.DeviceAssignmentConfig
import com.rdr.roast.app.DeviceAssignmentEtBtConfig
import com.rdr.roast.app.DeviceAssignmentNetworksConfig
import com.rdr.roast.app.DeviceAssignmentPhidgetsConfig
import com.rdr.roast.app.DeviceAssignmentSymbolicConfig
import com.rdr.roast.app.DeviceAssignmentYoctopuceConfig
import com.rdr.roast.app.ModbusInputConfig
import com.rdr.roast.app.ModbusInputDecode
import com.rdr.roast.app.ModbusInputMode
import com.rdr.roast.app.ModbusPidConfig
import com.rdr.roast.app.SettingsManager
import com.rdr.roast.app.Transport
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
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

    class ExtraDeviceRow(
        device: String,
        commPort: String,
        baudRate: String,
        byteSize: String,
        parity: String,
        stopbits: String,
        timeout: String
    ) {
        val deviceProperty = SimpleStringProperty(device)
        val commPortProperty = SimpleStringProperty(commPort)
        val baudRateProperty = SimpleStringProperty(baudRate)
        val byteSizeProperty = SimpleStringProperty(byteSize)
        val parityProperty = SimpleStringProperty(parity)
        val stopbitsProperty = SimpleStringProperty(stopbits)
        val timeoutProperty = SimpleStringProperty(timeout)
    }

    @FXML lateinit var tabPane: javafx.scene.control.TabPane
    @FXML lateinit var cmbComPort: ComboBox<String>
    @FXML lateinit var cmbBaudRate: ComboBox<String>
    @FXML lateinit var cmbByteSize: ComboBox<String>
    @FXML lateinit var cmbParity: ComboBox<String>
    @FXML lateinit var cmbStopbits: ComboBox<String>
    @FXML lateinit var txtTimeout: TextField
    @FXML lateinit var chkEtCurve: CheckBox
    @FXML lateinit var chkBtCurve: CheckBox
    @FXML lateinit var chkEtLcd: CheckBox
    @FXML lateinit var chkBtLcd: CheckBox
    @FXML lateinit var chkSwapEtBt: CheckBox
    @FXML lateinit var chkDeviceLogging: CheckBox
    @FXML lateinit var chkDeviceControl: CheckBox
    @FXML lateinit var extraDevicesTable: TableView<ExtraDeviceRow>
    @FXML lateinit var colExtraDevice: TableColumn<ExtraDeviceRow, String>
    @FXML lateinit var colExtraCommPort: TableColumn<ExtraDeviceRow, String>
    @FXML lateinit var colExtraBaudRate: TableColumn<ExtraDeviceRow, String>
    @FXML lateinit var colExtraByteSize: TableColumn<ExtraDeviceRow, String>
    @FXML lateinit var colExtraParity: TableColumn<ExtraDeviceRow, String>
    @FXML lateinit var colExtraStopbits: TableColumn<ExtraDeviceRow, String>
    @FXML lateinit var colExtraTimeout: TableColumn<ExtraDeviceRow, String>
    @FXML lateinit var btnAddExtraDevice: Button
    @FXML lateinit var btnDeleteExtraDevice: Button
    @FXML lateinit var txtSymbEt: TextField
    @FXML lateinit var txtSymbBt: TextField
    @FXML lateinit var chkPhidgetRemote: CheckBox
    @FXML lateinit var chkPhidgetRemoteOnly: CheckBox
    @FXML lateinit var txtPhidgetServerId: TextField
    @FXML lateinit var txtPhidgetPort: TextField
    @FXML lateinit var txtPhidgetPassword: TextField
    @FXML lateinit var txtPhidgetEtChannel: TextField
    @FXML lateinit var txtPhidgetBtChannel: TextField
    @FXML lateinit var chkYoctoRemote: CheckBox
    @FXML lateinit var txtYoctoVirtualHub: TextField
    @FXML lateinit var txtYoctoEmissivity: TextField
    @FXML lateinit var cmbAmbientTempDevice: ComboBox<String>
    @FXML lateinit var cmbAmbientTempSource: ComboBox<String>
    @FXML lateinit var cmbAmbientHumidityDevice: ComboBox<String>
    @FXML lateinit var cmbAmbientHumiditySource: ComboBox<String>
    @FXML lateinit var cmbAmbientPressureDevice: ComboBox<String>
    @FXML lateinit var cmbAmbientPressureSource: ComboBox<String>
    @FXML lateinit var txtAmbientElevation: TextField
    @FXML lateinit var txtNetS7Host: TextField
    @FXML lateinit var txtNetS7Port: TextField
    @FXML lateinit var txtNetSantokerHost: TextField
    @FXML lateinit var txtNetSantokerPort: TextField
    @FXML lateinit var txtNetKaleidoHost: TextField
    @FXML lateinit var txtNetKaleidoPort: TextField
    @FXML lateinit var txtNetWsHost: TextField
    @FXML lateinit var txtNetWsPort: TextField
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
    @FXML lateinit var txtModbusSerialDelay: TextField
    @FXML lateinit var txtModbusSerialRetries: TextField
    @FXML lateinit var txtModbusIpTimeout: TextField
    @FXML lateinit var txtModbusIpRetries: TextField
    @FXML lateinit var chkModbusLittleEndian: CheckBox
    @FXML lateinit var chkModbusLittleEndianWords: CheckBox
    @FXML lateinit var chkModbusOptimize: CheckBox
    @FXML lateinit var chkModbusFetchFullBlocks: CheckBox
    @FXML lateinit var modbusInputsGrid: GridPane
    @FXML lateinit var txtPidDevice: TextField
    @FXML lateinit var txtPidSv: TextField
    @FXML lateinit var txtPidP: TextField
    @FXML lateinit var txtPidI: TextField
    @FXML lateinit var txtPidD: TextField
    @FXML lateinit var txtPidOn: TextField
    @FXML lateinit var txtPidOff: TextField
    @FXML lateinit var s7InputsGrid: GridPane
    @FXML lateinit var txtS7Host: TextField
    @FXML lateinit var txtS7Port: TextField
    @FXML lateinit var txtS7Rack: TextField
    @FXML lateinit var txtS7Slot: TextField
    @FXML lateinit var chkS7Optimize: CheckBox
    @FXML lateinit var chkS7FetchFullBlocks: CheckBox
    @FXML lateinit var txtS7PidDevice: TextField
    @FXML lateinit var txtS7PidSv: TextField
    @FXML lateinit var txtS7PidP: TextField
    @FXML lateinit var txtS7PidI: TextField
    @FXML lateinit var txtS7PidD: TextField
    @FXML lateinit var txtWsHost: TextField
    @FXML lateinit var txtWsPort: TextField
    @FXML lateinit var txtWsPath: TextField
    @FXML lateinit var txtWsId: TextField
    @FXML lateinit var txtWsTimeoutConnect: TextField
    @FXML lateinit var txtWsTimeoutReconnect: TextField
    @FXML lateinit var txtWsTimeoutRequest: TextField
    @FXML lateinit var txtWsNodeMessageId: TextField
    @FXML lateinit var txtWsNodeMachineId: TextField
    @FXML lateinit var txtWsNodeCommand: TextField
    @FXML lateinit var txtWsNodeData: TextField
    @FXML lateinit var txtWsNodeMessage: TextField
    @FXML lateinit var txtWsCommandDataRequest: TextField
    @FXML lateinit var txtWsMessageCharge: TextField
    @FXML lateinit var txtWsMessageDrop: TextField
    @FXML lateinit var txtWsFlagStartOnCharge: TextField
    @FXML lateinit var txtWsFlagOffOnDrop: TextField
    @FXML lateinit var txtWsEventEvent: TextField
    @FXML lateinit var txtWsEventNode: TextField
    @FXML lateinit var txtWsEventDry: TextField
    @FXML lateinit var txtWsEventFcs: TextField
    @FXML lateinit var txtWsEventFce: TextField
    @FXML lateinit var txtWsEventScs: TextField
    @FXML lateinit var txtWsEventSce: TextField
    @FXML lateinit var chkWsCompression: CheckBox
    @FXML lateinit var wsInputsGrid: GridPane
    @FXML lateinit var btnOk: javafx.scene.control.Button
    @FXML lateinit var btnCancel: javafx.scene.control.Button

    /** Input 1–10: Device, Register, Function, Divider, Mode, Decode (filled in initialize). */
    private val inputDeviceEdits = mutableListOf<TextField>()
    private val inputRegisterEdits = mutableListOf<TextField>()
    private val inputFunctionCombos = mutableListOf<ComboBox<String>>()
    private val inputDividerCombos = mutableListOf<ComboBox<String>>()
    private val inputModeCombos = mutableListOf<ComboBox<String>>()
    private val inputDecodeCombos = mutableListOf<ComboBox<String>>()
    private val s7AreaCombos = mutableListOf<ComboBox<String>>()
    private val s7DbEdits = mutableListOf<TextField>()
    private val s7StartEdits = mutableListOf<TextField>()
    private val s7TypeCombos = mutableListOf<ComboBox<String>>()
    private val s7FactorEdits = mutableListOf<TextField>()
    private val s7ModeCombos = mutableListOf<ComboBox<String>>()
    private val wsInputRequestEdits = mutableListOf<TextField>()
    private val wsInputNodeEdits = mutableListOf<TextField>()
    private val wsInputModeCombos = mutableListOf<ComboBox<String>>()

    /** Set to true when user clicked OK (caller checks this and then reads getters). */
    var applied: Boolean = false
        private set

    // Keep Artisan index mapping: 0=RTU, 1=ASCII, 2=Binary, 3=TCP, 4=UDP
    private val modbusTypeItems = listOf("Serial RTU", "Serial ASCII", "Serial Binary", "TCP", "UDP")
    private val baudRates = listOf("1200", "2400", "4800", "9600", "19200", "38400", "57600", "115200")
    private val byteSizes = listOf("7", "8")
    private val parityItems = listOf("O", "E", "N")
    private val stopbitsItems = listOf("1", "2")
    private val functionCodeItems = listOf("3", "4")
    private val dividerItems = listOf("1/10", "1")
    private val modeItems = listOf("C", "F")
    private val decodeItems = listOf("uint16", "int16", "float32")
    private val ambientDeviceItems = listOf("None", "Main Device", "Extra Device")
    private val ambientSourceItems = listOf("Auto", "ET", "BT", "Extra 1", "Extra 2")

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        buildModbusInputsGrid()
        buildS7InputsGrid()
        buildWsInputsGrid()
        initExtraDevicesTable()
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
        cmbAmbientTempDevice.items.setAll(ambientDeviceItems)
        cmbAmbientHumidityDevice.items.setAll(ambientDeviceItems)
        cmbAmbientPressureDevice.items.setAll(ambientDeviceItems)
        cmbAmbientTempSource.items.setAll(ambientSourceItems)
        cmbAmbientHumiditySource.items.setAll(ambientSourceItems)
        cmbAmbientPressureSource.items.setAll(ambientSourceItems)
        val settings = SettingsManager.load()
        val config = settings.machineConfig
        loadFromConfig(config)
        loadDeviceAssignment(settings.deviceAssignment, config)
        initPlaceholderDefaults()

        btnOk.setOnAction {
            applied = true
            (btnOk.scene?.window as? Stage)?.close()
        }
        btnCancel.setOnAction {
            (btnCancel.scene?.window as? Stage)?.close()
        }
    }

    private fun loadDeviceAssignment(config: DeviceAssignmentConfig, machineConfig: MachineConfig) {
        chkEtCurve.isSelected = config.etBt.etCurve
        chkBtCurve.isSelected = config.etBt.btCurve
        chkEtLcd.isSelected = config.etBt.etLcd
        chkBtLcd.isSelected = config.etBt.btLcd
        chkSwapEtBt.isSelected = config.etBt.swapEtBt
        chkDeviceLogging.isSelected = config.etBt.logging
        chkDeviceControl.isSelected = config.etBt.control

        txtSymbEt.text = config.symbolic.etExpression
        txtSymbBt.text = config.symbolic.btExpression

        chkPhidgetRemote.isSelected = config.phidgets.remoteFlag
        chkPhidgetRemoteOnly.isSelected = config.phidgets.remoteOnly
        txtPhidgetServerId.text = config.phidgets.serverId
        txtPhidgetPort.text = config.phidgets.port.toString()
        txtPhidgetPassword.text = config.phidgets.password
        txtPhidgetEtChannel.text = config.phidgets.etChannel.toString().ifBlank { machineConfig.phidgetEtChannel.toString() }
        txtPhidgetBtChannel.text = config.phidgets.btChannel.toString().ifBlank { machineConfig.phidgetBtChannel.toString() }

        chkYoctoRemote.isSelected = config.yoctopuce.remoteFlag
        txtYoctoVirtualHub.text = config.yoctopuce.virtualHub
        txtYoctoEmissivity.text = config.yoctopuce.emissivity.toString()

        cmbAmbientTempDevice.selectionModel.select(config.ambient.temperatureDeviceIndex.coerceIn(0, ambientDeviceItems.lastIndex))
        cmbAmbientHumidityDevice.selectionModel.select(config.ambient.humidityDeviceIndex.coerceIn(0, ambientDeviceItems.lastIndex))
        cmbAmbientPressureDevice.selectionModel.select(config.ambient.pressureDeviceIndex.coerceIn(0, ambientDeviceItems.lastIndex))
        cmbAmbientTempSource.selectionModel.select(config.ambient.temperatureSourceIndex.coerceIn(0, ambientSourceItems.lastIndex))
        cmbAmbientHumiditySource.selectionModel.select(config.ambient.humiditySourceIndex.coerceIn(0, ambientSourceItems.lastIndex))
        cmbAmbientPressureSource.selectionModel.select(config.ambient.pressureSourceIndex.coerceIn(0, ambientSourceItems.lastIndex))
        txtAmbientElevation.text = config.ambient.elevationMeters.toString()

        txtNetS7Host.text = config.networks.s7Host
        txtNetS7Port.text = config.networks.s7Port.toString()
        txtNetSantokerHost.text = config.networks.santokerHost
        txtNetSantokerPort.text = config.networks.santokerPort.toString()
        txtNetKaleidoHost.text = config.networks.kaleidoHost
        txtNetKaleidoPort.text = config.networks.kaleidoPort.toString()
        txtNetWsHost.text = config.networks.websocketHost
        txtNetWsPort.text = config.networks.websocketPort.toString()

        if (config.extraDevices.isNotEmpty()) {
            val rows = config.extraDevices.mapNotNull { decodeExtraDeviceRow(it) }
            if (rows.isNotEmpty()) {
                extraDevicesTable.items.setAll(rows)
            }
        }
    }

    private fun initExtraDevicesTable() {
        colExtraDevice.setCellValueFactory { it.value.deviceProperty }
        colExtraCommPort.setCellValueFactory { it.value.commPortProperty }
        colExtraBaudRate.setCellValueFactory { it.value.baudRateProperty }
        colExtraByteSize.setCellValueFactory { it.value.byteSizeProperty }
        colExtraParity.setCellValueFactory { it.value.parityProperty }
        colExtraStopbits.setCellValueFactory { it.value.stopbitsProperty }
        colExtraTimeout.setCellValueFactory { it.value.timeoutProperty }
        extraDevicesTable.items = FXCollections.observableArrayList(
            ExtraDeviceRow(
                device = "Virtual",
                commPort = "",
                baudRate = "9600",
                byteSize = "8",
                parity = "N",
                stopbits = "1",
                timeout = "0.5"
            )
        )
        btnAddExtraDevice.setOnAction {
            extraDevicesTable.items.add(
                ExtraDeviceRow("Virtual", "", "9600", "8", "N", "1", "0.5")
            )
        }
        btnDeleteExtraDevice.setOnAction {
            val selected = extraDevicesTable.selectionModel.selectedItem ?: return@setOnAction
            extraDevicesTable.items.remove(selected)
        }
    }

    private fun buildModbusInputsGrid() {
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
                selectionModel.select("1")
                prefWidth = 70.0
            }
            val mode = ComboBox<String>().apply {
                items.setAll(modeItems)
                selectionModel.select("C")
                prefWidth = 50.0
            }
            val dec = ComboBox<String>().apply {
                items.setAll(decodeItems)
                selectionModel.select("uint16")
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

    private fun buildS7InputsGrid() {
        s7InputsGrid.addRow(
            0,
            Label("Input"),
            Label("Area"),
            Label("DB#"),
            Label("Start"),
            Label("Type"),
            Label("Factor"),
            Label("Mode")
        )
        for (i in 0 until 12) {
            val row = i + 1
            val area = ComboBox<String>().apply {
                items.setAll("DB", "M", "I", "Q")
                selectionModel.select("DB")
                prefWidth = 70.0
            }
            val db = TextField("1").apply { prefColumnCount = 4 }
            val start = TextField("0").apply { prefColumnCount = 6 }
            val type = ComboBox<String>().apply {
                items.setAll("Int", "Float")
                selectionModel.select("Float")
                prefWidth = 80.0
            }
            val factor = TextField("1.0").apply { prefColumnCount = 6 }
            val mode = ComboBox<String>().apply {
                items.setAll("C", "F")
                selectionModel.select("C")
                prefWidth = 60.0
            }
            s7AreaCombos.add(area)
            s7DbEdits.add(db)
            s7StartEdits.add(start)
            s7TypeCombos.add(type)
            s7FactorEdits.add(factor)
            s7ModeCombos.add(mode)
            s7InputsGrid.addRow(row, Label("${i + 1}"), area, db, start, type, factor, mode)
        }
    }

    private fun buildWsInputsGrid() {
        wsInputsGrid.addRow(0, Label("Input"), Label("Request"), Label("Node"), Label("Mode"))
        for (i in 0 until 10) {
            val request = TextField().apply { prefColumnCount = 10 }
            val node = TextField().apply { prefColumnCount = 8 }
            val mode = ComboBox<String>().apply {
                items.setAll("C", "F")
                selectionModel.select("C")
                prefWidth = 70.0
            }
            wsInputRequestEdits.add(request)
            wsInputNodeEdits.add(node)
            wsInputModeCombos.add(mode)
            wsInputsGrid.addRow(i + 1, Label("${i + 1}"), request, node, mode)
        }
    }

    private fun initPlaceholderDefaults() {
        txtModbusSerialDelay.text = "0"
        txtModbusSerialRetries.text = "0"
        txtModbusIpTimeout.text = txtModbusTimeout.text.ifBlank { "0.4" }
        txtModbusIpRetries.text = "1"
        txtS7Host.text = "127.0.0.1"
        txtS7Port.text = "102"
        txtS7Rack.text = "0"
        txtS7Slot.text = "1"
        txtWsHost.text = "127.0.0.1"
        txtWsPort.text = "80"
        txtWsPath.text = "/"
        txtWsId.text = ""
        txtWsTimeoutConnect.text = "3"
        txtWsTimeoutReconnect.text = "3"
        txtWsTimeoutRequest.text = "3"
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
            inputDividerCombos.getOrNull(i)?.selectionModel?.select(if (c.dividerIndex == 1) "1/10" else "1")
            inputModeCombos.getOrNull(i)?.selectionModel?.select(if (c.mode == ModbusInputMode.F) "F" else "C")
            inputDecodeCombos.getOrNull(i)?.selectionModel?.select(
                when (c.decode) {
                    ModbusInputDecode.SINT16 -> "int16"
                    ModbusInputDecode.FLOAT32 -> "float32"
                    else -> "uint16"
                }
            )
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
            dividerIndex = if (inputDividerCombos.getOrNull(i)?.selectionModel?.selectedItem == "1/10") 1 else 0,
            mode = if (inputModeCombos.getOrNull(i)?.selectionModel?.selectedItem == "F") ModbusInputMode.F else ModbusInputMode.C,
            decode = when (inputDecodeCombos.getOrNull(i)?.selectionModel?.selectedItem) {
                "int16" -> ModbusInputDecode.SINT16
                "float32" -> ModbusInputDecode.FLOAT32
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

    // New tabs are wired for safe dialog operation; persistence/integration follows in later tasks.
    fun getModbusIpTimeout(): Double = txtModbusIpTimeout.text.toDoubleOrNull() ?: getModbusTimeout()
    fun getModbusIpRetries(): Int = txtModbusIpRetries.text.toIntOrNull() ?: 1

    fun getDeviceAssignmentConfig(): DeviceAssignmentConfig = DeviceAssignmentConfig(
        etBt = DeviceAssignmentEtBtConfig(
            etCurve = chkEtCurve.isSelected,
            btCurve = chkBtCurve.isSelected,
            etLcd = chkEtLcd.isSelected,
            btLcd = chkBtLcd.isSelected,
            swapEtBt = chkSwapEtBt.isSelected,
            logging = chkDeviceLogging.isSelected,
            control = chkDeviceControl.isSelected
        ),
        extraDevices = extraDevicesTable.items.map { encodeExtraDeviceRow(it) },
        symbolic = DeviceAssignmentSymbolicConfig(
            etExpression = txtSymbEt.text?.trim().orEmpty(),
            btExpression = txtSymbBt.text?.trim().orEmpty()
        ),
        phidgets = DeviceAssignmentPhidgetsConfig(
            remoteFlag = chkPhidgetRemote.isSelected,
            serverId = txtPhidgetServerId.text?.trim().orEmpty(),
            port = txtPhidgetPort.text.toIntOrNull() ?: 5661,
            password = txtPhidgetPassword.text?.trim().orEmpty(),
            remoteOnly = chkPhidgetRemoteOnly.isSelected,
            etChannel = txtPhidgetEtChannel.text.toIntOrNull()?.coerceIn(1, 4) ?: 1,
            btChannel = txtPhidgetBtChannel.text.toIntOrNull()?.coerceIn(1, 4) ?: 2
        ),
        yoctopuce = DeviceAssignmentYoctopuceConfig(
            remoteFlag = chkYoctoRemote.isSelected,
            virtualHub = txtYoctoVirtualHub.text?.trim().orEmpty(),
            emissivity = txtYoctoEmissivity.text.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 1.0
        ),
        ambient = DeviceAssignmentAmbientConfig(
            temperatureDeviceIndex = cmbAmbientTempDevice.selectionModel.selectedIndex.coerceAtLeast(0),
            humidityDeviceIndex = cmbAmbientHumidityDevice.selectionModel.selectedIndex.coerceAtLeast(0),
            pressureDeviceIndex = cmbAmbientPressureDevice.selectionModel.selectedIndex.coerceAtLeast(0),
            temperatureSourceIndex = cmbAmbientTempSource.selectionModel.selectedIndex.coerceAtLeast(0),
            humiditySourceIndex = cmbAmbientHumiditySource.selectionModel.selectedIndex.coerceAtLeast(0),
            pressureSourceIndex = cmbAmbientPressureSource.selectionModel.selectedIndex.coerceAtLeast(0),
            elevationMeters = txtAmbientElevation.text.toIntOrNull() ?: 0
        ),
        networks = DeviceAssignmentNetworksConfig(
            s7Host = txtNetS7Host.text?.trim().orEmpty().ifBlank { "127.0.0.1" },
            s7Port = txtNetS7Port.text.toIntOrNull() ?: 102,
            santokerHost = txtNetSantokerHost.text?.trim().orEmpty(),
            santokerPort = txtNetSantokerPort.text.toIntOrNull() ?: 20001,
            kaleidoHost = txtNetKaleidoHost.text?.trim().orEmpty(),
            kaleidoPort = txtNetKaleidoPort.text.toIntOrNull() ?: 20002,
            websocketHost = txtNetWsHost.text?.trim().orEmpty().ifBlank { "127.0.0.1" },
            websocketPort = txtNetWsPort.text.toIntOrNull() ?: 80
        )
    )

    private fun encodeExtraDeviceRow(row: ExtraDeviceRow): String =
        listOf(
            row.deviceProperty.get(),
            row.commPortProperty.get(),
            row.baudRateProperty.get(),
            row.byteSizeProperty.get(),
            row.parityProperty.get(),
            row.stopbitsProperty.get(),
            row.timeoutProperty.get()
        ).joinToString("|")

    private fun decodeExtraDeviceRow(raw: String): ExtraDeviceRow? {
        val parts = raw.split('|')
        if (parts.size < 7) return null
        return ExtraDeviceRow(
            device = parts[0],
            commPort = parts[1],
            baudRate = parts[2],
            byteSize = parts[3],
            parity = parts[4],
            stopbits = parts[5],
            timeout = parts[6]
        )
    }
}
