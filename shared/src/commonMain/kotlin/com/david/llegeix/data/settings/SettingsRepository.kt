package com.david.llegeix.data.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The preferences behind the Settings screen, kept in a [SettingsStore]:
 * SharedPreferences on the phone, java.util.prefs on the Mac.
 *
 * A plain key-value store rather than the Room database or DataStore: the
 * language has to be readable synchronously from Android's attachBaseContext,
 * which runs before any coroutine scope exists, and a suspending read there
 * would mean the first frame renders in the wrong language.
 */
class SettingsRepository(private val prefs: SettingsStore) {

    private val _settings = MutableStateFlow(read(prefs))
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val current: AppSettings get() = _settings.value

    fun setThemeMode(mode: ThemeMode) {
        prefs.putString(KEY_THEME, mode.key)
        _settings.value = current.copy(themeMode = mode)
    }

    fun setAccent(accent: AccentColor) {
        prefs.putString(KEY_ACCENT, accent.key)
        _settings.value = current.copy(accent = accent)
    }

    fun setLibraryLayout(layout: LibraryLayout) {
        prefs.putString(KEY_LAYOUT, layout.key)
        _settings.value = current.copy(libraryLayout = layout)
    }

    /** Open or folded shut. See [ContinueShelf]. */
    fun setContinueShelf(shelf: ContinueShelf) {
        prefs.putString(KEY_CONTINUE_SHELF, shelf.key)
        _settings.value = current.copy(continueShelf = shelf)
    }

    fun setCustomAccent(argb: Int) {
        prefs.putInt(KEY_CUSTOM_ACCENT, argb)
        _settings.value = current.copy(customAccent = argb, accent = AccentColor.CUSTOM)
    }


    fun setFlag(flag: AppFlag) {
        prefs.putString(KEY_FLAG, flag.key)
        _settings.value = current.copy(flag = flag)
    }

    fun setTranslationTarget(target: TranslationTarget) {
        prefs.putString(KEY_TRANSLATION, target.code)
        _settings.value = current.copy(translationTarget = target)
    }

    fun setPageTint(tint: PageTint) {
        prefs.putString(KEY_PAGE_TINT, tint.key)
        _settings.value = current.copy(pageTint = tint)
    }

    fun setCropMargins(crop: Boolean) {
        prefs.putBoolean(KEY_CROP_MARGINS, crop)
        _settings.value = current.copy(cropMargins = crop)
    }

    fun setMarkSavedWords(mark: Boolean) {
        prefs.putBoolean(KEY_MARK_SAVED, mark)
        _settings.value = current.copy(markSavedWords = mark)
    }

    fun setReadingMode(mode: ReadingMode) {
        prefs.putString(KEY_READING_MODE, mode.key)
        _settings.value = current.copy(readingMode = mode)
    }

    /** Whether the whole-device sweep is switched off in Fonts. */
    fun setDeviceScanOptOut(optOut: Boolean) {
        prefs.putBoolean(KEY_DEVICE_SCAN_OPT_OUT, optOut)
        _settings.value = read(prefs)
    }

    /**
     * Put every preference back to its default.
     *
     * Part of "erase everything": a wipe that left the theme and the chosen
     * language behind would not be the clean slate the dialog promises.
     */
    fun resetToDefaults() {
        prefs.clear()
        _settings.value = read(prefs)
    }

    companion object {
        /** The file the phone has always kept them in, and the Mac's node name. */
        const val PREFS_NAME = "llegeix.settings"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_ACCENT = "accent"
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


        private fun read(prefs: SettingsStore): AppSettings = AppSettings(
            themeMode = ThemeMode.fromKey(prefs.getString(KEY_THEME, null)),
            accent = AccentColor.fromKey(prefs.getString(KEY_ACCENT, null)),
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
    }
}
