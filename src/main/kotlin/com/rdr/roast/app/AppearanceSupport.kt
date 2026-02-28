package com.rdr.roast.app

import javafx.scene.Scene
import javafx.scene.paint.Color
import java.util.prefs.Preferences

object AppearanceSupport {
    private const val PREF_UI_SCALE = "uiScale"
    private const val PREF_FONT_SIZE = "fontSize"
    private const val PREF_DENSITY = "density"
    private const val PREF_ACCENT_COLOR = "accentColor"

    private const val DEFAULT_SCALE = 1.0
    private const val DEFAULT_FONT_SIZE = "normal"
    private const val DEFAULT_DENSITY = "normal"
    private const val DEFAULT_ACCENT = ""

    private val scaleEntries = listOf(1.0 to "100%", 1.1 to "110%", 1.25 to "125%")
    private val fontSizeEntries = listOf(
        "compact" to "Компактный (12px)",
        "normal" to "Обычный (14px)",
        "large" to "Крупный (16px)"
    )
    private val densityEntries = listOf(
        "compact" to "Компактный",
        "normal" to "Обычный"
    )

    private fun prefs(): Preferences =
        Preferences.userNodeForPackage(com.rdr.roast.RoastApp::class.java)

    fun loadScale(): Double = prefs().get(PREF_UI_SCALE, DEFAULT_SCALE.toString()).toDoubleOrNull() ?: DEFAULT_SCALE
    fun saveScale(scale: Double) { prefs().put(PREF_UI_SCALE, scale.toString()) }

    fun loadFontSize(): String = prefs().get(PREF_FONT_SIZE, DEFAULT_FONT_SIZE).takeIf { it in listOf("compact", "normal", "large") } ?: DEFAULT_FONT_SIZE
    fun saveFontSize(value: String) { prefs().put(PREF_FONT_SIZE, value) }

    fun loadDensity(): String = prefs().get(PREF_DENSITY, DEFAULT_DENSITY).takeIf { it in listOf("compact", "normal") } ?: DEFAULT_DENSITY
    fun saveDensity(value: String) { prefs().put(PREF_DENSITY, value) }

    fun loadAccentColor(): String = prefs().get(PREF_ACCENT_COLOR, DEFAULT_ACCENT)
    fun saveAccentColor(hex: String) { prefs().put(PREF_ACCENT_COLOR, hex) }

    fun scaleDisplayValues(): List<String> = scaleEntries.map { it.second }
    fun scaleFromDisplay(display: String): Double = scaleEntries.find { it.second == display }?.first ?: DEFAULT_SCALE
    fun displayFromScale(scale: Double): String = scaleEntries.find { it.first == scale }?.second ?: "100%"

    fun fontSizeDisplayValues(): List<String> = fontSizeEntries.map { it.second }
    fun fontSizeFromDisplay(display: String): String = fontSizeEntries.find { it.second == display }?.first ?: DEFAULT_FONT_SIZE
    fun displayFromFontSize(value: String): String = fontSizeEntries.find { it.first == value }?.second ?: "Обычный (14px)"

    fun densityDisplayValues(): List<String> = densityEntries.map { it.second }
    fun densityFromDisplay(display: String): String = densityEntries.find { it.second == display }?.first ?: DEFAULT_DENSITY
    fun displayFromDensity(value: String): String = densityEntries.find { it.first == value }?.second ?: "Обычный"

    fun applyToScene(scene: Scene) {
        val root = scene.root ?: return
        val scale = loadScale()
        val fontSize = loadFontSize()
        val density = loadDensity()
        val accentHex = loadAccentColor()

        root.scaleX = scale
        root.scaleY = scale

        root.styleClass.removeAll("font-size-compact", "font-size-normal", "font-size-large")
        root.styleClass.add("font-size-$fontSize")

        root.styleClass.removeAll("density-compact", "density-normal")
        root.styleClass.add("density-$density")

        var style = (root.style ?: "").replace(Regex("\\s*-fx-accent-color:\\s*[^;]+;?"), "").trim()
        if (accentHex.isNotBlank()) style += " -fx-accent-color: $accentHex;"
        root.style = style
    }

    fun restoreDefaults() {
        saveScale(DEFAULT_SCALE)
        saveFontSize(DEFAULT_FONT_SIZE)
        saveDensity(DEFAULT_DENSITY)
        saveAccentColor(DEFAULT_ACCENT)
    }

    fun colorToHex(color: Color): String =
        "#%02x%02x%02x".format(
            (color.red * 255).toInt().coerceIn(0, 255),
            (color.green * 255).toInt().coerceIn(0, 255),
            (color.blue * 255).toInt().coerceIn(0, 255)
        )
}
