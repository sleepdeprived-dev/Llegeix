package com.david.llegeix.lang

import android.content.Context
import java.io.IOException

/** One sense of a word: the synonyms that share a meaning, and its part of speech. */
data class SynonymSense(
    /** As the dictionary labels it — `n`, `v`, `adj`, sometimes with a gloss. */
    val partOfSpeech: String,
    val words: List<String>,
)

/** Everything the thesaurus knows about one word. */
data class ThesaurusEntry(
    /** The form actually found, which may be the base form of what was selected. */
    val headword: String,
    val senses: List<SynonymSense>,
    val antonyms: List<String>,
) {
    val isEmpty: Boolean get() = senses.isEmpty() && antonyms.isEmpty()
}

/**
 * Catalan synonyms and antonyms, read from a bundled dictionary.
 *
 * The source is the *Diccionari de sinònims de català* by Jaume Ortolà and
 * Softcatalà, CC BY 4.0, converted at build time from the MyThes file that
 * LibreOffice ships into one sorted line per headword. Attribution is required
 * by the licence and is shown in Configuració.
 *
 * It lives entirely on the device: no account, no key, no request. The cost is
 * about 1.5 MB of APK and, once something is looked up, five megabytes of
 * memory — so the file is read on the first lookup and not before.
 *
 * Lookup is a binary search over the raw bytes rather than a `HashMap`. The
 * file is sorted, and UTF-8 keeps code-point order when compared byte by byte,
 * so the search needs the file in memory but no index built from it, and none
 * of the 44,000 keys are ever turned into Java strings.
 */
