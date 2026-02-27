package com.rdr.roast

import javafx.application.Application
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.stage.Stage

class RoastApp : Application() {
    override fun start(primaryStage: Stage) {
        val loader = FXMLLoader(javaClass.getResource("/com/rdr/roast/ui/MainView.fxml"))
        val root = loader.load<javafx.scene.Parent>()
        primaryStage.title = "RDR - Roast.Drop" +
            ".Repeat"
        val scene = Scene(root, 1280.0, 800.0)
        scene.stylesheets.add(
            javaClass.getResource("/com/rdr/roast/ui/main.css")?.toExternalForm() ?: ""
        )
        primaryStage.scene = scene
        primaryStage.show()
    }
}

fun main() {
    Application.launch(RoastApp::class.java)
}
