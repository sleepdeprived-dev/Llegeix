package com.david.llegeix.lang

import java.text.Normalizer
import java.util.Locale

/** Which family a verb belongs to, which is decided by how its infinitive ends. */
enum class Conjugation { FIRST, SECOND, THIRD }

/** The three moods a finite form can be in. */
enum class Mood { INDICATIVE, SUBJUNCTIVE, IMPERATIVE }

/** The tenses this app tells apart. The imperative has none, so it carries null. */
enum class Tense { PRESENT, IMPERFECT, PAST, FUTURE, CONDITIONAL }

enum class Person { S1, S2, S3, P1, P2, P3 }

/** The three forms with no person in them. */
enum class NonFiniteForm { INFINITIVE, GERUND, PARTICIPLE }

/** One thing a written form could be. A form is often several. */
sealed interface VerbReading {
    data class Finite(val mood: Mood, val tense: Tense?, val person: Person) : VerbReading
    data class NonFinite(val form: NonFiniteForm) : VerbReading
}

/**
 * What a written verb form turns out to be.
 *
 * [readings] can be empty: an irregular form this file has never heard of is
 * still worth saying *is a form of* its infinitive, and saying only that is
 * much better than guessing at a tense. Nothing here ever reports a reading it
 * is not reasonably sure of.
 */
data class VerbForm(
    /** As written, normalised. */
    val form: String,
    val infinitive: String,
    val conjugation: Conjugation,
    val readings: List<VerbReading>,
) {
    /** True when the reader typed the infinitive itself. */
    val isInfinitive: Boolean
        get() = readings.any { it is VerbReading.NonFinite && it.form == NonFiniteForm.INFINITIVE }
}

/**
 * Catalan verb forms, read backwards.
 *
 * ### What this is for
 *
 * A learner meeting *cantéssim* in a book and typing it into the dictionary
 * was answered with a translation and, if the inflected-forms file happened to
 * know it, a definition filed under *cantar*. What they were not told is the
 * thing they actually needed: that this is a verb, that it is *cantar*, and
 * that it is the imperfect subjunctive, first person plural. That is the
 * difference between looking a word up and learning the language.
 *
 * ### How it works
 *
 * The infinitive is not guessed here — it comes from the bundled table of
 * inflected forms, which records it outright, irregulars included. What this
 * file does is work out *which* form of that infinitive the written word is,
 * and it tries three things in order of how much each can be trusted.
 *
 * 1. **The irregular paradigms below.** Six verbs are spelled out in full:
 *    *ser*, *estar*, *haver*, *anar*, *fer* and *tenir*. They are the ones a
 *    learner meets on every page, they are the ones no rule describes, and
 *    they are the ones where a rule confidently gives the wrong answer — *és*
 *    ends like an imperfect subjunctive and is the present indicative of *to
 *    be*.
 *
 * 2. **The regular paradigm, generated from the infinitive.** Compared with
 *    accents removed on both sides, because Catalan moves its stress around a
 *    paradigm without changing anything else: *córrer* makes *corria*, which is
 *    the generated *córria* with the accent where it belongs.
 *
 * 3. **The endings alone**, longest first, ignoring what the stem is. This is
 *    what catches the very many verbs that are regular except for a spelling
 *    change in the stem — *menjar* making *menges*, not *menjes* — since the
 *    ending is untouched by any of that.
 *
 * ### What it will not do
 *
 * Only the first two steps can tell *corria* (imperfect) from *correria*
 * (conditional), because by the endings alone the first looks like the second
 * of a verb whose stem ends in *-r*. That is exactly why the generated
 * paradigm is tried before the endings and not after.
 */
object CatalanVerbs {

    /**
     * What [form] is, as a form of [infinitive], or null when [infinitive] is
     * not an infinitive at all.
     *
     * The caller supplies the infinitive because the app already knows it: the
     * inflected-forms table records the lemma of every form it lists, which is
     * a recorded fact and not something a rule should be asked to reproduce.
     */
    fun analyse(form: String, infinitive: String): VerbForm? {
        val written = normalise(form)
        val lemma = normalise(infinitive)
        val conjugation = conjugationOf(lemma) ?: return null
        if (written.isEmpty()) return null

        val readings = irregularReadings(written, lemma)
            ?: generatedReadings(written, lemma, conjugation)
            ?: endingReadings(written, conjugation)
        return VerbForm(written, lemma, conjugation, readings.sortedWith(ReadingOrder))
    }

