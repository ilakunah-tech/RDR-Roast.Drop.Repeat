package com.rdr.roast

import com.rdr.roast.app.SettingsManager
import com.rdr.roast.app.ThemeService
import javafx.application.Application
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.stage.Stage
import javafx.stage.StageStyle

class RoastApp : Application() {
    private fun loadStylesheet(path: String): String {
        return javaClass.getResource(path)?.toExternalForm()
            ?: throw IllegalStateException("Required stylesheet not found: $path")
    }

    override fun start(primaryStage: Stage) {
        primaryStage.initStyle(StageStyle.UNDECORATED)
        var settings = SettingsManager.load()
        val migratedTheme = ThemeService.migrateFromPreferences(settings.themeSettings)
        if (migratedTheme != settings.themeSettings) {
            settings = settings.copy(themeSettings = migratedTheme)
            SettingsManager.save(settings)
        }
        ThemeService.applyTheme(settings.themeSettings, null)
        val loader = FXMLLoader(javaClass.getResource("/com/rdr/roast/ui/MainView.fxml"))
        val root = loader.load<javafx.scene.Parent>()
        primaryStage.title = "RDR - Roast.Drop.Repeat"
        primaryStage.minWidth = 900.0
        primaryStage.minHeight = 720.0
        javaClass.getResource("/com/rdr/roast/app-icon.png")?.toExternalForm()?.let { url ->
            primaryStage.icons.add(Image(url))
        }
        val scene = Scene(root, 1280.0, 800.0)
        scene.stylesheets.add(loadStylesheet("/com/rdr/roast/ui/main.css"))
        val customCss = javaClass.getResource("/css/custom.css")?.toExternalForm()
        if (customCss != null) {
            scene.stylesheets.add(customCss)
        } else {
            println("Warning: Optional stylesheet not found: /css/custom.css")
        }
        scene.stylesheets.add(loadStylesheet("/css/appearance.css"))
        ThemeService.applyTheme(settings.themeSettings, scene)
        primaryStage.scene = scene
        primaryStage.show()
    }
}

fun main() {
    Application.launch(RoastApp::class.java)
}
