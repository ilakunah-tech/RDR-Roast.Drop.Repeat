package com.rdr.roast.app

import com.rdr.roast.domain.ControlEventType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

enum class RorSmoothing(val movingAvgWindow: Int, val rorWindowMs: Long) {
    SENSITIVE(5, 10_000L),
    RECOMMENDED(10, 18_000L),
    NOISE_RESISTANT(10, 30_000L)
}

enum class MachineType {
    SIMULATOR, BESCA, DIEDRICH,
    MODBUS_GENERIC, S7_GENERIC, SERIAL_GENERIC, WEBSOCKET_GENERIC
}

/** How the app connects to the roaster: serial (Modbus RTU), TCP (Modbus TCP), or Phidget USB. */
enum class Transport { SERIAL, TCP, PHIDGET }
enum class ModbusTransportType { SERIAL_RTU, SERIAL_ASCII, TCP, UDP }
enum class SerialParity { ODD, EVEN, NONE }

/** Artisan-style decode for a MODBUS input register. */
enum class ModbusInputDecode { UINT16, SINT16, UINT32, SINT32, BCD16, BCD32, FLOAT32 }

/** Artisan-style mode for temperature display: none, Celsius, Fahrenheit. */
enum class ModbusInputMode { NONE, C, F }

/** Single MODBUS input channel (Artisan Input 1–10): device, register, function, divider, mode, decode. */
data class ModbusInputConfig(
    val deviceId: Int = 0,
    val register: Int = 0,
    val functionCode: Int = 3,
    /** 0 = 1, 1 = 1/10, 2 = 1/100 */
    val dividerIndex: Int = 0,
    val mode: ModbusInputMode = ModbusInputMode.C,
    val decode: ModbusInputDecode = ModbusInputDecode.UINT16
) {
    fun divisionFactor(): Double = when (dividerIndex) {
        1 -> 10.0
        2 -> 100.0
        else -> 1.0
    }
}

/** Artisan-style MODBUS PID section: device, SV/P/I/D registers, ON/OFF command strings. */
data class ModbusPidConfig(
    val deviceId: Int = 0,
    val svRegister: Int = 0,
    val pRegister: Int = 0,
    val iRegister: Int = 0,
    val dRegister: Int = 0,
    val onCommand: String = "",
    val offCommand: String = ""
)

/** Default list of 10 MODBUS inputs (Artisan channels). */
fun defaultModbusInputs(): List<ModbusInputConfig> = List(10) { ModbusInputConfig() }

data class MachineConfig(
    val machineType: MachineType = MachineType.SIMULATOR,
    /** Transport: SERIAL for Modbus RTU, TCP for Modbus TCP (Besca over Ethernet), PHIDGET for Diedrich 1048. */
    val transport: Transport = Transport.SERIAL,
    /** Host for Modbus TCP (e.g. "10.0.0.9" or "127.0.0.1"). Ignored when transport != TCP. */
    val host: String? = null,
    /** Modbus TCP port (default 502). Used only when transport == TCP. */
    val tcpPort: Int = 502,
    /** Serial port (e.g. "COM4", "/dev/cu.usbserial-*"). Used when transport == SERIAL. */
    val port: String = "COM4",
    val baudRate: Int = 9600,
    /** Serial byte size. Artisan default: 8. */
    val byteSize: Int = 8,
    /** Serial parity. Artisan default: NONE. */
    val parity: SerialParity = SerialParity.NONE,
    /** Serial stop bits. Artisan default: 1. */
    val stopBits: Int = 1,
    /** Serial timeout in seconds. Artisan default ~0.4-0.5. */
    val serialTimeoutSec: Double = 0.5,
    /** MODBUS transport type (Artisan-style). */
    val modbusTransportType: ModbusTransportType = ModbusTransportType.SERIAL_RTU,
    /** MODBUS IP timeout in seconds for TCP/UDP. */
    val ipTimeoutSec: Double = 0.5,
    /** MODBUS retries for TCP/UDP. */
    val ipRetries: Int = 1,
    /** Disconnect connection when comm errors exceed threshold. */
    val disconnectOnError: Boolean = true,
    /** Allowed read/write errors before forced reconnect. */
    val acceptableErrors: Int = 3,
    val slaveId: Int = 1,
    /** Artisan Besca Full Auto defaults: Input1=reg6, Input2=reg7, Divider=1/10. */
    val btRegister: Int = 6,
    val etRegister: Int = 7,
    val functionCode: Int = 3,
    val divisionFactor: Double = 10.0,
    val pollingIntervalMs: Long = 1000,
    /** Phidget 1048: ET thermocouple channel (1-based). Used when transport == PHIDGET. */
    val phidgetEtChannel: Int = 1,
    /** Phidget 1048: BT thermocouple channel (1-based). Used when transport == PHIDGET. */
    val phidgetBtChannel: Int = 2,
    /** Modbus register for gas control (Besca Full Auto: 3904). 0 = no control. */
    val gasRegister: Int = 0,
    /** Modbus register for airflow control (Besca Full Auto: 1003). 0 = no control. */
    val airflowRegister: Int = 0,
    /** Modbus register for drum control (Besca Full Auto: 1001). 0 = no control. */
    val drumRegister: Int = 0,
    /** Artisan-style MODBUS inputs 1–10 (Device, Register, Function, Divider, Mode, Decode). */
    val modbusInputs: List<ModbusInputConfig> = defaultModbusInputs(),
    /** Artisan-style MODBUS PID (device, SV/P/I/D registers, ON/OFF commands). Null = not configured. */
    val modbusPid: ModbusPidConfig? = null,
    /**
     * Optional Modbus command strings for roast events. Keys: CHARGE, DROP, DRY_END, FC_START, COOL_END.
     * Format: "write(slaveId,register,value);wcoil(slaveId,coil,0|1);sleep(seconds);..."
     * Executed when the user triggers that event (e.g. C=Charge, D=Drop, or DE/FC from chart).
     */
    val eventCommands: Map<String, String> = emptyMap()
)

