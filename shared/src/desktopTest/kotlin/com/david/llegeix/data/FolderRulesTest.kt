package com.david.llegeix.data

import com.david.llegeix.data.model.PdfDocument
import com.david.llegeix.data.model.PdfOrigin
import com.david.llegeix.data.source.FolderRules
import com.david.llegeix.data.source.applyFolderRules
import com.david.llegeix.data.source.folderTreeUnder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two shapes of request the sources screen has to satisfy:
 * hide one folder out of a big granted one, and keep only one folder out of a
 * big granted one. Both come from the same inheritance rule, so both are
 * pinned down here.
 */
class FolderRulesTest {

    private fun pdf(name: String, folder: String) = PdfDocument(
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
        pdf("d.pdf", "Documents/Feina"),
        pdf("e.pdf", "Documents/Feina/2025"),
        pdf("f.pdf", "Baixades"),
    )

    @Test
    fun `with no rules everything is shown`() {
        assertEquals(library, applyFolderRules(library, FolderRules.Empty))
    }

    @Test
    fun `hiding one folder keeps the rest of its parent`() {
        val rules = FolderRules.of(listOf("Documents/Feina" to false))
        val kept = applyFolderRules(library, rules).map { it.displayName }
        assertEquals(listOf("a.pdf", "b.pdf", "c.pdf", "f.pdf"), kept)
    }

    @Test
    fun `hiding a folder hides everything under it`() {
        val rules = FolderRules.of(listOf("Documents/Català" to false))
        assertFalse(rules.allows("Documents/Català/Història"))
        assertTrue(rules.allows("Documents/Feina"))
    }

    @Test
    fun `keeping one folder out of a hidden parent works`() {
        val rules = FolderRules.of(
            listOf("Documents" to false, "Documents/Català" to true),
        )
        val kept = applyFolderRules(library, rules).map { it.displayName }
        // The one included subfolder and its children, plus the untouched source.
        assertEquals(listOf("b.pdf", "c.pdf", "f.pdf"), kept)
    }

    @Test
    fun `the nearest decision wins over one further up`() {
        val rules = FolderRules.of(
            listOf(
                "Documents" to false,
                "Documents/Català" to true,
                "Documents/Català/Història" to false,
            ),
        )
        assertFalse(rules.allows("Documents"))
        assertTrue(rules.allows("Documents/Català"))
        assertFalse(rules.allows("Documents/Català/Història"))
    }

    @Test
    fun `a document with no folder is never hidden`() {
        val rules = FolderRules.of(listOf("Documents" to false))
        assertTrue(rules.allows(null))
        assertTrue(rules.allows(""))
    }

    @Test
    fun `a folder name is not confused with one that starts the same way`() {
        val rules = FolderRules.of(listOf("Documents/Feina" to false))
        assertTrue(rules.allows("Documents/Feines"))
        assertFalse(rules.allows("Documents/Feina/2025"))
    }

    @Test
    fun `the tree counts a folder's own files apart from its children's`() {
        val tree = folderTreeUnder("Documents", library, FolderRules.Empty)
        val byPath = tree.associateBy { it.path }
        assertEquals(5, byPath.getValue("Documents").total)
        assertEquals(1, byPath.getValue("Documents").direct)
        assertEquals(2, byPath.getValue("Documents/Català").total)
        assertEquals(1, byPath.getValue("Documents/Català").direct)
        assertEquals(0, byPath.getValue("Documents").depth)
        assertEquals(1, byPath.getValue("Documents/Català").depth)
        assertEquals(2, byPath.getValue("Documents/Català/Història").depth)
    }

    @Test
    fun `the tree stops at the source it was asked about`() {
        val paths = folderTreeUnder("Documents", library, FolderRules.Empty).map { it.path }
        assertFalse(paths.contains("Baixades"))
    }

    @Test
    fun `the tree reports where a decision was actually made`() {
        val rules = FolderRules.of(listOf("Documents/Feina" to false))
        val tree = folderTreeUnder("Documents", library, rules).associateBy { it.path }
        assertTrue(tree.getValue("Documents/Feina").decidedHere)
        assertFalse(tree.getValue("Documents/Feina").visible)
        assertFalse(tree.getValue("Documents/Feina/2025").decidedHere)
        assertFalse(tree.getValue("Documents/Feina/2025").visible)
        assertTrue(tree.getValue("Documents/Català").visible)
    }
}
