package com.david.llegeix.data.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.david.llegeix.R

/** Light, dark, true black, or whatever the system is currently doing. */
enum class ThemeMode(val key: String, @param:StringRes val labelRes: Int) {
    SYSTEM("system", R.string.settings_theme_system),
    LIGHT("light", R.string.settings_theme_light),
    DARK("dark", R.string.settings_theme_dark),

    /**
     * True black, for OLED screens.
     *
     * Separate from [DARK] rather than a switch beside it: on an OLED panel a
     * black pixel is an off pixel, so this is a different look and a different
     * battery cost, not a shade of the same one. Reading at night is most of
     * what this app is for.
     */
    AMOLED("amoled", R.string.settings_theme_amoled),
    ;

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/**
 * Which colour the app is built around.
 *
 * Six plain colours, plus the two that are not colours: [SYSTEM] is Material
 * You, derived from the wallpaper by the platform, and [CUSTOM] is whatever the
 * reader mixed. The six are named for what they look like rather than for a
 * palette — someone choosing an accent is choosing "the green one", not a tone
 * value — and each is a fixed scheme in ui/theme/Accents.kt, so the choice
 * survives a wallpaper change.
 */
enum class AccentColor(val key: String, @param:StringRes val labelRes: Int) {
    SYSTEM("system", R.string.settings_accent_system),
    RED("red", R.string.settings_accent_red),
    ORANGE("orange", R.string.settings_accent_orange),
    YELLOW("yellow", R.string.settings_accent_yellow),
    GREEN("green", R.string.settings_accent_green),
    BLUE("blue", R.string.settings_accent_blue),
    PURPLE("purple", R.string.settings_accent_purple),

    /** A colour mixed by hand; the value lives in [AppSettings.customAccent]. */
    CUSTOM("custom", R.string.settings_accent_custom),
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

/**
 * The language a tapped word is translated into.
 *
 * English is the default because that is what the app was built around, but the
 * reader is not necessarily an English speaker — someone learning Catalan from
 * Romanian is served badly by being routed through a third language. Both are
 * listed in their own language, so the choice is readable whichever is active.
 */
enum class TranslationTarget(
    val code: String,
    @param:StringRes val labelRes: Int,
    /** Named inside the sentence "Catalan → …", so it inflects with the UI. */
    @param:StringRes val directionRes: Int,
    /** Shown on the reader's quick toggle, where a flag reads faster than a word. */
    @param:DrawableRes val flagRes: Int,
    /**
     * What that flag is called out loud.
     *
     * Its own string rather than one sentence with the language slotted in:
     * Catalan contracts the article differently for each one — *a l'anglès*
     * but *al romanès* — and a template would get one of them wrong.
     */
    @param:StringRes val switchRes: Int,
) {
    ENGLISH(
        "en",
        R.string.settings_translation_english,
        R.string.lookup_target_english,
        R.drawable.ic_flag_uk,
        R.string.lookup_switch_english,
    ),
    ROMANIAN(
        "ro",
        R.string.settings_translation_romanian,
        R.string.lookup_target_romanian,
        R.drawable.ic_flag_ro,
        R.string.lookup_switch_romanian,
    ),
    ;

    companion object {
        val Default: TranslationTarget = ENGLISH

        fun fromCode(code: String?): TranslationTarget =
            entries.firstOrNull { it.code == code } ?: Default
    }
}

/**
 * Which Catalan flag sits beside the app's name.
 *
 * Not a setting in Configuració, and deliberately not: it is found by pressing
 * the flag itself, which is the only place the question could possibly be
 * asked. The Senyera is the default because it is the flag of the language this
 * app is for; the Estelada is there because a great many of the people reading
 * Catalan on a phone would rather see that one, and the app is theirs too.
 */
enum class AppFlag(val key: String, @param:StringRes val labelRes: Int) {
    SENYERA("senyera", R.string.flag_senyera),
    ESTELADA("estelada", R.string.flag_estelada),
    ;

    companion object {
        fun fromKey(key: String?): AppFlag = entries.firstOrNull { it.key == key } ?: SENYERA
    }
}

/** Whether documents are listed as rows or as a grid of covers. */
enum class LibraryLayout(val key: String) {
    LIST("list"),
    GRID("grid"),
    ;

    fun toggled(): LibraryLayout = if (this == LIST) GRID else LIST

    companion object {
        fun fromKey(key: String?): LibraryLayout =
            entries.firstOrNull { it.key == key } ?: LIST
    }
}

/**
 * Whether the Continue reading shelf is open or folded.
 *
 * Two states, toggled by a tap on the shelf's own heading. There used to be a
 * third, which took the shelf off the library altogether; it went, because a
 * shelf that is not on the screen cannot carry the way back to itself, and that
 * way back had to live in Configuració instead. Folded is as far as it goes:
 * the heading stays, says how many books are on the shelf, and opens again
 * where the reader is looking.
 *
 * Nothing is forgotten in either state. The reading history is untouched, it is
 * still listed under Saved as *Recently viewed*, and every book keeps the page
 * it was left on — this is a decision about one shelf on one screen, not about
 * what the app remembers.
 */
enum class ContinueShelf(val key: String) {
    SHOWN("shown"),
    COLLAPSED("collapsed"),
    ;

    /** The state a tap on the shelf's heading leads to. */
    fun toggled(): ContinueShelf = if (this == COLLAPSED) SHOWN else COLLAPSED

