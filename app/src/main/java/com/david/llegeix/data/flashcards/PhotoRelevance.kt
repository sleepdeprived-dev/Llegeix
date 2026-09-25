package com.david.llegeix.data.flashcards

import org.json.JSONException
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/**
 * Whether a photo is a picture of the word, as a vocabulary card means it.
 *
 * The services photos come from answer "what matches these letters", which is
 * a different question. Flickr, through Openverse, tags a bra "peach" and a
 * man in a green shirt "lime", because those are colours; *apple* is a company
 * before it is a fruit on half the internet; and a town that happens to share
 * a word's name brings its whole skyline along. A flashcard wants the fruit.
 *
 * Two things are done about it, both from text the services already send:
 *
 * - **Which sense.** Wikipedia's search, with each article's one-line
 *   description, says what a word most often *is*: "Edible fruit",
 *   "American multinational technology company", "Video game character". The
 *   first article that is plainly the kind of thing vocabulary is made of —
 *   a fruit, an animal, a dish, a colour, a tool — is the [Concept], and its
 *   description's noun ("fruit") and its Commons category ("Limes") steer the
 *   searches that follow.
 * - **Each photo, scored.** Its title, tags and categories are read for the
 *   word, for that noun, and for what vocabulary photos are not: people posing,
 *   underwear, logos, landscapes and skylines. People and underwear are left
 *   out unless the word is itself about people; logos and scenery go to the end.
 */
object PhotoRelevance {

    /** The sense of a word a flashcard most likely means. */
    data class Concept(
        /** The article's title, e.g. "Lime (fruit)". */
        val title: String,
        val description: String,
        /**
         * A plain noun from the description that sharpens a photo search —
         * "fruit" for *lime* — or null when the description has none worth
         * adding (a dog's "Domesticated species of canid" would only narrow it).
         */
        val hint: String?,
        /** Wikidata's id for it, which leads to its Commons category. */
        val wikidataId: String?,
    )

    // ---- Which sense -------------------------------------------------------

    /**
     * The best vocabulary sense among Wikipedia's answers for [query], or null
     * when none of them is one — in which case the search goes ahead unsteered.
     *
     * An article qualifies when its title is the word (plurals and a
     * parenthesis like "(fruit)" allowed) and its description does not say it
     * is a company, a person, a work, a place or a disambiguation page. Among
     * those, one whose description names a vocabulary kind of thing wins over
     * one that merely is not a bad kind; after that, Wikipedia's own order.
     */
    fun concept(json: String, query: String): Concept? {
        val pages = try {
            JSONObject(json).optJSONObject("query")?.optJSONArray("pages") ?: return null
        } catch (error: JSONException) {
            return null
        }
        val wanted = stems(query)
        if (wanted.isEmpty()) return null
        return (0 until pages.length()).asSequence()
            .mapNotNull { pages.optJSONObject(it) }
            .sortedBy { it.optInt("index", Int.MAX_VALUE) }
            .mapNotNull { page ->
                val title = page.optString("title")
                val description = page.optString("description")
                val base = title.substringBefore(" (")
                if (stems(base) != wanted) return@mapNotNull null
                val kind = senseScore(description + " " + title.substringAfter(" (", ""))
                if (kind < 0) return@mapNotNull null
                val id = page.optJSONObject("pageprops")?.optString("wikibase_item")
                    ?.takeIf { it.isNotBlank() }
                kind to Concept(title, description, hintIn(description), id)
            }
            .sortedByDescending { it.first }
            .firstOrNull()
            ?.second
    }

    /**
     * Positive when a description names a thing vocabulary is made of,
     * negative when it names something a flashcard never means, 0 otherwise.
     */
    fun senseScore(description: String): Int {
        val words = words(description)
        val text = " " + words.joinToString(" ") + " "
        if (NOT_VOCABULARY_PHRASES.any { " $it " in text }) return -1
        if (words.any { it in NOT_VOCABULARY }) return -1
        return if (words.any { it in VOCABULARY }) 1 else 0
    }

    private fun hintIn(description: String): String? =
        words(description).firstOrNull { it in HINTS }

    /** The Commons category Wikidata files the concept's pictures under, from `wbgetclaims`. */
    fun commonsCategory(json: String): String? = try {
        JSONObject(json).optJSONObject("claims")?.optJSONArray("P373")
            ?.optJSONObject(0)?.optJSONObject("mainsnak")?.optJSONObject("datavalue")
            ?.optString("value")?.takeIf { it.isNotBlank() }
    } catch (error: JSONException) {
        null
    }

    // ---- Each photo --------------------------------------------------------

