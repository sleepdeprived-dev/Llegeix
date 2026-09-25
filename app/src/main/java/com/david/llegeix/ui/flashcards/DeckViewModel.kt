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
import com.david.llegeix.translate.WordTranslator
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

    /**
     * How many cards have no English meaning — the cards practice shows with
     * their Romanian alone, since every card shows both when it has both.
     */
    val missingEnglish: StateFlow<Int> = cards.map { list ->
        list.orEmpty().count { MeaningLanguage.ENGLISH.meaningOf(it) == null }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _fillingEnglish = MutableStateFlow(false)
    val fillingEnglish: StateFlow<Boolean> = _fillingEnglish.asStateFlow()

    /**
     * Ask the translator for the English of every card without one.
     *
     * Each is written as the card's English, to be corrected in the card like
     * anything else; a word the translator cannot do is left for the reader.
     */
    fun onFillEnglish() {
        if (_fillingEnglish.value) return
        _fillingEnglish.value = true
        viewModelScope.launch {
            val translator = WordTranslator(targetLanguage = MeaningLanguage.ENGLISH.code)
            try {
                val ready = runCatchingCancellable { translator.ensureModel(requireWifi = true) }
                if (ready.isFailure) {
                    _message.value = UiText.of(R.string.flashcards_meaning_needs_model)
                    return@launch
                }
                val filled = flashcards.fillEnglish(deckId) { word ->
                    pictureSearch.meanings(word)?.english
                        ?: runCatchingCancellable { translator.translate(word) }.getOrNull()
                }
                _message.value = UiText.ofPlural(R.plurals.flashcards_english_filled, filled)
            } finally {
                translator.close()
                _fillingEnglish.value = false
            }
        }
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
