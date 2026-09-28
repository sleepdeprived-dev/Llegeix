package com.david.llegeix.data.flashcards

import org.junit.Assert.assertEquals
import org.junit.Test

class DeckNamesTest {

    @Test
    fun `a new name is accepted and tidied`() {
        assertEquals(
            DeckNames.Check.Ok("Menjar i beure"),
            DeckNames.check("  Menjar   i beure ", others = listOf("Verbs")),
        )
    }

    @Test
    fun `a blank name is refused`() {
        assertEquals(DeckNames.Check.Blank, DeckNames.check("", others = emptyList()))
        assertEquals(DeckNames.Check.Blank, DeckNames.check("   \t ", others = emptyList()))
    }

    @Test
    fun `a name differing only in case is taken`() {
        assertEquals(
            DeckNames.Check.Taken("Menjar"),
            DeckNames.check("menjar", others = listOf("Verbs", "Menjar")),
        )
    }

    @Test
    fun `a name differing only in spacing is taken`() {
        assertEquals(
            DeckNames.Check.Taken("Viatges curts"),
            DeckNames.check("Viatges curts ", others = listOf("Viatges  curts")),
        )
    }

    @Test
    fun `accented letters are not folded into plain ones`() {
        // "Pèl" and "Pel" are different Catalan words, so they may be two decks.
        assertEquals(DeckNames.Check.Ok("Pel"), DeckNames.check("Pel", others = listOf("Pèl")))
    }

    @Test
    fun `accented capitals clash with their lower case`() {
        assertEquals(
            DeckNames.Check.Taken("Èxits"),
            DeckNames.check("èxits", others = listOf("Èxits")),
        )
    }

    @Test
    fun `renaming a deck to a new capitalisation of its own name is allowed`() {
        // The caller leaves the deck being renamed out of the others.
        assertEquals(DeckNames.Check.Ok("VERBS"), DeckNames.check("VERBS", others = listOf("Menjar")))
    }
}
