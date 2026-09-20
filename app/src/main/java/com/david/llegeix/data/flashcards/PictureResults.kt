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

    /** How many to offer: a small grid, not a gallery. */
    const val LIMIT = 12

    /**
     * How many photos to ask for, more than are offered, so that the ones the
     * safety check drops still leave a full grid.
     */
    const val FETCH = 20

    /** The width Commons is asked to make its thumbnails at, for the grid. */
    private const val COMMONS_THUMBNAIL_PX = 320

    /** And the width the one chosen picture is fetched at. */
    private const val COMMONS_FULL_PX = 800

    private val HTML_TAG = Regex("<[^>]*>")
    private val WHITESPACE = Regex("\\s+")

    const val ARASAAC_CREDIT = "Sergio Palao · ARASAAC · CC BY-NC-SA"

    /**
     * Whether a picture with this credit is an ARASAAC pictogram, and so drawn
     * as one: black lines on paper that the app recolours to fit its theme.
     */
    fun isPictogram(credit: String?): Boolean = credit?.contains("ARASAAC") == true

    fun arasaacSearchUrl(word: String): String =
        "https://api.arasaac.org/v1/pictograms/ca/search/" + encodePath(word.trim())

    fun openverseSearchUrl(query: String): String =
        "https://api.openverse.org/v1/images/?q=" +
            URLEncoder.encode(query.trim(), "UTF-8") +
            "&page_size=$FETCH&mature=false"

    /**
     * Wikimedia Commons, asked for the same word.
     *
     * Added because Openverse on its own was thin: "bread" comes back from it
     * with a couple of hundred candidates, most of them somebody's photograph
     * of a loaf on a table at a wedding, and for an ordinary noun in a
     * vocabulary deck that is not enough to find one good picture in. Commons
     * is the largest freely licensed media collection there is, it is keyless
     * and accountless like everything else this app talks to, and its files are
     * categorised by people — which is both why the results are better and how
     * they can be checked.
     *
     * `filetype:bitmap` keeps out the SVG diagrams and the PDFs; the categories
     * come back with the search so every candidate can be read against the
     * same blocklist Openverse's tags are.
     */
    fun commonsSearchUrl(query: String): String =
        "https://commons.wikimedia.org/w/api.php?action=query&format=json" +
            "&formatversion=2&generator=search&gsrnamespace=6&gsrlimit=$FETCH" +
            "&gsrsearch=" + URLEncoder.encode("filetype:bitmap " + query.trim(), "UTF-8") +
            "&prop=imageinfo%7Ccategories&cllimit=20&iiprop=url%7Cextmetadata" +
            "&iiurlwidth=$COMMONS_THUMBNAIL_PX"

    /**
     * Commons' answer: one page per file, each with a thumbnail already made
     * at the size asked for.
     *
     * The thumbnail matters: the originals on Commons are frequently twenty
     * megapixels, and a grid of twelve of them would be a hundred megabytes
     * over somebody's mobile data. The full-size link is only followed for the
     * one picture actually chosen, and even that is asked for at a width the
     * card can use.
     */
    fun parseCommons(json: String): List<PictureHit> {
        val pages = try {
            JSONObject(json).optJSONObject("query")?.optJSONArray("pages") ?: return emptyList()
        } catch (error: JSONException) {
            return emptyList()
        }
        return (0 until pages.length()).asSequence()
            .mapNotNull { pages.optJSONObject(it) }
            .filterNot { page ->
                PictureSafety.isBlockedPhoto(page.optString("title"), categoriesOf(page))
            }
            .mapNotNull { page ->
                val info = page.optJSONArray("imageinfo")?.optJSONObject(0)
                    ?: return@mapNotNull null
                val thumbnail = info.optString("thumburl").takeIf { it.startsWith("https://") }
                    ?: return@mapNotNull null
                val full = info.optString("url").takeIf { it.startsWith("https://") } ?: thumbnail
                val meta = info.optJSONObject("extmetadata")
                PictureHit(
                    source = PictureSource.PHOTOS,
                    id = "commons:" + page.optString("title"),
                    thumbnailUrl = thumbnail,
                    // Commons will make a copy at any width; asking for one the
                    // card can actually use saves pulling down the original.
                    fullUrl = thumbnail.replace(
                        "/${COMMONS_THUMBNAIL_PX}px-",
                        "/${COMMONS_FULL_PX}px-",
                    ),
                    credit = commonsCredit(
                        creator = stripMarkup(meta?.optJSONObject("Artist")?.optString("value")),
                        license = meta?.optJSONObject("LicenseShortName")?.optString("value")
                            .orEmpty(),
                    ),
                )
            }
            .take(LIMIT)
            .toList()
    }

    private fun categoriesOf(page: JSONObject): List<String> {
        val categories = page.optJSONArray("categories") ?: return emptyList()
        return (0 until categories.length()).mapNotNull {
            categories.optJSONObject(it)?.optString("title")?.removePrefix("Category:")
        }
    }

    /**
     * Commons writes its credits as a fragment of HTML, since they are meant
     * for a web page. A card is not one, so the tags come out and the entities
     * that matter come back.
     */
    fun stripMarkup(html: String?): String = html.orEmpty()
        .replace(HTML_TAG, "")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#039;", "'")
        .replace("&nbsp;", " ")
        .replace(WHITESPACE, " ")
        .trim()

    /** "Evan-Amos · Wikimedia Commons · CC0", leaving out what is unknown. */
    fun commonsCredit(creator: String, license: String): String =
        listOf(creator.takeIf { it.isNotBlank() }, "Wikimedia Commons", license.takeIf { it.isNotBlank() })
            .filterNotNull()
            .joinToString(" · ")

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
            // Openverse's own flag is only as complete as whoever set it, so
            // every photo is also read for itself: its title and its tags.
            .filterNot { PictureSafety.isBlockedPhoto(it.optString("title"), tagsOf(it)) }
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

    private fun tagsOf(photo: JSONObject): List<String> {
        val tags = photo.optJSONArray("tags") ?: return emptyList()
        return (0 until tags.length()).mapNotNull { tags.optJSONObject(it)?.optString("name") }
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