    /**
     * True for one of the six verbs spelled out in full here.
     *
     * The dictionary asks before deciding whether a word is a verb at all: it
     * normally takes the Viccionari's word for that, and these six are worth
     * knowing about even in the unlikely event the entry is missing.
     */
    fun isKnownIrregular(infinitive: String): Boolean = normalise(infinitive) in Irregulars

    /** Whether [word] looks like an infinitive, and which family it is in. */
    fun conjugationOf(word: String): Conjugation? {
        val lemma = normalise(word)
        if (lemma.length < 3) return null
        return when {
            lemma.endsWith("ar") -> Conjugation.FIRST
            lemma.endsWith("re") || lemma.endsWith("er") -> Conjugation.SECOND
            lemma.endsWith("ir") -> Conjugation.THIRD
            else -> null
        }
    }

    // ---- Step one: the verbs no rule describes -----------------------------

    private fun irregularReadings(form: String, lemma: String): List<VerbReading>? {
        val paradigm = Irregulars[lemma] ?: return null
        return paradigm[deaccent(form)] ?: emptyList()
    }

    // ---- Step two: the paradigm this infinitive would have if it were regular

    private fun generatedReadings(
        form: String,
        lemma: String,
        conjugation: Conjugation,
    ): List<VerbReading>? {
        val paradigm = buildParadigm(lemma, conjugation)
        // Spelled exactly as generated first, and only then with the accents
        // off. The two really do differ: the future of a *-re* verb is the
        // infinitive with an accent on the end — *beure*, *beuré* — so
        // ignoring accents from the start would read every one of those
        // futures as an infinitive.
        val found = paradigm.exact[form] ?: paradigm.loose[deaccent(form)]
        return found?.takeIf { it.isNotEmpty() }
    }

    /**
     * Every form the regular rules make from [lemma], indexed twice: as
     * spelled, and with the accents taken off.
     *
     * Built fresh per lookup rather than cached: it is a few dozen strings, and
     * a dictionary lookup happens when a finger stops moving.
     */
    private class Paradigm(
        val exact: MutableMap<String, MutableList<VerbReading>> = HashMap(96),
        val loose: MutableMap<String, MutableList<VerbReading>> = HashMap(96),
    )

    private fun buildParadigm(lemma: String, conjugation: Conjugation): Paradigm {
        val out = Paradigm()
        fun put(word: String, reading: VerbReading) {
            if (word.isEmpty()) return
            for ((map, key) in listOf(out.exact to word, out.loose to deaccent(word))) {
                if (key.isEmpty()) continue
                val bucket = map.getOrPut(key) { mutableListOf() }
                if (reading !in bucket) bucket += reading
            }
        }
        fun six(stem: String, endings: List<String>, mood: Mood, tense: Tense?) {
            endings.forEachIndexed { index, ending ->
                put(stem + ending, VerbReading.Finite(mood, tense, Person.entries[index]))
            }
        }
        fun imperative(stem: String, endings: List<String>) {
            val people = listOf(Person.S2, Person.S3, Person.P1, Person.P2, Person.P3)
            endings.forEachIndexed { index, ending ->
                put(stem + ending, VerbReading.Finite(Mood.IMPERATIVE, null, people[index]))
            }
        }

        // The stem the personal endings hang off: the infinitive without its
        // own ending. The future and the conditional are the exception — in
        // Catalan they are built on the whole infinitive, which is why they
        // have a stem of their own below.
        val stem = lemma.dropLast(2)
        val futureStem = if (lemma.endsWith("re")) lemma.dropLast(1) else lemma

        put(lemma, VerbReading.NonFinite(NonFiniteForm.INFINITIVE))
        val nonFinite = NonFiniteByConjugation.getValue(conjugation)
        put(stem + nonFinite.gerund, VerbReading.NonFinite(NonFiniteForm.GERUND))
        nonFinite.participles.forEach { put(stem + it, VerbReading.NonFinite(NonFiniteForm.PARTICIPLE)) }

        six(futureStem, FutureEndings, Mood.INDICATIVE, Tense.FUTURE)
        six(futureStem, ConditionalEndings, Mood.INDICATIVE, Tense.CONDITIONAL)

        for (set in FiniteByConjugation.getValue(conjugation)) {
            six(stem, set.presentIndicative, Mood.INDICATIVE, Tense.PRESENT)
            six(stem, set.imperfect, Mood.INDICATIVE, Tense.IMPERFECT)
            six(stem, set.past, Mood.INDICATIVE, Tense.PAST)
            six(stem, set.presentSubjunctive, Mood.SUBJUNCTIVE, Tense.PRESENT)
            six(stem, set.imperfectSubjunctive, Mood.SUBJUNCTIVE, Tense.IMPERFECT)
            imperative(stem, set.imperative)
        }
        return out
    }

