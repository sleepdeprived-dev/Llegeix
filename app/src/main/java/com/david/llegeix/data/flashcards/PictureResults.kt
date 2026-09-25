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

    /**
     * Emoji, drawn large: Google's Noto set as 512-pixel transparent PNGs,
     * then OpenMoji's. Searched by their names and keywords in Catalan (from
     * Unicode's CLDR) and in English, on the phone, from catalogues fetched
     * once per run.
     */
    EMOJI,
}

/** How a picture is drawn, which follows from where it came from. */
enum class PictureKind {
    /** Fills its frame. */
    PHOTO,

    /** A drawing on white paper, shown whole with a little air. */
    PICTOGRAM,

    /** Shown whole and as large as fits, with nothing behind it. */
    EMOJI,
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
    /**
     * What the source says the picture is — its title, tags or categories,
     * and for Wikipedia the article's description — for [PhotoRelevance] to
     * read. Never shown.
     */
    val context: String = "",
    /** The picture's own name — a file name, a photo's title — also for [PhotoRelevance]. */
    val title: String = "",
)

/**
 * The part of picture search that is only text: where to ask, and what the
 * answer means. Kept apart from the network so it can be tested without one.
 */
object PictureResults {

    /**
     * How many to ask each service for.
     *
     * There is no longer a cap on what is shown: the grid used to stop at
     * twenty-four, which threw away pictures a service had already found and
     * the reader might have wanted. Every source is asked for as much as it
     * will give in one anonymous request, and everything that survives the
     * safety check and the duplicate check is offered.
     */
    const val COMMONS_FETCH = 50

    /** Openverse refuses more than twenty to a request made without a key. */
    const val OPENVERSE_PAGE = 20

    /** And so is asked for two pages. */
    const val OPENVERSE_PAGES = 2

    /** Global Symbols refuses a limit above a hundred. */
    const val GLOBAL_SYMBOLS_FETCH = 100

    const val WIKIPEDIA_FETCH = 20

    /**
     * The width Commons is asked to make its thumbnails at, for the grid.
     *
     * Wikimedia only makes thumbnails at a fixed ladder of widths now — 330,
     * 500, 960, 1280 and so on — and answers any other width with an error.
     * Asked for 320 it quietly sent 330, which broke the swap below to the
     * larger size; asked for 800, it refused. So both are rungs of the ladder,
     * and the swap matches whatever width came back.
     */
    private const val COMMONS_THUMBNAIL_PX = 330

    /** And the width the one chosen picture is fetched at. */
    private const val COMMONS_FULL_PX = 960

    /** The width in a Wikimedia thumbnail's file name, "/330px-". */
    private val THUMBNAIL_WIDTH = Regex("/\\d+px-")

    /**
     * The same picture at [COMMONS_FULL_PX] across. A picture smaller than that
     * has no such copy; the download falls back to the thumbnail itself.
     */
    private fun fullSize(thumbnail: String): String =
        thumbnail.replace(THUMBNAIL_WIDTH, "/${COMMONS_FULL_PX}px-")

    private val HTML_TAG = Regex("<[^>]*>")
    private val WHITESPACE = Regex("\\s+")

    const val ARASAAC_CREDIT = "Sergio Palao · ARASAAC · CC BY-NC-SA"

    /**
     * Whether a picture with this credit is a pictogram, and so drawn as one:
     * whole, on its own white paper, with a little air around it.
     *
     * By credit rather than by a flag on the card, because the credit is what a
     * card keeps — the source a picture came from is not stored anywhere else,
     * and cards made before Global Symbols existed carry only ARASAAC's.
     */
    fun isPictogram(credit: String?): Boolean = kindOf(credit) == PictureKind.PICTOGRAM

    /** How a picture with this credit is drawn; see [PictureKind]. */
    fun kindOf(credit: String?): PictureKind = when {
        credit == null -> PictureKind.PHOTO
        credit.contains(NOTO_EMOJI) || credit.contains(OPENMOJI) -> PictureKind.EMOJI
        credit.contains("ARASAAC") || credit.contains(GLOBAL_SYMBOLS) -> PictureKind.PICTOGRAM
        else -> PictureKind.PHOTO
    }

