package com.david.llegeix.data.flashcards

import android.graphics.Bitmap
import android.net.Uri
import androidx.room.withTransaction
import com.david.llegeix.data.db.LlegeixDatabase
import com.david.llegeix.data.db.dao.DeckWithCount
import com.david.llegeix.data.db.entity.FlashcardDeckEntity
import com.david.llegeix.data.db.entity.FlashcardEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
    suspend fun createDeck(name: String): DeckNames.Check = database.withTransaction {
        val check = DeckNames.check(name, dao.decks().map { it.name })
        if (check is DeckNames.Check.Ok) {
            dao.insertDeck(FlashcardDeckEntity(name = check.name))
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
            val paths = dao.imagePathsInDeck(id)
            dao.deleteDeck(id)
            paths
        }
        images.delete(paths)
    }

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
                ipa = card.ipa,
                ipaApproximate = card.ipaApproximate,
                imagePath = card.imagePath,
            )
            card.id
        }

    /** Delete a card, then its picture, in that order for the reason [deleteDeck] gives. */
    suspend fun deleteCard(card: FlashcardEntity) {
        dao.deleteCard(card.id)
        card.imagePath?.let { images.delete(listOf(it)) }
    }

    // ---- Pictures ----------------------------------------------------------

    /** Copy a picked photo in, shrunk. Returns its stored path. */
    suspend fun importImage(uri: Uri): String = images.import(uri)

    suspend fun loadImage(path: String, maxEdge: Int): Bitmap? = images.load(path, maxEdge)

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

    /** Every deck, card and picture, for the wipe in Configuració. */
    suspend fun eraseEverything() {
        database.withTransaction {
            dao.clearCards()
            dao.clearDecks()
        }
        images.deleteAll()
    }
}