/** ET/BT section of Device Assignment dialog. */
data class DeviceAssignmentEtBtConfig(
    val etCurve: Boolean = true,
    val btCurve: Boolean = true,
    val etLcd: Boolean = true,
    val btLcd: Boolean = true,
    val swapEtBt: Boolean = false,
    val logging: Boolean = true,
    val control: Boolean = false,
    val meter: String = "MODBUS",
    val pidControlType: String = "Fuji PXG",
    val pidReadType: String = "Fuji PXR",
    val pidControlUnitId: Int = 1,
    val pidReadUnitId: Int = 2,
    val pidDutyPowerLcds: Boolean = true,
    val pidUseModbusPort: Boolean = false,
    val tc4EtChannel: Int = 1,
    val tc4BtChannel: Int = 2,
    val tc4AtChannel: String = "None",
    val tc4PidFirmware: Boolean = true,
    val progInputPath: String = "",
    val progOutputEnabled: Boolean = false,
    val progOutputPath: String = ""
)

/** Symbolic ET/BT formula strings from Device Assignment dialog. */
data class DeviceAssignmentSymbolicConfig(
    val etExpression: String = "",
    val btExpression: String = ""
)

/** Phidgets section from Device Assignment dialog. */
data class DeviceAssignmentPhidgetsConfig(
    val remoteFlag: Boolean = false,
    val serverId: String = "",
    val port: Int = 5661,
    val password: String = "",
    val remoteOnly: Boolean = false,
    val etChannel: Int = 1,
    val btChannel: Int = 2,
    val tcTypes: List<String> = listOf("K", "K", "K", "K"),
    val tcAsync: List<Boolean> = listOf(false, false, false, false),
    val tcChange: List<Double> = listOf(0.2, 0.2, 0.2, 0.2),
    val tcRateMs: Int = 256,
    val irAsync: Boolean = false,
    val irChange: Double = 0.2,
    val irRateMs: Int = 256,
    val irEmissivity: Double = 1.0,
    val rtdTypeA: String = "PT100 3850",
    val rtdWireA: String = "2-wire",
    val rtdAsyncA: Boolean = false,
    val rtdChangeA: Double = 0.2,
    val rtdRateAms: Int = 250,
    val rtdTypeB: String = "PT100 3850",
    val rtdWireB: String = "2-wire",
    val rtdAsyncB: Boolean = false,
    val rtdChangeB: Double = 0.2,
    val rtdRateBms: Int = 250,
    val daq1400Power: String = "12V",
    val daq1400Mode: String = "NPN",
    val rtd1046Gain: List<String> = listOf("1", "1", "1", "1"),
    val rtd1046Wiring: List<String> = listOf("Div", "Div", "Div", "Div"),
    val rtd1046Async: List<Boolean> = listOf(false, false, false, false),
    val rtd1046RateMs: Int = 256,
    val ioAsync: List<Boolean> = List(8) { false },
    val ioChange: List<String> = List(8) { "100mV" },
    val ioRateMs: List<Int> = List(8) { 256 },
    val ioRange: List<String> = List(8) { "Auto" },
    val ioRatio: List<Boolean> = List(8) { false }
)

/** Yoctopuce section from Device Assignment dialog. */
data class DeviceAssignmentYoctopuceConfig(
    val remoteFlag: Boolean = false,
    val virtualHub: String = "",
    val emissivity: Double = 1.0,
    val asyncEnabled: Boolean = false,
    val asyncRateMs: Int = 256
)

/** Ambient section from Device Assignment dialog. */
data class DeviceAssignmentAmbientConfig(
    val temperatureDeviceIndex: Int = 0,
    val humidityDeviceIndex: Int = 0,
    val pressureDeviceIndex: Int = 0,
    val temperatureSourceIndex: Int = 0,
    val humiditySourceIndex: Int = 0,
    val pressureSourceIndex: Int = 0,
    val elevationMeters: Int = 0
)

