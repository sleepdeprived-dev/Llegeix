package com.david.llegeix.pdf

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Scanned pages on the Mac: Vision, through llegeix-ocr, reading a picture of
 * [TestPdf]'s first page — a picture, so nothing but its pixels — and the
 * reader's own word lookup over what it read. Skipped when PDFium or the
 * helper has not been built.
 */
class MacOcrTest {

    @Test
    fun readsAPictureOfAPage() = runBlocking {
        assumeTrue(System.getProperty("llegeix.pdfium")?.let { File(it).isFile } == true)
        assumeTrue(System.getProperty("llegeix.ocr")?.let { File(it).canExecute() } == true)
        val folder = Files.createTempDirectory("llegeix-ocr").toFile()
        try {
            val picture = PdfiumPageRenderer.open(TestPdf.write(File(folder, "prova.pdf"))).use { it.renderPage(0, 1190) }
            val ocr = PageOcr()
            val page = ocr.read(0, picture)

            assertTrue(page.lines.map { it.text }.contains(TestPdf.LINE_1))
            // Vision reads the middle dot as a hyphen; the Mac puts it back.
            assertTrue(page.words.any { it.text == "intel·ligència" })

            // "finestra" on the first line, at twice the PDF's points.
            val word = assertNotNullAnd(page.wordAt(290f, 150f, tolerance = 4f))
            assertEquals("finestra", word.text)
            val selection = assertNotNullAnd(page.selectionBetween(240f, 214f, 400f, 214f, tolerance = 4f))
            assertEquals("parlava amb", selection.text)
            assertEquals(2, selection.lineNumber)
            // And the second read of the page is the first one, kept.
            assertTrue(ocr.cached(0, picture.width) === page)
            ocr.close()
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun putsTheMiddleDotBack() {
        assertEquals("intel·ligència", withMiddleDots("intel-ligència"))
        assertEquals("col·lecció", withMiddleDots("col.lecció"))
        assertEquals("ben-vinguda", withMiddleDots("ben-vinguda"))
    }

    private fun <T> assertNotNullAnd(value: T?): T {
        assertNotNull(value)
        return value!!
    }
}
