package com.david.llegeix.data.flashcards

/**
 * A field the app may fill in, until the reader has written in it.
 *
 * The card form offers two things it did not get from the reader: the
 * pronunciation, generated from the Catalan spelling, and a Romanian meaning,
 * from the on-device translator. Both are guesses, and both are offered the
 * same way — in the field itself, marked as the app's, and replaced as the
 * Catalan changes — right up until the reader types in the field. From then on
 * it is theirs, and nothing the app works out afterwards is allowed to write
 * over it.
 *
 * Emptying the field hands it back. A cleared field is the reader saying "not
 * that", and the next suggestion is the most useful thing to put there.
 */
data class Suggested(
    val text: String = "",
    /** Still the app's suggestion, as against something the reader wrote. */
    val isSuggestion: Boolean = false,
    /**
     * The suggestion is known to be a guess in part, like a pronunciation whose
     * stressed e or o the spelling does not settle. Only ever true of a
     * suggestion: once the reader has written the field, it is not the app's
     * guess any more.
     */
    val isApproximate: Boolean = false,
) {

    /** Whether a new suggestion may replace what is there. */
    val acceptsSuggestions: Boolean get() = isSuggestion || text.isBlank()

    /** The reader typed: the field is theirs now, whatever it says. */
    fun typed(newText: String): Suggested =
        if (newText == text) this else Suggested(newText, isSuggestion = false)

    /**
     * The app has a suggestion. Taken if the field accepts one, ignored if the
     * reader has written in it. An empty suggestion clears a field that was
     * only holding the last one — the Catalan it was made from has gone.
     */
    fun offer(suggestion: String, approximate: Boolean = false): Suggested = when {
        !acceptsSuggestions -> this
        suggestion.isEmpty() -> Suggested()
        else -> Suggested(suggestion, isSuggestion = true, isApproximate = approximate)
    }

    companion object {
        /** What the reader saved earlier: theirs, so never replaced. */
        fun owned(text: String?): Suggested = Suggested(text.orEmpty())
    }
}

/**
 * A pronunciation as the reader might type it, as the app stores it.
 *
 * Stored bare, the way the transcriber writes it, because every screen that
 * shows it adds its own brackets. Somebody copying a transcription from a
 * dictionary will bring the brackets or slashes with it.
 */
fun tidyIpa(raw: String): String {
    val trimmed = raw.trim()
    val bare = when {
        trimmed.length >= 2 && trimmed.first() == '[' && trimmed.last() == ']' ->
            trimmed.substring(1, trimmed.length - 1)

        trimmed.length >= 2 && trimmed.first() == '/' && trimmed.last() == '/' ->
            trimmed.substring(1, trimmed.length - 1)

        else -> trimmed
    }
    return bare.trim()
}

/**
 * A translation, capitalised the way the word it translates is.
 *
 * The translator writes sentences, so it hands back *Planeta* for *terra*. A
 * card should read like the word on its other side: a lower-case Catalan word
 * gets a lower-case meaning. Only the first letter is touched, and not when
 * the next one is a capital too — *UE* is an abbreviation, not a sentence.
 * A Catalan word that starts with a capital, a name, keeps whatever came back.
 */
fun matchLeadingCase(source: String, translation: String): String {
    val first = source.trimStart().firstOrNull() ?: return translation
    if (!first.isLowerCase()) return translation
    val lead = translation.firstOrNull() ?: return translation
    if (!lead.isUpperCase()) return translation
    if (translation.getOrNull(1)?.isUpperCase() == true) return translation
    return lead.lowercase() + translation.substring(1)
}
