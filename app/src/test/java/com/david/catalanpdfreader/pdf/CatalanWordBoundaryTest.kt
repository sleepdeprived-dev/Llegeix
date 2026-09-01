package com.david.catalanpdfreader.pdf

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The word-boundary rule decides what a tap-and-hold actually sends to the
 * translator, so the Catalan-specific cases are worth pinning down.
 */
class CatalanWordBoundaryTest {

    private val isWordChar = PdfiumPageRenderer.Companion::isWordChar

    @Test
    fun `plain letters are word characters`() {
        assertTrue(isWordChar('m'))
        assertTrue(isWordChar('A'))
    }

    @Test
    fun `accented Catalan vowels are word characters`() {
        // à è é í ï ò ó ú ü all appear in ordinary Catalan text.
        listOf('à', 'è', 'é', 'í', 'ï', 'ò', 'ó', 'ú', 'ü').forEach {
            assertTrue("expected $it to be a word character", isWordChar(it))
        }
    }

    @Test
    fun `c-cedilla is a word character`() {
        assertTrue(isWordChar('ç'))
        assertTrue(isWordChar('Ç'))
    }

    @Test
    fun `the middle dot holds the l-geminate together`() {
        // "intel·ligent" is one word; splitting on the interpunct would send
        // "intel" to the dictionary.
        assertTrue(isWordChar('·'))
    }

    @Test
    fun `whitespace and punctuation end a word`() {
        listOf(' ', '.', ',', ';', ':', '!', '?', '\n', '\t').forEach {
            assertFalse("expected $it to end a word", isWordChar(it))
        }
    }

    @Test
    fun `the apostrophe ends a word so elisions look up the noun`() {
        // "l'aigua" should look up "aigua", not the elided article.
        assertFalse(isWordChar('\''))
        assertFalse(isWordChar('’'))
    }

    @Test
    fun `the hyphen ends a word so clitics do not stick`() {
        // "anar-hi" looks up "anar".
        assertFalse(isWordChar('-'))
    }

    @Test
    fun `digits are not word characters`() {
        assertFalse(isWordChar('7'))
    }
}
