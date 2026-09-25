package com.david.llegeix.ui.flashcards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.david.llegeix.LlegeixApp
import com.david.llegeix.R
import com.david.llegeix.data.db.entity.FlashcardDeckEntity
import com.david.llegeix.data.db.entity.FlashcardEntity
import com.david.llegeix.data.flashcards.CardSearch
import com.david.llegeix.data.flashcards.CardSort
import com.david.llegeix.data.flashcards.FlashcardRepository
import com.david.llegeix.ui.common.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import com.david.llegeix.data.flashcards.MeaningLanguage
import com.david.llegeix.data.flashcards.PictureSearch
import com.david.llegeix.util.runCatchingCancellable
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** One deck's cards. */
class DeckViewModel(
    private val flashcards: FlashcardRepository,
    private val pictureSearch: PictureSearch,
    private val deckId: Long,
) : ViewModel() {

    val deck: StateFlow<FlashcardDeckEntity?> = flashcards.observeDeck(deckId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Null until the first answer, so an empty deck is not flashed on the way in. */
    val cards: StateFlow<List<FlashcardEntity>?> = flashcards.observeCards(deckId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /**
     * The cards the search finds, or all of them when there is no search.
     *
     * Filtered here rather than in the database, because the match ignores
     * accents and SQLite's `LIKE` cannot; a deck is small enough that this is
     * the cheaper of the two anyway.
     */
    private val _sort = MutableStateFlow(flashcards.prefs.cardSort)
    val sort: StateFlow<CardSort> = _sort.asStateFlow()

    fun onSort(sort: CardSort) {
        _sort.value = sort
        flashcards.prefs.cardSort = sort
    }

    val shown: StateFlow<List<FlashcardEntity>?> = combine(cards, _query, _sort) { all, query, sort ->
        // A search keeps its own order, best match first; the chosen order is
        // for browsing.
        all?.let { if (query.isBlank()) sort.sorted(it) else CardSearch.filter(it, query) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onQueryChange(query: String) {
        _query.value = query
    }

    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    fun deleteCard(card: FlashcardEntity) = viewModelScope.launch {
        flashcards.deleteCard(card)
        _message.value = UiText.of(R.string.flashcards_card_deleted, card.catalan)
    }

    fun onMessageShown() {
        _message.value = null
    }

    companion object {
        fun factory(deckId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as LlegeixApp
                DeckViewModel(app.flashcardRepository, app.pictureSearch, deckId)
            }
        }
    }
}
