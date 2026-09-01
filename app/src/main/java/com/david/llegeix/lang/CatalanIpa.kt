package com.david.llegeix.lang

/**
 * The pronunciation of a word, and how much to trust it.
 *
 * [isApproximate] is not modesty for its own sake. Central Catalan distinguishes
 * close /e o/ from open /ɛ ɔ/, and when the stressed vowel carries no written
 * accent the spelling simply does not record which one it is: *pedra* is
 * [ˈpeðɾə] but *terra* is [ˈtɛrə], and nothing in the letters says so. Those
 * words are transcribed with the open vowel and flagged, so the reader knows to
 * check rather than learning a wrong vowel with confidence.
 */
data class Pronunciation(
    val ipa: String,
    val isApproximate: Boolean,
)

/**
 * Spelling to IPA for Central (standard) Catalan.
 *
 * Rule-based rather than a dictionary, because the point is to pronounce
 * whatever word the reader happened to press, including names and inflected
 * forms no word list would carry. The rules cover what Catalan orthography
 * genuinely encodes:
 *
 * - Unstressed vowel reduction, the feature that most marks the dialect:
 *   unstressed a/e collapse to [ə] and unstressed o/u to [u].
 * - Stress placement from the written accent, or from the ending when there is
 *   none.
 * - The digraphs — ll, ny, l·l, tx, tg/tj, ig, qu, gu, ix.
 * - Positional consonants: s voicing between vowels, r trilled initially and
 *   after n/l/s, final devoicing of b/d/g, betacism of v, silent final r in
 *   polysyllables.
 *
 * Deliberately broad: no spirantisation of b/d/g between vowels, no
 * phrase-level resyllabification or assimilation. Those are real, but a
 * transcription dense with detail is harder to read than it is useful, and this
 * is here to be read by someone learning the language.
 */
object CatalanIpa {

    fun transcribe(text: String): Pronunciation {
        val words = text.trim().split(WHITESPACE).filter { it.isNotBlank() }
        if (words.isEmpty()) return Pronunciation("", false)

        val parts = words.map { transcribeWord(it) }
        return Pronunciation(
            ipa = parts.joinToString(" ") { it.ipa },
            isApproximate = parts.any { it.isApproximate },
        )
    }

    private fun transcribeWord(raw: String): Pronunciation {
        // An elided article is its own phonological word: l'aigua is two.
        if (raw.contains('\'') || raw.contains('’')) {
            val pieces = raw.split('\'', '’').filter { it.isNotBlank() }
            if (pieces.size > 1) {
                val host = transcribeWord(pieces.last())
                val clitics = pieces.dropLast(1).joinToString("") { cliticOnset(it) }
                // The clitic joins the stressed syllable's onset, so it belongs
                // inside the stress mark: [ˈlajɡwə], not [lˈajɡwə].
                val ipa = if (host.ipa.startsWith("ˈ")) {
                    "ˈ" + clitics + host.ipa.removePrefix("ˈ")
                } else {
                    clitics + host.ipa
                }
                return Pronunciation(ipa, host.isApproximate)
            }
        }

        val word = raw.lowercase().filter { it.isLetter() || it == '·' }
        if (word.isEmpty()) return Pronunciation("", false)
        EXCEPTIONS[word]?.let { return Pronunciation(it, false) }

        val units = segment(word)
        val nuclei = units.indices.filter { units[it].isVowel }
        if (nuclei.isEmpty()) {
            return Pronunciation(
                units.indices.joinToString("") { consonant(it, units) },
                false,
            )
        }

        val stressed = stressedNucleus(units, nuclei)
        var approximate = false

        val phonemes = ArrayList<String>(units.size)
        // Where each unit's phonemes start, so the stress mark can be inserted
        // ahead of the stressed syllable's onset rather than glued to the vowel.
        val startOfUnit = IntArray(units.size)

        units.forEachIndexed { index, unit ->
            startOfUnit[index] = phonemes.size
            if (unit.isVowel) {
                val isStressed = index == stressed
                val vowel = vowel(unit, units, index, isStressed)
                if (isStressed && vowel.approximate) approximate = true
                phonemes += vowel.ipa
            } else {
                val mapped = consonant(index, units)
                if (mapped.isNotEmpty()) phonemes += mapped
            }
        }

        val mark = onsetOf(startOfUnit[stressed], phonemes)
        val body = buildString {
            phonemes.forEachIndexed { index, phoneme ->
                if (index == mark && phonemes.size > 1) append('ˈ')
                append(phoneme)
            }
        }
        return Pronunciation(body, approximate)
    }

