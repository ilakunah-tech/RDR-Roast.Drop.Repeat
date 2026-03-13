package com.rdr.roast.ui

import com.fazecast.jSerialComm.SerialPort
import com.rdr.roast.app.MachineConfig
import com.rdr.roast.app.DeviceAssignmentAmbientConfig
import com.rdr.roast.app.DeviceAssignmentConfig
import com.rdr.roast.app.DeviceAssignmentBatchManagerConfig
import com.rdr.roast.app.DeviceAssignmentEtBtConfig
import com.rdr.roast.app.DeviceAssignmentNetworksConfig
import com.rdr.roast.app.DeviceAssignmentPhidgetsConfig
import com.rdr.roast.app.DeviceAssignmentSymbolicConfig
import com.rdr.roast.app.DeviceAssignmentYoctopuceConfig
import com.rdr.roast.app.ModbusInputConfig
import com.rdr.roast.app.ModbusInputDecode
import com.rdr.roast.app.ModbusInputMode
import com.rdr.roast.app.ModbusPidConfig
import com.rdr.roast.app.PortConfig
import com.rdr.roast.app.PortExtraSerialDeviceConfig
import com.rdr.roast.app.PortModbusAdvancedConfig
import com.rdr.roast.app.PortS7Config
import com.rdr.roast.app.PortS7InputConfig
import com.rdr.roast.app.PortS7PidConfig
import com.rdr.roast.app.PortWebSocketConfig
import com.rdr.roast.app.PortWebSocketInputConfig
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
import javafx.scene.layout.VBox
import javafx.stage.Stage
import java.net.URL
import java.util.ResourceBundle

/**
 * Artisan-style "Ports Configuration" dialog controller.
 * Tab 1: Comm Port (serial for non-MODBUS). Tab 2: MODBUS (serial params + Type + Host + Port + Slave ID).
 * On OK the dialog closes; the **caller** must read getters and apply to [MachineConfig]/settings (Artisan pattern).
 */
class PortsConfigController : Initializable {
    enum class ViewMode {
        FULL,
        DEVICES_ONLY,
        PORT_ONLY
    }

    class ExtraDeviceRow(
        device: String,
        color1: String,
        color2: String,
        label1: String,
        label2: String,
        y1: String,
        y2: String,
        lcd1: String,
        lcd2: String,
        curve1: String,
        curve2: String,
        deltaAxis1: String,
        deltaAxis2: String,
        fill1: String,
        fill2: String
    ) {
        val deviceProperty = SimpleStringProperty(device)
        val color1Property = SimpleStringProperty(color1)
        val color2Property = SimpleStringProperty(color2)
        val label1Property = SimpleStringProperty(label1)
        val label2Property = SimpleStringProperty(label2)
        val y1Property = SimpleStringProperty(y1)
        val y2Property = SimpleStringProperty(y2)
        val lcd1Property = SimpleStringProperty(lcd1)
        val lcd2Property = SimpleStringProperty(lcd2)
        val curve1Property = SimpleStringProperty(curve1)
        val curve2Property = SimpleStringProperty(curve2)
        val deltaAxis1Property = SimpleStringProperty(deltaAxis1)
        val deltaAxis2Property = SimpleStringProperty(deltaAxis2)
        val fill1Property = SimpleStringProperty(fill1)
        val fill2Property = SimpleStringProperty(fill2)
    }