    // ---- Step three: the ending on its own ---------------------------------

    /**
     * What the ending alone says, taking the longest ending that fits.
     *
     * Two limits, and both are there because this step only ever runs on a
     * verb whose stem did *not* behave — the regular paradigm has already been
     * tried and missed — so the ending is the only evidence left and it has to
     * be worth something on its own.
     *
     * The stem has to be left with something in it, which is what keeps *és*
     * from being read as an imperfect subjunctive with nothing in front of it.
     * And the ending has to be at least two letters: a single letter is shared
     * by half the paradigm, and on an irregular stem it is a coin toss dressed
     * as an answer. *Digues* ends in *-s* and is an order, not the second
     * person present it would otherwise be called; with this limit the card
     * says it is a form of *dir* and declines to say which, which is true.
     */
    private fun endingReadings(form: String, conjugation: Conjugation): List<VerbReading> {
        val key = deaccent(form)
        val table = EndingTables.getValue(conjugation)
        var best = MIN_FALLBACK_ENDING
        val found = mutableListOf<VerbReading>()
        for ((ending, readings) in table) {
            if (ending.length < best) continue
            if (key.length <= ending.length || !key.endsWith(ending)) continue
            if (ending.length > best) {
                best = ending.length
                found.clear()
            }
            readings.forEach { if (it !in found) found += it }
        }
        return found
    }

    /**
     * Every ending the regular paradigms use, keyed without accents.
     *
     * Built lazily because it is built *out of* the tables below it, which an
     * object initialises in the order they are written.
     */
    private val EndingTables: Map<Conjugation, Map<String, List<VerbReading>>> by lazy {
        Conjugation.entries.associateWith { conjugation ->
            // The same builder as the paradigm, run over an empty stem: an
            // ending table that could drift out of step with the paradigm it
            // is the fallback for would be the worst kind of bug to find.
            val out = HashMap<String, MutableList<VerbReading>>(96)
            fun put(word: String, reading: VerbReading) {
                val key = deaccent(word)
                if (key.isEmpty()) return
                val bucket = out.getOrPut(key) { mutableListOf() }
                if (reading !in bucket) bucket += reading
            }
            fun six(endings: List<String>, mood: Mood, tense: Tense?) {
                endings.forEachIndexed { index, ending ->
                    put(ending, VerbReading.Finite(mood, tense, Person.entries[index]))
                }
            }
            val nonFinite = NonFiniteByConjugation.getValue(conjugation)
            put(nonFinite.gerund, VerbReading.NonFinite(NonFiniteForm.GERUND))
            nonFinite.participles.forEach { put(it, VerbReading.NonFinite(NonFiniteForm.PARTICIPLE)) }
            // The infinitive's own ending, so that a word ending in -ar is at
            // least not read as something else.
            put(nonFinite.infinitive, VerbReading.NonFinite(NonFiniteForm.INFINITIVE))
            // The future and the conditional keep the infinitive in front of
            // them, so their endings include it.
            val carried = nonFinite.infinitive.dropLast(if (nonFinite.infinitive == "re") 1 else 0)
            six(FutureEndings.map { carried + it }, Mood.INDICATIVE, Tense.FUTURE)
            six(ConditionalEndings.map { carried + it }, Mood.INDICATIVE, Tense.CONDITIONAL)
            for (set in FiniteByConjugation.getValue(conjugation)) {
                six(set.presentIndicative, Mood.INDICATIVE, Tense.PRESENT)
                six(set.imperfect, Mood.INDICATIVE, Tense.IMPERFECT)
                six(set.past, Mood.INDICATIVE, Tense.PAST)
                six(set.presentSubjunctive, Mood.SUBJUNCTIVE, Tense.PRESENT)
                six(set.imperfectSubjunctive, Mood.SUBJUNCTIVE, Tense.IMPERFECT)
                val people = listOf(Person.S2, Person.S3, Person.P1, Person.P2, Person.P3)
                set.imperative.forEachIndexed { index, ending ->
                    put(ending, VerbReading.Finite(Mood.IMPERATIVE, null, people[index]))
                }
            }
            out.mapValues { it.value.toList() }
        }
    }

