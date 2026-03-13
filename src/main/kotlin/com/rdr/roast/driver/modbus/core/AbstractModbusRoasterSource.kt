package com.rdr.roast.driver.modbus.core

import com.rdr.roast.app.MachineConfig
import com.rdr.roast.app.ModbusInputDecode
import com.rdr.roast.app.toRuntimeConfig
import com.rdr.roast.domain.TemperatureSample
import com.rdr.roast.driver.ConnectionState
import com.rdr.roast.driver.RoastDataSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory
import java.util.concurrent.Semaphore

/**
 * Shared sampler/orchestration for MODBUS-backed roasters.
 * Uses fixed poll interval and semaphore to prevent overlapping sample requests.
 */
abstract class AbstractModbusRoasterSource(
    protected val config: MachineConfig,
    private val connectedDeviceName: String,
    private val readInputRegisters: Boolean
) : RoastDataSource, ModbusCommandRunner {

    override fun writeSingle(deviceId: Int, register: Int, value: Int) = core.writeSingle(deviceId, register, value)
    override fun writeCoil(deviceId: Int, coil: Int, value: Boolean) = core.writeCoil(deviceId, coil, value)

    private val log = LoggerFactory.getLogger(javaClass)
    protected val core = ModbusConnectionCore(config.toRuntimeConfig())
    private val samplingSemaphore = Semaphore(1, true)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override fun connectionState(): StateFlow<ConnectionState> = _connectionState

    @Volatile
    protected var running = false

    override fun connect() {
        running = true
        _connectionState.value = ConnectionState.Connecting
        when (val state = core.connect()) {
            is ModbusCoreState.Connected -> _connectionState.value = ConnectionState.Connected(connectedDeviceName)
            is ModbusCoreState.Error -> _connectionState.value = ConnectionState.Error(state.message)
            is ModbusCoreState.Disconnected -> _connectionState.value = ConnectionState.Disconnected
        }
    }

    override fun disconnect() {
        running = false
        core.disconnect()
        _connectionState.value = ConnectionState.Disconnected
    }

    override fun sampleFlow(): Flow<TemperatureSample> = flow {
        var timeSec = 0.0
        val pollMs = config.pollingIntervalMs.coerceAtLeast(100L)
        while (running) {
            if (!core.isConnected()) {
                val reconnect = core.connect()
                if (reconnect !is ModbusCoreState.Connected) {
                    _connectionState.value = ConnectionState.Error((reconnect as? ModbusCoreState.Error)?.message ?: "Reconnect failed")
                    delay(500L)
                    continue
                }
                _connectionState.value = ConnectionState.Connected(connectedDeviceName)
            }

            if (!samplingSemaphore.tryAcquire()) {
                delay(10L)
                continue
            }
            try {
                val (bt, et) = readBtEtFromConfig()
                val extras = readExtraChannels()
                emit(TemperatureSample(timeSec = timeSec, bt = bt, et = et, extras = extras))
                timeSec += pollMs / 1000.0
            } catch (e: Exception) {
                val msg = e.message ?: "Read failed"
                log.warn("{} read error: {}", connectedDeviceName, msg)
                _connectionState.value = ConnectionState.Error(msg)
                delay(500L)
            } finally {
                samplingSemaphore.release()
            }
            delay(pollMs)
        }
    }

    /**
     * Reads BT and ET: when [MachineConfig.modbusInputs] channels 0 and 1 are configured (deviceId != 0),
     * uses those (Device, Register, Function, Divider, Decode). Otherwise uses legacy btRegister/etRegister/divisionFactor.
     */
    private fun readBtEtFromConfig(): Pair<Double, Double> {
        val inputs = config.modbusInputs
        val ch0 = inputs.getOrNull(0)
        val ch1 = inputs.getOrNull(1)
        val useCh0 = ch0 != null && ch0.deviceId != 0
        val useCh1 = ch1 != null && ch1.deviceId != 0
        if (useCh0 && useCh1 && decodeSupported(ch0.decode) && decodeSupported(ch1.decode)) {
            val bt = readChannelFromInput(ch0!!)
            val et = readChannelFromInput(ch1!!)
            return (bt ?: 0.0) to (et ?: 0.0)
        }
        // Legacy: single slave, consecutive registers
        val btReg = config.btRegister
        val etReg = config.etRegister
        val count = maxOf(1, (etReg - btReg).coerceAtLeast(0) + 1)
        val regs = if (readInputRegisters) core.readInput(config.slaveId, btReg, count) else core.readHolding(config.slaveId, btReg, count)
        val bt = (regs.getOrNull(0)?.value ?: 0) / config.divisionFactor
        val et = (regs.getOrNull((etReg - btReg).coerceAtLeast(0))?.value ?: 0) / config.divisionFactor
        return bt to et
    }

    /**
     * Reads extra sensor channels from modbusInputs[2..9].
     * Returns a map of (channel index → value) for channels that have deviceId != 0.
     * Channel indices map to ExtraSensorChannelConfig: modbusInputs[2]→ch 0, [3]→ch 1, etc.
     */
    private fun readExtraChannels(): Map<Int, Double> {
        val inputs = config.modbusInputs
        if (inputs.size <= 2) return emptyMap()
        val result = mutableMapOf<Int, Double>()
        for (i in 2 until inputs.size) {
            val ch = inputs[i]
            if (ch.deviceId == 0) continue
            if (!decodeSupported(ch.decode)) continue
            try {
                val value = readChannelFromInput(ch)
                if (value != null) {
                    result[i - 2] = value
                }
            } catch (e: Exception) {
                log.trace("Extra channel {} read failed: {}", i, e.message)
            }
        }
        return result
    }

    private fun decodeSupported(decode: com.rdr.roast.app.ModbusInputDecode): Boolean =
        decode == ModbusInputDecode.UINT16 || decode == ModbusInputDecode.SINT16

    /** Reads one channel from an input config (single register, UINT16/SINT16). Uses input.functionCode: 4 = input registers, else holding. */
    private fun readChannelFromInput(input: com.rdr.roast.app.ModbusInputConfig): Double? {
        val regs = if (input.functionCode == 4) core.readInput(input.deviceId, input.register, 1) else core.readHolding(input.deviceId, input.register, 1)
        val raw = regs.getOrNull(0)?.value ?: return null
        val value = when (input.decode) {
            ModbusInputDecode.SINT16 -> (raw.toShort().toInt()).toDouble()
            else -> raw.toDouble()
        }
        return value / input.divisionFactor()
    }
}

