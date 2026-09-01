package com.david.catalanpdfreader.data

import com.david.catalanpdfreader.data.model.LibrarySort
import com.david.catalanpdfreader.data.model.PdfDocument
import com.david.catalanpdfreader.data.model.PdfOrigin
import com.david.catalanpdfreader.data.source.isPdf
import com.david.catalanpdfreader.data.source.mergePdfDocuments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun doc(
    name: String,
    size: Long = 1_000L,
    modified: Long = 0L,
    origin: PdfOrigin = PdfOrigin.GRANTED_FOLDER,
    uri: String = "content://test/$name",
) = PdfDocument(
    uriString = uri,
    displayName = name,
    sizeBytes = size,
    lastModified = modified,
    origin = origin,
    parentLabel = null,
)

class PdfMergeTest {

    @Test
    fun `same file from both sources appears once`() {
        val merged = mergePdfDocuments(
            fromFolders = listOf(doc("verbs.pdf", size = 2_048)),
            fromDevice = listOf(
                doc("verbs.pdf", size = 2_048, origin = PdfOrigin.DEVICE_SCAN, uri = "content://media/42"),
            ),
        )
        assertEquals(1, merged.size)
    }

    @Test
    fun `duplicate keeps the granted-folder handle, which outlives the permission`() {
        val merged = mergePdfDocuments(
            fromFolders = listOf(doc("verbs.pdf", size = 2_048, uri = "content://saf/tree/verbs")),
            fromDevice = listOf(
                doc("verbs.pdf", size = 2_048, origin = PdfOrigin.DEVICE_SCAN, uri = "content://media/42"),
            ),
        )
        assertEquals(PdfOrigin.GRANTED_FOLDER, merged.single().origin)
        assertEquals("content://saf/tree/verbs", merged.single().uriString)
    }

    @Test
    fun `same name but different size is two different documents`() {
        val merged = mergePdfDocuments(
            fromFolders = listOf(doc("notes.pdf", size = 100)),
            fromDevice = listOf(doc("notes.pdf", size = 999, origin = PdfOrigin.DEVICE_SCAN)),
        )
        assertEquals(2, merged.size)
    }

    @Test
    fun `dedupe ignores filename case`() {
        val merged = mergePdfDocuments(
            fromFolders = listOf(doc("Gramatica.PDF", size = 10)),
            fromDevice = listOf(doc("gramatica.pdf", size = 10, origin = PdfOrigin.DEVICE_SCAN)),
        )
        assertEquals(1, merged.size)
    }

    @Test
    fun `device-only documents survive the merge`() {
        val merged = mergePdfDocuments(
            fromFolders = emptyList(),
            fromDevice = listOf(doc("a.pdf", origin = PdfOrigin.DEVICE_SCAN)),
        )
        assertEquals(1, merged.size)
        assertEquals(PdfOrigin.DEVICE_SCAN, merged.single().origin)
    }
}

class PdfDetectionTest {

    @Test
    fun `accepts a correct mime type`() {
        assertTrue(isPdf("guia.pdf", "application/pdf"))
    }

    @Test
    fun `accepts extension when the provider reports octet-stream`() {
        assertTrue(isPdf("guia.pdf", "application/octet-stream"))
    }

    @Test
    fun `accepts an uppercase extension`() {
        assertTrue(isPdf("GUIA.PDF", null))
    }

    @Test
    fun `rejects a non-pdf`() {
        assertFalse(isPdf("guia.epub", "application/epub+zip"))
        assertFalse(isPdf("guia.pdf.bak", null))
    }
}

class LibrarySortTest {

    private val old = doc("a.pdf", size = 500, modified = 1_000)
    private val new = doc("z.pdf", size = 100, modified = 9_000)

    @Test
    fun `recent puts the newest first`() {
        val sorted = listOf(old, new).sortedWith(LibrarySort.RECENT.comparator())
        assertEquals(new, sorted.first())
    }

    @Test
    fun `name sorts alphabetically, case-insensitively`() {
        val sorted = listOf(doc("Zebra.pdf"), doc("apple.pdf"))
            .sortedWith(LibrarySort.NAME.comparator())
        assertEquals("apple.pdf", sorted.first().displayName)
    }

    @Test
    fun `size puts the largest first`() {
        val sorted = listOf(new, old).sortedWith(LibrarySort.SIZE.comparator())
        assertEquals(old, sorted.first())
    }

    @Test
    fun `title strips the extension`() {
        assertEquals("El Petit Princep", doc("El Petit Princep.pdf").title)
    }

    @Test
    fun `title strips an uppercase extension`() {
        assertEquals("GRAMATICA", doc("GRAMATICA.PDF").title)
    }

    @Test
    fun `a name that is only an extension is left alone`() {
        assertEquals(".pdf", doc(".pdf").title)
    }

    @Test
    fun `an inner pdf is not stripped`() {
        assertEquals("notes.pdf.backup", doc("notes.pdf.backup").title)
    }
}
