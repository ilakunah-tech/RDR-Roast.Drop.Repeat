package com.rdr.roast.app

/** Preset identifier: light or dark base theme. */
enum class ThemePreset { LIGHT, DARK }

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

/** Window shell: main window background, title bar, borders. */
data class ThemeWindowShell(
    val background: String = "#FFFFFF",
    val titleBarBackground: String = "#FFFFFF",
    val border: String = "#E6ECF0",
    val titleFontSize: Double = 12.0,
    val titleFontWeight: String = "bold"
)

/** Left sidebar: background, icon and text. */
data class ThemeSidebar(
    val background: String = "#138b85",
    val iconColor: String = "rgba(255,255,255,0.88)",
    val iconActiveColor: String = "#0c5b57",
    val iconSize: Double = 13.0,
    val buttonHoverBackground: String = "rgba(255,255,255,0.18)"
)

/** Cards and right panel / drawer. */
data class ThemeCardsPanels(
    val cardBackground: String = "#FFFFFF",
    val panelBackground: String = "#f5f5f5",
    val headerFontSize: Double = 13.0,
    val hintFontSize: Double = 11.0,
    val border: String = "rgba(17, 24, 39, 0.06)"
)

/** Forms: labels, inputs, combos, tabs, buttons. */
data class ThemeForms(
    val labelColor: String = "#667085",
    val labelFontSize: Double = 12.0,
    val inputBackground: String = "#FFFFFF",
    val inputBorder: String = "rgba(17, 24, 39, 0.10)",
    val inputFontSize: Double = 12.0,
    val primaryButtonBackground: String = "#1AA7A1",
    val primaryButtonText: String = "#FFFFFF",
    val secondaryButtonBackground: String = "#FFFFFF",
    val secondaryButtonText: String = "#17212B",
    val tabSelectedColor: String = "#0e6f6a"
)

/** Live readouts: BT, ET, RoR, timer. */
data class ThemeReadouts(
    val btColor: String = "#0C8A84",
    val etColor: String = "#E07A5F",
    val rorColor: String = "#0C8A84",
    val timerFontSize: Double = 30.0,
    val valueFontSize: Double = 24.0,
    val labelFontSize: Double = 8.0,
    val unitFontSize: Double = 9.0
)

/** Control sliders: gas, air, drum colors and labels. */
data class ThemeSliders(
    val gasColor: String = "#dc2626",
    val airColor: String = "#2563eb",
    val drumColor: String = "#16a34a",
    val genericColor: String = "#6b7280",
    val tickLabelFontSize: Double = 10.0,
    val tickLabelColor: String = "#374151",
    val valueFieldFontSize: Double = 12.0
)

/** Chart area: background, grid, axes; series colors stay in ChartColors. */
data class ThemeCharts(
    val backgroundColor: String = "#ffffff",
    val gridColor: String = "#d3d3d3",
    val axisLabelColor: String = "#17212B",
    val axisLabelFontSize: Double = 11.0,
    val legendFontSize: Double = 11.0
)

/** Custom event buttons default style. */
data class ThemeCustomButtons(
    val defaultBackground: String = "#e8e8e8",
    val defaultTextColor: String = "#333333",
    val fontSize: Double = 12.0
)

/**
 * Single source of truth for UI theme. Replaces legacy ThemeSupport + AppearanceSupport preferences.
 * On first load after migration, values from Java Preferences are merged in and saved to settings.json.
 */
