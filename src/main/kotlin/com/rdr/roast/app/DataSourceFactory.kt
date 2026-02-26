package com.rdr.roast.app

import com.rdr.roast.driver.RoastDataSource
import com.rdr.roast.driver.besca.BescaModbusSource
import com.rdr.roast.driver.besca.BescaModbusTcpSource
import com.rdr.roast.driver.diedrich.DiedrichSource
import com.rdr.roast.driver.simulator.SimulatorSource

/**
 * Builds a [RoastDataSource] from saved or selected [MachineConfig].
 * Used at startup (connect to configured roaster) and when applying a preset or saving settings.
 */
object DataSourceFactory {

    fun create(config: MachineConfig): RoastDataSource = when (config.machineType) {
        MachineType.SIMULATOR -> SimulatorSource()
        MachineType.BESCA -> when (config.transport) {
            Transport.TCP -> BescaModbusTcpSource(config)
            else -> BescaModbusSource(config)
        }
        MachineType.DIEDRICH -> when (config.transport) {
            Transport.PHIDGET -> createDiedrichPhidgetSource(config)
            else -> DiedrichSource(config)
        }
    }

    private fun createDiedrichPhidgetSource(config: MachineConfig): RoastDataSource {
        return try {
            val clazz = Class.forName("com.rdr.roast.driver.diedrich.DiedrichPhidget1048Source")
            clazz.getConstructor(MachineConfig::class.java).newInstance(config) as RoastDataSource
        } catch (e: ClassNotFoundException) {
            com.rdr.roast.driver.ErrorDataSource(
                "Phidget 1048 driver not on classpath. Add Phidget22 library for Diedrich USB connection."
            )
        } catch (e: Exception) {
            com.rdr.roast.driver.ErrorDataSource(e.message ?: "Failed to create Phidget source")
        }
    }
}
