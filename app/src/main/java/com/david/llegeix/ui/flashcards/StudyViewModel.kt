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
import com.david.llegeix.data.flashcards.StudyDirection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StudyUiState(
    val isLoading: Boolean = true,
    /** The deck being studied, or null for every deck at once. */
    val deckName: String? = null,
    val direction: StudyDirection = StudyDirection.Default,
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
    /** How many cards there are to study at all, for when none are due. */
    val cardsInScope: Int = 0,
    /** When the next card comes back this way round, for when none are due now. */
    val nextDueAt: Long? = null,
    /** How many are waiting the other way round, which is somewhere to go instead. */
    val otherDirectionDue: Int = 0,
) {
    val otherDirection: StudyDirection get() = direction.other()

    val current: FlashcardEntity? get() = cards.getOrNull(index)

    val isFinished: Boolean get() = !isLoading && cards.isNotEmpty() && index >= cards.size

    /** Nothing was due in the first place, which is a different screen. */
    val isEmpty: Boolean get() = !isLoading && cards.isEmpty()

    val progress: Float
        get() = if (cards.isEmpty()) 0f else (index.toFloat() / cards.size).coerceIn(0f, 1f)
}

/**
 * A short session over the cards that are due in one direction.
 *
 * Built like [com.david.llegeix.ui.practice.PracticeViewModel] on purpose: the
 * same shape of session, the same two answers, the same scheduler. The one
 * thing added is the direction, which decides which side of the card is the
 * question and which half of its schedule the answer moves.
 */
class StudyViewModel(
    private val flashcards: FlashcardRepository,
    private val deckId: Long?,
    direction: StudyDirection,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudyUiState(direction = direction))
    val uiState: StateFlow<StudyUiState> = _uiState.asStateFlow()

    init {
        deal()
    }

    /** Take a fresh hand of due cards. */
    fun deal() = viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true) }
        val deckName = deckId?.let { flashcards.observeDeck(it).first()?.name }
        val all = flashcards.cardsToStudy(deckId)
        val now = System.currentTimeMillis()
        val direction = _uiState.value.direction
        val due = FlashcardSession.deal(all, direction, now)
        _uiState.update {
            it.copy(
                isLoading = false,
                deckName = deckName,
                cards = due,
                index = 0,
                isRevealed = false,
                correct = 0,
                cardsInScope = all.size,
                nextDueAt = FlashcardSession.nextDueAt(all, direction, now),
                otherDirectionDue = all.count { card -> direction.other().isDue(card, now) },
            )
        }
    }

    /**
     * Nothing is due this way round but something is the other way: turn the
     * session round rather than send the reader back to choose it.
     */
    fun onSwitchDirection() {
        _uiState.update { it.copy(direction = it.direction.other()) }
        deal()
    }

    fun onReveal() {
        _uiState.update { it.copy(isRevealed = true) }
    }

    /**
     * Answer the card in front of the reader and move on.
     *
     * The move happens whether or not the write has landed: the schedule is a
     * background fact and the next card is the thing being waited for.
     */
    fun onAnswer(correct: Boolean) {
        val state = _uiState.value
        val card = state.current ?: return
        viewModelScope.launch {
            flashcards.recordAnswer(card, state.direction, correct, System.currentTimeMillis())
        }
        _uiState.update {
            it.copy(
                index = it.index + 1,
                isRevealed = false,
                correct = it.correct + if (correct) 1 else 0,
            )
        }
    }

    companion object {
        fun factory(deckId: Long?, direction: StudyDirection): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as LlegeixApp
                    StudyViewModel(app.flashcardRepository, deckId, direction)
                }
            }
    }
}