/** Networks section from Device Assignment dialog. */
data class DeviceAssignmentNetworksConfig(
    val s7Host: String = "127.0.0.1",
    val s7Port: Int = 102,
    val santokerHost: String = "",
    val santokerPort: Int = 20001,
    val santokerBluetooth: Boolean = false,
    val santokerSerial: Boolean = false,
    val santokerWifi: Boolean = true,
    val santokerCharge: Boolean = false,
    val santokerDry: Boolean = false,
    val santokerFcs: Boolean = false,
    val santokerFce: Boolean = false,
    val santokerScs: Boolean = false,
    val santokerSce: Boolean = false,
    val santokerDrop: Boolean = false,
    val kaleidoHost: String = "",
    val kaleidoPort: Int = 20002,
    val kaleidoWifi: Boolean = true,
    val kaleidoCharge: Boolean = false,
    val kaleidoDry: Boolean = false,
    val kaleidoFcs: Boolean = false,
    val kaleidoFce: Boolean = false,
    val kaleidoScs: Boolean = false,
    val kaleidoSce: Boolean = false,
    val kaleidoDrop: Boolean = false,
    val mugmaHost: String = "127.0.0.1",
    val mugmaPort: Int = 1504,
    val colorTrackMeanFilter: Int = 50,
    val colorTrackMedianFilter: Int = 50,
    val shelly3emHost: String = "127.0.0.1",
    val shellyPlusPlugHost: String = "127.0.0.1",
    val websocketHost: String = "127.0.0.1",
    val websocketPort: Int = 80
)

/** Batch Manager section from Device Assignment dialog. */
data class DeviceAssignmentBatchManagerConfig(
    val scale1Model: String = "Acaia",
    val scale1Name: String = "",
    val scale2Model: String = "Acaia",
    val scale2Name: String = "",
    val containerGreen: String = "",
    val containerRoasted: String = "",
    val containerAccuracyPct: Double = 10.0,
    val taskDisplayGreenEnabled: Boolean = false,
    val taskDisplayGreenPort: Int = 8081,
    val taskDisplayRoastedEnabled: Boolean = false,
    val taskDisplayRoastedPort: Int = 8082
)

/** Saved snapshot of Device Assignment dialog sections aligned with Artisan tabs. */
data class DeviceAssignmentConfig(
    val etBt: DeviceAssignmentEtBtConfig = DeviceAssignmentEtBtConfig(),
    val extraDevices: List<String> = emptyList(),
    val symbolic: DeviceAssignmentSymbolicConfig = DeviceAssignmentSymbolicConfig(),
    val phidgets: DeviceAssignmentPhidgetsConfig = DeviceAssignmentPhidgetsConfig(),
    val yoctopuce: DeviceAssignmentYoctopuceConfig = DeviceAssignmentYoctopuceConfig(),
    val ambient: DeviceAssignmentAmbientConfig = DeviceAssignmentAmbientConfig(),
    val networks: DeviceAssignmentNetworksConfig = DeviceAssignmentNetworksConfig(),
    val batchManager: DeviceAssignmentBatchManagerConfig = DeviceAssignmentBatchManagerConfig()
)

/** Port tab "Extra" row (serial params per extra device). */
data class PortExtraSerialDeviceConfig(
    val device: String = "Virtual",
    val commPort: String = "COM4",
    val baudRate: String = "115200",
    val byteSize: String = "8",
    val parity: String = "N",
    val stopbits: String = "1",
    val timeout: String = "1.0"
)

/** MODBUS-specific advanced options shown in Port -> Modbus. */
data class PortModbusAdvancedConfig(
    val serialDelayMs: Int = 0,
    val serialRetries: Int = 0,
    val ipTimeoutSec: Double = 0.4,
    val ipRetries: Int = 1,
    val littleEndian: Boolean = false,
    val littleEndianWords: Boolean = false,
    val optimize: Boolean = false,
    val fetchFullBlocks: Boolean = false
)

/** S7 input row shown in Port -> S7 grid (1..12). */
data class PortS7InputConfig(
    val area: String = "DB",
    val db: Int = 1,
    val start: Int = 0,
    val type: String = "Int",
    val factor: String = "",
    val mode: String = "C"
)

/** S7 PID block in Port -> S7. */
data class PortS7PidConfig(
    val device: Int = 0,
    val sv: Int = 0,
    val p: Int = 0,
    val i: Int = 0,
    val d: Int = 0
)

/** Full S7 settings from Port -> S7. */
data class PortS7Config(
    val host: String = "127.0.0.1",
    val port: Int = 102,
    val rack: Int = 0,
    val slot: Int = 1,
    val optimize: Boolean = false,
    val fetchFullBlocks: Boolean = false,
    val inputs: List<PortS7InputConfig> = List(12) { PortS7InputConfig() },
    val pid: PortS7PidConfig = PortS7PidConfig()
)

/** WebSocket input row shown in Port -> WebSocket grid (1..10). */
data class PortWebSocketInputConfig(
    val request: String = "",
    val node: String = "",
    val mode: String = "C"
)

/** Full WebSocket settings from Port -> WebSocket. */
data class PortWebSocketConfig(
    val host: String = "127.0.0.1",
    val port: Int = 80,
    val path: String = "/",
    val id: String = "",
    val timeoutConnectSec: String = "3",
    val timeoutReconnectSec: String = "3",
    val timeoutRequestSec: String = "3",
    val nodeMessageId: String = "",
    val nodeMachineId: String = "",
    val nodeCommand: String = "",
    val nodeData: String = "",
    val nodeMessage: String = "",
    val commandDataRequest: String = "",
    val messageCharge: String = "",
    val messageDrop: String = "",
    val flagStartOnCharge: String = "",
    val flagOffOnDrop: String = "",
    val compression: Boolean = false,
    val eventEvent: String = "",
    val eventNode: String = "",
    val eventDry: String = "",
    val eventFcs: String = "",
    val eventFce: String = "",
    val eventScs: String = "",
    val eventSce: String = "",
    val inputs: List<PortWebSocketInputConfig> = List(10) { PortWebSocketInputConfig() }
)

