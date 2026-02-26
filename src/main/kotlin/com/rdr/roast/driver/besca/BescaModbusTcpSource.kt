package com.rdr.roast.driver.besca

import com.ghgande.j2mod.modbus.ModbusException
import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster
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
 * Modbus TCP data source for Besca roasters over Ethernet.
 * Uses same register/division semantics as [BescaModbusSource]; default registers 6/7 and division 10 match Cropster Besca Auto.
 */
class BescaModbusTcpSource(private val config: MachineConfig) : RoastDataSource {

    private val log = LoggerFactory.getLogger(BescaModbusTcpSource::class.java)
    private val executor = Executors.newSingleThreadExecutor()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override fun connectionState(): StateFlow<ConnectionState> = _connectionState

    @Volatile
    private var master: ModbusTCPMaster? = null

    @Volatile
    private var running = false

    override fun connect() {
        val host = config.host?.takeIf { it.isNotBlank() } ?: run {
            _connectionState.value = ConnectionState.Error("Host is required for TCP")
            return
        }
        running = true
        _connectionState.value = ConnectionState.Connecting
        log.info("Besca TCP connecting to {}:{}", host, config.tcpPort)

        try {
            val future = executor.submit {
                ModbusTCPMaster(host, config.tcpPort, READ_TIMEOUT_MS, false).apply {
                    connect()
                }
            }
            val m = future.get(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS) as ModbusTCPMaster
            master = m
            _connectionState.value = ConnectionState.Connected("Besca TCP")
            log.info("Besca TCP connected successfully")
        } catch (e: Exception) {
            log.error("Besca TCP connect failed: {}", e.message)
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
            log.warn("Besca TCP disconnect error: {}", e.message)
        }
        _connectionState.value = ConnectionState.Disconnected
        log.info("Besca TCP disconnected")
    }

    override fun sampleFlow(): Flow<TemperatureSample> = flow {
        var timeSec = 0.0
        while (running) {
            val m = master
            if (m == null || !m.isConnected) {
                if (running) {
                    _connectionState.value = ConnectionState.Error("Not connected")
                    log.warn("Besca TCP read skipped: not connected")
                    delay(RECONNECT_DELAY_MS)
                }
                continue
            }

            try {
                val btReg = config.btRegister
                val etReg = config.etRegister
                val count = maxOf(1, (etReg - btReg).coerceAtLeast(0) + 1)
                val registers = m.readMultipleRegisters(config.slaveId, btReg, count)
                val bt = (registers.getOrNull(0)?.value ?: 0) / config.divisionFactor
                val et = (registers.getOrNull((etReg - btReg).coerceAtLeast(0))?.value ?: 0) / config.divisionFactor
                emit(TemperatureSample(timeSec = timeSec, bt = bt, et = et))
                timeSec += config.pollingIntervalMs / 1000.0
            } catch (e: ModbusException) {
                log.warn("Besca TCP read error: {}", e.message)
                _connectionState.value = ConnectionState.Error(e.message ?: "Read failed")
                delay(RECONNECT_DELAY_MS)
                continue
            } catch (e: Exception) {
                log.warn("Besca TCP read error: {}", e.message)
                _connectionState.value = ConnectionState.Error(e.message ?: "Read failed")
                delay(RECONNECT_DELAY_MS)
                continue
            }

            delay(config.pollingIntervalMs)
        }
    }
}
