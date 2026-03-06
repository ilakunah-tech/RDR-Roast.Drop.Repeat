package com.rdr.roast.driver.modbus.core

import com.ghgande.j2mod.modbus.ModbusException
import com.ghgande.j2mod.modbus.facade.ModbusSerialMaster
import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster
import com.ghgande.j2mod.modbus.procimg.InputRegister
import com.ghgande.j2mod.modbus.procimg.Register
import com.ghgande.j2mod.modbus.procimg.SimpleRegister
import com.ghgande.j2mod.modbus.util.SerialParameters
import com.rdr.roast.app.ConnectionRuntimeConfig
import com.rdr.roast.app.ModbusTransportType
import com.rdr.roast.app.SerialParity
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

sealed interface ModbusCoreState {
    data object Disconnected : ModbusCoreState
    data object Connected : ModbusCoreState
    data class Error(val message: String) : ModbusCoreState
}

/**
 * Artisan-style MODBUS core:
 * - transport switch (RTU/ASCII/TCP/UDP)
 * - timeout/retry handling
 * - comm error threshold with optional forced disconnect
 */
class ModbusConnectionCore(
    private val config: ConnectionRuntimeConfig
) : ModbusCommandRunner {
    private val connectExecutor = Executors.newSingleThreadExecutor()
    private val ioSemaphore = Semaphore(1, true)

    @Volatile
    private var serialMaster: ModbusSerialMaster? = null

    @Volatile
    private var tcpMaster: ModbusTCPMaster? = null

    @Volatile
    private var commErrorCount = 0

    fun connect(): ModbusCoreState {
        return try {
            val timeoutMs = when (config.transportType) {
                ModbusTransportType.TCP, ModbusTransportType.UDP -> (config.ipTimeoutSec * 1000.0).toLong().coerceAtLeast(500L)
                else -> (config.serialTimeoutSec * 1000.0).toLong().coerceAtLeast(500L)
            }
            val future = connectExecutor.submit {
                when (config.transportType) {
                    ModbusTransportType.TCP, ModbusTransportType.UDP -> {
                        val host = config.host?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("Host is required for TCP/UDP")
                        ModbusTCPMaster(host, config.port, timeoutMs.toInt(), false).also { it.connect() }.also { tcpMaster = it }
                    }
                    ModbusTransportType.SERIAL_RTU, ModbusTransportType.SERIAL_ASCII -> {
                        val params = SerialParameters().apply {
                            portName = config.serialPort
                            baudRate = config.baudRate
                            databits = config.byteSize
                            stopbits = config.stopBits
                            parity = when (config.parity) {
                                SerialParity.NONE -> 0
                                SerialParity.ODD -> 1
                                SerialParity.EVEN -> 2
                            }
                            encoding = if (config.transportType == ModbusTransportType.SERIAL_ASCII) "ascii" else "rtu"
                        }
                        ModbusSerialMaster(params, timeoutMs.toInt()).also {
                            it.connect()
                            it.setTimeout(timeoutMs.toInt())
                        }.also { serialMaster = it }
                    }
                }
            }
            future.get(timeoutMs + 5000L, TimeUnit.MILLISECONDS)
            commErrorCount = 0
            ModbusCoreState.Connected
        } catch (e: Exception) {
            val msg = e.cause?.message ?: e.message ?: "Connection failed"
            disconnect()
            ModbusCoreState.Error(msg)
        }
    }

    fun disconnect() {
        try {
            serialMaster?.disconnect()
        } catch (_: Exception) { }
        try {
            tcpMaster?.disconnect()
        } catch (_: Exception) { }
        serialMaster = null
        tcpMaster = null
    }

    fun isConnected(): Boolean {
        return (serialMaster?.isConnected == true) || (tcpMaster?.isConnected == true)
    }

    fun readHolding(deviceId: Int, register: Int, count: Int): List<Register> {
        return guardedRead {
            val serial = serialMaster
            val tcp = tcpMaster
            when {
                serial != null && serial.isConnected -> serial.readMultipleRegisters(deviceId, register, count).toList()
                tcp != null && tcp.isConnected -> tcp.readMultipleRegisters(deviceId, register, count).toList()
                else -> throw IllegalStateException("Not connected")
            }
        }
    }

    fun readInput(deviceId: Int, register: Int, count: Int): List<InputRegister> {
        return guardedRead {
            val serial = serialMaster
            when {
                serial != null && serial.isConnected -> serial.readInputRegisters(deviceId, register, count).toList()
                else -> throw IllegalStateException("Input registers supported only on serial source in current implementation")
            }
        }
    }

    override fun writeSingle(deviceId: Int, register: Int, value: Int) {
        guardedWrite {
            val reg = SimpleRegister(value.coerceIn(0, 65535))
            val serial = serialMaster
            val tcp = tcpMaster
            when {
                serial != null && serial.isConnected -> serial.writeSingleRegister(deviceId, register, reg)
                tcp != null && tcp.isConnected -> tcp.writeSingleRegister(deviceId, register, reg)
                else -> throw IllegalStateException("Not connected")
            }
        }
    }

    /** Write a single coil (Modbus function 0x05). [coil] is the coil address, [value] is on (true) or off (false). */
    override fun writeCoil(deviceId: Int, coil: Int, value: Boolean) {
        guardedWrite {
            val serial = serialMaster
            val tcp = tcpMaster
            when {
                serial != null && serial.isConnected -> serial.writeCoil(deviceId, coil, value)
                tcp != null && tcp.isConnected -> tcp.writeCoil(deviceId, coil, value)
                else -> throw IllegalStateException("Not connected")
            }
        }
    }

    private fun <T> guardedRead(call: () -> T): T {
        return guardedIo(call)
    }

    private fun guardedWrite(call: () -> Unit) {
        guardedIo(call)
    }

    private fun <T> guardedIo(call: () -> T): T {
        ioSemaphore.acquire()
        try {
            var lastError: Exception? = null
            val attempts = (config.ipRetries + 1).coerceAtLeast(1)
            repeat(attempts) {
                try {
                    val result = call()
                    commErrorCount = 0
                    return result
                } catch (e: ModbusException) {
                    lastError = e
                    commErrorCount++
                } catch (e: Exception) {
                    lastError = e
                    commErrorCount++
                }
            }
            if (config.disconnectOnError && commErrorCount > config.acceptableErrors) {
                disconnect()
            }
            throw lastError ?: IllegalStateException("MODBUS IO failed")
        } finally {
            ioSemaphore.release()
        }
    }
}

