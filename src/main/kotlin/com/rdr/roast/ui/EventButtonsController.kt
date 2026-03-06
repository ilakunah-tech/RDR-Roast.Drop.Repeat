package com.rdr.roast.ui

import com.rdr.roast.app.AppSettings
import com.rdr.roast.app.ButtonSize
import com.rdr.roast.app.CustomButtonConfig
import com.rdr.roast.app.EventButtonsDialogConfig
import com.rdr.roast.app.EventQuantifierConfig
import com.rdr.roast.app.EventQuantifiersConfig
import com.rdr.roast.app.EventSliderRowConfig
import com.rdr.roast.app.EventSlidersConfig
import com.rdr.roast.app.QuantifierSource
import com.rdr.roast.app.SliderChannelConfig
import com.rdr.roast.app.SliderStepConfig
import javafx.collections.FXCollections
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.CheckBox
import javafx.scene.control.ChoiceBox
import javafx.scene.control.ColorPicker
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.scene.control.TextField
import javafx.scene.control.cell.PropertyValueFactory
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.layout.GridPane
import javafx.scene.paint.Color
import javafx.stage.Stage
import java.net.URL
import java.util.ResourceBundle

data class EventButtonsDialogResult(
    val customButtons: List<CustomButtonConfig>,
    val eventButtonsConfig: EventButtonsDialogConfig,
    val eventCommands: Map<String, String>,
    val sliderStepConfig: SliderStepConfig,
    val eventSliders: EventSlidersConfig,
    val eventQuantifiers: EventQuantifiersConfig
)

class EventButtonsController : Initializable {
    @FXML lateinit var table: TableView<CustomButtonConfig>
    @FXML lateinit var colLabel: TableColumn<CustomButtonConfig, String>
    @FXML lateinit var colDescription: TableColumn<CustomButtonConfig, String>
    @FXML lateinit var colType: TableColumn<CustomButtonConfig, String>
    @FXML lateinit var colValue: TableColumn<CustomButtonConfig, Int>
    @FXML lateinit var colAction: TableColumn<CustomButtonConfig, String>
    @FXML lateinit var colDocumentation: TableColumn<CustomButtonConfig, String>
    @FXML lateinit var colVisibility: TableColumn<CustomButtonConfig, Boolean>
    @FXML lateinit var colBgColor: TableColumn<CustomButtonConfig, String>
    @FXML lateinit var colTextColor: TableColumn<CustomButtonConfig, String>
    @FXML lateinit var btnAdd: Button
    @FXML lateinit var btnInsert: Button
    @FXML lateinit var btnDelete: Button
    @FXML lateinit var btnCopyTable: Button

    @FXML lateinit var txtEventType1: TextField
    @FXML lateinit var txtEventType2: TextField
    @FXML lateinit var txtEventType3: TextField
    @FXML lateinit var txtEventType4: TextField
    @FXML lateinit var chkEventButton: CheckBox
    @FXML lateinit var chkShowOnBt: CheckBox
    @FXML lateinit var chkAnnotations: CheckBox
    @FXML lateinit var chkPhaseLines: CheckBox
    @FXML lateinit var chkTimeGuide: CheckBox
    @FXML lateinit var txtDefaultAction: TextField
    @FXML lateinit var txtDefaultCommand: TextField
    @FXML lateinit var chkAutoCharge: CheckBox
    @FXML lateinit var chkAutoDryEnd: CheckBox
    @FXML lateinit var chkAutoFirstCrack: CheckBox
    @FXML lateinit var chkAutoDrop: CheckBox

    @FXML lateinit var txtEventCharge: TextField
    @FXML lateinit var txtEventDrop: TextField
    @FXML lateinit var txtEventDryEnd: TextField
    @FXML lateinit var txtEventFcStart: TextField
    @FXML lateinit var txtEventCoolEnd: TextField

    @FXML lateinit var txtMaxButtonsPerRow: TextField
    @FXML lateinit var cmbButtonSize: ChoiceBox<String>
    @FXML lateinit var txtColorPattern: TextField
    @FXML lateinit var chkMarkLastPressed: CheckBox
    @FXML lateinit var chkTooltips: CheckBox

