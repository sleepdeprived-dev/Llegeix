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
     * Matched from both ends rather than by set difference: what is wanted is
     * the place the sentences diverge, and a word that merely moved is not a
     * word that was contributed. A set difference reported *contenty tail* and
     * *bank to get the* on real output, which is how this ended up anchored.
     */
    fun difference(whole: String, ablated: String, language: String): String? {
        val full = whole.split(WHITESPACE).filter { it.isNotBlank() }
        val short = ablated.split(WHITESPACE).filter { it.isNotBlank() }
        if (full.size < MIN_SENTENCE_WORDS || short.size < MIN_SENTENCE_WORDS) return null
        // Not necessarily shorter: a model that loses a word often puts another
        // in its place rather than closing the gap, turning *tied the head of
        // the rope* into *tied the one of the rope*. What matters is that the
        // sentences diverge in one place, not that one of them got shorter.

        // Both walks are bounded by the *shorter* sentence, not by the ablated
        // one. Removing a word does not reliably shorten the translation — the
        // model re-reads the line and can come back with more words than it
        // started with — so bounding by either sentence alone walks off the end
        // of the other.
        val common = minOf(full.size, short.size)

        var prefix = 0
        while (prefix < common && same(full[prefix], short[prefix])) prefix++

        var suffix = 0
        while (
            suffix < common - prefix &&
            same(full[full.size - 1 - suffix], short[short.size - 1 - suffix])
        ) {
            suffix++
        }

        // Most of the shorter sentence has to have survived unmoved. When it
        // has not, the model rewrote the line rather than dropping a word from
        // it, and whatever sits in the gap is not the word's meaning.
        if (prefix + suffix < short.size * MIN_ANCHOR_FRACTION) return null

        val span = full.subList(prefix, full.size - suffix)
        if (span.isEmpty() || span.size > MAX_SPAN_WORDS) return null

        return trim(span, language)?.takeIf { it.isNotBlank() }
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

    /** How much of the shorter translation must line up word for word. */
    private const val MIN_ANCHOR_FRACTION = 0.6

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