    /**
     * How well a photo fits [query] as a vocabulary picture, or null when it
     * should not be offered at all.
     *
     * @param title the photo's own name. The word there counts most: a file
     *   named for the thing is a photo of it.
     * @param text everything the source says about it — title, tags,
     *   categories, description.
     */
    fun score(text: String, query: String, hint: String?, title: String = ""): Int? {
        val words = words(text)
        val stemmed = words.map(::stem).toSet()
        val inTitle = words(title).map(::stem).toSet()
        val wanted = stems(query)
        val aboutPeople = words(query).any { it in PEOPLE }
        var score = 0
        if (wanted.isNotEmpty()) {
            score += when {
                wanted.all { it in inTitle } -> 4
                wanted.all { it in stemmed } -> 2
                else -> 0
            }
        }
        if (hint != null) {
            val h = stem(hint)
            score += when {
                h in inTitle -> 2
                h in stemmed -> 1
                else -> 0
            }
            // A fruit or a vegetable is wanted as one: the tree it grows on,
            // its blossom and the orchard are the same species and the wrong
            // picture.
            if (h in PRODUCE && words.any { it in PLANT_PARTS }) score -= 2
        }
        if (!aboutPeople) {
            if (words.any { it in UNDERWEAR }) return null
            if (words.any { it in PEOPLE }) score -= 4
        }
        if (words.any { it in BRANDS }) score -= 3
        if (words.any { it in SCENERY }) score -= 2
        if (words.any { it in UNAPPEALING }) score -= 2
        if (words.any { it in ARTWORK }) score -= 2
        if (!aboutPeople && words.any { it in EVENTS }) score -= 3
        if (words.any { it in CLEAN }) score += 1
        return score
    }

    /**
     * [hits] best first: dropped where [score] says so, then by score, with
     * the order they came in — already interleaved between the sources — kept
     * among equals.
     *
     * @param strict leave out anything that does not mention the word at all.
     *   Openverse's tags are free-for-all, and a photo there that never says
     *   the word is almost never a photo of it.
     * @param vouched for pictures people have already filed under the concept
     *   — its Commons category, its article's own picture — which go ahead of
     *   anything that only matches by its words.
     */
    fun rank(
        hits: List<PictureHit>,
        query: String,
        hint: String?,
        strict: (PictureHit) -> Boolean = { false },
        vouched: (PictureHit) -> Boolean = { false },
    ): List<PictureHit> {
        val wanted = stems(query)
        return hits.withIndex()
            .mapNotNull { (index, hit) ->
                var score = score(hit.context, query, hint, hit.title) ?: return@mapNotNull null
                if (strict(hit) && wanted.isNotEmpty()) {
                    val stemmed = words(hit.context).map(::stem).toSet()
                    if (!wanted.all { it in stemmed }) return@mapNotNull null
                }
                if (vouched(hit)) score += 2
                Triple(hit, score, index)
            }
            .sortedWith(compareByDescending<Triple<PictureHit, Int, Int>> { it.second }.thenBy { it.third })
            .map { it.first }
    }

    // ---- Words -------------------------------------------------------------

    /** Lower case, accents off, split on anything that is not a letter or digit. */
    fun words(text: String): List<String> =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(MARKS, "")
            .split(NON_WORD)
            .filter { it.isNotEmpty() }

    private fun stems(text: String): List<String> = words(text).map(::stem)

    /** "limes" and "lime", "peaches" and "peach", "cherries" and "cherry" alike. */
    fun stem(word: String): String = when {
        word.length > 4 && word.endsWith("ies") -> word.dropLast(3) + "y"
        word.length > 4 && (word.endsWith("ches") || word.endsWith("shes") || word.endsWith("xes") ||
            word.endsWith("sses")) -> word.dropLast(2)
        word.length > 3 && word.endsWith("s") && !word.endsWith("ss") -> word.dropLast(1)
        else -> word
    }

    private val MARKS = Regex("\\p{Mn}+")
    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

    /** Kinds of thing a vocabulary deck is made of, in English and Catalan. */
    private val VOCABULARY = setOf(
        "fruit", "fruits", "vegetable", "vegetables", "plant", "tree", "flower", "herb", "spice",
        "species", "genus", "animal", "mammal", "bird", "fish", "insect", "reptile", "amphibian",
        "breed", "food", "dish", "dessert", "drink", "beverage", "bread", "cheese", "meat", "pastry",
        "cake", "sauce", "soup", "color", "colour", "number", "numeral", "tool", "utensil", "furniture",
        "vehicle", "garment", "clothing", "footwear", "toy", "instrument", "body", "organ", "household",
        "container", "appliance", "weather", "season", "emotion", "sport", "occupation", "profession",
        "shape", "edible", "cereal", "grain", "nut", "berry", "citrus", "legume", "mushroom",
        // Catalan, for the Viquipèdia's descriptions.
        "fruita", "verdura", "hortalissa", "planta", "arbre", "flor", "especie", "animal", "mamifer",
        "ocell", "peix", "insecte", "raca", "aliment", "menjar", "plat", "postres", "beguda", "color",
        "eina", "moble", "vehicle", "joguina", "instrument",
    )

