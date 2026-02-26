package com.rdr.roast.app

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

enum class MachineType { SIMULATOR, BESCA, DIEDRICH }

/** How the app connects to the roaster: serial (Modbus RTU), TCP (Modbus TCP), or Phidget USB. */
enum class Transport { SERIAL, TCP, PHIDGET }

/** Serial port parameters for ET/BT (non-Modbus) and Modbus Serial. Artisan-style Ports → ET/BT. */
data class SerialPortParams(
    val port: String = "COM4",
    val baudRate: Int = 115200,
    val byteSize: Int = 8,
    val parity: String = "N",
    val stopbits: Int = 1,
    val timeout: Double = 1.0
)

/** Single Modbus input channel (Device, Register, Function, Divider, Mode, Decode). */
data class ModbusInput(
    val deviceId: Int = 0,
    val register: Int = 0,
    val functionCode: Int = 3,
    val divider: String = "1/10",
    val mode: String = "C",
    val decode: String = "uint16"
)

/** Modbus port config: serial/TCP, inputs 1–10, PID registers, commands. Artisan-style Ports → Modbus. */
data class ModbusPortConfig(
    val serial: SerialPortParams = SerialPortParams(),
    val type: Int = 0, // 0=Serial RTU, 3=TCP, 4=UDP
    val host: String = "127.0.0.1",
    val port: Int = 502,
    val inputs: List<ModbusInput> = List(10) { ModbusInput() },
    val pidDeviceId: Int = 1,
    val pidSv: Int = 20,
    val pidP: Int = 100,
    val pidI: Int = 150,
    val pidD: Int = 200,
    val pidOnCommand: String = "wcoil(1,2009,1)",
    val pidOffCommand: String = "wcoil(1,2009,0)",
    val littleEndian: Boolean = true,
    val optimize: Boolean = true,
    val fetchFullBlocks: Boolean = false,
    val serialDelay: Double = 0.5,
    val serialRetries: Int = 1,
    val ipTimeout: Double = 0.3,
    val ipRetries: Int = 1
)

/** S7 input channel (Area, DB#, Start, Type, Factor, Mode). */
data class S7Input(
    val area: Int = 0,
    val dbNr: Int = 1,
    val start: Int = 0,
    val type: String = "Int",
    val factor: String = "",
    val mode: String = ""
)

/** S7 port config. Artisan-style Ports → S7. */
data class S7PortConfig(
    val host: String = "127.0.0.1",
    val port: Int = 102,
    val rack: Int = 0,
    val slot: Int = 0,
    val inputs: List<S7Input> = List(12) { S7Input() },
    val pidArea: Int = 0,
    val pidDbNr: Int = 0,
    val pidSv: Int = 0,
    val pidP: Int = 0,
    val pidI: Int = 0,
    val pidD: Int = 0,
    val pidOnCommand: String = "",
    val pidOffCommand: String = "",
    val optimize: Boolean = true,
    val fetchFullBlocks: Boolean = false
)

/** WebSocket input (Request, Node, Mode). */
data class WebSocketInput(
    val request: String = "",
    val node: String = "",
    val mode: String = "-"
)

/** WebSocket port config. Artisan-style Ports → WebSocket. */
data class WebSocketPortConfig(
    val host: String = "127.0.0.1",
    val port: Int = 80,
    val path: String = "WebSocket",
    val connectTimeout: Double = 4.0,
    val reconnectInterval: Double = 2.0,
    val requestTimeout: Double = 0.5,
    val messageIdNode: String = "id",
    val machineIdNode: String = "roasterID",
    val commandNode: String = "command",
    val dataNode: String = "data",
    val pushMessageNode: String = "pushMessage",
    val dataRequestCommand: String = "getData",
    val chargeMessage: String = "startRoasting",
    val dropMessage: String = "endRoasting",
    val startOnCharge: Boolean = false,
    val offOnDrop: Boolean = false,
    val eventNode: String = "addEvent",
    val dryEvent: String = "colorChangeEvent",
    val fcsEvent: String = "firstCrackBeginningEvent",
    val fceEvent: String = "firstCrackEndEvent",
    val scsEvent: String = "secondCrackBeginningEvent",
    val sceEvent: String = "secondCrackEndEvent",
    val inputs: List<WebSocketInput> = List(10) { WebSocketInput() },
    val compression: Boolean = false
)

