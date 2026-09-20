package com.david.llegeix.ui.flashcards

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.david.llegeix.LlegeixApp
import com.david.llegeix.R
import com.david.llegeix.data.db.dao.DeckWithCount
import com.david.llegeix.data.db.entity.FlashcardCollectionEntity
import com.david.llegeix.data.flashcards.DeckNames
import com.david.llegeix.data.flashcards.FlashcardBackup
import com.david.llegeix.data.flashcards.FlashcardRepository
import com.david.llegeix.data.flashcards.MeaningLanguage
import com.david.llegeix.data.flashcards.StudyDirection
import com.david.llegeix.ui.common.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A collection and the decks on it, as the list draws them.
 *
 * The counts are added up here rather than asked of SQL, because the decks
 * have already been loaded with theirs and a second query would have to repeat
 * the first one's cover logic word for word to get the same answers.
 */
data class DeckShelf(
    val collection: FlashcardCollectionEntity,
    val decks: List<DeckWithCount>,
) {
    val id: Long get() = collection.id
    val cardCount: Int get() = decks.sumOf { it.cardCount }
    val englishCount: Int get() = decks.sumOf { it.englishCount }

    /**
     * The picture that stands for the shelf: the one it was given, or failing
     * that the first picture any deck on it has.
     *
     * The borrowed one is the older behaviour and stays the default, because it
     * is usually right and costs nobody a decision — *Food* showing the
     * vegetables is a perfectly good *Food*. What it could not do was be
     * overruled, and a shelf that had quietly settled on the parsley from
     * *Herbs* had no way to be told otherwise.
     */
    val coverImage: String?
        get() = collection.coverPath ?: decks.firstNotNullOfOrNull { it.coverImage }

    val coverCredit: String?
        get() = if (collection.coverPath != null) {
            collection.coverCredit
        } else {
            decks.firstOrNull { it.coverImage != null }?.coverCredit
        }

    /** Only the chosen picture, so the menu knows whether there is one to remove. */
    val chosenCover: String? get() = collection.coverPath
}

/**
 * The tab's list: the shelves, then the decks that are on no shelf.
 *
 * Null until the database has answered, which is what stops the screen
 * flashing "no decks yet" for a frame on every visit.
 */
data class DeckList(
    val shelves: List<DeckShelf>,
    val loose: List<DeckWithCount>,
) {
    val allDecks: List<DeckWithCount> get() = shelves.flatMap { it.decks } + loose

    val isEmpty: Boolean get() = shelves.isEmpty() && loose.isEmpty()

    val hasCards: Boolean get() = allDecks.any { it.cardCount > 0 }
}

/**
 * The list of decks and the shelves they sit on, and making, renaming and
 * deleting either.
 */
