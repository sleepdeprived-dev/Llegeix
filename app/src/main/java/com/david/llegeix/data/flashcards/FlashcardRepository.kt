package com.david.llegeix.data.flashcards

import androidx.room.withTransaction
import com.david.llegeix.data.db.LlegeixDatabase
import com.david.llegeix.data.db.dao.DeckWithCount
import com.david.llegeix.data.db.entity.FlashcardDeckEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The reader's decks and cards, and the pictures that go with them.
 *
 * Its own repository rather than more methods on the library's: nothing here
 * touches a document, and keeping the two apart means the flashcards can be
 * changed without reading a line of the code that looks after PDFs.
 *
 * @param filesDir the app's private files directory. Card pictures live in
 *   [IMAGE_DIRECTORY] under it, and the database stores paths relative to it,
 *   so nothing breaks if Android ever moves the app's storage.
 */
class FlashcardRepository(
    private val database: LlegeixDatabase,
    private val filesDir: File,
) {

    private val dao = database.flashcardDao()

    fun observeDecks(): Flow<List<DeckWithCount>> = dao.observeDecks()

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
        deleteImages(paths)
    }

    /** Every deck, card and picture, for the wipe in Configuració. */
    suspend fun eraseEverything() {
        database.withTransaction {
            dao.clearCards()
            dao.clearDecks()
        }
        withContext(Dispatchers.IO) {
            runCatching { File(filesDir, IMAGE_DIRECTORY).deleteRecursively() }
        }
    }

    private suspend fun deleteImages(paths: List<String>) = withContext(Dispatchers.IO) {
        for (path in paths) {
            runCatching { File(filesDir, path).delete() }
        }
    }

    companion object {
        /** Where card pictures are kept, under the app's files directory. */
        const val IMAGE_DIRECTORY = "flashcards"
    }
}
