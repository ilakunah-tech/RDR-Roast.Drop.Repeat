package com.rdr.roast.driver.besca

import com.rdr.roast.app.MachineConfig
import com.rdr.roast.app.SettingsManager
import com.rdr.roast.app.SliderChannelConfig
import com.rdr.roast.driver.ControlSpec
import com.rdr.roast.driver.RoastControl
import com.rdr.roast.driver.modbus.core.AbstractModbusRoasterSource
import org.slf4j.LoggerFactory

/** Control IDs for Besca (Cropster-compatible register names). */
private const val CONTROL_GAS = "gas"
private const val CONTROL_AIRFLOW = "airflow"
private const val CONTROL_DRUM = "drum"

/** Artisan-style formula: value sent to register = (factor * sliderValue) + offset. */
private fun computeRegisterValue(sliderValue: Double, factor: Double, offset: Double): Int {
    val raw = (factor * sliderValue) + offset
    return raw.toInt().coerceIn(0, 65535)
}

private fun effectiveChannelConfig(
    channel: SliderChannelConfig?,
    machineRegister: Int,
    defaultMin: Double = 0.0,
    defaultMax: Double = 100.0,
    defaultStep: Double = 1.0
): Pair<Int, SliderChannelConfig>? {
    val reg = channel?.register?.takeIf { it != 0 } ?: machineRegister
    if (reg == 0) return null
    val cfg = channel ?: SliderChannelConfig(
        register = reg,
        min = defaultMin,
        max = defaultMax,
        factor = 1.0,
        offset = 0.0,
        step = defaultStep
    )
    return reg to cfg.copy(register = reg)
}

/**
 * Besca TCP MODBUS source backed by the shared Artisan-style MODBUS core.
 * Slider config (register, min, max, factor, offset, step) is read from [SettingsManager];
 * when register is 0 in config, machine registers from [MachineConfig] are used.
 */
class BescaModbusTcpSource(
    private val tcpConfig: MachineConfig
) : AbstractModbusRoasterSource(
    config = tcpConfig,
    connectedDeviceName = "Besca TCP",
    readInputRegisters = false
), RoastControl {

    private val log = LoggerFactory.getLogger(BescaModbusTcpSource::class.java)

    override fun supportsControl(): Boolean {
        val stepConfig = SettingsManager.load().sliderStepConfig
        return effectiveChannelConfig(stepConfig.gasChannel, tcpConfig.gasRegister) != null ||
            effectiveChannelConfig(stepConfig.airChannel, tcpConfig.airflowRegister) != null ||
            effectiveChannelConfig(stepConfig.drumChannel, tcpConfig.drumRegister) != null
    }

    override fun controlSpecs(): List<ControlSpec> {
        val stepConfig = SettingsManager.load().sliderStepConfig
        val list = mutableListOf<ControlSpec>()
        effectiveChannelConfig(stepConfig.gasChannel, tcpConfig.gasRegister)?.let { (_, cfg) ->
            list.add(ControlSpec(CONTROL_GAS, ControlSpec.ControlType.SLIDER, "Gas", cfg.min, cfg.max, "%", cfg.step))
        }
        effectiveChannelConfig(stepConfig.airChannel, tcpConfig.airflowRegister)?.let { (_, cfg) ->
            list.add(ControlSpec(CONTROL_AIRFLOW, ControlSpec.ControlType.SLIDER, "Airflow", cfg.min, cfg.max, "%", cfg.step))
        }
        effectiveChannelConfig(stepConfig.drumChannel, tcpConfig.drumRegister)?.let { (_, cfg) ->
            list.add(ControlSpec(CONTROL_DRUM, ControlSpec.ControlType.SLIDER, "Drum", cfg.min, cfg.max, "%", cfg.step))
        }
        return list
    }

    override fun setControl(id: String, value: Double) {
        val stepConfig = SettingsManager.load().sliderStepConfig
        val (reg, cfg) = when (id) {
            CONTROL_GAS -> effectiveChannelConfig(stepConfig.gasChannel, tcpConfig.gasRegister)
            CONTROL_AIRFLOW -> effectiveChannelConfig(stepConfig.airChannel, tcpConfig.airflowRegister)
            CONTROL_DRUM -> effectiveChannelConfig(stepConfig.drumChannel, tcpConfig.drumRegister)
            else -> null
        } ?: return
        val clamped = value.coerceIn(cfg.min, cfg.max)
        val modbusValue = computeRegisterValue(clamped, cfg.factor, cfg.offset)
        try {
            core.writeSingle(tcpConfig.slaveId, reg, modbusValue)
        } catch (e: Exception) {
            log.warn("Besca TCP write {} failed: {}", id, e.message)
        }
    }
}
