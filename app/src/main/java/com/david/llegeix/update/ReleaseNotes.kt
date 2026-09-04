package com.david.llegeix.update

/**
 * Release notes as they are written, turned into notes that can be read.
 *
 * They are authored as markdown, because that is what the release page renders,
 * and shown here as text, because a settings card is not a browser. Rather than
 * rendering the markdown — a library and a great deal of layout for a paragraph
 * of prose read once per release — the marks are taken off and the paragraphs
 * are put back together.
 *
 * Putting them back together is the part that matters. Notes are written
 * wrapped at about seventy characters, which is the right width for a terminal
 * and a release page and completely wrong for a phone: kept as written, every
 * line breaks a second time against the edge of the card and a paragraph comes
 * out as a ragged column of half-lines. A single newline in markdown is not a
 * break, so it is not treated as one here either. A blank line is.
 *
 * Only the marks this project actually writes are handled. Anything unrecognised
 * is left alone, on the grounds that a stray asterisk is a smaller problem than
 * a mangled sentence.
 */
internal object ReleaseNotes {

    fun plain(markdown: String): String {
        val notes = StringBuilder()
        val paragraph = StringBuilder()

        /** Close the paragraph being gathered, if there is one. */
        fun flush() {
            if (paragraph.isEmpty()) return
            if (notes.isNotEmpty()) notes.append("\n\n")
            notes.append(paragraph)
            paragraph.clear()
        }

        for (raw in markdown.replace('\r', '\n').split('\n')) {
            val line = raw.trim()
            when {
                // A blank line is the only break the author actually meant.
                line.isEmpty() -> flush()

                // A table drawn in pipes is a table, and a line of dashes is a
                // rule. Neither is a sentence, and neither survives being read
                // as one.
                line.startsWith("|") || line.isRuleOnly() -> flush()

                // A heading stands alone: it is a label for what follows, not
                // the first words of it.
                line.startsWith("#") -> {
                    flush()
                    paragraph.append(line.trimStart('#').trim().stripEmphasis())
                    flush()
                }

                // A bullet begins something new even with no blank line above
                // it; its own continuation lines then gather into it.
                line.isListItem() -> {
                    flush()
                    paragraph.append(line.stripEmphasis())
                }

                else -> {
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(line.stripEmphasis())
                }
            }
        }
        flush()
        return notes.toString()
    }

    /** A line of only dashes or underscores draws a rule and says nothing. */
    private fun String.isRuleOnly(): Boolean =
        length >= 3 && all { it == '-' || it == '_' || it == '=' }

    /** "- something", or "1. something". A dash alone is a rule, not a bullet. */
    private fun String.isListItem(): Boolean =
        startsWith("- ") || startsWith("* ") || NUMBERED.containsMatchIn(this)

    private fun String.stripEmphasis(): String = this
        .replace("**", "")
        .replace("`", "")

    private val NUMBERED = Regex("^\\d+\\. ")
}
