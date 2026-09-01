package com.david.llegeix.lang

import android.content.Context
import java.io.IOException

/**
 * Which way a word's stressed e or o opens, for the words spelling cannot say.
 *
 * Backed by `assets/catalan-aperture.tsv`. The lookup does a little morphology
 * before giving up: Catalan inflection is regular enough that listing *terra*
 * should also answer *terres*, and listing *portar* should answer *portava*, so
 * the loader strips common endings rather than making the file carry every
 * form. That keeps the list small enough to stay trustworthy — which matters,
 * because a wrong entry here is worse than the "approximate" marker it replaces.
 */
class ApertureLexicon private constructor(
    private val entries: Map<String, String>,
) {

    /**
     * The stressed vowel for [word], or null when the list does not cover it and
     * the caller should fall back to guessing.
     */
    fun apertureOf(word: String): String? {
        val key = word.lowercase()
        entries[key]?.let { return it }

        // Inflection, longest ending first so -aves is tried before -es.
        for (suffix in SUFFIXES) {
            if (key.length > suffix.length + 2 && key.endsWith(suffix)) {
                val stem = key.dropLast(suffix.length)
                entries[stem]?.let { return it }
                // Verbs are listed by infinitive: portava -> port + ar.
                entries[stem + "ar"]?.let { return it }
                entries[stem + "a"]?.let { return it }
                entries[stem + "e"]?.let { return it }
            }
        }
        return null
    }

    companion object {
        private const val ASSET = "catalan-aperture.tsv"

        /**
         * Endings stripped when looking for a listed base form.
         *
         * Only endings that leave the stressed syllable alone: adding *-es* to
         * *terra* does not move the stress, so the aperture carries over.
         * Endings that shift the stress — the *-ció* of a derived noun, say —
         * are deliberately absent, because the vowel they land on is a
         * different vowel entirely.
         */
        private val SUFFIXES = listOf(
            "aven", "aves", "ava", "aran", "aria", "arem", "aria",
            "ets", "ets", "ers", "es", "os", "ns", "s", "a", "e",
        ).sortedByDescending { it.length }

        @Volatile
        private var instance: ApertureLexicon? = null

        /** Loaded once and shared; the file is small and read on first lookup. */
        fun get(context: Context): ApertureLexicon =
            instance ?: synchronized(this) {
                instance ?: load(context).also { instance = it }
            }

        private fun load(context: Context): ApertureLexicon {
            val entries = HashMap<String, String>(256)
            try {
                context.applicationContext.assets.open(ASSET).bufferedReader().useLines { lines ->
                    for (raw in lines) {
                        val line = raw.trim()
                        if (line.isEmpty() || line.startsWith("#")) continue
                        val parts = line.split('\t')
                        if (parts.size != 2) continue
                        val quality = parts[1].trim()
                        if (quality in VALID) entries[parts[0].trim().lowercase()] = quality
                    }
                }
            } catch (error: IOException) {
                // A missing or unreadable asset costs accuracy, not function:
                // every word simply falls back to the marked-approximate guess.
                return ApertureLexicon(emptyMap())
            }
            return ApertureLexicon(entries)
        }

        private val VALID = setOf("e", "ɛ", "o", "ɔ")

        /** Test seam, so the rules can be exercised without an Android context. */
        internal fun of(entries: Map<String, String>): ApertureLexicon =
            ApertureLexicon(entries)
    }
}
