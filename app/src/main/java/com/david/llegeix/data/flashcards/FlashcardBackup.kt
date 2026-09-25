package com.david.llegeix.data.flashcards

import com.david.llegeix.data.db.entity.FlashcardEntity
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Locale

/**
 * The flashcards as a file, and the rules for bringing one back.
 *
 * ### Why this exists
 *
 * The app keeps nothing in anybody's cloud: its data is excluded from Android's
 * backup on purpose. That is the right call for a reading history, and the
 * wrong outcome for a deck of cards somebody spent weeks writing by hand, which
 * an uninstall or a lost phone would take with it. A copy the reader saves
 * themselves, to wherever they like, is the only thing standing between those
 * cards and that.
 *
 * ### The file
 *
 * A zip holding [JSON_NAME] and the pictures under [IMAGE_FOLDER]. The JSON is
 * plain on purpose — readable in any text editor, so a copy is not locked
 * inside this app — and carries a [FORMAT] name and a [VERSION] so a later
 * version can read an older copy and an older one can refuse a newer copy
 * instead of misreading it. Each card carries both of its schedules, so a
 * restored deck carries on where it left off rather than starting again.
 *
 * ### Bringing one back
 *
 * Only ever adds. A deck whose name is already taken — ignoring case, as
 * everywhere else — is filled rather than duplicated, and a card whose two
 * sides are already in that deck is skipped. Nothing is overwritten and nothing
 * is deleted: restoring the same copy twice changes nothing the second time,
 * and restoring an old copy over newer cards cannot lose any of them.
 */
object FlashcardBackup {

    const val FORMAT = "llegeix-flashcards"
    const val VERSION = 1
    const val JSON_NAME = "flashcards.json"
    const val IMAGE_FOLDER = "images/"

    data class Card(
        val catalan: String,
        val romanian: String,
        val english: String? = null,
        val ipa: String? = null,
        val ipaApproximate: Boolean = false,
        /** The picture's name inside the zip, e.g. `images/3f2a.jpg`. */
        val image: String? = null,
        /** Who made the picture, when it came from a search. */
        val imageCredit: String? = null,
        val createdAt: Long = 0,
        val box: Int = 0,
        val dueAt: Long = 0,
        val reviewCount: Int = 0,
        val lastReviewedAt: Long? = null,
        val reverseBox: Int = 0,
        val reverseDueAt: Long = 0,
        val reverseReviewCount: Int = 0,
        val reverseLastReviewedAt: Long? = null,
    )

    /**
     * A shelf, as the file records it.
     *
     * Only what a shelf owns: its name, and the picture it was given if it was
     * given one. Which decks are on it is not recorded here — each deck names
     * its own shelf, which is the same fact written once rather than twice and
     * therefore the same fact that cannot disagree with itself.
     */
    data class Collection(
        val name: String,
        val isPinned: Boolean = false,
        /** The picture's name inside the zip, e.g. `images/3f2a.jpg`. */
        val cover: String? = null,
        val coverCredit: String? = null,
        /**
         * The collection this one sits inside, by name, or null for one at the
         * top of the list. Optional like the deck's shelf, and for the same
         * reason: an older version reads a copy with it and simply lays every
         * collection out flat.
         */
        val parent: String? = null,
    )

    data class Deck(
        val name: String,
        val createdAt: Long,
        val cards: List<Card>,
        val isPinned: Boolean = false,
        /** The deck's own picture's name inside the zip, if it chose one. */
        val cover: String? = null,
        val coverCredit: String? = null,
        /**
         * The shelf the deck was on, by name, or null for a deck that was on
         * none.
         *
         * By name rather than by id, like everything else in this file: the ids
         * on the phone a copy is brought back to have nothing to do with the
         * ids on the phone it was made on. A copy naming a collection that does
         * not exist yet makes it; one naming a collection that does joins it.
         *
         * Added without moving [VERSION] on. It is one optional key, and a
         * version older than this one reads a copy containing it perfectly —
         * it simply does not see the shelves, which is a much kinder outcome
         * than refusing the whole file over a grouping.
         */
        val collection: String? = null,
    )

