package com.rdr.roast.ui

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.VBox

/**
 * In-app keyboard shortcuts reference (aligned with Cropster RI where applicable).
 * Open via F1 or Help → Keyboard shortcuts.
 */
object HotkeysHelpView {

    private val modifier = if (System.getProperty("os.name").lowercase().contains("mac")) "⌘" else "Ctrl"

    private data class Row(val keys: String, val action: String)

    private fun rows(): List<Row> = listOf(
        Row("$modifier+Enter", "Start (Connect / Start / New roast)"),
        Row("Space", "Start при простое; Drop при записи"),
        Row("C", "Charge — отметить charge в текущее время"),
        Row("D", "Drop — отметить drop и завершить обжарку"),
        Row("$modifier+1", "First crack"),
        Row("$modifier+2", "Second crack"),
        Row("$modifier+3", "Dry end (color change)"),
        Row("$modifier+R", "Reset chart zoom"),
        Row("$modifier+Esc", "Прервать обжарку (во время записи)"),
        Row("F1", "Показать эту справку"),
    )

    fun createContent(): VBox {
        val title = Label("Клавиатурные сокращения").apply {
            styleClass.add("drawer-sheet-title")
        }
        val grid = GridPane().apply {
            styleClass.add("secondary-grid")
            hgap = 24.0
            vgap = 8.0
            padding = Insets(8.0, 16.0, 16.0, 16.0)
            alignment = Pos.TOP_LEFT
            columnConstraints.addAll(
                ColumnConstraints(100.0),  // keys column
                ColumnConstraints(280.0)   // description column
            )
        }
        rows().forEachIndexed { i, row ->
            grid.add(Label(row.keys).apply { styleClass.add("shortcut-keys") }, 0, i)
            grid.add(Label(row.action).apply { styleClass.add("secondary-hint-label") }, 1, i)
        }
        return VBox(12.0, title, grid).apply {
            styleClass.add("secondary-window-shell")
            padding = Insets(16.0)
            alignment = Pos.TOP_LEFT
        }
    }
}
