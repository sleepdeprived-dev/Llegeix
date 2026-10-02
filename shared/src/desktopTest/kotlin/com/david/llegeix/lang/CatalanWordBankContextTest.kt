package com.david.llegeix.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reference shelf read in context rather than in isolation.
 *
 * The fixture is deliberately shaped like the real files: synonym groups whose
 * own words are too rare to appear in a sentence, and definitions that carry
 * the ordinary words a reader might actually be looking at. That shape is why
 * the synonyms are expanded through their definitions before anything is
 * compared — *peixada* will not be in the line, but *peix* may well be.
 */
class CatalanWordBankContextTest {

    private val bank = CatalanWordBank.of(
        definitions = listOf(
            "banc\tnom:Seient estret i llarg per a diverses persones." +
                "Entitat on es dipositen diners.",
            "banc de dades\tnom:Conjunt organitzat de dades emmagatzemades.",
            "peixada\tnom:Gran quantitat de peixos que van junts.",
            "sucursal\tnom:Oficina dependent d'una entitat bancària.",
        ),
        thesaurus = listOf(
            // Spelled as the real thesaurus spells it: the shoal sense lists
            // "peixos" outright, which is what a line about fish can match.
            "banc\tn:peixada|peixalla|peixos\tn:oficina|sucursal|filial",
        ),
        lemmas = emptyList(),
    )

    @Test
    fun `finds the expression the word belongs to`() {
        val tokens = CatalanContext.tokenise("Va consultar el banc de dades ahir")
        val reading = bank.readInContext(tokens, index = 3, passage = "")
        assertEquals("banc de dades", reading.phrase)
    }

    @Test
    fun `reports no expression when the word simply stands next to others`() {
        val tokens = CatalanContext.tokenise("Es va asseure al banc del parc")
        assertNull(bank.readInContext(tokens, index = 4, passage = "").phrase)
    }

    @Test
    fun `brings the sense the passage points at to the front`() {
        val line = "Van veure un banc de peixos sota la barca"
        val tokens = CatalanContext.tokenise(line)
        val reference = bank.readInContext(tokens, index = 3, passage = line).reference

        assertEquals(listOf("peixada", "peixalla", "peixos"), reference?.senses?.first()?.words)
        assertTrue(reference?.isLeadingSenseLikely == true)
        // The reader is told which word did the arguing, not just the verdict.
        assertEquals(listOf("peixos"), reference?.contextSupport)
    }

    @Test
    fun `ranks on a definition match but does not claim one`() {
        // "entitat" is not a synonym of banc; it only turns up in the prose
        // defining "sucursal". That is enough to order the senses and nowhere
        // near enough to point at one, or the longest definition would win
        // every time simply for using the most ordinary words.
        val line = "Hi treballava per a una entitat des de feia anys"
        val tokens = CatalanContext.tokenise("Va entrar al banc aquell dia")
        val reference = bank.readInContext(tokens, index = 3, passage = line).reference

        assertEquals(listOf("oficina", "sucursal", "filial"), reference?.senses?.first()?.words)
        assertFalse(reference?.isLeadingSenseLikely == true)
    }

    @Test
    fun `keeps every sense, only reordered`() {
        val line = "Van veure un banc de peixos sota la barca"
        val tokens = CatalanContext.tokenise(line)
        val reference = bank.readInContext(tokens, index = 3, passage = line).reference

        // The point of ranking rather than choosing: the sources leave out a
        // word's plainest meaning — this fixture has no bench either — so the
        // losing senses stay on screen for the reader to overrule the guess.
        assertEquals(2, reference?.senses?.size)
    }

    @Test
    fun `makes no claim when the passage says nothing`() {
        val tokens = CatalanContext.tokenise("Al banc")
        val reference = bank.readInContext(tokens, index = 1, passage = "Al banc").reference

        assertFalse(reference?.isLeadingSenseLikely == true)
        assertTrue(reference?.contextSupport.isNullOrEmpty())
    }

    @Test
    fun `puts the definition the passage points at first`() {
        val line = "Hi havia diners al banc de la cantonada"
        val tokens = CatalanContext.tokenise(line)
        val reference = bank.readInContext(tokens, index = 4, passage = line).reference

        val meanings = reference?.definitions?.first()?.meanings.orEmpty()
        assertTrue(meanings.first().startsWith("Entitat"))
    }
}
