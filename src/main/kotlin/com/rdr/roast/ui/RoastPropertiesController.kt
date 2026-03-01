package com.rdr.roast.ui

import com.rdr.roast.app.CatalogApi
import com.rdr.roast.app.ProfileStorage
import com.rdr.roast.app.ReferenceApi
import com.rdr.roast.app.ReferenceItem
import com.rdr.roast.app.ServerConfig
import com.rdr.roast.app.SettingsManager
import com.rdr.roast.domain.RoastProfile
import javafx.fxml.FXML
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip
import javafx.stage.FileChooser
import javafx.stage.Stage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.javafx.JavaFx

class RoastPropertiesController {

    @FXML lateinit var txtTitle: TextField
    @FXML lateinit var cmbStock: ComboBox<String>
    @FXML lateinit var cmbBlend: ComboBox<String>
    @FXML lateinit var cmbReference: ComboBox<String>
    @FXML lateinit var txtWeightIn: TextField
    @FXML lateinit var txtWeightOut: TextField
    @FXML lateinit var txtBeans: TextArea
    @FXML lateinit var btnOk: Button
    @FXML lateinit var btnCancel: Button
    @FXML lateinit var btnLoadFile: Button

    private var stage: Stage? = null
    private var loadedFileProfile: RoastProfile? = null
    private var loadedFileName: String? = null
    private val scope = CoroutineScope(Dispatchers.JavaFx + Job())
    private var referenceItems: List<ReferenceItem> = emptyList()
    private var stockItems: List<CatalogApi.StockItem> = emptyList()
    private var blendItems: List<CatalogApi.BlendItem> = emptyList()

    /** Called when user clicks OK with (savedState, referenceProfileOrNull). */
    var onApply: ((RoastPropertiesState, RoastProfile?) -> Unit)? = null
    /** When in drawer mode: called on Cancel or after OK to close the drawer. */
    var onCloseDrawer: (() -> Unit)? = null

    data class RoastPropertiesState(
        val title: String,
        val referenceId: String,
        val stockId: String,
        val blendId: String,
        val weightInKg: Double,
        val weightOutKg: Double,
        val beansNotes: String
    )

    fun setStage(s: Stage?) { stage = s }

    @FXML
    fun initialize() {
        btnOk.setOnAction { applyAndClose() }
        btnCancel.setOnAction {
            stage?.close()
            onCloseDrawer?.invoke()
        }
        btnLoadFile.tooltip = Tooltip("Загрузить профиль .alog с компьютера")
        btnLoadFile.setOnAction { loadProfileFromFile() }
    }

    private fun loadProfileFromFile() {
        val chooser = FileChooser().apply {
            title = "Загрузить профиль .alog"
            extensionFilters.add(FileChooser.ExtensionFilter("Artisan profile", "*.alog"))
        }
        val window = btnLoadFile.scene?.window
        val file = chooser.showOpenDialog(window) ?: return
        try {
            val profile = ProfileStorage.loadProfile(file.toPath())
            loadedFileProfile = profile
            loadedFileName = file.name
            onApply?.invoke(
                RoastPropertiesState(
                    title = txtTitle.text?.trim() ?: "",
                    referenceId = "",
                    stockId = "",
                    blendId = "",
                    weightInKg = txtWeightIn.text?.toDoubleOrNull() ?: 0.0,
                    weightOutKg = txtWeightOut.text?.toDoubleOrNull() ?: 0.0,
                    beansNotes = txtBeans.text?.trim() ?: ""
                ),
                profile
            )
        } catch (e: Exception) {
            Alert(Alert.AlertType.ERROR).apply {
                title = "Ошибка загрузки"
                headerText = "Не удалось загрузить профиль"
                contentText = e.message ?: e.toString()
            }.showAndWait()
        }
    }

    fun loadFromSettings() {
        val s = SettingsManager.load()
        txtTitle.text = s.roastPropertiesTitle
        txtWeightIn.text = if (s.roastPropertiesWeightInKg > 0) "%.2f".format(s.roastPropertiesWeightInKg) else ""
        txtWeightOut.text = if (s.roastPropertiesWeightOutKg > 0) "%.2f".format(s.roastPropertiesWeightOutKg) else ""
        txtBeans.text = s.roastPropertiesBeansNotes
    }

    /** Fill title and weight from a schedule item (e.g. when user selects a plan row). */
    fun setFromSchedule(title: String, weightKg: Double?) {
        txtTitle.text = title
        txtWeightIn.text = weightKg?.let { "%.2f".format(it) } ?: ""
    }

    private fun selectStockAndBlendInCombos(stockId: String, blendId: String) {
        if (stockId.isNotBlank()) {
            val idx = stockItems.indexOfFirst { it.id == stockId }
            if (idx >= 0) cmbStock.selectionModel.select(idx) else cmbStock.selectionModel.selectFirst()
        } else {
            cmbStock.selectionModel.selectFirst()
        }
        if (blendId.isNotBlank()) {
            val idx = blendItems.indexOfFirst { it.id == blendId }
            if (idx >= 0) cmbBlend.selectionModel.select(idx) else cmbBlend.selectionModel.selectFirst()
        } else {
            cmbBlend.selectionModel.selectFirst()
        }
    }