    /** Why a file could not be read as a copy, for the message the reader sees. */
    class UnreadableException(val reason: Reason, cause: Throwable? = null) :
        Exception(reason.name, cause) {
        enum class Reason { NOT_A_BACKUP, TOO_NEW }
    }

    // ---- Writing -----------------------------------------------------------

    fun encode(
        decks: List<Deck>,
        exportedAt: Long,
        collections: List<Collection> = emptyList(),
    ): String {
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("exportedAt", exportedAt)
            .put(
                "collections",
                JSONArray(
                    collections.map { collection ->
                        JSONObject()
                            .put("name", collection.name)
                            .put("pinned", collection.isPinned)
                            .putOpt("cover", collection.cover)
                            .putOpt("coverCredit", collection.coverCredit)
                            .putOpt("parent", collection.parent)
                    },
                ),
            )
            .put(
                "decks",
                JSONArray(
                    decks.map { deck ->
                        JSONObject()
                            .put("name", deck.name)
                            .put("createdAt", deck.createdAt)
                            .put("pinned", deck.isPinned)
                            .putOpt("cover", deck.cover)
                            .putOpt("coverCredit", deck.coverCredit)
                            .putOpt("collection", deck.collection)
                            .put("cards", JSONArray(deck.cards.map(::encodeCard)))
                    },
                ),
            )
        return root.toString(2)
    }

    private fun encodeCard(card: Card): JSONObject = JSONObject()
        .put("catalan", card.catalan)
        .put("romanian", card.romanian)
        .putOpt("english", card.english)
        .putOpt("ipa", card.ipa)
        .put("ipaApproximate", card.ipaApproximate)
        .putOpt("image", card.image)
        .putOpt("imageCredit", card.imageCredit)
        .put("createdAt", card.createdAt)
        .put("box", card.box)
        .put("dueAt", card.dueAt)
        .put("reviewCount", card.reviewCount)
        .putOpt("lastReviewedAt", card.lastReviewedAt)
        .put("reverseBox", card.reverseBox)
        .put("reverseDueAt", card.reverseDueAt)
        .put("reverseReviewCount", card.reverseReviewCount)
        .putOpt("reverseLastReviewedAt", card.reverseLastReviewedAt)

    // ---- Reading -----------------------------------------------------------