    class ExtraSerialRow(
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
    @FXML lateinit var cmbEtBtMeter: ComboBox<String>
    @FXML lateinit var cmbPidControlType: ComboBox<String>
    @FXML lateinit var cmbPidReadType: ComboBox<String>
    @FXML lateinit var cmbPidControlUnitId: ComboBox<String>
    @FXML lateinit var cmbPidReadUnitId: ComboBox<String>
    @FXML lateinit var chkPidDutyPowerLcds: CheckBox
    @FXML lateinit var chkPidUseModbusPort: CheckBox
    @FXML lateinit var cmbTc4EtChannel: ComboBox<String>
    @FXML lateinit var cmbTc4BtChannel: ComboBox<String>
    @FXML lateinit var cmbTc4AtChannel: ComboBox<String>
    @FXML lateinit var chkTc4PidFirmware: CheckBox
    @FXML lateinit var txtProgInputPath: TextField
    @FXML lateinit var chkProgOutputEnabled: CheckBox
    @FXML lateinit var txtProgOutputPath: TextField
    @FXML lateinit var extraDevicesTable: TableView<ExtraDeviceRow>
    @FXML lateinit var colExtraDevice: TableColumn<ExtraDeviceRow, String>
    @FXML lateinit var colExtraColor1: TableColumn<ExtraDeviceRow, String>
    @FXML lateinit var colExtraColor2: TableColumn<ExtraDeviceRow, String>
    @FXML lateinit var colExtraLabel1: TableColumn<ExtraDeviceRow, String>
    @FXML lateinit var colExtraLabel2: TableColumn<ExtraDeviceRow, String>
    @FXML lateinit var colExtraY1: TableColumn<ExtraDeviceRow, String>
    @FXML lateinit var colExtraY2: TableColumn<ExtraDeviceRow, String>
    @FXML lateinit var colExtraLcd1: TableColumn<ExtraDeviceRow, String>
    @FXML lateinit var colExtraLcd2: TableColumn<ExtraDeviceRow, String>
    @FXML lateinit var colExtraCurve1: TableColumn<ExtraDeviceRow, String>
    @FXML lateinit var colExtraCurve2: TableColumn<ExtraDeviceRow, String>
    @FXML lateinit var colExtraDeltaAxis1: TableColumn<ExtraDeviceRow, String>
    @FXML lateinit var colExtraDeltaAxis2: TableColumn<ExtraDeviceRow, String>
    @FXML lateinit var colExtraFill1: TableColumn<ExtraDeviceRow, String>
    @FXML lateinit var colExtraFill2: TableColumn<ExtraDeviceRow, String>
    @FXML lateinit var btnAddExtraDevice: Button
    @FXML lateinit var btnDeleteExtraDevice: Button
    @FXML lateinit var btnCopyExtraTable: Button
    @FXML lateinit var btnUpdateExtraProfile: Button
    @FXML lateinit var btnResetExtraDevices: Button
    @FXML lateinit var btnHelpExtraDevices: Button
    @FXML lateinit var txtSymbEt: TextField
    @FXML lateinit var txtSymbBt: TextField
    @FXML lateinit var chkPhidgetRemote: CheckBox
    @FXML lateinit var chkPhidgetRemoteOnly: CheckBox
    @FXML lateinit var txtPhidgetServerId: TextField
    @FXML lateinit var txtPhidgetPort: TextField
    @FXML lateinit var txtPhidgetPassword: TextField
    @FXML lateinit var txtPhidgetEtChannel: TextField
    @FXML lateinit var txtPhidgetBtChannel: TextField
    @FXML lateinit var cmbPhidgetType0: ComboBox<String>
    @FXML lateinit var cmbPhidgetType1: ComboBox<String>
    @FXML lateinit var cmbPhidgetType2: ComboBox<String>
    @FXML lateinit var cmbPhidgetType3: ComboBox<String>
    @FXML lateinit var chkPhidgetAsync0: CheckBox
    @FXML lateinit var chkPhidgetAsync1: CheckBox
    @FXML lateinit var chkPhidgetAsync2: CheckBox
    @FXML lateinit var chkPhidgetAsync3: CheckBox
    @FXML lateinit var txtPhidgetChange0: TextField
    @FXML lateinit var txtPhidgetChange1: TextField
    @FXML lateinit var txtPhidgetChange2: TextField
    @FXML lateinit var txtPhidgetChange3: TextField
    @FXML lateinit var cmbPhidgetChange0: ComboBox<String>
    @FXML lateinit var cmbPhidgetChange1: ComboBox<String>
    @FXML lateinit var cmbPhidgetChange2: ComboBox<String>
    @FXML lateinit var cmbPhidgetChange3: ComboBox<String>
    @FXML lateinit var cmbPhidgetRate: ComboBox<String>
    @FXML lateinit var chkPhidgetIrAsync: CheckBox
    @FXML lateinit var txtPhidgetIrChange: TextField
    @FXML lateinit var cmbPhidgetIrChange: ComboBox<String>
    @FXML lateinit var cmbPhidgetIrRate: ComboBox<String>
    @FXML lateinit var txtPhidgetIrEmissivity: TextField
    @FXML lateinit var cmbPhidget1200TypeA: ComboBox<String>
    @FXML lateinit var cmbPhidget1200WireA: ComboBox<String>
    @FXML lateinit var chkPhidget1200AsyncA: CheckBox
    @FXML lateinit var cmbPhidget1200ChangeA: ComboBox<String>
    @FXML lateinit var cmbPhidget1200RateA: ComboBox<String>
    @FXML lateinit var cmbPhidget1200TypeB: ComboBox<String>
    @FXML lateinit var cmbPhidget1200WireB: ComboBox<String>
    @FXML lateinit var chkPhidget1200AsyncB: CheckBox
    @FXML lateinit var cmbPhidget1200ChangeB: ComboBox<String>
    @FXML lateinit var cmbPhidget1200RateB: ComboBox<String>
    @FXML lateinit var cmbPhidget1400Power: ComboBox<String>
    @FXML lateinit var cmbPhidget1400Mode: ComboBox<String>
    @FXML lateinit var cmbPhidget1046Gain0: ComboBox<String>
    @FXML lateinit var cmbPhidget1046Gain1: ComboBox<String>
    @FXML lateinit var cmbPhidget1046Gain2: ComboBox<String>
    @FXML lateinit var cmbPhidget1046Gain3: ComboBox<String>
    @FXML lateinit var cmbPhidget1046Wiring0: ComboBox<String>
    @FXML lateinit var cmbPhidget1046Wiring1: ComboBox<String>
    @FXML lateinit var cmbPhidget1046Wiring2: ComboBox<String>
    @FXML lateinit var cmbPhidget1046Wiring3: ComboBox<String>
    @FXML lateinit var chkPhidget1046Async0: CheckBox
    @FXML lateinit var chkPhidget1046Async1: CheckBox
    @FXML lateinit var chkPhidget1046Async2: CheckBox
    @FXML lateinit var chkPhidget1046Async3: CheckBox
    @FXML lateinit var cmbPhidget1046Rate: ComboBox<String>
    @FXML lateinit var chkPhidgetIoAsync0: CheckBox
    @FXML lateinit var chkPhidgetIoAsync1: CheckBox
    @FXML lateinit var chkPhidgetIoAsync2: CheckBox
    @FXML lateinit var chkPhidgetIoAsync3: CheckBox
    @FXML lateinit var chkPhidgetIoAsync4: CheckBox
    @FXML lateinit var chkPhidgetIoAsync5: CheckBox
    @FXML lateinit var chkPhidgetIoAsync6: CheckBox
    @FXML lateinit var chkPhidgetIoAsync7: CheckBox
    @FXML lateinit var cmbPhidgetIoChange0: ComboBox<String>
    @FXML lateinit var cmbPhidgetIoChange1: ComboBox<String>
    @FXML lateinit var cmbPhidgetIoChange2: ComboBox<String>
    @FXML lateinit var cmbPhidgetIoChange3: ComboBox<String>
    @FXML lateinit var cmbPhidgetIoChange4: ComboBox<String>
    @FXML lateinit var cmbPhidgetIoChange5: ComboBox<String>
    @FXML lateinit var cmbPhidgetIoChange6: ComboBox<String>
    @FXML lateinit var cmbPhidgetIoChange7: ComboBox<String>
    @FXML lateinit var cmbPhidgetIoRate0: ComboBox<String>
    @FXML lateinit var cmbPhidgetIoRate1: ComboBox<String>
    @FXML lateinit var cmbPhidgetIoRate2: ComboBox<String>
    @FXML lateinit var cmbPhidgetIoRate3: ComboBox<String>
    @FXML lateinit var cmbPhidgetIoRate4: ComboBox<String>
    @FXML lateinit var cmbPhidgetIoRate5: ComboBox<String>
    @FXML lateinit var cmbPhidgetIoRate6: ComboBox<String>
    @FXML lateinit var cmbPhidgetIoRate7: ComboBox<String>
    @FXML lateinit var cmbPhidgetIoRange0: ComboBox<String>
    @FXML lateinit var cmbPhidgetIoRange1: ComboBox<String>
    @FXML lateinit var cmbPhidgetIoRange2: ComboBox<String>
    @FXML lateinit var cmbPhidgetIoRange3: ComboBox<String>
    @FXML lateinit var cmbPhidgetIoRange4: ComboBox<String>
    @FXML lateinit var cmbPhidgetIoRange5: ComboBox<String>
    @FXML lateinit var cmbPhidgetIoRange6: ComboBox<String>
    @FXML lateinit var cmbPhidgetIoRange7: ComboBox<String>
    @FXML lateinit var chkPhidgetIoRatio0: CheckBox
    @FXML lateinit var chkPhidgetIoRatio1: CheckBox
    @FXML lateinit var chkPhidgetIoRatio2: CheckBox
    @FXML lateinit var chkPhidgetIoRatio3: CheckBox
    @FXML lateinit var chkPhidgetIoRatio4: CheckBox
    @FXML lateinit var chkPhidgetIoRatio5: CheckBox
    @FXML lateinit var chkPhidgetIoRatio6: CheckBox
    @FXML lateinit var chkPhidgetIoRatio7: CheckBox
    @FXML lateinit var chkYoctoRemote: CheckBox
    @FXML lateinit var txtYoctoVirtualHub: TextField
    @FXML lateinit var txtYoctoEmissivity: TextField
    @FXML lateinit var chkYoctoAsync: CheckBox
    @FXML lateinit var cmbYoctoAsyncRate: ComboBox<String>
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
    @FXML lateinit var chkNetSantokerBluetooth: CheckBox
    @FXML lateinit var chkNetSantokerSerial: CheckBox
    @FXML lateinit var chkNetSantokerWifi: CheckBox
    @FXML lateinit var chkNetSantokerCharge: CheckBox
    @FXML lateinit var chkNetSantokerDry: CheckBox
    @FXML lateinit var chkNetSantokerFcs: CheckBox
    @FXML lateinit var chkNetSantokerFce: CheckBox
    @FXML lateinit var chkNetSantokerScs: CheckBox
    @FXML lateinit var chkNetSantokerSce: CheckBox
    @FXML lateinit var chkNetSantokerDrop: CheckBox
    @FXML lateinit var txtNetKaleidoHost: TextField
    @FXML lateinit var txtNetKaleidoPort: TextField
    @FXML lateinit var chkNetKaleidoWifi: CheckBox
    @FXML lateinit var chkNetKaleidoCharge: CheckBox
    @FXML lateinit var chkNetKaleidoDry: CheckBox
    @FXML lateinit var chkNetKaleidoFcs: CheckBox
    @FXML lateinit var chkNetKaleidoFce: CheckBox
    @FXML lateinit var chkNetKaleidoScs: CheckBox
    @FXML lateinit var chkNetKaleidoSce: CheckBox
    @FXML lateinit var chkNetKaleidoDrop: CheckBox
    @FXML lateinit var txtNetMugmaHost: TextField
    @FXML lateinit var txtNetMugmaPort: TextField
    @FXML lateinit var txtNetColorTrackMean: TextField
    @FXML lateinit var txtNetColorTrackMedian: TextField
    @FXML lateinit var txtNetShelly3emHost: TextField
    @FXML lateinit var txtNetShellyPlusHost: TextField
    @FXML lateinit var txtNetWsHost: TextField
    @FXML lateinit var txtNetWsPort: TextField
    @FXML lateinit var cmbBatchScale1Model: ComboBox<String>
    @FXML lateinit var cmbBatchScale1Name: ComboBox<String>
    @FXML lateinit var cmbBatchScale2Model: ComboBox<String>
    @FXML lateinit var cmbBatchScale2Name: ComboBox<String>
    @FXML lateinit var cmbBatchContainerGreen: ComboBox<String>
    @FXML lateinit var cmbBatchContainerRoasted: ComboBox<String>
    @FXML lateinit var txtBatchAccuracy: TextField
    @FXML lateinit var chkBatchGreenPort: CheckBox
    @FXML lateinit var txtBatchGreenPort: TextField
    @FXML lateinit var chkBatchRoastedPort: CheckBox
    @FXML lateinit var txtBatchRoastedPort: TextField
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
    @FXML lateinit var deviceEtBtPane: VBox
    @FXML lateinit var portEtBtPane: VBox
    @FXML lateinit var deviceExtraPane: VBox
    @FXML lateinit var portExtraPane: VBox
    @FXML lateinit var portExtraTable: TableView<ExtraSerialRow>
    @FXML lateinit var colPortExtraDevice: TableColumn<ExtraSerialRow, String>
    @FXML lateinit var colPortExtraCommPort: TableColumn<ExtraSerialRow, String>
    @FXML lateinit var colPortExtraBaudRate: TableColumn<ExtraSerialRow, String>
    @FXML lateinit var colPortExtraByteSize: TableColumn<ExtraSerialRow, String>
    @FXML lateinit var colPortExtraParity: TableColumn<ExtraSerialRow, String>
    @FXML lateinit var colPortExtraStopbits: TableColumn<ExtraSerialRow, String>
    @FXML lateinit var colPortExtraTimeout: TableColumn<ExtraSerialRow, String>

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
    private val s7FactorCombos = mutableListOf<ComboBox<String>>()
    private val s7ModeCombos = mutableListOf<ComboBox<String>>()
    private val wsInputRequestEdits = mutableListOf<TextField>()
    private val wsInputNodeEdits = mutableListOf<TextField>()
    private val wsInputModeCombos = mutableListOf<ComboBox<String>>()

    /** Set to true when user clicked OK (caller checks this and then reads getters). */
    var applied: Boolean = false
        private set

    private var viewMode: ViewMode = ViewMode.FULL
    private var initialized: Boolean = false

    // Keep Artisan index mapping: 0=RTU, 1=ASCII, 2=Binary, 3=TCP, 4=UDP
    private val modbusTypeItems = listOf("Serial RTU", "Serial ASCII", "Serial Binary", "TCP", "UDP")
    private val baudRates = listOf("1200", "2400", "4800", "9600", "19200", "38400", "57600", "57800", "115200")
    private val byteSizes = listOf("7", "8")
    private val parityItems = listOf("O", "E", "N")
    private val stopbitsItems = listOf("1", "2")
    private val functionCodeItems = listOf("1", "2", "3", "4")
    private val dividerItems = listOf("", "1/10", "1/100")
    private val modeItems = listOf("", "C", "F")
    private val decodeItems = listOf("uInt16", "uInt32", "sInt16", "sInt32", "BCD16", "BCD32", "Float32")
    private val ambientDeviceItems = listOf("", "Phidget HUM1000x", "Yocto Meteo", "Phidget TMP1000")
    private val ambientSourceItems = listOf("", "ET", "BT", "Extra 1", "Extra 2")
    private val scaleModelItems = listOf("Acaia", "Generic", "Phidget", "Yoctopuce", "Virtual")
    private val batchContainerItems = listOf("", "Container 1", "Container 2", "Container 3")
    private val yoctoAsyncRates = listOf("32ms", "64ms", "128ms", "256ms", "512ms", "768ms", "1s", "15s")
    private val phidgetTypeItems = listOf("K", "J", "E", "T")
    private val phidgetChangeItems = listOf("0.0C", "0.005C", "0.01C", "0.02C", "0.05C", "0.1C", "0.2C", "0.3C", "0.4C", "0.5C", "0.6C", "0.7C", "0.8C", "0.9C", "1.0C")
    private val phidgetRateItems = listOf("32ms", "64ms", "128ms", "256ms", "512ms", "768ms", "1s")
    private val phidget1200TypeItems = listOf("PT100 3850", "PT100 3920", "PT1000 3850", "PT1000 3920")
    private val phidget1200WireItems = listOf("2-wire", "3-wire", "4-wire")
    private val phidget1400PowerItems = listOf("12V", "24V")
    private val phidget1400ModeItems = listOf("NPN", "PNP")
    private val phidget1046GainItems = listOf("1", "8", "16", "32", "64", "128")
    private val phidget1046WiringItems = listOf("Div", "2-wire", "3-wire", "4-wire")
    private val phidgetIoChangeItems = listOf("100mV", "10mV", "1mV")
    private val phidgetIoRangeItems = listOf("Auto", "+1mV", "+40mV", "+20mV", "+312.5mV", "+400mV", "+1000mV", "+2V", "+5V", "+15V")
    private val etBtMeters = listOf(
        "MODBUS", "ARC BT/ET", "Aillio Bullet R1 BT/DT", "Aillio Bullet R1 BT/S7", "Aillio Bullet R2",
        "Amprobe", "Apollo DT301", "Behmor BT/CT", "CENTER 300", "CENTER 301", "CENTER 302",
        "CENTER 303", "CENTER 304", "CENTER 305", "CENTER 306", "CENTER 309", "ColorTrack BT",
        "ColorTrack Serial", "DUMMY", "Digi-Sense 20250-07", "EXTECH 421509", "EXTECH 755",
        "Extech 42570", "HB BI/ET", "Hottop BI/FI", "IKAWA", "Kaleido BT/ET", "Mastech MS6514",
        "Mugma RI/O", "NONE", "Omega HH309A", "Omega HH806RA", "Omega HH802U", "Omega HH806AUJ",
        "Omega HHM290J", "Phidget 1011 K 01", "Phidget 1011 K 01 Digital 01", "Phidget TMP1100 1xTC",
        "Phidget TMP1101 4xTC 01", "Phidget TMP1200 1xRTD A", "Phidget VCP1000", "Phidget VCP1001",
        "Phidget VCP1002", "S7gram", "Santoker BT/ET", "Santoker R BT/ET", "TASI TA612B",
        "TE VA18B", "Thermoworks BlueDOT", "VICTOR 8686", "VOLTCRAFT 300K", "VOLTCRAFT 302KJ",
        "VOLTCRAFT K201", "VOLTCRAFT K202", "VOLTCRAFT K204", "VOLTCRAFT PL-125-T2",
        "VOLTCRAFT PL-125-T4", "WebSocket", "Yocto 0-10V Rx", "Yocto 4-20mA Rx", "Yocto Current",
        "Yocto Energy", "Yocto IR", "Yocto PT100", "Yocto Power", "Yocto Sensor", "Yocto Serial",
        "Yocto Thermocouple", "Yocto Voltage", "Yocto millivolt Rx"
    )
    private val pidTypeItems = listOf("Fuji PXG", "Fuji PXR", "Delta DTA")
    private val tc4AtChannelItems = listOf("None") + (1..16).map { it.toString() }
    private val rs485UnitIdItems = (1..32).map { it.toString() }

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        buildModbusInputsGrid()
        buildS7InputsGrid()
        buildWsInputsGrid()
        initExtraDevicesTable()
        initPortExtraTable()
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
        cmbYoctoAsyncRate.items.setAll(yoctoAsyncRates)
        cmbBatchScale1Model.items.setAll(scaleModelItems)
        cmbBatchScale2Model.items.setAll(scaleModelItems)
        cmbBatchContainerGreen.items.setAll(batchContainerItems)
        cmbBatchContainerRoasted.items.setAll(batchContainerItems)
        cmbEtBtMeter.items.setAll(etBtMeters)
        cmbPidControlType.items.setAll(pidTypeItems)
        cmbPidReadType.items.setAll(pidTypeItems)
        cmbPidControlUnitId.items.setAll(rs485UnitIdItems)
        cmbPidReadUnitId.items.setAll(rs485UnitIdItems)
        cmbTc4EtChannel.items.setAll("1", "2", "3", "4")
        cmbTc4BtChannel.items.setAll("1", "2", "3", "4")
        cmbTc4AtChannel.items.setAll(tc4AtChannelItems)
        listOf(cmbPhidgetType0, cmbPhidgetType1, cmbPhidgetType2, cmbPhidgetType3).forEach { it.items.setAll(phidgetTypeItems) }
        listOf(cmbPhidgetChange0, cmbPhidgetChange1, cmbPhidgetChange2, cmbPhidgetChange3, cmbPhidgetIrChange).forEach { it.items.setAll(phidgetChangeItems) }
        cmbPhidgetRate.items.setAll(phidgetRateItems)
        cmbPhidgetIrRate.items.setAll(phidgetRateItems)
        listOf(cmbPhidget1200TypeA, cmbPhidget1200TypeB).forEach { it.items.setAll(phidget1200TypeItems) }
        listOf(cmbPhidget1200WireA, cmbPhidget1200WireB).forEach { it.items.setAll(phidget1200WireItems) }
        listOf(cmbPhidget1200ChangeA, cmbPhidget1200ChangeB).forEach { it.items.setAll(phidgetChangeItems) }
        listOf(cmbPhidget1200RateA, cmbPhidget1200RateB, cmbPhidget1046Rate).forEach { it.items.setAll(phidgetRateItems) }
        cmbPhidget1400Power.items.setAll(phidget1400PowerItems)
        cmbPhidget1400Mode.items.setAll(phidget1400ModeItems)
        listOf(cmbPhidget1046Gain0, cmbPhidget1046Gain1, cmbPhidget1046Gain2, cmbPhidget1046Gain3).forEach { it.items.setAll(phidget1046GainItems) }
        listOf(cmbPhidget1046Wiring0, cmbPhidget1046Wiring1, cmbPhidget1046Wiring2, cmbPhidget1046Wiring3).forEach { it.items.setAll(phidget1046WiringItems) }
        listOf(
            cmbPhidgetIoChange0, cmbPhidgetIoChange1, cmbPhidgetIoChange2, cmbPhidgetIoChange3,
            cmbPhidgetIoChange4, cmbPhidgetIoChange5, cmbPhidgetIoChange6, cmbPhidgetIoChange7
        ).forEach { it.items.setAll(phidgetIoChangeItems) }
        listOf(
            cmbPhidgetIoRate0, cmbPhidgetIoRate1, cmbPhidgetIoRate2, cmbPhidgetIoRate3,
            cmbPhidgetIoRate4, cmbPhidgetIoRate5, cmbPhidgetIoRate6, cmbPhidgetIoRate7
        ).forEach { it.items.setAll(phidgetRateItems) }
        listOf(
            cmbPhidgetIoRange0, cmbPhidgetIoRange1, cmbPhidgetIoRange2, cmbPhidgetIoRange3,
            cmbPhidgetIoRange4, cmbPhidgetIoRange5, cmbPhidgetIoRange6, cmbPhidgetIoRange7
        ).forEach { it.items.setAll(phidgetIoRangeItems) }
        cmbAmbientTempDevice.selectionModel.selectedIndexProperty().addListener { _, _, _ -> updateAmbientSourceEnablement() }
        cmbAmbientHumidityDevice.selectionModel.selectedIndexProperty().addListener { _, _, _ -> updateAmbientSourceEnablement() }
        cmbAmbientPressureDevice.selectionModel.selectedIndexProperty().addListener { _, _, _ -> updateAmbientSourceEnablement() }
        val settings = SettingsManager.load()
        val config = settings.machineConfig
        loadFromConfig(config)
        syncPortExtraFromCurrentSerial()
        loadDeviceAssignment(settings.deviceAssignment, config)
        loadPortConfig(settings.portConfig)
        initPlaceholderDefaults()
        initialized = true
        applyViewMode()

        btnOk.setOnAction {
            applied = true
            (btnOk.scene?.window as? Stage)?.close()
        }
        btnCancel.setOnAction {
            (btnCancel.scene?.window as? Stage)?.close()
        }
    }

