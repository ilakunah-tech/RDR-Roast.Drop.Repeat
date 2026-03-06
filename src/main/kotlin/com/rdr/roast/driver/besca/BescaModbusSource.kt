package com.rdr.roast.driver.besca

import com.rdr.roast.app.MachineConfig
import com.rdr.roast.driver.modbus.core.AbstractModbusRoasterSource

/**
 * Besca serial MODBUS source backed by the shared Artisan-style MODBUS core.
 */
class BescaModbusSource(config: MachineConfig) : AbstractModbusRoasterSource(
    config = config,
    connectedDeviceName = "Besca",
    readInputRegisters = false
)
