package com.rdr.roast.app

import atlantafx.base.theme.Theme
import atlantafx.base.theme.CupertinoDark
import atlantafx.base.theme.CupertinoLight
import atlantafx.base.theme.NordDark
import atlantafx.base.theme.NordLight
import atlantafx.base.theme.PrimerDark
import atlantafx.base.theme.PrimerLight
import javafx.application.Application
import javafx.scene.Scene
import java.util.prefs.Preferences

/**
 * Single entry point for applying theme from [ThemeSettings] and migrating from legacy Preferences.
 * Theme is stored in AppSettings.themeSettings; Preferences are only read once for migration.
 */
object ThemeService {
    private const val PREF_THEME = "theme"
    private const val PREF_UI_SCALE = "uiScale"
    private const val PREF_FONT_SIZE = "fontSize"
    private const val PREF_DENSITY = "density"
    private const val PREF_ACCENT_COLOR = "rdr.appearance.accent"
    private const val PREF_RADIUS = "rdr.appearance.radius"
    private const val PREF_PANEL_BG = "rdr.appearance.panelBg"
    private const val MIGRATION_FLAG = "rdr.theme.migrated"

    private fun prefs(): Preferences =
        Preferences.userNodeForPackage(com.rdr.roast.RoastApp::class.java)

    /**
     * If theme was never migrated and Preferences contain user values, merge them into [current]
     * and return the result; otherwise return [current] unchanged.
     * Caller should save the merged theme back to AppSettings when migration occurs.
     */
    fun migrateFromPreferences(current: ThemeSettings): ThemeSettings {
        if (prefs().getBoolean(MIGRATION_FLAG, false)) return current
        val themeId = prefs().get(PREF_THEME, ThemeSettings.DEFAULT_ATLANTAFX_LIGHT)
        val scale = prefs().get(PREF_UI_SCALE, "1.0").toDoubleOrNull() ?: 1.0
        val fontSize = prefs().get(PREF_FONT_SIZE, "normal").takeIf { it in listOf("compact", "normal", "large") } ?: "normal"
        val density = prefs().get(PREF_DENSITY, "normal").takeIf { it in listOf("compact", "normal") } ?: "normal"
        val accentHex = prefs().get(PREF_ACCENT_COLOR, "").trim()
        val radiusPx = prefs().get(PREF_RADIUS, "10").toIntOrNull()?.coerceIn(0, 20) ?: 10
        val panelBg = prefs().get(PREF_PANEL_BG, "#f5f5f5").takeIf { it.isNotBlank() } ?: "#f5f5f5"

        val dark = themeId.contains("Dark", ignoreCase = true)
        val preset = if (dark) ThemePreset.DARK else ThemePreset.LIGHT
        val merged = current.copy(
            preset = preset,
            atlantafxThemeId = themeId,
            scale = scale,
            fontSize = fontSize,
            density = density,
            accentHex = accentHex,
            radiusPx = radiusPx,
            panelBackground = panelBg,
            darkMode = dark
        )
        prefs().putBoolean(MIGRATION_FLAG, true)
        return merged
    }

    /**
     * Apply [theme] globally (Atlantafx) and to [scene] if non-null.
     */
    fun applyTheme(theme: ThemeSettings, scene: Scene?) {
        applyBaseTheme(theme.atlantafxThemeId)
        scene?.let { applyToScene(theme, it) }
    }

    fun applyBaseTheme(atlantafxThemeId: String) {
        val theme = themeById(atlantafxThemeId) ?: themeById(ThemeSettings.DEFAULT_ATLANTAFX_LIGHT)!!
        Application.setUserAgentStylesheet(theme.userAgentStylesheet)
    }

    private fun themeById(id: String): Theme? = when (id) {
        "CupertinoLight" -> CupertinoLight()
        "CupertinoDark" -> CupertinoDark()
        "PrimerLight" -> PrimerLight()
        "PrimerDark" -> PrimerDark()
        "NordLight" -> NordLight()
        "NordDark" -> NordDark()
        else -> null
    }

