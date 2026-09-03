package com.david.llegeix.pdf

/** A search hit with enough of its line around it to be recognised in a list. */
internal data class Snippet(
    val text: String,
    /** Where the searched term starts inside [text]. */
    val start: Int,
    /** One past where it ends. */
    val end: Int,
)

/**
 * Cutting the line around a match down to something a reader can scan.
 *
 * A list of results is only useful if each row says which occurrence it is, and
 * "page 12" does not: a word can be on a page four times meaning four different
 * things. So each hit travels with the words either side of it, which is the
 * one thing that tells them apart.
 *
 * Pulled out of the renderer, like [PageLines], because it is index arithmetic
 * and index arithmetic is worth testing without a PDF and a device to open it
 * on. The awkward part is that the text has to be tidied — a page's extracted
 * text is full of line breaks and runs of spaces that would render as gaps — and
 * tidying it moves the very positions the caller needs to underline. So the
 * positions are tracked as the text is rebuilt rather than searched for
 * afterwards, which would find the wrong occurrence whenever the term appears in
 * the snippet twice.
 */
internal object MatchSnippet {

    /**
     * [charCount] characters from [charIndex] of [pageText], with [RADIUS]
     * characters of context either side.
     */
    fun around(pageText: String, charIndex: Int, charCount: Int, radius: Int = RADIUS): Snippet {
        if (pageText.isEmpty() || charCount <= 0) return Snippet("", 0, 0)
        val matchStart = charIndex.coerceIn(0, pageText.length)
        val matchEnd = (charIndex + charCount).coerceIn(matchStart, pageText.length)

        var from = (matchStart - radius).coerceAtLeast(0)
        var to = (matchEnd + radius).coerceAtMost(pageText.length)
        // Never open or close in the middle of a word: half a word reads as a
        // spelling mistake rather than as an abbreviation.
        if (from > 0) {
            val space = (from until matchStart).firstOrNull { pageText[it].isWhitespace() }
            if (space != null) from = space + 1
        }
        if (to < pageText.length) {
            val space = (to - 1 downTo matchEnd).firstOrNull { pageText[it].isWhitespace() }
            if (space != null) to = space
        }

        val builder = StringBuilder(to - from + 2)
        var start = -1
        var end = -1
        // Whitespace is not written out as it is met: it is remembered, and one
        // space is written before the next real character. That collapses the
        // line breaks and the double spaces in one pass and never leaves the
        // snippet starting or ending on a space.
        var pendingSpace = false
        for (index in from until to) {
            val character = pageText[index]
            if (character.isWhitespace()) {
                if (index == matchEnd && end < 0) end = builder.length
                pendingSpace = builder.isNotEmpty()
                continue
            }
            if (pendingSpace) {
                builder.append(' ')
                pendingSpace = false
            }
            if (index == matchStart && start < 0) start = builder.length
            if (index == matchEnd && end < 0) end = builder.length
            builder.append(character)
        }
        if (start < 0) start = 0
        if (end < 0) end = builder.length

        // An ellipsis only where text was actually cut off, so a short line
        // shown whole does not pretend to be an extract.
        var text = builder.toString()
        if (from > 0) {
            text = ELLIPSIS + text
            start += ELLIPSIS.length
            end += ELLIPSIS.length
        }
        // Not after a full stop, though: "mal pagada.…" is four dots in a row
        // and reads as a typographic accident. An extract that happens to end
        // where a sentence does is already saying it stopped.
        if (to < pageText.length && text.lastOrNull() !in SENTENCE_ENDS) text += ELLIPSIS

        return Snippet(
            text = text,
            start = start.coerceIn(0, text.length),
            end = end.coerceIn(start.coerceIn(0, text.length), text.length),
        )
    }

    /**
     * Characters kept either side of the match.
     *
     * Enough to fill the two lines a row is allowed and no more. A longer
     * extract is not more context, it is a paragraph in a list, and the point of
     * the list is that a glance down it is quicker than reading.
     */
    private const val RADIUS = 36

    private const val ELLIPSIS = "…"

    /** Punctuation that already says the extract stopped somewhere sensible. */
    private val SENTENCE_ENDS = setOf('.', '!', '?', '…')
}