class CatalanThesaurus private constructor(
    private val data: ByteArray,
    private val lineStarts: IntArray,
) {

    /**
     * What the dictionary has for [word], or null when it has nothing.
     *
     * Written forms are looked up first, then the base form: *històries* is not
     * listed but *història* is, and a reader who taps a plural expects an
     * answer rather than a shrug.
     */
    fun lookup(word: String): ThesaurusEntry? {
        val key = normalise(word)
        if (key.isEmpty()) return null
        findEntry(key)?.let { return it }
        for (candidate in baseForms(key)) {
            findEntry(candidate)?.let { return it }
        }
        return null
    }

    private fun findEntry(key: String): ThesaurusEntry? {
        val needle = key.toByteArray(Charsets.UTF_8)
        var low = 0
        var high = lineStarts.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val cmp = compareKeyAt(lineStarts[mid], needle)
            when {
                cmp < 0 -> low = mid + 1
                cmp > 0 -> high = mid - 1
                else -> return parse(lineStarts[mid])
            }
        }
        return null
    }

    /** Compares the key of the line at [start] against [needle], byte by byte. */
    private fun compareKeyAt(start: Int, needle: ByteArray): Int {
        var i = start
        var j = 0
        while (i < data.size && data[i] != TAB && data[i] != NEWLINE) {
            if (j == needle.size) return 1
            val a = data[i].toInt() and 0xFF
            val b = needle[j].toInt() and 0xFF
            if (a != b) return a - b
            i++
            j++
        }
        return if (j == needle.size) 0 else -1
    }

    private fun parse(start: Int): ThesaurusEntry {
        var end = start
        while (end < data.size && data[end] != NEWLINE) end++
        val fields = String(data, start, end - start, Charsets.UTF_8).split('\t')
        val senses = mutableListOf<SynonymSense>()
        val antonyms = mutableListOf<String>()
        for (field in fields.drop(1)) {
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
        return ThesaurusEntry(fields[0], senses, antonyms)
    }

    companion object {
        private const val ASSET = "catalan-thesaurus.tsv"
        private const val TAB = '\t'.code.toByte()
        private const val NEWLINE = '\n'.code.toByte()

        /**
         * How a written form is walked back to the form the dictionary lists.
         *
         * Each rule strips an ending and offers the endings that could replace
         * it, best guess first. The replacement matters: *cases* is the plural
         * of *casa*, not of *cas*, and answering with the synonyms of *cas*
         * would be worse than answering with nothing. The list is short on
         * purpose for the same reason — it covers the regular plurals and the
         * common first-conjugation verb forms, and leaves the rest alone.
         */
        private val RULES: List<Pair<String, List<String>>> = listOf(
            // First-conjugation verbs, listed in the dictionary by infinitive.
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
            // Plurals. A feminine -a becomes -es, a word in -e keeps its -e,
            // and everything else simply loses the -s.
            "es" to listOf("a", "e", ""),
            "os" to listOf("", "o"),
            "ns" to listOf("", "n"),
            "s" to listOf(""),
            // Feminine adjectives, which the dictionary lists in the masculine:
            // bonica is not an entry, bonic is. Only reached when the written
            // form itself was not found, so casa still answers as casa.
            "a" to listOf(""),
        ).sortedByDescending { it.first.length }

        /**
         * Words the thesaurus should not be asked about.
         *
         * Catalan's function words collide with real entries — *es* is both the
         * pronoun and a noun meaning a bend in a river, *son* is both the verb
         * and sleep — so looking them up answers a question nobody asked, and
         * does it confidently. A reader learning the language is exactly the
         * person least able to spot that the answer is for a different word.
         *
         * Only closed classes are here: articles, prepositions, conjunctions,
         * and the weak pronouns. Verbs and nouns are left alone even when they
         * are common, because there the answer is genuinely useful.
         */
        private val FUNCTION_WORDS = setOf(
            "el", "la", "els", "les", "l", "un", "una", "uns", "unes",
            // The elided forms are what a PDF actually contains: l'obra, d'ell.
            "l'", "d'", "s'", "m'", "t'", "n'",
            "a", "de", "d", "en", "amb", "per", "sense", "sobre", "sota",
            "fins", "cap", "entre", "contra", "segons", "durant", "des",
            "i", "o", "però", "que", "què", "si", "com", "quan", "doncs",
            "perquè", "mentre", "ni", "sinó",
            "es", "se", "s", "em", "et", "ens", "us", "li", "hi", "ho",
            "em", "me", "te", "jo", "tu", "ell", "ella", "nosaltres",
            "vosaltres", "ells", "elles", "meu", "teu", "seu", "meva",
            "teva", "seva", "aquest", "aquesta", "aquell", "aquella",
            "és", "són", "hi_ha",
        )

        /**
         * True when [word] is worth asking the dictionary about at all.
         *
         * Used before a lookup rather than inside it, so that the dictionary
         * stays a plain dictionary and the judgement about what is useful to
         * show sits with the screen showing it.
         */
        fun isWorthLookingUp(word: String): Boolean {
            val key = normalise(word)
            return key.length > 1 && key !in FUNCTION_WORDS
        }

        @Volatile
        private var instance: CatalanThesaurus? = null

        /**
         * The shared dictionary, loading it on the calling thread the first time.
         *
         * Five megabytes of file reading, so callers should be off the main
         * thread; every later call is a field read.
         */
        fun get(context: Context): CatalanThesaurus =
            instance ?: synchronized(this) {
                instance ?: load(context).also { instance = it }
            }

        private fun load(context: Context): CatalanThesaurus {
            val data = try {
                context.applicationContext.assets.open(ASSET).use { it.readBytes() }
            } catch (error: IOException) {
                // Without the file every lookup simply finds nothing, which the
                // sheet already knows how to say.
                ByteArray(0)
            }
            return CatalanThesaurus(data, indexLines(data))
        }

        private fun indexLines(data: ByteArray): IntArray {
            if (data.isEmpty()) return IntArray(0)
            var count = 0
            for (byte in data) if (byte == NEWLINE) count++
            val starts = IntArray(count + 1)
            var n = 0
            starts[n++] = 0
            for (i in data.indices) {
                if (data[i] == NEWLINE && i + 1 < data.size) starts[n++] = i + 1
            }
            return if (n == starts.size) starts else starts.copyOf(n)
        }

        /**
         * The dictionary's own spelling of a selected word.
         *
         * PDFs are full of typographic apostrophes and stray punctuation that
         * the dictionary, written in plain ASCII quoting, does not use.
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

        /** Test seam: build a dictionary from lines instead of the asset. */
        internal fun of(lines: List<String>): CatalanThesaurus {
            val data = (lines.sorted().joinToString("\n") + "\n").toByteArray(Charsets.UTF_8)
            return CatalanThesaurus(data, indexLines(data))
        }
    }
}
