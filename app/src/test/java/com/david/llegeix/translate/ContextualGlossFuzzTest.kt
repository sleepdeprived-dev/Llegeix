package com.david.llegeix.translate

import kotlin.random.Random
import org.junit.Test

/**
 * The alignment, fed rubbish on purpose.
 *
 * Every input here comes from a translation model rather than from the app, so
 * nothing about its shape can be relied on: it may be longer than what it was
 * derived from, share no word with it, be punctuation, or be one enormous
 * token. The contract is narrow — return a span or return null — and the one
 * thing that must never happen is a throw, because it would take down a lookup
 * the reader is waiting on.
 *
 * A crash is exactly what a fixed seed caught here first: both ends of the walk
 * were bounded by the ablated sentence, so a translation that got *longer* when
 * a word was removed indexed past the end of the other one.
 */
class ContextualGlossFuzzTest {

    private val pieces = listOf(
        "the", "a", "bank", "bench", "fish", "under", "boat", "head", "rope",
        "ground", "floor", "—", "!", "don't", "l'", "ușii", "cheia", "", " ",
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "12", ".",
    )

    @Test
    fun `never throws, whatever the model returns`() {
        val random = Random(20260904)
        repeat(20_000) {
            val whole = sentence(random)
            val ablated = sentence(random)
            val language = listOf("en", "ro", "de", "").random(random)
            // The result is not asserted on: for arbitrary pairs there is no
            // right answer, only the requirement that asking is safe.
            ContextualGloss.difference(whole, ablated, language)
        }
    }

    @Test
    fun `never throws when the ablated line really is derived from the whole`() {
        val random = Random(1312)
        repeat(20_000) {
            val words = List(random.nextInt(0, 12)) { pieces.random(random) }
            val whole = words.joinToString(" ")
            // The realistic shape: the same sentence with one word gone, and
            // sometimes a word put in its place, which is what the model does.
            val ablated = words.toMutableList().apply {
                if (isNotEmpty()) {
                    val at = random.nextInt(size)
                    if (random.nextBoolean()) removeAt(at) else set(at, pieces.random(random))
                }
            }.joinToString(" ")
            ContextualGloss.difference(whole, ablated, "en")
            ContextualGloss.withoutWord(whole, pieces.random(random))
        }
    }

    @Test
    fun `ablation never throws on any line and any word`() {
        val random = Random(77)
        repeat(20_000) {
            ContextualGloss.withoutWord(sentence(random), pieces.random(random))
        }
    }

    private fun sentence(random: Random): String =
        List(random.nextInt(0, 14)) { pieces.random(random) }.joinToString(" ")
}