    fun configureViewMode(mode: ViewMode) {
        viewMode = mode
        applyViewMode()
    }

    private fun applyViewMode() {
        if (!initialized || !::tabPane.isInitialized) return
        val allTabs = tabPane.tabs.toList()
        val filtered = when (viewMode) {
            ViewMode.FULL -> allTabs
            ViewMode.DEVICES_ONLY -> {
                allTabs.filter {
                    it.text in setOf("ET/BT", "Extra Devices", "Symb ET/BT", "Phidgets", "Yoctopuce", "Ambient", "Networks", "Batch Manager")
                }
            }
            ViewMode.PORT_ONLY -> {
                allTabs.filter {
                    it.text in setOf("ET/BT", "Extra Devices", "MODBUS", "S7", "WebSocket")
                }.onEach { tab ->
                    if (tab.text == "Extra Devices") tab.text = "Extra"
                    if (tab.text == "MODBUS") tab.text = "Modbus"
                }
            }
        }
        tabPane.tabs.setAll(filtered)
        val isPortMode = viewMode == ViewMode.PORT_ONLY
        val isDeviceMode = !isPortMode
        if (::deviceEtBtPane.isInitialized) {
            deviceEtBtPane.isVisible = isDeviceMode
            deviceEtBtPane.isManaged = isDeviceMode
        }
        if (::portEtBtPane.isInitialized) {
            portEtBtPane.isVisible = isPortMode
            portEtBtPane.isManaged = isPortMode
        }
        if (::deviceExtraPane.isInitialized) {
            deviceExtraPane.isVisible = isDeviceMode
            deviceExtraPane.isManaged = isDeviceMode
        }
        if (::portExtraPane.isInitialized) {
            portExtraPane.isVisible = isPortMode
            portExtraPane.isManaged = isPortMode
        }
    }

