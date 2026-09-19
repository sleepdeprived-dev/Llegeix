package com.david.llegeix.ui.flashcards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.david.llegeix.LlegeixApp
import com.david.llegeix.R
import com.david.llegeix.data.db.dao.DeckWithCount
import com.david.llegeix.data.flashcards.DeckNames
import com.david.llegeix.data.flashcards.FlashcardRepository
import com.david.llegeix.ui.common.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    fun onMessageShown() {
        _message.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as LlegeixApp
                FlashcardsViewModel(app.flashcardRepository)
            }
        }
    }
}