    fun applyToScene(theme: ThemeSettings, scene: Scene) {
        val root = scene.root ?: return
        root.scaleX = theme.scale
        root.scaleY = theme.scale

        val dark = theme.darkMode || theme.atlantafxThemeId.contains("Dark", ignoreCase = true)
        root.styleClass.removeAll("font-size-compact", "font-size-normal", "font-size-large")
        root.styleClass.add("font-size-${theme.fontSize}")
        root.styleClass.removeAll("density-compact", "density-normal")
        root.styleClass.add("density-${theme.density}")
        root.styleClass.removeAll("rdr-theme-light", "rdr-theme-dark")
        root.styleClass.add(if (dark) "rdr-theme-dark" else "rdr-theme-light")

        val accent = theme.effectiveAccent()
        val radius = theme.radiusPx.coerceIn(0, 20)
        val panelBg = theme.panelBackground.ifBlank { "#f5f5f5" }
        val c = theme.colors
        val ws = theme.windowShell
        val sb = theme.sidebar
        val cp = theme.cardsPanels
        val f = theme.forms
        val ro = theme.readouts
        val sl = theme.sliders
        val chart = theme.charts
        val sidebarBg = if (theme.accentHex.isBlank()) sb.background else accent
        val shadowColor = if (dark) "rgba(0,0,0,0.42)" else "rgba(18,28,45,0.12)"
        val shadowSoft = if (dark) "rgba(0,0,0,0.26)" else "rgba(23,33,43,0.08)"
        val neutralSoft = if (dark) "rgba(255,255,255,0.06)" else "rgba(17,24,39,0.04)"
        val neutralBorder = if (dark) "rgba(255,255,255,0.10)" else "rgba(17,24,39,0.08)"
        val sidebarGlass = if (dark) "rgba(255,255,255,0.08)" else "rgba(255,255,255,0.10)"
        val sidebarGlassStrong = if (dark) "rgba(255,255,255,0.14)" else "rgba(255,255,255,0.18)"
        val dangerSoft = if (dark) "rgba(239,68,68,0.18)" else "#fee2e2"
        val dangerSoftStrong = if (dark) "rgba(239,68,68,0.26)" else "#fecaca"
        val dangerBorder = if (dark) "rgba(239,68,68,0.35)" else "rgba(239,68,68,0.20)"
        val accentSubtle = if (dark) "derive($accent, -78%)" else "derive($accent, 85%)"
        val accentMuted = if (dark) "derive($accent, -35%)" else "derive($accent, 55%)"
        val accentStrong = if (dark) "derive($accent, 18%)" else "derive($accent, -18%)"
        val cardBackground = cp.cardBackground.ifBlank { c.cardBackground }
        val cardBackgroundAlt = if (dark) "derive($cardBackground, 8%)" else "derive($cardBackground, -3%)"
        val inputBackgroundAlt = if (dark) "derive(${f.inputBackground}, 10%)" else "derive(${f.inputBackground}, -3%)"
        val secondaryButtonHover = if (dark) "derive(${f.secondaryButtonBackground}, 10%)" else "derive(${f.secondaryButtonBackground}, -4%)"
        val sidebarSelectedBg = if (dark) "derive(${c.surface}, 12%)" else "#ffffff"

        val style = buildString {
            append(" -color-bg-default: ${ws.background};")
            append(" -color-bg-overlay: $cardBackground;")
            append(" -color-bg-subtle: $panelBg;")
            append(" -color-bg-inset: ${c.surface};")
            append(" -color-fg-default: ${c.textPrimary};")
            append(" -color-fg-muted: ${c.textSecondary};")
            append(" -color-fg-subtle: ${c.textHint};")
            append(" -color-fg-emphasis: #ffffff;")
            append(" -color-border-default: ${c.border};")
            append(" -color-border-muted: derive(${c.border}, ${if (dark) "15%" else "-4%"});")
            append(" -color-border-subtle: derive(${c.border}, ${if (dark) "28%" else "10%"});")
            append(" -color-shadow-default: $shadowColor;")
            append(" -color-accent-emphasis: $accent;")
            append(" -color-accent-fg: $accent;")
            append(" -color-accent-subtle: $accentSubtle;")
            append(" -color-accent-muted: $accentMuted;")
            append(" -color-success-emphasis: ${c.positive};")
            append(" -color-success-fg: ${c.positive};")
            append(" -color-danger-emphasis: ${c.negative};")
            append(" -color-danger-fg: ${c.negative};")
            append(" -rdr-radius: ${radius}px;")
            append(" -rdr-panel-bg: $panelBg;")
            append(" -rdr-accent: $accent;")
            append(" -rdr-accent-strong: $accentStrong;")
            append(" -rdr-accent-soft: $accentSubtle;")
            append(" -rdr-accent-text: #ffffff;")
            append(" -rdr-accent-hover: derive($accent, -15%);")
            append(" -fx-accent-color: $accent;")
            append(" -rdr-bg: ${ws.background};")
            append(" -rdr-bg-secondary: ${c.backgroundAlt};")
            append(" -rdr-surface: ${c.surface};")
            append(" -rdr-card-bg: $cardBackground;")
            append(" -rdr-card-bg-alt: $cardBackgroundAlt;")
            append(" -rdr-input-bg: ${f.inputBackground};")
            append(" -rdr-input-bg-alt: $inputBackgroundAlt;")
            append(" -rdr-secondary-btn-bg: ${f.secondaryButtonBackground};")
            append(" -rdr-secondary-btn-hover: $secondaryButtonHover;")
            append(" -rdr-secondary-btn-border: ${f.inputBorder};")
            append(" -rdr-neutral-soft: $neutralSoft;")
            append(" -rdr-neutral-border: $neutralBorder;")
            append(" -rdr-shadow-color: $shadowColor;")
            append(" -rdr-shadow-soft: $shadowSoft;")
            append(" -rdr-danger-soft: $dangerSoft;")
            append(" -rdr-danger-soft-strong: $dangerSoftStrong;")
            append(" -rdr-danger-border: $dangerBorder;")
            append(" -rdr-title-bar: ${ws.titleBarBackground};")
            append(" -rdr-border: ${c.border};")
            append(" -rdr-text-primary: ${c.textPrimary};")
            append(" -rdr-text-secondary: ${c.textSecondary};")
            append(" -rdr-text-hint: ${c.textHint};")
            append(" -rdr-sidebar-bg: $sidebarBg;")
            append(" -rdr-sidebar-icon: ${sb.iconColor};")
            append(" -rdr-sidebar-active: ${sb.iconActiveColor};")
            append(" -rdr-sidebar-glass: $sidebarGlass;")
            append(" -rdr-sidebar-glass-strong: $sidebarGlassStrong;")
            append(" -rdr-sidebar-selected-bg: $sidebarSelectedBg;")
            append(" -rdr-readout-bt: ${ro.btColor};")
            append(" -rdr-readout-et: ${ro.etColor};")
            append(" -rdr-positive: ${c.positive};")
            append(" -rdr-negative: ${c.negative};")
            append(" -rdr-slider-gas: ${sl.gasColor};")
            append(" -rdr-slider-air: ${sl.airColor};")
            append(" -rdr-slider-drum: ${sl.drumColor};")
            append(" -rdr-chart-bg: ${chart.backgroundColor};")
            append(" -rdr-chart-grid: ${chart.gridColor};")
            append(" -rdr-chart-axis: ${chart.axisLabelColor};")
        }
        val clearRegex = Regex("\\s*(-rdr-[a-zA-Z-]+|-fx-accent-color|-color-[a-zA-Z-]+):[^;]+;?")
        root.style = (root.style?.replace(clearRegex, "")?.trim() ?: "").trim() + style
    }

    /** Display names for theme combo; Light/Dark presets map to first light/dark entry. */
    fun themeEntries(): List<Pair<String, String>> = listOf(
        "CupertinoLight" to "Cupertino Light",
        "CupertinoDark" to "Cupertino Dark",
        "PrimerLight" to "Primer Light",
        "PrimerDark" to "Primer Dark",
        "NordLight" to "Nord Light",
        "NordDark" to "Nord Dark"
    )

    fun themeIdToDisplayName(id: String): String? = themeEntries().find { it.first == id }?.second
    fun displayNameToThemeId(display: String): String? = themeEntries().find { it.second == display }?.first
}
