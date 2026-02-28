package com.rdr.roast.app

import com.rdr.roast.driver.ConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory

/**
 * Result of auto-detecting a roaster (type, transport, and config to use).
 */
data class DetectedRoaster(
    val machineType: MachineType,
    val transport: Transport,
    val config: MachineConfig
) {
    fun displayLabel(): String = when (transport) {
        Transport.TCP -> "Besca TCP (${config.host ?: "?"}:${config.tcpPort})"
        Transport.SERIAL -> "Besca (${config.port})"
        Transport.PHIDGET -> "Diedrich Phidget"
    }
}

/**
 * Discovers roasters by probing TCP addresses and serial ports.
 * Use from a background dispatcher (e.g. Dispatchers.Default).
 */
object RoasterDiscovery {

    private const val PROBE_TIMEOUT_MS = 2500L
    val defaultTcpHosts: List<String> = listOf("10.0.0.9", "192.168.1.100", "127.0.0.1")
    private val log = LoggerFactory.getLogger(RoasterDiscovery::class.java)

    /**
     * Runs discovery: tries TCP hosts then serial ports.
     * @param tcpHosts list of IP/hostnames to try for Modbus TCP (default: 10.0.0.9, 192.168.1.100, 127.0.0.1)
     * @return list of detected roasters (may be empty)
     */
    suspend fun discover(tcpHosts: List<String>? = null): List<DetectedRoaster> = withContext(Dispatchers.Default) {
        val hosts = (tcpHosts?.takeIf { it.isNotEmpty() } ?: defaultTcpHosts).distinct()
        val found = mutableListOf<DetectedRoaster>()

        for (host in hosts) {
            if (host.isBlank()) continue
            val config = MachineConfig(
                machineType = MachineType.BESCA,
                transport = Transport.TCP,
                host = host,
                tcpPort = 502,
                slaveId = 1,
                btRegister = 45,
                etRegister = 46,
                divisionFactor = 10.0,
                pollingIntervalMs = 1000
            )
            val result = withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                ConnectionTester.test(config)
            }
            if (result != null && result.isSuccess && result.getOrNull() is ConnectionState.Connected) {
                found.add(DetectedRoaster(MachineType.BESCA, Transport.TCP, config))
                log.info("Discovery: found Besca TCP at {}", host)
            }
        }

        val serialPorts = com.fazecast.jSerialComm.SerialPort.getCommPorts().map { it.systemPortName }.distinct()
        for (portName in serialPorts) {
            val config = MachineConfig(
                machineType = MachineType.BESCA,
                transport = Transport.SERIAL,
                port = portName,
                baudRate = 9600,
                slaveId = 1,
                btRegister = 45,
                etRegister = 46,
                divisionFactor = 10.0,
                pollingIntervalMs = 1000
            )
            val result = withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                ConnectionTester.test(config)
            }
            if (result != null && result.isSuccess && result.getOrNull() is ConnectionState.Connected) {
                found.add(DetectedRoaster(MachineType.BESCA, Transport.SERIAL, config))
                log.info("Discovery: found Besca at serial port {}", portName)
            }
        }

        found
    }
}
