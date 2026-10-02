package com.david.llegeix.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The raw-bytes table the language assets are read out of.
 *
 * Worth its own tests because nothing about it is obvious from the call sites:
 * it binary-searches UTF-8 bytes rather than strings, so the cases that matter
 * are the ones where a byte comparison could disagree with a reader's idea of
 * alphabetical order — accents, the middle dot, a key that is a prefix of
 * another key, and the very first and last lines of the file.
 */
class SortedTsvTest {

    private val table = SortedTsv.of(
        listOf(
            "cas\tun",
            "casa\tdos\ttres",
            "casament\tquatre",
            "col·legi\tcinc",
            "història\tsis",
            "zebra\tset",
        ),
    )

    @Test
    fun `a key is found with all of its fields`() {
        assertEquals(listOf("casa", "dos", "tres"), table.find("casa"))
    }

    @Test
    fun `the first and last lines are reachable`() {
        assertEquals(listOf("cas", "un"), table.find("cas"))
        assertEquals(listOf("zebra", "set"), table.find("zebra"))
    }

    @Test
    fun `a key that is only a prefix of a listed one is a miss`() {
        assertNull(table.find("ca"))
        assertNull(table.find("casam"))
    }

    @Test
    fun `accents and the middle dot are matched byte for byte`() {
        assertEquals(listOf("història", "sis"), table.find("història"))
        assertEquals(listOf("col·legi", "cinc"), table.find("col·legi"))
    }

    @Test
    fun `an empty table answers nothing rather than failing`() {
        assertNull(SortedTsv.of(emptyList()).find("casa"))
        assertTrue(SortedTsv.of(emptyList()).keysStartingWith("c", 5).isEmpty())
        assertTrue(SortedTsv.of(emptyList()).isEmpty)
    }

    @Test
    fun `a prefix returns every key under it, in file order`() {
        assertEquals(
            listOf("cas", "casa", "casament"),
            table.keysStartingWith("cas", 10),
        )
        assertEquals(listOf("casa", "casament"), table.keysStartingWith("casa", 10))
    }

    @Test
    fun `a prefix stops at the limit`() {
        assertEquals(listOf("cas", "casa"), table.keysStartingWith("cas", 2))
        assertTrue(table.keysStartingWith("cas", 0).isEmpty())
    }

    @Test
    fun `a prefix matching nothing returns nothing`() {
        assertTrue(table.keysStartingWith("cat", 10).isEmpty())
        assertTrue(table.keysStartingWith("", 10).isEmpty())
        // Past the end of the file, where the lower bound lands on no line.
        assertTrue(table.keysStartingWith("zz", 10).isEmpty())
    }

    @Test
    fun `a multi-byte prefix does not split a character`() {
        assertEquals(listOf("història"), table.keysStartingWith("hist", 10))
        assertEquals(listOf("col·legi"), table.keysStartingWith("col·", 10))
    }
}
