package com.rdr.roast.ui.control

import com.rdr.roast.app.EventQuantifiersConfig
import com.rdr.roast.app.SliderPanelLayoutMode
import com.rdr.roast.app.SliderStepConfig
import com.rdr.roast.domain.ControlEventType
import com.rdr.roast.driver.ControlSpec
import com.rdr.roast.driver.RoastControl
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.control.TextField
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import javafx.scene.control.Tooltip
import javafx.scene.input.MouseButton
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox

/**
 * Cropster RI5–style control slider panel: one slider per [ControlSpec], clickable tick labels for quick-jump,
 * direct value input, single-column (Gas/Air/Drum toggle) or grid layout (Gas full right, Air/Drum left at half size).
 * Fires [onValueChanged] on every change; caller should debounce and call device write + recorder.addControlEvent.
 */
class ControlSliderPanel(
    private val control: RoastControl,
    private val eventQuantifiers: EventQuantifiersConfig,
    private val layoutMode: SliderPanelLayoutMode,
    private val sliderStepConfig: SliderStepConfig,
    private val onValueChanged: (specId: String, value: Double, eventType: ControlEventType, displayString: String?) -> Unit
) : VBox(6.0) {

    /** Canonical order for slider display: Gas → Airflow → Drum → Damper → other (Cropster order). */
    private fun specOrder(spec: ControlSpec): Int = when (specToEventType(spec)) {
        ControlEventType.GAS -> 0
        ControlEventType.AIR -> 1
        ControlEventType.DRUM -> 2
        ControlEventType.DAMPER -> 3
        else -> 4
    }

    init {
        alignment = Pos.TOP_CENTER
        padding = Insets(8.0)
        val sliderSpecs = control.controlSpecs()
            .filter { it.type == ControlSpec.ControlType.SLIDER }
            .sortedBy { specOrder(it) }
        if (sliderSpecs.isNotEmpty()) {
            when (layoutMode) {
                SliderPanelLayoutMode.SINGLE_COLUMN_TOGGLE -> buildSingleColumnToggle(sliderSpecs)
                SliderPanelLayoutMode.GRID_ALL -> buildGridAll(sliderSpecs)
            }
        }
    }

    private fun specToEventType(spec: ControlSpec): ControlEventType = when {
        spec.id.contains("gas", ignoreCase = true) || spec.displayName.contains("gas", ignoreCase = true) -> ControlEventType.GAS
        spec.id.contains("air", ignoreCase = true) || spec.displayName.contains("air", ignoreCase = true) -> ControlEventType.AIR
        spec.id.contains("drum", ignoreCase = true) || spec.displayName.contains("drum", ignoreCase = true) -> ControlEventType.DRUM
        spec.id.contains("damper", ignoreCase = true) || spec.displayName.contains("damper", ignoreCase = true) -> ControlEventType.DAMPER
        else -> ControlEventType.GAS
    }

    private fun sliderKind(eventType: ControlEventType): String = when (eventType) {
        ControlEventType.GAS -> "gas"
        ControlEventType.AIR -> "air"
        ControlEventType.DRUM -> "drum"
        ControlEventType.DAMPER -> "generic"
    }

    private fun effectiveRange(spec: ControlSpec, eventType: ControlEventType): Pair<Double, Double> {
        val q = eventQuantifiers.get(eventType)
        val rangeMin = q.min.toDouble().coerceIn(spec.min, spec.max)
        val rangeMax = q.max.toDouble().coerceIn(spec.min, spec.max)
        val effectiveMin = minOf(rangeMin, rangeMax)
        val effectiveMax = maxOf(rangeMin, rangeMax)
        return effectiveMin to effectiveMax
    }

    private fun formatValue(value: Double): String = value.toInt().toString()

    private fun buildSingleColumnToggle(specs: List<ControlSpec>) {
        val toggleGroup = ToggleGroup()
        val toggleBar = HBox(4.0).apply {
            alignment = Pos.CENTER_LEFT
        }
        val stack = VBox(6.0).apply {
            VBox.setVgrow(this, Priority.ALWAYS)
        }
        for ((index, spec) in specs.withIndex()) {
            val eventType = specToEventType(spec)
            val kind = sliderKind(eventType)
            val toggleBtn = ToggleButton(spec.displayName).apply {
                this.toggleGroup = toggleGroup
                isSelected = index == 0
                styleClass.add("control-toggle-btn")
                styleClass.add("control-toggle-$kind")
            }
            toggleBar.children.add(toggleBtn)
        }
        children.add(toggleBar)
        children.add(stack)
        VBox.setVgrow(stack, Priority.ALWAYS)

        for ((index, spec) in specs.withIndex()) {
            val row = buildOneSliderRow(spec)
            row.isVisible = index == 0
            row.isManaged = index == 0
            stack.children.add(row)
            (toggleGroup.toggles.getOrNull(index) as? ToggleButton)?.setOnAction {
                stack.children.forEachIndexed { i, node ->
                    node.isVisible = i == index
                    node.isManaged = i == index
                }
            }
        }
    }

    private fun buildGridAll(specs: List<ControlSpec>) {
        val gasSpec = specs.find { specToEventType(it) == ControlEventType.GAS }
        val airSpec = specs.find { specToEventType(it) == ControlEventType.AIR }
        val drumSpec = specs.find { specToEventType(it) == ControlEventType.DRUM }
        val otherSpecs = specs.filter {
            val t = specToEventType(it)
            t != ControlEventType.GAS && t != ControlEventType.AIR && t != ControlEventType.DRUM
        }
        when {
            gasSpec != null && (airSpec != null || drumSpec != null) -> {
                val leftRows = listOfNotNull(airSpec, drumSpec).map { buildOneSliderRow(it) }
                val gasRow = buildOneSliderRow(gasSpec)
                leftRows.forEach { VBox.setVgrow(it, Priority.ALWAYS) }
                val leftColumn = VBox(6.0).apply {
                    alignment = Pos.TOP_CENTER
                    children.addAll(leftRows)
                    VBox.setVgrow(this, Priority.ALWAYS)
                }
                val root = HBox(8.0).apply {
                    alignment = Pos.CENTER_LEFT
                    children.add(leftColumn)
                    children.add(gasRow)
                    HBox.setHgrow(gasRow, Priority.ALWAYS)
                    VBox.setVgrow(this, Priority.ALWAYS)
                }
                children.add(root)
                VBox.setVgrow(root, Priority.ALWAYS)
                if (otherSpecs.isNotEmpty()) {
                    val otherStack = VBox(6.0).apply {
                        alignment = Pos.TOP_LEFT
                        children.addAll(otherSpecs.map { buildOneSliderRow(it) })
                    }
                    children.add(otherStack)
                }
            }
            else -> {
                val grid = GridPane().apply {
                    hgap = 8.0
                    vgap = 8.0
                    alignment = Pos.TOP_LEFT
                }
                specs.forEachIndexed { rowIndex, spec ->
                    val row = buildOneSliderRow(spec)
                    grid.add(row, 0, rowIndex)
                    GridPane.setHgrow(row, Priority.ALWAYS)
                }
                children.add(grid)
                VBox.setVgrow(grid, Priority.ALWAYS)
            }
        }
    }

    private fun buildOneSliderRow(spec: ControlSpec): VBox {
        val eventType = specToEventType(spec)
        val (effectiveMin, effectiveMax) = effectiveRange(spec, eventType)
        val kind = sliderKind(eventType)
        val q = eventQuantifiers.get(eventType)
        val rangeHint = when (q.source.name) {
            "ET" -> " (source: ET)"
            "BT" -> " (source: BT)"
            else -> ""
        }

        val effectiveMinForSlider = when (eventType) {
            ControlEventType.DRUM -> 0.0
            else -> effectiveMin
        }
        val effectiveMaxForSlider = when (eventType) {
            ControlEventType.DRUM -> 100.0
            else -> effectiveMax
        }
        return when (eventType) {
            ControlEventType.GAS, ControlEventType.AIR, ControlEventType.DRUM, ControlEventType.DAMPER -> {
                val slider = Slider(effectiveMinForSlider, effectiveMaxForSlider, effectiveMinForSlider).apply {
                    orientation = Orientation.VERTICAL
                    blockIncrement = 1.0
                    majorTickUnit = 10.0
                    minorTickCount = 9
                    isShowTickMarks = false
                    isShowTickLabels = false
                    isSnapToTicks = true
                    minHeight = 80.0
                    VBox.setVgrow(this, Priority.ALWAYS)
                    styleClass.addAll("slider-vertical", "slider-vertical-narrow", "slider-vertical-$kind")
                }
                val valueField = TextField().apply {
                    text = formatValue(slider.value)
                    alignment = Pos.CENTER
                    styleClass.add("control-value-field")
                    if (eventType == ControlEventType.DRUM) styleClass.add("control-drum-field")
                    tooltip = Tooltip("${effectiveMinForSlider.toInt()} – ${effectiveMaxForSlider.toInt()} ${spec.unit}$rangeHint")
                }
                fun applyFieldValue() {
                    val parsed = valueField.text.replace(',', '.').toIntOrNull()?.toDouble() ?: return
                    val clamped = parsed.coerceIn(effectiveMinForSlider, effectiveMaxForSlider)
                    slider.value = clamped.toInt().toDouble()
                    valueField.text = formatValue(clamped)
                    onValueChanged(spec.id, clamped, eventType, null)
                }
                valueField.setOnAction { applyFieldValue() }
                valueField.focusedProperty().addListener { _, _, f -> if (!f) applyFieldValue() }
                fun notifyChange(value: Double) {
                    if (!valueField.isFocused) valueField.text = formatValue(value)
                    onValueChanged(spec.id, value, eventType, null)
                }
                slider.valueProperty().addListener { _, _, newVal ->
                    val intVal = newVal.toDouble().toInt().toDouble()
                    notifyChange(intVal)
                }

                val majorTicks = mutableListOf<Double>()
                var v = effectiveMaxForSlider
                while (v >= effectiveMinForSlider) {
                    majorTicks.add(v)
                    if (v <= effectiveMinForSlider) break
                    v = (v - 10.0).coerceAtLeast(effectiveMinForSlider)
                }
                val labelsVBox = VBox(0.0).apply {
                    alignment = Pos.TOP_CENTER
                    styleClass.add("slider-tick-labels")
                    for (i in majorTicks.indices) {
                        if (i > 0) {
                            val spacer = Region()
                            VBox.setVgrow(spacer, Priority.ALWAYS)
                            children.add(spacer)
                        }
                        val tickVal = majorTicks[i]
                        val lbl = Label(formatValue(tickVal)).apply {
                            styleClass.addAll("slider-tick-major", "slider-tick-$kind")
                            tooltip = Tooltip("Click to set ${formatValue(tickVal)}")
                            isMouseTransparent = false
                            setOnMouseClicked { e ->
                                if (e.button == MouseButton.PRIMARY) {
                                    val clamped = tickVal.toInt().toDouble().coerceIn(effectiveMinForSlider, effectiveMaxForSlider)
                                    slider.value = clamped
                                    valueField.text = formatValue(clamped)
                                    onValueChanged(spec.id, clamped, eventType, null)
                                }
                            }
                        }
                        children.add(lbl)
                    }
                }
                val sliderWithLabels = HBox(4.0).apply {
                    alignment = Pos.CENTER_LEFT
                    VBox.setVgrow(this, Priority.ALWAYS)
                    children.add(labelsVBox)
                    children.add(slider)
                    HBox.setHgrow(slider, Priority.ALWAYS)
                }
                VBox(4.0).apply {
                    alignment = Pos.TOP_CENTER
                    VBox.setVgrow(this, Priority.ALWAYS)
                    children.add(Label(spec.displayName).apply { styleClass.add("control-label") })
                    children.add(sliderWithLabels)
                    VBox.setVgrow(sliderWithLabels, Priority.ALWAYS)
                    children.add(valueField)
                }
            }
        }
    }
}
