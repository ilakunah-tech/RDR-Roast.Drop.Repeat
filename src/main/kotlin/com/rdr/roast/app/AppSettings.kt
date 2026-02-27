package com.rdr.roast.app

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

enum class MachineType { SIMULATOR, BESCA, DIEDRICH }

/** How the app connects to the roaster: serial (Modbus RTU), TCP (Modbus TCP), or Phidget USB. */
enum class Transport { SERIAL, TCP, PHIDGET }

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
    val unit: String = "C",
    val savePath: String = System.getProperty("user.home") + "/roasts",
    /** Base URL of the roast server (e.g. https://artqqplus.ru/api/v1) for loading reference profiles. */
    val serverBaseUrl: String = "",
    /** Optional Bearer token for server API auth. */
    val serverToken: String = "",
    val chartColors: ChartColors = ChartColors(),
    val chartConfig: ChartConfig = ChartConfig(),
    /** Saved divider position for center|right SplitPane (0..1). */
    val layoutDividerCenterRight: Double? = null,
    /** Saved divider position for values|reference SplitPane (0..1). */
    val layoutDividerReferenceChannels: Double? = null
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
