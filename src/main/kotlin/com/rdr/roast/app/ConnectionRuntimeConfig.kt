package com.rdr.roast.app

/**
 * Runtime-only config for MODBUS connection behavior.
 * Mirrors Artisan semantics (timeouts/retries/transport-specific params).
 */
data class ConnectionRuntimeConfig(
    val host: String?,
    val port: Int,
    val serialPort: String,
    val baudRate: Int,
    val byteSize: Int,
    val parity: SerialParity,
    val stopBits: Int,
    val serialTimeoutSec: Double,
    val ipTimeoutSec: Double,
    val ipRetries: Int,
    val transportType: ModbusTransportType,
    val disconnectOnError: Boolean,
    val acceptableErrors: Int,
    val slaveId: Int
)

fun MachineConfig.toRuntimeConfig(): ConnectionRuntimeConfig {
    val type = when (transport) {
        Transport.TCP -> ModbusTransportType.TCP
        else -> modbusTransportType
    }
    return ConnectionRuntimeConfig(
        host = host,
        port = tcpPort,
        serialPort = port,
        baudRate = baudRate,
        byteSize = byteSize,
        parity = parity,
        stopBits = stopBits,
        serialTimeoutSec = serialTimeoutSec,
        ipTimeoutSec = ipTimeoutSec,
        ipRetries = ipRetries,
        transportType = type,
        disconnectOnError = disconnectOnError,
        acceptableErrors = acceptableErrors,
        slaveId = slaveId
    )
}