    @FXML lateinit var txtAirCommand: TextField
    @FXML lateinit var txtAirMin: TextField
    @FXML lateinit var txtAirMax: TextField
    @FXML lateinit var txtAirFactor: TextField
    @FXML lateinit var txtAirOffset: TextField
    @FXML lateinit var chkAirBernoulli: CheckBox
    @FXML lateinit var txtAirStep: TextField
    @FXML lateinit var chkAirTemp: CheckBox
    @FXML lateinit var txtAirUnit: TextField
    @FXML lateinit var txtDrumCommand: TextField
    @FXML lateinit var txtDrumMin: TextField
    @FXML lateinit var txtDrumMax: TextField
    @FXML lateinit var txtDrumFactor: TextField
    @FXML lateinit var txtDrumOffset: TextField
    @FXML lateinit var chkDrumBernoulli: CheckBox
    @FXML lateinit var txtDrumStep: TextField
    @FXML lateinit var chkDrumTemp: CheckBox
    @FXML lateinit var txtDrumUnit: TextField
    @FXML lateinit var txtDamperCommand: TextField
    @FXML lateinit var txtDamperMin: TextField
    @FXML lateinit var txtDamperMax: TextField
    @FXML lateinit var txtDamperFactor: TextField
    @FXML lateinit var txtDamperOffset: TextField
    @FXML lateinit var chkDamperBernoulli: CheckBox
    @FXML lateinit var txtDamperStep: TextField
    @FXML lateinit var chkDamperTemp: CheckBox
    @FXML lateinit var txtDamperUnit: TextField
    @FXML lateinit var txtBurnerCommand: TextField
    @FXML lateinit var txtBurnerMin: TextField
    @FXML lateinit var txtBurnerMax: TextField
    @FXML lateinit var txtBurnerFactor: TextField
    @FXML lateinit var txtBurnerOffset: TextField
    @FXML lateinit var chkBurnerBernoulli: CheckBox
    @FXML lateinit var txtBurnerStep: TextField
    @FXML lateinit var chkBurnerTemp: CheckBox
    @FXML lateinit var txtBurnerUnit: TextField
    @FXML lateinit var chkAlternativeLayout: CheckBox
    @FXML lateinit var chkKeyboardControl: CheckBox

    @FXML lateinit var cmbQuantAirSource: ComboBox<String>
    @FXML lateinit var txtQuantAirSv: TextField
    @FXML lateinit var txtQuantAirMin: TextField
    @FXML lateinit var txtQuantAirMax: TextField
    @FXML lateinit var txtQuantAirStep: TextField
    @FXML lateinit var chkQuantAirAction: CheckBox
    @FXML lateinit var cmbQuantDrumSource: ComboBox<String>
    @FXML lateinit var txtQuantDrumSv: TextField
    @FXML lateinit var txtQuantDrumMin: TextField
    @FXML lateinit var txtQuantDrumMax: TextField
    @FXML lateinit var txtQuantDrumStep: TextField
    @FXML lateinit var chkQuantDrumAction: CheckBox
    @FXML lateinit var cmbQuantDamperSource: ComboBox<String>
    @FXML lateinit var txtQuantDamperSv: TextField
    @FXML lateinit var txtQuantDamperMin: TextField
    @FXML lateinit var txtQuantDamperMax: TextField
    @FXML lateinit var txtQuantDamperStep: TextField
    @FXML lateinit var chkQuantDamperAction: CheckBox
    @FXML lateinit var cmbQuantBurnerSource: ComboBox<String>
    @FXML lateinit var txtQuantBurnerSv: TextField
    @FXML lateinit var txtQuantBurnerMin: TextField
    @FXML lateinit var txtQuantBurnerMax: TextField
    @FXML lateinit var txtQuantBurnerStep: TextField
    @FXML lateinit var chkQuantBurnerAction: CheckBox
    @FXML lateinit var chkQuantCluster: CheckBox

    @FXML lateinit var btnApply: Button
    @FXML lateinit var btnOk: Button
    @FXML lateinit var btnCancel: Button

    private val items = FXCollections.observableArrayList<CustomButtonConfig>()
    private var sourceSliderStepConfig = SliderStepConfig()
    private var result: EventButtonsDialogResult? = null

