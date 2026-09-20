package com.david.llegeix.ui.flashcards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.david.llegeix.LlegeixApp
import com.david.llegeix.data.db.entity.FlashcardEntity
import com.david.llegeix.data.flashcards.FlashcardRepository
import com.david.llegeix.data.flashcards.FlashcardSession
import com.david.llegeix.data.flashcards.MeaningLanguage
import com.david.llegeix.data.flashcards.StudyDirection
import com.david.llegeix.data.flashcards.StudyScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StudyUiState(
    val isLoading: Boolean = true,
    /**
     * What is being studied, by name: a deck, a collection, or null for
     * everything at once.
     */
    val scopeName: String? = null,
    val direction: StudyDirection = StudyDirection.Default,
    val language: MeaningLanguage = MeaningLanguage.Default,
    /**
     * A round of extra practice: cards whether due or not, and answers not
     * recorded. See [FlashcardSession.extra] for why they are kept off the
     * schedule.
     */
    val isExtra: Boolean = false,
    /**
     * The session, dealt once and then left alone.
     *
     * Not a live query, for the reason the saved-words practice gives: a card
     * answered wrongly is due again in ten minutes, and a deck that kept
     * listening would grow back under the reader while "4 of 12" counted
     * towards a number that moved.
     */
    val cards: List<FlashcardEntity> = emptyList(),
    val index: Int = 0,
    val isRevealed: Boolean = false,
    val correct: Int = 0,
    /** The cards answered "not yet" this session, to name at the end. */
    val missed: List<FlashcardEntity> = emptyList(),
    /** How many cards there are to practise at all, for when there are none. */
    val cardsInScope: Int = 0,
    /** Cards left out because they have no meaning in the language chosen. */
    val cardsWithoutMeaning: Int = 0,
) {
    val current: FlashcardEntity? get() = cards.getOrNull(index)

    val isFinished: Boolean get() = !isLoading && cards.isNotEmpty() && index >= cards.size

    /** There was nothing to ask at all, which is a different screen. */
    val isEmpty: Boolean get() = !isLoading && cards.isEmpty()

    val progress: Float
        get() = if (cards.isEmpty()) 0f else (index.toFloat() / cards.size).coerceIn(0f, 1f)

    /** The card's meaning in the language being practised; dealt cards always have one. */
    fun meaningOf(card: FlashcardEntity): String = language.meaningOf(card) ?: card.romanian
}

/**
 * A session over every card in a deck, a collection or the whole lot.
 *
 * Built like [com.david.llegeix.ui.practice.PracticeViewModel]: the same shape
 * of session, the same two answers, the same scheduler. Added are the
 * direction, which decides which side of the card is the question and which
 * half of its schedule an answer moves; the language the meaning is shown in;
 * and extra practice, which is what *Repeat these* deals at the end of a round.
 *
 * "Every card" is the whole of it — see [FlashcardSession.everything]. The
 * schedule still orders the hand and still moves with the answers; it no longer
 * decides which cards are in it.
 */
class StudyViewModel(
    private val flashcards: FlashcardRepository,
    private val scope: StudyScope,
    direction: StudyDirection,
    language: MeaningLanguage,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        StudyUiState(direction = direction, language = language),
    )
    val uiState: StateFlow<StudyUiState> = _uiState.asStateFlow()

    init {
        deal()
    }

    /** Take a fresh hand: every card there is, or a capped extra round. */
    fun deal() = viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true) }
        val scopeName = when (scope) {
            StudyScope.Everything -> null
            is StudyScope.Deck -> flashcards.observeDeck(scope.id).first()?.name
            is StudyScope.Collection -> flashcards.collection(scope.id)?.name
        }
        val all = flashcards.cardsToStudy(scope)
        val state = _uiState.value
        val direction = state.direction
        val language = state.language
        val dealt = if (state.isExtra) {
            FlashcardSession.extra(all, direction, language = language)
        } else {
            FlashcardSession.everything(all, direction, language = language)
        }
        _uiState.update {
            it.copy(
                isLoading = false,
                scopeName = scopeName,
                cards = dealt,
                index = 0,
                isRevealed = false,
                correct = 0,
                missed = emptyList(),
                cardsInScope = all.count { card -> language.meaningOf(card) != null },
                cardsWithoutMeaning = all.count { card -> language.meaningOf(card) == null },
            )
        }
    }

    /**
     * The same cards again, in a new order, as extra practice — straight after
     * a session, while they are fresh, is when going over them again helps.
     */
    fun onRepeat() {
        _uiState.update {
            it.copy(
                isExtra = true,
                cards = it.cards.shuffled(),
                index = 0,
                isRevealed = false,
                correct = 0,
                missed = emptyList(),
            )
        }
    }

    fun onReveal() {
        _uiState.update { it.copy(isRevealed = true) }
    }

    /**
     * Answer the card in front of the reader and move on.
     *
     * The move happens whether or not the write has landed: the schedule is a
     * background fact and the next card is the thing being waited for. In
     * extra practice nothing is written at all.
     */
    fun onAnswer(correct: Boolean) {
        val state = _uiState.value
        val card = state.current ?: return
        if (!state.isExtra) {
            viewModelScope.launch {
                flashcards.recordAnswer(card, state.direction, correct, System.currentTimeMillis())
            }
        }
        _uiState.update {
            it.copy(
                index = it.index + 1,
                isRevealed = false,
                correct = it.correct + if (correct) 1 else 0,
                missed = if (correct) it.missed else it.missed + card,
            )
        }
    }

    companion object {
        fun factory(
            scope: StudyScope,
            direction: StudyDirection,
            language: MeaningLanguage,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as LlegeixApp
                StudyViewModel(app.flashcardRepository, scope, direction, language)
            }
        }
    }
}
