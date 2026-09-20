package com.david.llegeix.lang

import android.content.Context

/** A definition, with the part of speech the dictionary filed it under. */
data class Definition(
    /** `nom`, `verb`, `adj`, `adv`, `interj` or `loc`, as the dictionary labels it. */
    val partOfSpeech: String,
    val meanings: List<String>,
)

/** One sense of a word: the synonyms that share a meaning. */
data class SynonymSense(
    val partOfSpeech: String,
    val words: List<String>,
)

/** Everything the bundled references know about one word. */
data class WordReference(
    /** The form the references are filed under, which may be the base form. */
    val headword: String,
    val definitions: List<Definition>,
    val senses: List<SynonymSense>,
    val antonyms: List<String>,
    /**
     * True when the surrounding text singled out the sense now listed first.
     *
     * Only ever a hint to the reader. Nothing translated depends on it.
     */
    val isLeadingSenseLikely: Boolean = false,
    /** The words in the surrounding text that pointed at that sense. */
    val contextSupport: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = definitions.isEmpty() && senses.isEmpty() && antonyms.isEmpty()
}

/**
 * What the words around a word add to it.
 *
 * The two halves are trusted very differently. [phrase] is a listed expression
 * matched by name, so it is safe to translate in place of the word. [reference]
 * is the same entry the dictionary would have shown, with its senses put in the
 * order the surroundings suggest — a hint about what to read first, never an
 * answer.
 */
data class ContextReading(
    val phrase: String? = null,
    val reference: WordReference? = null,
)

/**
 * The Catalan reference shelf: what a word means, and what else it could be.
 *
 * Three files, all on the device and all read only when something is actually
 * looked up:
 *
 *  - **Definitions** from the Viccionari, the Catalan Wiktionary, CC BY-SA.
 *    Definitions in Catalan, which is the point: someone reading Catalan to
 *    learn it is better served by a Catalan definition than by another
 *    translation.
 *  - **Synonyms and antonyms** from the *Diccionari de sinònims de català* by
 *    Jaume Ortolà and Softcatalà, CC BY 4.0.
 *  - **Inflected forms**, also from the Viccionari, which record the lemma
 *    outright — *boniques* is filed as a form of *bonic* — so a reader who taps
 *    a plural or a conjugated verb gets an answer instead of a shrug. Endings
 *    are only stripped by rule when this file has nothing, because a rule can
 *    be confidently wrong and a recorded lemma cannot.
 *
 * Note on what is *not* here: the DEIEC, the Institut d'Estudis Catalans'
 * dictionary, would be the authoritative choice, but it is published under
 * CC BY-NC-ND — no derivative works and no commercial use — so it cannot be
 * converted into an app asset or shipped in a store listing.
 */
