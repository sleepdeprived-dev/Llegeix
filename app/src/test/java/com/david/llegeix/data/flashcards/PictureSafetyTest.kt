package com.david.llegeix.data.flashcards

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PictureSafetyTest {

    @Test
    fun `explicit searches are never sent`() {
        for (query in listOf("nude", "Naked woman", "porn", "sexy", "NSFW", "erotic art", "topless")) {
            assertTrue(query, PictureSafety.isBlockedQuery(query))
        }
    }

    @Test
    fun `a word's other forms are caught from one entry`() {
        assertTrue(PictureSafety.isBlockedQuery("nudity"))
        assertTrue(PictureSafety.isBlockedQuery("pornographic"))
        assertTrue(PictureSafety.isBlockedQuery("masturbation"))
        assertTrue(PictureSafety.isBlockedQuery("sex"))
        assertTrue(PictureSafety.isBlockedQuery("sexual"))
    }

    @Test
    fun `Catalan, Romanian and Spanish searches are caught too`() {
        for (query in listOf("despullada", "nua", "pornografia", "nud", "goală", "desnudo", "eròtic")) {
            assertTrue(query, PictureSafety.isBlockedQuery(query))
        }
    }

    @Test
    fun `graphic violence is kept out as well as sex`() {
        for (query in listOf("gore", "corpse", "beheading", "torture chamber", "dead body")) {
            assertTrue(query, PictureSafety.isBlockedQuery(query))
        }
    }

    @Test
    fun `everyday vocabulary is not caught by accident`() {
        for (word in listOf(
            "bread", "apple", "cat", "Sussex", "nuance", "chicken breasts", "gol", "goal",
            "nuts", "numbers", "tortoise", "tortuga", "execute", "butter", "sextant cheese",
            "gorge", "escargot", "cadena", "door", "sextet",
        )) {
            assertFalse(word, PictureSafety.isBlockedQuery(word))
        }
    }

    @Test
    fun `a photo is dropped for its title or for any tag`() {
        assertTrue(PictureSafety.isBlockedPhoto("Beach day", listOf("summer", "nude")))
        assertTrue(PictureSafety.isBlockedPhoto("Sexy bread", emptyList()))
        assertFalse(PictureSafety.isBlockedPhoto("Fresh bread", listOf("bakery", "loaf", "food")))
    }

    @Test
    fun `accents do not slip a word past the list`() {
        assertTrue(PictureSafety.isBlockedPhoto("Érotica", emptyList()))
    }
}
