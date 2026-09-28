package com.david.llegeix.resources

import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * The strings read as they did when Android's resources read them.
 *
 * When they moved to Compose resources every one of them was checked against
 * what Android resolved from the v4.4.5 APK, and they matched once the file was
 * written the way Compose reads it. These keep it written that way, and pin the
 * cases the two readings differed on.
 */
class StringsTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun catalan() {
            Locale.setDefault(Locale.forLanguageTag("ca"))
        }
    }

    private val file = File("src/commonMain/composeResources/values/strings.xml").readText()
        // The header describes the rules, and so quotes what they forbid.
        .substringAfter("<resources>")

    @Test
    fun noAndroidOnlyEscapes() {
        assertTrue("\\' shows its backslash in Compose; write '", "\\'" !in file)
        assertTrue("\\\" shows its backslash in Compose; write \"", "\\\"" !in file)
        assertTrue("%% shows both signs in Compose; write %", "%%" !in file)
    }

    @Test
    fun apostrophesAndPercentSigns() = runBlocking {
        assertEquals("No s'ha trobat res", getString(Res.string.reader_find_empty_title))
        assertEquals("16% llegit", getString(Res.string.library_read_progress, 16))
        assertEquals("Pàgina 34 de 210 · 16%", getString(Res.string.library_continue_position, 34, 210, 16))
    }

    @Test
    fun catalanPluralsIncludingMillions() = runBlocking {
        assertEquals("1 font", getPluralString(Res.plurals.sources_source_count, 1, 1))
        assertEquals("2 fonts", getPluralString(Res.plurals.sources_source_count, 2, 2))
        assertEquals("0 fonts", getPluralString(Res.plurals.sources_source_count, 0, 0))
        assertEquals("1000000 de fonts", getPluralString(Res.plurals.sources_source_count, 1_000_000, 1_000_000))
    }
}
