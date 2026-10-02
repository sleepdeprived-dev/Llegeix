package com.david.llegeix.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.rememberNavController
import com.david.llegeix.platform.ContentRef
import com.david.llegeix.ui.theme.LlegeixTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Biblioteca on the Mac, driven as a reader would: a folder of PDFs chosen
 * (the open panel itself cannot be driven, so the folder is handed to the
 * library as the panel would hand it), browsed into, its document's cover
 * drawn by PDFium, and the document opened. Screenshots of each step go to
 * desktop/build/screenshots.
 */
@OptIn(ExperimentalTestApi::class)
class LibraryOnTheMacTest {

    @Test
    fun chooseAFolderAndBrowseIt() = MacTestSession.run {
        val books = Files.createTempDirectory("llegeix-books").toFile()
        val catalan = File(books, "Català").apply { mkdirs() }
        javaClass.getResourceAsStream("/prova.pdf")!!.use { input ->
            File(catalan, "prova.pdf").outputStream().use { input.copyTo(it) }
        }
        DesktopApp.pdfLibrary.addFolder(ContentRef(books))
        try {
            runComposeUiTest {
                // A window on screen is resumed; the test's stand-in has to be told.
                val lifecycle = object : LifecycleOwner {
                    val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
                    override val lifecycle: Lifecycle get() = registry
                }
                setContent {
                    CompositionLocalProvider(LocalLifecycleOwner provides lifecycle) {
                        LlegeixTheme { LibraryHost(rememberNavController()) }
                    }
                }
                waitUntil(timeoutMillis = 15_000) { onAllNodesWithText(books.name).fetchSemanticsNodes().isNotEmpty() }
                waitForIdle()
                screenshot("library-1-sources")

                onAllNodesWithText(books.name)[0].performClick()
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Català").fetchSemanticsNodes().isNotEmpty() }
                onAllNodesWithText("Català")[0].performClick()
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("prova").fetchSemanticsNodes().isNotEmpty() }
                // Time for the cover, PDFium's drawing of the first page, to arrive.
                Thread.sleep(1_500)
                waitForIdle()
                screenshot("library-2-folder")

                onAllNodesWithText("prova")[0].performClick()
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodesWithText("El lector encara no és al Mac", substring = true).fetchSemanticsNodes().isNotEmpty()
                }
                waitForIdle()
                screenshot("library-3-opened")

                onNodeWithContentDescription("Enrere").performClick()
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("prova").fetchSemanticsNodes().isNotEmpty() }

                // The ways in, in the Mac's words.
                onNodeWithContentDescription("Afegeix documents").performClick()
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodesWithText("Busca per tot el Mac").fetchSemanticsNodes().isNotEmpty()
                }
                waitForIdle()
                screenshot("library-4-add")
            }
            // The cover the rows draw, and the document whole, as the reader will open it.
            val uri = File(catalan, "prova.pdf").toURI().toString()
            assertNotNull(runBlocking { DesktopApp.pdfThumbnails.load(uri, 120) })
            runBlocking { DesktopApp.openPdf(uri) }.use { renderer ->
                assertNotNull(runBlocking { renderer.renderPage(0, 300) })
            }
        } finally {
            books.deleteRecursively()
        }
    }
}
