package com.rdr.roast.ui.control

import com.rdr.roast.app.AppearanceSupport
import com.rdr.roast.app.AppSettings
import com.rdr.roast.app.SettingsManager
import com.rdr.roast.app.SliderPanelLayoutMode
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.stage.Screen
import javafx.stage.Stage
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid
import org.kordamp.ikonli.javafx.FontIcon

/**
 * Detachable window hosting [ControlSliderPanel]. On close request hides instead of closing;
 * attach/detach state and position are persisted in [AppSettings].
 * Calls [onHidden] when the window is hidden (X or programmatic hide) so the main window can keep [sliderPanelVisible] in sync.
 */
class DetachableSliderWindow(
    private val panelProvider: () -> ControlSliderPanel,
    private val onAttachRequest: () -> Unit,
    private val onHidden: () -> Unit = {}
) {
    private var stage: Stage? = null
    private var contentPanel: ControlSliderPanel? = null

    fun isDetached(): Boolean = stage?.isShowing == true

    fun showDetached(owner: javafx.stage.Window?) {
        if (stage == null) {
            val settings = SettingsManager.load()
            stage = Stage().apply {
                title = "Control sliders"
                isResizable = true
                minWidth = 380.0
                minHeight = 320.0
                width = 400.0
                height = 380.0
                settings.sliderPanelDetachedX?.let { x = it }
                settings.sliderPanelDetachedY?.let { y = it }
                setOnCloseRequest { e ->
                    e.consume()
                    hideWindow()
                }
            }
        }
        val panel = panelProvider()
        contentPanel = panel
        val header = HBox(8.0).apply {
            alignment = Pos.CENTER_LEFT
            padding = Insets(6.0, 8.0, 6.0, 8.0)
            styleClass.add("detachable-slider-header")
            children.add(Label("Control sliders").apply {
                styleClass.add("detachable-slider-title")
                HBox.setHgrow(this, Priority.ALWAYS)
            })
            val btnAttach = Button().apply {
                graphic = FontIcon(FontAwesomeSolid.COMPRESS_ALT).apply { iconSize = 12 }
                tooltip = javafx.scene.control.Tooltip("Attach to main window")
                styleClass.add("detach-attach-btn")
                setOnAction {
                    persistPosition()
                    hideWindow(skipOnHidden = true)
                    onAttachRequest()
                }
            }
            children.add(btnAttach)
        }
        val root = BorderPane().apply {
            top = header
            center = panel
        }
        stage!!.scene = javafx.scene.Scene(root, 280.0, 380.0)
        owner?.scene?.stylesheets?.let { stage!!.scene.stylesheets.setAll(it) }
        if (stage!!.scene.stylesheets.isEmpty()) {
            DetachableSliderWindow::class.java.getResource("/com/rdr/roast/ui/main.css")?.toExternalForm()?.let { stage!!.scene.stylesheets.add(it) }
            DetachableSliderWindow::class.java.getResource("/css/appearance.css")?.toExternalForm()?.let { stage!!.scene.stylesheets.add(it) }
        }
        AppearanceSupport.applyToScene(stage!!.scene)
        owner?.let { stage!!.initOwner(it) }
        ensureOnScreen(owner)
        stage!!.show()
        stage!!.toFront()
        stage!!.requestFocus()
    }

    /**
     * Hides the detached window. When [skipOnHidden] is true (e.g. on Attach), [onHidden] is not called
     * so the main window can show the drawer instead of treating the panel as closed.
     */
    fun hideWindow(skipOnHidden: Boolean = false) {
        stage?.let {
            persistPosition()
            it.hide()
            if (!skipOnHidden) onHidden()
        }
    }

    private fun ensureOnScreen(owner: javafx.stage.Window?) {
        val s = stage ?: return
        val screen = Screen.getScreensForRectangle(s.x, s.y, s.width, s.height).firstOrNull()
        if (screen != null) return

        val targetX: Double
        val targetY: Double
        if (owner != null) {
            targetX = owner.x + (owner.width - s.width) / 2.0
            targetY = owner.y + (owner.height - s.height) / 2.0
        } else {
            val vb = Screen.getPrimary().visualBounds
            targetX = vb.minX + (vb.width - s.width) / 2.0
            targetY = vb.minY + (vb.height - s.height) / 2.0
        }
        s.x = targetX
        s.y = targetY
    }

    private fun persistPosition() {
        val s = stage ?: return
        if (!s.isShowing) return
        val current = SettingsManager.load()
        SettingsManager.save(current.copy(
            sliderPanelDetached = true,
            sliderPanelDetachedX = s.x,
            sliderPanelDetachedY = s.y
        ))
    }

    fun updateContent(panel: ControlSliderPanel) {
        contentPanel = panel
        stage?.scene?.root?.let { root ->
            if (root is BorderPane) {
                root.center = panel
            }
        }
    }

    fun applyStylesheets(stylesheets: List<String>) {
        stage?.scene?.let { scene ->
            scene.stylesheets.setAll(stylesheets)
            AppearanceSupport.applyToScene(scene)
        }
    }
}
