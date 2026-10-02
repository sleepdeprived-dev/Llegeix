package com.david.llegeix.data

import com.david.llegeix.data.model.PdfDocument
import com.david.llegeix.data.model.PdfOrigin
import com.david.llegeix.data.source.documentsIn
import com.david.llegeix.data.source.foldersIn
import com.david.llegeix.data.source.nearestLivePath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The library browser walks one level at a time, so what matters is that each
 * level shows exactly what is at it: the folders directly inside, counted by
 * everything below them, and the documents that are in this folder rather than
 * in one under it.
 */
class LibraryTreeTest {

    private fun pdf(name: String, folder: String?) = PdfDocument(
        uriString = "content://tree/$folder/$name",
        displayName = name,
        sizeBytes = 1_000,
        lastModified = 0,
        origin = PdfOrigin.GRANTED_FOLDER,
        parentLabel = folder,
    )

    private val library = listOf(
        pdf("a.pdf", "Documents"),
        pdf("b.pdf", "Documents/Català"),
        pdf("c.pdf", "Documents/Català/Història"),
        pdf("d.pdf", "Documents/Català/Poesia"),
        pdf("e.pdf", "Documents/Feina"),
        pdf("f.pdf", "Baixades"),
        pdf("picked.pdf", null),
    )

    @Test
    fun `the top level lists one folder per source, not one per document`() {
        val folders = foldersIn(null, library)

        assertEquals(listOf("Baixades", "Documents"), folders.map { it.name })
    }

    @Test
    fun `a folder counts everything below it, not just what sits in it`() {
        val documents = foldersIn(null, library).single { it.name == "Documents" }

        assertEquals(5, documents.documentCount)
        assertEquals(2, documents.folderCount)
    }

    @Test
    fun `a folder with nothing under it is not offered`() {
        val leaf = foldersIn("Documents/Feina", library)

        assertEquals(emptyList<String>(), leaf.map { it.name })
    }

    @Test
    fun `going in shows only that folder's own subfolders`() {
        val inside = foldersIn("Documents/Català", library)

        assertEquals(listOf("Història", "Poesia"), inside.map { it.name })
        assertEquals(listOf(1, 1), inside.map { it.documentCount })
    }

    @Test
    fun `a folder shows its own documents and not those of its subfolders`() {
        assertEquals(listOf("b.pdf"), documentsIn("Documents/Català", library).map { it.displayName })
        assertEquals(listOf("a.pdf"), documentsIn("Documents", library).map { it.displayName })
    }

    @Test
    fun `picked files, which have no folder on the device, sit at the top`() {
        assertEquals(listOf("picked.pdf"), documentsIn(null, library).map { it.displayName })
    }

    @Test
    fun `a path that still holds something is kept`() {
        assertEquals("Documents/Català", nearestLivePath("Documents/Català", library))
    }

    @Test
    fun `a path whose documents have gone falls back to the nearest one above`() {
        val emptied = library.filterNot { it.parentLabel?.startsWith("Documents/Català") == true }

        assertEquals("Documents", nearestLivePath("Documents/Català/Poesia", emptied))
    }

    @Test
    fun `a path with nothing left anywhere above it falls back to the top`() {
        assertNull(nearestLivePath("Escola/Apunts", library))
    }
}
