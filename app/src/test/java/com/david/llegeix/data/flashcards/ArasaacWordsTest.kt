package com.david.llegeix.data.flashcards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArasaacWordsTest {

    @Test
    fun `the search asks for exact Catalan matches, safely encoded`() {
        assertEquals(
            "https://api.arasaac.org/v1/pictograms/ca/bestsearch/pastanaga",
            ArasaacWords.exactSearchUrl(" pastanaga "),
        )
        assertEquals(
            "https://api.arasaac.org/v1/pictograms/ca/bestsearch/cafe%20amb%20llet",
            ArasaacWords.exactSearchUrl("cafe amb llet"),
        )
        assertEquals("https://api.arasaac.org/v1/pictograms/ro/2619", ArasaacWords.pictogramUrl("ro", 2619))
    }

    @Test
    fun `only a pictogram labelled with the very word counts`() {
        val json = """[
            {"_id": 10, "keywords": [{"keyword": "pastís de pastanaga"}]},
            {"_id": 2619, "keywords": [{"keyword": "Pastanaga"}]}
        ]"""
        assertEquals(2619, ArasaacWords.firstExactId(json, "pastanaga"))
        assertNull(ArasaacWords.firstExactId(json, "pastís"))
    }

    @Test
    fun `no match, or no answer, is no meaning`() {
        assertNull(ArasaacWords.firstExactId("[]", "pa"))
        assertNull(ArasaacWords.firstExactId("<html>", "pa"))
        assertNull(ArasaacWords.firstExactId("""[{"_id": -1, "keywords": [{"keyword": "pa"}]}]""", "pa"))
    }

    @Test
    fun `the first label is the meaning`() {
        assertEquals("morcov", ArasaacWords.firstKeyword("""{"keywords": [{"keyword": " morcov "}, {"keyword": "x"}]}"""))
        assertEquals("pâine", ArasaacWords.firstKeyword("""{"keywords": [{"keyword": ""}, {"keyword": "pâine"}]}"""))
        assertNull(ArasaacWords.firstKeyword("""{"keywords": []}"""))
        assertNull(ArasaacWords.firstKeyword("not json"))
    }

    @Test
    fun `old cedilla letters become the Romanian comma letters`() {
        assertEquals("mașină", ArasaacWords.tidyRomanian("maşină"))
        assertEquals("oraș", ArasaacWords.tidyRomanian("oraş"))
        assertEquals("Țară", ArasaacWords.tidyRomanian("Ţară"))
        assertEquals("apple", ArasaacWords.tidyRomanian("apple"))
    }
}
