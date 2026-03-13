package com.rdr.roast.ui.graph

import com.rdr.roast.app.CurvesConfig
import com.rdr.roast.app.CurvesMathConfig
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.control.RadioButton
import javafx.scene.control.Spinner
import javafx.scene.control.SpinnerValueFactory
import javafx.scene.control.TextField
import javafx.scene.control.ToggleGroup

/**
 * Controller for the Curves Math tab (tab3): Interpolate, Univariate, ln(), Exponent, Polyfit.
 * Loads from [CurvesConfig] and returns [CurvesMathConfig] via [getResult].
 */
class GraphMathTabController {

    @FXML lateinit var chkInterpShow: CheckBox
    @FXML lateinit var cmbInterpKind: ComboBox<String>

    @FXML lateinit var chkUnivarShow: CheckBox
    @FXML lateinit var btnUnivarInfo: Button

    @FXML lateinit var chkLnShow: CheckBox
    @FXML lateinit var txtLnResult: TextField

    @FXML lateinit var chkExpShow: CheckBox
    @FXML lateinit var radExpX2: RadioButton
    @FXML lateinit var radExpX3: RadioButton
    @FXML lateinit var txtExpOffsetSec: TextField
    @FXML lateinit var txtExpResult: TextField
    @FXML lateinit var btnCreateBackgroundCurve: Button

    @FXML lateinit var chkPolyfitShow: CheckBox
    @FXML lateinit var txtPolyfitStartSec: TextField
    @FXML lateinit var txtPolyfitEndSec: TextField
    @FXML lateinit var spnPolyfitDeg: Spinner<Int>
    @FXML lateinit var cmbPolyfitC1: ComboBox<String>
    @FXML lateinit var cmbPolyfitC2: ComboBox<String>
    @FXML lateinit var txtPolyfitResult: TextField

    @FXML lateinit var expPowerGroup: ToggleGroup

    @FXML
    fun initialize() {
        cmbInterpKind.items.setAll("linear", "cubic", "nearest")
        cmbPolyfitC1.items.setAll("ET", "BT")
        cmbPolyfitC2.items.setAll("ET", "BT")
        spnPolyfitDeg.valueFactory = SpinnerValueFactory.IntegerSpinnerValueFactory(1, 4, 1)
        spnPolyfitDeg.isEditable = true

        btnUnivarInfo.setOnAction { showUnivarInfo() }
        btnCreateBackgroundCurve.setOnAction { onCreateBackgroundCurve() }
    }

    fun loadFrom(config: CurvesConfig) {
        val m = config.math
        chkInterpShow.isSelected = m.interpShow
        cmbInterpKind.value = m.interpKind.takeIf { it in listOf("linear", "cubic", "nearest") } ?: "linear"
        chkUnivarShow.isSelected = m.univarShow
        chkLnShow.isSelected = m.lnShow
        txtLnResult.text = m.lnResult
        chkExpShow.isSelected = m.expShow
        when (m.expPower) {
            3 -> radExpX3.isSelected = true
            else -> radExpX2.isSelected = true
        }
        txtExpOffsetSec.text = m.expOffsetSec.toString()
        txtExpResult.text = m.expResult
        chkPolyfitShow.isSelected = m.polyfitShow
        txtPolyfitStartSec.text = m.polyfitStartSec.toString()
        txtPolyfitEndSec.text = m.polyfitEndSec.toString()
        (spnPolyfitDeg.valueFactory as? SpinnerValueFactory.IntegerSpinnerValueFactory)?.value = m.polyfitDeg.coerceIn(1, 4)
        cmbPolyfitC1.value = m.polyfitC1.takeIf { it in listOf("ET", "BT") } ?: "ET"
        cmbPolyfitC2.value = m.polyfitC2.takeIf { it in listOf("ET", "BT") } ?: "BT"
        txtPolyfitResult.text = m.polyfitResult
    }

    fun getResult(): CurvesMathConfig {
        val expPower = if (radExpX3.isSelected) 3 else 2
        val polyfitStart = txtPolyfitStartSec.text.toDoubleOrNull() ?: 0.0
        val polyfitEnd = txtPolyfitEndSec.text.toDoubleOrNull() ?: 0.0
        val polyfitDeg = spnPolyfitDeg.value
        return CurvesMathConfig(
            interpShow = chkInterpShow.isSelected,
            interpKind = cmbInterpKind.value ?: "linear",
            univarShow = chkUnivarShow.isSelected,
            lnShow = chkLnShow.isSelected,
            lnResult = txtLnResult.text ?: "",
            expShow = chkExpShow.isSelected,
            expPower = expPower,
            expOffsetSec = txtExpOffsetSec.text.toIntOrNull()?.coerceIn(0, 86400) ?: 180,
            expResult = txtExpResult.text ?: "",
            polyfitShow = chkPolyfitShow.isSelected,
            polyfitStartSec = polyfitStart,
            polyfitEndSec = polyfitEnd,
            polyfitDeg = polyfitDeg,
            polyfitC1 = cmbPolyfitC1.value ?: "ET",
            polyfitC2 = cmbPolyfitC2.value ?: "BT",
            polyfitResult = txtPolyfitResult.text ?: ""
        )
    }

    private fun showUnivarInfo() {
        // Placeholder: parent or application service can show Univariate info dialog
    }

    private fun onCreateBackgroundCurve() {
        // Placeholder: parent or application service creates background curve from exponent
    }
}
