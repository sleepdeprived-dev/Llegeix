package com.david.llegeix.pdf

import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.math.abs

/**
 * The renderer on the Mac, over the PDFium from tools/macos/fetch-pdfium.sh,
 * against [TestPdf]: every question the reader asks of a page, with answers
 * worked out from the page's own geometry.
 *
 * Positions are in pixels of a page rendered 595 px wide, which for A4 is one
 * pixel per point: x as in the PDF, y counted down from the top (842 − y).
 * In 24 pt Helvetica "La " is 33.4 pt wide, so on the first line "finestra"
 * runs from x 105 to 184 above a baseline at y 82.
 *
 * Skipped when PDFium has not been fetched.
 */
class MacPdfiumTest {

    @Test
    fun opensAndMeasures() = runBlocking {
        assertEquals(2, renderer.pageCount)
        assertEquals(595f / 842f, renderer.pageAspectRatio(0), 0.001f)
        assertEquals(842f / 595f, renderer.pageAspectRatio(1), 0.001f)
        assertTrue(renderer.hasTextLayer(0))
    }

    @Test
    fun readsTheTableOfContents() = runBlocking {
        assertEquals(
            listOf(PdfOutlineEntry("Primer capítol", 0, 0), PdfOutlineEntry("Segon", 1, 0)),
            renderer.outline(),
        )
    }

    @Test
    fun rendersInkOnWhite() = runBlocking {
        val page = renderer.renderPage(0, 595)
        assertEquals(595, page.width)
        assertEquals(842, page.height)
        val pixels = IntArray(page.width * page.height).also { page.readPixels(it) }
        assertEquals(0xFFFFFFFF.toInt(), pixels[0])
        // Somewhere inside "finestra" there is ink.
        val ink = (66..82).any { y -> (106..184).any { x -> pixels[y * 595 + x] and 0xFF < 128 } }
        assertTrue(ink)
    }

    @Test
    fun findsTheWordUnderAPoint() = runBlocking {
        val word = assertNotNullAnd(renderer.wordAt(0, 145f, 75f, 595, 842))
        assertEquals("finestra", word.text)
        // Its box is where the arithmetic says it is.
        assertEquals(105.4f, word.boundsPx.left, 1.5f)
        assertEquals(184.1f, word.boundsPx.right, 1.5f)
        assertTrue(word.boundsPx.top < 75f && word.boundsPx.bottom > 75f)

        // The geminate holds together: one word, not "intel" and "ligència".
        assertEquals("intel·ligència", renderer.wordAt(0, 300f, 107f, 595, 842)?.text)
        // A margin has no word in it.
        assertNull(renderer.wordAt(0, 20f, 420f, 595, 842))
    }

    @Test
    fun selectsWholeWordsWithTheirLine() = runBlocking {
        // From inside "parlava" to inside "amb", on the second line.
        val selection = assertNotNullAnd(renderer.selectionBetween(0, 120f, 107f, 200f, 107f, 595, 842))
        assertEquals("parlava amb", selection.text)
        assertEquals(TestPdf.LINE_2, selection.lineText.trim())
        assertEquals(2, selection.lineNumber)
        assertTrue(selection.boundsPx.isNotEmpty())
        assertTrue(TestPdf.LINE_1 in selection.passage && TestPdf.LINE_3 in selection.passage)
    }

    @Test
    fun searchesCaseInsensitively() = runBlocking {
        val found = ArrayList<PdfMatch>()
        renderer.findMatches("FINESTRA") { found += it }
        assertEquals(2, found.size)
        assertTrue(found.all { it.pageIndex == 0 && it.charCount == "finestra".length })
        assertTrue("finestra" in found.first().snippet.substring(found.first().snippetStart, found.first().snippetEnd))
        val boxes = renderer.matchBoundsPx(found.first(), 595, 842)
        assertEquals(1, boxes.size)
        assertEquals(105.4f, boxes.single().left, 1.5f)
    }

    @Test
    fun marksSavedWords() = runBlocking {
        val marks = renderer.savedWordBounds(0, setOf("finestra", "parlava"), 595, 842)
        // "finestra" twice, "parlava" once.
        assertEquals(3, marks.size)
    }

    @Test
    fun cropsToTheInk() = runBlocking {
        val whole = renderer.pageAspectRatio(1)
        val cropped = renderer.pageAspectRatio(1, crop = true)
        // One line of type across a landscape page: far wider than tall.
        assertTrue(cropped > whole * 2)
        val page = renderer.renderPage(1, 400, crop = true)
        assertEquals(400, page.width)
        assertEquals(400 / cropped, page.height.toFloat(), 2f)
        // And a press still lands on the word under it, cropped or not.
        assertEquals("pàgina", renderer.wordAt(1, 380f, page.height / 2f, page.width, page.height, crop = true)?.text)
    }

    @Test
    fun refusesWhatIsNotAPdf() {
        val notPdf = File(folder, "nota.pdf").apply { writeText("not a PDF") }
        val failed = runCatching { runBlocking { PdfiumPageRenderer.open(notPdf) } }.exceptionOrNull()
        assertTrue(failed is IOException)
    }

    private fun <T> assertNotNullAnd(value: T?): T {
        assertNotNull(value)
        return value!!
    }

    companion object {
        private lateinit var folder: File
        private lateinit var renderer: PdfiumPageRenderer

        @BeforeClass
        @JvmStatic
        fun open() {
            assumeTrue(System.getProperty("llegeix.pdfium")?.let { File(it).isFile } == true)
            folder = Files.createTempDirectory("llegeix-pdf").toFile()
            renderer = runBlocking { PdfiumPageRenderer.open(TestPdf.write(File(folder, "prova.pdf"))) }
        }

        @AfterClass
        @JvmStatic
        fun close() {
            if (::renderer.isInitialized) renderer.close()
            if (::folder.isInitialized) folder.deleteRecursively()
        }
    }
}

private fun assertEquals(expected: Float, actual: Float, delta: Float) =
    assertTrue("expected $expected ± $delta, was $actual", abs(expected - actual) <= delta)
