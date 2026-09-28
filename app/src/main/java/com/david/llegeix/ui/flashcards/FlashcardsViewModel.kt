package com.david.llegeix.ui.flashcards

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.david.llegeix.LlegeixApp
import com.david.llegeix.data.db.dao.DeckWithCount
import com.david.llegeix.data.db.entity.FlashcardCollectionEntity
import com.david.llegeix.data.flashcards.CollectionTree
import com.david.llegeix.data.flashcards.DeckNames
import com.david.llegeix.data.flashcards.FlashcardBackup
import com.david.llegeix.data.flashcards.FlashcardRepository
import com.david.llegeix.data.flashcards.ListSort
import com.david.llegeix.data.flashcards.StudyDirection
import com.david.llegeix.platform.ContentRef
import com.david.llegeix.resources.*
import com.david.llegeix.ui.common.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A collection, the decks on it and the collections inside it, as the list
 * draws them.
 *
 * The counts are added up here rather than asked of SQL, because the decks
 * have already been loaded with theirs and a second query would have to repeat
 * the first one's cover logic word for word to get the same answers.
 */
/**
 * One thing in a list of the tab: a collection or a deck. They are listed
 * together, in one order, so that "A to Z" is A to Z across both — collections
 * used to come first whatever the order, and a sorted list that starts again
 * from A halfway down reads as not sorted at all.
 */
sealed interface ListEntry {
    val key: String

    data class Shelf(val shelf: DeckShelf) : ListEntry {
        override val key: String get() = "shelf-${shelf.id}"
    }

    data class Deck(val deck: DeckWithCount) : ListEntry {
        override val key: String get() = "deck-${deck.id}"
    }
}

/** How the tab lays its collections and decks out. */
enum class ListLayout {
    /** Rows with a cover, a name over two lines and the count: the default. */
    LIST,

    /** One tight row each, no cover — for many decks at once. */
    COMPACT,

    /** Two columns of cards, the cover large. */
    GRID,
    ;

    companion object {
        fun fromName(name: String?): ListLayout = entries.firstOrNull { it.name == name } ?: LIST
    }
}

data class DeckShelf(
    val collection: FlashcardCollectionEntity,
    val decks: List<DeckWithCount>,
    /** The shelves directly inside this one, in the order they are listed. */
    val children: List<DeckShelf> = emptyList(),
    /** [children] and [decks] together, in the order chosen. */
    val entries: List<ListEntry> = emptyList(),
    /** How many shelves up the top of the list is: 0 for a shelf at the top. */
    val depth: Int = 0,
) {
    val id: Long get() = collection.id

    /** Every deck on this shelf and on every shelf inside it, however deep. */
    val allDecks: List<DeckWithCount> get() = decks + children.flatMap { it.allDecks }

    /** This shelf and every shelf inside it, in the order the tree lists them. */
    val allShelves: List<DeckShelf> get() = listOf(this) + children.flatMap { it.allShelves }

    val cardCount: Int get() = allDecks.sumOf { it.cardCount }

    val isEmpty: Boolean get() = decks.isEmpty() && children.isEmpty()

    /**
     * The picture that stands for the shelf: the one it was given, or failing
     * that the first picture any deck on it has — its own decks first, then
     * those of the shelves inside it.
     *
     * The borrowed one is the older behaviour and stays the default, because it
     * is usually right and costs nobody a decision — *Food* showing the
     * vegetables is a perfectly good *Food*. What it could not do was be
     * overruled, and a shelf that had quietly settled on the parsley from
     * *Herbs* had no way to be told otherwise.
     */
    val coverImage: String?
        get() = collection.coverPath ?: allDecks.firstNotNullOfOrNull { it.coverImage }

    val coverCredit: String?
        get() = if (collection.coverPath != null) {
            collection.coverCredit
        } else {
            allDecks.firstOrNull { it.coverImage != null }?.coverCredit
        }

    /** Only the chosen picture, so the menu knows whether there is one to remove. */
    val chosenCover: String? get() = collection.coverPath
}

/**
 * The tab's list: the shelves at the top of the tree, then the decks that are
 * on no shelf.
 *
 * Null until the database has answered, which is what stops the screen
 * flashing "no decks yet" for a frame on every visit.
 */
