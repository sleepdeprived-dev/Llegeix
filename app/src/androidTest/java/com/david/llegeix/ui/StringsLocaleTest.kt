package com.david.llegeix.ui

import androidx.compose.ui.text.intl.Locale
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.david.llegeix.MainActivity
import com.david.llegeix.resources.Res
import com.david.llegeix.resources.library_read_progress
import com.david.llegeix.resources.sources_source_count
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The strings come out in Catalan on a phone set to something else.
 *
 * Compose resources pick plural forms by the locale Compose reports, which on
 * Android is the process default, and MainActivity sets that to Catalan before
 * anything is drawn. Catalan has a form English lacks: "1000000 de fonts".
 */
@RunWith(AndroidJUnit4::class)
class StringsLocaleTest {

    @Test
    fun catalanWhateverThePhoneIsSetTo() {
        ActivityScenario.launch(MainActivity::class.java).use {
            assertEquals("ca", Locale.current.language)
            runBlocking {
                assertEquals("1 font", getPluralString(Res.plurals.sources_source_count, 1, 1))
                assertEquals("1000000 de fonts", getPluralString(Res.plurals.sources_source_count, 1_000_000, 1_000_000))
                assertEquals("16% llegit", getString(Res.string.library_read_progress, 16))
            }
        }
    }
}
