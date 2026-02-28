package com.rdr.roast

import com.rdr.roast.app.AppearanceSupport
import com.rdr.roast.app.ThemeSupport
import javafx.application.Application
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.stage.Stage

class RoastApp : Application() {
    override fun start(primaryStage: Stage) {
        ThemeSupport.applyTheme(ThemeSupport.loadThemeId())
        val loader = FXMLLoader(javaClass.getResource("/com/rdr/roast/ui/MainView.fxml"))
        val root = loader.load<javafx.scene.Parent>()
        primaryStage.title = "RDR - Roast.Drop.Repeat"
        javaClass.getResource("/com/rdr/roast/app-icon.png")?.toExternalForm()?.let { url ->
            primaryStage.icons.add(Image(url))
        }
        val scene = Scene(root, 1280.0, 800.0)
        scene.stylesheets.add(
            javaClass.getResource("/com/rdr/roast/ui/main.css")?.toExternalForm() ?: ""
        )
        scene.stylesheets.add(
            javaClass.getResource("/css/custom.css")?.toExternalForm() ?: ""
        )
        scene.stylesheets.add(
            javaClass.getResource("/css/appearance.css")?.toExternalForm() ?: ""
        )
        AppearanceSupport.applyToScene(scene)
        primaryStage.scene = scene
        primaryStage.show()
    }
}

fun main() {
    Application.launch(RoastApp::class.java)
}