class FlashcardsViewModel(
    private val flashcards: FlashcardRepository,
) : ViewModel() {

    /**
     * Null until the first answer from the database.
     *
     * Not an empty list: the screen would otherwise flash "no decks yet" for a
     * frame on every visit before the real list arrived.
     */
    val decks: StateFlow<List<DeckWithCount>?> = flashcards.observeDecks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val collections: StateFlow<List<FlashcardCollectionEntity>?> =
        flashcards.observeCollections()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Which shelves are open.
     *
     * Kept here rather than in the screen so that going into a deck and coming
     * back does not fold everything shut again — which is the one thing that
     * would make a shelf feel like a place you are put rather than one you
     * opened.
     */
    private val _openShelves = MutableStateFlow<Set<Long>>(emptySet())
    val openShelves: StateFlow<Set<Long>> = _openShelves.asStateFlow()

    /** The decks grouped under the shelves they are on, in the order both are listed. */
    val list: StateFlow<DeckList?> = combine(decks, collections) { decks, collections ->
        if (decks == null || collections == null) return@combine null
        val byCollection = decks.groupBy { it.collectionId }
        DeckList(
            // A shelf with nothing on it is still listed: it was made on
            // purpose, and a collection that vanished until something was put
            // on it would look like the app having forgotten it.
            shelves = collections.map { DeckShelf(it, byCollection[it.id].orEmpty()) },
            loose = byCollection[null].orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onToggleShelf(id: Long) {
        _openShelves.update { if (id in it) it - id else it + id }
    }

    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    // ---- What to study -----------------------------------------------------

    private val _direction = MutableStateFlow(flashcards.prefs.direction)
    val direction: StateFlow<StudyDirection> = _direction.asStateFlow()

    /** Which language the meanings are practised in. */
    private val _language = MutableStateFlow(flashcards.prefs.language)
    val language: StateFlow<MeaningLanguage> = _language.asStateFlow()

    fun onChooseDirection(direction: StudyDirection) {
        _direction.value = direction
        flashcards.prefs.direction = direction
    }

    fun onChooseLanguage(language: MeaningLanguage) {
        _language.value = language
        flashcards.prefs.language = language
    }

    /**
     * Both halves of "which languages, and which way round", set together.
     *
     * They were two controls and are now one menu of four, because they were
     * never really two questions: nobody wants Catalan→English asked in
     * Romanian. Setting them one at a time through the old pair meant passing
     * through a combination nobody chose, however briefly.
     */
    fun onChoosePair(direction: StudyDirection, language: MeaningLanguage) {
        onChooseDirection(direction)
        onChooseLanguage(language)
    }

    fun setPinned(deck: DeckWithCount, pinned: Boolean) = viewModelScope.launch {
        flashcards.setDeckPinned(deck.id, pinned)
    }

    // ---- Collections -------------------------------------------------------

    fun setCollectionPinned(id: Long, pinned: Boolean) = viewModelScope.launch {
        flashcards.setCollectionPinned(id, pinned)
    }

    /** Whether [name] could be used for a collection; see [checkName] for a deck's. */
    fun checkCollectionName(name: String, renaming: Long? = null): DeckNames.Check {
        val others = list.value?.shelves.orEmpty()
            .filter { it.id != renaming }
            .map { it.collection.name }
        return DeckNames.check(name, others)
    }

    fun renameCollection(id: Long, name: String) = viewModelScope.launch {
        report(flashcards.renameCollection(id, name))
    }

    /**
     * Take a shelf away. The decks on it stay, and the message says so, because
     * "delete" next to a thing holding sixty cards is worth being explicit
     * about.
     */
    fun deleteCollection(shelf: DeckShelf) = viewModelScope.launch {
        flashcards.deleteCollection(shelf.id)
        _openShelves.update { it - shelf.id }
        _message.value = UiText.of(R.string.flashcards_collection_deleted, shelf.collection.name)
    }

    /** Put a deck on a shelf, or take it off one with null. */
    fun moveDeck(deck: DeckWithCount, collectionId: Long?) = viewModelScope.launch {
        flashcards.setDeckCollection(deck.id, collectionId)
        // Filing something into a shelf that is folded shut looks like the deck
        // disappearing, so the shelf it went onto opens.
        if (collectionId != null) _openShelves.update { it + collectionId }
    }

    init {
        viewModelScope.launch { flashcards.sweepImagesOnce() }
    }

    /**
     * Whether [name] could be used, asked on every keystroke by the dialog.
     *
     * So a clash is said under the field while the reader is still typing,
     * rather than after they have pressed the button and the dialog has gone.
     *
     * @param renaming the deck being renamed, which is not a clash with itself.
     */
    fun checkName(name: String, renaming: Long? = null): DeckNames.Check {
        val others = decks.value.orEmpty().filter { it.id != renaming }.map { it.name }
        return DeckNames.check(name, others)
    }

    fun renameDeck(id: Long, name: String) = viewModelScope.launch {
        report(flashcards.renameDeck(id, name))
    }

    fun deleteDeck(deck: DeckWithCount) = viewModelScope.launch {
        flashcards.deleteDeck(deck.id)
        _message.value = UiText.of(R.string.flashcards_deck_deleted, deck.name)
    }

    /**
     * Only reached when the list moved between the dialog's check and the
     * write — the dialog does not let a clash through otherwise.
     */
    private fun report(check: DeckNames.Check) {
        if (check is DeckNames.Check.Taken) {
            _message.value = UiText.of(R.string.flashcards_deck_exists, check.existing)
        }
    }

    // ---- Backup ------------------------------------------------------------

    /** A copy is being written or read; the buttons wait for it. */
    private val _backupBusy = MutableStateFlow(false)
    val backupBusy: StateFlow<Boolean> = _backupBusy.asStateFlow()

    fun onExport(uri: Uri) = backup {
        val cards = flashcards.exportTo(uri)
        UiText.ofPlural(R.plurals.flashcards_backup_saved, cards)
    }

    fun onRestore(uri: Uri) = backup {
        val result = flashcards.restoreFrom(uri)
        if (result.added == 0) {
            UiText.Joined(
                listOfNotNull(
                    UiText.of(R.string.flashcards_backup_nothing_new),
                    result.skipped.takeIf { it > 0 }
                        ?.let { UiText.ofPlural(R.plurals.flashcards_backup_skipped, it) },
                ),
            )
        } else {
            UiText.Joined(
                listOfNotNull(
                    UiText.ofPlural(R.plurals.flashcards_backup_added, result.added),
                    result.skipped.takeIf { it > 0 }
                        ?.let { UiText.ofPlural(R.plurals.flashcards_backup_skipped, it) },
                    result.picturesLost.takeIf { it > 0 }
                        ?.let { UiText.ofPlural(R.plurals.flashcards_backup_pictures_lost, it) },
                ),
            )
        }
    }

    /**
     * Run one backup job, one at a time, and say how it went.
     *
     * Failures are told apart only as far as the reader can act on them: not a
     * copy at all, a copy from a newer version of the app, or a file that
     * could not be read or written.
     */
    private fun backup(job: suspend () -> UiText) {
        if (_backupBusy.value) return
        _backupBusy.value = true
        viewModelScope.launch {
            _message.value = try {
                job()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: FlashcardBackup.UnreadableException) {
                UiText.of(
                    when (error.reason) {
                        FlashcardBackup.UnreadableException.Reason.NOT_A_BACKUP ->
                            R.string.flashcards_backup_not_a_copy

                        FlashcardBackup.UnreadableException.Reason.TOO_NEW ->
                            R.string.flashcards_backup_too_new
                    },
                )
            } catch (error: Exception) {
                Log.w(TAG, "Backup failed", error)
                UiText.of(R.string.flashcards_backup_failed)
            } finally {
                _backupBusy.value = false
            }
        }
    }

    fun onMessageShown() {
        _message.value = null
    }

    companion object {
        private const val TAG = "Flashcards"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as LlegeixApp
                FlashcardsViewModel(app.flashcardRepository)
            }
        }
    }
}
