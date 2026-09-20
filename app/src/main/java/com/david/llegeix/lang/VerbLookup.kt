package com.david.llegeix.lang

/**
 * A verb form and what the references say the verb itself means.
 *
 * [definition] is the Viccionari's own first definition of the infinitive, in
 * Catalan, and it is null when the entry has none — which is why the type does
 * not fold the two together.
 */
data class VerbEntry(val form: VerbForm, val definition: String?)

/**
 * Whether [word] is a verb form, and if so which one.
 *
 * Three questions, and all three have to answer yes. The inflected-forms table
 * has to know a base form for it — or the word has to be its own, as when
 * somebody types *cantar* outright. That base form has to look like an
 * infinitive. And the Viccionari has to file it as a verb, which is the check
 * that keeps a noun with a verb's shape from being conjugated at the reader.
 * Only then is the form itself worked out, by [CatalanVerbs].
 *
 * Lives here rather than on either screen because both ask it. A word tapped
 * on a page and the same word typed into the dictionary are the same word, and
 * two copies of this rule would eventually disagree about one of them.
 *
 * Reads the bundled reference files, so callers must be off the main thread.
 */
fun CatalanWordBank.verbEntry(word: String): VerbEntry? {
    val key = CatalanVerbs.normalise(word)
    if (key.isEmpty()) return null
    val lemma = lemmaOf(key) ?: key
    if (CatalanVerbs.conjugationOf(lemma) == null) return null
    val definition = lookup(lemma)
        ?.definitions
        ?.firstOrNull { it.partOfSpeech == VERB_PART_OF_SPEECH }
    if (definition == null && !CatalanVerbs.isKnownIrregular(lemma)) return null
    val form = CatalanVerbs.analyse(key, lemma) ?: return null
    return VerbEntry(form, definition?.meanings?.firstOrNull())
}

/** How the Viccionari labels a verb entry. */
private const val VERB_PART_OF_SPEECH = "verb"
