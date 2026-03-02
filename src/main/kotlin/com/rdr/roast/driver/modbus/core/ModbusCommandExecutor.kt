package com.rdr.roast.driver.modbus.core

import org.slf4j.LoggerFactory

/**
 * Interface for executing Modbus write operations. Implemented by [ModbusConnectionCore]
 * and exposed by Modbus-backed data sources so event commands and custom buttons can run
 * write/wcoil/sleep command strings without depending on the core directly.
 */
interface ModbusCommandRunner {
    fun writeSingle(deviceId: Int, register: Int, value: Int)
    fun writeCoil(deviceId: Int, coil: Int, value: Boolean)
}

/**
 * Parses and executes Artisan-style Modbus command strings. Supports:
 * - write(slaveId, register, value) — single register write
 * - wcoil(slaveId, coil, 0|1) — single coil write (0=off, 1=on)
 * - sleep(seconds) — delay (supports decimals, e.g. sleep(1.5))
 *
 * Commands are separated by ";". Example:
 * "write(1,1008,2);sleep(15);wcoil(1,2005,1);write(1,1008,5)"
 *
 * Intended for use from event buttons (CHARGE, DROP, FC START, COOL END) and custom buttons (Agent 3).
 * Run on a background thread/coroutine when the string contains sleep() to avoid blocking the UI.
 */
object ModbusCommandExecutor {
    private val log = LoggerFactory.getLogger(ModbusCommandExecutor::class.java)

    private val writeRegex = Regex("""write\s*\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)""", RegexOption.IGNORE_CASE)
    private val wcoilRegex = Regex("""wcoil\s*\(\s*(\d+)\s*,\s*(\d+)\s*,\s*([01])\s*\)""", RegexOption.IGNORE_CASE)
    private val sleepRegex = Regex("""sleep\s*\(\s*([\d.]+)\s*\)""", RegexOption.IGNORE_CASE)

    /**
     * Execute a command string using the given runner. Splits by ";", trims each token,
     * and runs write/wcoil/sleep in order. Unknown tokens are skipped with a log.
     * Runs on the current thread — call from a background dispatcher if the string may contain sleep().
     */
    fun execute(runner: ModbusCommandRunner, commandString: String) {
        val trimmed = commandString.trim()
        if (trimmed.isEmpty()) return
        val tokens = trimmed.split(";").map { it.trim() }.filter { it.isNotEmpty() }
        for (token in tokens) {
            try {
                when {
                    writeRegex.matches(token) -> {
                        val (slaveId, reg, value) = writeRegex.find(token)!!.groupValues.drop(1).map { it.toInt() }
                        runner.writeSingle(slaveId, reg, value)
                        log.trace("Executed write({}, {}, {})", slaveId, reg, value)
                    }
                    wcoilRegex.matches(token) -> {
                        val groups = wcoilRegex.find(token)!!.groupValues
                        val slaveId = groups[1].toInt()
                        val coil = groups[2].toInt()
                        val value = groups[3] == "1"
                        runner.writeCoil(slaveId, coil, value)
                        log.trace("Executed wcoil({}, {}, {})", slaveId, coil, value)
                    }
                    sleepRegex.matches(token) -> {
                        val seconds = sleepRegex.find(token)!!.groupValues[1].toDouble().coerceIn(0.0, 3600.0)
                        val ms = (seconds * 1000).toLong()
                        Thread.sleep(ms)
                        log.trace("Executed sleep({})", seconds)
                    }
                    else -> log.debug("Unknown command token, skipping: {}", token)
                }
            } catch (e: Exception) {
                log.warn("Command failed: {} — {}", token, e.message)
                throw e
            }
        }
    }
}
