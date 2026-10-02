package com.david.llegeix.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading a word by its neighbours.
 *
 * The expression match and the grammar reading are exact and are tested as
 * such. The sense ranking is a guess, so what is pinned down here is not that
 * it picks the right meaning — it often cannot — but that it declines to claim
 * one when the evidence is thin or shared between senses. That refusal is the
 * property the rest of the app depends on.
 */
class CatalanContextTest {

    private val listed = setOf("banc de dades", "base de dades", "a boca de canó", "cap de setmana")

    @Test
    fun `finds an expression from a tap on any of its words`() {
        val tokens = CatalanContext.tokenise("Va consultar la base de dades ahir")
        // "base", "de" and "dades" all have to reach the same expression: the
        // reader taps the word they did not recognise, not the first one.
        for (index in 3..5) {
            assertEquals(
                "base de dades",
                CatalanContext.phraseAround(tokens, index) { it in listed },
            )
        }
    }

    @Test
    fun `prefers the longer expression`() {
        val tokens = CatalanContext.tokenise("Ho va dir a boca de canó")
        assertEquals(
            "a boca de canó",
            CatalanContext.phraseAround(tokens, 5) { it in listed },
        )
    }

    @Test
    fun `finds nothing when the words are merely adjacent`() {
        val tokens = CatalanContext.tokenise("La base del turó")
        assertNull(CatalanContext.phraseAround(tokens, 1) { it in listed })
    }

    @Test
    fun `reads the part of speech off an auxiliary`() {
        // "ha cantat" is a participle whatever else "cantat" might be.
        assertEquals(setOf(CatalanContext.VERB), CatalanContext.posAround("ha", "bé"))
        assertEquals(setOf(CatalanContext.VERB), CatalanContext.posAround("va", "dir"))
        assertEquals(setOf(CatalanContext.VERB), CatalanContext.posAround("es", "diu"))
    }

    @Test
    fun `reads a noun off a determiner`() {
        assertEquals(
            setOf(CatalanContext.NOUN, CatalanContext.ADJECTIVE),
            CatalanContext.posAround("el", "de"),
        )
        assertEquals(
            setOf(CatalanContext.ADJECTIVE, CatalanContext.ADVERB),
            CatalanContext.posAround("molt", null),
        )
    }

    @Test
    fun `has no opinion where the cue is ambiguous`() {
        // "la" is a determiner in "la casa" and a pronoun in "la veig", so it
        // is deliberately absent rather than guessed at.
        assertTrue(CatalanContext.posAround("la", "casa").isEmpty())
        assertTrue(CatalanContext.posAround(null, null).isEmpty())
    }

    @Test
    fun `reconciles the two files' names for a part of speech`() {
        assertEquals(CatalanContext.NOUN, CatalanContext.normalisePos("n"))
        assertEquals(CatalanContext.NOUN, CatalanContext.normalisePos("nom"))
        assertEquals(CatalanContext.ADJECTIVE, CatalanContext.normalisePos("adj/n"))
        // The thesaurus hangs a semantic note off the tag; the tag is the part.
        assertEquals(CatalanContext.NOUN, CatalanContext.normalisePos("n; d'una flor"))
    }

    @Test
    fun `weighs a word that tells the senses apart above one that does not`() {
        val shared = CatalanContext.Candidate("shared", CatalanContext.NOUN, setOf("cosa", "peix"))
        val other = CatalanContext.Candidate("other", CatalanContext.NOUN, setOf("cosa", "oficina"))
        val ranked = CatalanContext.rankSenses(listOf(shared, other), setOf("cosa", "peix"))

        assertEquals("shared", ranked.first().value)
        // "cosa" is in both, so it is worth half a point to each; "peix" is in
        // one, so it is worth a whole one.
        assertEquals(1.5, ranked.first().score, 0.001)
        assertEquals(0.5, ranked.last().score, 0.001)
    }

