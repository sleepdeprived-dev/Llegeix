package com.david.llegeix.translate

/**
 * What a word contributed to the translation of the sentence it was in.
 *
 * The trick is to ask the translator twice — once for the line, once for the
 * line with the word taken out — and keep what disappeared. The translator is
 * a sentence model, so the line it was given carries the context the word on
 * its own has none of: *cap* alone comes back as *no*, but *va lligar el cap de
 * la corda* puts *head* in the sentence, and taking *cap* out takes *head* with
 * it.
 *
 * This is the only contextual reading in the app allowed to change what the
 * reader is told a word means, because it is the only one derived from the real
 * sentence rather than from a guess about which dictionary sense applies.
 *
 * It is also wrong sometimes, in the ordinary way a translation is wrong, and
 * it is deliberately built to give up often. Removing a word can make the model
 * rewrite the whole line, and a diff of two unrelated sentences is noise; so
 * the two translations have to agree everywhere except in one short run of
 * words, or nothing is reported at all. Silence is the failure mode.
 */
object ContextualGloss {

    /**
     * The run of words [whole] has and [ablated] does not, or null when the two
     * translations are too far apart to be compared.
     *
     * Aligned *locally* rather than end to end. Anchoring on both ends of the
     * sentence was the obvious way to do this and it refuses to answer the case
     * the feature exists for: taking *cap* out of "va lligar el cap de la corda
     * al pal més alt" leaves the model free to re-read the rest of the line, and
     * it does —
     *
     *     He tied the head of the rope to the highest bar.
     *     He tied the one of the rope to the tall post of the boat.
     *
     * — so the two sentences share three words at the front, nothing at the
     * back, and an end-to-end diff calls the whole tail the meaning of *cap*
     * and then throws it away for being too long. But the divergence itself is
     * as clean as it could be: *head* against *one*, in the same place, with
     * the sentence coming straight back together on *of the rope*. The
     * rewritten clause at the end has nothing to do with the missing word.
     *
     * So what is looked for is a short gap that both sentences come out of
     * together: some words matching before it, at most [MAX_SPAN_WORDS] words
     * in it, and a run of words matching again after it. What is inside that
     * gap is what the word contributed. Everything past the point where the
     * sentences come back together is ignored, which is the whole point — that
     * is where a sentence model does its rewriting.
     *
     * Silence is still the failure mode, and there is a good deal of it: the
     * anchor either side has to be real, so a line the model rewrote from its
     * first word is refused outright rather than guessed at.
     */
    fun difference(whole: String, ablated: String, language: String): String? {
        val full = whole.split(WHITESPACE).filter { it.isNotBlank() }
        val short = ablated.split(WHITESPACE).filter { it.isNotBlank() }
        if (full.size < MIN_SENTENCE_WORDS || short.size < MIN_SENTENCE_WORDS) return null

        // Read forwards, and if the sentence gives nothing that way, backwards:
        // a word at the very start of the line has no anchor in front of it and
        // all of its anchor behind. The reversed views hand back the span in
        // reversed order, so it is turned round again before it is read.
        val span = spanBetweenAnchors(full, short)
            ?: spanBetweenAnchors(full.asReversed(), short.asReversed())?.asReversed()
            ?: return null

        return trim(span, language)?.takeIf { it.isNotBlank() }
    }

    /**
     * The words of [full] sitting in a gap that [short] does not have, found by
     * matching forwards from the start of both.
     *
     * Smallest gap first, and a gap that swallows a neighbour is refused rather
     * than reported: it is better to say nothing than to tell a reader that
     * *cap* means "red one".
     */
    private fun spanBetweenAnchors(full: List<String>, short: List<String>): List<String>? {
        var prefix = 0
        val common = minOf(full.size, short.size)
        while (prefix < common && same(full[prefix], short[prefix])) prefix++

        // Nothing diverged, or it diverged immediately: with no anchor in front
        // of the gap there is nothing to say the two sentences were ever
        // describing the same thing.
        if (prefix >= full.size || prefix < MIN_ANCHOR_WORDS) return null

        for (taken in 1..MAX_SPAN_WORDS) {
            if (prefix + taken > full.size) break
            for (replaced in 0..MAX_REPLACEMENT_WORDS) {
                if (prefix + replaced > short.size) break
                if (rejoins(full, prefix + taken, short, prefix + replaced)) {
                    return full.subList(prefix, prefix + taken)
                }
            }
        }
        return null
    }