/** Snapshot of Port tab (ET/BT, Extra, Modbus, S7, WebSocket). */
data class PortConfig(
    val etBtCommPort: String = "COM4",
    val etBtBaudRate: Int = 9600,
    val etBtByteSize: Int = 8,
    val etBtParity: String = "N",
    val etBtStopbits: Int = 1,
    val etBtTimeoutSec: Double = 0.5,
    val extraSerialDevices: List<PortExtraSerialDeviceConfig> = listOf(PortExtraSerialDeviceConfig()),
    val modbusAdvanced: PortModbusAdvancedConfig = PortModbusAdvancedConfig(),
    val s7: PortS7Config = PortS7Config(),
    val websocket: PortWebSocketConfig = PortWebSocketConfig()
)

/** Source for event quantifier: ET (environment temp), BT (bean temp), or none. Aligns with Artisan quantifier source. */
enum class QuantifierSource { NONE, ET, BT }

/** Per-slider quantifier: source (ET/BT/None), set value, min, max, step, action enabled. Stored per event type (Air, Drum, Damper, Burner). */
data class EventQuantifierConfig(
    val source: QuantifierSource = QuantifierSource.NONE,
    val sv: Double = 0.0,
    val min: Int = 0,
    val max: Int = 100,
    val step: Double = 5.0,
    val actionEnabled: Boolean = false
)

/** Quantifiers for each slider event (Air, Drum, Damper, Burner). Used for range limits, step, and ET/BT-based hints. */
data class EventQuantifiersConfig(
    val air: EventQuantifierConfig = EventQuantifierConfig(),
    val drum: EventQuantifierConfig = EventQuantifierConfig(),
    val damper: EventQuantifierConfig = EventQuantifierConfig(),
    val burner: EventQuantifierConfig = EventQuantifierConfig()
) {
    fun get(type: ControlEventType): EventQuantifierConfig = when (type) {
        ControlEventType.AIR -> air
        ControlEventType.DRUM -> drum
        ControlEventType.DAMPER -> damper
        ControlEventType.GAS -> burner
        ControlEventType.BURNER -> burner
    }
}

/** Artisan Curves RoR tab: delta ET/BT, LCDs, projection, symbolic formulas. Each subagent extends this. */
data class CurvesRorConfig(
    val deltaET: Boolean = true,
    val deltaBT: Boolean = true,
    val deltaETspanSec: Int = 15,
    val deltaBTspanSec: Int = 15,
    val deltaETlcd: Boolean = true,
    val deltaBTlcd: Boolean = true,
    val swapdeltalcds: Boolean = false,
    val deltaETfunction: String = "",
    val deltaBTfunction: String = "",
    val etProjectFlag: Boolean = false,
    val btProjectFlag: Boolean = false,
    val projectDeltaFlag: Boolean = false,
    val projectionMode: Int = 0
)

/** Artisan Curves Filters tab: input filter, curve filter, display filter, RoR filter. */
data class CurvesFiltersConfig(
    val interpolateDuplicates: Boolean = true,
    val dropDuplicatesLimit: Double = 0.1,
    val dropSpikes: Boolean = true,
    val swapETBT: Boolean = false,
    val minMaxLimits: Boolean = true,
    val filterDropOutTmin: Int = 10,
    val filterDropOutTmax: Int = 700,
    val curvefilter: Int = 1,
    val filterDropOuts: Boolean = true,
    val foregroundShowFullflag: Boolean = true,
    val interpolateDropsflag: Boolean = true,
    val deltaETfilter: Int = 15,
    val deltaBTfilter: Int = 15,
    val polyfitRoRcalc: Boolean = true,
    val optimalSmoothing: Boolean = false,
    val rorLimitFlag: Boolean = true,
    val rorLimitMin: Int = -95,
    val rorLimitMax: Int = 95
)

/** Artisan Curves Plotter tab: P1–P9 formulas, colors, background. */
data class CurvesPlotterConfig(
    val plotcurves: List<String> = List(9) { "" },
    val plotcurvecolor: List<String> = List(9) { "#000000" },
    val backgroundEqu: String = "",
    val etBtColor: String = "#000000"
)

/** Artisan Curves Math tab: interpolate, univariate, ln, exponent, polyfit. */
data class CurvesMathConfig(
    val interpShow: Boolean = false,
    val interpKind: String = "linear",
    val univarShow: Boolean = false,
    val lnShow: Boolean = false,
    val lnResult: String = "",
    val expShow: Boolean = false,
    val expPower: Int = 2,
    val expOffsetSec: Int = 180,
    val expResult: String = "",
    val polyfitShow: Boolean = false,
    val polyfitStartSec: Double = 0.0,
    val polyfitEndSec: Double = 0.0,
    val polyfitDeg: Int = 1,
    val polyfitC1: String = "ET",
    val polyfitC2: String = "BT",
    val polyfitResult: String = ""
)

/** Artisan Curves Analyze tab: curve fit window, analyze interval, thresholds. */
data class CurvesAnalyzeConfig(
    val curvefitstartchoice: Int = 0,
    val curvefitoffset: Int = 5,
    val analysisstartchoice: Int = 1,
    val analysisoffset: Int = 180,
    val segmentsamplesthreshold: Int = 3,
    val segmentdeltathreshold: Double = 0.6
)

