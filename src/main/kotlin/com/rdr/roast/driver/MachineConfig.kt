package com.rdr.roast.driver

/**
 * Preset configurations derived from Artisan .aset files for common roaster models.
 */
object MachinePresets {
    val BESCA_MANUAL_V2 = mapOf(
        "port" to "COM4",
        "baudRate" to 9600,
        "slaveId" to 1,
        "btRegister" to 45,
        "etRegister" to 46,
        "functionCode" to 3,
        "pollingIntervalMs" to 3000L
    )

    val DIEDRICH_6_SENSOR = mapOf(
        "port" to "COM1",
        "baudRate" to 19200,
        "slaveId" to 1,
        "btRegister" to 0,
        "etRegister" to 1,
        "functionCode" to 4,
        "pollingIntervalMs" to 1000L
    )
}