    private fun initPortExtraTable() {
        colPortExtraDevice.setCellValueFactory { it.value.deviceProperty }
        colPortExtraCommPort.setCellValueFactory { it.value.commPortProperty }
        colPortExtraBaudRate.setCellValueFactory { it.value.baudRateProperty }
        colPortExtraByteSize.setCellValueFactory { it.value.byteSizeProperty }
        colPortExtraParity.setCellValueFactory { it.value.parityProperty }
        colPortExtraStopbits.setCellValueFactory { it.value.stopbitsProperty }
        colPortExtraTimeout.setCellValueFactory { it.value.timeoutProperty }
        portExtraTable.items = FXCollections.observableArrayList(
            ExtraSerialRow("Virtual", "COM4", "115200", "8", "N", "1", "1.0")
        )
    }

    private fun syncPortExtraFromCurrentSerial() {
        if (!::portExtraTable.isInitialized || portExtraTable.items.isEmpty()) return
        val current = portExtraTable.items[0]
        current.commPortProperty.set(cmbComPort.value ?: "COM4")
        current.baudRateProperty.set(cmbBaudRate.value ?: "115200")
        current.byteSizeProperty.set(cmbByteSize.value ?: "8")
        current.parityProperty.set(cmbParity.value ?: "N")
        current.stopbitsProperty.set(cmbStopbits.value ?: "1")
        current.timeoutProperty.set(txtTimeout.text.ifBlank { "1.0" })
        portExtraTable.refresh()
    }

    fun loadDeviceAssignment(config: DeviceAssignmentConfig, modbusInputs: List<ModbusInputConfig>, modbusPid: ModbusPidConfig?) {
        val machineConfig = MachineConfig(modbusInputs = modbusInputs, modbusPid = modbusPid)
        loadDeviceAssignment(config, machineConfig)
    }

    private fun loadDeviceAssignment(config: DeviceAssignmentConfig, machineConfig: MachineConfig) {
        chkEtCurve.isSelected = config.etBt.etCurve
        chkBtCurve.isSelected = config.etBt.btCurve
        chkEtLcd.isSelected = config.etBt.etLcd
        chkBtLcd.isSelected = config.etBt.btLcd
        chkSwapEtBt.isSelected = config.etBt.swapEtBt
        chkDeviceLogging.isSelected = config.etBt.logging
        chkDeviceControl.isSelected = config.etBt.control
        cmbEtBtMeter.value = config.etBt.meter.ifBlank { "MODBUS" }
        cmbPidControlType.value = config.etBt.pidControlType.ifBlank { "Fuji PXG" }
        cmbPidReadType.value = config.etBt.pidReadType.ifBlank { "Fuji PXR" }
        cmbPidControlUnitId.value = config.etBt.pidControlUnitId.toString()
        cmbPidReadUnitId.value = config.etBt.pidReadUnitId.toString()
        chkPidDutyPowerLcds.isSelected = config.etBt.pidDutyPowerLcds
        chkPidUseModbusPort.isSelected = config.etBt.pidUseModbusPort
        cmbTc4EtChannel.value = config.etBt.tc4EtChannel.toString()
        cmbTc4BtChannel.value = config.etBt.tc4BtChannel.toString()
        cmbTc4AtChannel.value = config.etBt.tc4AtChannel.ifBlank { "None" }
        chkTc4PidFirmware.isSelected = config.etBt.tc4PidFirmware
        txtProgInputPath.text = config.etBt.progInputPath
        chkProgOutputEnabled.isSelected = config.etBt.progOutputEnabled
        txtProgOutputPath.text = config.etBt.progOutputPath

        txtSymbEt.text = config.symbolic.etExpression
        txtSymbBt.text = config.symbolic.btExpression

        chkPhidgetRemote.isSelected = config.phidgets.remoteFlag
        chkPhidgetRemoteOnly.isSelected = config.phidgets.remoteOnly
        txtPhidgetServerId.text = config.phidgets.serverId
        txtPhidgetPort.text = config.phidgets.port.toString()
        txtPhidgetPassword.text = config.phidgets.password
        txtPhidgetEtChannel.text = config.phidgets.etChannel.toString().ifBlank { machineConfig.phidgetEtChannel.toString() }
        txtPhidgetBtChannel.text = config.phidgets.btChannel.toString().ifBlank { machineConfig.phidgetBtChannel.toString() }
        val tcTypes = config.phidgets.tcTypes + listOf("K", "K", "K", "K")
        val tcAsync = config.phidgets.tcAsync + listOf(false, false, false, false)
        val tcChange = config.phidgets.tcChange + listOf(0.2, 0.2, 0.2, 0.2)
        listOf(cmbPhidgetType0, cmbPhidgetType1, cmbPhidgetType2, cmbPhidgetType3).forEachIndexed { i, cmb ->
            cmb.value = tcTypes[i]
        }
        listOf(chkPhidgetAsync0, chkPhidgetAsync1, chkPhidgetAsync2, chkPhidgetAsync3).forEachIndexed { i, chk ->
            chk.isSelected = tcAsync[i]
        }
        listOf(cmbPhidgetChange0, cmbPhidgetChange1, cmbPhidgetChange2, cmbPhidgetChange3).forEachIndexed { i, cmb ->
            cmb.value = "${tcChange[i]}C"
            if (cmb.value !in phidgetChangeItems) cmb.value = "0.2C"
        }
        cmbPhidgetRate.value = "${config.phidgets.tcRateMs}ms"
        if (cmbPhidgetRate.value !in phidgetRateItems) cmbPhidgetRate.value = "256ms"
        chkPhidgetIrAsync.isSelected = config.phidgets.irAsync
        cmbPhidgetIrChange.value = "${config.phidgets.irChange}C"
        if (cmbPhidgetIrChange.value !in phidgetChangeItems) cmbPhidgetIrChange.value = "0.2C"
        cmbPhidgetIrRate.value = "${config.phidgets.irRateMs}ms"
        if (cmbPhidgetIrRate.value !in phidgetRateItems) cmbPhidgetIrRate.value = "256ms"
        txtPhidgetIrEmissivity.text = config.phidgets.irEmissivity.toString()
        cmbPhidget1200TypeA.value = config.phidgets.rtdTypeA.ifBlank { "PT100 3850" }
        cmbPhidget1200WireA.value = config.phidgets.rtdWireA.ifBlank { "2-wire" }
        chkPhidget1200AsyncA.isSelected = config.phidgets.rtdAsyncA
        cmbPhidget1200ChangeA.value = "${config.phidgets.rtdChangeA}C"
        if (cmbPhidget1200ChangeA.value !in phidgetChangeItems) cmbPhidget1200ChangeA.value = "0.2C"
        cmbPhidget1200RateA.value = "${config.phidgets.rtdRateAms}ms"
        if (cmbPhidget1200RateA.value !in phidgetRateItems) cmbPhidget1200RateA.value = "250ms"
        cmbPhidget1200TypeB.value = config.phidgets.rtdTypeB.ifBlank { "PT100 3850" }
        cmbPhidget1200WireB.value = config.phidgets.rtdWireB.ifBlank { "2-wire" }
        chkPhidget1200AsyncB.isSelected = config.phidgets.rtdAsyncB
        cmbPhidget1200ChangeB.value = "${config.phidgets.rtdChangeB}C"
        if (cmbPhidget1200ChangeB.value !in phidgetChangeItems) cmbPhidget1200ChangeB.value = "0.2C"
        cmbPhidget1200RateB.value = "${config.phidgets.rtdRateBms}ms"
        if (cmbPhidget1200RateB.value !in phidgetRateItems) cmbPhidget1200RateB.value = "250ms"
        cmbPhidget1400Power.value = config.phidgets.daq1400Power.ifBlank { "12V" }
        cmbPhidget1400Mode.value = config.phidgets.daq1400Mode.ifBlank { "NPN" }
        val rtdGain = config.phidgets.rtd1046Gain + listOf("1", "1", "1", "1")
        val rtdWiring = config.phidgets.rtd1046Wiring + listOf("Div", "Div", "Div", "Div")
        val rtdAsync = config.phidgets.rtd1046Async + listOf(false, false, false, false)
        listOf(cmbPhidget1046Gain0, cmbPhidget1046Gain1, cmbPhidget1046Gain2, cmbPhidget1046Gain3).forEachIndexed { i, cmb -> cmb.value = rtdGain[i] }
        listOf(cmbPhidget1046Wiring0, cmbPhidget1046Wiring1, cmbPhidget1046Wiring2, cmbPhidget1046Wiring3).forEachIndexed { i, cmb -> cmb.value = rtdWiring[i] }
        listOf(chkPhidget1046Async0, chkPhidget1046Async1, chkPhidget1046Async2, chkPhidget1046Async3).forEachIndexed { i, chk -> chk.isSelected = rtdAsync[i] }
        cmbPhidget1046Rate.value = "${config.phidgets.rtd1046RateMs}ms"
        if (cmbPhidget1046Rate.value !in phidgetRateItems) cmbPhidget1046Rate.value = "256ms"
        val ioAsync = config.phidgets.ioAsync + List(8) { false }
        val ioChange = config.phidgets.ioChange + List(8) { "100mV" }
        val ioRate = config.phidgets.ioRateMs + List(8) { 256 }
        val ioRange = config.phidgets.ioRange + List(8) { "Auto" }
        val ioRatio = config.phidgets.ioRatio + List(8) { false }
        listOf(chkPhidgetIoAsync0, chkPhidgetIoAsync1, chkPhidgetIoAsync2, chkPhidgetIoAsync3, chkPhidgetIoAsync4, chkPhidgetIoAsync5, chkPhidgetIoAsync6, chkPhidgetIoAsync7).forEachIndexed { i, chk -> chk.isSelected = ioAsync[i] }
        listOf(cmbPhidgetIoChange0, cmbPhidgetIoChange1, cmbPhidgetIoChange2, cmbPhidgetIoChange3, cmbPhidgetIoChange4, cmbPhidgetIoChange5, cmbPhidgetIoChange6, cmbPhidgetIoChange7).forEachIndexed { i, cmb -> cmb.value = ioChange[i] }
        listOf(cmbPhidgetIoRate0, cmbPhidgetIoRate1, cmbPhidgetIoRate2, cmbPhidgetIoRate3, cmbPhidgetIoRate4, cmbPhidgetIoRate5, cmbPhidgetIoRate6, cmbPhidgetIoRate7).forEachIndexed { i, cmb ->
            cmb.value = "${ioRate[i]}ms"
            if (cmb.value !in phidgetRateItems) cmb.value = "256ms"
        }
        listOf(cmbPhidgetIoRange0, cmbPhidgetIoRange1, cmbPhidgetIoRange2, cmbPhidgetIoRange3, cmbPhidgetIoRange4, cmbPhidgetIoRange5, cmbPhidgetIoRange6, cmbPhidgetIoRange7).forEachIndexed { i, cmb -> cmb.value = ioRange[i] }
        listOf(chkPhidgetIoRatio0, chkPhidgetIoRatio1, chkPhidgetIoRatio2, chkPhidgetIoRatio3, chkPhidgetIoRatio4, chkPhidgetIoRatio5, chkPhidgetIoRatio6, chkPhidgetIoRatio7).forEachIndexed { i, chk -> chk.isSelected = ioRatio[i] }

        chkYoctoRemote.isSelected = config.yoctopuce.remoteFlag
        txtYoctoVirtualHub.text = config.yoctopuce.virtualHub
        txtYoctoEmissivity.text = config.yoctopuce.emissivity.toString()
        chkYoctoAsync.isSelected = config.yoctopuce.asyncEnabled
        cmbYoctoAsyncRate.value = "${config.yoctopuce.asyncRateMs}ms"
        if (cmbYoctoAsyncRate.value !in yoctoAsyncRates) cmbYoctoAsyncRate.value = "256ms"

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
        chkNetSantokerBluetooth.isSelected = config.networks.santokerBluetooth
        chkNetSantokerSerial.isSelected = config.networks.santokerSerial
        chkNetSantokerWifi.isSelected = config.networks.santokerWifi
        chkNetSantokerCharge.isSelected = config.networks.santokerCharge
        chkNetSantokerDry.isSelected = config.networks.santokerDry
        chkNetSantokerFcs.isSelected = config.networks.santokerFcs
        chkNetSantokerFce.isSelected = config.networks.santokerFce
        chkNetSantokerScs.isSelected = config.networks.santokerScs
        chkNetSantokerSce.isSelected = config.networks.santokerSce
        chkNetSantokerDrop.isSelected = config.networks.santokerDrop
        txtNetKaleidoHost.text = config.networks.kaleidoHost
        txtNetKaleidoPort.text = config.networks.kaleidoPort.toString()
        chkNetKaleidoWifi.isSelected = config.networks.kaleidoWifi
        chkNetKaleidoCharge.isSelected = config.networks.kaleidoCharge
        chkNetKaleidoDry.isSelected = config.networks.kaleidoDry
        chkNetKaleidoFcs.isSelected = config.networks.kaleidoFcs
        chkNetKaleidoFce.isSelected = config.networks.kaleidoFce
        chkNetKaleidoScs.isSelected = config.networks.kaleidoScs
        chkNetKaleidoSce.isSelected = config.networks.kaleidoSce
        chkNetKaleidoDrop.isSelected = config.networks.kaleidoDrop
        txtNetMugmaHost.text = config.networks.mugmaHost
        txtNetMugmaPort.text = config.networks.mugmaPort.toString()
        txtNetColorTrackMean.text = config.networks.colorTrackMeanFilter.toString()
        txtNetColorTrackMedian.text = config.networks.colorTrackMedianFilter.toString()
        txtNetShelly3emHost.text = config.networks.shelly3emHost
        txtNetShellyPlusHost.text = config.networks.shellyPlusPlugHost
        txtNetWsHost.text = config.networks.websocketHost
        txtNetWsPort.text = config.networks.websocketPort.toString()
        cmbBatchScale1Model.value = config.batchManager.scale1Model.ifBlank { "Acaia" }
        cmbBatchScale1Name.value = config.batchManager.scale1Name
        cmbBatchScale2Model.value = config.batchManager.scale2Model.ifBlank { "Acaia" }
        cmbBatchScale2Name.value = config.batchManager.scale2Name
        cmbBatchContainerGreen.value = config.batchManager.containerGreen
        cmbBatchContainerRoasted.value = config.batchManager.containerRoasted
        txtBatchAccuracy.text = config.batchManager.containerAccuracyPct.toString()
        chkBatchGreenPort.isSelected = config.batchManager.taskDisplayGreenEnabled
        txtBatchGreenPort.text = config.batchManager.taskDisplayGreenPort.toString()
        chkBatchRoastedPort.isSelected = config.batchManager.taskDisplayRoastedEnabled
        txtBatchRoastedPort.text = config.batchManager.taskDisplayRoastedPort.toString()

        if (config.extraDevices.isNotEmpty()) {
            val rows = config.extraDevices.mapNotNull { decodeExtraDeviceRow(it) }
            if (rows.isNotEmpty()) {
                extraDevicesTable.items.setAll(rows)
            }
        }
        cmbAmbientTempSource.items.setAll(ambientSourceItems)
        cmbAmbientHumiditySource.items.setAll(ambientSourceItems)
        cmbAmbientPressureSource.items.setAll(ambientSourceItems)
        updateAmbientSourceEnablement()
    }