    /** ARASAAC in Catalan, or — for the words it has only labelled in English — "en". */
    fun arasaacSearchUrl(word: String, language: String = "ca"): String =
        "https://api.arasaac.org/v1/pictograms/$language/search/" + encodePath(word.trim())

    /**
     * Global Symbols, asked for the same word in Catalan.
     *
     * A second shelf of pictograms beside ARASAAC's, and a deliberately
     * different one: Global Symbols is an index over some three dozen freely
     * licensed symbol sets — Mulberry, Sclera, Blissymbols, Tawasol and others
     * — which between them draw in styles ARASAAC does not, and label words
     * ARASAAC has not. It needs no key and no account, like everything else
     * here, and it is asked in Catalan, so what comes back is what Catalan
     * speakers labelled rather than a translation of an English label.
     *
     * ARASAAC is in the index too, so its pictograms can come back twice; the
     * duplicate check in [PictureSearch] is what stops the grid showing the
     * same drawing side by side.
     */
    fun globalSymbolsSearchUrl(word: String, language: String = "cat"): String =
        "https://globalsymbols.com/api/v1/labels/search?language=$language" +
            "&limit=$GLOBAL_SYMBOLS_FETCH&query=" +
            URLEncoder.encode(word.trim(), "UTF-8")

    /** The index of symbol sets, which is where a Global Symbols credit comes from. */
    const val GLOBAL_SYMBOLS_SETS_URL = "https://globalsymbols.com/api/v1/symbolsets"

    /**
     * Global Symbols' answer: one entry per label, each carrying its drawing.
     *
     * Only the bitmaps. A fair number of the sets are drawn as SVG, which is
     * the better format and one this app cannot decode: every picture here goes
     * through the same shrink-and-store path a photograph from the reader's own
     * gallery does, and that path reads bitmaps. Offering a thumbnail that
     * cannot be drawn would be worse than offering one fewer.
     *
     * Global Symbols searches by the start of a label, so *pa* also brings back
     * *paciència*, *pala* and *palau*. Given the [query], only labels that have
     * it as a whole word are kept: *pa*, *pa de motllo*, *pa amb tomàquet*.
     *
     * @param setNames symbol set id to its name and licence, from
     *   [parseGlobalSymbolsSets]; the credit says "unknown" about neither, it
     *   simply leaves out what it was not told.
     */
    fun parseGlobalSymbols(
        json: String,
        setNames: Map<Int, String> = emptyMap(),
        query: String? = null,
    ): List<PictureHit> {
        val array = try {
            JSONArray(json)
        } catch (error: JSONException) {
            return emptyList()
        }
        return (0 until array.length()).asSequence()
            .mapNotNull { array.optJSONObject(it) }
            .filter { query == null || hasWholeWord(it.optString("text"), query) }
            .mapNotNull { label ->
                val picto = label.optJSONObject("picto") ?: return@mapNotNull null
                val url = picto.optString("image_url").takeIf { it.startsWith("https://") }
                    ?: return@mapNotNull null
                if (!isDrawableImage(url)) return@mapNotNull null
                val setId = picto.optInt("symbolset_id", -1)
                PictureHit(
                    source = PictureSource.PICTOGRAMS,
                    id = "gs:" + picto.optInt("id", 0),
                    thumbnailUrl = url,
                    fullUrl = url,
                    credit = globalSymbolsCredit(setNames[setId]),
                )
            }
            .toList()
    }

    /**
     * Whether [text] has every word of [query] in it as a word of its own, not
     * as the start of a longer one. Case and accents are ignored, so *Pa* and
     * *pa* are the same word, and so are *cafe* and *cafè*.
     */
    fun hasWholeWord(text: String, query: String): Boolean {
        val words = wordsOf(text).toSet()
        val wanted = wordsOf(query)
        return wanted.isNotEmpty() && wanted.all { it in words }
    }

