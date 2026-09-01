package com.david.llegeix.data.settings

import androidx.annotation.StringRes
import com.david.llegeix.R

/** Light, dark, or whatever the system is currently doing. */
enum class ThemeMode(val key: String, @param:StringRes val labelRes: Int) {
    SYSTEM("system", R.string.settings_theme_system),
    LIGHT("light", R.string.settings_theme_light),
    DARK("dark", R.string.settings_theme_dark),
    ;

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/**
 * Which colour the app is built around.
 *
 * [SYSTEM] means Material You: the scheme is derived from the wallpaper by the
 * platform. Every other entry is a fixed scheme defined in ui/theme/Accents.kt,
 * so the choice survives a wallpaper change.
 */
enum class AccentColor(val key: String, @param:StringRes val labelRes: Int) {
    SYSTEM("system", R.string.settings_accent_system),
    SENYERA("senyera", R.string.settings_accent_senyera),
    BLUE("blue", R.string.settings_accent_blue),
    GREEN("green", R.string.settings_accent_green),
    VIOLET("violet", R.string.settings_accent_violet),
    AMBER("amber", R.string.settings_accent_amber),
    TEAL("teal", R.string.settings_accent_teal),
    ;

    companion object {
        fun fromKey(key: String?): AccentColor =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/**
 * The language the app itself is shown in.
 *
 * Deliberately independent of the device language: this is a tool for reading
 * Catalan, so Catalan is the default even on an English phone. The label is not
 * a translatable string — each language is listed in its own language so the
 * picker stays readable whichever one is currently active.
 */
enum class AppLanguage(val tag: String, @param:StringRes val labelRes: Int) {
    CATALAN("ca", R.string.settings_language_catalan),
    ENGLISH("en", R.string.settings_language_english),
    ;

    companion object {
        val Default: AppLanguage = CATALAN

        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag == tag } ?: Default
    }
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentColor = AccentColor.SYSTEM,
    val language: AppLanguage = AppLanguage.Default,
)
