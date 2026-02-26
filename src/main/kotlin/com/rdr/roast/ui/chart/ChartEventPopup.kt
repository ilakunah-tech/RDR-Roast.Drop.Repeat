package com.rdr.roast.ui.chart

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.Separator
import javafx.scene.control.TextField
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.Popup

/**
 * Context popup when the user clicks on the roast chart (Cropster-style).
 *
 * - Header: "Comment at: MM:SS"
 * - Event buttons: DE (Dry End), FC (First Crack) only
 * - Temperature °C field (optional; pre-filled with BT at click time if available)
 * - Custom comment + Add
 * - Close
 *
 * Charge = hotkey C; Drop = hotkey D; TP is auto-computed.
 */
class ChartEventPopup(
    private val timeMs: Long,
    private val btAtTime: Double?,
    private val onAdd: (timeMs: Long, label: String) -> Unit
) : Popup() {

    init {
        isAutoHide = true
        isAutoFix  = true
        val mmss = formatTime(timeMs)

        val root = VBox(8.0).apply {
            padding = Insets(12.0, 14.0, 12.0, 14.0)
            minWidth = 240.0
            maxWidth = 280.0
            style = """
                -fx-background-color: white;
                -fx-border-color: #c0c0c0;
                -fx-border-radius: 6;
                -fx-background-radius: 6;
                -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.22), 10, 0, 0, 3);
            """.trimIndent()
        }

        val lblAt   = Label("Комментарий в:").apply { style = "-fx-font-size: 11px; -fx-text-fill: #888;" }
        val lblTime = Label(mmss).apply { style = "-fx-font-size: 14px; -fx-font-weight: bold;" }
        val lblUnit = Label("mm:ss").apply { style = "-fx-font-size: 10px; -fx-text-fill: #bbb;" }
        root.children += HBox(6.0, lblAt, lblTime, lblUnit).apply { alignment = Pos.CENTER_LEFT }
        root.children += Separator()

        // DE (Dry End), FC (First Crack)
        val grid = GridPane().apply { hgap = 6.0; vgap = 5.0 }
        // Temperature °C (Cropster: event at a specific temperature)
        val lblTemp = Label("Температура °C:").apply { style = "-fx-font-size: 11px; -fx-text-fill: #888;" }
        val txtTemp = TextField().apply {
            promptText = "optional"
            style = "-fx-font-size: 11px;"
            if (btAtTime != null) text = "%.1f".format(btAtTime)
        }
        root.children += HBox(6.0, lblTemp, txtTemp).also { row ->
            row.alignment = Pos.CENTER_LEFT
            HBox.setHgrow(txtTemp, Priority.ALWAYS)
        }

        fun labelWithTemp(base: String, tempStr: String): String {
            val t = tempStr.trim().toDoubleOrNull()
            return if (t != null) "$base · $t °C" else base
        }

        val btnDE = Button("DE (Dry End)").apply {
            maxWidth = Double.MAX_VALUE
            style = greenButtonStyle()
            setOnAction {
                onAdd(timeMs, labelWithTemp("DE @ $mmss", txtTemp.text))
                hide()
            }
        }
        val btnFC = Button("FC (First Crack)").apply {
            maxWidth = Double.MAX_VALUE
            style = greenButtonStyle()
            setOnAction {
                onAdd(timeMs, labelWithTemp("FC @ $mmss", txtTemp.text))
                hide()
            }
        }
        grid.add(btnDE, 0, 0)
        grid.add(btnFC, 1, 0)
        root.children += grid
        root.children += Separator()

        val txtComment = TextField().apply {
            promptText = "Свой комментарий…"
            style = "-fx-font-size: 11px;"
        }
        val btnAdd = Button("Добавить").apply {
            style = """
                -fx-background-color: #3498db;
                -fx-text-fill: white;
                -fx-font-size: 11px;
                -fx-background-radius: 3;
            """.trimIndent()
            setOnAction {
                val txt = txtComment.text.trim()
                if (txt.isNotEmpty()) {
                    onAdd(timeMs, labelWithTemp(txt, txtTemp.text))
                    hide()
                }
            }
        }
        root.children += HBox(6.0, txtComment, btnAdd).also { row ->
            row.alignment = Pos.CENTER_LEFT
            HBox.setHgrow(txtComment, Priority.ALWAYS)
        }

        val btnClose = Button("Закрыть").apply {
            maxWidth = Double.MAX_VALUE
            style = """
                -fx-background-color: #ecf0f1;
                -fx-text-fill: #555;
                -fx-font-size: 11px;
                -fx-background-radius: 3;
            """.trimIndent()
            setOnAction { hide() }
        }
        root.children += btnClose
        content.add(root)
    }

    private fun greenButtonStyle() = """
        -fx-background-color: #27ae60;
        -fx-text-fill: white;
        -fx-font-size: 11px;
        -fx-background-radius: 3;
        -fx-cursor: hand;
    """.trimIndent()

    private fun formatTime(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return "%02d:%02d".format(s / 60, s % 60)
    }
}