/** Artisan Curves UI tab: path effects, style, font, resolution, decimals, rename ET/BT, logo. */
data class CurvesUiConfig(
    val pathEffects: Int = 1,
    val glow: Boolean = true,
    val graphStyle: Int = 0,
    val graphFont: Int = 0,
    val notifications: Boolean = true,
    val beep: Boolean = false,
    val graphResolutionPercent: Int = 100,
    val tempDecimals: Int = 1,
    val percentDecimals: Int = 1,
    val renameET: String = "ET",
    val renameBT: String = "BT",
    val logoImagePath: String = "",
    val logoOpacity: Double = 2.0,
    val hideLogoDuringRoast: Boolean = false,
    val webLcdPort: Int = 8080,
    val webLcdEnabled: Boolean = false,
    val alarmPopups: Boolean = false
)

/** Artisan Curves dialog: 6 tabs RoR, Filters, Plotter, Math, Analyze, UI. */
data class CurvesConfig(
    val ror: CurvesRorConfig = CurvesRorConfig(),
    val filters: CurvesFiltersConfig = CurvesFiltersConfig(),
    val plotter: CurvesPlotterConfig = CurvesPlotterConfig(),
    val math: CurvesMathConfig = CurvesMathConfig(),
    val analyze: CurvesAnalyzeConfig = CurvesAnalyzeConfig(),
    val ui: CurvesUiConfig = CurvesUiConfig()
)

/** Per-channel slider config (Artisan-style: register, min, max, factor, offset, step). Value sent to register = factor * sliderValue + offset. */
data class SliderChannelConfig(
    /** Modbus register address; 0 = use machine default from [MachineConfig]. */
    val register: Int = 0,
    val min: Double = 0.0,
    val max: Double = 100.0,
    val factor: Double = 1.0,
    val offset: Double = 0.0,
    val step: Double = 1.0
)

/** Slider panel layout: single column with Gas/Air/Drum toggle, or grid showing all. */
enum class SliderPanelLayoutMode { SINGLE_COLUMN_TOGGLE, GRID_ALL }

/** Configuration for control sliders (Gas/Air/Drum) step buttons and per-channel register/factor/offset. */
data class SliderStepConfig(
    val leftSteps: List<Int> = listOf(100, 90, 80, 70, 60, 50, 40, 30, 20, 10),
    val rightSteps: List<Int> = listOf(95, 85, 75, 65, 55, 45, 35, 25, 15, 5),
    val showGas: Boolean = true,
    val showAir: Boolean = true,
    val showDrum: Boolean = true,
    val min: Int = 0,
    val max: Int = 100,
    /** Per-channel config (Air, Drum, Burner). Null = use machine register and defaults. */
    val gasChannel: SliderChannelConfig? = null,
    val airChannel: SliderChannelConfig? = null,
    val drumChannel: SliderChannelConfig? = null
)

/** Single custom event button (Artisan-style extraevents). Action type e.g. "Modbus Command"; commandString run via ModbusCommandExecutor. */
data class CustomButtonConfig(
    val label: String = "",
    val description: String = "",
    /** Event type recorded by this button (Air/Drum/Damper/Burner/blank). */
    val eventType: String = "",
    /** Event value (1-100 in Artisan style). */
    val eventValue: Int = 0,
    /** Display type/action name, e.g. "Modbus Command". */
    val actionType: String = "Modbus Command",
    /** Command string for Modbus (write/wcoil/sleep). */
    val commandString: String = "",
    /** Optional documentation text shown in Events->Buttons table. */
    val documentation: String = "",
    val visibility: Boolean = true,
    val backgroundColor: String = "#e8e8e8",
    val textColor: String = "#333333"
)

enum class ButtonSize { TINY, SMALL, LARGE }

/** Extra UI options from Events -> Buttons dialog (Artisan-style, kept minimal). */
data class EventButtonsDialogConfig(
    val eventType1Label: String = "Air",
    val eventType2Label: String = "Drum",
    val eventType3Label: String = "Damper",
    val eventType4Label: String = "Burner",
    val eventButtonEnabled: Boolean = true,
    val showOnBt: Boolean = true,
    val annotations: Boolean = true,
    val phaseLines: Boolean = true,
    val timeGuide: Boolean = false,
    val defaultButtonAction: String = "Modbus Command",
    val defaultButtonCommand: String = "",
    val autoMarkCharge: Boolean = false,
    val autoMarkDryEnd: Boolean = false,
    val autoMarkFirstCrack: Boolean = false,
    val autoMarkDrop: Boolean = false,
    val autoMarkDryEndTemp: Double? = null,
    val autoMarkFirstCrackTemp: Double? = null,
    val maxButtonsPerRow: Int = 8,
    val buttonSize: ButtonSize = ButtonSize.SMALL,
    val colorPattern: Int = 0,
    val markLastPressed: Boolean = false,
    val tooltips: Boolean = true,
    val alternativeSliderLayout: Boolean = false,
    val keyboardControl: Boolean = false,
    val quantifiersCluster: Boolean = false
)

/** Per event slider row as shown in Events -> Buttons -> Sliders. */
data class EventSliderRowConfig(
    val command: String = "",
    val min: Double = 0.0,
    val max: Double = 100.0,
    val factor: Double = 1.0,
    val offset: Double = 0.0,
    val bernoulli: Boolean = false,
    val step: Double = 1.0,
    val temp: Boolean = false,
    val unit: String = ""
)

