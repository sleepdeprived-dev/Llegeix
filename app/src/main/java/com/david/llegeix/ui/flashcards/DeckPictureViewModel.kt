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
import com.david.llegeix.data.flashcards.FlashcardRepository
import com.david.llegeix.data.flashcards.PictureHit
import com.david.llegeix.data.flashcards.PictureSearch
import com.david.llegeix.data.flashcards.PictureSource
import com.david.llegeix.translate.WordTranslator
import com.david.llegeix.ui.common.UiText
import com.david.llegeix.util.runCatchingCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Choosing a deck's own picture.
 *
 * The same search as a card's, asked with the deck's name — *Menjar* brings
 * up food, *Viatges* suitcases — and the same ways in: a suggestion, or one of
 * the reader's own photos. Unlike a card, nothing is half-chosen here: the
 * picture is put on the deck the moment it is picked, and the sheet closes.
 */
class DeckPictureViewModel(
    private val flashcards: FlashcardRepository,
    pictureSearch: PictureSearch,
    private val deckId: Long,
) : ViewModel() {

    private var english: WordTranslator? = null

    val pictures = PictureSuggester(
        scope = viewModelScope,
        search = pictureSearch,
        flashcards = flashcards,
        englishFor = { name -> translate(name) },
        englishFromRomanian = { null },
    )

    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            flashcards.observeDecks().collect { decks ->
                decks.firstOrNull { it.id == deckId }?.let { deck ->
                    if (!pictures.isFor(deck.name)) pictures.suggest(deck.name, pause = false)
                }
            }
        }
    }

    fun onSourceChange(source: PictureSource) = pictures.setSource(source)

    fun onRetry() = pictures.again()

    fun onPick(hit: PictureHit) = setCover {
        flashcards.setDeckCover(deckId, pictures.fetch(hit), hit.credit)
    }

    fun onPickOwn(uri: Uri) = setCover {
        flashcards.setDeckCover(deckId, flashcards.importImage(uri), credit = null)
    }

    /** Back to the first card's picture, or the initial. */
    fun onRemove() = setCover {
        flashcards.setDeckCover(deckId, path = null, credit = null)
    }

    private fun setCover(job: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            runCatchingCancellable { job() }
                .onSuccess { _done.value = true }
                .onFailure { error ->
                    Log.w(TAG, "Could not set the deck's picture", error)
                    _message.value = UiText.of(R.string.flashcards_picture_fetch_failed)
                }
            _busy.value = false
        }
    }

    fun onMessageShown() {
        _message.value = null
    }

    private suspend fun translate(text: String): String? {
        val translator = english ?: WordTranslator(targetLanguage = "en").also { english = it }
        if (!translator.isModelReady) {
            runCatchingCancellable { translator.ensureModel(requireWifi = true) }.onFailure { return null }
        }
        return runCatchingCancellable { translator.translate(text) }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }

    override fun onCleared() {
        english?.close()
    }

    companion object {
        private const val TAG = "DeckPicture"

        fun factory(deckId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as LlegeixApp
                DeckPictureViewModel(app.flashcardRepository, app.pictureSearch, deckId)
            }
        }
    }
}