/** Device assignment: which curves/LCDs are active. Artisan-style Device Assignment. */
data class DeviceAssignment(
    val curveET: Boolean = true,
    val curveBT: Boolean = true,
    val lcdET: Boolean = true,
    val lcdBT: Boolean = true,
    val swapLcds: Boolean = false,
    val logging: Boolean = false
)

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
    val byteSize: Int = 8,
    val parity: String = "N",
    val stopbits: Int = 1,
    val timeout: Double = 1.0,
    val slaveId: Int = 1,
    val btRegister: Int = 45,
    val etRegister: Int = 46,
    val functionCode: Int = 3,
    val divisionFactor: Double = 1.0,
    val pollingIntervalMs: Long = 1000,
    /** Phidget 1048: ET thermocouple channel (1-based). Used when transport == PHIDGET. */
    val phidgetEtChannel: Int = 1,
    /** Phidget 1048: BT thermocouple channel (1-based). Used when transport == PHIDGET. */
    val phidgetBtChannel: Int = 2
)

/** Named connection preset (like Artisan .aset machine settings). */
data class ConnectionPreset(
    val name: String,
    val config: MachineConfig
)

/** Custom colours for chart curves (hex strings). Ref series use refAlpha for transparency. */
data class ChartColors(
    val liveBt: String = "#d4483b",
    val liveEt: String = "#3498db",
    val liveRorBt: String = "#2c3e50",
    val liveRorEt: String = "#95a5a6",
    val refBt: String = "#d4483b",
    val refEt: String = "#3498db",
    val refRorBt: String = "#888888",
    val refRorEt: String = "#bbbbbb",
    val refAlpha: Int = 80
)

/** Chart axes, grid, and line widths. */
data class ChartConfig(
    val tempMin: Double = 50.0,
    val tempMax: Double = 300.0,
    val rorMin: Double = -5.0,
    val rorMax: Double = 30.0,
    val timeRangeMin: Int = 15,
    val showGrid: Boolean = true,
    val btLineWidth: Float = 2.0f,
    val etLineWidth: Float = 2.0f,
    val rorLineWidth: Float = 1.5f,
    val refLineWidth: Float = 1.5f,
    val backgroundColor: String = "#ffffff",
    val gridColor: String = "#d3d3d3"
)

data class AppSettings(
    val machineConfig: MachineConfig = MachineConfig(),
    /** Device Assignment: curves ET/BT, LCDs, logging. */
    val deviceAssignment: DeviceAssignment = DeviceAssignment(),
    /** Serial params for ET/BT tab (non-Modbus devices). */
    val serialPortParams: SerialPortParams = SerialPortParams(),
    /** Modbus port config (Ports → Modbus tab). */
    val modbusPortConfig: ModbusPortConfig = ModbusPortConfig(),
    /** S7 port config (Ports → S7 tab). */
    val s7PortConfig: S7PortConfig = S7PortConfig(),
    /** WebSocket port config (Ports → WebSocket tab). */
    val wsPortConfig: WebSocketPortConfig = WebSocketPortConfig(),
    val unit: String = "C",
    val savePath: String = System.getProperty("user.home") + "/roasts",
    /** Base URL of the roast server (e.g. https://artqqplus.ru/api/v1) for loading reference profiles. */
    val serverBaseUrl: String = "",
    /** Optional Bearer token for server API auth. */
    val serverToken: String = "",
    val chartColors: ChartColors = ChartColors(),
    val chartConfig: ChartConfig = ChartConfig()
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
