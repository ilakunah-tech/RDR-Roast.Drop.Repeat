package com.rdr.roast.ui.chart

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.Separator
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.Popup

enum class ChartPopupMode { ROAST, BBP }

sealed interface ChartPopupResult {
    val timeMs: Long
}

data class ChartPopupEventResult(
    override val timeMs: Long,
    val label: String
) : ChartPopupResult

data class ChartPopupCommentResult(
    override val timeMs: Long,
    val text: String,
    val tempBT: Double?,
    val gas: Double? = null,
    val airflow: Double? = null
) : ChartPopupResult

/**
 * Context popup when the user clicks on the roast or BBP chart.
 */
class ChartEventPopup(
    private val mode: ChartPopupMode,
    private val timeMs: Long,
    private val btAtTime: Double?,
    private val onAdd: (ChartPopupResult) -> Unit
) : Popup() {

    init {
        isAutoHide = true
        isAutoFix = true
        val mmss = formatTime(timeMs)

        val root = VBox(8.0).apply {
            padding = Insets(10.0, 12.0, 10.0, 12.0)
            minWidth = 220.0
            maxWidth = 300.0
            style = """
                -fx-background-color: white;
                -fx-border-color: #c0c0c0;
                -fx-border-radius: 6;
                -fx-background-radius: 6;
                -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.22), 10, 0, 0, 3);
            """.trimIndent()
        }

        val lblAt = Label(if (mode == ChartPopupMode.BBP) "Between batch comment at" else "Comment at").apply {
            style = "-fx-font-size: 11px; -fx-text-fill: #888;"
        }
        val lblTime = Label(mmss).apply { style = "-fx-font-size: 14px; -fx-font-weight: bold;" }
        val lblUnit = Label("mm:ss").apply { style = "-fx-font-size: 10px; -fx-text-fill: #bbb;" }
        root.children += HBox(6.0, lblAt, lblTime, lblUnit).apply { alignment = Pos.CENTER_LEFT }
        btAtTime?.let { bt ->
            root.children += Label("BT ${"%.1f".format(bt)} °C").apply {
                style = "-fx-font-size: 11px; -fx-text-fill: #666;"
            }
        }
        root.children += Separator()

        when (mode) {
            ChartPopupMode.ROAST -> buildRoastContent(root, mmss)
            ChartPopupMode.BBP -> buildBbpContent(root)
        }

        val btnClose = Button("Cancel").apply {
            maxWidth = Double.MAX_VALUE
            style = secondaryButtonStyle()
            setOnAction { hide() }
        }
        root.children += btnClose
        content.add(root)
    }

    private fun buildRoastContent(root: VBox, mmss: String) {
        val actions = VBox(6.0)
        val btnDE = Button("Dry end").apply {
            maxWidth = Double.MAX_VALUE
            style = greenButtonStyle()
            setOnAction {
                onAdd(ChartPopupEventResult(timeMs, "DE @ $mmss"))
                hide()
            }
        }
        val btnFC = Button("First crack").apply {
            maxWidth = Double.MAX_VALUE
            style = greenButtonStyle()
            setOnAction {
                onAdd(ChartPopupEventResult(timeMs, "FC @ $mmss"))
                hide()
            }
        }
        actions.children.addAll(btnDE, btnFC)
        root.children += actions
        root.children += Separator()

        val txtComment = TextField().apply {
            promptText = "Comment"
            style = "-fx-font-size: 11px;"
        }
        val btnAdd = Button("Add comment").apply {
            style = primaryButtonStyle()
            setOnAction {
                val txt = txtComment.text.trim()
                if (txt.isNotEmpty()) {
                    onAdd(ChartPopupCommentResult(timeMs, txt, btAtTime))
                    hide()
                }
            }
        }
        root.children += HBox(6.0, txtComment, btnAdd).also { row ->
            row.alignment = Pos.CENTER_LEFT
            HBox.setHgrow(txtComment, Priority.ALWAYS)
        }
    }

    private fun buildBbpContent(root: VBox) {
        val txtGas = TextField().apply {
            promptText = "Gas"
            style = "-fx-font-size: 11px;"
        }
        val txtAirflow = TextField().apply {
            promptText = "Airflow"
            style = "-fx-font-size: 11px;"
        }
        root.children += HBox(6.0, txtGas, txtAirflow).also { row ->
            row.alignment = Pos.CENTER_LEFT
            HBox.setHgrow(txtGas, Priority.ALWAYS)
            HBox.setHgrow(txtAirflow, Priority.ALWAYS)
        }

        val txtComment = TextField().apply {
            promptText = "Comment"
            style = "-fx-font-size: 11px;"
        }
        val btnAdd = Button("Add comment").apply {
            style = primaryButtonStyle()
            setOnAction {
                val gas = txtGas.text.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
                val airflow = txtAirflow.text.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
                val txt = txtComment.text.trim()
                if (txt.isNotEmpty() || gas != null || airflow != null) {
                    onAdd(ChartPopupCommentResult(timeMs, txt, btAtTime, gas = gas, airflow = airflow))
                    hide()
                }
            }
        }
        root.children += HBox(6.0, txtComment, btnAdd).also { row ->
            row.alignment = Pos.CENTER_LEFT
            HBox.setHgrow(txtComment, Priority.ALWAYS)
        }
    }

    private fun greenButtonStyle() = """
        -fx-background-color: #27ae60;
        -fx-text-fill: white;
        -fx-font-size: 11px;
        -fx-background-radius: 3;
        -fx-cursor: hand;
    """.trimIndent()

    private fun primaryButtonStyle() = """
        -fx-background-color: #3498db;
        -fx-text-fill: white;
        -fx-font-size: 11px;
        -fx-background-radius: 3;
    """.trimIndent()

    private fun secondaryButtonStyle() = """
        -fx-background-color: #ecf0f1;
        -fx-text-fill: #555;
        -fx-font-size: 11px;
        -fx-background-radius: 3;
    """.trimIndent()

    private fun formatTime(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return "%02d:%02d".format(s / 60, s % 60)
    }
}
