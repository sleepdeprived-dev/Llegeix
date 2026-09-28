package com.david.llegeix.data.settings

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.prefs.Preferences

/** Settings on the Mac read back what was written, across a fresh repository. */
class DesktopSettingsTest {

    // A node of its own, so the test never touches the real settings.
    private val node = Preferences.userRoot().node("com/david/llegeix/test-${System.nanoTime()}")

    @After
    fun remove() = node.removeNode()

    @Test
    fun choicesPersist() {
        val store = javaPrefsStore(node)
        SettingsRepository(store).apply {
            setThemeMode(ThemeMode.DARK)
            setAccent(AccentColor.GREEN)
            setMarkSavedWords(false)
        }
        val again = SettingsRepository(javaPrefsStore(node)).current
        assertEquals(ThemeMode.DARK, again.themeMode)
        assertEquals(AccentColor.GREEN, again.accent)
        assertEquals(false, again.markSavedWords)
    }

    @Test
    fun defaultsWhenNothingIsSaved() {
        assertEquals(AppSettings(), SettingsRepository(javaPrefsStore(node)).current)
    }
}
