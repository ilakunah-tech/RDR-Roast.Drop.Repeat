package com.rdr.roast.ui.graph

import com.rdr.roast.app.CurvesConfig
import com.rdr.roast.app.CurvesFiltersConfig
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.CheckBox
import javafx.scene.control.Spinner
import javafx.scene.control.TextField
import java.net.URL
import java.util.ResourceBundle

/**
 * Controller for the Curves Filters tab (Artisan-style): Input Filter, Curve Filter,
 * Display Filter, and Rate of Rise Filter. Loads from [CurvesConfig] and returns [CurvesFiltersConfig].
 */
class GraphFiltersTabController : Initializable {

    @FXML lateinit var chkInterpolateDuplicates: CheckBox
    @FXML lateinit var txtDropDuplicatesLimit: TextField
    @FXML lateinit var chkDropSpikes: CheckBox
    @FXML lateinit var chkSwapETBT: CheckBox
    @FXML lateinit var chkMinMaxLimits: CheckBox
    @FXML lateinit var txtFilterDropOutTmin: TextField
    @FXML lateinit var txtFilterDropOutTmax: TextField

    @FXML lateinit var spinnerCurveFilter: Spinner<Int>
    @FXML lateinit var chkFilterDropOuts: CheckBox

    @FXML lateinit var chkForegroundShowFull: CheckBox
    @FXML lateinit var chkInterpolateDrops: CheckBox

    @FXML lateinit var spinnerDeltaET: Spinner<Int>
    @FXML lateinit var spinnerDeltaBT: Spinner<Int>
    @FXML lateinit var chkPolyfitRoRcalc: CheckBox
    @FXML lateinit var chkOptimalSmoothing: CheckBox
    @FXML lateinit var chkRorLimitFlag: CheckBox
    @FXML lateinit var txtRorLimitMin: TextField
    @FXML lateinit var txtRorLimitMax: TextField

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        spinnerCurveFilter.valueFactory = javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory(0, 20, 1)
        spinnerDeltaET.valueFactory = javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory(1, 999, 15)
        spinnerDeltaBT.valueFactory = javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory(1, 999, 15)
    }

    /** Loads filter state from the given Curves config (typically [CurvesConfig.filters]). */
    fun loadFrom(config: CurvesConfig) {
        val f = config.filters
        chkInterpolateDuplicates.isSelected = f.interpolateDuplicates
        txtDropDuplicatesLimit.text = f.dropDuplicatesLimit.toString()
        chkDropSpikes.isSelected = f.dropSpikes
        chkSwapETBT.isSelected = f.swapETBT
        chkMinMaxLimits.isSelected = f.minMaxLimits
        txtFilterDropOutTmin.text = f.filterDropOutTmin.toString()
        txtFilterDropOutTmax.text = f.filterDropOutTmax.toString()
        spinnerCurveFilter.valueFactory.value = f.curvefilter
        chkFilterDropOuts.isSelected = f.filterDropOuts
        chkForegroundShowFull.isSelected = f.foregroundShowFullflag
        chkInterpolateDrops.isSelected = f.interpolateDropsflag
        spinnerDeltaET.valueFactory.value = f.deltaETfilter
        spinnerDeltaBT.valueFactory.value = f.deltaBTfilter
        chkPolyfitRoRcalc.isSelected = f.polyfitRoRcalc
        chkOptimalSmoothing.isSelected = f.optimalSmoothing
        chkRorLimitFlag.isSelected = f.rorLimitFlag
        txtRorLimitMin.text = f.rorLimitMin.toString()
        txtRorLimitMax.text = f.rorLimitMax.toString()
    }

    /** Returns current UI state as [CurvesFiltersConfig]. Uses defaults when numeric fields are invalid. */
    fun getResult(): CurvesFiltersConfig {
        return CurvesFiltersConfig(
            interpolateDuplicates = chkInterpolateDuplicates.isSelected,
            dropDuplicatesLimit = txtDropDuplicatesLimit.text.toDoubleOrNull() ?: CurvesFiltersConfig().dropDuplicatesLimit,
            dropSpikes = chkDropSpikes.isSelected,
            swapETBT = chkSwapETBT.isSelected,
            minMaxLimits = chkMinMaxLimits.isSelected,
            filterDropOutTmin = txtFilterDropOutTmin.text.toIntOrNull() ?: CurvesFiltersConfig().filterDropOutTmin,
            filterDropOutTmax = txtFilterDropOutTmax.text.toIntOrNull() ?: CurvesFiltersConfig().filterDropOutTmax,
            curvefilter = spinnerCurveFilter.value,
            filterDropOuts = chkFilterDropOuts.isSelected,
            foregroundShowFullflag = chkForegroundShowFull.isSelected,
            interpolateDropsflag = chkInterpolateDrops.isSelected,
            deltaETfilter = spinnerDeltaET.value,
            deltaBTfilter = spinnerDeltaBT.value,
            polyfitRoRcalc = chkPolyfitRoRcalc.isSelected,
            optimalSmoothing = chkOptimalSmoothing.isSelected,
            rorLimitFlag = chkRorLimitFlag.isSelected,
            rorLimitMin = txtRorLimitMin.text.toIntOrNull() ?: CurvesFiltersConfig().rorLimitMin,
            rorLimitMax = txtRorLimitMax.text.toIntOrNull() ?: CurvesFiltersConfig().rorLimitMax
        )
    }
}
