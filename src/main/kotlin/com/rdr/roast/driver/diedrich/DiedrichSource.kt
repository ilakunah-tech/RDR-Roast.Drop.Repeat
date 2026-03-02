package com.rdr.roast.driver.diedrich

import com.rdr.roast.app.MachineConfig
import com.rdr.roast.driver.modbus.core.AbstractModbusRoasterSource

/**
 * Diedrich serial MODBUS source backed by the shared Artisan-style MODBUS core.
 * Uses input-register reads for ET/BT.
 */
class DiedrichSource(config: MachineConfig) : AbstractModbusRoasterSource(
    config = config,
    connectedDeviceName = "Diedrich",
    readInputRegisters = true
)
