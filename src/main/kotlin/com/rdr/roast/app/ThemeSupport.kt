package com.rdr.roast.app

import atlantafx.base.theme.Theme
import atlantafx.base.theme.CupertinoDark
import atlantafx.base.theme.CupertinoLight
import atlantafx.base.theme.NordDark
import atlantafx.base.theme.NordLight
import atlantafx.base.theme.PrimerDark
import atlantafx.base.theme.PrimerLight
import javafx.application.Application
import java.util.prefs.Preferences

object ThemeSupport {
    private const val PREF_THEME = "theme"
    private const val DEFAULT_THEME_ID = "CupertinoLight"

    val themeEntries: List<Pair<String, String>> = listOf(
        "CupertinoLight" to "Cupertino Light",
        "CupertinoDark" to "Cupertino Dark",
        "PrimerLight" to "Primer Light",
        "PrimerDark" to "Primer Dark",
        "NordLight" to "Nord Light",
        "NordDark" to "Nord Dark"
    )

    private fun prefs(): Preferences =
        Preferences.userNodeForPackage(com.rdr.roast.RoastApp::class.java)

    fun loadThemeId(): String =
        prefs().get(PREF_THEME, DEFAULT_THEME_ID)

    fun saveTheme(themeId: String) {
        prefs().put(PREF_THEME, themeId)
    }

    fun themeIdToDisplayName(themeId: String): String? =
        themeEntries.find { it.first == themeId }?.second

    fun displayNameToThemeId(displayName: String): String? =
        themeEntries.find { it.second == displayName }?.first

    fun applyTheme(themeId: String) {
        val theme = themeById(themeId) ?: themeById(DEFAULT_THEME_ID)!!
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
}
