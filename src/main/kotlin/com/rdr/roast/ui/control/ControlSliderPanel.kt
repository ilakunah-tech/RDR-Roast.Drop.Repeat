package com.rdr.roast.ui.control

import com.rdr.roast.app.EventQuantifiersConfig
import com.rdr.roast.app.PresetSliderConfig
import com.rdr.roast.app.SliderPanelLayoutMode
import com.rdr.roast.app.SliderStepConfig
import com.rdr.roast.domain.ControlEventType
import com.rdr.roast.driver.ControlSpec
import com.rdr.roast.driver.RoastControl
import javafx.beans.binding.Bindings
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
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid
import org.kordamp.ikonli.javafx.FontIcon

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

    private fun specOrder(spec: ControlSpec): Int = when (specToEventType(spec)) {
        ControlEventType.GAS -> 0
        ControlEventType.AIR -> 1
        ControlEventType.DRUM -> 2
        ControlEventType.DAMPER -> 3
        ControlEventType.BURNER -> 4
    }

    init {
        alignment = Pos.TOP_CENTER
        isFillWidth = true
        minHeight = 0.0
        padding = Insets(4.0)
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

    companion object {
        fun fromPreset(
            presetSliders: List<PresetSliderConfig>,
            layoutMode: SliderPanelLayoutMode,
            onCommand: (command: String, value: Double) -> Unit
        ): ControlSliderPanel? {
            val visible = presetSliders.filter { it.visible && it.label.isNotBlank() }
            if (visible.isEmpty()) return null
            val specs = visible.mapIndexed { i, cfg ->
                ControlSpec(
                    id = "preset_slider_$i",
                    displayName = cfg.label,
                    min = cfg.min,
                    max = cfg.max,
                    step = 1.0,
                    unit = cfg.unit.ifBlank { "%" },
                    type = ControlSpec.ControlType.SLIDER
                )
            }
            val dummyControl = object : RoastControl {
                override fun supportsControl(): Boolean = true
                override fun controlSpecs(): List<ControlSpec> = specs
                override fun setControl(specId: String, value: Double) {
                    val idx = specId.removePrefix("preset_slider_").toIntOrNull() ?: return
                    val cfg = visible.getOrNull(idx) ?: return
                    if (cfg.command.isBlank()) return
                    val transformedValue = value * cfg.factor + cfg.offset
                    val filledCommand = cfg.command.replace("{}", transformedValue.toInt().toString())
                    onCommand(filledCommand, transformedValue)
                }
            }
            return ControlSliderPanel(
                control = dummyControl,
                eventQuantifiers = EventQuantifiersConfig(),
                layoutMode = layoutMode,
                sliderStepConfig = SliderStepConfig(),
                onValueChanged = { specId, value, _, _ ->
                    dummyControl.setControl(specId, value)
                }
            )
        }
    }

    private fun specToEventType(spec: ControlSpec): ControlEventType {
        val name = (spec.displayName + " " + spec.id).lowercase()
        return when {
            name.contains("gas") || name.contains("fire") || name.contains("flame") -> ControlEventType.GAS
            "air" in name || "fan" in name || "airflow" in name -> ControlEventType.AIR
            "drum" in name -> ControlEventType.DRUM
            "damper" in name || "cooling" in name -> ControlEventType.DAMPER
            "burner" in name || "heater" in name || "heat" in name -> ControlEventType.BURNER
            else -> ControlEventType.GAS
        }
    }

    private fun sliderKind(eventType: ControlEventType): String = when (eventType) {
        ControlEventType.GAS -> "gas"
        ControlEventType.AIR -> "air"
        ControlEventType.DRUM -> "drum"
        ControlEventType.DAMPER -> "damper"
        ControlEventType.BURNER -> "burner"
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
        styleClass.add("cropster-slider-panel")
        styleClass.add("cropster-slider-panel-single")
        val toggleGroup = ToggleGroup()
        val toggleBar = HBox(3.0).apply {
            alignment = Pos.CENTER_LEFT
        }
        val stack = VBox(4.0).apply {
            alignment = Pos.TOP_CENTER
            minHeight = 0.0
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

        for ((index, spec) in specs.withIndex()) {
            val section = buildCropsterSection(spec).apply {
                maxWidth = 192.0
                VBox.setVgrow(this, Priority.ALWAYS)
            }
            section.isVisible = index == 0
            section.isManaged = index == 0
            stack.children.add(section)
            (toggleGroup.toggles.getOrNull(index) as? ToggleButton)?.setOnAction {
                stack.children.forEachIndexed { i, node ->
                    node.isVisible = i == index
                    node.isManaged = i == index
                }
            }
        }
    }

    /** Cropster-style: Gas left column; all other sliders (Air, Drum, Damper, Burner) in the right column, stacked vertically. */
    private fun buildCropsterLayout(gasSpec: ControlSpec, rightSpecs: List<ControlSpec>) {
        styleClass.add("cropster-slider-panel")
        val leftColumn = buildCropsterSection(gasSpec).apply {
            VBox.setVgrow(this, Priority.ALWAYS)
            minWidth = 120.0
            maxWidth = 150.0
            minHeight = 0.0
        }
        val rightSections = rightSpecs.map { buildCropsterSection(it).apply { VBox.setVgrow(this, Priority.ALWAYS) } }
        val rightColumn = VBox(6.0).apply {
            alignment = Pos.TOP_CENTER
            minWidth = 120.0
            minHeight = 0.0
            children.addAll(rightSections)
            VBox.setVgrow(this, Priority.ALWAYS)
        }
        val root = HBox(6.0).apply {
            alignment = Pos.TOP_LEFT
            minHeight = 0.0
            children.add(leftColumn)
            children.add(rightColumn)
            HBox.setHgrow(leftColumn, Priority.ALWAYS)
            HBox.setHgrow(rightColumn, Priority.ALWAYS)
            VBox.setVgrow(this, Priority.ALWAYS)
        }
        children.add(root)
        VBox.setVgrow(root, Priority.ALWAYS)
    }

    private fun buildCropsterSection(spec: ControlSpec): VBox {
        val eventType = specToEventType(spec)
        val (effectiveMin, effectiveMax) = effectiveRange(spec, eventType)
        val kind = sliderKind(eventType)
        val effectiveMinForSlider = when (eventType) {
            ControlEventType.DRUM -> spec.min
            else -> effectiveMin
        }
        val effectiveMaxForSlider = when (eventType) {
            ControlEventType.DRUM -> spec.max
            else -> effectiveMax
        }
        val unitStr = if (eventType == ControlEventType.DRUM) spec.unit.ifBlank { "rpm" } else "%"
        val displayName = when (eventType) {
            ControlEventType.AIR -> "Airflow"
            ControlEventType.DRUM -> "Drum"
            else -> spec.displayName
        }

        val slider = Slider(effectiveMinForSlider, effectiveMaxForSlider, effectiveMinForSlider).apply {
            orientation = Orientation.VERTICAL
            blockIncrement = 1.0
            majorTickUnit = 10.0
            minorTickCount = 9
            isShowTickMarks = false
            isShowTickLabels = false
            isSnapToTicks = true
            minHeight = 40.0
            VBox.setVgrow(this, Priority.ALWAYS)
            styleClass.addAll("cropster-slider", "cropster-slider-$kind")
        }
        val sliderTrackBg = Region().apply {
            styleClass.add("cropster-slider-track-bg")
            isMouseTransparent = true
        }
        val sliderTrackFill = Region().apply {
            styleClass.addAll("cropster-slider-track-fill", "cropster-slider-track-fill-$kind")
            isMouseTransparent = true
            prefHeightProperty().bind(
                Bindings.createDoubleBinding(
                    {
                        val range = effectiveMaxForSlider - effectiveMinForSlider
                        if (range <= 0.0) {
                            0.0
                        } else {
                            val fraction = ((slider.value - effectiveMinForSlider) / range).coerceIn(0.0, 1.0)
                            ((slider.height - 14.0).coerceAtLeast(0.0)) * fraction
                        }
                    },
                    slider.valueProperty(),
                    slider.heightProperty()
                )
            )
        }
        val sliderStack = StackPane().apply {
            alignment = Pos.CENTER
            minWidth = 18.0
            prefWidth = 18.0
            minHeight = 0.0
            children.addAll(sliderTrackBg, sliderTrackFill, slider)
            StackPane.setAlignment(sliderTrackBg, Pos.CENTER)
            StackPane.setAlignment(sliderTrackFill, Pos.BOTTOM_CENTER)
            StackPane.setAlignment(slider, Pos.CENTER)
            VBox.setVgrow(this, Priority.ALWAYS)
        }
        val valueField = TextField().apply {
            text = formatValue(slider.value)
            alignment = Pos.CENTER
            styleClass.addAll("cropster-value-field")
            tooltip = Tooltip("${effectiveMinForSlider.toInt()} – ${effectiveMaxForSlider.toInt()} $unitStr")
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

        val step = spec.step.coerceAtLeast(1.0)
        val btnMinus = javafx.scene.control.Button().apply {
            graphic = FontIcon(FontAwesomeSolid.MINUS).apply { iconSize = 12 }
            styleClass.addAll("cropster-step-btn")
            setOnAction {
                val v = (slider.value - step).coerceIn(effectiveMinForSlider, effectiveMaxForSlider)
                slider.value = v
                valueField.text = formatValue(v)
                onValueChanged(spec.id, v, eventType, null)
            }
        }
        val btnPlus = javafx.scene.control.Button().apply {
            graphic = FontIcon(FontAwesomeSolid.PLUS).apply { iconSize = 12 }
            styleClass.addAll("cropster-step-btn")
            setOnAction {
                val v = (slider.value + step).coerceIn(effectiveMinForSlider, effectiveMaxForSlider)
                slider.value = v
                valueField.text = formatValue(v)
                onValueChanged(spec.id, v, eventType, null)
            }
        }

        val iconNode = StackPane().apply {
            styleClass.addAll("cropster-icon-box", "cropster-icon-$kind")
            children.add(
                when (eventType) {
                    ControlEventType.GAS -> FontIcon(FontAwesomeSolid.FIRE).apply { iconSize = 14 }
                    ControlEventType.AIR -> FontIcon(FontAwesomeSolid.FAN).apply { iconSize = 14 }
                    ControlEventType.DRUM -> FontIcon(FontAwesomeSolid.SYNC_ALT).apply { iconSize = 14 }
                    ControlEventType.BURNER -> FontIcon(FontAwesomeSolid.FIRE_ALT).apply { iconSize = 14 }
                    ControlEventType.DAMPER -> FontIcon(FontAwesomeSolid.SLIDERS_H).apply { iconSize = 14 }
                }
            )
        }
        val unitLabel = Label(unitStr).apply {
            styleClass.add("cropster-unit-box")
        }
        val inputRow = HBox(0.0).apply {
            styleClass.add("cropster-input-row")
            alignment = Pos.CENTER_LEFT
            children.addAll(iconNode, valueField, unitLabel)
        }
        val stepButtons = HBox(4.0).apply {
            alignment = Pos.CENTER
            children.addAll(btnMinus, btnPlus)
        }

        val majorTicks = mutableListOf<Double>()
        var v = effectiveMaxForSlider
        while (v >= effectiveMinForSlider) {
            majorTicks.add(v)
            if (v <= effectiveMinForSlider) break
            v = (v - 10.0).coerceAtLeast(effectiveMinForSlider)
        }
        val minorTicks = mutableListOf<Double>()
        var vMin = effectiveMaxForSlider - 5.0
        while (vMin >= effectiveMinForSlider) {
            if (vMin.toInt() % 10 != 0) minorTicks.add(vMin)
            if (vMin <= effectiveMinForSlider) break
            vMin = (vMin - 10.0).coerceAtLeast(effectiveMinForSlider)
        }
        val leftLabels = VBox(0.0).apply {
            alignment = Pos.CENTER_RIGHT
            minHeight = 0.0
            styleClass.addAll("cropster-tick-column", "cropster-tick-column-left")
            for (i in majorTicks.indices) {
                if (i > 0) {
                    val spacer = Region()
                    VBox.setVgrow(spacer, Priority.ALWAYS)
                    children.add(spacer)
                }
                val tickVal = majorTicks[i]
                val lbl = Label(formatValue(tickVal)).apply {
                    styleClass.addAll("cropster-tick-major", "cropster-tick-chip", "cropster-tick-$kind")
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
        val rightLabels = VBox(0.0).apply {
            alignment = Pos.CENTER_LEFT
            minHeight = 0.0
            styleClass.addAll("cropster-tick-column", "cropster-tick-column-right")
            for (i in minorTicks.indices) {
                if (i > 0) {
                    val spacer = Region()
                    VBox.setVgrow(spacer, Priority.ALWAYS)
                    children.add(spacer)
                }
                val tickVal = minorTicks[i]
                val lbl = Label(formatValue(tickVal)).apply {
                    styleClass.addAll("cropster-tick-minor", "cropster-tick-chip", "cropster-tick-$kind")
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
        val sliderWithTicks = HBox(4.0).apply {
            alignment = Pos.CENTER
            minHeight = 0.0
            VBox.setVgrow(this, Priority.ALWAYS)
            children.addAll(leftLabels, sliderStack, rightLabels)
            HBox.setHgrow(sliderStack, Priority.ALWAYS)
        }

        val headerLine = HBox(4.0).apply {
            alignment = Pos.CENTER_LEFT
            children.add(Label(displayName).apply {
                styleClass.addAll("cropster-section-title")
            })
        }
        val headerUnderline = Region().apply {
            styleClass.addAll("cropster-header-underline", "cropster-underline-$kind")
        }
        val header = VBox(2.0).apply {
            children.addAll(headerLine, headerUnderline)
        }

        return VBox(6.0).apply {
            styleClass.addAll("cropster-section", "cropster-section-$kind")
            alignment = Pos.TOP_CENTER
            minHeight = 0.0
            VBox.setVgrow(this, Priority.ALWAYS)
            children.addAll(header, inputRow, stepButtons, sliderWithTicks)
            VBox.setVgrow(sliderWithTicks, Priority.ALWAYS)
        }
    }

    private fun buildGridAll(specs: List<ControlSpec>) {
        val sorted = specs.sortedBy { specOrder(it) }
        when {
            sorted.size >= 2 -> {
                val primary = sorted.first()
                val rest = sorted.drop(1)
                buildCropsterLayout(primary, rest)
            }
            sorted.size == 1 -> {
                val section = buildCropsterSection(sorted.first()).apply {
                    VBox.setVgrow(this, Priority.ALWAYS)
                    maxWidth = 200.0
                }
                children.add(section)
                VBox.setVgrow(section, Priority.ALWAYS)
            }
            else -> {}
        }
    }

    private fun buildTwoColumnLayout(specs: List<ControlSpec>) {
        val half = (specs.size + 1) / 2
        val leftSpecs = specs.take(half)
        val rightSpecs = specs.drop(half)

        val leftCol = VBox(6.0).apply {
            alignment = Pos.TOP_CENTER
            minWidth = 0.0
            minHeight = 0.0
            children.addAll(leftSpecs.map { buildOneSliderRow(it).apply { VBox.setVgrow(this, Priority.ALWAYS) } })
            VBox.setVgrow(this, Priority.ALWAYS)
        }
        val rightCol = VBox(6.0).apply {
            alignment = Pos.TOP_CENTER
            minWidth = 0.0
            minHeight = 0.0
            children.addAll(rightSpecs.map { buildOneSliderRow(it).apply { VBox.setVgrow(this, Priority.ALWAYS) } })
            VBox.setVgrow(this, Priority.ALWAYS)
        }
        val root = HBox(6.0).apply {
            alignment = Pos.TOP_LEFT
            minHeight = 0.0
            children.add(leftCol)
            children.add(rightCol)
            HBox.setHgrow(leftCol, Priority.ALWAYS)
            HBox.setHgrow(rightCol, Priority.ALWAYS)
            VBox.setVgrow(this, Priority.ALWAYS)
        }
        children.add(root)
        VBox.setVgrow(root, Priority.ALWAYS)
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
            ControlEventType.GAS, ControlEventType.AIR, ControlEventType.DRUM, ControlEventType.DAMPER, ControlEventType.BURNER -> {
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
