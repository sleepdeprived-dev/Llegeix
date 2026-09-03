package com.david.llegeix.lang

/**
 * Reading a word by the company it keeps.
 *
 * Three separate jobs live here, and they are deliberately different in how far
 * they are trusted:
 *
 *  - [phraseAround] finds a listed multiword expression. It is a string match
 *    against the reference files, so it is either right or absent, and its
 *    answer is good enough to translate instead of the word.
 *  - [posAround] reads the part of speech off the neighbouring function words.
 *    Catalan's determiners, auxiliaries and weak pronouns are a small closed
 *    set, and what follows one of them is grammar rather than guesswork.
 *  - [rankSenses] guesses which *meaning* is in play by overlap with the
 *    surrounding text. This one is a guess, it is wrong often enough to matter,
 *    and nothing that changes an answer is allowed to depend on it — see the
 *    note on the sense inventory in [rankSenses].
 *
 * All of it is plain string work over the bundled files. Nothing here calls
 * anything, costs anything, or leaves the device.
 */
object CatalanContext {

    /** Splits a line into words, keeping the apostrophes and the interpunct. */
    private val SPLIT = Regex("[^\\p{L}\\p{N}'·’]+")

    /**
     * The words of [text], normalised the way the reference files spell them.
     *
     * Empty pieces are dropped but nothing else is: the caller needs the
     * positions to line up with what was tapped.
     */
    fun tokenise(text: String): List<String> = SPLIT.split(text)
        .map { CatalanWordBank.normalise(it) }
        .filter { it.isNotEmpty() }

    // ---- Multiword expressions -------------------------------------------

    /**
     * The listed expression covering the word at [index], or null.
     *
     * Longest first, and both directions: *base de dades* has to be found from
     * a tap on any of its three words, because the reader taps the word they
     * did not recognise, which is rarely the first one.
     *
     * This is the one contextual reading trusted enough to translate in place
     * of the word itself. A phrase either is in the dictionary or is not, so
     * unlike a sense guess it cannot be confidently wrong — at worst it finds
     * nothing.
     */
    fun phraseAround(tokens: List<String>, index: Int, isListed: (String) -> Boolean): String? {
        if (index !in tokens.indices) return null
        for (length in MAX_PHRASE_WORDS downTo 2) {
            // Every window of this length that still covers the tapped word.
            val firstStart = (index - length + 1).coerceAtLeast(0)
            val lastStart = index.coerceAtMost(tokens.size - length)
            for (start in firstStart..lastStart) {
                val phrase = tokens.subList(start, start + length).joinToString(" ")
                if (isListed(phrase)) return phrase
            }
        }
        return null
    }

    // ---- Part of speech from the neighbours -------------------------------

    /**
     * What the words on either side say the tapped word must be.
     *
     * An empty set means "no opinion", which is the common case and the honest
     * one: most words are not next to a determiner or an auxiliary.
     *
     * Only the unambiguous cues are listed. *la* is a determiner but also a
     * weak pronoun, so *la casa* and *la veig* want opposite answers; it is
     * therefore absent rather than guessed at.
     */
    fun posAround(before: String?, after: String?): Set<String> {
        val previous = before?.let { CatalanWordBank.normalise(it) }
        val next = after?.let { CatalanWordBank.normalise(it) }

        // An auxiliary in front means a participle, and that beats everything
        // else: "ha cantat" is a verb whatever else "cantat" could be.
        if (previous in HAVER) return setOf(VERB)
        if (previous in ANAR_PAST) return setOf(VERB)
        // A weak pronoun in front of a word makes it the verb it hangs off.
        if (previous in WEAK_PRONOUNS) return setOf(VERB)
        // A determiner makes a noun, or the adjective standing in for one.
        if (previous in DETERMINERS) return setOf(NOUN, ADJECTIVE)
        // A degree adverb qualifies an adjective or another adverb.
        if (previous in DEGREE) return setOf(ADJECTIVE, ADVERB)
        // A following infinitive marker makes what came before an adverb or a
        // verb rather than a noun: "després de menjar".
        if (next in INFINITIVE_MARKERS) return setOf(VERB, ADVERB)
        return emptySet()
    }

    /** Dictionary part-of-speech codes, which the thesaurus abbreviates. */
    const val NOUN = "nom"
    const val VERB = "verb"
    const val ADJECTIVE = "adj"
    const val ADVERB = "adv"

