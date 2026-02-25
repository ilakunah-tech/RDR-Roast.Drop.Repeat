package com.rdr.roast.ui

import com.rdr.roast.domain.EventType
import com.rdr.roast.domain.RoastProfile
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.stage.Stage

/**
 * Controller for the "Finish roast" dialog (Cropster-style after Stop/Drop).
 * User can adjust start/end time, weights, notes, then Save (.alog to folder from settings) or Don't save.
 */
class FinishRoastController {

    @FXML lateinit var txtStartTime: TextField
    @FXML lateinit var txtEndTime: TextField
    @FXML lateinit var txtStartWeight: TextField
    @FXML lateinit var txtEndWeight: TextField
    @FXML lateinit var lblWeightLoss: Label
    @FXML lateinit var txtNotes: TextArea
    @FXML lateinit var btnSave: Button
    @FXML lateinit var btnDontSave: Button

    private var stage: Stage? = null

    /** Called when user clicks Save; caller should save profile and hide panel. */
    var onSave: (() -> Unit)? = null
    /** Called when user clicks Don't save; caller should hide panel (inline) or close stage. */
    var onDontSave: (() -> Unit)? = null

    fun setStage(stage: Stage?) {
        this.stage = stage
    }

    fun setProfile(profile: RoastProfile) {
        val startSec = 0.0
        val endSec = profile.eventByType(EventType.DROP)?.timeSec
            ?: profile.timex.maxOrNull()
            ?: 0.0
        txtStartTime.text = formatMmSs(startSec)
        txtEndTime.text = formatMmSs(endSec)
        txtStartWeight.text = ""
        txtEndWeight.text = ""
        txtNotes.text = ""
    }

    @FXML
    fun initialize() {
        btnSave.setOnAction {
            onSave?.invoke()
            stage?.close()
        }
        btnDontSave.setOnAction {
            onDontSave?.invoke()
            stage?.close()
        }
        listOf(txtStartWeight, txtEndWeight).forEach { tf ->
            tf.textProperty().addListener { _, _, _ -> updateWeightLossLabel() }
        }
    }

    private fun updateWeightLossLabel() {
        val start = txtStartWeight.text.toDoubleOrNull()
        val end = txtEndWeight.text.toDoubleOrNull()
        lblWeightLoss.text = when {
            start != null && end != null && start > 0 -> "%.1f%%".format((start - end) / start * 100)
            else -> "—"
        }
    }

    private fun formatMmSs(sec: Double): String {
        val m = (sec / 60).toInt()
        val s = (sec % 60).toInt()
        return "%02d:%02d".format(m, s)
    }
}