data class EventSlidersConfig(
    val air: EventSliderRowConfig = EventSliderRowConfig(),
    val drum: EventSliderRowConfig = EventSliderRowConfig(),
    val damper: EventSliderRowConfig = EventSliderRowConfig(),
    val burner: EventSliderRowConfig = EventSliderRowConfig()
)

/** Configuration for which events appear in the Comments panel. */
data class CommentsConfig(
    val showCharge: Boolean = true,
    val showColorChange: Boolean = true,
    val showFirstCrack: Boolean = true,
    val showDrop: Boolean = true,
    val showControlEvents: Boolean = true
)

/** Named connection preset (like Artisan .aset machine settings). */
data class ConnectionPreset(
    val name: String,
    val config: MachineConfig
)

/** Custom colours for chart curves (hex strings). Ref series use refAlpha for transparency. Artisan Colors: Profile + Background Profile + Opaqueness. */
data class ChartColors(
    val liveBt: String = "#FF6B35",
    val liveEt: String = "#F59E0B",
    val liveRorBt: String = "#06B6D4",
    val liveRorEt: String = "#0EA5E9",
    val refBt: String = "#FF6B35",
    val refEt: String = "#F59E0B",
    val refRorBt: String = "#7DD3FC",
    val refRorEt: String = "#A5F3FC",
    val refExtra1: String = "#008000",
    val refExtra2: String = "#403e3e",
    val refAlpha: Int = 80
)

/** Roast phases (Artisan Roast Phases dialog): Drying, Maillard, Finishing temperature bounds and LCD/watermark flags. */
data class RoastPhasesConfig(
    /** [dryMin, dryMax, maillardMax, finishMax] in Celsius. Artisan defaults: 150, 150, 200, 230. */
    val phasesCelsius: List<Int> = listOf(150, 150, 200, 230),
    /** Auto Adjusted: DRY/FC buttons update phase temps during roast. */
    val phasesButtonFlag: Boolean = true,
    /** From Background: set phases from background profile on load. */
    val phasesFromBackgroundFlag: Boolean = false,
    /** Show phase watermarks on chart. */
    val watermarksFlag: Boolean = true,
    /** Show phases LCDs during roast. */
    val phasesLCDflag: Boolean = true,
    /** Auto DRY: auto-mark DRY when BT reaches dry max. */
    val autoDRYflag: Boolean = false,
    /** Auto FCs: auto-mark FCs when BT reaches maillard max. */
    val autoFCsFlag: Boolean = false,
    /** Phases LCD mode per phase: 0=Time, 1=Percentage, 2=Temp. */
    val phasesLCDmodeL: List<Int> = listOf(1, 1, 1),
    /** Phases LCDs All: for finishing phase, show time/percentage/temp across all 3 LCDs. */
    val phasesLCDmodeAll: List<Boolean> = listOf(false, false, true)
) {
    fun dryMin(): Int = phasesCelsius.getOrElse(0) { 150 }
    fun dryMax(): Int = phasesCelsius.getOrElse(1) { 150 }
    fun maillardMax(): Int = phasesCelsius.getOrElse(2) { 200 }
    fun finishMax(): Int = phasesCelsius.getOrElse(3) { 230 }
}

/** Grid line style (Artisan Axes dialog). */
enum class GridStyle { SOLID, DASHED, DASHED_DOT, DOTTED }

/** Legend position (Artisan: upper right, upper left, etc.). Index 0 = none, 1 = upper right, 2 = upper left, ... */
enum class LegendLocation(val index: Int) {
    NONE(0),
    UPPER_RIGHT(1),
    UPPER_LEFT(2),
    LOWER_LEFT(3),
    LOWER_RIGHT(4),
    RIGHT(5),
    CENTER_LEFT(6),
    CENTER_RIGHT(7),
    LOWER_CENTER(8),
    UPPER_CENTER(9),
    CENTER(10);
    companion object {
        fun fromIndex(i: Int): LegendLocation = entries.getOrElse(i.coerceIn(0, 10)) { NONE }
    }
}

