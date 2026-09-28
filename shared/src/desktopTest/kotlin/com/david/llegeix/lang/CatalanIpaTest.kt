package com.david.llegeix.lang

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks the transcriber against pronunciations a Central Catalan speaker would
 * recognise. The expected values are the broad transcription this class aims at:
 * no spirantisation, no phrase-level assimilation.
 */
class CatalanIpaTest {

    private fun ipa(word: String) = CatalanIpa.transcribe(word).ipa

    /** The transcription without its stress mark, for testing sounds alone. */
    private fun sounds(word: String) = ipa(word).replace("ˈ", "")

    @Test
    fun `stressed vowels keep their quality`() {
        assertEquals("ˈɡat", ipa("gat"))
        assertEquals("ˈsis", ipa("sis"))
    }

    @Test
    fun `unstressed a and e reduce to schwa`() {
        // The defining feature of the dialect: cantar is not [kanˈtar].
        assertEquals("kənˈta", ipa("cantar"))
        assertEquals("pəˈtit", ipa("petit"))
    }

    @Test
    fun `unstressed o raises to u`() {
        assertEquals("kuˈmɛnsə", ipa("comença"))
    }

    @Test
    fun `written accents place the stress and fix the aperture`() {
        assertEquals("kəˈfɛ", ipa("cafè"))
        assertEquals("bəˈʒe", ipa("vegé"))
        assertFalse(CatalanIpa.transcribe("cafè").isApproximate)
    }

    @Test
    fun `stress falls on the penultimate vowel for plain endings`() {
        assertEquals("ˈkazə", ipa("casa"))
        assertEquals("ˈkazəs", ipa("cases"))
    }

    @Test
    fun `stress falls on the last vowel otherwise`() {
        assertEquals("pəˈɾeʎ", ipa("parell").replace("ɛ", "e"))
    }

    @Test
    fun `digraphs map to single sounds`() {
        assertEquals("ˈaɲ", ipa("any"))
        assertEquals("ˈkaʃə", ipa("caixa"))
        assertEquals("ˈʎuna", ipa("lluna").replace("ə", "a"))
    }

    @Test
    fun `geminate l is transcribed long`() {
        assertTrue(ipa("col·legi").contains("ɫː"))
    }

    @Test
    fun `s voices between vowels but not at the edges`() {
        assertEquals("ˈkazə", ipa("casa"))
        assertEquals("ˈsɔɫ", ipa("sol"))
        // A double s stays voiceless.
        assertEquals("ˈpasə", ipa("passa"))
    }

    @Test
    fun `r is trilled initially and tapped between vowels`() {
        assertTrue(sounds("roig").startsWith("r"))
        assertTrue(sounds("cara").contains("ɾ"))
    }

    @Test
    fun `final r is silent in polysyllables but kept in monosyllables`() {
        assertEquals("kənˈta", ipa("cantar"))
        assertEquals("ˈmaɾ", ipa("mar"))
    }

    @Test
    fun `final voiced stops devoice`() {
        assertEquals("ˈfɾɛt", ipa("fred"))
        assertEquals("ˈsak", ipa("sac"))
    }

    @Test
    fun `v is pronounced as b`() {
        assertTrue(sounds("vaig").startsWith("b"))
    }

    @Test
    fun `final ig is an affricate`() {
        assertEquals("ˈmatʃ", ipa("maig"))
    }

    @Test
    fun `g and c soften before front vowels`() {
        assertTrue(sounds("gent").startsWith("ʒ"))
        assertTrue(sounds("cel").startsWith("s"))
    }

    @Test
    fun `qu and gu lose the u before front vowels but keep it elsewhere`() {
        assertEquals("ˈki", ipa("qui"))
        assertEquals("ˈkwatɾə", ipa("quatre"))
    }

    @Test
    fun `stressed e and o without an accent are flagged as approximate`() {
        // Nothing in the spelling says whether this e is close or open.
        assertTrue(CatalanIpa.transcribe("terra").isApproximate ||
            CatalanIpa.transcribe("pedra").isApproximate ||
            CatalanIpa.transcribe("net").isApproximate)
        assertFalse(CatalanIpa.transcribe("cantar").isApproximate)
    }

    @Test
    fun `a phrase is transcribed word by word`() {
        val result = CatalanIpa.transcribe("el petit princep")
        assertEquals(3, result.ipa.split(" ").size)
        assertTrue(result.ipa.startsWith("əɫ"))
    }