    // ---- The regular endings -----------------------------------------------

    private class NonFiniteEndings(
        val infinitive: String,
        val gerund: String,
        val participles: List<String>,
    )

    private class FiniteEndings(
        val presentIndicative: List<String>,
        val imperfect: List<String>,
        val past: List<String>,
        val presentSubjunctive: List<String>,
        val imperfectSubjunctive: List<String>,
        /** 2nd singular, 3rd singular, then the three plurals. */
        val imperative: List<String>,
    )

    /** Built on the infinitive, so the same for every conjugation. */
    private val FutureEndings = listOf("é", "às", "à", "em", "eu", "an")
    private val ConditionalEndings = listOf("ia", "ies", "ia", "íem", "íeu", "ien")

    private val NonFiniteByConjugation = mapOf(
        Conjugation.FIRST to NonFiniteEndings("ar", "ant", listOf("at", "ada", "ats", "ades")),
        Conjugation.SECOND to NonFiniteEndings("re", "ent", listOf("ut", "uda", "uts", "udes")),
        Conjugation.THIRD to NonFiniteEndings("ir", "int", listOf("it", "ida", "its", "ides")),
    )

    /**
     * The personal endings, by family.
     *
     * The third conjugation gets two sets rather than one: most of its verbs
     * grow an *-eix-* in the singular and the third plural of the present
     * (*serveixes*), a few do not (*dorms*), and which a verb does is not
     * written anywhere in the infinitive. Both are generated and whichever
     * matches answers — a verb cannot accidentally be both, because no verb
     * has both spellings.
     */
    private val FiniteByConjugation: Map<Conjugation, List<FiniteEndings>> = mapOf(
        Conjugation.FIRST to listOf(
            FiniteEndings(
                presentIndicative = listOf("o", "es", "a", "em", "eu", "en"),
                imperfect = listOf("ava", "aves", "ava", "àvem", "àveu", "aven"),
                past = listOf("í", "ares", "à", "àrem", "àreu", "aren"),
                presentSubjunctive = listOf("i", "is", "i", "em", "eu", "in"),
                imperfectSubjunctive = listOf("és", "essis", "és", "éssim", "éssiu", "essin"),
                imperative = listOf("a", "i", "em", "eu", "in"),
            ),
        ),
        Conjugation.SECOND to listOf(
            FiniteEndings(
                presentIndicative = listOf("o", "s", "", "em", "eu", "en"),
                imperfect = listOf("ia", "ies", "ia", "íem", "íeu", "ien"),
                past = listOf("í", "eres", "é", "érem", "éreu", "eren"),
                presentSubjunctive = listOf("i", "is", "i", "em", "eu", "in"),
                imperfectSubjunctive = listOf("és", "essis", "és", "éssim", "éssiu", "essin"),
                imperative = listOf("", "i", "em", "eu", "in"),
            ),
        ),
        Conjugation.THIRD to listOf(
            // Inchoative: servir, serveixo.
            FiniteEndings(
                presentIndicative = listOf("eixo", "eixes", "eix", "im", "iu", "eixen"),
                imperfect = listOf("ia", "ies", "ia", "íem", "íeu", "ien"),
                past = listOf("í", "ires", "í", "írem", "íreu", "iren"),
                presentSubjunctive = listOf("eixi", "eixis", "eixi", "im", "iu", "eixin"),
                imperfectSubjunctive = listOf("ís", "issis", "ís", "íssim", "íssiu", "issin"),
                imperative = listOf("eix", "eixi", "im", "iu", "eixin"),
            ),
            // Pure: dormir, dormo.
            FiniteEndings(
                presentIndicative = listOf("o", "s", "", "im", "iu", "en"),
                imperfect = listOf("ia", "ies", "ia", "íem", "íeu", "ien"),
                past = listOf("í", "ires", "í", "írem", "íreu", "iren"),
                presentSubjunctive = listOf("i", "is", "i", "im", "iu", "in"),
                imperfectSubjunctive = listOf("ís", "issis", "ís", "íssim", "íssiu", "issin"),
                imperative = listOf("", "i", "im", "iu", "in"),
            ),
        ),
    )

