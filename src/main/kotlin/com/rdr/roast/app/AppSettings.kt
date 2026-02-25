package com.rdr.roast.app

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

enum class MachineType { SIMULATOR, BESCA, DIEDRICH }

data class MachineConfig(
    val machineType: MachineType = MachineType.SIMULATOR,
    val port: String = "COM4",
    val baudRate: Int = 9600,
    val slaveId: Int = 1,
    val btRegister: Int = 45,
    val etRegister: Int = 46,
    val functionCode: Int = 3,
    val divisionFactor: Double = 1.0,
    val pollingIntervalMs: Long = 1000
)

data class AppSettings(
    val machineConfig: MachineConfig = MachineConfig(),
    val unit: String = "C",
    val savePath: String = System.getProperty("user.home") + "/roasts",
    /** Base URL of the roast server (e.g. https://artqqplus.ru/api/v1) for loading reference profiles. */
    val serverBaseUrl: String = "",
    /** Optional Bearer token for server API auth. */
    val serverToken: String = ""
)

object SettingsManager {
    private val mapper = ObjectMapper().registerKotlinModule()

    private fun settingsPath(): Path {
        val userHome = Paths.get(System.getProperty("user.home"))
        return userHome.resolve(".rdr").resolve("settings.json")
    }

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
        val path = settingsPath()
        Files.createDirectories(path.parent)
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), settings)
    }
}
