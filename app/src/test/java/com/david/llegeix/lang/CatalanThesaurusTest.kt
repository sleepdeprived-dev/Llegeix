package com.david.llegeix.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The thesaurus lookup, exercised without the bundled five-megabyte asset.
 *
 * The binary search runs over raw UTF-8, so the cases that matter are the ones
 * where byte order and character order could disagree: accented headwords, a
 * middle dot, and keys that are prefixes of other keys.
 */
class CatalanThesaurusTest {

    private val dictionary = CatalanThesaurus.of(
        listOf(
            "casa\tn:habitatge|llar|domicili\tn:casal|mansió",
            "cas\tn:situació|circumstància",
            "història\tn:crònica|relat|narració\t!futur",
            "gran\tadj:enorme|immens|vast\t!petit|menut|xic",
            "col·legi\tn:escola|institut",
            "portar\tv:dur|traslladar|transportar",
            "bonic\tadj:formós|bell|maco",
        ),
    )

    @Test
    fun `finds a plain headword`() {
        val entry = dictionary.lookup("casa")
        assertEquals("casa", entry?.headword)
        assertEquals(2, entry?.senses?.size)
        assertEquals(listOf("habitatge", "llar", "domicili"), entry?.senses?.first()?.words)
    }

    @Test
    fun `a shorter key is not swallowed by a longer one`() {
        assertEquals("cas", dictionary.lookup("cas")?.headword)
        assertEquals("casa", dictionary.lookup("casa")?.headword)
    }

    @Test
    fun `accents and the middle dot survive the byte comparison`() {
        assertEquals("història", dictionary.lookup("història")?.headword)
        assertEquals("col·legi", dictionary.lookup("col·legi")?.headword)
    }

    @Test
    fun `reads antonyms apart from synonyms`() {
        val entry = dictionary.lookup("gran")
        assertEquals(listOf("petit", "menut", "xic"), entry?.antonyms)
        assertEquals(listOf("enorme", "immens", "vast"), entry?.senses?.single()?.words)
    }

    @Test
    fun `an unlisted word is a miss, not a wrong answer`() {
        assertNull(dictionary.lookup("bicicleta"))
    }

    @Test
    fun `a plural falls back to the listed singular`() {
        assertEquals("casa", dictionary.lookup("cases")?.headword)
        assertEquals("història", dictionary.lookup("històries")?.headword)
    }

    @Test
    fun `a conjugated verb falls back to the infinitive`() {
        assertEquals("portar", dictionary.lookup("portava")?.headword)
        assertEquals("portar", dictionary.lookup("portaven")?.headword)
    }

    @Test
    fun `case and typographic punctuation are normalised away`() {
        assertEquals("història", dictionary.lookup("Història,")?.headword)
        assertEquals("casa", dictionary.lookup("  CASA  ")?.headword)
    }

    @Test
    fun `an empty dictionary answers nothing rather than failing`() {
        assertNull(CatalanThesaurus.of(emptyList()).lookup("casa"))
    }

    @Test
    fun `base forms never strip a word down to a stub`() {
        assertTrue(CatalanThesaurus.baseForms("es").isEmpty())
        assertTrue(CatalanThesaurus.baseForms("os").isEmpty())
    }

    @Test
    fun `function words are not worth looking up`() {
        assertFalse(CatalanThesaurus.isWorthLookingUp("es"))
        assertFalse(CatalanThesaurus.isWorthLookingUp("i"))
        assertFalse(CatalanThesaurus.isWorthLookingUp("De"))
        assertFalse(CatalanThesaurus.isWorthLookingUp("l'"))
    }

    @Test
    fun `content words are worth looking up`() {
        assertTrue(CatalanThesaurus.isWorthLookingUp("muntanya"))
        assertTrue(CatalanThesaurus.isWorthLookingUp("bonica"))
        assertTrue(CatalanThesaurus.isWorthLookingUp("Història"))
    }

    @Test
    fun `a feminine adjective falls back to the masculine entry`() {
        assertEquals("bonic", dictionary.lookup("bonica")?.headword)
    }

    @Test
    fun `a listed word in -a is answered as itself, not as its stem`() {
        assertEquals("casa", dictionary.lookup("casa")?.headword)
    }
}