class CatalanWordBank private constructor(
    private val definitions: SortedTsv,
    private val thesaurus: SortedTsv,
    private val lemmas: SortedTsv,
) {

    /**
     * What the references have for [word], or null when they have nothing.
     *
     * The written form is tried first, then the recorded lemma, then the
     * rule-stripped base forms. Whichever answers first names the entry, so the
     * screen can say *bonic* when the reader tapped *boniques*.
     */
    fun lookup(word: String): WordReference? {
        val key = normalise(word)
        if (key.isEmpty()) return null
        for (candidate in candidates(key)) {
            val reference = assemble(candidate)
            if (reference != null && !reference.isEmpty) return reference
        }
        return null
    }

    /**
     * The base form the inflected-forms table records for [word], or null when
     * it has none.
     *
     * A recorded fact rather than a rule: the file says outright that *vaig*
     * is a form of *anar*, which no amount of ending-stripping would ever work
     * out. The dictionary asks this before deciding whether a word is worth
     * explaining as a verb form.
     *
     * An entry can name more than one base form — *soc* is filed against both
     * *ésser* and *ser* — and the wiki markup they are written in is stripped
     * here so that callers only ever see words.
     */
    fun lemmaOf(word: String): String? {
        val key = normalise(word)
        if (key.isEmpty()) return null
        val recorded = lemmas.find(key)?.getOrNull(1) ?: return null
        return recorded.split(LEMMA_SEPARATOR)
            .map { it.replace("[[", "").replace("]]", "").trim() }
            .firstOrNull { it.isNotEmpty() && it != key }
    }

    /**
     * Headwords beginning with [prefix], for the dictionary screen's list of
     * what the reader might mean.
     *
     * Both files are asked, because they do not hold the same words: the
     * Viccionari has definitions the thesaurus has never heard of and the other
     * way round, and a word missing from a suggestion list reads as a word the
     * app does not know.
     *
     * Shortest first, then alphabetical. Typing *cas* should offer *cas* before
     * *casament*: the word itself is what was typed, and a list that buries it
     * under its own derivations is a list you have to read rather than aim at.
     */
    fun suggest(prefix: String, limit: Int = MAX_SUGGESTIONS): List<String> {
        val key = normalise(prefix)
        if (key.isEmpty()) return emptyList()
        val found = LinkedHashSet<String>(limit * 2)
        found += definitions.keysStartingWith(key, limit)
        found += thesaurus.keysStartingWith(key, limit)
        return found.sortedWith(compareBy({ it.length }, { it })).take(limit)
    }

    /**
     * What [line] adds to the word at [index] of its tokens.
     *
     * Two questions, asked in the order of how much their answers can be
     * trusted: whether the word is part of a listed expression, and which of
     * its senses the surrounding words suggest.
     *
     * [passage] is the wider text to weigh senses against — the neighbouring
     * lines as well as this one, since a single line of a book is often four
     * words long and a sense needs more than that to show itself.
     */
    fun readInContext(tokens: List<String>, index: Int, passage: String): ContextReading {
        val word = tokens.getOrNull(index) ?: return ContextReading()
        val allowed = CatalanContext.posAround(
            tokens.getOrNull(index - 1),
            tokens.getOrNull(index + 1),
        )
        return ContextReading(
            phrase = phraseIn(tokens, index),
            reference = lookup(word)?.let { inContext(it, word, passage, allowed) },
        )
    }

    /**
     * Just the listed expression the word at [index] belongs to.
     *
     * Separate from [readInContext] because the reader's sheet wants only this,
     * on every lookup, and the sense ranking behind the full reading is not
     * free: it expands each synonym through its own definition, which is a good
     * deal of work to do and throw away when all that was asked was whether
     * these three words are in the dictionary.
     */
    fun phraseIn(tokens: List<String>, index: Int): String? =
        CatalanContext.phraseAround(tokens, index) { candidate ->
            definitions.find(candidate) != null || thesaurus.find(candidate) != null
        }

    /**
     * The same entry, with the sense the surroundings favour brought to the
     * front.
     *
     * The card only has room for two synonym groups and two meanings, so which
     * ones those are is the whole question: a reader looking up *baixada* on a
     * mountain should not have to scroll past the medical sense to reach the
     * slope.
     */
    private fun inContext(
        reference: WordReference,
        word: String,
        passage: String,
        allowedPos: Set<String>,
    ): WordReference {
        // Both spellings are dropped, not just the one the entry is filed
        // under: a reader who taps *cues* is answered by *cua*, and leaving
        // *cues* in the passage would let the word vote for its own sense.
        val context = CatalanContext.contentWords(passage)
            .filterNot { it == reference.headword || it == word }
            .toSet()
        if (context.isEmpty()) return reference

        val ranked = CatalanContext.rankSenses(
            candidates = reference.senses.map { sense ->
                val words = sense.words.flatMap { CatalanContext.contentWords(it) }.toSet()
                CatalanContext.Candidate(
                    value = sense,
                    partOfSpeech = CatalanContext.normalisePos(sense.partOfSpeech),
                    terms = words,
                    related = describe(words) - words,
                )
            },
            context = context,
            allowedPos = allowedPos,
        )

        // Definitions are ranked on their own text: unlike the synonym groups
        // they carry no words but their own, so there is nothing to expand.
        val definitions = reference.definitions.map { definition ->
            val meanings = CatalanContext.rankSenses(
                candidates = definition.meanings.map { meaning ->
                    CatalanContext.Candidate(
                        value = meaning,
                        partOfSpeech = CatalanContext.normalisePos(definition.partOfSpeech),
                        terms = CatalanContext.contentWords(meaning).toSet(),
                    )
                },
                context = context,
                allowedPos = allowedPos,
            )
            definition.copy(meanings = meanings.map { it.value })
        }.sortedByDescending {
            if (CatalanContext.normalisePos(it.partOfSpeech) in allowedPos) 1 else 0
        }

        return reference.copy(
            definitions = definitions,
            senses = ranked.map { it.value },
            isLeadingSenseLikely = CatalanContext.isClear(ranked),
            contextSupport = ranked.firstOrNull()?.support.orEmpty(),
        )
    }

    /**
     * The words the dictionary uses to define each of [synonyms].
     *
     * The expansion is what makes the overlap work at all. A synonym list is
     * four or five words and a line of a book rarely contains any of them, but
     * *peixada* is defined with *peix* in it, and that the line does contain.
     *
     * Kept apart from the synonyms themselves and scored lower, because this is
     * ordinary prose: a long sense drags in a great deal of it, and treating it
     * as equal evidence lets surface area decide which meaning wins.
     */
    private fun describe(synonyms: Set<String>): Set<String> {
        val terms = HashSet<String>(64)
        for (word in synonyms) {
            definitions.find(word)?.drop(1)?.forEach { field ->
                val colon = field.indexOf(':')
                if (colon > 0) {
                    terms += CatalanContext.contentWords(
                        field.substring(colon + 1).replace(UNIT_SEPARATOR, ' '),
                    )
                }
            }
        }
        return terms
    }

    /** The forms to try, in order of how much they can be trusted. */
    private fun candidates(key: String): List<String> {
        val tried = LinkedHashSet<String>()
        tried += key
        lemmas.find(key)?.getOrNull(1)?.let { tried += it }
        tried += baseForms(key)
        return tried.toList()
    }

    private fun assemble(key: String): WordReference? {
        val defined = definitions.find(key)
        val synonyms = thesaurus.find(key)
        if (defined == null && synonyms == null) return null

        val meanings = defined.orEmpty().drop(1).mapNotNull { field ->
            val colon = field.indexOf(':')
            if (colon <= 0) return@mapNotNull null
            val texts = field.substring(colon + 1).split(UNIT_SEPARATOR)
                .filter { it.isNotBlank() }
            if (texts.isEmpty()) null else Definition(field.substring(0, colon), texts)
        }

        val senses = mutableListOf<SynonymSense>()
        val antonyms = mutableListOf<String>()
        for (field in synonyms.orEmpty().drop(1)) {
            if (field.startsWith('!')) {
                antonyms += field.drop(1).split('|').filter { it.isNotBlank() }
            } else {
                val colon = field.indexOf(':')
                if (colon <= 0) continue
                val words = field.substring(colon + 1).split('|').filter { it.isNotBlank() }
                if (words.isNotEmpty()) {
                    senses += SynonymSense(field.substring(0, colon), words)
                }
            }
        }
        return WordReference(key, meanings, senses, antonyms)
    }

    companion object {
        private const val DEFINITIONS_ASSET = "catalan-dictionary.tsv"
        private const val THESAURUS_ASSET = "catalan-thesaurus.tsv"
        private const val LEMMAS_ASSET = "catalan-lemmas.tsv"

        /** Suggestions offered while typing: a list you scroll is not a shortcut. */
        const val MAX_SUGGESTIONS = 12

        /** Separates the meanings inside one definition field. */
        private const val UNIT_SEPARATOR = '\u001F'

        /**
         * Splits an entry that records more than one base form.
         *
         * The inflected-forms file keeps them as wiki links joined by slashes
         * — `[[ésser]]/[[ser]]` — because that is how the Viccionari writes
         * them.
         */
        private val LEMMA_SEPARATOR = Regex("[/,]")

        /**
         * How a written form is walked back to a listed one when the recorded
         * lemma is missing.
         *
         * Each rule strips an ending and offers the endings that could replace
         * it, best guess first. The replacement matters: *cases* is the plural
         * of *casa*, not of *cas*, and answering with *cas* would be worse than
         * answering with nothing.
         */
        private val RULES: List<Pair<String, List<String>>> = listOf(
            "aven" to listOf("ar"),
            "aves" to listOf("ar"),
            "àvem" to listOf("ar"),
            "àveu" to listOf("ar"),
            "aria" to listOf("ar"),
            "arem" to listOf("ar"),
            "aran" to listOf("ar"),
            "ava" to listOf("ar"),
            "ant" to listOf("ar"),
            "ada" to listOf("at", "ar"),
            "ades" to listOf("at", "ar"),
            "ats" to listOf("at", "ar"),
            "es" to listOf("a", "e", ""),
            "os" to listOf("", "o"),
            "ns" to listOf("", "n"),
            "s" to listOf(""),
            // Feminine adjectives, listed in the masculine: bonica -> bonic.
            "a" to listOf(""),
        ).sortedByDescending { it.first.length }

        /**
         * Words the references should not be asked about.
         *
         * Catalan's function words collide with real entries — *es* is both the
         * pronoun and a noun meaning a bend in a river — so looking them up
         * answers a question nobody asked, and does it confidently. A reader
         * learning the language is exactly the person least able to notice.
         */
        private val FUNCTION_WORDS = setOf(
            "el", "la", "els", "les", "l", "un", "una", "uns", "unes",
            "l'", "d'", "s'", "m'", "t'", "n'",
            "a", "de", "d", "en", "amb", "per", "sense", "sobre", "sota",
            "fins", "cap", "entre", "contra", "segons", "durant", "des",
            "i", "o", "però", "que", "què", "si", "com", "quan", "doncs",
            "perquè", "mentre", "ni", "sinó",
            "es", "se", "s", "em", "et", "ens", "us", "li", "hi", "ho",
            "me", "te", "jo", "tu", "ell", "ella", "nosaltres",
            "vosaltres", "ells", "elles", "meu", "teu", "seu", "meva",
            "teva", "seva", "aquest", "aquesta", "aquell", "aquella",
            "és", "són",
        )

        @Volatile
        private var instance: CatalanWordBank? = null

        /**
         * The shared reference shelf, read on the calling thread the first time.
         *
         * Sixteen megabytes of file reading, so callers must be off the main
         * thread; every later call is a field read.
         */
        fun get(context: Context): CatalanWordBank =
            instance ?: synchronized(this) {
                instance ?: CatalanWordBank(
                    definitions = SortedTsv.load(context, DEFINITIONS_ASSET),
                    thesaurus = SortedTsv.load(context, THESAURUS_ASSET),
                    lemmas = SortedTsv.load(context, LEMMAS_ASSET),
                ).also { instance = it }
            }

        /**
         * True when [word] is worth looking up at all.
         *
         * Asked before a lookup rather than inside it, so the references stay
         * plain references and the judgement about what is useful to show sits
         * with the screen showing it.
         */
        fun isWorthLookingUp(word: String): Boolean {
            val key = normalise(word)
            return key.length > 1 && key !in FUNCTION_WORDS
        }

        /**
         * The references' own spelling of a selected word.
         *
         * PDFs are full of typographic apostrophes and stray punctuation that
         * the files, written in plain ASCII quoting, do not use.
         */
        internal fun normalise(word: String): String = word
            .lowercase()
            .replace('’', '\'')
            .replace('ʼ', '\'')
            .trim { it.isWhitespace() || (!it.isLetterOrDigit() && it != '\'' && it != '·') }

        /** Plausible base forms of [key], longest ending stripped first. */
        internal fun baseForms(key: String): List<String> {
            val forms = mutableListOf<String>()
            for ((suffix, replacements) in RULES) {
                if (key.length <= suffix.length + 2 || !key.endsWith(suffix)) continue
                val stem = key.dropLast(suffix.length)
                for (replacement in replacements) forms += stem + replacement
            }
            return forms.distinct()
        }

        /** Test seam: build a bank from lines instead of from the assets. */
        internal fun of(
            definitions: List<String> = emptyList(),
            thesaurus: List<String> = emptyList(),
            lemmas: List<String> = emptyList(),
        ): CatalanWordBank = CatalanWordBank(
            SortedTsv.of(definitions),
            SortedTsv.of(thesaurus),
            SortedTsv.of(lemmas),
        )
    }
}