    @Test
    fun `declines to claim a sense the evidence is split on`() {
        val a = CatalanContext.Candidate("a", CatalanContext.NOUN, setOf("aigua"))
        val b = CatalanContext.Candidate("b", CatalanContext.NOUN, setOf("aigua"))
        assertFalse(CatalanContext.isClear(CatalanContext.rankSenses(listOf(a, b), setOf("aigua"))))
    }

    @Test
    fun `declines to claim a sense on no evidence at all`() {
        val a = CatalanContext.Candidate("a", CatalanContext.NOUN, setOf("aigua"))
        val b = CatalanContext.Candidate("b", CatalanContext.NOUN, setOf("foc"))
        val ranked = CatalanContext.rankSenses(listOf(a, b), setOf("gos"), setOf(CatalanContext.NOUN))
        // Both got the grammar bonus and neither got a word, so there is
        // nothing to point at even though the scores are not zero.
        assertFalse(CatalanContext.isClear(ranked))
    }

    @Test
    fun `claims a sense the evidence is clearly behind`() {
        val a = CatalanContext.Candidate("a", CatalanContext.NOUN, setOf("peix", "mar", "barca"))
        val b = CatalanContext.Candidate("b", CatalanContext.NOUN, setOf("diner"))
        val ranked = CatalanContext.rankSenses(listOf(a, b), setOf("peix", "mar"))
        assertTrue(CatalanContext.isClear(ranked))
        assertEquals("a", ranked.first().value)
        assertEquals(listOf("peix", "mar"), ranked.first().support.sortedDescending())
    }

    @Test
    fun `the grammar lifts the sense the neighbours allow`() {
        val noun = CatalanContext.Candidate("noun", CatalanContext.NOUN, setOf("cap"))
        val verb = CatalanContext.Candidate("verb", CatalanContext.VERB, setOf("cap"))
        val ranked = CatalanContext.rankSenses(
            listOf(verb, noun),
            context = setOf("cap"),
            allowedPos = setOf(CatalanContext.NOUN),
        )
        assertEquals("noun", ranked.first().value)
    }

    @Test
    fun `the grammar never removes a sense, only demotes it`() {
        val noun = CatalanContext.Candidate("noun", CatalanContext.NOUN, setOf("cap"))
        val verb = CatalanContext.Candidate("verb", CatalanContext.VERB, setOf("cap"))
        val ranked = CatalanContext.rankSenses(
            listOf(noun, verb),
            context = setOf("cap"),
            allowedPos = setOf(CatalanContext.NOUN),
        )
        // Reading the part of speech off the neighbours is a heuristic like any
        // other. One that can delete the right answer takes away the reader's
        // ability to notice it was wrong, which is the whole point of ranking
        // rather than choosing.
        assertEquals(2, ranked.size)
        assertEquals(setOf("noun", "verb"), ranked.map { it.value }.toSet())
    }

    @Test
    fun `a matched word outweighs the grammar disagreeing`() {
        val allowed = CatalanContext.Candidate("allowed", CatalanContext.NOUN, setOf("aigua"))
        val evidenced = CatalanContext.Candidate("evidenced", CatalanContext.VERB, setOf("foc"))
        val ranked = CatalanContext.rankSenses(
            listOf(allowed, evidenced),
            context = setOf("foc"),
            allowedPos = setOf(CatalanContext.NOUN),
        )
        // The neighbours are a hint; a word actually on the page is evidence.
        assertEquals("evidenced", ranked.first().value)
    }

    @Test
    fun `keeps the words that could tell two meanings apart`() {
        val content = CatalanContext.contentWords("El gos petit va veure una cosa a la platja")
        assertEquals(listOf("gos", "petit", "veure", "platja"), content)
    }

    @Test
    fun `tokenises the way the reference files spell things`() {
        assertEquals(
            listOf("l'aigua", "del", "col·legi"),
            CatalanContext.tokenise("L’aigua del col·legi,"),
        )
    }
}