    private fun wordsOf(text: String): List<String> =
        java.text.Normalizer.normalize(text.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .split(NON_WORD)
            .filter { it.isNotEmpty() }

    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

    /** Whether the app's bitmap decoder stands a chance with this file. */
    private fun isDrawableImage(url: String): Boolean {
        val path = url.substringBefore('?').lowercase(Locale.ROOT)
        return path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") ||
            path.endsWith(".webp")
    }

    /** "Mulberry Symbols · CC BY-SA 4.0 · Global Symbols". */
    fun globalSymbolsCredit(setName: String?): String =
        listOfNotNull(setName?.takeIf { it.isNotBlank() }, GLOBAL_SYMBOLS)
            .joinToString(" · ")

    /** Named once: it is both a credit and the mark that says "this is a pictogram". */
    const val GLOBAL_SYMBOLS = "Global Symbols"

    /** The symbol set index, flattened to what a credit needs: id to name and licence. */
    fun parseGlobalSymbolsSets(json: String): Map<Int, String> {
        val array = try {
            JSONArray(json)
        } catch (error: JSONException) {
            return emptyMap()
        }
        return (0 until array.length()).asSequence()
            .mapNotNull { array.optJSONObject(it) }
            .mapNotNull { set ->
                val id = set.optInt("id", -1).takeIf { it >= 0 } ?: return@mapNotNull null
                val name = set.optString("name").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val licence = set.optJSONObject("licence")?.optString("name")
                id to listOfNotNull(name, licence?.takeIf { it.isNotBlank() }).joinToString(" · ")
            }
            .toMap()
    }

    /**
     * One page of Openverse's answer.
     *
     * Twenty to a page, because that is the most it gives a request without a
     * key: asking for thirty got a refusal, not thirty, and every photo search
     * came back from Openverse empty without anything saying so.
     */
    fun openverseSearchUrl(query: String, page: Int = 1): String =
        "https://api.openverse.org/v1/images/?q=" +
            URLEncoder.encode(query.trim(), "UTF-8") +
            "&page_size=$OPENVERSE_PAGE&page=$page&mature=false"

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
    /**
     * Commons at large, for files with the word in their own name: a file
     * called *Red apples.jpg* is a photo of apples, where one that merely has
     * "apple" somewhere in its description is as likely a banana at a market
     * that also sold them. [hint] is added as a word to rank by, not to require.
     */
    fun commonsSearchUrl(query: String, hint: String? = null): String =
        commonsUrl(
            "filetype:bitmap intitle:\"" + query.trim().replace("\"", "") + "\"" +
                (hint?.let { " $it" } ?: ""),
        )

    /**
     * Commons, asked only inside the category Wikidata files a concept's
     * pictures under — *Limes*, *Prunus persica* — which is people having
     * already sorted the photos of the thing from the photos that mention it.
     */
    fun commonsCategorySearchUrl(category: String): String =
        commonsUrl("filetype:bitmap incategory:\"" + category.replace("\"", "") + "\"")

    private fun commonsUrl(search: String): String =
        "https://commons.wikimedia.org/w/api.php?action=query&format=json" +
            "&formatversion=2&generator=search&gsrnamespace=6&gsrlimit=$COMMONS_FETCH" +
            "&gsrsearch=" + URLEncoder.encode(search, "UTF-8") +
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
            // The pages come back keyed, not ranked; the rank is in "index".
            .sortedBy { it.optInt("index", Int.MAX_VALUE) }
            .filterNot { page ->
                PictureSafety.isBlockedPhoto(page.optString("title"), categoriesOf(page))
            }
            .mapNotNull { page ->
                val info = page.optJSONArray("imageinfo")?.optJSONObject(0)
                    ?: return@mapNotNull null
                val thumbnail = info.optString("thumburl").substringBefore('?')
                    .takeIf { it.startsWith("https://") }
                    ?: return@mapNotNull null
                val full = info.optString("url").takeIf { it.startsWith("https://") } ?: thumbnail
                val meta = info.optJSONObject("extmetadata")
                PictureHit(
                    source = PictureSource.PHOTOS,
                    id = "commons:" + page.optString("title"),
                    thumbnailUrl = thumbnail,
                    // Commons will make a copy at any width; asking for one the
                    // card can actually use saves pulling down the original.
                    fullUrl = fullSize(thumbnail),
                    credit = commonsCredit(
                        creator = stripMarkup(meta?.optJSONObject("Artist")?.optString("value")),
                        license = meta?.optJSONObject("LicenseShortName")?.optString("value")
                            .orEmpty(),
                    ),
                    context = (listOf(page.optString("title")) + categoriesOf(page)).joinToString(" "),
                    title = page.optString("title").removePrefix("File:"),
                )
            }
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
                    context = (listOf(photo.optString("title")) + tagsOf(photo)).joinToString(" "),
                    title = photo.optString("title"),
                )
            }
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

    /**
     * The pictures at the top of Wikipedia's articles for a word.
     *
     * An article's lead picture was chosen by people to show what the article
     * is about, which for a noun is very often exactly the photo a card wants:
     * *Bread* leads with a basket of rolls, *Gos* with a dog. Asked in Catalan
     * with the card's own word as well as in English with its translation, so a
     * word the translator gets wrong still finds its article.
     */
    fun wikipediaSearchUrl(language: String, query: String): String =
        "https://$language.wikipedia.org/w/api.php?action=query&format=json" +
            "&formatversion=2&generator=search&gsrnamespace=0&gsrlimit=$WIKIPEDIA_FETCH" +
            "&gsrsearch=" + URLEncoder.encode(query.trim(), "UTF-8") +
            "&prop=pageimages%7Cdescription%7Cpageprops&ppprop=wikibase_item" +
            "&piprop=thumbnail%7Cname&pithumbsize=$COMMONS_THUMBNAIL_PX"

    /**
     * Wikipedia's answer, in the order the search ranked it.
     *
     * Only pictures kept on Commons: a Wikipedia may hold a few pictures of its
     * own under "fair use", which is a licence to show them in that article and
     * nowhere else. Logos, flags, maps and coats of arms are left out too —
     * they lead the articles on places and companies, and are not a picture
     * of a word.
     */
    fun parseWikipedia(json: String, query: String? = null): List<PictureHit> {
        val pages = try {
            JSONObject(json).optJSONObject("query")?.optJSONArray("pages") ?: return emptyList()
        } catch (error: JSONException) {
            return emptyList()
        }
        return (0 until pages.length()).asSequence()
            .mapNotNull { pages.optJSONObject(it) }
            .sortedBy { it.optInt("index", Int.MAX_VALUE) }
            .mapNotNull { page ->
                val file = page.optString("pageimage").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val thumbnail = page.optJSONObject("thumbnail")?.optString("source")
                    ?.substringBefore('?')
                    ?.takeIf { it.startsWith("https://") && "/wikipedia/commons/" in it }
                    ?: return@mapNotNull null
                val title = page.optString("title")
                val description = page.optString("description")
                if (NOT_A_PICTURE.containsMatchIn(file)) return@mapNotNull null
                // Only articles about the word, and only the kinds of thing a
                // flashcard means: *Apple*, not *Apple Inc.* nor *Fiona Apple*.
                if (query != null) {
                    if (!hasWholeWord(PhotoRelevance.words(title).joinToString(" ") { PhotoRelevance.stem(it) },
                            PhotoRelevance.words(query).joinToString(" ") { PhotoRelevance.stem(it) })
                    ) return@mapNotNull null
                    if (PhotoRelevance.senseScore("$description $title") < 0) return@mapNotNull null
                }
                if (!isDrawableImage(thumbnail)) return@mapNotNull null
                if (PictureSafety.isBlockedPhoto(title, listOf(file))) return@mapNotNull null
                PictureHit(
                    source = PictureSource.PHOTOS,
                    // The same file as a Commons result, so the two are told
                    // apart from each other by the duplicate check.
                    id = "commons:File:" + file.replace('_', ' '),
                    thumbnailUrl = thumbnail,
                    fullUrl = fullSize(thumbnail),
                    credit = listOf(file.substringBeforeLast('.').replace('_', ' '), "Wikimedia Commons")
                        .joinToString(" · "),
                    context = "$title $description ${file.replace('_', ' ')}",
                    title = title,
                )
            }
            .toList()
    }

    /** Where Wikidata keeps a concept's Commons category (property P373). */
    fun wikidataCategoryUrl(wikidataId: String): String =
        "https://www.wikidata.org/w/api.php?action=wbgetclaims&format=json&property=P373&entity=" +
            URLEncoder.encode(wikidataId, "UTF-8")

    private val NOT_A_PICTURE =
        Regex("logo|flag|bandera|map[a_ .-]|mapa|escut|coat.of.arms|\\.svg", RegexOption.IGNORE_CASE)

    /**
     * OpenMoji: four thousand emoji drawn as open, flat pictograms, every one
     * of them under CC BY-SA 4.0 and served as PNG from a public CDN.
     *
     * There is no search service — the whole catalogue is one file of
     * annotations in English, fetched once per run and searched on the phone.
     * Skin-tone variants are left out: a card about *mà* wants the hand, not
     * the same hand five times.
     */
    const val OPENMOJI_DATA_URL =
        "https://cdn.jsdelivr.net/gh/hfg-gmuend/openmoji@15.1.0/data/openmoji.json"

    private const val OPENMOJI_PNG =
        "https://cdn.jsdelivr.net/gh/hfg-gmuend/openmoji@15.1.0/color/618x618/"

    const val OPENMOJI = "OpenMoji"
    const val OPENMOJI_CREDIT = "$OPENMOJI · CC BY-SA 4.0"

    /** One OpenMoji, reduced to what a search needs. */
    data class OpenMoji(val hexcode: String, val annotation: String, val tags: String)

    fun parseOpenMojiIndex(json: String): List<OpenMoji> {
        val array = try {
            JSONArray(json)
        } catch (error: JSONException) {
            return emptyList()
        }
        return (0 until array.length()).mapNotNull { index ->
            val emoji = array.optJSONObject(index) ?: return@mapNotNull null
            if (emoji.optString("skintone").isNotEmpty()) return@mapNotNull null
            val hexcode = emoji.optString("hexcode").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            OpenMoji(
                hexcode = hexcode,
                annotation = emoji.optString("annotation"),
                tags = emoji.optString("tags") + ", " + emoji.optString("openmoji_tags"),
            )
        }
    }

    /**
     * The OpenMoji whose name or tags have [english] as whole words: the ones
     * named for it first — *bread* before *sandwich*, which is only tagged
     * with it — and never more than a handful, since past that they are
     * things that merely go with the word.
     */
    fun openMojiMatches(index: List<OpenMoji>, english: String): List<PictureHit> {
        if (wordsOf(english).isEmpty()) return emptyList()
        val named = index.filter { hasWholeWord(it.annotation, english) }
        val tagged = index.filter { it !in named && hasWholeWord(it.tags, english) }
        return (named + tagged).take(OPENMOJI_MAX).map { emoji ->
            val url = OPENMOJI_PNG + emoji.hexcode + ".png"
            PictureHit(
                source = PictureSource.EMOJI,
                id = "openmoji:" + emoji.hexcode,
                thumbnailUrl = url,
                fullUrl = url,
                credit = OPENMOJI_CREDIT,
            )
        }
    }

    private const val OPENMOJI_MAX = 12

    // ---- Noto emoji --------------------------------------------------------

    const val NOTO_EMOJI = "Noto Emoji"
    const val NOTO_CREDIT = "$NOTO_EMOJI · Google · Apache 2.0"

    /** Every emoji with its English name and tags, from emojibase. */
    const val EMOJI_ENGLISH_URL = "https://cdn.jsdelivr.net/npm/emojibase-data@16/en/compact.json"

    /** Their Catalan names and keywords, from Unicode's CLDR. */
    const val EMOJI_CATALAN_URL =
        "https://cdn.jsdelivr.net/npm/cldr-annotations-full@46/annotations/ca/annotations.json"

    private const val NOTO_PNG = "https://cdn.jsdelivr.net/gh/googlefonts/noto-emoji@v2.047/png/"

    /** One emoji, with what it is called in both languages it is searched in. */
    data class Emoji(
        val hexcode: String,
        val english: String,
        val englishTags: List<String>,
        val catalan: String,
        val catalanKeywords: List<String>,
        val order: Int,
    )

    /**
     * The two catalogues, joined on the emoji itself. Skin-tone variants,
     * components and flags are left out: flags are named differently in
     * Noto's files, and the rest are the same picture again.
     */
    fun parseEmoji(englishJson: String, catalanJson: String): List<Emoji> {
        val catalan = try {
            JSONObject(catalanJson).optJSONObject("annotations")?.optJSONObject("annotations")
        } catch (error: JSONException) {
            null
        }
        val english = try {
            JSONArray(englishJson)
        } catch (error: JSONException) {
            return emptyList()
        }
        return (0 until english.length()).mapNotNull { index ->
            val entry = english.optJSONObject(index) ?: return@mapNotNull null
            if (!entry.has("group")) return@mapNotNull null
            val group = entry.optInt("group", -1)
            if (group == 2 || group == 9) return@mapNotNull null
            val char = entry.optString("unicode").replace("\uFE0F", "")
            val hexcode = entry.optString("hexcode").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val names = catalan?.optJSONObject(char)
            fun list(json: JSONArray?) = json?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
            Emoji(
                hexcode = hexcode,
                english = entry.optString("label"),
                englishTags = list(entry.optJSONArray("tags")),
                catalan = list(names?.optJSONArray("tts")).firstOrNull().orEmpty(),
                catalanKeywords = list(names?.optJSONArray("default")),
                order = entry.optInt("order", Int.MAX_VALUE),
            )
        }
    }

    /**
     * The emoji for a word, best first: one *named* the word, in Catalan or in
     * English, before one that only has it among its keywords — *poma
     * vermella* before the fruit basket that lists *poma*.
     */
    fun emojiMatches(index: List<Emoji>, catalan: String, english: String?): List<PictureHit> {
        fun stems(text: String) = PhotoRelevance.words(text).map(PhotoRelevance::stem)
        val ca = stems(catalan)
        val en = english?.let { stems(stripArticles(it)) }.orEmpty()
        fun has(text: String, wanted: List<String>) =
            wanted.isNotEmpty() && stems(text).toSet().containsAll(wanted)
        fun equal(text: String, wanted: List<String>) = wanted.isNotEmpty() && stems(text) == wanted
        return index.asSequence()
            .map { emoji ->
                val score = when {
                    equal(emoji.catalan, ca) || equal(emoji.english, en) -> 6
                    has(emoji.catalan, ca) || has(emoji.english, en) -> 4
                    emoji.catalanKeywords.any { equal(it, ca) } || emoji.englishTags.any { equal(it, en) } -> 2
                    emoji.catalanKeywords.any { has(it, ca) } -> 1
                    else -> 0
                }
                emoji to score
            }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Emoji, Int>> { it.second }.thenBy { it.first.order })
            .take(EMOJI_MAX)
            .map { (emoji, _) ->
                val name = emoji.hexcode.lowercase(Locale.ROOT).split('-').filter { it != "fe0f" }
                    .joinToString("_")
                PictureHit(
                    source = PictureSource.EMOJI,
                    id = "noto:" + emoji.hexcode,
                    thumbnailUrl = NOTO_PNG + "128/emoji_u$name.png",
                    fullUrl = NOTO_PNG + "512/emoji_u$name.png",
                    credit = NOTO_CREDIT,
                )
            }
            .toList()
    }

    private const val EMOJI_MAX = 30

    /**
     * "eat" from "to eat", "apple" from "an apple": the English meaning as a
     * card holds it, without the words that only say what part of speech it
     * is — which searched for literally find photos of the word "to".
     */
    fun stripArticles(english: String): String {
        val trimmed = english.trim()
        val lower = trimmed.lowercase(Locale.ROOT)
        for (prefix in listOf("to ", "a ", "an ", "the ")) {
            if (lower.startsWith(prefix) && trimmed.length > prefix.length) return trimmed.substring(prefix.length).trim()
        }
        return trimmed
    }

    /** A path segment, where a space is %20 rather than the query string's +. */
    private fun encodePath(segment: String): String =
        URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
}
