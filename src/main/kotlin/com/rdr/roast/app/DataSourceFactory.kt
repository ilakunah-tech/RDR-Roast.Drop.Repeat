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

    /** Cropster Besca Full Auto default control registers (gas, airflow, drum). */
    private const val BESCA_TCP_DEFAULT_GAS_REG = 3904
    private const val BESCA_TCP_DEFAULT_AIRFLOW_REG = 1003
    private const val BESCA_TCP_DEFAULT_DRUM_REG = 1001

    fun create(config: MachineConfig): RoastDataSource = when (config.machineType) {
        MachineType.SIMULATOR -> SimulatorSource(config)
        MachineType.BESCA -> when (config.transport) {
            Transport.TCP -> {
                val configured = config.copy(modbusTransportType = ModbusTransportType.TCP)
                val cfg = if (configured.gasRegister == 0 && configured.airflowRegister == 0 && configured.drumRegister == 0)
                    configured.copy(
                        gasRegister = BESCA_TCP_DEFAULT_GAS_REG,
                        airflowRegister = BESCA_TCP_DEFAULT_AIRFLOW_REG,
                        drumRegister = BESCA_TCP_DEFAULT_DRUM_REG
                    )
                else configured
                BescaModbusTcpSource(cfg)
            }
            else -> BescaModbusSource(config.copy(modbusTransportType = config.modbusTransportType))
        }
        MachineType.DIEDRICH -> when (config.transport) {
            Transport.PHIDGET -> createDiedrichPhidgetSource(config)
            else -> DiedrichSource(config.copy(modbusTransportType = config.modbusTransportType))
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
