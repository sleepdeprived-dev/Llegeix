package com.david.llegeix.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The word-from-a-sentence diff, pinned against real translator output.
 *
 * Every pair below was produced by ML Kit on a device, Catalan to English,
 * rather than written to make the algorithm look good — which matters, because
 * the thing being tested is precisely how the code behaves when the model
 * rewrites a sentence instead of quietly dropping a word from it. The cases
 * that must return null are as much the point as the ones that must answer.
 */
class ContextualGlossTest {

    @Test
    fun `recovers a word that changed meaning in its sentence`() {
        // "Va lligar el cap de la corda al pal" — cap alone translates as "no".
        assertEquals(
            "head",
            ContextualGloss.difference(
                whole = "Tied the head of the rope to the stick",
                ablated = "Tied the one of the rope to the stick",
                language = "en",
            ),
        )
    }

    @Test
    fun `recovers a two word reading`() {
        // "La planta baixa de l'edifici era buida" — planta alone is "plant".
        assertEquals(
            "ground floor",
            ContextualGloss.difference(
                whole = "The ground floor of the building was empty",
                ablated = "The lowering of the building was empty",
                language = "en",
            ),
        )
    }

    @Test
    fun `recovers a word dropped from the end`() {
        assertEquals(
            "key",
            ContextualGloss.difference(
                whole = "Turned the door lock key",
                ablated = "Turned the door lock",
                language = "en",
            ),
        )
    }

    @Test
    fun `gives up when the model rewrote the sentence`() {
        // Removing the word moved the subject and changed the verb; whatever
        // sits in the gap is not what the word meant.
        assertNull(
            ContextualGloss.difference(
                whole = "Went to the bank to get money from the account",
                ablated = "He went to take money from the account",
                language = "en",
            ),
        )
    }

    @Test
    fun `gives up when the gap is a clause rather than a word`() {
        assertNull(
            ContextualGloss.difference(
                whole = "There was a long tail in front of cinema",
                ablated = "There was a long front of the cinema",
                language = "en",
            ),
        )
    }

    @Test
    fun `gives up when nothing changed`() {
        assertNull(
            ContextualGloss.difference(
                whole = "The dog moved the tail",
                ablated = "The dog moved the tail",
                language = "en",
            ),
        )
    }

    @Test
    fun `gives up on a sentence too short to have anchors`() {
        assertNull(
            ContextualGloss.difference(
                whole = "The bank closed",
                ablated = "It closed",
                language = "en",
            ),
        )
    }

    @Test
    fun `strips the grammar clinging to the edges of the span`() {
        // The article belongs to the sentence, not to the word.
        assertEquals(
            "bench",
            ContextualGloss.difference(
                whole = "They saw a bench of fish under the boat",
                ablated = "They saw one of fish under the boat",
                language = "en",
            ),
        )
        // A trailing preposition, likewise: "letter from" is "letter".
        assertEquals(
            "letter",
            ContextualGloss.difference(
                whole = "Played the last letter from the fight",
                ablated = "Played the last of the fight",
                language = "en",
            ),
        )
    }

    @Test
    fun `aligns in the other target language too`() {
        assertEquals(
            "cheia",
            ContextualGloss.difference(
                whole = "A întors cheia de la broasca ușii",
                ablated = "A întors de la broasca ușii",
                language = "ro",
            ),
        )
    }

    @Test
    fun `gives up when the span is only grammar`() {
        assertNull(
            ContextualGloss.difference(
                whole = "He went to the shop and then home",
                ablated = "He went the shop and then home",
                language = "en",
            ),
        )
    }

    @Test
    fun `survives an ablation that made the translation longer`() {
        // Removing a word does not reliably shorten the translation: the model
        // re-reads the line and can produce more words than before. Both ends
        // of the alignment must be bounded by both sentences or the walk runs
        // off the end of the shorter one.
        assertNull(
            ContextualGloss.difference(
                whole = "one two three four",
                ablated = "one two three four five six",
                language = "en",
            ),
        )
        assertNull(
            ContextualGloss.difference(
                whole = "one two three four",
                ablated = "zero zero one two three four",
                language = "en",
            ),
        )
    }

    @Test
    fun `survives translations with nothing whatever in common`() {
        assertNull(
            ContextualGloss.difference(
                whole = "alpha beta gamma delta",
                ablated = "one two three four five six seven",
                language = "en",
            ),
        )
    }

    @Test
    fun `removes only the first occurrence of the word`() {
        assertEquals(
            "El gos va veure el gos",
            ContextualGloss.withoutWord("El gos va veure el gos petit", "petit"),
        )
        // The second "el" survives, so the line still reads as a line.
        assertEquals(
            "gos va veure el gos petit",
            ContextualGloss.withoutWord("El gos va veure el gos petit", "el"),
        )
    }

    @Test
    fun `refuses to ablate what it cannot find or cannot spare`() {
        assertNull(ContextualGloss.withoutWord("El gos dormia", "gat"))
        // Taking the word out would leave too little sentence to align against.
        assertNull(ContextualGloss.withoutWord("El gos dormia", "gos"))
        // A dragged phrase is not a word and has no single place to remove.
        assertNull(ContextualGloss.withoutWord("El gos dormia molt tranquil", "el gos"))
    }

    @Test
    fun `matches words regardless of punctuation and case`() {
        assertEquals(
            "Sense ell, res no tenia sentit",
            ContextualGloss.withoutWord("Sense ell, res —Cap! no tenia sentit", "cap"),
        )
    }
}
