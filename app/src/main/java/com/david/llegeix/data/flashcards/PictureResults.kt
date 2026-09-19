package com.david.llegeix.data.flashcards

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

/** Where suggested pictures come from. */
enum class PictureSource {
    /**
     * ARASAAC's pictograms: clear drawings made for learning vocabulary, one
     * idea to a picture, searched in Catalan directly. Asked first because a
     * card is one word, and a drawing of one thing is what that wants.
     */
    PICTOGRAMS,

    /**
     * Openverse's openly licensed photos, from Flickr, Wikimedia and others.
     * Searched in English, because that is what their titles and tags are
     * written in: "pa" finds Pennsylvania there, "bread" finds bread.
     */
    PHOTOS,
}

/** One picture on offer. */
data class PictureHit(
    val source: PictureSource,
    val id: String,
    /** Small enough to show a row of at once. */
    val thumbnailUrl: String,
    /** The picture to put on the card, shrunk on the way in like any other. */
    val fullUrl: String,
    /** Who made it and under what licence, to keep with the card. */
    val credit: String,
)

/**
 * The part of picture search that is only text: where to ask, and what the
 * answer means. Kept apart from the network so it can be tested without one.
 */
object PictureResults {

    /** How many to offer. A row a thumb can flick through, not a gallery. */
    const val LIMIT = 12

    const val ARASAAC_CREDIT = "Sergio Palao · ARASAAC · CC BY-NC-SA"

    fun arasaacSearchUrl(word: String): String =
        "https://api.arasaac.org/v1/pictograms/ca/search/" + encodePath(word.trim())

    fun openverseSearchUrl(query: String): String =
        "https://api.openverse.org/v1/images/?q=" +
            URLEncoder.encode(query.trim(), "UTF-8") +
            "&page_size=$LIMIT&mature=false"

    /**
     * ARASAAC's answer: an array of pictograms.
     *
     * Pictograms the collection itself marks as showing sex or violence are
     * left out. They exist for good reasons in a symbol set for communication,
     * and none of them is what somebody wants on a vocabulary card by surprise.
     */
    fun parseArasaac(json: String): List<PictureHit> {
        val array = try {
            JSONArray(json)
        } catch (error: JSONException) {
            return emptyList()
        }
        return (0 until array.length()).asSequence()
            .mapNotNull { array.optJSONObject(it) }
            .filterNot { it.optBoolean("sex", false) || it.optBoolean("violence", false) }
            .mapNotNull { pictogram ->
                val id = pictogram.optInt("_id", -1).takeIf { it > 0 } ?: return@mapNotNull null
                PictureHit(
                    source = PictureSource.PICTOGRAMS,
                    id = id.toString(),
                    thumbnailUrl = "https://static.arasaac.org/pictograms/$id/${id}_300.png",
                    fullUrl = "https://static.arasaac.org/pictograms/$id/${id}_500.png",
                    credit = ARASAAC_CREDIT,
                )
            }
            .take(LIMIT)
            .toList()
    }

    /** Openverse's answer: `results`, each with its own maker and licence. */
    fun parseOpenverse(json: String): List<PictureHit> {
        val results = try {
            JSONObject(json).optJSONArray("results") ?: return emptyList()
        } catch (error: JSONException) {
            return emptyList()
        }
        return (0 until results.length()).asSequence()
            .mapNotNull { results.optJSONObject(it) }
            .filterNot { it.optBoolean("mature", false) }
            .mapNotNull { photo ->
                val id = photo.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val full = photo.optString("url").takeIf { it.startsWith("https://") }
                    ?: return@mapNotNull null
                val thumbnail = photo.optString("thumbnail").takeIf { it.startsWith("https://") }
                    ?: full
                PictureHit(
                    source = PictureSource.PHOTOS,
                    id = id,
                    thumbnailUrl = thumbnail,
                    fullUrl = full,
                    credit = openverseCredit(
                        creator = photo.optString("creator"),
                        source = photo.optString("source"),
                        license = photo.optString("license"),
                        version = photo.optString("license_version"),
                    ),
                )
            }
            .take(LIMIT)
            .toList()
    }

    /** "astronomy_blog · Flickr · CC BY-NC-SA 2.0", leaving out what is unknown. */
    fun openverseCredit(creator: String, source: String, license: String, version: String): String {
        val licence = when (val code = license.lowercase(Locale.ROOT).trim()) {
            "" -> null
            "cc0" -> "CC0"
            "pdm" -> "Public domain"
            else -> "CC ${code.uppercase(Locale.ROOT)}" + version.trim().let { if (it.isEmpty()) "" else " $it" }
        }
        val where = source.trim().takeIf { it.isNotEmpty() }
            ?.replaceFirstChar { it.titlecase(Locale.ROOT) }
        return listOfNotNull(creator.trim().takeIf { it.isNotEmpty() }, where, licence)
            .joinToString(" · ")
    }

    /** A path segment, where a space is %20 rather than the query string's +. */
    private fun encodePath(segment: String): String =
        URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
}
