package com.david.llegeix.data.flashcards

import android.graphics.Bitmap
import android.net.Uri
import androidx.room.withTransaction
import com.david.llegeix.data.db.LlegeixDatabase
import com.david.llegeix.data.db.dao.DeckDue
import com.david.llegeix.data.db.dao.DeckWithCount
import com.david.llegeix.data.db.entity.FlashcardDeckEntity
import com.david.llegeix.data.db.entity.FlashcardEntity
import com.david.llegeix.data.flashcards.FlashcardBackup.toBackup
import com.david.llegeix.data.flashcards.FlashcardBackup.toEntity
import com.david.llegeix.util.runCatchingCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The reader's decks and cards, and the pictures that go with them.
 *
 * Its own repository rather than more methods on the library's: nothing here
 * touches a document, and keeping the two apart means the flashcards can be
 * changed without reading a line of the code that looks after PDFs.
 */
class FlashcardRepository(
    private val database: LlegeixDatabase,
    private val images: FlashcardImages,
    private val backupFiles: FlashcardBackupFiles,
    /** How the reader last chose to practise, remembered between visits. */
    val prefs: FlashcardPrefs,
) {

    private val dao = database.flashcardDao()

    private val swept = AtomicBoolean(false)

    // ---- Decks -------------------------------------------------------------

    fun observeDecks(): Flow<List<DeckWithCount>> = dao.observeDecks()

    fun observeDeck(id: Long): Flow<FlashcardDeckEntity?> = dao.observeDeck(id)

    /**
     * Make a deck, or say why not.
     *
     * The check and the insert share a transaction, so two presses in quick
     * succession cannot both find the name free and both take it.
     */
    suspend fun createDeck(
        name: String,
        coverPath: String? = null,
        coverCredit: String? = null,
    ): DeckNames.Check = database.withTransaction {
        val check = DeckNames.check(name, dao.decks().map { it.name })
        if (check is DeckNames.Check.Ok) {
            dao.insertDeck(
                FlashcardDeckEntity(
                    name = check.name,
                    coverPath = coverPath,
                    coverCredit = coverCredit.takeIf { coverPath != null },
                ),
            )
        }
        check
    }

    suspend fun renameDeck(id: Long, name: String): DeckNames.Check = database.withTransaction {
        val others = dao.decks().filter { it.id != id }.map { it.name }
        val check = DeckNames.check(name, others)
        if (check is DeckNames.Check.Ok) dao.renameDeck(id, check.name)
        check
    }

    /**
     * Delete a deck, its cards and their pictures.
     *
     * The paths are read before the rows go, because afterwards nothing records
     * which files belonged to the deck. The files are deleted only once the rows
     * are: a picture left behind by a failure is wasted space, but a card left
     * pointing at a deleted picture is a card that has lost part of itself.
     */
    suspend fun deleteDeck(id: Long) {
        val paths = database.withTransaction {
            val paths = dao.imagePathsInDeck(id) + listOfNotNull(dao.deck(id)?.coverPath)
            dao.deleteDeck(id)
            paths
        }
        images.delete(paths)
    }

    suspend fun setDeckPinned(id: Long, pinned: Boolean) = dao.setDeckPinned(id, pinned)

    /**
     * Give a deck a picture of its own, or take it away with null.
     *
     * The picture replaced is deleted once the deck no longer points at it,
     * in the same order a card's is.
     */
    suspend fun setDeckCover(id: Long, path: String?, credit: String?) {
        val old = dao.deck(id)?.coverPath
        dao.setDeckCover(id, path, credit.takeIf { path != null })
        if (old != null && old != path) images.delete(listOf(old))
    }

    /**
     * Fill in English for a deck's cards that have none, with [translate].
     *
     * Returns how many were filled. A card the translator cannot do — or hands
     * back unchanged, which is it not knowing the word — is left without, to
     * be written by hand, rather than given a guess dressed as an answer.
     */
    suspend fun fillEnglish(deckId: Long, translate: suspend (String) -> String?): Int {
        var filled = 0
        for (card in dao.cardsWithoutEnglish(deckId)) {
            val english = translate(card.catalan)?.trim()
                ?.takeIf { it.isNotEmpty() && !it.equals(card.catalan.trim(), ignoreCase = true) }
                ?: continue
            dao.setEnglish(card.id, matchLeadingCase(card.catalan, english))
            filled++
        }
        return filled
    }

    suspend fun cardsWithoutEnglish(deckId: Long): Int = dao.cardsWithoutEnglish(deckId).size

    // ---- Cards -------------------------------------------------------------

    /** A deck's cards in Catalan alphabetical order. */
    fun observeCards(deckId: Long): Flow<List<FlashcardEntity>> =
        dao.observeCards(deckId).map(CardOrder::sorted)

    suspend fun card(id: Long): FlashcardEntity? = dao.card(id)

    /**
     * Save what a card says: a new card if [card] has no id yet, otherwise
     * the words and picture of an existing one, leaving its schedule as it is.
     *
     * Returns the card's id.
     */
    suspend fun saveCard(card: FlashcardEntity): Long =
        if (card.id == 0L) {
            dao.insertCard(card)
        } else {
            dao.updateCardContent(
                id = card.id,
                catalan = card.catalan,
                romanian = card.romanian,
                english = card.english,
                ipa = card.ipa,
                ipaApproximate = card.ipaApproximate,
                imagePath = card.imagePath,
                imageCredit = card.imageCredit,
            )
            card.id
        }

    /** Delete a card, then its picture, in that order for the reason [deleteDeck] gives. */
    suspend fun deleteCard(card: FlashcardEntity) {
        dao.deleteCard(card.id)
        card.imagePath?.let { images.delete(listOf(it)) }
    }

    // ---- Study -------------------------------------------------------------

    /**
     * Due counts per deck and direction, as of [now], counting only cards
     * that can be asked in [language].
     */
    fun observeDueCounts(now: Long, language: MeaningLanguage): Flow<List<DeckDue>> =
        dao.observeDueCounts(now, englishOnly = language == MeaningLanguage.ENGLISH)

    /** Every card a session could be dealt from: one deck's, or all of them for null. */
    suspend fun cardsToStudy(deckId: Long?): List<FlashcardEntity> =
        if (deckId == null) dao.allCards() else dao.cardsInDeck(deckId)

    /**
     * Record an answer in [direction], and only there.
     *
     * The schedule comes from [com.david.llegeix.data.practice.Leitner] by way
     * of [StudyDirection.answer]: the flashcards and the saved words are
     * scheduled by the same rule, so a card and a saved word answered the same
     * way come back at the same time.
     */
    suspend fun recordAnswer(
        card: FlashcardEntity,
        direction: StudyDirection,
        correct: Boolean,
        now: Long,
    ) {
        val next = direction.answer(card, correct, now)
        when (direction) {
            StudyDirection.CATALAN_TO_MEANING ->
                dao.recordForward(card.id, next.box, next.dueAt, reviewedAt = now)

            StudyDirection.MEANING_TO_CATALAN ->
                dao.recordReverse(card.id, next.box, next.dueAt, reviewedAt = now)
        }
    }

    // ---- Pictures ----------------------------------------------------------

    /** Copy a picked photo in, shrunk. Returns its stored path. */
    suspend fun importImage(uri: Uri): String = images.import(uri)

    suspend fun loadImage(path: String, maxEdge: Int, cutPaper: Boolean = false): Bitmap? =
        images.load(path, maxEdge, cutPaper)

    suspend fun deleteImage(path: String) = images.delete(listOf(path))

    /** For a form going away with a picture it never saved. */
    fun deleteImageNow(path: String) = images.deleteNow(path)

    /**
     * Clear out pictures no card points at, once per run of the app.
     *
     * Called when the Flashcards tab first opens rather than at launch, so an
     * app opened only to read never touches the database or the folder for it.
     */
    suspend fun sweepImagesOnce() {
        if (!swept.compareAndSet(false, true)) return
        runCatching { images.sweep(dao.allImagePaths().toSet()) }
    }

    // ---- Backup ------------------------------------------------------------

    /**
     * Write every deck, card and picture to [uri] as one copy.
     *
     * Returns how many cards went into it. Pictures are copied as they are
     * stored, under their own file names, since those are already names this
     * app made up.
     */
    suspend fun exportTo(uri: Uri, now: Long = System.currentTimeMillis()): Int {
        val cardsByDeck = dao.allCards().groupBy { it.deckId }
        val pictures = LinkedHashMap<String, File>()
        val decks = dao.decks()
            .sortedBy { it.name.lowercase(Locale.ROOT) }
            .map { deck ->
                val coverEntry = deck.coverPath?.let { path ->
                    (FlashcardBackup.IMAGE_FOLDER + File(path).name).also {
                        pictures[it] = images.fileOf(path)
                    }
                }
                FlashcardBackup.Deck(
                    name = deck.name,
                    createdAt = deck.createdAt,
                    isPinned = deck.isPinned,
                    cover = coverEntry,
                    coverCredit = deck.coverCredit.takeIf { coverEntry != null },
                    cards = CardOrder.sorted(cardsByDeck[deck.id].orEmpty()).map { card ->
                        val entry = card.imagePath?.let { path ->
                            (FlashcardBackup.IMAGE_FOLDER + File(path).name).also {
                                pictures[it] = images.fileOf(path)
                            }
                        }
                        card.toBackup(entry)
                    },
                )
            }
        backupFiles.write(uri, FlashcardBackup.encode(decks, now), pictures)
        return decks.sumOf { it.cards.size }
    }

    /** What a restore did, for the message afterwards. */
    data class RestoreResult(val added: Int, val skipped: Int, val picturesLost: Int)

    /**
     * Add what is in the copy at [uri] to what is already here.
     *
     * Only ever adds; see [FlashcardBackup] for the rules. The pictures are
     * brought in first, each through the same path as a picked photo, and the
     * rows are written afterwards in one transaction — so a copy that fails
     * part-way adds nothing rather than half a deck, and the pictures already
     * brought in for it are deleted again.
     *
     * Throws [FlashcardBackup.UnreadableException] for a file that is not a
     * copy this version understands.
     */
    suspend fun restoreFrom(uri: Uri, now: Long = System.currentTimeMillis()): RestoreResult {
        val unpacked = backupFiles.read(uri)
        try {
            val incoming = FlashcardBackup.decode(unpacked.json)
            val existing = dao.decks().map { deck ->
                FlashcardBackup.ExistingDeck(deck.id, deck.name, dao.cardsInDeck(deck.id))
            }
            val plan = FlashcardBackup.plan(incoming, existing)

            var picturesLost = 0
            val broughtIn = mutableListOf<String>()
            // A deck's own picture comes back only for a deck the copy creates:
            // one already on the phone keeps whatever it has chosen since.
            val covers = plan.decks.filter { it.existingId == null && it.cover != null }.associate { deck ->
                val stored = unpacked.images[deck.cover]?.let { file ->
                    runCatchingCancellable { images.import(Uri.fromFile(file)) }.getOrNull()
                }
                if (stored == null) picturesLost++ else broughtIn += stored
                deck.name to stored
            }
            val prepared = plan.decks.map { deck ->
                deck to deck.cards.map { card ->
                    val source = card.image?.let { unpacked.images[it] }
                    val stored = source?.let { file ->
                        runCatchingCancellable { images.import(Uri.fromFile(file)) }.getOrNull()
                    }
                    // Named in the copy but missing from it, or not a picture:
                    // the card still comes back, without it, and the count says so.
                    if (card.image != null && stored == null) picturesLost++
                    stored?.let(broughtIn::add)
                    card to stored
                }
            }

            try {
                database.withTransaction {
                    for ((deck, cards) in prepared) {
                        val deckId = deck.existingId ?: dao.insertDeck(
                            FlashcardDeckEntity(
                                name = deck.name,
                                createdAt = deck.createdAt.takeIf { it > 0 } ?: now,
                                isPinned = deck.isPinned,
                                coverPath = covers[deck.name],
                                coverCredit = deck.coverCredit.takeIf { covers[deck.name] != null },
                            ),
                        )
                        for ((card, picture) in cards) dao.insertCard(card.toEntity(deckId, picture))
                    }
                }
            } catch (error: Throwable) {
                images.delete(broughtIn)
                throw error
            }
            return RestoreResult(plan.cardCount, plan.skipped, picturesLost)
        } finally {
            backupFiles.discard(unpacked)
        }
    }

    /** Every deck, card and picture, for the wipe in Configuració. */
    suspend fun eraseEverything() {
        database.withTransaction {
            dao.clearCards()
            dao.clearDecks()
        }
        images.deleteAll()
        prefs.clear()
    }
}
