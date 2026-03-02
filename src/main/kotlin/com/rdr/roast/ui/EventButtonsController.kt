package com.rdr.roast.ui

import com.rdr.roast.app.CustomButtonConfig
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.CheckBox
import javafx.scene.control.ColorPicker
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.scene.control.TextField
import javafx.scene.control.cell.PropertyValueFactory
import javafx.scene.paint.Color
import javafx.stage.Stage
import java.net.URL
import java.util.ResourceBundle

/**
 * Artisan-style "Events → Buttons" dialog. Table of custom buttons (Label, Description, Action, Command, Visibility, Color).
 * On OK the caller reads [getCustomButtons] and applies to settings (no direct persist from this dialog).
 */
class EventButtonsController : Initializable {

    @FXML lateinit var table: TableView<CustomButtonConfig>
    @FXML lateinit var colLabel: TableColumn<CustomButtonConfig, String>
    @FXML lateinit var colDescription: TableColumn<CustomButtonConfig, String>
    @FXML lateinit var colAction: TableColumn<CustomButtonConfig, String>
    @FXML lateinit var colCommand: TableColumn<CustomButtonConfig, String>
    @FXML lateinit var colVisibility: TableColumn<CustomButtonConfig, Boolean>
    @FXML lateinit var colBgColor: TableColumn<CustomButtonConfig, String>
    @FXML lateinit var btnAdd: Button
    @FXML lateinit var btnEdit: Button
    @FXML lateinit var btnDelete: Button
    @FXML lateinit var btnOk: Button
    @FXML lateinit var btnCancel: Button

    private val items = javafx.collections.FXCollections.observableArrayList<CustomButtonConfig>()

    /** Set to true when user clicked OK (caller checks and then reads [getCustomButtons]). */
    var applied: Boolean = false
        private set

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        table.items = items
        colLabel.setCellValueFactory(PropertyValueFactory("label"))
        colDescription.setCellValueFactory(PropertyValueFactory("description"))
        colAction.setCellValueFactory(PropertyValueFactory("actionType"))
        colCommand.setCellValueFactory(PropertyValueFactory("commandString"))
        colVisibility.setCellValueFactory(PropertyValueFactory("visibility"))
        colBgColor.setCellValueFactory(PropertyValueFactory("backgroundColor"))

        btnAdd.setOnAction { addOrEdit(null) }
        btnEdit.setOnAction {
            val sel = table.selectionModel.selectedItem ?: return@setOnAction
            addOrEdit(sel)
        }
        btnDelete.setOnAction {
            val sel = table.selectionModel.selectedItem ?: return@setOnAction
            items.remove(sel)
        }
        btnOk.setOnAction {
            applied = true
            (btnOk.scene?.window as? Stage)?.close()
        }
        btnCancel.setOnAction {
            (btnCancel.scene?.window as? Stage)?.close()
        }
    }

    /** Load initial list from settings (call before showAndWait). */
    fun loadFrom(buttons: List<CustomButtonConfig>) {
        items.setAll(buttons)
    }

    /** Current list for caller to save (after OK). */
    fun getCustomButtons(): List<CustomButtonConfig> = items.toList()

    private fun addOrEdit(existing: CustomButtonConfig?) {
        val dialog = Alert(Alert.AlertType.NONE).apply {
            title = if (existing == null) "Add button" else "Edit button"
            headerText = null
            buttonTypes.setAll(ButtonType.OK, ButtonType.CANCEL)
        }
        val labelField = TextField(existing?.label ?: "").apply { promptText = "Label"; prefWidth = 280.0 }
        val descField = TextField(existing?.description ?: "").apply { promptText = "Description"; prefWidth = 280.0 }
        val actionField = TextField(existing?.actionType ?: "Modbus Command").apply { promptText = "Action type"; prefWidth = 280.0 }
        val cmdField = TextField(existing?.commandString ?: "").apply {
            promptText = "write(1,1008,2);sleep(15)"
            prefWidth = 280.0
        }
        val visibilityCheck = CheckBox("Visible").apply { isSelected = existing?.visibility != false }
        val bgColorPicker = ColorPicker(tryParseColor(existing?.backgroundColor ?: "#e8e8e8"))
        val textColorPicker = ColorPicker(tryParseColor(existing?.textColor ?: "#333333"))
        dialog.dialogPane.content = javafx.scene.layout.GridPane().apply {
            hgap = 8.0
            vgap = 8.0
            add(javafx.scene.control.Label("Label:"), 0, 0)
            add(labelField, 1, 0)
            add(javafx.scene.control.Label("Description:"), 0, 1)
            add(descField, 1, 1)
            add(javafx.scene.control.Label("Action type:"), 0, 2)
            add(actionField, 1, 2)
            add(javafx.scene.control.Label("Command string:"), 0, 3)
            add(cmdField, 1, 3)
            add(javafx.scene.control.Label("Visible:"), 0, 4)
            add(visibilityCheck, 1, 4)
            add(javafx.scene.control.Label("Background:"), 0, 5)
            add(bgColorPicker, 1, 5)
            add(javafx.scene.control.Label("Text color:"), 0, 6)
            add(textColorPicker, 1, 6)
        }
        val result = dialog.showAndWait()
        if (result.orElse(ButtonType.CANCEL) != ButtonType.OK) return
        val config = CustomButtonConfig(
            label = labelField.text?.trim() ?: "",
            description = descField.text?.trim() ?: "",
            actionType = actionField.text?.trim()?.ifBlank { "Modbus Command" } ?: "Modbus Command",
            commandString = cmdField.text?.trim() ?: "",
            visibility = visibilityCheck.isSelected,
            backgroundColor = toHex(bgColorPicker.value),
            textColor = toHex(textColorPicker.value)
        )
        if (existing != null) {
            val idx = items.indexOf(existing)
            if (idx >= 0) items[idx] = config
        } else {
            items.add(config)
        }
    }

    private fun tryParseColor(hex: String): Color {
        return try {
            val s = hex.trim().removePrefix("#")
            when (s.length) {
                6 -> Color.web("#$s")
                8 -> Color.web("#$s")
                else -> Color.web("#e8e8e8")
            }
        } catch (_: Exception) {
            Color.web("#e8e8e8")
        }
    }

    private fun toHex(c: Color): String {
        val r = (c.red * 255).toInt().coerceIn(0, 255)
        val g = (c.green * 255).toInt().coerceIn(0, 255)
        val b = (c.blue * 255).toInt().coerceIn(0, 255)
        return "#%02x%02x%02x".format(r, g, b)
    }
}