    companion object {
        /**
         * The stored state, with the retired "hidden" read as folded: somebody
         * who had taken the shelf away gets its heading back, closed, rather
         * than the whole shelf open again unasked — and the heading is the way
         * to open it.
         */
        fun fromKey(key: String?): ContinueShelf = when (key) {
            HIDDEN_KEY -> COLLAPSED
            else -> entries.firstOrNull { it.key == key } ?: SHOWN
        }

        private const val HIDDEN_KEY = "hidden"
    }
}

/**
 * What the page itself is tinted to, which is not the same question as the
 * app's theme.
 *
 * A dark interface around a page that still looks like paper is a perfectly
 * reasonable thing to want, and so is the opposite. [SEPIA] is here because
 * [INVERT] is a blunt instrument: a full negative is right for reading a white
 * page in the dark and wrong for reading one in a lit room, where the thing
 * that actually helps is taking the glare off the white rather than replacing
 * it with black.
 */
enum class PageTint(val key: String, @param:StringRes val labelRes: Int) {
    NONE("none", R.string.reader_tint_none),
    SEPIA("sepia", R.string.reader_tint_sepia),
    INVERT("invert", R.string.reader_tint_invert),

    /**
     * Inverted and then warmed: cream type on a near-black page.
     *
     * The one that was missing. Warm was a daylight setting and dark was a
     * night one, which left the commonest way of reading in bed — the lights
     * off and the screen at its least blue — with nothing to pick. It is the
     * same pass over the pixels that [INVERT] uses, so photographs are still
     * found and left alone, with [SEPIA]'s own warming laid over the result.
     */
    WARM_DARK("warm-dark", R.string.reader_tint_warm_dark),
    ;

    /**
     * Whether the page is turned light-on-dark before anything else is done to
     * it.
     *
     * Not a filter: see [com.david.llegeix.pdf.PageInvert]. This is what tells
     * the reader that a page has to be re-rendered rather than merely re-drawn,
     * and what tells the screen around it to go dark too.
     */
    val invertsPage: Boolean get() = this == INVERT || this == WARM_DARK

    companion object {
        fun fromKey(key: String?): PageTint =
            entries.firstOrNull { it.key == key } ?: NONE
    }
}

/**
 * Whether pages are turned or scrolled.
 *
 * Both are right for different documents. A book is a sequence of pages and
 * turning them is what it is for; a scanned handout, a paper with figures
 * running across a page break, or anything read in short bursts is easier as
 * one continuous strip.
 */
enum class ReadingMode(
    val key: String,
    @param:StringRes val labelRes: Int,
    /** A mark beside the word, since "Turn" and "Scroll" are near-synonyms read quickly. */
    @param:DrawableRes val iconRes: Int,
) {
    PAGED("paged", R.string.reader_mode_paged, R.drawable.ic_page_turn),
    SCROLL("scroll", R.string.reader_mode_scroll, R.drawable.ic_page_scroll),
    ;

    companion object {
        fun fromKey(key: String?): ReadingMode =
            entries.firstOrNull { it.key == key } ?: PAGED
    }
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentColor = AccentColor.SYSTEM,
    val language: AppLanguage = AppLanguage.Default,
    val libraryLayout: LibraryLayout = LibraryLayout.LIST,
    /** How much of the Continue reading shelf the library draws. See [ContinueShelf]. */
    val continueShelf: ContinueShelf = ContinueShelf.SHOWN,
    val translationTarget: TranslationTarget = TranslationTarget.Default,
    /**
     * Inverts the rendered page so a white PDF reads as light-on-dark.
     *
     * Separate from [themeMode]: the app's own chrome and the document are
     * different surfaces, and plenty of people want a dark interface around a
     * page that still looks like paper.
     */
    /**
     * Set when the reader has switched the whole-device sweep off in Fonts.
     *
     * Held apart from the permission because the two are different questions:
     * Android says whether the app *may* sweep the phone, this says whether it
     * *should*. Erasing everything sets it, since an app cannot revoke its own
     * all-files access and a library that refills itself two seconds after
     * being emptied is not an erased library.
     */
    val deviceScanOptOut: Boolean = false,
    val pageTint: PageTint = PageTint.NONE,
    /**
     * Trims the blank margins off a page before it is drawn.
     *
     * The single biggest legibility win available on a phone. An A4 page fitted
     * to a phone's width is small, and a fifth of that width is usually paper
     * with nothing on it; dropping the margins makes the type meaningfully
     * bigger without magnifying anything, which is the difference between
     * reading a page and panning around one.
     */
    val cropMargins: Boolean = false,
    val readingMode: ReadingMode = ReadingMode.PAGED,
    /**
     * Underlines words already in the saved list, on the page itself.
     *
     * The saved words were a list somewhere else in the app; this puts them
     * back where they were met. Reading a page and seeing four words you have
     * already worked on marked in it is the most direct answer there is to "am
     * I getting anywhere with this".
     */
    val markSavedWords: Boolean = true,
    /** ARGB for [AccentColor.CUSTOM]. Ignored by every other accent. */
    val customAccent: Int = DEFAULT_CUSTOM_ACCENT,
    /** Which flag is drawn beside the app's name. See [AppFlag]. */
    val flag: AppFlag = AppFlag.SENYERA,
)

/** The colour the custom picker opens on before anything is chosen. */
const val DEFAULT_CUSTOM_ACCENT: Int = 0xFF7A5AF8.toInt()