    private fun initExtraDevicesTable() {
        colExtraDevice.setCellValueFactory { it.value.deviceProperty }
        colExtraColor1.setCellValueFactory { it.value.color1Property }
        colExtraColor2.setCellValueFactory { it.value.color2Property }
        colExtraLabel1.setCellValueFactory { it.value.label1Property }
        colExtraLabel2.setCellValueFactory { it.value.label2Property }
        colExtraY1.setCellValueFactory { it.value.y1Property }
        colExtraY2.setCellValueFactory { it.value.y2Property }
        colExtraLcd1.setCellValueFactory { it.value.lcd1Property }
        colExtraLcd2.setCellValueFactory { it.value.lcd2Property }
        colExtraCurve1.setCellValueFactory { it.value.curve1Property }
        colExtraCurve2.setCellValueFactory { it.value.curve2Property }
        colExtraDeltaAxis1.setCellValueFactory { it.value.deltaAxis1Property }
        colExtraDeltaAxis2.setCellValueFactory { it.value.deltaAxis2Property }
        colExtraFill1.setCellValueFactory { it.value.fill1Property }
        colExtraFill2.setCellValueFactory { it.value.fill2Property }
        extraDevicesTable.items = FXCollections.observableArrayList(
            ExtraDeviceRow(
                device = "Virtual",
                color1 = "black",
                color2 = "black",
                label1 = "Extra 1",
                label2 = "Extra 2",
                y1 = "",
                y2 = "",
                lcd1 = "",
                lcd2 = "",
                curve1 = "",
                curve2 = "",
                deltaAxis1 = "",
                deltaAxis2 = "",
                fill1 = "0",
                fill2 = "0"
            )
        )
        btnAddExtraDevice.setOnAction {
            extraDevicesTable.items.add(
                ExtraDeviceRow("Virtual", "black", "black", "Extra 1", "Extra 2", "", "", "", "", "", "", "", "", "0", "0")
            )
        }
        btnDeleteExtraDevice.setOnAction {
            val selected = extraDevicesTable.selectionModel.selectedItem ?: return@setOnAction
            extraDevicesTable.items.remove(selected)
        }
        btnCopyExtraTable.setOnAction { extraDevicesTable.items = FXCollections.observableArrayList(extraDevicesTable.items.map { it }) }
        btnUpdateExtraProfile.setOnAction { }
        btnResetExtraDevices.setOnAction {
            extraDevicesTable.items.setAll(
                ExtraDeviceRow("Virtual", "black", "black", "Extra 1", "Extra 2", "", "", "", "", "", "", "", "", "0", "0")
            )
        }
        btnHelpExtraDevices.setOnAction { }
    }

