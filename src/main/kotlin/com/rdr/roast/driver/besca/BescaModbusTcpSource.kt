package com.rdr.roast.driver.besca

import com.ghgande.j2mod.modbus.ModbusException
import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster
import com.ghgande.j2mod.modbus.procimg.SimpleRegister
import com.rdr.roast.app.MachineConfig
import com.rdr.roast.domain.TemperatureSample
import com.rdr.roast.driver.ConnectionState
import com.rdr.roast.driver.RoastControl
import com.rdr.roast.driver.RoastDataSource
import com.rdr.roast.driver.ControlSpec
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
/** Control IDs for Besca (Cropster-compatible register names). */
private const val CONTROL_GAS = "gas"
private const val CONTROL_AIRFLOW = "airflow"
private const val CONTROL_DRUM = "drum"

class BescaModbusTcpSource(private val config: MachineConfig) : RoastDataSource, RoastControl {

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

    // ── RoastControl ────────────────────────────────────────────────────────

    override fun supportsControl(): Boolean = config.gasRegister != 0 || config.airflowRegister != 0 || config.drumRegister != 0

    override fun controlSpecs(): List<ControlSpec> {
        val list = mutableListOf<ControlSpec>()
        if (config.gasRegister != 0) list.add(ControlSpec(CONTROL_GAS, ControlSpec.ControlType.SLIDER, "Gas", 0.0, 100.0, "%"))
        if (config.airflowRegister != 0) list.add(ControlSpec(CONTROL_AIRFLOW, ControlSpec.ControlType.SLIDER, "Airflow", 0.0, 100.0, "%"))
        if (config.drumRegister != 0) list.add(ControlSpec(CONTROL_DRUM, ControlSpec.ControlType.SLIDER, "Drum", 0.0, 100.0, "%"))
        return list
    }

    override fun setControl(id: String, value: Double) {
        val reg = when (id) {
            CONTROL_GAS -> config.gasRegister
            CONTROL_AIRFLOW -> config.airflowRegister
            CONTROL_DRUM -> config.drumRegister
            else -> 0
        }
        if (reg == 0) return
        val m = master ?: return
        if (!m.isConnected) return
        val intVal = value.coerceIn(0.0, 100.0).toInt().coerceIn(0, 65535)
        try {
            m.writeSingleRegister(config.slaveId, reg, SimpleRegister(intVal))
            log.trace("Besca TCP setControl {} = {}", id, intVal)
        } catch (e: ModbusException) {
            log.warn("Besca TCP write {} failed: {}", id, e.message)
        } catch (e: Exception) {
            log.warn("Besca TCP write {} failed: {}", id, e.message)
        }
    }
}