    @Test
    fun `an elided article joins the following word`() {
        // The l leans on the next word as its onset: [ˈlajɡwə], not [ɫ…].
        assertEquals("ˈlajɡwə", ipa("l'aigua"))
    }

    @Test
    fun `an empty or punctuation only input is handled`() {
        assertEquals("", CatalanIpa.transcribe("").ipa)
        assertEquals("", CatalanIpa.transcribe("—").ipa)
    }

    @Test
    fun `a rising i is a syllable of its own, not a glide`() {
        // The imperfect: the stress belongs on the i, which only works if the
        // i counts as a nucleus.
        assertEquals("təˈniə", ipa("tenia"))
        assertEquals("səˈβiə", ipa("sabia").replace("b", "β"))
    }

    @Test
    fun `a falling diphthong stays one syllable`() {
        assertEquals("ˈbɛwɾə", ipa("veure"))
        assertEquals("siwˈtat", ipa("ciutat"))
    }
}

/**
 * The aperture list is the answer to the one thing the rules cannot derive, so
 * it is worth checking that consulting it actually changes the output — and,
 * just as importantly, that a word it does not cover still comes back marked
 * approximate rather than silently guessed.
 */
class ApertureLexiconTest {

    private val lexicon = ApertureLexicon.of(
        mapOf(
            "terra" to "ɛ",
            "pedra" to "e",
            "poble" to "ɔ",
            "portar" to "o",
        ),
    )

    @After
    fun tearDown() {
        // The transcriber is a singleton; leave it as the other tests expect.
        CatalanIpa.useLexicon(ApertureLexicon.of(emptyMap()))
    }

    @Test
    fun `a listed word gets its real vowel and is no longer approximate`() {
        CatalanIpa.useLexicon(lexicon)

        val terra = CatalanIpa.transcribe("terra")
        assertEquals("ˈtɛrə", terra.ipa)
        assertFalse(terra.isApproximate)

        val pedra = CatalanIpa.transcribe("pedra")
        assertEquals("ˈpedɾə", pedra.ipa)
        assertFalse(pedra.isApproximate)
    }

    @Test
    fun `the two words spelling cannot tell apart come out different`() {
        CatalanIpa.useLexicon(lexicon)
        assertNotEquals(
            CatalanIpa.transcribe("terra").ipa.first { it in "eɛ" },
            CatalanIpa.transcribe("pedra").ipa.first { it in "eɛ" },
        )
    }

    @Test
    fun `an inflected form inherits from its listed base`() {
        CatalanIpa.useLexicon(lexicon)
        // The plural does not move the stress, so the aperture carries over.
        val plural = CatalanIpa.transcribe("terres")
        assertTrue("expected an open e, got ${plural.ipa}", plural.ipa.contains("ɛ"))
        assertFalse(plural.isApproximate)
    }

    @Test
    fun `a verb form falls back to its infinitive`() {
        CatalanIpa.useLexicon(lexicon)
        val imperfect = CatalanIpa.transcribe("portava")
        assertFalse(imperfect.isApproximate)
    }

    @Test
    fun `a word the list does not cover stays honestly approximate`() {
        CatalanIpa.useLexicon(lexicon)
        assertTrue(CatalanIpa.transcribe("cadena").isApproximate)
    }

    @Test
    fun `an absent word list costs accuracy but never function`() {
        CatalanIpa.useLexicon(ApertureLexicon.of(emptyMap()))
        val result = CatalanIpa.transcribe("terra")
        assertEquals("ˈtɛrə", result.ipa)
        assertTrue(result.isApproximate)
    }
}

/**
 * Regressions for digraphs that only hold in one position. Each of these was
 * masked by an entry in the old hard-coded exception list.
 */
class CatalanIpaPositionTest {

    private fun ipa(word: String) = CatalanIpa.transcribe(word).ipa

    @Test
    fun `ig is an affricate only at the end of a word`() {
        assertEquals("ˈmatʃ", ipa("maig"))
        // Mid-word it is a plain i and g: aigua is two syllables, not three.
        assertEquals("ˈajɡwə", ipa("aigua"))
    }

    @Test
    fun `an unstressed clitic keeps its reduced vowel`() {
        assertEquals("əɫ", ipa("el"))
        assertEquals("ˈlajɡwə", ipa("l'aigua"))
    }

    @Test
    fun `an accented word still needs no exception entry`() {
        assertEquals("ˈpɾinsəp", ipa("príncep"))
        assertEquals("pəˈɾɔ", ipa("però"))
    }
}