    /**
     * An elided article or pronoun — the l of l'aigua, the d of d'or.
     *
     * It leans on the following word and is syllabified as its onset, so it
     * takes the clear onset form rather than the velarised coda one.
     */
    private fun cliticOnset(piece: String): String =
        piece.lowercase().filter { it.isLetter() }.map { c ->
            when (c) {
                'l' -> "l"
                'd' -> "d"
                'n' -> "n"
                's' -> "s"
                'm' -> "m"
                't' -> "t"
                else -> c.toString()
            }
        }.joinToString("")

    // ---- Segmentation -----------------------------------------------------

    /** One orthographic unit: a digraph counts as one, as does each vowel. */
    private data class Unit(val text: String, val isVowel: Boolean)

    private val DIGRAPHS = listOf(
        "l·l", "ny", "ll", "tx", "ig", "tj", "tg", "tz", "ts",
        "qu", "gu", "gü", "qü", "rr", "ss", "ch",
    )

    private fun segment(word: String): List<Unit> {
        val units = mutableListOf<Unit>()
        var i = 0
        while (i < word.length) {
            val digraph = DIGRAPHS.firstOrNull { word.startsWith(it, i) }
            // qu/gu are only digraphs before e or i; before a or o the u is a
            // real glide, as in "quatre" and "guant".
            val isLabialised = digraph == "qu" || digraph == "gu"
            val next = word.getOrNull(i + (digraph?.length ?: 0))
            if (digraph != null && !(isLabialised && next != null && next !in "ei")) {
                units += Unit(digraph, false)
                i += digraph.length
            } else {
                val c = word[i]
                units += Unit(c.toString(), c in VOWELS)
                i++
            }
        }
        return units
    }

    private const val VOWELS = "aeiouàèéíïòóúü"
    private val WHITESPACE = Regex("\\s+")

    // ---- Stress -----------------------------------------------------------

    private const val ACCENTED = "àèéíòóú"

    /**
     * Which vowel carries the stress.
     *
     * A written accent settles it. Otherwise Catalan's default: words ending in
     * a vowel, a vowel plus -s, or -en/-in are stressed on the penultimate
     * vowel; everything else on the last.
     */
    private fun stressedNucleus(units: List<Unit>, nuclei: List<Int>): Int {
        nuclei.firstOrNull { units[it].text.first() in ACCENTED }?.let { return it }

        // Glides are not syllable nuclei, so they cannot take the stress.
        val syllabic = nuclei.filter { !isGlide(units, it) }
        if (syllabic.isEmpty()) return nuclei.last()
        if (syllabic.size == 1) return syllabic.first()

        val last = units.last().text
        val penultimate = units.getOrNull(units.size - 2)?.text.orEmpty()
        val endsPlain = units.last().isVowel ||
            (last == "s" && units.getOrNull(units.size - 2)?.isVowel == true) ||
            (last == "n" && penultimate == "e") ||
            (last == "n" && penultimate == "i")

        return if (endsPlain) syllabic[syllabic.size - 2] else syllabic.last()
    }