/** Chart axes, grid, and line widths. Artisan-style Axes: Time, Temperature, Legend, Grid, Δ Axis. */
data class ChartConfig(
    val tempMin: Double = 50.0,
    val tempMax: Double = 300.0,
    val rorMin: Double = -5.0,
    val rorMax: Double = 30.0,
    val timeRangeMin: Int = 15,
    // Time Axis (Artisan)
    val timeAxisAuto: Boolean = true,
    /** Auto mode: 0 = Roast, 1 = Roast/Record. Kept for Artisan Axes compatibility. */
    val timeAxisAutoMode: Int = 0,
    val timeAxisLock: Boolean = false,
    val timeAxisMin: Double = 0.0,
    val timeAxisMax: Double = 15.0,
    val timeAxisStepSec: Int = 60,
    val recordMinSec: Int = -30,
    val recordMaxSec: Int = 600,
    val timeAxisExpand: Boolean = true,
    // Temperature Axis
    val tempAxisStep: Double = 25.0,
    /** Optional 100% Event Step anchor value from Artisan Axes dialog. */
    val step100EventTemp: Double? = null,
    // Legend
    val legendLocation: LegendLocation = LegendLocation.NONE,
    // Grid
    val showGrid: Boolean = true,
    val gridStyle: GridStyle = GridStyle.SOLID,
    val gridWidth: Int = 1,
    val gridTime: Boolean = true,
    val gridTemp: Boolean = true,
    val gridOpaqueness: Double = 0.2,
    // Δ Axis (RoR / delta)
    val deltaAxisAuto: Boolean = false,
    val deltaET: Boolean = false,
    val deltaBT: Boolean = true,
    val deltaMin: Double = -5.0,
    val deltaMax: Double = 30.0,
    val deltaStep: Double = 5.0,
    // Visibility and line widths
    val showBt: Boolean = true,
    val showEt: Boolean = true,
    val showRorBt: Boolean = true,
    val showRorEt: Boolean = true,
    val showReferenceCurves: Boolean = true,
    val showReferenceEvents: Boolean = true,
    val showPhaseStrips: Boolean = true,
    val btLineWidth: Float = 2.0f,
    val etLineWidth: Float = 2.0f,
    val rorLineWidth: Float = 1.5f,
    val refLineWidth: Float = 1.5f,
    val backgroundColor: String = "#ffffff",
    val gridColor: String = "#d3d3d3",
    val axisLabelColor: String = "#17212B",
    val markerColor: String = "#7f7f7f",
    val markerLabelBackgroundColor: String = "#ffffff",
    val phaseDryingColor: String = "#add18f",
    val phaseMaillardColor: String = "#f5e094",
    val phaseDevelopmentColor: String = "#cead78",
    val phaseCoolingColor: String = "#bde0ee",
    val phaseLabelColor: String = "#111111",
    val fontFamily: String = "",
    val axisFontSize: Double = 11.0,
    val markerFontSize: Double = 11.0
)

data class PresetSliderConfig(
    val label: String = "",
    val command: String = "",
    val actionType: Int = 0,
    val min: Double = 0.0,
    val max: Double = 100.0,
    val factor: Double = 1.0,
    val offset: Double = 0.0,
    val visible: Boolean = true,
    val unit: String = "",
    val isTemp: Boolean = false
)

data class ExtraSensorChannelConfig(
    val deviceId: Int = 0,
    val label1: String = "",
    val label2: String = "",
    val color1: String = "black",
    val color2: String = "black",
    val curveVisible1: Boolean = true,
    val curveVisible2: Boolean = true,
    val lcdVisible1: Boolean = false,
    val lcdVisible2: Boolean = false,
    val mathExpression1: String = "",
    val mathExpression2: String = ""
)

