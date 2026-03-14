package com.rdr.roast.ui.graph

import com.rdr.roast.app.CurvesConfig
import com.rdr.roast.app.CurvesRorConfig
import com.rdr.roast.app.RorSmoothing
import javafx.fxml.FXML
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.control.TextField

class GraphRoRTabController {

    @FXML lateinit var chkDeltaET: CheckBox
    @FXML lateinit var chkDeltaBT: CheckBox
    @FXML lateinit var cmbDeltaETspan: ComboBox<String>
    @FXML lateinit var cmbDeltaBTspan: ComboBox<String>
    @FXML lateinit var chkETProject: CheckBox
    @FXML lateinit var chkBTProject: CheckBox
    @FXML lateinit var cmbProjectionMode: ComboBox<String>
    @FXML lateinit var chkProjectDelta: CheckBox
    @FXML lateinit var chkDeltaETlcd: CheckBox
    @FXML lateinit var chkDeltaBTlcd: CheckBox
    @FXML lateinit var chkSwapDeltaLcds: CheckBox
    @FXML lateinit var txtDeltaETfunction: TextField
    @FXML lateinit var txtDeltaBTfunction: TextField
    @FXML lateinit var cmbRorSmoothing: ComboBox<String>

    @FXML
    fun initialize() {
        val spanItems = (0..30).map { "${it}s" }
        cmbDeltaETspan.items.setAll(spanItems)
        cmbDeltaBTspan.items.setAll(spanItems)
        cmbProjectionMode.items.setAll("linear", "quadratic")
        cmbRorSmoothing.items.setAll(RorSmoothing.entries.map { it.name.lowercase().replace('_', ' ').replaceFirstChar { c -> c.uppercase() } })
    }

    fun loadFrom(config: CurvesConfig) {
        val ror = config.ror
        chkDeltaET.isSelected = ror.deltaET
        chkDeltaBT.isSelected = ror.deltaBT
        cmbDeltaETspan.selectionModel.select(ror.deltaETspanSec.coerceIn(0, 30))
        cmbDeltaBTspan.selectionModel.select(ror.deltaBTspanSec.coerceIn(0, 30))
        val smoothing = ror.rorSmoothing ?: RorSmoothing.RECOMMENDED
        val smoothingIndex = RorSmoothing.entries.indexOf(smoothing).coerceAtLeast(0)
        cmbRorSmoothing.selectionModel.select(smoothingIndex)
        chkETProject.isSelected = ror.etProjectFlag
        chkBTProject.isSelected = ror.btProjectFlag
        cmbProjectionMode.selectionModel.select(ror.projectionMode.coerceIn(0, 1))
        chkProjectDelta.isSelected = ror.projectDeltaFlag
        chkDeltaETlcd.isSelected = ror.deltaETlcd
        chkDeltaBTlcd.isSelected = ror.deltaBTlcd
        chkSwapDeltaLcds.isSelected = ror.swapdeltalcds
        txtDeltaETfunction.text = ror.deltaETfunction
        txtDeltaBTfunction.text = ror.deltaBTfunction
    }

    fun getResult(): CurvesRorConfig {
        val etSpanIndex = cmbDeltaETspan.selectionModel.selectedIndex
        val btSpanIndex = cmbDeltaBTspan.selectionModel.selectedIndex
        val projectionIndex = cmbProjectionMode.selectionModel.selectedIndex
        val smoothingIndex = cmbRorSmoothing.selectionModel.selectedIndex
        val rorSmoothing = if (smoothingIndex in RorSmoothing.entries.indices) RorSmoothing.entries[smoothingIndex] else RorSmoothing.RECOMMENDED
        return CurvesRorConfig(
            deltaET = chkDeltaET.isSelected,
            deltaBT = chkDeltaBT.isSelected,
            deltaETspanSec = if (etSpanIndex >= 0) etSpanIndex else 15,
            deltaBTspanSec = if (btSpanIndex >= 0) btSpanIndex else 15,
            deltaETlcd = chkDeltaETlcd.isSelected,
            deltaBTlcd = chkDeltaBTlcd.isSelected,
            swapdeltalcds = chkSwapDeltaLcds.isSelected,
            deltaETfunction = txtDeltaETfunction.text ?: "",
            deltaBTfunction = txtDeltaBTfunction.text ?: "",
            etProjectFlag = chkETProject.isSelected,
            btProjectFlag = chkBTProject.isSelected,
            projectDeltaFlag = chkProjectDelta.isSelected,
            projectionMode = if (projectionIndex >= 0) projectionIndex else 0,
            rorSmoothing = rorSmoothing
        )
    }
}
