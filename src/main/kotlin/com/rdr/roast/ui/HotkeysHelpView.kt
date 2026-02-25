package com.rdr.roast.ui

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.layout.GridPane
import javafx.scene.layout.VBox
import javafx.scene.text.Font
import javafx.scene.text.FontWeight

/**
 * In-app keyboard shortcuts reference (aligned with Cropster RI where applicable).
 * Open via F1 or Help → Keyboard shortcuts.
 */
object HotkeysHelpView {

    private val modifier = if (System.getProperty("os.name").lowercase().contains("mac")) "⌘" else "Ctrl"

    private data class Row(val keys: String, val action: String)

    private fun rows(): List<Row> = listOf(
        Row("$modifier+Enter", "Start roast (Connect / Start / New roast)"),
        Row("Space", "Start roast when idle; Drop when recording"),
        Row("$modifier+Escape", "Abort roast (while recording)"),
        Row("F1", "Show this shortcuts help"),
    )

    fun createContent(): VBox {
        val title = Label("Keyboard shortcuts").apply {
            font = Font.font(null, FontWeight.BOLD, 16.0)
        }
        val grid = GridPane().apply {
            hgap = 24.0
            vgap = 8.0
            padding = Insets(8.0, 16.0, 16.0, 16.0)
            alignment = Pos.TOP_LEFT
        }
        rows().forEachIndexed { i, row ->
            grid.add(Label(row.keys).apply { style = "-fx-font-family: monospace; -fx-font-weight: bold;" }, 0, i)
            grid.add(Label(row.action), 1, i)
        }
        return VBox(12.0, title, grid).apply {
            padding = Insets(16.0)
            alignment = Pos.TOP_LEFT
        }
    }
}