data class AppSettings(
    val machineConfig: MachineConfig = MachineConfig(),
    val unit: String = "C",
    val savePath: String = System.getProperty("user.home") + "/roasts",
    /** Base URL of the roast server (e.g. https://artqqplus.ru/api/v1) for loading reference profiles. */
    val serverBaseUrl: String = "",
    /** Optional Bearer token for server API auth. Set after login. */
    val serverToken: String = "",
    /** Refresh token for renewing access (optional). */
    val serverRefreshToken: String = "",
    /** Email to pre-fill in login dialog when "Remember" was used. */
    val serverRememberEmail: String = "",
    /** Full dynamic UI theme configuration. */
    val themeSettings: ThemeSettings = ThemeSettings.rdrCoffeeDefault(),
    val chartColors: ChartColors = ChartColors(),
    val chartConfig: ChartConfig = ChartConfig(),
    /** Artisan Curves: RoR, Filters, Plotter, Math, Analyze, UI tabs. */
    val curvesConfig: CurvesConfig = CurvesConfig(),
    /** Roast Phases: Drying/Maillard/Finishing temps, Auto DRY/FCs, watermarks, phases LCDs (Artisan Roast Phases dialog). */
    val roastPhasesConfig: RoastPhasesConfig = RoastPhasesConfig(),
    /** Saved divider position for center|right SplitPane (0..1). */
    val layoutDividerCenterRight: Double? = null,
    /** Saved divider position for values|reference SplitPane (0..1). */
    val layoutDividerReferenceChannels: Double? = null,
    /** When true, Reference Comments block is collapsed (Cropster-style). */
    val referenceCommentsCollapsed: Boolean = false,
    /** When true, after Stop enter BBP phase and record BT/ET until next Start or Stop BBP (Cropster-style). */
    val betweenBatchProtocolEnabled: Boolean = true,
    /** When true, run roaster discovery on Connect and use detected config if found. */
    val autoDetectRoaster: Boolean = false,
    /** TCP hosts to scan for discovery (e.g. ["10.0.0.9"]). Null = use built-in default list. */
    val discoveryTcpHosts: List<String>? = null,
    /** Remember last successfully detected roaster and try it first on next Connect (skip full scan). */
    val rememberLastDetectedRoaster: Boolean = true,
    /** Last detected roaster config (used when rememberLastDetectedRoaster is true). */
    val lastDetectedConfig: MachineConfig? = null,
    // Roast properties (for aroast payload and Roast Properties dialog)
    val roastPropertiesTitle: String = "",
    val roastPropertiesReferenceId: String = "",
    val roastPropertiesStockId: String = "",
    val roastPropertiesBlendId: String = "",
    val roastPropertiesWeightInKg: Double = 0.0,
    val roastPropertiesWeightOutKg: Double = 0.0,
    val roastPropertiesBeansNotes: String = "",
    val sliderStepConfig: SliderStepConfig = SliderStepConfig(),
    val commentsConfig: CommentsConfig = CommentsConfig(),
    /** Per-event quantifiers (Source, SV, Min, Max, Step, Action) for Air, Drum, Damper, Burner. Used in slider/event logic. */
    val eventQuantifiers: EventQuantifiersConfig = EventQuantifiersConfig(),
    /** Custom buttons (label, Modbus command, visibility, color). Shown in main window; on click run command via ModbusCommandExecutor. */
    val customButtons: List<CustomButtonConfig> = emptyList(),
    /** Events -> Buttons dialog options and labels. */
    val eventButtonsConfig: EventButtonsDialogConfig = EventButtonsDialogConfig(),
    /** Events -> Buttons slider rows (includes Damper and extra UI columns). */
    val eventSliders: EventSlidersConfig = EventSlidersConfig(),
    /** Device Assignment dialog state (ET/BT, Extra, Symb ET/BT, Phidgets, Yoctopuce, Ambient, Networks). */
    val deviceAssignment: DeviceAssignmentConfig = DeviceAssignmentConfig(),
    /** Port tab state (ET/BT, Extra, Modbus, S7, WebSocket). */
    val portConfig: PortConfig = PortConfig(),
    /** Control slider panel layout: single-column toggle (one at a time) or grid (all visible). */
    val sliderPanelLayoutMode: SliderPanelLayoutMode = SliderPanelLayoutMode.GRID_ALL,
    /** When true, slider panel is shown in a detachable window instead of inline. */
    val sliderPanelDetached: Boolean = false,
    /** Last position of detached slider window (x, y). Null = use default. */
    val sliderPanelDetachedX: Double? = null,
    val sliderPanelDetachedY: Double? = null,
    /** When true, BBP comments anchor to BT value instead of time. */
    val bbpCommentByTemp: Boolean = false,
    /** Pre-fill gas value from reference BBP's last gas comment. */
    val bbpPreFillGas: Boolean = true,
    /** Pre-fill airflow value from reference BBP's last airflow comment. */
    val bbpPreFillAirflow: Boolean = true,
    /** Maximum BBP recording duration in seconds (default 15 min = 900). */
    val bbpMaxDurationSec: Int = 900,
    /** When true, show BBP duration exceeded alarm toast. */
    val bbpAlarmEnabled: Boolean = true,
    val rorSmoothing: RorSmoothing = RorSmoothing.RECOMMENDED,
    /** Preset-driven sliders from .aset file (dynamic count, labels, commands). */
    val presetSliders: List<PresetSliderConfig> = emptyList(),
    /** Extra sensor channels from .aset file. */
    val extraSensors: List<ExtraSensorChannelConfig> = emptyList(),
    /** Currently loaded machine preset brand. Empty = none. */
    val presetBrand: String = "",
    /** Currently loaded machine preset model. Empty = none. */
    val presetModel: String = ""
)

object SettingsManager {
    private val mapper = ObjectMapper().registerKotlinModule()

    private fun rdrDir(): Path {
        return Paths.get(System.getProperty("user.home")).resolve(".rdr")
    }

    private fun settingsPath(): Path = rdrDir().resolve("settings.json")

    private fun presetsPath(): Path = rdrDir().resolve("connection-presets.json")

    fun load(): AppSettings {
        val path = settingsPath()
        val raw = if (Files.exists(path)) {
            try {
                mapper.readValue(path.toFile(), AppSettings::class.java)
            } catch (e: Exception) {
                AppSettings()
            }
        } else {
            AppSettings()
        }
        return migratePresetLabels(raw)
    }

    private fun migratePresetLabels(settings: AppSettings): AppSettings {
        if (settings.presetBrand.isBlank() || settings.presetModel.isBlank()) return settings
        val needsSliderFix = settings.presetSliders.any { it.label.matches(Regex("^Slider \\d+$")) }
        val needsButtonFix = settings.customButtons.any { cfg ->
            val l = cfg.label
            l.contains("\\") || (l.contains("\n") && l.length < 4)
        }
        if (!needsSliderFix && !needsButtonFix) return settings
        return try {
            val entry = MachinePresetRegistry.loadIndex()
                .values.flatten()
                .find { it.brand == settings.presetBrand && it.model == settings.presetModel }
                ?: return settings
            val preset = MachinePresetRegistry.getPreset(entry)
            val migrated = MachinePresetApplier.apply(preset, settings.presetBrand, settings.presetModel, settings)
            save(migrated)
            migrated
        } catch (_: Exception) {
            settings
        }
    }

    fun save(settings: AppSettings) {
        Files.createDirectories(rdrDir())
        mapper.writerWithDefaultPrettyPrinter().writeValue(settingsPath().toFile(), settings)
    }

    fun loadPresets(): List<ConnectionPreset> {
        val path = presetsPath()
        if (!Files.exists(path)) return emptyList()
        return try {
            val listType = mapper.typeFactory.constructCollectionType(List::class.java, ConnectionPreset::class.java)
            mapper.readValue(path.toFile(), listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun savePresets(presets: List<ConnectionPreset>) {
        Files.createDirectories(rdrDir())
        mapper.writerWithDefaultPrettyPrinter().writeValue(presetsPath().toFile(), presets)
    }
}
