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

    /** Shown, folded shut, or off the library altogether. See [ContinueShelf]. */
    fun setContinueShelf(shelf: ContinueShelf) {
        prefs.edit { putString(KEY_CONTINUE_SHELF, shelf.key) }
        _settings.value = current.copy(continueShelf = shelf)
    }

    fun setCustomAccent(argb: Int) {
        prefs.edit { putInt(KEY_CUSTOM_ACCENT, argb) }
        _settings.value = current.copy(customAccent = argb, accent = AccentColor.CUSTOM)
    }


    fun setFlag(flag: AppFlag) {
        prefs.edit { putString(KEY_FLAG, flag.key) }
        _settings.value = current.copy(flag = flag)
    }

    fun setTranslationTarget(target: TranslationTarget) {
        prefs.edit { putString(KEY_TRANSLATION, target.code) }
        _settings.value = current.copy(translationTarget = target)
    }

    fun setPageTint(tint: PageTint) {
        prefs.edit { putString(KEY_PAGE_TINT, tint.key) }
        _settings.value = current.copy(pageTint = tint)
    }

    fun setCropMargins(crop: Boolean) {
        prefs.edit { putBoolean(KEY_CROP_MARGINS, crop) }
        _settings.value = current.copy(cropMargins = crop)
    }

    fun setMarkSavedWords(mark: Boolean) {
        prefs.edit { putBoolean(KEY_MARK_SAVED, mark) }
        _settings.value = current.copy(markSavedWords = mark)
    }

    fun setReadingMode(mode: ReadingMode) {
        prefs.edit { putString(KEY_READING_MODE, mode.key) }
        _settings.value = current.copy(readingMode = mode)
    }

    /** Whether the whole-device sweep is switched off in Fonts. */
    fun setDeviceScanOptOut(optOut: Boolean) {
        prefs.edit { putBoolean(KEY_DEVICE_SCAN_OPT_OUT, optOut) }
        _settings.value = read(prefs)
    }

    /**
     * Put every preference back to its default.
     *
     * Part of "erase everything": a wipe that left the theme and the chosen
     * language behind would not be the clean slate the dialog promises.
     */
    fun resetToDefaults() {
        prefs.edit { clear() }
        _settings.value = read(prefs)
    }

    companion object {
        private const val PREFS_NAME = "llegeix.settings"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_ACCENT = "accent"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_LAYOUT = "library_layout"
        private const val KEY_CONTINUE_SHELF = "continue_shelf"
        private const val KEY_TRANSLATION = "translation_target"
        private const val KEY_INVERT_PAGES = "invert_pages"
        private const val KEY_PAGE_TINT = "page_tint"
        private const val KEY_CROP_MARGINS = "crop_margins"
        private const val KEY_READING_MODE = "reading_mode"
        private const val KEY_MARK_SAVED = "mark_saved_words"
        private const val KEY_DEVICE_SCAN_OPT_OUT = "device_scan_opt_out"
        private const val KEY_CUSTOM_ACCENT = "custom_accent"
        private const val KEY_FLAG = "flag"

        private fun preferences(context: Context): SharedPreferences =
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        private fun read(prefs: SharedPreferences): AppSettings = AppSettings(
            themeMode = ThemeMode.fromKey(prefs.getString(KEY_THEME, null)),
            accent = AccentColor.fromKey(prefs.getString(KEY_ACCENT, null)),
            language = AppLanguage.fromTag(prefs.getString(KEY_LANGUAGE, null)),
            libraryLayout = LibraryLayout.fromKey(prefs.getString(KEY_LAYOUT, null)),
            continueShelf = ContinueShelf.fromKey(prefs.getString(KEY_CONTINUE_SHELF, null)),
            translationTarget = TranslationTarget.fromCode(prefs.getString(KEY_TRANSLATION, null)),
            deviceScanOptOut = prefs.getBoolean(KEY_DEVICE_SCAN_OPT_OUT, false),
            // The old boolean is still read, so a reader who had inverted
            // pages before this became a three-way choice finds them inverted
            // afterwards rather than back to white.
            pageTint = PageTint.fromKey(
                prefs.getString(KEY_PAGE_TINT, null)
                    ?: PageTint.INVERT.key.takeIf { prefs.getBoolean(KEY_INVERT_PAGES, false) },
            ),
            cropMargins = prefs.getBoolean(KEY_CROP_MARGINS, false),
            readingMode = ReadingMode.fromKey(prefs.getString(KEY_READING_MODE, null)),
            markSavedWords = prefs.getBoolean(KEY_MARK_SAVED, true),
            customAccent = prefs.getInt(KEY_CUSTOM_ACCENT, DEFAULT_CUSTOM_ACCENT),
            flag = AppFlag.fromKey(prefs.getString(KEY_FLAG, null)),
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