    /** True when this i/u is riding on a neighbouring vowel rather than its own. */
    private fun isGlide(units: List<Unit>, index: Int): Boolean {
        val unit = units[index]
        if (unit.text !in listOf("i", "u")) return false
        // The silent marker i of "ix" is not a nucleus and not a glide.
        if (unit.text == "i" &&
            units.getOrNull(index + 1)?.text == "x" &&
            units.getOrNull(index - 1)?.isVowel == true
        ) {
            return true
        }
        val before = units.getOrNull(index - 1)

        // Catalan diphthongs fall, they do not rise: ai, ei, oi, au, eu, iu are
        // one syllable, but an i or u *followed* by a vowel is a hiatus and a
        // syllable of its own. Treating "-ia" as a diphthong put the stress on
        // the wrong vowel of every imperfect — tenia is [təˈniə], not [ˈtɛnjə].
        // "iu" and "ui" are real falling diphthongs — niu, ciutat, cuina — so a
        // preceding i or u counts, as long as it is a nucleus itself and not
        // another glide. The recursion walks left and always terminates.
        if (before?.isVowel == true && !isGlide(units, index - 1)) return true

        // The one rising exception: the u of qua/guo, which qu/gu only spell as
        // a digraph before e and i.
        return unit.text == "u" && before?.text in listOf("q", "g")
    }

    // ---- Vowels -----------------------------------------------------------

    private data class VowelSound(val ipa: String, val approximate: Boolean)

    private fun vowel(
        unit: Unit,
        units: List<Unit>,
        index: Int,
        isStressed: Boolean,
    ): VowelSound {
        // The i of "ix" only tells you the x is [ʃ]; it is not pronounced when
        // a vowel already precedes it. caixa is [ˈkaʃə], not [ˈkajʃə].
        if (unit.text == "i" &&
            units.getOrNull(index + 1)?.text == "x" &&
            units.getOrNull(index - 1)?.isVowel == true
        ) {
            return VowelSound("", false)
        }
        if (isGlide(units, index)) {
            return VowelSound(if (unit.text == "i") "j" else "w", false)
        }
        return when (unit.text) {
            "à" -> VowelSound("a", false)
            "é" -> VowelSound("e", false)
            "è" -> VowelSound("ɛ", false)
            "í", "ï" -> VowelSound("i", false)
            "ó" -> VowelSound("o", false)
            "ò" -> VowelSound("ɔ", false)
            "ú", "ü" -> VowelSound("u", false)
            "i" -> VowelSound("i", false)
            "u" -> VowelSound("u", false)
            "a" -> if (isStressed) VowelSound("a", false) else VowelSound("ə", false)
            // The two the spelling refuses to disambiguate. Open is the guess.
            "e" -> if (isStressed) VowelSound("ɛ", true) else VowelSound("ə", false)
            "o" -> if (isStressed) VowelSound("ɔ", true) else VowelSound("u", false)
            else -> VowelSound(unit.text, false)
        }
    }

    // ---- Consonants -------------------------------------------------------

