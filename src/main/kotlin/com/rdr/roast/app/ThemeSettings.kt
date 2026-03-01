package com.rdr.roast.app

data class ThemeColors(
    val primary: String = "#6B4E31",
    val secondary: String = "#8C6A4B",
    val background: String = "#F8F4EE",
    val backgroundAlt: String = "#FAF6F0",
    val surface: String = "#FFFFFF",
    val cardBackground: String = "#FFFFFF",
    val accent: String = "#FF6B35",
    val graphBt: String = "#FF6B35",
    val graphEt: String = "#F59E0B",
    val graphRor: String = "#06B6D4",
    val textPrimary: String = "#1F1F1F",
    val textSecondary: String = "#4A4A4A",
    val textHint: String = "#7C7C7C",
    val positive: String = "#10B981",
    val negative: String = "#EF4444",
    val warning: String = "#F59E0B",
    val sidebarBackground: String = "#111111",
    val sidebarIcon: String = "#FFFFFF",
    val buttonCharge: String = "#6B4E31",
    val buttonDrop: String = "#FF6B35",
    val buttonCool: String = "#0EA5E9",
    val buttonStop: String = "#1F1F1F",
    val border: String = "#ECE6DD",
    val titleBar: String = "#FFFFFF",
    val panelBackground: String = "#FFFFFF"
)

data class ThemeTypography(
    val fontFamily: String = "System",
    val customFontPath: String = "",
    val textScalePercent: Int = 100,
    val h1: Double = 32.0,
    val h2: Double = 26.0,
    val h3: Double = 22.0,
    val h4: Double = 18.0,
    val body: Double = 14.0,
    val caption: Double = 12.0,
    val button: Double = 14.0,
    val label: Double = 13.0
)

data class ThemeShape(
    val cardRadius: Double = 28.0,
    val buttonRadius: Double = 20.0,
    val inputRadius: Double = 16.0
)

data class ThemeLayout(
    val spacingScale: Double = 1.0,
    val shadowIntensity: Double = 0.45
)

data class ThemeSettings(
    val darkMode: Boolean = false,
    val colors: ThemeColors = ThemeColors(),
    val typography: ThemeTypography = ThemeTypography(),
    val shape: ThemeShape = ThemeShape(),
    val layout: ThemeLayout = ThemeLayout()
) {
    companion object {
        fun rdrCoffeeDefault(): ThemeSettings = ThemeSettings()
    }
}

