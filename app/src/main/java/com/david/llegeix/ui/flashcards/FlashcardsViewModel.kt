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
import com.david.llegeix.data.db.dao.DeckDue
import com.david.llegeix.data.db.dao.DeckWithCount
import com.david.llegeix.data.flashcards.DeckNames
import com.david.llegeix.data.flashcards.FlashcardBackup
import com.david.llegeix.data.flashcards.FlashcardRepository
import com.david.llegeix.data.flashcards.StudyDirection
import com.david.llegeix.ui.common.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The list of decks, and making, renaming and deleting them.
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

    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    // ---- What to study -----------------------------------------------------

    /** The deck chosen to study, or null for all of them. */
    private val _studyDeck = MutableStateFlow<Long?>(null)
    val studyDeck: StateFlow<Long?> = _studyDeck.asStateFlow()

    private val _direction = MutableStateFlow(StudyDirection.Default)
    val direction: StateFlow<StudyDirection> = _direction.asStateFlow()

    /**
     * The moment "due" is measured against.
     *
     * A due count is a question about the clock, and a query takes its answer
     * at the moment it is asked. So the clock is moved on each time the tab is
     * looked at again — coming back from a session, or from another app — and
     * the counts follow, rather than a card that fell due ten minutes ago
     * staying out of the count until the app is restarted.
     */
    private val now = MutableStateFlow(System.currentTimeMillis())

    @OptIn(ExperimentalCoroutinesApi::class)
    val dueByDeck: StateFlow<Map<Long, DeckDue>> = now
        .flatMapLatest { flashcards.observeDueCounts(it) }
        .map { rows -> rows.associateBy { it.deckId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun onChooseStudyDeck(deckId: Long?) {
        _studyDeck.value = deckId
    }

    fun onChooseDirection(direction: StudyDirection) {
        _direction.value = direction
    }

    /** The tab is being looked at again: count against the time it is now. */
    fun onResumed() {
        now.value = System.currentTimeMillis()
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

    fun createDeck(name: String) = viewModelScope.launch {
        report(flashcards.createDeck(name))
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