    /**
     * The shelves named in [json], by name.
     *
     * Separate from [decode] because they are separate things to put back: a
     * deck is added or filled, a collection is found or made. A copy written
     * before collections existed has none, which decodes to nothing at all and
     * is exactly right.
     */
    fun decodeCollections(json: String): List<Collection> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val listed = root.optJSONArray("collections") ?: return emptyList()
        return (0 until listed.length()).mapNotNull { i ->
            val entry = listed.optJSONObject(i) ?: return@mapNotNull null
            val name = DeckNames.tidy(entry.optString("name"))
            if (name.isEmpty()) return@mapNotNull null
            Collection(
                name = name,
                isPinned = entry.optBoolean("pinned", false),
                cover = entry.optStringOrNull("cover"),
                coverCredit = entry.optStringOrNull("coverCredit"),
                parent = entry.optStringOrNull("parent")?.let(DeckNames::tidy)?.takeIf { it.isNotEmpty() },
            )
        }
    }

    /**
     * The decks in [json], or [UnreadableException] if it is not a copy this
     * version understands.
     *
     * Forgiving about what is missing inside a card — a schedule field absent
     * is a card that starts again, not a copy that cannot be read — and strict
     * about what the file says it is.
     */
    fun decode(json: String): List<Deck> {
        val root = try {
            JSONObject(json)
        } catch (error: JSONException) {
            throw UnreadableException(UnreadableException.Reason.NOT_A_BACKUP, error)
        }
        if (root.optString("format") != FORMAT) {
            throw UnreadableException(UnreadableException.Reason.NOT_A_BACKUP)
        }
        val version = root.optInt("version", -1)
        if (version < 1) throw UnreadableException(UnreadableException.Reason.NOT_A_BACKUP)
        if (version > VERSION) throw UnreadableException(UnreadableException.Reason.TOO_NEW)

        val decks = root.optJSONArray("decks") ?: JSONArray()
        return (0 until decks.length()).mapNotNull { i ->
            val deck = decks.optJSONObject(i) ?: return@mapNotNull null
            val name = DeckNames.tidy(deck.optString("name"))
            if (name.isEmpty()) return@mapNotNull null
            val cards = deck.optJSONArray("cards") ?: JSONArray()
            Deck(
                name = name,
                createdAt = deck.optLong("createdAt", 0),
                isPinned = deck.optBoolean("pinned", false),
                cover = deck.optStringOrNull("cover"),
                coverCredit = deck.optStringOrNull("coverCredit"),
                collection = deck.optStringOrNull("collection")?.let(DeckNames::tidy)
                    ?.takeIf { it.isNotEmpty() },
                cards = (0 until cards.length()).mapNotNull { j ->
                    cards.optJSONObject(j)?.let(::decodeCard)
                },
            )
        }
    }

    private fun decodeCard(card: JSONObject): Card? {
        val catalan = card.optString("catalan").trim()
        val romanian = card.optString("romanian").trim()
        // Both sides are what makes a card; one without either is not one.
        if (catalan.isEmpty() || romanian.isEmpty()) return null
        return Card(
            catalan = catalan,
            romanian = romanian,
            english = card.optStringOrNull("english"),
            ipa = card.optStringOrNull("ipa"),
            ipaApproximate = card.optBoolean("ipaApproximate", false),
            image = card.optStringOrNull("image"),
            imageCredit = card.optStringOrNull("imageCredit"),
            createdAt = card.optLong("createdAt", 0),
            box = card.optInt("box", 0).coerceAtLeast(0),
            dueAt = card.optLong("dueAt", 0),
            reviewCount = card.optInt("reviewCount", 0).coerceAtLeast(0),
            lastReviewedAt = card.optLongOrNull("lastReviewedAt"),
            reverseBox = card.optInt("reverseBox", 0).coerceAtLeast(0),
            reverseDueAt = card.optLong("reverseDueAt", 0),
            reverseReviewCount = card.optInt("reverseReviewCount", 0).coerceAtLeast(0),
            reverseLastReviewedAt = card.optLongOrNull("reverseLastReviewedAt"),
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (isNull(key)) null else optLong(key)

    // ---- Bringing a copy back ----------------------------------------------

    /** A deck already on the phone, as far as merging needs to know it. */
    data class ExistingDeck(val id: Long, val name: String, val cards: List<FlashcardEntity>)

    /**
     * What restoring will do, worked out before anything is written.
     *
     * @property decks one entry per deck that gets cards, in file order, with
     *   the id of the existing deck it fills or null for a new one.
     * @property skipped cards not added because their deck already had them.
     */
    data class Plan(val decks: List<DeckPlan>, val skipped: Int) {
        val cardCount: Int get() = decks.sumOf { it.cards.size }
    }

    data class DeckPlan(
        val name: String,
        val existingId: Long?,
        val createdAt: Long,
        val cards: List<Card>,
        val isPinned: Boolean = false,
        val cover: String? = null,
        val coverCredit: String? = null,
        /**
         * The shelf to put the deck on — only ever for a deck this restore
         * creates.
         *
         * A deck already on the phone keeps where the reader has filed it. A
         * copy is allowed to add things and never to rearrange what is already
         * here, and moving somebody's deck off the shelf they put it on would
         * be exactly that.
         */
        val collection: String? = null,
    )

    fun plan(incoming: List<Deck>, existing: List<ExistingDeck>): Plan {
        // Keyed as DeckNames compares them, so "Menjar" in the copy fills
        // "menjar" on the phone rather than colliding with it.
        val byKey = LinkedHashMap<String, MutableDeck>()
        for (deck in existing) {
            byKey[deckKey(deck.name)] = MutableDeck(
                name = deck.name,
                existingId = deck.id,
                createdAt = 0,
                seen = deck.cards.mapTo(HashSet()) { cardKey(it.catalan, it.romanian) },
            )
        }
        var skipped = 0
        for (deck in incoming) {
            val target = byKey.getOrPut(deckKey(deck.name)) {
                MutableDeck(
                    deck.name,
                    existingId = null,
                    createdAt = deck.createdAt,
                    seen = HashSet(),
                    isPinned = deck.isPinned,
                    cover = deck.cover,
                    coverCredit = deck.coverCredit,
                    collection = deck.collection,
                )
            }
            for (card in deck.cards) {
                // `seen` holds the deck's cards and everything already taken
                // from the copy, so a card listed twice in the file is added once.
                if (target.seen.add(cardKey(card.catalan, card.romanian))) {
                    target.adding += card
                } else {
                    skipped++
                }
            }
        }
        return Plan(
            decks = byKey.values
                .filter { it.adding.isNotEmpty() }
                .map {
                    DeckPlan(
                        name = it.name,
                        existingId = it.existingId,
                        createdAt = it.createdAt,
                        cards = it.adding.toList(),
                        isPinned = it.isPinned,
                        cover = it.cover,
                        coverCredit = it.coverCredit,
                        collection = it.collection.takeIf { _ -> it.existingId == null },
                    )
                },
            skipped = skipped,
        )
    }

    private class MutableDeck(
        val name: String,
        val existingId: Long?,
        val createdAt: Long,
        val seen: MutableSet<String>,
        val adding: MutableList<Card> = mutableListOf(),
        val isPinned: Boolean = false,
        val cover: String? = null,
        val coverCredit: String? = null,
        val collection: String? = null,
    )

    private fun deckKey(name: String): String = DeckNames.tidy(name).lowercase(Locale.ROOT)

    /** The same two sides, ignoring case and stray spaces — not accents, which change words. */
    private fun cardKey(catalan: String, romanian: String): String =
        DeckNames.tidy(catalan).lowercase(Locale.ROOT) + "\u0000" +
            DeckNames.tidy(romanian).lowercase(Locale.ROOT)

    // ---- Between the file and the database ---------------------------------

    fun Card.toEntity(deckId: Long, imagePath: String?): FlashcardEntity = FlashcardEntity(
        deckId = deckId,
        catalan = catalan,
        romanian = romanian,
        english = english,
        ipa = ipa,
        ipaApproximate = ipaApproximate,
        imagePath = imagePath,
        // A credit is only true of the picture it came with.
        imageCredit = imageCredit.takeIf { imagePath != null },
        createdAt = createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
        box = box,
        dueAt = dueAt,
        reviewCount = reviewCount,
        lastReviewedAt = lastReviewedAt,
        reverseBox = reverseBox,
        reverseDueAt = reverseDueAt,
        reverseReviewCount = reverseReviewCount,
        reverseLastReviewedAt = reverseLastReviewedAt,
    )

    fun FlashcardEntity.toBackup(image: String?): Card = Card(
        catalan = catalan,
        romanian = romanian,
        english = english,
        ipa = ipa,
        ipaApproximate = ipaApproximate,
        image = image,
        imageCredit = imageCredit.takeIf { image != null },
        createdAt = createdAt,
        box = box,
        dueAt = dueAt,
        reviewCount = reviewCount,
        lastReviewedAt = lastReviewedAt,
        reverseBox = reverseBox,
        reverseDueAt = reverseDueAt,
        reverseReviewCount = reverseReviewCount,
        reverseLastReviewedAt = reverseLastReviewedAt,
    )
}