    fun loadStockAndBlends() {
        val settings = SettingsManager.load()
        val baseUrl = ServerConfig.API_BASE_URL.trim().removeSuffix("/")
        val token = settings.serverToken.takeIf { it.isNotBlank() }
        if (token == null) {
            cmbStock.items.clear()
            cmbStock.items.add("— Не выбрано —")
            cmbBlend.items.clear()
            cmbBlend.items.add("— Не выбрано —")
            return
        }
        scope.launch {
            val data = withContext(Dispatchers.IO) {
                try {
                    CatalogApi.getStockAndBlends(baseUrl, token!!)
                } catch (_: Exception) {
                    CatalogApi.StockAndBlends(emptyList(), emptyList())
                }
            }
            withContext(Dispatchers.JavaFx) {
                stockItems = data.coffees
                blendItems = data.blends
                cmbStock.items.clear()
                cmbStock.items.add("— Не выбрано —")
                data.coffees.forEach { cmbStock.items.add(it.label) }
                cmbBlend.items.clear()
                cmbBlend.items.add("— Не выбрано —")
                data.blends.forEach { cmbBlend.items.add(it.label) }
                selectStockAndBlendInCombos(settings.roastPropertiesStockId, settings.roastPropertiesBlendId)
            }
        }
    }

    fun loadReferencesAndSelect() {
        val settings = SettingsManager.load()
        val baseUrl = ServerConfig.API_BASE_URL.trim().removeSuffix("/")
        if (baseUrl.isEmpty()) return
        scope.launch {
            val items = withContext(Dispatchers.IO) {
                try {
                    ReferenceApi.listReferences(
                        baseUrl = baseUrl,
                        token = settings.serverToken.takeIf { it.isNotBlank() }
                    )
                } catch (_: Exception) {
                    emptyList()
                }
            }
            withContext(Dispatchers.JavaFx) {
                referenceItems = items
                cmbReference.items.clear()
                cmbReference.items.add("— Без эталона —")
                items.forEach { cmbReference.items.add("${it.label} (${it.id.take(8)}…)") }
                val currentId = settings.roastPropertiesReferenceId
                if (currentId.isNotBlank()) {
                    val idx = items.indexOfFirst { it.id == currentId }
                    if (idx >= 0) cmbReference.selectionModel.select(idx + 1)
                    else cmbReference.selectionModel.selectFirst()
                } else {
                    cmbReference.selectionModel.selectFirst()
                }
            }
        }
    }

    private fun applyAndClose() {
        val title = txtTitle.text?.trim() ?: ""
        val weightIn = txtWeightIn.text?.toDoubleOrNull()?.coerceIn(0.0, 9999.0) ?: 0.0
        val weightOut = txtWeightOut.text?.toDoubleOrNull()?.coerceIn(0.0, 9999.0) ?: 0.0
        val beansNotes = txtBeans.text?.trim() ?: ""
        val refIdx = cmbReference.selectionModel.selectedIndex
        val referenceId = when {
            refIdx <= 0 -> ""
            refIdx - 1 in referenceItems.indices -> referenceItems[refIdx - 1].id
            else -> ""
        }
        val stockIdx = cmbStock.selectionModel.selectedIndex
        val stockId = when {
            stockIdx <= 0 -> ""
            stockIdx - 1 in stockItems.indices -> stockItems[stockIdx - 1].id
            else -> ""
        }
        val blendIdx = cmbBlend.selectionModel.selectedIndex
        val blendId = when {
            blendIdx <= 0 -> ""
            blendIdx - 1 in blendItems.indices -> blendItems[blendIdx - 1].id
            else -> ""
        }
        val state = RoastPropertiesState(
            title = title,
            referenceId = referenceId,
            stockId = stockId,
            blendId = blendId,
            weightInKg = weightIn,
            weightOutKg = weightOut,
            beansNotes = beansNotes
        )
        val settings = SettingsManager.load()
        val newSettings = settings.copy(
            roastPropertiesTitle = state.title,
            roastPropertiesReferenceId = state.referenceId,
            roastPropertiesStockId = state.stockId,
            roastPropertiesBlendId = state.blendId,
            roastPropertiesWeightInKg = state.weightInKg,
            roastPropertiesWeightOutKg = state.weightOutKg,
            roastPropertiesBeansNotes = state.beansNotes
        )
        SettingsManager.save(newSettings)

        if (referenceId.isNotBlank()) {
            scope.launch {
                val baseUrl = ServerConfig.API_BASE_URL.trim().removeSuffix("/")
                val token = settings.serverToken.takeIf { it.isNotBlank() }
                val profile = withContext(Dispatchers.IO) {
                    try {
                        ReferenceApi.getProfileData(baseUrl, referenceId, token)
                    } catch (_: Exception) {
                        null
                    }
                }
                withContext(Dispatchers.JavaFx) {
                    onApply?.invoke(state, profile)
                    stage?.close()
                    onCloseDrawer?.invoke()
                }
            }
        } else {
            onApply?.invoke(state, null)
            stage?.close()
            onCloseDrawer?.invoke()
        }
    }
}