    // ---- The six that had to be written out --------------------------------

    private class Irregular(
        val infinitives: List<String>,
        val presentIndicative: List<String>,
        val imperfect: List<String>,
        val past: List<String>,
        val future: List<String>,
        val conditional: List<String>,
        val presentSubjunctive: List<String>,
        val imperfectSubjunctive: List<String>,
        /** 2nd singular, 3rd singular, then the three plurals. */
        val imperative: List<String>,
        val gerund: String,
        val participles: List<String>,
    )

    /**
     * Spellings a form is genuinely written with, separated by a slash.
     *
     * *sóc* and *soc*, *hem* and *havem*: both are the first person plural and
     * a reader who typed either is owed the same answer.
     */
    private fun String.spellings(): List<String> = split('/').filter { it.isNotBlank() }

    private val IrregularParadigms = listOf(
        Irregular(
            infinitives = listOf("ser", "ésser"),
            presentIndicative = listOf("sóc/soc", "ets", "és", "som", "sou", "són/son"),
            imperfect = listOf("era", "eres", "era", "érem", "éreu", "eren"),
            past = listOf("fui", "fores", "fou", "fórem", "fóreu", "foren"),
            future = listOf("seré", "seràs", "serà", "serem", "sereu", "seran"),
            conditional = listOf("seria", "series", "seria", "seríem", "seríeu", "serien"),
            presentSubjunctive = listOf("sigui", "siguis", "sigui", "siguem", "sigueu", "siguin"),
            imperfectSubjunctive = listOf("fos", "fossis", "fos", "fóssim", "fóssiu", "fossin"),
            imperative = listOf("sigues", "sigui", "siguem", "sigueu", "siguin"),
            gerund = "sent/essent",
            participles = listOf("estat", "sigut", "estada", "estats", "estades"),
        ),
        Irregular(
            infinitives = listOf("estar"),
            presentIndicative = listOf("estic", "estàs", "està", "estem", "esteu", "estan"),
            imperfect = listOf("estava", "estaves", "estava", "estàvem", "estàveu", "estaven"),
            past = listOf("estiguí", "estigueres", "estigué", "estiguérem", "estiguéreu", "estigueren"),
            future = listOf("estaré", "estaràs", "estarà", "estarem", "estareu", "estaran"),
            conditional = listOf("estaria", "estaries", "estaria", "estaríem", "estaríeu", "estarien"),
            presentSubjunctive = listOf("estigui", "estiguis", "estigui", "estiguem", "estigueu", "estiguin"),
            imperfectSubjunctive = listOf(
                "estigués", "estiguessis", "estigués", "estiguéssim", "estiguéssiu", "estiguessin",
            ),
            imperative = listOf("estigues", "estigui", "estiguem", "estigueu", "estiguin"),
            gerund = "estant",
            participles = listOf("estat", "estada", "estats", "estades"),
        ),
        Irregular(
            infinitives = listOf("haver"),
            presentIndicative = listOf("he", "has", "ha", "hem/havem", "heu/haveu", "han"),
            imperfect = listOf("havia", "havies", "havia", "havíem", "havíeu", "havien"),
            past = listOf("haguí", "hagueres", "hagué", "haguérem", "haguéreu", "hagueren"),
            future = listOf("hauré", "hauràs", "haurà", "haurem", "haureu", "hauran"),
            conditional = listOf("hauria", "hauries", "hauria", "hauríem", "hauríeu", "haurien"),
            presentSubjunctive = listOf("hagi", "hagis", "hagi", "hàgim/haguem", "hàgiu/hagueu", "hagin"),
            imperfectSubjunctive = listOf(
                "hagués", "haguessis", "hagués", "haguéssim", "haguéssiu", "haguessin",
            ),
            imperative = listOf("", "", "", "", ""),
            gerund = "havent",
            participles = listOf("hagut", "haguda", "haguts", "hagudes"),
        ),
        Irregular(
            infinitives = listOf("anar"),
            presentIndicative = listOf("vaig", "vas", "va", "anem", "aneu", "van"),
            imperfect = listOf("anava", "anaves", "anava", "anàvem", "anàveu", "anaven"),
            past = listOf("aní", "anares", "anà", "anàrem", "anàreu", "anaren"),
            future = listOf("aniré", "aniràs", "anirà", "anirem", "anireu", "aniran"),
            conditional = listOf("aniria", "aniries", "aniria", "aniríem", "aniríeu", "anirien"),
            presentSubjunctive = listOf("vagi", "vagis", "vagi", "anem", "aneu", "vagin"),
            imperfectSubjunctive = listOf("anés", "anessis", "anés", "anéssim", "anéssiu", "anessin"),
            imperative = listOf("vés/ves", "vagi", "anem", "aneu", "vagin"),
            gerund = "anant",
            participles = listOf("anat", "anada", "anats", "anades"),
        ),
        Irregular(
            infinitives = listOf("fer"),
            presentIndicative = listOf("faig", "fas", "fa", "fem", "feu", "fan"),
            imperfect = listOf("feia", "feies", "feia", "fèiem", "fèieu", "feien"),
            past = listOf("fiu", "feres", "féu", "férem", "féreu", "feren"),
            future = listOf("faré", "faràs", "farà", "farem", "fareu", "faran"),
            conditional = listOf("faria", "faries", "faria", "faríem", "faríeu", "farien"),
            presentSubjunctive = listOf("faci", "facis", "faci", "fem", "feu", "facin"),
            imperfectSubjunctive = listOf("fes", "fessis", "fes", "féssim", "féssiu", "fessin"),
            imperative = listOf("fes", "faci", "fem", "feu", "facin"),
            gerund = "fent",
            participles = listOf("fet", "feta", "fets", "fetes"),
        ),
        Irregular(
            infinitives = listOf("tenir"),
            presentIndicative = listOf("tinc", "tens", "té", "tenim", "teniu", "tenen"),
            imperfect = listOf("tenia", "tenies", "tenia", "teníem", "teníeu", "tenien"),
            past = listOf("tinguí", "tingueres", "tingué", "tinguérem", "tinguéreu", "tingueren"),
            future = listOf("tindré", "tindràs", "tindrà", "tindrem", "tindreu", "tindran"),
            conditional = listOf("tindria", "tindries", "tindria", "tindríem", "tindríeu", "tindrien"),
            presentSubjunctive = listOf("tingui", "tinguis", "tingui", "tinguem", "tingueu", "tinguin"),
            imperfectSubjunctive = listOf(
                "tingués", "tinguessis", "tingués", "tinguéssim", "tinguéssiu", "tinguessin",
            ),
            imperative = listOf("té/tingues", "tingui", "tinguem", "tingueu", "tinguin"),
            gerund = "tenint",
            participles = listOf("tingut", "tinguda", "tinguts", "tingudes"),
        ),
    )