    private fun updateAmbientSourceEnablement() {
        cmbAmbientTempSource.isDisable = cmbAmbientTempDevice.selectionModel.selectedIndex != 0
        cmbAmbientHumiditySource.isDisable = cmbAmbientHumidityDevice.selectionModel.selectedIndex != 0
        cmbAmbientPressureSource.isDisable = cmbAmbientPressureDevice.selectionModel.selectedIndex != 0
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
                selectionModel.select("1/10")
                prefWidth = 70.0
            }
            val mode = ComboBox<String>().apply {
                items.setAll(modeItems)
                selectionModel.select("C")
                prefWidth = 50.0
            }
            val dec = ComboBox<String>().apply {
                items.setAll(decodeItems)
                selectionModel.select("uInt16")
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
                items.setAll("", "PE", "PA", "MK", "CT", "TM", "DB")
                selectionModel.select("DB")
                prefWidth = 70.0
            }
            val db = TextField("1").apply { prefColumnCount = 4 }
            val start = TextField("0").apply { prefColumnCount = 6 }
            val type = ComboBox<String>().apply {
                items.setAll("Int", "Float", "IntFloat", "Bool(0)", "Bool(1)", "Bool(2)", "Bool(3)", "Bool(4)", "Bool(5)", "Bool(6)", "Bool(7)")
                selectionModel.select("Int")
                prefWidth = 80.0
            }
            val factor = ComboBox<String>().apply {
                items.setAll(dividerItems)
                selectionModel.select("")
                prefWidth = 70.0
            }
            val mode = ComboBox<String>().apply {
                items.setAll("", "C", "F")
                selectionModel.select("C")
                prefWidth = 60.0
            }
            s7AreaCombos.add(area)
            s7DbEdits.add(db)
            s7StartEdits.add(start)
            s7TypeCombos.add(type)
            s7FactorCombos.add(factor)
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
        if (txtModbusSerialDelay.text.isBlank()) txtModbusSerialDelay.text = "0"
        if (txtModbusSerialRetries.text.isBlank()) txtModbusSerialRetries.text = "0"
        if (txtModbusIpTimeout.text.isBlank()) txtModbusIpTimeout.text = txtModbusTimeout.text.ifBlank { "0.4" }
        if (txtModbusIpRetries.text.isBlank()) txtModbusIpRetries.text = "1"
        if (txtS7Host.text.isBlank()) txtS7Host.text = "127.0.0.1"
        if (txtS7Port.text.isBlank()) txtS7Port.text = "102"
        if (txtS7Rack.text.isBlank()) txtS7Rack.text = "0"
        if (txtS7Slot.text.isBlank()) txtS7Slot.text = "1"
        if (txtWsHost.text.isBlank()) txtWsHost.text = "127.0.0.1"
        if (txtWsPort.text.isBlank()) txtWsPort.text = "80"
        if (txtWsPath.text.isBlank()) txtWsPath.text = "/"
        if (txtWsId.text.isBlank()) txtWsId.text = ""
        if (txtWsTimeoutConnect.text.isBlank()) txtWsTimeoutConnect.text = "3"
        if (txtWsTimeoutReconnect.text.isBlank()) txtWsTimeoutReconnect.text = "3"
        if (txtWsTimeoutRequest.text.isBlank()) txtWsTimeoutRequest.text = "3"
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
            inputDividerCombos.getOrNull(i)?.selectionModel?.select(
                when (c.dividerIndex) {
                    1 -> "1/10"
                    2 -> "1/100"
                    else -> ""
                }
            )
            inputModeCombos.getOrNull(i)?.selectionModel?.select(
                when (c.mode) {
                    ModbusInputMode.C -> "C"
                    ModbusInputMode.F -> "F"
                    else -> ""
                }
            )
            inputDecodeCombos.getOrNull(i)?.selectionModel?.select(
                when (c.decode) {
                    ModbusInputDecode.UINT32 -> "uInt32"
                    ModbusInputDecode.SINT16 -> "sInt16"
                    ModbusInputDecode.SINT32 -> "sInt32"
                    ModbusInputDecode.BCD16 -> "BCD16"
                    ModbusInputDecode.BCD32 -> "BCD32"
                    ModbusInputDecode.FLOAT32 -> "Float32"
                    else -> "uInt16"
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
            dividerIndex = when (inputDividerCombos.getOrNull(i)?.selectionModel?.selectedItem) {
                "1/10" -> 1
                "1/100" -> 2
                else -> 0
            },
            mode = when (inputModeCombos.getOrNull(i)?.selectionModel?.selectedItem) {
                "C" -> ModbusInputMode.C
                "F" -> ModbusInputMode.F
                else -> ModbusInputMode.NONE
            },
            decode = when (inputDecodeCombos.getOrNull(i)?.selectionModel?.selectedItem) {
                "uInt32" -> ModbusInputDecode.UINT32
                "sInt16" -> ModbusInputDecode.SINT16
                "sInt32" -> ModbusInputDecode.SINT32
                "BCD16" -> ModbusInputDecode.BCD16
                "BCD32" -> ModbusInputDecode.BCD32
                "Float32" -> ModbusInputDecode.FLOAT32
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

    fun loadPortConfig(config: PortConfig) {
        ensurePortInList(cmbComPort, config.etBtCommPort)
        cmbBaudRate.selectionModel.select(config.etBtBaudRate.toString().takeIf { it in baudRates } ?: "9600")
        cmbByteSize.selectionModel.select(config.etBtByteSize.toString().takeIf { it in byteSizes } ?: "8")
        cmbParity.selectionModel.select(config.etBtParity.takeIf { it in parityItems } ?: "N")
        cmbStopbits.selectionModel.select(config.etBtStopbits.toString().takeIf { it in stopbitsItems } ?: "1")
        txtTimeout.text = config.etBtTimeoutSec.toString()

        val extraRows = config.extraSerialDevices.map {
            ExtraSerialRow(it.device, it.commPort, it.baudRate, it.byteSize, it.parity, it.stopbits, it.timeout)
        }
        if (extraRows.isNotEmpty()) {
            portExtraTable.items.setAll(extraRows)
        }

        txtModbusSerialDelay.text = config.modbusAdvanced.serialDelayMs.toString()
        txtModbusSerialRetries.text = config.modbusAdvanced.serialRetries.toString()
        txtModbusIpTimeout.text = config.modbusAdvanced.ipTimeoutSec.toString()
        txtModbusIpRetries.text = config.modbusAdvanced.ipRetries.toString()
        chkModbusLittleEndian.isSelected = config.modbusAdvanced.littleEndian
        chkModbusLittleEndianWords.isSelected = config.modbusAdvanced.littleEndianWords
        chkModbusOptimize.isSelected = config.modbusAdvanced.optimize
        chkModbusFetchFullBlocks.isSelected = config.modbusAdvanced.fetchFullBlocks

        txtS7Host.text = config.s7.host
        txtS7Port.text = config.s7.port.toString()
        txtS7Rack.text = config.s7.rack.toString()
        txtS7Slot.text = config.s7.slot.toString()
        chkS7Optimize.isSelected = config.s7.optimize
        chkS7FetchFullBlocks.isSelected = config.s7.fetchFullBlocks
        val s7Rows = config.s7.inputs + List(12) { PortS7InputConfig() }
        for (i in 0 until 12) {
            val row = s7Rows[i]
            s7AreaCombos.getOrNull(i)?.selectionModel?.select(row.area.takeIf { it in s7AreaCombos[i].items } ?: "DB")
            s7DbEdits.getOrNull(i)?.text = row.db.toString()
            s7StartEdits.getOrNull(i)?.text = row.start.toString()
            s7TypeCombos.getOrNull(i)?.selectionModel?.select(row.type.takeIf { it in s7TypeCombos[i].items } ?: "Int")
            s7FactorCombos.getOrNull(i)?.selectionModel?.select(row.factor.takeIf { it in dividerItems } ?: "")
            s7ModeCombos.getOrNull(i)?.selectionModel?.select(row.mode.takeIf { it in modeItems } ?: "C")
        }
        txtS7PidDevice.text = config.s7.pid.device.toString()
        txtS7PidSv.text = config.s7.pid.sv.toString()
        txtS7PidP.text = config.s7.pid.p.toString()
        txtS7PidI.text = config.s7.pid.i.toString()
        txtS7PidD.text = config.s7.pid.d.toString()

        txtWsHost.text = config.websocket.host
        txtWsPort.text = config.websocket.port.toString()
        txtWsPath.text = config.websocket.path
        txtWsId.text = config.websocket.id
        txtWsTimeoutConnect.text = config.websocket.timeoutConnectSec
        txtWsTimeoutReconnect.text = config.websocket.timeoutReconnectSec
        txtWsTimeoutRequest.text = config.websocket.timeoutRequestSec
        txtWsNodeMessageId.text = config.websocket.nodeMessageId
        txtWsNodeMachineId.text = config.websocket.nodeMachineId
        txtWsNodeCommand.text = config.websocket.nodeCommand
        txtWsNodeData.text = config.websocket.nodeData
        txtWsNodeMessage.text = config.websocket.nodeMessage
        txtWsCommandDataRequest.text = config.websocket.commandDataRequest
        txtWsMessageCharge.text = config.websocket.messageCharge
        txtWsMessageDrop.text = config.websocket.messageDrop
        txtWsFlagStartOnCharge.text = config.websocket.flagStartOnCharge
        txtWsFlagOffOnDrop.text = config.websocket.flagOffOnDrop
        chkWsCompression.isSelected = config.websocket.compression
        txtWsEventEvent.text = config.websocket.eventEvent
        txtWsEventNode.text = config.websocket.eventNode
        txtWsEventDry.text = config.websocket.eventDry
        txtWsEventFcs.text = config.websocket.eventFcs
        txtWsEventFce.text = config.websocket.eventFce
        txtWsEventScs.text = config.websocket.eventScs
        txtWsEventSce.text = config.websocket.eventSce
        val wsRows = config.websocket.inputs + List(10) { PortWebSocketInputConfig() }
        for (i in 0 until 10) {
            val row = wsRows[i]
            wsInputRequestEdits.getOrNull(i)?.text = row.request
            wsInputNodeEdits.getOrNull(i)?.text = row.node
            wsInputModeCombos.getOrNull(i)?.selectionModel?.select(row.mode.takeIf { it == "C" || it == "F" } ?: "C")
        }
    }

    fun getPortConfig(): PortConfig {
        val extraRows = portExtraTable.items.map {
            PortExtraSerialDeviceConfig(
                device = it.deviceProperty.get(),
                commPort = it.commPortProperty.get(),
                baudRate = it.baudRateProperty.get(),
                byteSize = it.byteSizeProperty.get(),
                parity = it.parityProperty.get(),
                stopbits = it.stopbitsProperty.get(),
                timeout = it.timeoutProperty.get()
            )
        }.ifEmpty { listOf(PortExtraSerialDeviceConfig()) }

        val s7Inputs = (0 until 12).map { i ->
            PortS7InputConfig(
                area = s7AreaCombos.getOrNull(i)?.value ?: "DB",
                db = s7DbEdits.getOrNull(i)?.text?.toIntOrNull() ?: 1,
                start = s7StartEdits.getOrNull(i)?.text?.toIntOrNull() ?: 0,
                type = s7TypeCombos.getOrNull(i)?.value ?: "Int",
                factor = s7FactorCombos.getOrNull(i)?.value ?: "",
                mode = s7ModeCombos.getOrNull(i)?.value ?: "C"
            )
        }
        val wsInputs = (0 until 10).map { i ->
            PortWebSocketInputConfig(
                request = wsInputRequestEdits.getOrNull(i)?.text?.trim().orEmpty(),
                node = wsInputNodeEdits.getOrNull(i)?.text?.trim().orEmpty(),
                mode = wsInputModeCombos.getOrNull(i)?.value ?: "C"
            )
        }

        return PortConfig(
            etBtCommPort = getCommPort(),
            etBtBaudRate = getBaudRate(),
            etBtByteSize = getByteSize(),
            etBtParity = getParity(),
            etBtStopbits = getStopbits(),
            etBtTimeoutSec = getTimeout(),
            extraSerialDevices = extraRows,
            modbusAdvanced = PortModbusAdvancedConfig(
                serialDelayMs = txtModbusSerialDelay.text.toIntOrNull() ?: 0,
                serialRetries = txtModbusSerialRetries.text.toIntOrNull() ?: 0,
                ipTimeoutSec = getModbusIpTimeout(),
                ipRetries = getModbusIpRetries(),
                littleEndian = chkModbusLittleEndian.isSelected,
                littleEndianWords = chkModbusLittleEndianWords.isSelected,
                optimize = chkModbusOptimize.isSelected,
                fetchFullBlocks = chkModbusFetchFullBlocks.isSelected
            ),
            s7 = PortS7Config(
                host = txtS7Host.text?.trim().orEmpty().ifBlank { "127.0.0.1" },
                port = txtS7Port.text.toIntOrNull() ?: 102,
                rack = txtS7Rack.text.toIntOrNull() ?: 0,
                slot = txtS7Slot.text.toIntOrNull() ?: 1,
                optimize = chkS7Optimize.isSelected,
                fetchFullBlocks = chkS7FetchFullBlocks.isSelected,
                inputs = s7Inputs,
                pid = PortS7PidConfig(
                    device = txtS7PidDevice.text.toIntOrNull() ?: 0,
                    sv = txtS7PidSv.text.toIntOrNull() ?: 0,
                    p = txtS7PidP.text.toIntOrNull() ?: 0,
                    i = txtS7PidI.text.toIntOrNull() ?: 0,
                    d = txtS7PidD.text.toIntOrNull() ?: 0
                )
            ),
            websocket = PortWebSocketConfig(
                host = txtWsHost.text?.trim().orEmpty().ifBlank { "127.0.0.1" },
                port = txtWsPort.text.toIntOrNull() ?: 80,
                path = txtWsPath.text?.trim().orEmpty().ifBlank { "/" },
                id = txtWsId.text?.trim().orEmpty(),
                timeoutConnectSec = txtWsTimeoutConnect.text?.trim().orEmpty().ifBlank { "3" },
                timeoutReconnectSec = txtWsTimeoutReconnect.text?.trim().orEmpty().ifBlank { "3" },
                timeoutRequestSec = txtWsTimeoutRequest.text?.trim().orEmpty().ifBlank { "3" },
                nodeMessageId = txtWsNodeMessageId.text?.trim().orEmpty(),
                nodeMachineId = txtWsNodeMachineId.text?.trim().orEmpty(),
                nodeCommand = txtWsNodeCommand.text?.trim().orEmpty(),
                nodeData = txtWsNodeData.text?.trim().orEmpty(),
                nodeMessage = txtWsNodeMessage.text?.trim().orEmpty(),
                commandDataRequest = txtWsCommandDataRequest.text?.trim().orEmpty(),
                messageCharge = txtWsMessageCharge.text?.trim().orEmpty(),
                messageDrop = txtWsMessageDrop.text?.trim().orEmpty(),
                flagStartOnCharge = txtWsFlagStartOnCharge.text?.trim().orEmpty(),
                flagOffOnDrop = txtWsFlagOffOnDrop.text?.trim().orEmpty(),
                compression = chkWsCompression.isSelected,
                eventEvent = txtWsEventEvent.text?.trim().orEmpty(),
                eventNode = txtWsEventNode.text?.trim().orEmpty(),
                eventDry = txtWsEventDry.text?.trim().orEmpty(),
                eventFcs = txtWsEventFcs.text?.trim().orEmpty(),
                eventFce = txtWsEventFce.text?.trim().orEmpty(),
                eventScs = txtWsEventScs.text?.trim().orEmpty(),
                eventSce = txtWsEventSce.text?.trim().orEmpty(),
                inputs = wsInputs
            )
        )
    }

    fun getDeviceAssignmentConfig(): DeviceAssignmentConfig = DeviceAssignmentConfig(
        etBt = DeviceAssignmentEtBtConfig(
            etCurve = chkEtCurve.isSelected,
            btCurve = chkBtCurve.isSelected,
            etLcd = chkEtLcd.isSelected,
            btLcd = chkBtLcd.isSelected,
            swapEtBt = chkSwapEtBt.isSelected,
            logging = chkDeviceLogging.isSelected,
            control = chkDeviceControl.isSelected,
            meter = cmbEtBtMeter.value?.trim().orEmpty().ifBlank { "MODBUS" },
            pidControlType = cmbPidControlType.value?.trim().orEmpty().ifBlank { "Fuji PXG" },
            pidReadType = cmbPidReadType.value?.trim().orEmpty().ifBlank { "Fuji PXR" },
            pidControlUnitId = cmbPidControlUnitId.value?.toIntOrNull() ?: 1,
            pidReadUnitId = cmbPidReadUnitId.value?.toIntOrNull() ?: 2,
            pidDutyPowerLcds = chkPidDutyPowerLcds.isSelected,
            pidUseModbusPort = chkPidUseModbusPort.isSelected,
            tc4EtChannel = cmbTc4EtChannel.value?.toIntOrNull() ?: 1,
            tc4BtChannel = cmbTc4BtChannel.value?.toIntOrNull() ?: 2,
            tc4AtChannel = cmbTc4AtChannel.value?.trim().orEmpty().ifBlank { "None" },
            tc4PidFirmware = chkTc4PidFirmware.isSelected,
            progInputPath = txtProgInputPath.text?.trim().orEmpty(),
            progOutputEnabled = chkProgOutputEnabled.isSelected,
            progOutputPath = txtProgOutputPath.text?.trim().orEmpty()
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
            btChannel = txtPhidgetBtChannel.text.toIntOrNull()?.coerceIn(1, 4) ?: 2,
            tcTypes = listOf(cmbPhidgetType0, cmbPhidgetType1, cmbPhidgetType2, cmbPhidgetType3).map { it.value?.trim().orEmpty().ifBlank { "K" } },
            tcAsync = listOf(chkPhidgetAsync0, chkPhidgetAsync1, chkPhidgetAsync2, chkPhidgetAsync3).map { it.isSelected },
            tcChange = listOf(cmbPhidgetChange0, cmbPhidgetChange1, cmbPhidgetChange2, cmbPhidgetChange3).map { it.value?.removeSuffix("C")?.toDoubleOrNull() ?: 0.2 },
            tcRateMs = cmbPhidgetRate.value?.removeSuffix("ms")?.toIntOrNull() ?: 256,
            irAsync = chkPhidgetIrAsync.isSelected,
            irChange = cmbPhidgetIrChange.value?.removeSuffix("C")?.toDoubleOrNull() ?: 0.2,
            irRateMs = cmbPhidgetIrRate.value?.removeSuffix("ms")?.toIntOrNull() ?: 256,
            irEmissivity = txtPhidgetIrEmissivity.text.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 1.0,
            rtdTypeA = cmbPhidget1200TypeA.value?.trim().orEmpty().ifBlank { "PT100 3850" },
            rtdWireA = cmbPhidget1200WireA.value?.trim().orEmpty().ifBlank { "2-wire" },
            rtdAsyncA = chkPhidget1200AsyncA.isSelected,
            rtdChangeA = cmbPhidget1200ChangeA.value?.removeSuffix("C")?.toDoubleOrNull() ?: 0.2,
            rtdRateAms = cmbPhidget1200RateA.value?.removeSuffix("ms")?.toIntOrNull() ?: 250,
            rtdTypeB = cmbPhidget1200TypeB.value?.trim().orEmpty().ifBlank { "PT100 3850" },
            rtdWireB = cmbPhidget1200WireB.value?.trim().orEmpty().ifBlank { "2-wire" },
            rtdAsyncB = chkPhidget1200AsyncB.isSelected,
            rtdChangeB = cmbPhidget1200ChangeB.value?.removeSuffix("C")?.toDoubleOrNull() ?: 0.2,
            rtdRateBms = cmbPhidget1200RateB.value?.removeSuffix("ms")?.toIntOrNull() ?: 250,
            daq1400Power = cmbPhidget1400Power.value?.trim().orEmpty().ifBlank { "12V" },
            daq1400Mode = cmbPhidget1400Mode.value?.trim().orEmpty().ifBlank { "NPN" },
            rtd1046Gain = listOf(cmbPhidget1046Gain0, cmbPhidget1046Gain1, cmbPhidget1046Gain2, cmbPhidget1046Gain3).map { it.value?.trim().orEmpty().ifBlank { "1" } },
            rtd1046Wiring = listOf(cmbPhidget1046Wiring0, cmbPhidget1046Wiring1, cmbPhidget1046Wiring2, cmbPhidget1046Wiring3).map { it.value?.trim().orEmpty().ifBlank { "Div" } },
            rtd1046Async = listOf(chkPhidget1046Async0, chkPhidget1046Async1, chkPhidget1046Async2, chkPhidget1046Async3).map { it.isSelected },
            rtd1046RateMs = cmbPhidget1046Rate.value?.removeSuffix("ms")?.toIntOrNull() ?: 256,
            ioAsync = listOf(chkPhidgetIoAsync0, chkPhidgetIoAsync1, chkPhidgetIoAsync2, chkPhidgetIoAsync3, chkPhidgetIoAsync4, chkPhidgetIoAsync5, chkPhidgetIoAsync6, chkPhidgetIoAsync7).map { it.isSelected },
            ioChange = listOf(cmbPhidgetIoChange0, cmbPhidgetIoChange1, cmbPhidgetIoChange2, cmbPhidgetIoChange3, cmbPhidgetIoChange4, cmbPhidgetIoChange5, cmbPhidgetIoChange6, cmbPhidgetIoChange7).map { it.value?.trim().orEmpty().ifBlank { "100mV" } },
            ioRateMs = listOf(cmbPhidgetIoRate0, cmbPhidgetIoRate1, cmbPhidgetIoRate2, cmbPhidgetIoRate3, cmbPhidgetIoRate4, cmbPhidgetIoRate5, cmbPhidgetIoRate6, cmbPhidgetIoRate7).map { it.value?.removeSuffix("ms")?.toIntOrNull() ?: 256 },
            ioRange = listOf(cmbPhidgetIoRange0, cmbPhidgetIoRange1, cmbPhidgetIoRange2, cmbPhidgetIoRange3, cmbPhidgetIoRange4, cmbPhidgetIoRange5, cmbPhidgetIoRange6, cmbPhidgetIoRange7).map { it.value?.trim().orEmpty().ifBlank { "Auto" } },
            ioRatio = listOf(chkPhidgetIoRatio0, chkPhidgetIoRatio1, chkPhidgetIoRatio2, chkPhidgetIoRatio3, chkPhidgetIoRatio4, chkPhidgetIoRatio5, chkPhidgetIoRatio6, chkPhidgetIoRatio7).map { it.isSelected }
        ),
        yoctopuce = DeviceAssignmentYoctopuceConfig(
            remoteFlag = chkYoctoRemote.isSelected,
            virtualHub = txtYoctoVirtualHub.text?.trim().orEmpty(),
            emissivity = txtYoctoEmissivity.text.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 1.0,
            asyncEnabled = chkYoctoAsync.isSelected,
            asyncRateMs = cmbYoctoAsyncRate.value?.removeSuffix("ms")?.toIntOrNull() ?: 256
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
            santokerBluetooth = chkNetSantokerBluetooth.isSelected,
            santokerSerial = chkNetSantokerSerial.isSelected,
            santokerWifi = chkNetSantokerWifi.isSelected,
            santokerCharge = chkNetSantokerCharge.isSelected,
            santokerDry = chkNetSantokerDry.isSelected,
            santokerFcs = chkNetSantokerFcs.isSelected,
            santokerFce = chkNetSantokerFce.isSelected,
            santokerScs = chkNetSantokerScs.isSelected,
            santokerSce = chkNetSantokerSce.isSelected,
            santokerDrop = chkNetSantokerDrop.isSelected,
            kaleidoHost = txtNetKaleidoHost.text?.trim().orEmpty(),
            kaleidoPort = txtNetKaleidoPort.text.toIntOrNull() ?: 20002,
            kaleidoWifi = chkNetKaleidoWifi.isSelected,
            kaleidoCharge = chkNetKaleidoCharge.isSelected,
            kaleidoDry = chkNetKaleidoDry.isSelected,
            kaleidoFcs = chkNetKaleidoFcs.isSelected,
            kaleidoFce = chkNetKaleidoFce.isSelected,
            kaleidoScs = chkNetKaleidoScs.isSelected,
            kaleidoSce = chkNetKaleidoSce.isSelected,
            kaleidoDrop = chkNetKaleidoDrop.isSelected,
            mugmaHost = txtNetMugmaHost.text?.trim().orEmpty().ifBlank { "127.0.0.1" },
            mugmaPort = txtNetMugmaPort.text.toIntOrNull() ?: 1504,
            colorTrackMeanFilter = txtNetColorTrackMean.text.toIntOrNull() ?: 50,
            colorTrackMedianFilter = txtNetColorTrackMedian.text.toIntOrNull() ?: 50,
            shelly3emHost = txtNetShelly3emHost.text?.trim().orEmpty().ifBlank { "127.0.0.1" },
            shellyPlusPlugHost = txtNetShellyPlusHost.text?.trim().orEmpty().ifBlank { "127.0.0.1" },
            websocketHost = txtNetWsHost.text?.trim().orEmpty().ifBlank { "127.0.0.1" },
            websocketPort = txtNetWsPort.text.toIntOrNull() ?: 80
        ),
        batchManager = DeviceAssignmentBatchManagerConfig(
            scale1Model = cmbBatchScale1Model.value?.trim().orEmpty().ifBlank { "Acaia" },
            scale1Name = cmbBatchScale1Name.value?.trim().orEmpty(),
            scale2Model = cmbBatchScale2Model.value?.trim().orEmpty().ifBlank { "Acaia" },
            scale2Name = cmbBatchScale2Name.value?.trim().orEmpty(),
            containerGreen = cmbBatchContainerGreen.value?.trim().orEmpty(),
            containerRoasted = cmbBatchContainerRoasted.value?.trim().orEmpty(),
            containerAccuracyPct = txtBatchAccuracy.text.toDoubleOrNull()?.coerceIn(0.1, 100.0) ?: 10.0,
            taskDisplayGreenEnabled = chkBatchGreenPort.isSelected,
            taskDisplayGreenPort = txtBatchGreenPort.text.toIntOrNull() ?: 8081,
            taskDisplayRoastedEnabled = chkBatchRoastedPort.isSelected,
            taskDisplayRoastedPort = txtBatchRoastedPort.text.toIntOrNull() ?: 8082
        )
    )

    private fun encodeExtraDeviceRow(row: ExtraDeviceRow): String =
        listOf(
            row.deviceProperty.get(),
            row.color1Property.get(),
            row.color2Property.get(),
            row.label1Property.get(),
            row.label2Property.get(),
            row.y1Property.get(),
            row.y2Property.get(),
            row.lcd1Property.get(),
            row.lcd2Property.get(),
            row.curve1Property.get(),
            row.curve2Property.get(),
            row.deltaAxis1Property.get(),
            row.deltaAxis2Property.get(),
            row.fill1Property.get(),
            row.fill2Property.get()
        ).joinToString("|")

    private fun decodeExtraDeviceRow(raw: String): ExtraDeviceRow? {
        val parts = raw.split('|')
        if (parts.size < 7) return null
        // Backward compatibility with legacy 7-column row format.
        if (parts.size < 13) {
            return ExtraDeviceRow(
                device = parts[0],
                color1 = "black",
                color2 = "black",
                label1 = "Extra 1",
                label2 = "Extra 2",
                y1 = "",
                y2 = "",
                lcd1 = "",
                lcd2 = "",
                curve1 = "",
                curve2 = "",
                deltaAxis1 = "",
                deltaAxis2 = "",
                fill1 = "0",
                fill2 = "0"
            )
        }
        if (parts.size < 15) {
            return ExtraDeviceRow(
                device = parts[0],
                color1 = parts[1],
                color2 = parts[2],
                label1 = parts[3],
                label2 = parts[4],
                y1 = parts[5],
                y2 = parts[6],
                lcd1 = parts[7],
                lcd2 = parts[8],
                curve1 = parts[9],
                curve2 = parts[10],
                deltaAxis1 = parts[11],
                deltaAxis2 = parts[12],
                fill1 = "0",
                fill2 = "0"
            )
        }
        return ExtraDeviceRow(
            device = parts[0],
            color1 = parts[1],
            color2 = parts[2],
            label1 = parts[3],
            label2 = parts[4],
            y1 = parts[5],
            y2 = parts[6],
            lcd1 = parts[7],
            lcd2 = parts[8],
            curve1 = parts[9],
            curve2 = parts[10],
            deltaAxis1 = parts[11],
            deltaAxis2 = parts[12],
            fill1 = parts[13],
            fill2 = parts[14]
        )
    }
}