    private fun consonant(index: Int, units: List<Unit>): String {
        val text = units[index].text
        val nextUnit = units.getOrNull(index + 1)
        val prevUnit = units.getOrNull(index - 1)
        val next = nextUnit?.text?.firstOrNull()
        val isFinal = index == units.lastIndex
        val beforeFrontVowel = next != null && next in "eiéèíï"
        val betweenVowels = prevUnit?.isVowel == true && nextUnit?.isVowel == true

        return when (text) {
            "l·l" -> "ɫː"
            "ll" -> "ʎ"
            "ny" -> "ɲ"
            "tx" -> "tʃ"
            "tg", "tj" -> "dʒ"
            "tz" -> "dz"
            "ts" -> "ts"
            "qu" -> "k"
            "gu" -> "ɡ"
            "qü" -> "kw"
            "gü" -> "ɡw"
            "rr" -> "r"
            "ss" -> "s"
            "ch" -> "k"
            // Word-final -ig is [tʃ]: maig, desig, puig.
            "ig" -> if (isFinal) "tʃ" else "iɡ"

            "b" -> if (isFinal) "p" else "b"
            "c" -> if (beforeFrontVowel) "s" else "k"
            "ç" -> "s"
            "d" -> if (isFinal) "t" else "d"
            "f" -> "f"
            "g" -> when {
                beforeFrontVowel -> "ʒ"
                isFinal -> "k"
                else -> "ɡ"
            }
            "h" -> ""
            "j" -> "ʒ"
            "k" -> "k"
            // Catalan l is velarised; most audible in the coda.
            "l" -> if (nextUnit?.isVowel == true) "l" else "ɫ"
            "m" -> "m"
            "n" -> if (next != null && next in "gqk") "ŋ" else "n"
            "p" -> "p"
            "q" -> "k"
            "r" -> when {
                // Silent in polysyllables — cantar, carrer, senyor — but kept
                // in monosyllables like mar and cor.
                isFinal -> if (units.count { it.isVowel } > 1) "" else "ɾ"
                index == 0 -> "r"
                prevUnit?.text in listOf("n", "l", "s") -> "r"
                else -> "ɾ"
            }
            "s" -> if (betweenVowels) "z" else "s"
            "t" -> "t"
            "v" -> if (isFinal) "f" else "b"
            "w" -> "w"
            // Initial or post-consonantal x is [ʃ]; between vowels it is the
            // learned [ks] of "màxim". The i of "ix" is only a marker.
            "x" -> when {
                index == 0 -> "ʃ"
                prevUnit?.text == "i" -> "ʃ"
                prevUnit?.isVowel == false -> "ʃ"
                else -> "ks"
            }
            "y" -> "j"
            "z" -> if (isFinal) "s" else "z"
            else -> text
        }
    }

    /**
     * Where the stress mark goes: before the stressed syllable's onset.
     *
     * Walks back over the consonants that can legally begin a syllable — one,
     * or two when they form a stop-plus-liquid cluster like "bɾ" in "llibre".
     */
    private fun onsetOf(nucleus: Int, phonemes: List<String>): Int {
        if (nucleus == 0) return 0
        var start = nucleus
        val first = phonemes[nucleus - 1]
        if (first.isConsonant()) {
            start = nucleus - 1
            val second = phonemes.getOrNull(nucleus - 2)
            if (second != null && second.isConsonant() &&
                first in listOf("ɾ", "l", "ɫ", "w", "j") && second in STOPS
            ) {
                start = nucleus - 2
            }
        }
        return start
    }

    private val STOPS = setOf("p", "b", "t", "d", "k", "ɡ", "f")
    private val VOWEL_SOUNDS = setOf("a", "e", "ɛ", "i", "o", "ɔ", "u", "ə")

    private fun String.isConsonant(): Boolean = this !in VOWEL_SOUNDS && isNotEmpty()

    /**
     * Words the rules cannot get right, kept deliberately short.
     *
     * Every entry here is one where the stressed vowel's aperture is lexical, or
     * where the word is common enough that a wrong vowel would be actively
     * taught. It is not meant to grow into a dictionary.
     */
    private val EXCEPTIONS: Map<String, String> = mapOf(
        "és" to "es",
        "què" to "kɛ",
        "que" to "kə",
        "de" to "də",
        "el" to "əɫ",
        "la" to "lə",
        "les" to "ləs",
        "els" to "əɫs",
        "un" to "un",
        "una" to "ˈunə",
        "amb" to "am",
        "per" to "pər",
        "però" to "pəˈɾɔ",
        "com" to "kɔm",
        "molt" to "moɫ",
        "temps" to "tems",
        "món" to "mon",
        "home" to "ˈɔmə",
        "dona" to "ˈdɔnə",
        "terra" to "ˈtɛrə",
        "festa" to "ˈfɛstə",
        "cel" to "sɛɫ",
        "carrer" to "kəˈre",
        "senyor" to "səˈɲo",
        "barcelona" to "bərsəˈlonə",
        "petit" to "pəˈtit",
        "príncep" to "ˈprinsəp",
        "llibre" to "ˈʎibɾə",
        "pedra" to "ˈpedɾə",
        "aigua" to "ˈajɡwə",
    )
}