    /** Nouns worth adding to a photo search to pin the sense down. */
    private val HINTS = setOf(
        "fruit", "vegetable", "flower", "tree", "herb", "spice", "bird", "fish", "insect", "food",
        "dish", "dessert", "drink", "beverage", "bread", "cheese", "pastry", "cake", "color", "colour",
        "tool", "utensil", "furniture", "vehicle", "garment", "toy", "instrument", "mushroom", "nut",
        "berry", "citrus",
    )

    /** What a word on a flashcard almost never means. */
    private val NOT_VOCABULARY = setOf(
        "company", "corporation", "manufacturer", "brand", "retailer", "airline", "bank", "software",
        "website", "app", "film", "movie", "album", "single", "song", "band", "musician", "singer",
        "rapper", "actor", "actress", "politician", "footballer", "player", "surname", "character",
        "novel", "magazine", "newspaper", "television", "series", "episode", "city", "town", "village",
        "municipality", "commune", "county", "province", "region", "country", "river", "lake",
        "mountain", "island", "station", "airport", "street", "castle", "fortification", "fortifications",
        "frontier", "disambiguation", "label", "team", "club", "university", "ship", "emoji", "poem",
        "chemical", "compound", "mineral",
        // Catalan.
        "empresa", "companyia", "municipi", "poble", "ciutat", "riu", "pellicula", "album", "canco",
        "grup", "cantant", "actriu", "futbolista", "politic", "cognom", "personatge", "novella",
        "desambiguacio", "estacio", "videojoc",
    )

    private val NOT_VOCABULARY_PHRASES = listOf(
        "topics referred to", "given name", "video game", "operating system", "record label",
        "list of", "born", "pagina de desambiguacio",
    )

    /** People posing, which is most of what goes wrong. */
    private val PEOPLE = setOf(
        "man", "men", "woman", "women", "girl", "girls", "boy", "boys", "person", "people", "portrait",
        "selfie", "model", "models", "fashion", "face", "lady", "guy", "couple", "wedding", "bride",
        "actor", "actress", "singer", "musician", "concert", "festival", "party", "celebrity",
        "politician", "mayor", "president", "cosplay", "tattoo", "nude", "sexy", "posing", "pose",
        "baby", "child", "children", "kid", "kids", "family", "teacher", "doctor", "student",
    )

    /** Never on a card, whatever the word. */
    private val UNDERWEAR = setOf(
        "bra", "bras", "lingerie", "underwear", "panties", "thong", "bikini", "swimsuit", "corset",
        "stockings", "boudoir", "erotic", "topless",
    )

    private val BRANDS = setOf(
        "logo", "logos", "iphone", "ipad", "macbook", "trademark", "advertisement", "advert", "brand",
        "screenshot", "wordmark",
    )

    /** The thing, but not how a card should show it: rotten, diseased, or a lab chart. */
    private val UNAPPEALING = setOf(
        "rotten", "rotting", "rot", "decay", "decaying", "decayed", "mold", "mould", "moldy", "mouldy",
        "disease", "diseased", "damaged", "damage", "pest", "pests", "blight", "fungus", "infection",
        "electrophoresis", "gel", "diagram", "chart", "graph", "micrograph", "microscope", "herbarium",
        "specimen", "dead", "waste", "garbage", "litter",
    )

    /** A picture *of* a picture or a carving of the thing, rather than the thing. */
    private val ARTWORK = setOf(
        "sculpture", "statue", "statues", "carving", "carvings", "relief", "painting", "paintings",
        "manuscript", "museum", "mural", "fresco", "engraving", "stamp", "coin", "tapestry", "mosaic",
        "monument", "pillar", "temple", "ruins", "tomb", "exhibit", "exhibition", "collage",
    )

    /** Somebody's event: a talk, a signing, a conference — people again, by another name. */
    private val EVENTS = setOf(
        "author", "reading", "signing", "book", "bookstore", "interview", "conference", "talk",
        "lecture", "meeting", "speaker", "panel", "award", "ceremony", "premiere",
    )

    /** The thing shown plainly, as a card wants it. */
    private val CLEAN = setOf(
        "fresh", "ripe", "isolated", "whole", "closeup", "close", "studio", "white", "background",
    )

    private val PRODUCE = setOf("fruit", "vegetable", "berry", "nut", "citrus", "food", "mushroom")

    private val PLANT_PARTS = setOf(
        "tree", "trees", "blossom", "blossoms", "flower", "flowers", "flowering", "bloom", "blooming",
        "orchard", "orchards", "leaf", "leaves", "seedling", "bark", "branch", "branches", "habit",
        "blute", "bluten", "floracion", "flor",
    )

    /** Pictures of where rather than of what. */
    private val SCENERY = setOf(
        "landscape", "skyline", "panorama", "aerial", "cityscape", "street", "town", "village", "city",
        "church", "cathedral", "castle", "building", "architecture", "station", "bridge", "harbour",
        "harbor", "mountains", "valley", "map",
    )
}