    /**
     * Whether the two sentences are saying the same thing again from [a] and
     * [b] onwards.
     *
     * [MIN_REJOIN_WORDS] of them, or all of whatever is left when one of the
     * sentences simply ends there. A shorter run than that happens by accident
     * on ordinary words — *of the* turns up everywhere — and an accident is
     * what would let a rewritten clause be read as a word's meaning.
     */
    private fun rejoins(full: List<String>, a: Int, short: List<String>, b: Int): Boolean {
        var matched = 0
        while (a + matched < full.size && b + matched < short.size &&
            same(full[a + matched], short[b + matched])
        ) {
            matched++
        }
        if (matched >= MIN_REJOIN_WORDS) return true
        // Both running out together is the sentences ending in agreement, which
        // is as good an anchor as any number of matching words.
        return a + matched == full.size && b + matched == short.size
    }

    /**
     * The line with [word] taken out of it.
     *
     * Only whole words, and only the first occurrence, so that a line repeating
     * the word does not lose both and take two meanings out at once.
     */
    fun withoutWord(line: String, word: String): String? {
        val target = word.trim()
        if (target.isEmpty() || target.contains(' ')) return null
        var removed = false
        val kept = line.split(WHITESPACE).filter { piece ->
            if (!removed && bare(piece) == bare(target)) {
                removed = true
                false
            } else {
                piece.isNotBlank()
            }
        }
        return if (removed && kept.size >= MIN_SENTENCE_WORDS) kept.joinToString(" ") else null
    }

    /**
     * Drops the grammar clinging to the edges of the span.
     *
     * A diff picks up the article and the preposition that came with the noun —
     * *a bench*, *letter from* — and those belong to the sentence rather than to
     * the word. Only the two target languages are listed, because those are the
     * two the app translates into; an unlisted language keeps its span whole,
     * which is untidy rather than wrong.
     */
    private fun trim(span: List<String>, language: String): String? {
        val edges = EDGE_WORDS[language] ?: return span.joinToString(" ")
        var from = 0
        var to = span.size
        while (from < to && bare(span[from]) in edges) from++
        while (to > from && bare(span[to - 1]) in edges) to--
        if (from >= to) return null
        return span.subList(from, to).joinToString(" ").trim { !it.isLetterOrDigit() }
    }

    private fun same(a: String, b: String) = bare(a) == bare(b)

    private fun bare(word: String) = word.lowercase().trim { !it.isLetterOrDigit() }

    private val WHITESPACE = Regex("\\s+")

    /**
     * Words that can start or end a span without being part of the meaning.
     *
     * Articles, prepositions and the copula: enough to clean up *a bench* and
     * *letter from*, and short enough that no content word is at risk.
     */
    private val EDGE_WORDS = mapOf(
        "en" to setOf(
            "the", "a", "an", "of", "to", "in", "on", "at", "for", "from",
            "with", "by", "and", "or", "is", "was", "are", "were", "be",
            "it", "its", "that", "this", "he", "she", "they", "his", "her",
        ),
        "ro" to setOf(
            "un", "o", "de", "la", "în", "in", "pe", "cu", "și", "si", "sau",
            "este", "era", "sunt", "erau", "fi", "al", "ale", "lui", "ei",
            "care", "acest", "această", "acesta", "aceasta",
        ),
    )

    /** Below this a sentence has no anchor either side of the missing word. */
    private const val MIN_SENTENCE_WORDS = 4

    /** Matching words needed in front of the gap before it is believed. */
    private const val MIN_ANCHOR_WORDS = 2

    /**
     * Matching words needed after it.
     *
     * Three rather than two: *of the* is not evidence of anything in English,
     * and neither is *de la* in Romanian.
     */
    private const val MIN_REJOIN_WORDS = 3

    /**
     * Words the ablated sentence may put in the gap's place.
     *
     * A model that loses a word usually replaces it rather than closing up —
     * *the head of the rope* becomes *the one of the rope* — and occasionally
     * takes two words to do it. Past that the sentence is being rewritten, not
     * patched.
     */
    private const val MAX_REPLACEMENT_WORDS = 2

    /**
     * Longer than this and the gap is a phrase the model rebuilt, not a word.
     *
     * Two, because a word's meaning is one word or a compound of two — *ground
     * floor* — and everything longer that turned up in testing was the diff
     * swallowing a neighbour: *cap* in "no en tinc cap de vermell" came back as
     * *red one*, which is the adjective's meaning with the word's own missing.
     */
    private const val MAX_SPAN_WORDS = 2
}