    /**
     * The paradigms above, turned inside out: infinitive, then form, then what
     * it is.
     *
     * Lazily, like the ending table and for the same reason: it is built out
     * of things written further down the file, and an object initialises its
     * properties in the order they appear.
     */
    private val Irregulars: Map<String, Map<String, List<VerbReading>>> by lazy {
        buildMap {
        for (verb in IrregularParadigms) {
            val table = HashMap<String, MutableList<VerbReading>>(96)
            fun put(word: String, reading: VerbReading) {
                word.spellings().forEach { spelling ->
                    val key = deaccent(spelling)
                    if (key.isEmpty()) return@forEach
                    val bucket = table.getOrPut(key) { mutableListOf() }
                    if (reading !in bucket) bucket += reading
                }
            }
            fun six(forms: List<String>, mood: Mood, tense: Tense?) {
                forms.forEachIndexed { index, word ->
                    put(word, VerbReading.Finite(mood, tense, Person.entries[index]))
                }
            }
            verb.infinitives.forEach { put(it, VerbReading.NonFinite(NonFiniteForm.INFINITIVE)) }
            put(verb.gerund, VerbReading.NonFinite(NonFiniteForm.GERUND))
            verb.participles.forEach { put(it, VerbReading.NonFinite(NonFiniteForm.PARTICIPLE)) }
            six(verb.presentIndicative, Mood.INDICATIVE, Tense.PRESENT)
            six(verb.imperfect, Mood.INDICATIVE, Tense.IMPERFECT)
            six(verb.past, Mood.INDICATIVE, Tense.PAST)
            six(verb.future, Mood.INDICATIVE, Tense.FUTURE)
            six(verb.conditional, Mood.INDICATIVE, Tense.CONDITIONAL)
            six(verb.presentSubjunctive, Mood.SUBJUNCTIVE, Tense.PRESENT)
            six(verb.imperfectSubjunctive, Mood.SUBJUNCTIVE, Tense.IMPERFECT)
            val people = listOf(Person.S2, Person.S3, Person.P1, Person.P2, Person.P3)
            verb.imperative.forEachIndexed { index, word ->
                put(word, VerbReading.Finite(Mood.IMPERATIVE, null, people[index]))
            }
            val frozen = table.mapValues { it.value.toList() }
            // Filed under every spelling of the infinitive, since the lemma
            // table records both "ser" and "ésser".
            verb.infinitives.forEach { this[it] = frozen }
            }
        }
    }

