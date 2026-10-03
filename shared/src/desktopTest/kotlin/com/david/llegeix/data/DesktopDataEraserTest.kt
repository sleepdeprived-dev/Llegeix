package com.david.llegeix.data

import com.david.llegeix.data.db.LlegeixDatabase
import com.david.llegeix.data.db.entity.WordBookmarkEntity
import com.david.llegeix.data.db.openDesktop
import com.david.llegeix.data.flashcards.FlashcardBackupFiles
import com.david.llegeix.data.flashcards.FlashcardImages
import com.david.llegeix.data.flashcards.FlashcardPrefs
import com.david.llegeix.data.flashcards.FlashcardRepository
import com.david.llegeix.data.settings.SearchHistoryRepository
import com.david.llegeix.data.settings.SearchScope
import com.david.llegeix.data.settings.SettingsRepository
import com.david.llegeix.data.settings.ThemeMode
import com.david.llegeix.data.settings.javaPrefsStore
import com.david.llegeix.data.source.DesktopPdfLibrary
import com.david.llegeix.data.source.LibraryDataRepository
import com.david.llegeix.pdf.PdfThumbnails
import com.david.llegeix.platform.ContentRef
import com.david.llegeix.platform.DesktopAppFiles
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.prefs.Preferences

/** The Mac's "erase everything", over a scratch data folder and scratch preferences. */
class DesktopDataEraserTest {

    @get:Rule val folder = TemporaryFolder()
    private val prefs = Preferences.userRoot().node("com/david/llegeix/test-erase-${System.nanoTime()}")

    @After
    fun tearDown() {
        prefs.removeNode()
        System.clearProperty("llegeix.data")
    }

    @Test
    fun leavesTheAppAsIfNeverOpenedAndThePdfsAlone() = runBlocking {
        System.setProperty("llegeix.data", folder.root.path)
        val database = LlegeixDatabase.openDesktop(File(folder.root, "llegeix.db"))
        try {
            val files = DesktopAppFiles(folder.root)
            val settings = SettingsRepository(javaPrefsStore(prefs.node("settings")))
            val library = DesktopPdfLibrary(javaPrefsStore(prefs.node("library")), settings, folder.root) { emptyList() }
            val libraryData = LibraryDataRepository(database)
            val history = SearchHistoryRepository(javaPrefsStore(prefs.node("searches")))
            val flashcards = FlashcardRepository(
                database, FlashcardImages(files), FlashcardBackupFiles(files), FlashcardPrefs(javaPrefsStore(prefs.node("cards"))),
            )

            // A reader's worth of things: a folder of PDFs, a saved word, a
            // search, a setting, and the translation models fetched.
            val books = File(folder.root, "Llibres").apply { mkdirs() }
            val book = File(books, "conte.pdf").apply { writeText("%PDF-1.4") }
            library.addFolder(ContentRef(books))
            libraryData.toggleWordBookmark(
                WordBookmarkEntity(
                    word = "poma", translation = "măr", ipa = null, context = null,
                    documentUri = null, displayName = null, pageIndex = 0, lineNumber = 0,
                ),
            )
            history.record(SearchScope.WORDS, "poma")
            settings.setThemeMode(ThemeMode.DARK)
            val models = File(folder.root, "translation/caen").apply { mkdirs() }

            DesktopDataEraser(library, libraryData, flashcards, settings, history, PdfThumbnails { error("no covers") })
                .eraseEverything()

            assertTrue(library.grantedFolders().isEmpty())
            assertTrue(libraryData.observeWordBookmarks().first().isEmpty())
            assertTrue(history.history(SearchScope.WORDS).value.isEmpty())
            assertEquals(ThemeMode.SYSTEM, settings.current.themeMode)
            assertFalse(models.exists())
            // And the sweep stays off, so the library does not refill itself.
            assertFalse(library.isDeviceScanEnabled())
            // The PDFs were never the app's.
            assertTrue(book.exists())
        } finally {
            database.close()
        }
    }
}