    /**
     * The two files label parts of speech differently — *nom* against *n* —
     * so comparisons go through here rather than through either spelling.
     */
    fun normalisePos(code: String): String = when (code.substringBefore(';').trim()) {
        "n", "nom" -> NOUN
        "v", "verb" -> VERB
        "adj", "adj/n" -> ADJECTIVE
        "adv" -> ADVERB
        "ij", "interj" -> "interj"
        else -> code.substringBefore(';').trim()
    }

    // ---- Which sense is in play -------------------------------------------

    /**
     * One candidate meaning, and the words that would suggest it.
     *
     * Two bags, not one, and the difference matters more than it looks.
     * [terms] is what the sense is actually made of — its own synonyms — and a
     * context word found there is strong evidence. [related] is what those
     * words are *defined* with, which is how a line mentioning *peix* can be
     * matched to a sense listing *peixada*, but it is weak evidence and has to
     * be weighted as such.
     *
     * Weighting them equally does not merely blur the answer, it inverts it:
     * a sense with eight synonyms pulls in eight definitions' worth of ordinary
     * words and outscores a sense with three on sheer surface area. Looking up
     * *banc* against a line about *un banc de peixos* ranked the bank branch
     * above the shoal of fish, on a page where *peixos* is literally one of the
     * shoal's synonyms. Hence the two tiers.
     *
     * The bags are built by the caller, which keeps this file free of the
     * reference files and testable without them.
     */
    data class Candidate<T>(
        val value: T,
        val partOfSpeech: String,
        val terms: Set<String>,
        val related: Set<String> = emptySet(),
    )

    /** A scored candidate, and the context words that argued for it. */
    data class Ranked<T>(val value: T, val score: Double, val support: List<String>)

    /**
     * Order [candidates] by how well each fits [context], best first.
     *
     * A context word that appears in one sense's description and not the others
     * is strong evidence; one that appears in all of them is none at all, so
     * each match is worth one point divided by the number of candidates it
     * touches. Without that, the vaguest sense — the one whose definition uses
     * the most ordinary words — would win every time.
     *
     * **This ranking never changes a translation, only the order things are
     * shown in.** The reason is the shape of the sources rather than the
     * quality of the scoring: a synonym dictionary only lists a sense that
     * *has* synonyms, and a word's plainest meaning usually has none. *Banc*
     * has no bench, *clau* no key, *cua* no queue. Picking the best of the
     * listed senses would therefore answer confidently with a sense the word
     * does not have here, and it would do it on exactly the everyday words a
     * learner taps. Reordering a list the reader can still read past is a
     * proportionate use of a guess this good; replacing the answer is not.
     *
     * That applies to [allowedPos] too, which weights rather than filters.
     * Dropping the senses the neighbouring words rule out is tempting and wrong
     * for the same reason: the grammar reading is itself a heuristic, and a
     * heuristic that can delete the right answer takes away the reader's
     * ability to overrule it. Every candidate comes back, reordered.
     *
     * [support] keeps the ordering of [context], so a caller iterating an
     * insertion-ordered set gets the matched words in the order they appear on
     * the page rather than in whatever order a hash bucket produced.
     */
    fun <T> rankSenses(
        candidates: List<Candidate<T>>,
        context: Set<String>,
        allowedPos: Set<String> = emptySet(),
    ): List<Ranked<T>> {
        if (candidates.isEmpty()) return emptyList()

        return candidates.map { candidate ->
            var score = 0.0
            val support = mutableListOf<String>()
            for (word in context) {
                val weight = when (word) {
                    in candidate.terms -> 1.0
                    in candidate.related -> RELATED_WEIGHT
                    else -> continue
                }
                val shared = candidates.count { word in it.terms || word in it.related }
                score += weight / shared
                support += word
            }
            // A sense the grammar allows starts a nose ahead of one it rules
            // out, so "el cap" prefers the noun over the determiner before any
            // word has been matched — while a single word of real evidence
            // still outweighs it, which is the right way round.
            if (candidate.partOfSpeech in allowedPos) score += GRAMMAR_BONUS
            Ranked(candidate.value, score, support)
        }.sortedByDescending { it.score }
    }

    /**
     * Whether the best of [ranked] is worth pointing at rather than merely
     * listing first.
     *
     * Ahead of the runner-up by a clear margin, and on real evidence rather
     * than on the grammar bonus alone.
     */
    fun <T> isClear(ranked: List<Ranked<T>>): Boolean {
        val best = ranked.firstOrNull() ?: return false
        if (best.support.isEmpty() || best.score < MIN_SCORE) return false
        val second = ranked.getOrNull(1) ?: return true
        return second.score == 0.0 || best.score >= second.score * MARGIN
    }

