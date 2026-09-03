package com.david.llegeix.pdf

/** Where a selection sits in a page's text, and what is written around it. */
internal data class LocatedLine(
    val text: String,
    /** 1-based, counted down the page. */
    val number: Int,
    /** The line and its neighbours, joined, for weighing a word's sense. */
    val passage: String,
)

/**
 * Finding the line a character is on, and the lines either side of it.
 *
 * Pulled out of the renderer because both ways of getting words off a page want
 * it — a text layer gives one string with newlines in it, recognition gives a
 * list of lines — and because it is the sort of index arithmetic that is worth
 * being able to test without a PDF and a device to open it on.
 */
internal object PageLines {

    /**
     * The line containing [charIndex] of [pageText], and its neighbours.
     *
     * Counting newlines rather than clustering character boxes: PDFium already
     * breaks the extracted text where the page breaks lines, which is both
     * cheaper and closer to what the page looks like.
     */
    fun locate(pageText: String, charIndex: Int): LocatedLine {
        if (pageText.isEmpty()) return LocatedLine("", 1, "")
        val safeIndex = charIndex.coerceIn(0, pageText.length - 1)

        val lines = pageText.split('\n')
        var index = lines.lastIndex
        var consumed = 0
        for ((number, line) in lines.withIndex()) {
            // The newline itself belongs to the line it ends.
            consumed += line.length + 1
            if (safeIndex < consumed) {
                index = number
                break
            }
        }

        return LocatedLine(
            text = clean(lines[index]),
            number = index + 1,
            passage = passageAround(lines, index),
        )
    }

    /**
     * [index] and the lines either side of it, joined into something long
     * enough to have a subject.
     *
     * A line of a printed novel is often three or four words, which says
     * nothing about which sense of a word is in play. Two lines either way is
     * enough to make a sentence and few enough to stay inside the paragraph,
     * whose neighbour would argue for the wrong sense as readily as the right.
     */
    fun passageAround(lines: List<String>, index: Int): String {
        if (lines.isEmpty()) return ""
        val from = (index - PASSAGE_LINES).coerceAtLeast(0)
        val to = (index + PASSAGE_LINES + 1).coerceAtMost(lines.size)
        return lines.subList(from, to).joinToString(" ") { clean(it) }.trim()
    }

    fun clean(text: String): String = text.replace(LINE_BREAKS, " ").trim()

    /** PDFium reports page line breaks as CR, LF or both. */
    private val LINE_BREAKS = Regex("[\\r\\n]+")

    private const val PASSAGE_LINES = 2
}
