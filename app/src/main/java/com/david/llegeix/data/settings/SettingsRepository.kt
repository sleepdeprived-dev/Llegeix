package com.david.llegeix.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The three preferences behind the Settings screen.
 *
 * SharedPreferences rather than the Room database or DataStore: the language
 * has to be readable synchronously from [android.app.Activity.attachBaseContext],
 * which runs before any coroutine scope exists, and a suspending read there
 * would mean the first frame renders in the wrong language.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = preferences(context)

    private val _settings = MutableStateFlow(read(prefs))
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val current: AppSettings get() = _settings.value

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME, mode.key) }
        _settings.value = current.copy(themeMode = mode)
    }

    fun setAccent(accent: AccentColor) {
        prefs.edit { putString(KEY_ACCENT, accent.key) }
        _settings.value = current.copy(accent = accent)
    }

    fun setLanguage(language: AppLanguage) {
        prefs.edit { putString(KEY_LANGUAGE, language.tag) }
        _settings.value = current.copy(language = language)
    }

    fun setLibraryLayout(layout: LibraryLayout) {
        prefs.edit { putString(KEY_LAYOUT, layout.key) }
        _settings.value = current.copy(libraryLayout = layout)
    }

    fun setCustomAccent(argb: Int) {
        prefs.edit { putInt(KEY_CUSTOM_ACCENT, argb) }
        _settings.value = current.copy(customAccent = argb, accent = AccentColor.CUSTOM)
    }


    fun setTranslationTarget(target: TranslationTarget) {
        prefs.edit { putString(KEY_TRANSLATION, target.code) }
        _settings.value = current.copy(translationTarget = target)
    }

    fun setInvertPages(invert: Boolean) {
        prefs.edit { putBoolean(KEY_INVERT_PAGES, invert) }
        _settings.value = current.copy(invertPages = invert)
    }

    companion object {
        private const val PREFS_NAME = "llegeix.settings"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_ACCENT = "accent"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_LAYOUT = "library_layout"
        private const val KEY_TRANSLATION = "translation_target"
        private const val KEY_INVERT_PAGES = "invert_pages"
        private const val KEY_CUSTOM_ACCENT = "custom_accent"

        private fun preferences(context: Context): SharedPreferences =
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        private fun read(prefs: SharedPreferences): AppSettings = AppSettings(
            themeMode = ThemeMode.fromKey(prefs.getString(KEY_THEME, null)),
            accent = AccentColor.fromKey(prefs.getString(KEY_ACCENT, null)),
            language = AppLanguage.fromTag(prefs.getString(KEY_LANGUAGE, null)),
            libraryLayout = LibraryLayout.fromKey(prefs.getString(KEY_LAYOUT, null)),
            translationTarget = TranslationTarget.fromCode(prefs.getString(KEY_TRANSLATION, null)),
            invertPages = prefs.getBoolean(KEY_INVERT_PAGES, false),
            customAccent = prefs.getInt(KEY_CUSTOM_ACCENT, DEFAULT_CUSTOM_ACCENT),
        )

        /**
         * The stored language, read without building a repository.
         *
         * `attachBaseContext` needs this before the application's own singletons
         * are reachable, and it must not create a second [MutableStateFlow] that
         * would then drift from the one the UI observes.
         */
        fun storedLanguage(context: Context): AppLanguage =
            AppLanguage.fromTag(preferences(context).getString(KEY_LANGUAGE, null))
    }
}