    /** Words too common to tell two senses apart. */
    private val STOPWORDS = setOf(
        "el", "la", "els", "les", "un", "una", "uns", "unes", "lo",
        "de", "del", "dels", "al", "als", "pel", "pels", "amb", "per",
        "sense", "sobre", "sota", "fins", "entre", "contra", "segons",
        "durant", "des", "cap", "vers", "envers", "dins", "fora",
        "i", "o", "però", "que", "què", "si", "com", "quan", "doncs",
        "perquè", "mentre", "ni", "sinó", "també", "però",
        "es", "se", "em", "et", "ens", "us", "li", "hi", "ho", "me", "te",
        "jo", "tu", "ell", "ella", "nosaltres", "vosaltres", "ells", "elles",
        "meu", "teu", "seu", "meva", "teva", "seva", "seus", "seves",
        "aquest", "aquesta", "aquell", "aquella", "aquests", "aquestes",
        "això", "allò", "aquí", "allà",
        "ser", "és", "són", "era", "eren", "estar", "està", "estan",
        "haver", "he", "has", "ha", "hem", "heu", "han", "havia", "havien",
        "va", "van", "vaig", "vas", "vam", "vau", "fer", "fa", "fet", "fan",
        "molt", "molta", "molts", "moltes", "més", "menys", "tan", "tant",
        "no", "ja", "tot", "tots", "tota", "totes", "cada", "qual", "quals",
        "on", "qui", "altre", "altra", "altres", "mateix", "mateixa",
        // Words the definitions themselves lean on, which would otherwise
        // match every sense of everything.
        "manera", "part", "cosa", "coses", "forma", "acció", "acte",
        "conjunt", "persona", "quelcom", "algú", "dit", "usa", "diu",
        "serveix", "propi", "pròpia", "relatiu", "relativa", "qualitat",
    )

    /**
     * The words of [text] worth matching on: long enough to mean something,
     * and not on the stop list.
     */
    fun contentWords(text: String): List<String> = tokenise(text)
        .filter { it.length > 2 && it !in STOPWORDS }

    private val DETERMINERS = setOf(
        "el", "els", "un", "una", "uns", "unes", "lo", "los",
        "aquest", "aquesta", "aquests", "aquestes",
        "aquell", "aquella", "aquells", "aquelles",
        "meu", "teu", "seu", "meva", "teva", "seva", "nostre", "vostre",
        "algun", "alguna", "alguns", "algunes", "cada", "qualsevol",
        "del", "dels", "al", "als", "pel", "pels",
    )

    /** The auxiliary *haver*, which makes what follows a participle. */
    private val HAVER = setOf("he", "has", "ha", "hem", "heu", "han", "havia", "havien", "hagut")

    /** Periphrastic past: *va cantar* is a verb, not a noun. */
    private val ANAR_PAST = setOf("vaig", "vas", "va", "vam", "vau", "van")

    /**
     * Weak pronouns, which make what follows them the verb they hang off.
     *
     * *en* is absent for the reason *la* is: it is a pronoun in *en tinc* and a
     * preposition in *en aquest cas*, and the two want opposite answers. *es*
     * stays, because the noun spelled that way — a bend in a river — is rare
     * enough next to the reflexive that the cue is worth having.
     */
    private val WEAK_PRONOUNS = setOf("em", "et", "es", "ens", "us", "li", "hi", "ho", "se", "me", "te")

    private val DEGREE = setOf("molt", "molta", "molts", "moltes", "més", "menys", "tan", "tant", "força", "gens", "massa")

    private val INFINITIVE_MARKERS = setOf("de", "a", "per")

    /** Words in an expression, past which a "phrase" is really a sentence. */
    private const val MAX_PHRASE_WORDS = 4

    /** Weight of the grammar agreeing, worth less than one matched word. */
    private const val GRAMMAR_BONUS = 0.3

    /**
     * What a word found in a synonym's definition is worth, against one found
     * in the sense itself.
     *
     * Low enough that several of these together still lose to a single direct
     * hit, because that is the ranking the sources support: sharing a word with
     * a sense means something, sharing one with the prose that defines it is
     * mostly a fact about ordinary Catalan.
     */
    private const val RELATED_WEIGHT = 0.3

    /**
     * Evidence below this is one stray word and proves nothing.
     *
     * Set just under a single direct hit, so one shared synonym can mark a
     * sense but no amount of definition prose alone ever will.
     */
    private const val MIN_SCORE = 0.7

    /** How far ahead the winner has to be to count as a winner. */
    private const val MARGIN = 1.5
}