data class DeckList(
    val shelves: List<DeckShelf>,
    val loose: List<DeckWithCount>,
    /** The top level — [shelves] and [loose] together, in the order chosen. */
    val entries: List<ListEntry> = emptyList(),
) {
    /** The shelf with [id], however deep, or null. */
    fun shelf(id: Long): DeckShelf? = allShelves.firstOrNull { it.id == id }

    /** The shelves from the top down to [id], inclusive: the breadcrumb. */
    fun pathTo(id: Long): List<DeckShelf> {
        val byId = allShelves.associateBy { it.id }
        return generateSequence(byId[id]) { shelf -> shelf.collection.parentId?.let(byId::get) }
            .toList()
            .reversed()
    }

    /** Every shelf, however deep, in the order the tree lists them. */
    val allShelves: List<DeckShelf> get() = shelves.flatMap { it.allShelves }

    val allDecks: List<DeckWithCount> get() = shelves.flatMap { it.allDecks } + loose

    val isEmpty: Boolean get() = shelves.isEmpty() && loose.isEmpty()

    val hasCards: Boolean get() = allDecks.any { it.cardCount > 0 }

    companion object {
        /**
         * The rows laid out as a tree. A deck whose shelf has gone is loose,
         * and a shelf whose parent has gone is at the top — nothing is ever
         * lost from the list because what held it went first.
         */
        fun build(
            decks: List<DeckWithCount>,
            collections: List<FlashcardCollectionEntity>,
            sort: ListSort = ListSort.Default,
        ): DeckList {
            val deckOrder = sort.comparator<DeckWithCount>(
                name = { it.name },
                createdAt = { it.createdAt },
                pinned = { it.isPinned },
                cards = { it.cardCount },
            )
            val shelfOrder = sort.comparator<DeckShelf>(
                name = { it.collection.name },
                createdAt = { it.collection.createdAt },
                pinned = { it.collection.isPinned },
                cards = { it.cardCount },
            )
            val entryOrder = sort.comparator<ListEntry>(
                name = { if (it is ListEntry.Shelf) it.shelf.collection.name else (it as ListEntry.Deck).deck.name },
                createdAt = {
                    if (it is ListEntry.Shelf) it.shelf.collection.createdAt else (it as ListEntry.Deck).deck.createdAt
                },
                pinned = {
                    if (it is ListEntry.Shelf) it.shelf.collection.isPinned else (it as ListEntry.Deck).deck.isPinned
                },
                cards = { if (it is ListEntry.Shelf) it.shelf.cardCount else (it as ListEntry.Deck).deck.cardCount },
            )
            fun merged(shelves: List<DeckShelf>, decks: List<DeckWithCount>): List<ListEntry> =
                (shelves.map { ListEntry.Shelf(it) } + decks.map { ListEntry.Deck(it) }).sortedWith(entryOrder)
            val known = collections.mapTo(HashSet()) { it.id }
            val byCollection = decks.groupBy { deck -> deck.collectionId?.takeIf { it in known } }
            val parents = CollectionTree.parents(collections)
            val childrenOf = collections.groupBy { parents[it.id] }
            fun shelf(collection: FlashcardCollectionEntity, depth: Int): DeckShelf {
                val decks = byCollection[collection.id].orEmpty().sortedWith(deckOrder)
                val children = childrenOf[collection.id].orEmpty().map { shelf(it, depth + 1) }.sortedWith(shelfOrder)
                return DeckShelf(
                    collection = collection,
                    decks = decks,
                    children = children,
                    entries = merged(children, decks),
                    depth = depth,
                )
            }
            return DeckList(
                // A shelf with nothing on it is still listed: it was made on
                // purpose, and a collection that vanished until something was
                // put on it would look like the app having forgotten it.
                shelves = childrenOf[null].orEmpty().map { shelf(it, 0) }.sortedWith(shelfOrder),
                loose = byCollection[null].orEmpty().sortedWith(deckOrder),
            ).let { it.copy(entries = merged(it.shelves, it.loose)) }
        }
    }
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
     * The collection being looked inside, or null for the top of the tab.
     *
     * Collections are opened like folders in a file app — the list is replaced
     * by what is inside, with the way back above it — rather than unfolded in
     * place. Unfolding kept every level on one screen, indented and joined by
     * guide lines, and with a few collections that was a lot to take in at
     * once. Kept here, so going into a deck and back returns to the same place.
     */
    private val _current = MutableStateFlow<Long?>(null)
    val current: StateFlow<Long?> = _current.asStateFlow()

    fun onOpenShelf(id: Long) {
        _current.value = id
    }

    /** Go to [id], or the top with null — the breadcrumb. */
    fun onGoTo(id: Long?) {
        _current.value = id
    }

    /** One level up: to the collection this one is in, or the top. */
    fun onUp() {
        val id = _current.value ?: return
        _current.value = list.value?.shelf(id)?.collection?.parentId?.takeIf { list.value?.shelf(it) != null }
    }

    private val _layout = MutableStateFlow(ListLayout.fromName(flashcards.prefs.listLayout))
    val layout: StateFlow<ListLayout> = _layout.asStateFlow()

    fun onLayout(layout: ListLayout) {
        _layout.value = layout
        flashcards.prefs.listLayout = layout.name
    }

    /** The decks grouped under the shelves they are on, in the order both are listed. */
    private val _sort = MutableStateFlow(flashcards.prefs.listSort)
    val sort: StateFlow<ListSort> = _sort.asStateFlow()

    fun onSort(sort: ListSort) {
        _sort.value = sort
        flashcards.prefs.listSort = sort
    }

    val list: StateFlow<DeckList?> = combine(decks, collections, _sort) { decks, collections, sort ->
        if (decks == null || collections == null) return@combine null
        DeckList.build(decks, collections, sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** How many weak words there are, for their row at the top of the tab. */
    val weakCount: StateFlow<Int> = flashcards.observeWeak()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    // ---- What to study -----------------------------------------------------

    private val _direction = MutableStateFlow(flashcards.prefs.direction)
    val direction: StateFlow<StudyDirection> = _direction.asStateFlow()

    fun onChooseDirection(direction: StudyDirection) {
        _direction.value = direction
        flashcards.prefs.direction = direction
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
        val others = list.value?.allShelves.orEmpty()
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
        // Standing inside what is being deleted, go up out of it first.
        if (_current.value == shelf.id) onUp()
        flashcards.deleteCollection(shelf.id)
        _message.value = UiText.of(Res.string.flashcards_collection_deleted, shelf.collection.name)
    }

    /** Go into [id], after something was made inside it. */
    fun revealInside(id: Long) {
        _current.value = id
    }

    /** Put a deck on a shelf, or take it off one with null. */
    fun moveDeck(deck: DeckWithCount, collectionId: Long?) = viewModelScope.launch {
        if (deck.collectionId == collectionId) return@launch
        flashcards.setDeckCollection(deck.id, collectionId)
        revealAndSay(deck.name, collectionId)
    }

    /** Put a shelf inside another, or at the top of the list with null. */
    fun moveCollection(shelf: DeckShelf, parentId: Long?) = viewModelScope.launch {
        if (shelf.collection.parentId == parentId) return@launch
        if (flashcards.setCollectionParent(shelf.id, parentId)) revealAndSay(shelf.collection.name, parentId)
    }

    /** Say where something was just put, since it has left the list on show. */
    private fun revealAndSay(name: String, target: Long?) {
        val shelves = list.value?.allShelves.orEmpty()
        val where = target?.let { id -> shelves.firstOrNull { it.id == id }?.collection?.name }
        _message.value = if (where == null) {
            UiText.of(Res.string.flashcards_moved_to_top, name)
        } else {
            UiText.of(Res.string.flashcards_moved_to, name, where)
        }
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
        _message.value = UiText.of(Res.string.flashcards_deck_deleted, deck.name)
    }

    /**
     * Only reached when the list moved between the dialog's check and the
     * write — the dialog does not let a clash through otherwise.
     */
    private fun report(check: DeckNames.Check) {
        if (check is DeckNames.Check.Taken) {
            _message.value = UiText.of(Res.string.flashcards_deck_exists, check.existing)
        }
    }

    // ---- Backup ------------------------------------------------------------

    /** A copy is being written or read; the buttons wait for it. */
    private val _backupBusy = MutableStateFlow(false)
    val backupBusy: StateFlow<Boolean> = _backupBusy.asStateFlow()

    fun onExport(uri: Uri) = backup {
        val cards = flashcards.exportTo(ContentRef(uri))
        UiText.ofPlural(Res.plurals.flashcards_backup_saved, cards)
    }

    fun onRestore(uri: Uri) = backup {
        val result = flashcards.restoreFrom(ContentRef(uri))
        if (result.added == 0) {
            UiText.Joined(
                listOfNotNull(
                    UiText.of(Res.string.flashcards_backup_nothing_new),
                    result.skipped.takeIf { it > 0 }
                        ?.let { UiText.ofPlural(Res.plurals.flashcards_backup_skipped, it) },
                ),
            )
        } else {
            UiText.Joined(
                listOfNotNull(
                    UiText.ofPlural(Res.plurals.flashcards_backup_added, result.added),
                    result.skipped.takeIf { it > 0 }
                        ?.let { UiText.ofPlural(Res.plurals.flashcards_backup_skipped, it) },
                    result.picturesLost.takeIf { it > 0 }
                        ?.let { UiText.ofPlural(Res.plurals.flashcards_backup_pictures_lost, it) },
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
                            Res.string.flashcards_backup_not_a_copy

                        FlashcardBackup.UnreadableException.Reason.TOO_NEW ->
                            Res.string.flashcards_backup_too_new
                    },
                )
            } catch (error: Exception) {
                Log.w(TAG, "Backup failed", error)
                UiText.of(Res.string.flashcards_backup_failed)
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
