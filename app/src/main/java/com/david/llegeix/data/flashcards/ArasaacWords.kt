package com.david.llegeix.data.flashcards

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Meanings from ARASAAC, as against from a translator.
 *
 * Every ARASAAC pictogram is labelled by people, in every language the project
 * covers: the carrot is *pastanaga* in Catalan, *morcov* in Romanian and
 * *carrot* in English because somebody wrote each of those down, not because a
 * model guessed. For the concrete words a flashcard is usually made of that is
 * far better than a translator handed one word with no sentence around it —
 * which gives "apple" for *poma* in Romanian, and nothing at all for *pa*.
 *
 * Only an exact match counts: the pictogram has to be labelled with the very
 * word typed, so a meaning is never borrowed from a neighbouring word.
 */
object ArasaacWords {

    /** Pictograms labelled with exactly [word] in [language], best first. */
    fun exactSearchUrl(word: String, language: String = "ca"): String =
        "https://api.arasaac.org/v1/pictograms/$language/bestsearch/" +
            URLEncoder.encode(word.trim(), "UTF-8").replace("+", "%20")

    /** One pictogram, labelled in [language]. */
    fun pictogramUrl(language: String, id: Int): String =
        "https://api.arasaac.org/v1/pictograms/$language/$id"

    /**
     * The first pictogram in an exact search whose Catalan label really is
     * [word] — checked here as well, because "best" is ARASAAC's idea of best.
     */
    fun firstExactId(json: String, word: String): Int? {
        val array = try {
            JSONArray(json)
        } catch (error: JSONException) {
            return null
        }
        val wanted = word.trim().lowercase()
        for (i in 0 until array.length()) {
            val pictogram = array.optJSONObject(i) ?: continue
            val keywords = pictogram.optJSONArray("keywords") ?: continue
            val labelled = (0 until keywords.length()).any { k ->
                keywords.optJSONObject(k)?.optString("keyword")?.trim()?.lowercase() == wanted
            }
            val id = pictogram.optInt("_id", -1)
            if (labelled && id > 0) return id
        }
        return null
    }

    /** A pictogram's first label, tidied; null if it has none in that language. */
    fun firstKeyword(json: String): String? {
        val keywords = try {
            JSONObject(json).optJSONArray("keywords")
        } catch (error: JSONException) {
            null
        } ?: return null
        for (i in 0 until keywords.length()) {
            val keyword = keywords.optJSONObject(i)?.optString("keyword")?.trim().orEmpty()
            if (keyword.isNotEmpty()) return tidyRomanian(keyword)
        }
        return null
    }

    /**
     * Romanian written with a comma below its s and t, as it should be.
     *
     * Older Romanian text, much of ARASAAC's included, uses the cedilla forms
     * ş and ţ, which were what fonts had before the right letters existed. They
     * look nearly the same and are different characters — a card saying
     * *maşină* would not match a search for *mașină*.
     */
    fun tidyRomanian(text: String): String = text
        .replace('ş', 'ș').replace('Ş', 'Ș')
        .replace('ţ', 'ț').replace('Ţ', 'Ț')
}