    var applied: Boolean = false
        private set

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        table.items = items
        colLabel.setCellValueFactory(PropertyValueFactory("label"))
        colDescription.setCellValueFactory(PropertyValueFactory("description"))
        colType.setCellValueFactory(PropertyValueFactory("eventType"))
        colValue.setCellValueFactory(PropertyValueFactory("eventValue"))
        colAction.setCellValueFactory(PropertyValueFactory("actionType"))
        colDocumentation.setCellValueFactory(PropertyValueFactory("documentation"))
        colVisibility.setCellValueFactory(PropertyValueFactory("visibility"))
        colBgColor.setCellValueFactory(PropertyValueFactory("backgroundColor"))
        colTextColor.setCellValueFactory(PropertyValueFactory("textColor"))

        cmbButtonSize.items.setAll("Tiny", "Small", "Large")
        cmbButtonSize.value = "Small"
        val sourceItems = listOf("None", "ET", "BT")
        listOf(cmbQuantAirSource, cmbQuantDrumSource, cmbQuantDamperSource, cmbQuantBurnerSource).forEach {
            it.items.setAll(sourceItems)
            it.value = "None"
        }

        btnAdd.setOnAction { addOrEdit(null, insert = false) }
        btnInsert.setOnAction { addOrEdit(null, insert = true) }
        btnDelete.setOnAction {
            val selected = table.selectionModel.selectedIndex
            if (selected >= 0) items.removeAt(selected)
        }
        btnCopyTable.setOnAction { copyTableToClipboard() }
        btnApply.setOnAction { applyAndKeepOpen() }
        btnOk.setOnAction {
            applyAndKeepOpen()
            (btnOk.scene?.window as? Stage)?.close()
        }
        btnCancel.setOnAction { (btnCancel.scene?.window as? Stage)?.close() }
    }

    fun loadFrom(settings: AppSettings) {
        sourceSliderStepConfig = settings.sliderStepConfig
        val ec = settings.machineConfig.eventCommands
        txtEventCharge.text = ec["CHARGE"].orEmpty()
        txtEventDrop.text = ec["DROP"].orEmpty()
        txtEventDryEnd.text = ec["DRY_END"].orEmpty()
        txtEventFcStart.text = ec["FC_START"].orEmpty()
        txtEventCoolEnd.text = ec["COOL_END"].orEmpty()

        val ui = settings.eventButtonsConfig
        txtEventType1.text = ui.eventType1Label
        txtEventType2.text = ui.eventType2Label
        txtEventType3.text = ui.eventType3Label
        txtEventType4.text = ui.eventType4Label
        chkEventButton.isSelected = ui.eventButtonEnabled
        chkShowOnBt.isSelected = ui.showOnBt
        chkAnnotations.isSelected = ui.annotations
        chkPhaseLines.isSelected = ui.phaseLines
        chkTimeGuide.isSelected = ui.timeGuide
        txtDefaultAction.text = ui.defaultButtonAction
        txtDefaultCommand.text = ui.defaultButtonCommand
        chkAutoCharge.isSelected = ui.autoMarkCharge
        chkAutoDryEnd.isSelected = ui.autoMarkDryEnd
        chkAutoFirstCrack.isSelected = ui.autoMarkFirstCrack
        chkAutoDrop.isSelected = ui.autoMarkDrop
        txtMaxButtonsPerRow.text = ui.maxButtonsPerRow.toString()
        cmbButtonSize.value = when (ui.buttonSize) {
            ButtonSize.TINY -> "Tiny"
            ButtonSize.LARGE -> "Large"
            else -> "Small"
        }
        txtColorPattern.text = ui.colorPattern.toString()
        chkMarkLastPressed.isSelected = ui.markLastPressed
        chkTooltips.isSelected = ui.tooltips
        chkAlternativeLayout.isSelected = ui.alternativeSliderLayout
        chkKeyboardControl.isSelected = ui.keyboardControl
        chkQuantCluster.isSelected = ui.quantifiersCluster

        val s = settings.eventSliders
        setSliderFields(
            s.air.copy(
                min = sourceSliderStepConfig.airChannel?.min ?: s.air.min,
                max = sourceSliderStepConfig.airChannel?.max ?: s.air.max,
                factor = sourceSliderStepConfig.airChannel?.factor ?: s.air.factor,
                offset = sourceSliderStepConfig.airChannel?.offset ?: s.air.offset,
                step = sourceSliderStepConfig.airChannel?.step ?: s.air.step
            ),
            txtAirCommand, txtAirMin, txtAirMax, txtAirFactor, txtAirOffset, chkAirBernoulli, txtAirStep, chkAirTemp, txtAirUnit
        )
        setSliderFields(
            s.drum.copy(
                min = sourceSliderStepConfig.drumChannel?.min ?: s.drum.min,
                max = sourceSliderStepConfig.drumChannel?.max ?: s.drum.max,
                factor = sourceSliderStepConfig.drumChannel?.factor ?: s.drum.factor,
                offset = sourceSliderStepConfig.drumChannel?.offset ?: s.drum.offset,
                step = sourceSliderStepConfig.drumChannel?.step ?: s.drum.step
            ),
            txtDrumCommand, txtDrumMin, txtDrumMax, txtDrumFactor, txtDrumOffset, chkDrumBernoulli, txtDrumStep, chkDrumTemp, txtDrumUnit
        )
        setSliderFields(s.damper, txtDamperCommand, txtDamperMin, txtDamperMax, txtDamperFactor, txtDamperOffset, chkDamperBernoulli, txtDamperStep, chkDamperTemp, txtDamperUnit)
        setSliderFields(
            s.burner.copy(
                min = sourceSliderStepConfig.gasChannel?.min ?: s.burner.min,
                max = sourceSliderStepConfig.gasChannel?.max ?: s.burner.max,
                factor = sourceSliderStepConfig.gasChannel?.factor ?: s.burner.factor,
                offset = sourceSliderStepConfig.gasChannel?.offset ?: s.burner.offset,
                step = sourceSliderStepConfig.gasChannel?.step ?: s.burner.step
            ),
            txtBurnerCommand, txtBurnerMin, txtBurnerMax, txtBurnerFactor, txtBurnerOffset, chkBurnerBernoulli, txtBurnerStep, chkBurnerTemp, txtBurnerUnit
        )

        val quant = settings.eventQuantifiers
        setQuantifierRow(quant.air, cmbQuantAirSource, txtQuantAirSv, txtQuantAirMin, txtQuantAirMax, txtQuantAirStep, chkQuantAirAction)
        setQuantifierRow(quant.drum, cmbQuantDrumSource, txtQuantDrumSv, txtQuantDrumMin, txtQuantDrumMax, txtQuantDrumStep, chkQuantDrumAction)
        setQuantifierRow(quant.damper, cmbQuantDamperSource, txtQuantDamperSv, txtQuantDamperMin, txtQuantDamperMax, txtQuantDamperStep, chkQuantDamperAction)
        setQuantifierRow(quant.burner, cmbQuantBurnerSource, txtQuantBurnerSv, txtQuantBurnerMin, txtQuantBurnerMax, txtQuantBurnerStep, chkQuantBurnerAction)

        val loadedButtons = settings.customButtons.map {
            if (it.documentation.isBlank() && it.commandString.isNotBlank()) {
                it.copy(documentation = it.commandString)
            } else {
                it
            }
        }
        items.setAll(loadedButtons)
    }

    fun getResult(): EventButtonsDialogResult? = result

    private fun applyAndKeepOpen() {
        result = collectResult()
        applied = true
    }

    private fun collectResult(): EventButtonsDialogResult {
        val defaultAction = txtDefaultAction.text?.trim().orEmpty().ifBlank { "Modbus Command" }
        val defaultCommand = txtDefaultCommand.text?.trim().orEmpty()
        val normalizedButtons = items.map {
            val docs = it.documentation.trim()
            val command = docs.ifBlank { it.commandString.trim() }
            it.copy(
                eventType = it.eventType.trim(),
                eventValue = it.eventValue.coerceIn(0, 100),
                actionType = it.actionType.ifBlank { defaultAction },
                documentation = docs,
                commandString = command
            )
        }
        val eventCommands = mutableMapOf<String, String>()
        listOf(
            "CHARGE" to txtEventCharge.text,
            "DROP" to txtEventDrop.text,
            "DRY_END" to txtEventDryEnd.text,
            "FC_START" to txtEventFcStart.text,
            "COOL_END" to txtEventCoolEnd.text
        ).forEach { (key, value) ->
            val v = value?.trim().orEmpty()
            if (v.isNotEmpty()) eventCommands[key] = v
        }

        val sliders = EventSlidersConfig(
            air = parseSliderRow(txtAirCommand, txtAirMin, txtAirMax, txtAirFactor, txtAirOffset, chkAirBernoulli, txtAirStep, chkAirTemp, txtAirUnit),
            drum = parseSliderRow(txtDrumCommand, txtDrumMin, txtDrumMax, txtDrumFactor, txtDrumOffset, chkDrumBernoulli, txtDrumStep, chkDrumTemp, txtDrumUnit),
            damper = parseSliderRow(txtDamperCommand, txtDamperMin, txtDamperMax, txtDamperFactor, txtDamperOffset, chkDamperBernoulli, txtDamperStep, chkDamperTemp, txtDamperUnit),
            burner = parseSliderRow(txtBurnerCommand, txtBurnerMin, txtBurnerMax, txtBurnerFactor, txtBurnerOffset, chkBurnerBernoulli, txtBurnerStep, chkBurnerTemp, txtBurnerUnit)
        )
        val sliderStep = sourceSliderStepConfig.copy(
            airChannel = SliderChannelConfig(
                register = sourceSliderStepConfig.airChannel?.register ?: 0,
                min = sliders.air.min,
                max = sliders.air.max,
                factor = sliders.air.factor,
                offset = sliders.air.offset,
                step = sliders.air.step
            ),
            drumChannel = SliderChannelConfig(
                register = sourceSliderStepConfig.drumChannel?.register ?: 0,
                min = sliders.drum.min,
                max = sliders.drum.max,
                factor = sliders.drum.factor,
                offset = sliders.drum.offset,
                step = sliders.drum.step
            ),
            gasChannel = SliderChannelConfig(
                register = sourceSliderStepConfig.gasChannel?.register ?: 0,
                min = sliders.burner.min,
                max = sliders.burner.max,
                factor = sliders.burner.factor,
                offset = sliders.burner.offset,
                step = sliders.burner.step
            )
        )
        val quantifiers = EventQuantifiersConfig(
            air = parseQuantifierRow(cmbQuantAirSource, txtQuantAirSv, txtQuantAirMin, txtQuantAirMax, txtQuantAirStep, chkQuantAirAction),
            drum = parseQuantifierRow(cmbQuantDrumSource, txtQuantDrumSv, txtQuantDrumMin, txtQuantDrumMax, txtQuantDrumStep, chkQuantDrumAction),
            damper = parseQuantifierRow(cmbQuantDamperSource, txtQuantDamperSv, txtQuantDamperMin, txtQuantDamperMax, txtQuantDamperStep, chkQuantDamperAction),
            burner = parseQuantifierRow(cmbQuantBurnerSource, txtQuantBurnerSv, txtQuantBurnerMin, txtQuantBurnerMax, txtQuantBurnerStep, chkQuantBurnerAction)
        )
        val cfg = EventButtonsDialogConfig(
            eventType1Label = txtEventType1.text?.trim().orEmpty().ifBlank { "Air" },
            eventType2Label = txtEventType2.text?.trim().orEmpty().ifBlank { "Drum" },
            eventType3Label = txtEventType3.text?.trim().orEmpty().ifBlank { "Damper" },
            eventType4Label = txtEventType4.text?.trim().orEmpty().ifBlank { "Burner" },
            eventButtonEnabled = chkEventButton.isSelected,
            showOnBt = chkShowOnBt.isSelected,
            annotations = chkAnnotations.isSelected,
            phaseLines = chkPhaseLines.isSelected,
            timeGuide = chkTimeGuide.isSelected,
            defaultButtonAction = defaultAction,
            defaultButtonCommand = defaultCommand,
            autoMarkCharge = chkAutoCharge.isSelected,
            autoMarkDryEnd = chkAutoDryEnd.isSelected,
            autoMarkFirstCrack = chkAutoFirstCrack.isSelected,
            autoMarkDrop = chkAutoDrop.isSelected,
            maxButtonsPerRow = parseInt(txtMaxButtonsPerRow.text, 8).coerceIn(1, 24),
            buttonSize = when (cmbButtonSize.value) {
                "Tiny" -> ButtonSize.TINY
                "Large" -> ButtonSize.LARGE
                else -> ButtonSize.SMALL
            },
            colorPattern = parseInt(txtColorPattern.text, 0).coerceIn(0, 99),
            markLastPressed = chkMarkLastPressed.isSelected,
            tooltips = chkTooltips.isSelected,
            alternativeSliderLayout = chkAlternativeLayout.isSelected,
            keyboardControl = chkKeyboardControl.isSelected,
            quantifiersCluster = chkQuantCluster.isSelected
        )
        return EventButtonsDialogResult(
            customButtons = normalizedButtons,
            eventButtonsConfig = cfg,
            eventCommands = eventCommands,
            sliderStepConfig = sliderStep,
            eventSliders = sliders,
            eventQuantifiers = quantifiers
        )
    }

    private fun addOrEdit(existing: CustomButtonConfig?, insert: Boolean) {
        val template = existing ?: CustomButtonConfig(
            eventType = txtEventType1.text?.trim().orEmpty().ifBlank { "Air" },
            actionType = txtDefaultAction.text?.trim().orEmpty().ifBlank { "Modbus Command" },
            documentation = txtDefaultCommand.text?.trim().orEmpty(),
            commandString = txtDefaultCommand.text?.trim().orEmpty()
        )
        val dialog = Alert(Alert.AlertType.NONE).apply {
            title = if (existing == null) "Button" else "Edit Button"
            headerText = null
            buttonTypes.setAll(ButtonType.OK, ButtonType.CANCEL)
        }
        val labelField = TextField(template.label).apply { promptText = "Label"; prefWidth = 320.0 }
        val descField = TextField(template.description).apply { promptText = "Description"; prefWidth = 320.0 }
        val typeField = TextField(template.eventType).apply { promptText = "Air / Drum / Damper / Burner" }
        val valueField = TextField(template.eventValue.toString()).apply { promptText = "0-100" }
        val actionField = TextField(template.actionType).apply { promptText = "Action type" }
        val docsField = TextField(template.documentation.ifBlank { template.commandString }).apply {
            promptText = "Documentation / command"
            prefWidth = 320.0
        }
        val visibilityCheck = CheckBox("Visible").apply { isSelected = template.visibility }
        val bgColorPicker = ColorPicker(tryParseColor(template.backgroundColor))
        val textColorPicker = ColorPicker(tryParseColor(template.textColor))
        dialog.dialogPane.content = GridPane().apply {
            hgap = 8.0
            vgap = 8.0
            add(Label("Label:"), 0, 0); add(labelField, 1, 0)
            add(Label("Description:"), 0, 1); add(descField, 1, 1)
            add(Label("Type:"), 0, 2); add(typeField, 1, 2)
            add(Label("Value:"), 0, 3); add(valueField, 1, 3)
            add(Label("Action:"), 0, 4); add(actionField, 1, 4)
            add(Label("Documentation:"), 0, 5); add(docsField, 1, 5)
            add(Label("Visible:"), 0, 6); add(visibilityCheck, 1, 6)
            add(Label("Color:"), 0, 7); add(bgColorPicker, 1, 7)
            add(Label("Text color:"), 0, 8); add(textColorPicker, 1, 8)
        }
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return
        val docs = docsField.text?.trim().orEmpty()
        val config = template.copy(
            label = labelField.text?.trim().orEmpty(),
            description = descField.text?.trim().orEmpty(),
            eventType = typeField.text?.trim().orEmpty(),
            eventValue = parseInt(valueField.text, 0).coerceIn(0, 100),
            actionType = actionField.text?.trim().orEmpty().ifBlank { "Modbus Command" },
            documentation = docs,
            commandString = docs,
            visibility = visibilityCheck.isSelected,
            backgroundColor = toHex(bgColorPicker.value),
            textColor = toHex(textColorPicker.value)
        )
        if (existing != null) {
            val idx = items.indexOf(existing)
            if (idx >= 0) items[idx] = config
            return
        }
        val selected = table.selectionModel.selectedIndex
        if (insert && selected >= 0) items.add(selected, config) else items.add(config)
    }

    private fun copyTableToClipboard() {
        val rows = mutableListOf("Label\tDescription\tType\tValue\tAction\tDocumentation\tVisibility\tColor\tText Color")
        items.forEach {
            rows += listOf(
                it.label, it.description, it.eventType, it.eventValue.toString(), it.actionType,
                it.documentation.ifBlank { it.commandString }, if (it.visibility) "1" else "0",
                it.backgroundColor, it.textColor
            ).joinToString("\t")
        }
        val content = ClipboardContent()
        content.putString(rows.joinToString("\n"))
        Clipboard.getSystemClipboard().setContent(content)
    }

    private fun setSliderFields(
        cfg: EventSliderRowConfig,
        cmd: TextField,
        min: TextField,
        max: TextField,
        factor: TextField,
        offset: TextField,
        bernoulli: CheckBox,
        step: TextField,
        temp: CheckBox,
        unit: TextField
    ) {
        cmd.text = cfg.command
        min.text = cfg.min.toString()
        max.text = cfg.max.toString()
        factor.text = cfg.factor.toString()
        offset.text = cfg.offset.toString()
        bernoulli.isSelected = cfg.bernoulli
        step.text = cfg.step.toString()
        temp.isSelected = cfg.temp
        unit.text = cfg.unit
    }

    private fun parseSliderRow(
        cmd: TextField,
        min: TextField,
        max: TextField,
        factor: TextField,
        offset: TextField,
        bernoulli: CheckBox,
        step: TextField,
        temp: CheckBox,
        unit: TextField
    ): EventSliderRowConfig {
        return EventSliderRowConfig(
            command = cmd.text?.trim().orEmpty(),
            min = parseDouble(min.text, 0.0),
            max = parseDouble(max.text, 100.0),
            factor = parseDouble(factor.text, 1.0),
            offset = parseDouble(offset.text, 0.0),
            bernoulli = bernoulli.isSelected,
            step = parseDouble(step.text, 1.0),
            temp = temp.isSelected,
            unit = unit.text?.trim().orEmpty()
        )
    }

    private fun setQuantifierRow(
        cfg: EventQuantifierConfig,
        source: ComboBox<String>,
        sv: TextField,
        min: TextField,
        max: TextField,
        step: TextField,
        action: CheckBox
    ) {
        source.value = when (cfg.source) {
            QuantifierSource.ET -> "ET"
            QuantifierSource.BT -> "BT"
            else -> "None"
        }
        sv.text = cfg.sv.toString()
        min.text = cfg.min.toString()
        max.text = cfg.max.toString()
        step.text = cfg.step.toString()
        action.isSelected = cfg.actionEnabled
    }

    private fun parseQuantifierRow(
        source: ComboBox<String>,
        sv: TextField,
        min: TextField,
        max: TextField,
        step: TextField,
        action: CheckBox
    ): EventQuantifierConfig {
        return EventQuantifierConfig(
            source = when (source.value) {
                "ET" -> QuantifierSource.ET
                "BT" -> QuantifierSource.BT
                else -> QuantifierSource.NONE
            },
            sv = parseDouble(sv.text, 0.0),
            min = parseInt(min.text, 0),
            max = parseInt(max.text, 100),
            step = parseDouble(step.text, 5.0),
            actionEnabled = action.isSelected
        )
    }

    private fun parseInt(v: String?, fallback: Int): Int = v?.trim()?.toIntOrNull() ?: fallback
    private fun parseDouble(v: String?, fallback: Double): Double = v?.trim()?.toDoubleOrNull() ?: fallback

    private fun tryParseColor(hex: String): Color {
        return try { Color.web(hex.trim().ifBlank { "#e8e8e8" }) } catch (_: Exception) { Color.web("#e8e8e8") }
    }

    private fun toHex(c: Color): String {
        val r = (c.red * 255).toInt().coerceIn(0, 255)
        val g = (c.green * 255).toInt().coerceIn(0, 255)
        val b = (c.blue * 255).toInt().coerceIn(0, 255)
        return "#%02x%02x%02x".format(r, g, b)
    }
}
