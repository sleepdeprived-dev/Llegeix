package com.david.llegeix.data

import com.david.llegeix.data.model.PdfOrigin
import com.david.llegeix.data.settings.javaPrefsStore
import com.david.llegeix.data.source.DesktopPdfLibrary
import com.david.llegeix.platform.ContentRef
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.prefs.Preferences

/**
 * The Mac's three ways in, over a scratch home folder: a folder walked, a file
 * picked, and a stand-in for Spotlight.
 */
class DesktopPdfLibraryTest {

    private val home = Files.createTempDirectory("llegeix-home").toFile()
    private val node = Preferences.userRoot().node("com/david/llegeix/test-library-${System.nanoTime()}")
    private var spotlit = emptyList<File>()
    private val library = DesktopPdfLibrary(javaPrefsStore(node), null, home) { spotlit }

    @After
    fun tearDown() {
        node.removeNode()
        home.deleteRecursively()
    }

    private fun pdf(path: String) = File(home, path).apply { parentFile.mkdirs(); writeText("%PDF-1.4") }

    @Test
    fun walksAChosenFolder() = runBlocking {
        pdf("Llibres/conte.pdf")
        pdf("Llibres/Català/gramàtica.PDF")
        pdf("Llibres/.amagat/secret.pdf")
        pdf("Llibres/nota.txt")
        library.addFolder(ContentRef(File(home, "Llibres")))

        val found = library.loadLibrary().documents
        assertEquals(setOf("conte.pdf", "gramàtica.PDF"), found.map { it.displayName }.toSet())
        assertTrue(found.all { it.origin == PdfOrigin.GRANTED_FOLDER })
        assertEquals("Llibres/Català", found.single { it.displayName == "gramàtica.PDF" }.parentLabel)
        assertEquals("Llibres", found.single { it.displayName == "conte.pdf" }.parentLabel)
        assertEquals(listOf("Llibres"), library.grantedFolders().map { it.label })

        library.removeFolder(library.grantedFolders().single().treeUri)
        assertTrue(library.grantedFolders().isEmpty())
        assertTrue(library.loadLibrary().documents.isEmpty())
    }

    @Test
    fun keepsAPickedFile() = runBlocking {
        val file = pdf("Baixades/article.pdf")
        library.addPickedFile(ContentRef(file))
        library.addPickedFile(ContentRef(File(home, "foto.jpg")))

        val found = library.loadLibrary().documents.single()
        assertEquals(PdfOrigin.PICKED_FILE, found.origin)
        assertEquals("Baixades", found.parentLabel)
        assertEquals(setOf(file.toURI().toString()), library.pickedUris())
        // A picked file that has gone is still counted as kept.
        file.delete()
        assertTrue(library.loadLibrary().documents.isEmpty())
        assertEquals(1, library.pickedUris().size)
    }

    @Test
    fun sweepsOnlyOnceAskedAndOnlyTheReadersFiles() = runBlocking {
        spotlit = listOf(
            pdf("Documents/Català/llibre.pdf"),
            pdf("solt.pdf"),
            pdf("Library/Caches/intern.pdf"),
            pdf(".config/ocult.pdf"),
        )
        assertFalse(library.isDeviceScanEnabled())
        assertTrue(library.loadLibrary().documents.isEmpty())

        library.allowDeviceScan()
        val found = library.loadLibrary().documents.associateBy { it.displayName }
        assertEquals(setOf("llibre.pdf", "solt.pdf"), found.keys)
        assertEquals("Documents/Català", found.getValue("llibre.pdf").parentLabel)
        assertEquals(null, found.getValue("solt.pdf").parentLabel)
        assertTrue(found.values.all { it.origin == PdfOrigin.DEVICE_SCAN })
    }

    @Test
    fun aFileFoundTwiceKeepsItsFolder() = runBlocking {
        val book = pdf("Llibres/conte.pdf")
        library.addFolder(ContentRef(File(home, "Llibres")))
        spotlit = listOf(book)
        library.allowDeviceScan()

        val found = library.loadLibrary().documents.single()
        assertEquals(PdfOrigin.GRANTED_FOLDER, found.origin)
        assertEquals("Llibres", found.parentLabel)
    }
}
