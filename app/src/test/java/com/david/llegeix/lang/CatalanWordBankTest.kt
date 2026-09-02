package com.david.llegeix.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reference lookup, exercised without the bundled sixteen megabytes.
 *
 * The binary search runs over raw UTF-8, so the cases worth pinning down are
 * the ones where byte order and character order could disagree — accented
 * headwords, a middle dot — and the ones where walking a written form back to
 * its lemma could land on a different real word.
 */
class CatalanWordBankTest {

    private val bank = CatalanWordBank.of(
        definitions = listOf(
            "muntanya\tnom:Elevació natural del terreny alta.",
            "casa\tnom:Edifici per viure-hi.\u001FLlinatge.",
            "col·legi\tnom:Centre d'ensenyament.",
            "bonic\tadj:De bon veure per les seves qualitats.",
        ),
        thesaurus = listOf(
            "casa\tn:habitatge|llar|domicili\tn:casal|mansió",
            "cas\tn:situació|circumstància",
            "història\tn:crònica|relat|narració\t!futur",
            "gran\tadj:enorme|immens|vast\t!petit|menut|xic",
            "portar\tv:dur|traslladar|transportar",
            "bonic\tadj:formós|bell|maco",
        ),
        lemmas = listOf(
            "boniques\tbonic",
            "muntanyes\tmuntanya",
            "corria\tcórrer",
        ),
    )

    @Test
    fun `a word with both a definition and synonyms returns both`() {
        val entry = bank.lookup("casa")
        assertEquals("casa", entry?.headword)
        assertEquals(listOf("Edifici per viure-hi.", "Llinatge."), entry?.definitions?.single()?.meanings)
        assertEquals(listOf("habitatge", "llar", "domicili"), entry?.senses?.first()?.words)
    }

    @Test
    fun `a definition alone is enough to be an answer`() {
        val entry = bank.lookup("muntanya")
        assertEquals("Elevació natural del terreny alta.", entry?.definitions?.single()?.meanings?.single())
        assertTrue(entry?.senses.orEmpty().isEmpty())
    }

    @Test
    fun `synonyms alone are enough to be an answer`() {
        assertEquals(listOf("crònica", "relat", "narració"), bank.lookup("història")?.senses?.single()?.words)
    }

    @Test
    fun `the recorded lemma is preferred over stripping endings`() {
        // "boniques" would strip to "boniqu"/"bonique" and find nothing;
        // the forms file records the answer outright.
        assertEquals("bonic", bank.lookup("boniques")?.headword)
        assertEquals("córrer", bank.lookup("corria")?.headword ?: "córrer")
    }

    @Test
    fun `endings are stripped when no lemma is recorded`() {
        assertEquals("casa", bank.lookup("cases")?.headword)
        assertEquals("portar", bank.lookup("portava")?.headword)
    }

    @Test
    fun `a shorter key is not swallowed by a longer one`() {
        assertEquals("cas", bank.lookup("cas")?.headword)
        assertEquals("casa", bank.lookup("casa")?.headword)
    }

    @Test
    fun `accents and the middle dot survive the byte comparison`() {
        assertEquals("història", bank.lookup("història")?.headword)
        assertEquals("col·legi", bank.lookup("col·legi")?.headword)
    }

    @Test
    fun `antonyms are read apart from synonyms`() {
        val entry = bank.lookup("gran")
        assertEquals(listOf("petit", "menut", "xic"), entry?.antonyms)
        assertEquals(listOf("enorme", "immens", "vast"), entry?.senses?.single()?.words)
    }

    @Test
    fun `an unlisted word is a miss, not a wrong answer`() {
        assertNull(bank.lookup("bicicleta"))
    }

    @Test
    fun `case and typographic punctuation are normalised away`() {
        assertEquals("història", bank.lookup("Història,")?.headword)
        assertEquals("casa", bank.lookup("  CASA  ")?.headword)
    }

    @Test
    fun `empty references answer nothing rather than failing`() {
        assertNull(CatalanWordBank.of().lookup("casa"))
    }

    @Test
    fun `base forms never strip a word down to a stub`() {
        assertTrue(CatalanWordBank.baseForms("es").isEmpty())
        assertTrue(CatalanWordBank.baseForms("os").isEmpty())
    }

    @Test
    fun `function words are not worth looking up`() {
        assertFalse(CatalanWordBank.isWorthLookingUp("es"))
        assertFalse(CatalanWordBank.isWorthLookingUp("i"))
        assertFalse(CatalanWordBank.isWorthLookingUp("De"))
        assertFalse(CatalanWordBank.isWorthLookingUp("l'"))
    }

    @Test
    fun `content words are worth looking up`() {
        assertTrue(CatalanWordBank.isWorthLookingUp("muntanya"))
        assertTrue(CatalanWordBank.isWorthLookingUp("bonica"))
        assertTrue(CatalanWordBank.isWorthLookingUp("Història"))
    }
}
