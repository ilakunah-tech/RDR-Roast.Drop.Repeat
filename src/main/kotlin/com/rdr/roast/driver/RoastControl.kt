package com.rdr.roast.driver

/**
 * Specifies one controllable parameter (slider or button) for a roaster.
 */
data class ControlSpec(
    val id: String,
    val type: ControlType,
    val displayName: String,
    val min: Double = 0.0,
    val max: Double = 100.0,
    val unit: String = "%"
) {
    enum class ControlType { SLIDER, BUTTON }
}

/**
 * Optional capability of a [RoastDataSource]: ability to control roaster actuators
 * (gas, airflow, drum, etc.) by writing to device registers or commands.
 */
interface RoastControl {

    /** True if this source supports control (has writable controls). */
    fun supportsControl(): Boolean

    /** List of controls (sliders/buttons) available for this roaster. */
    fun controlSpecs(): List<ControlSpec>

    /**
     * Set a continuous control value (e.g. gas %, airflow %).
     * @param id control id from [controlSpecs]
     * @param value value in range [spec.min, spec.max]
     */
    fun setControl(id: String, value: Double)

    /**
     * Set a discrete state (e.g. button on/off).
     * Default implementation maps to setControl(id, if (on) 1.0 else 0.0).
     */
    fun setControlState(id: String, on: Boolean) {
        setControl(id, if (on) 1.0 else 0.0)
    }
}
