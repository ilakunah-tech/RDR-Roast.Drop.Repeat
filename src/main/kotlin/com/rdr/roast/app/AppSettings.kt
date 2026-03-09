package com.rdr.roast.app

import com.rdr.roast.domain.ControlEventType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

enum class MachineType { SIMULATOR, BESCA, DIEDRICH }

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
    val control: Boolean = false
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
    val btChannel: Int = 2
)

/** Yoctopuce section from Device Assignment dialog. */
data class DeviceAssignmentYoctopuceConfig(
    val remoteFlag: Boolean = false,
    val virtualHub: String = "",
    val emissivity: Double = 1.0
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
    val kaleidoHost: String = "",
    val kaleidoPort: Int = 20002,
    val websocketHost: String = "127.0.0.1",
    val websocketPort: Int = 80
)

/** Saved snapshot of Device Assignment dialog sections aligned with Artisan tabs. */
data class DeviceAssignmentConfig(
    val etBt: DeviceAssignmentEtBtConfig = DeviceAssignmentEtBtConfig(),
    val extraDevices: List<String> = emptyList(),
    val symbolic: DeviceAssignmentSymbolicConfig = DeviceAssignmentSymbolicConfig(),
    val phidgets: DeviceAssignmentPhidgetsConfig = DeviceAssignmentPhidgetsConfig(),
    val yoctopuce: DeviceAssignmentYoctopuceConfig = DeviceAssignmentYoctopuceConfig(),
    val ambient: DeviceAssignmentAmbientConfig = DeviceAssignmentAmbientConfig(),
    val networks: DeviceAssignmentNetworksConfig = DeviceAssignmentNetworksConfig()
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
    }
}

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

/** Custom colours for chart curves (hex strings). Ref series use refAlpha for transparency. */
data class ChartColors(
    val liveBt: String = "#FF6B35",
    val liveEt: String = "#F59E0B",
    val liveRorBt: String = "#06B6D4",
    val liveRorEt: String = "#0EA5E9",
    val refBt: String = "#FF6B35",
    val refEt: String = "#F59E0B",
    val refRorBt: String = "#7DD3FC",
    val refRorEt: String = "#A5F3FC",
    val refAlpha: Int = 80
)

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
    val gridColor: String = "#d3d3d3"
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
    /** Control slider panel layout: single-column toggle (one at a time) or grid (all visible). */
    val sliderPanelLayoutMode: SliderPanelLayoutMode = SliderPanelLayoutMode.SINGLE_COLUMN_TOGGLE,
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
    val bbpAlarmEnabled: Boolean = true
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
        return if (Files.exists(path)) {
            try {
                mapper.readValue(path.toFile(), AppSettings::class.java)
            } catch (e: Exception) {
                AppSettings()
            }
        } else {
            AppSettings()
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