    // ---- Small helpers -----------------------------------------------------

    /**
     * Which reading to lead with when a form is several things at once.
     *
     * *canta* is the present indicative and it is also an order; the first is
     * overwhelmingly what a reader has just met on a page, so it goes first and
     * the rest follow it. Within a mood the order is the one every grammar
     * prints the paradigm in, so the list reads like a table rather than like a
     * pile.
     */
    private object ReadingOrder : Comparator<VerbReading> {
        override fun compare(a: VerbReading, b: VerbReading): Int =
            rank(a).compareTo(rank(b))

        private fun rank(reading: VerbReading): Int = when (reading) {
            is VerbReading.NonFinite -> reading.form.ordinal
            is VerbReading.Finite -> {
                val mood = when (reading.mood) {
                    Mood.INDICATIVE -> 0
                    Mood.SUBJUNCTIVE -> 1
                    Mood.IMPERATIVE -> 2
                }
                10 + mood * 100 + (reading.tense?.ordinal ?: 0) * 10 + reading.person.ordinal
            }
        }
    }

    /** The shortest ending the fallback will read anything into. */
    private const val MIN_FALLBACK_ENDING = 2

    /** Lower case, with the app's usual apostrophes and stray punctuation gone. */
    internal fun normalise(word: String): String = CatalanWordBank.normalise(word)

    /**
     * The same word with its accents taken off.
     *
     * Only ever used to compare a written form against a generated one. Catalan
     * moves the stress around a paradigm — *córrer*, *corria*, *correré* — and
     * the accent is the only thing that moves, so comparing without it is what
     * lets one set of rules cover a whole regular verb.
     */
    internal fun deaccent(word: String): String = Normalizer
        .normalize(word.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")

    private val COMBINING_MARKS = Regex("\\p{Mn}+")
}
