package com.david.llegeix.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.david.llegeix.LlegeixApp
import com.david.llegeix.data.db.entity.WordBookmarkEntity
import com.david.llegeix.data.settings.SettingsRepository
import com.david.llegeix.data.settings.TranslationTarget
import com.david.llegeix.data.source.LibraryDataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PracticeUiState(
    val isLoading: Boolean = true,
    /**
     * The session, taken once and then left alone.
     *
     * Not a live query. A word answered wrongly is due again in ten minutes, so
     * a deck that kept listening would grow back under the reader and "4 of 12"
     * would start counting towards a number that moves.
     */
    val cards: List<WordBookmarkEntity> = emptyList(),
    val index: Int = 0,
    /** Whether the answer is showing, as against only the word. */
    val isRevealed: Boolean = false,
    val correct: Int = 0,
    /** How many words are saved altogether, for when none are due. */
    val savedTotal: Int = 0,
    val target: TranslationTarget = TranslationTarget.Default,
) {
    val current: WordBookmarkEntity? get() = cards.getOrNull(index)

    val isFinished: Boolean get() = !isLoading && cards.isNotEmpty() && index >= cards.size

    /** Nothing was due in the first place, which is a different screen. */
    val isEmpty: Boolean get() = !isLoading && cards.isEmpty()

    val progress: Float
        get() = if (cards.isEmpty()) 0f else (index.toFloat() / cards.size).coerceIn(0f, 1f)
}

/**
 * A short session over the words that are due.
 *
 * The saved words were a list you could search and sort, and nothing else — a
 * pile that grew and was never met again, which is the one thing that reliably
 * fails to teach anybody a language. This is the smallest honest answer to
 * that: the words that are due, one at a time, right or wrong, with the
 * schedule deciding when each comes back.
 */
class PracticeViewModel(
    private val libraryData: LibraryDataRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PracticeUiState())
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(target = settings.current.translationTarget) }
        deal()
    }

    /** Take a fresh hand of due words. */
    fun deal() = viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true) }
        val due = libraryData.observeDueWords(System.currentTimeMillis()).first()
        val total = libraryData.observeWordBookmarkCount().first()
        _uiState.update {
            it.copy(
                isLoading = false,
                cards = due,
                index = 0,
                isRevealed = false,
                correct = 0,
                savedTotal = total,
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
     * background fact and the next card is the thing being waited for.
     */
    fun onAnswer(correct: Boolean) {
        val card = _uiState.value.current ?: return
        viewModelScope.launch {
            libraryData.recordReview(
                id = card.id,
                box = card.box,
                correct = correct,
                now = System.currentTimeMillis(),
            )
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
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as LlegeixApp
                PracticeViewModel(app.libraryDataRepository, app.settingsRepository)
            }
        }
    }
}
