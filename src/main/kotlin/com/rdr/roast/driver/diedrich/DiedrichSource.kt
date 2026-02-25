package com.rdr.roast.driver.diedrich

import com.ghgande.j2mod.modbus.ModbusException
import com.ghgande.j2mod.modbus.facade.ModbusSerialMaster
import com.ghgande.j2mod.modbus.util.SerialParameters
import com.rdr.roast.app.MachineConfig
import com.rdr.roast.domain.TemperatureSample
import com.rdr.roast.driver.ConnectionState
import com.rdr.roast.driver.RoastDataSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val CONNECTION_TIMEOUT_MS = 5000L
private const val READ_TIMEOUT_MS = 3000
private const val RECONNECT_DELAY_MS = 5000L

/**
 * MODBUS RTU data source for Diedrich roasters.
 * Uses function code 4 (Read Input Registers) for bean and environment temperature.
 */
class DiedrichSource(private val config: MachineConfig) : RoastDataSource {

    private val log = LoggerFactory.getLogger(DiedrichSource::class.java)
    private val executor = Executors.newSingleThreadExecutor()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override fun connectionState(): StateFlow<ConnectionState> = _connectionState

    @Volatile
    private var master: ModbusSerialMaster? = null

    @Volatile
    private var running = false

    override fun connect() {
        running = true
        _connectionState.value = ConnectionState.Connecting
        log.info("Diedrich connecting to {} at {} baud", config.port, config.baudRate)

        try {
            val future = executor.submit {
                val params = SerialParameters().apply {
                    portName = config.port
                    baudRate = config.baudRate
                    databits = 8
                    stopbits = 1
                    parity = 0
                    encoding = "rtu"
                }
                ModbusSerialMaster(params, READ_TIMEOUT_MS).apply {
                    connect()
                    setTimeout(READ_TIMEOUT_MS)
                }
            }
            val m = future.get(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS) as ModbusSerialMaster
            master = m
            _connectionState.value = ConnectionState.Connected("Diedrich")
            log.info("Diedrich connected successfully")
        } catch (e: Exception) {
            log.error("Diedrich connect failed: {}", e.message)
            _connectionState.value = ConnectionState.Error(e.message ?: "Connection failed")
            master = null
        }
    }

    override fun disconnect() {
        running = false
        try {
            master?.disconnect()
            master = null
        } catch (e: Exception) {
            log.warn("Diedrich disconnect error: {}", e.message)
        }
        _connectionState.value = ConnectionState.Disconnected
        log.info("Diedrich disconnected")
    }

    override fun sampleFlow(): Flow<TemperatureSample> = flow {
        var timeSec = 0.0
        while (running) {
            val m = master
            if (m == null || !m.isConnected) {
                if (running) {
                    _connectionState.value = ConnectionState.Error("Not connected")
                    log.warn("Diedrich read skipped: not connected")
                    delay(RECONNECT_DELAY_MS)
                }
                continue
            }

            try {
                val btReg = config.btRegister
                val etReg = config.etRegister
                val count = maxOf(1, (etReg - btReg).coerceAtLeast(0) + 1)
                val registers = m.readInputRegisters(config.slaveId, btReg, count)
                val bt = (registers.getOrNull(0)?.value ?: 0) / config.divisionFactor
                val et = (registers.getOrNull((etReg - btReg).coerceAtLeast(0))?.value ?: 0) / config.divisionFactor
                emit(TemperatureSample(timeSec = timeSec, bt = bt, et = et))
                timeSec += config.pollingIntervalMs / 1000.0
            } catch (e: ModbusException) {
                log.warn("Diedrich read error: {}", e.message)
                _connectionState.value = ConnectionState.Error(e.message ?: "Read failed")
                delay(RECONNECT_DELAY_MS)
                continue
            } catch (e: Exception) {
                log.warn("Diedrich read error: {}", e.message)
                _connectionState.value = ConnectionState.Error(e.message ?: "Read failed")
                delay(RECONNECT_DELAY_MS)
                continue
            }

            delay(config.pollingIntervalMs)
        }
    }
}