data class ThemeSettings(
    /** Light or dark base preset; drives Atlantafx theme and default palette. */
    val preset: ThemePreset = ThemePreset.LIGHT,
    /** Atlantafx theme id (CupertinoLight, CupertinoDark, etc.). Applied as base user-agent stylesheet. */
    val atlantafxThemeId: String = "CupertinoLight",
    /** UI scale factor (e.g. 1.0 = 100%). */
    val scale: Double = 1.0,
    /** Font size tier: compact, normal, large. */
    val fontSize: String = "normal",
    /** Density tier: compact, normal. */
    val density: String = "normal",
    /** Accent color override (hex). Empty = use preset default. */
    val accentHex: String = "",
    /** Corner radius in px (0–20). */
    val radiusPx: Int = 10,
    /** Panel/sidebar/drawer background (hex). */
    val panelBackground: String = "#f5f5f5",
    val darkMode: Boolean = false,
    val colors: ThemeColors = ThemeColors(),
    val typography: ThemeTypography = ThemeTypography(),
    val shape: ThemeShape = ThemeShape(),
    val layout: ThemeLayout = ThemeLayout(),
    val windowShell: ThemeWindowShell = ThemeWindowShell(),
    val sidebar: ThemeSidebar = ThemeSidebar(),
    val cardsPanels: ThemeCardsPanels = ThemeCardsPanels(),
    val forms: ThemeForms = ThemeForms(),
    val readouts: ThemeReadouts = ThemeReadouts(),
    val sliders: ThemeSliders = ThemeSliders(),
    val charts: ThemeCharts = ThemeCharts(),
    val customButtons: ThemeCustomButtons = ThemeCustomButtons()
) {
    /** Effective accent for CSS: user override or preset default. */
    fun effectiveAccent(): String = accentHex.takeIf { it.isNotBlank() } ?: defaultAccentForPreset()

    private fun defaultAccentForPreset(): String = when (preset) {
        ThemePreset.LIGHT -> "#1AA7A1"
        ThemePreset.DARK -> "#20C997"
    }

    companion object {
        const val DEFAULT_ATLANTAFX_LIGHT = "CupertinoLight"
        const val DEFAULT_ATLANTAFX_DARK = "CupertinoDark"

        fun rdrCoffeeDefault(): ThemeSettings = ThemeSettings()

        fun lightDefault(): ThemeSettings = ThemeSettings(
            preset = ThemePreset.LIGHT,
            atlantafxThemeId = DEFAULT_ATLANTAFX_LIGHT,
            darkMode = false,
            colors = ThemeColors(
                accent = "#1AA7A1",
                panelBackground = "#f5f5f5",
                background = "#FFFFFF",
                backgroundAlt = "#F8FBFC",
                surface = "#F2F6F8",
                textPrimary = "#17212B",
                textSecondary = "#667085",
                textHint = "#98A2B3",
                border = "#E6ECF0",
                titleBar = "#FFFFFF",
                sidebarBackground = "#1A1A2E"
            ),
            forms = ThemeForms(primaryButtonBackground = "#1AA7A1"),
            sidebar = ThemeSidebar(background = "#138b85", iconActiveColor = "#0c5b57"),
            cardsPanels = ThemeCardsPanels(panelBackground = "#f5f5f5")
        )

        fun darkDefault(): ThemeSettings = ThemeSettings(
            preset = ThemePreset.DARK,
            atlantafxThemeId = DEFAULT_ATLANTAFX_DARK,
            darkMode = true,
            panelBackground = "#2d2d2d",
            colors = ThemeColors(
                accent = "#20C997",
                panelBackground = "#2d2d2d",
                background = "#1a1a1a",
                backgroundAlt = "#242424",
                surface = "#2d2d2d",
                cardBackground = "#2d2d2d",
                textPrimary = "#e6e6e6",
                textSecondary = "#c7cbd1",
                textHint = "#a6adb8",
                border = "#404040",
                titleBar = "#242424",
                sidebarBackground = "#1a1a2e"
            ),
            windowShell = ThemeWindowShell(
                background = "#1a1a1a",
                titleBarBackground = "#242424",
                border = "#404040"
            ),
            forms = ThemeForms(
                labelColor = "#c7cbd1",
                inputBackground = "#2d2d2d",
                inputBorder = "#404040",
                primaryButtonBackground = "#20C997",
                secondaryButtonBackground = "#2d2d2d",
                secondaryButtonText = "#e6e6e6",
                tabSelectedColor = "#20C997"
            ),
            sidebar = ThemeSidebar(
                background = "#1a1a2e",
                iconColor = "rgba(255,255,255,0.88)",
                iconActiveColor = "#20C997"
            ),
            cardsPanels = ThemeCardsPanels(
                cardBackground = "#2d2d2d",
                panelBackground = "#2d2d2d",
                border = "#404040"
            ),
            readouts = ThemeReadouts(btColor = "#20C997", etColor = "#E07A5F", rorColor = "#20C997"),
            charts = ThemeCharts(
                backgroundColor = "#2a3038",
                gridColor = "#667085",
                axisLabelColor = "#f5f7fa"
            ),
            customButtons = ThemeCustomButtons(
                defaultBackground = "#3d3d3d",
                defaultTextColor = "#e6e6e6"
            )
        )
    }
}
