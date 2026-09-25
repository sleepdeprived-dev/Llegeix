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
import com.david.llegeix.data.flashcards.DeckNames
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the + is making this time. */
enum class NewDeckKind { DECK, COLLECTION }

data class NewDeckUiState(
    val kind: NewDeckKind = NewDeckKind.DECK,
    val name: String = "",
    val check: DeckNames.Check = DeckNames.Check.Blank,
    /** The picture chosen so far, copied in already so it can be shown. */
    val coverPath: String? = null,
    val coverCredit: String? = null,
    val isBusy: Boolean = false,
)

/**
 * Making a deck: its name, and — while the name is being typed — its picture.
 *
 * Choosing the picture here rather than afterwards, because the name is the
 * best search there is for it: typing *Menjar* is already asking for pictures
 * of food. The suggestions follow the name as it is typed, the same way a card's
 * follow its word.
 *
 * A chosen picture is copied in at once, before the deck exists, so it can be
 * shown; if the sheet is closed without making the deck, that copy is deleted.
 */
class NewDeckViewModel(
    private val flashcards: FlashcardRepository,
    pictureSearch: PictureSearch,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewDeckUiState())
    val uiState: StateFlow<NewDeckUiState> = _uiState.asStateFlow()

    private val _created = MutableStateFlow(false)
    val created: StateFlow<Boolean> = _created.asStateFlow()

    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    private var english: WordTranslator? = null
    private var names: List<String> = emptyList()
    private var collectionNames: List<String> = emptyList()

    /** The names the typed one must not clash with, which depends on what is being made. */
    private fun takenNames(kind: NewDeckKind): List<String> = when (kind) {
        NewDeckKind.DECK -> names
        NewDeckKind.COLLECTION -> collectionNames
    }

    val pictures = PictureSuggester(
        scope = viewModelScope,
        search = pictureSearch,
        flashcards = flashcards,
        englishFor = { translate(it) },
        englishFromRomanian = { null },
    )

    init {
        viewModelScope.launch { names = flashcards.observeDecks().first().map { it.name } }
        viewModelScope.launch {
            collectionNames = flashcards.observeCollections().first().map { it.name }
        }
    }

    /**
     * Switch between making a deck and making a collection.
     *
     * The + makes both, because they are the same gesture — *something new
     * here* — and a screen with two floating buttons on it makes the reader
     * choose before they have been asked anything. The choice is the first
     * thing in the sheet instead, where the name field is waiting either way.
     *
     * A collection has no picture of its own: it wears the picture of the first
     * deck on it, which is a picture the reader already chose once. So any
     * picture picked before the switch is let go of here rather than quietly
     * kept and thrown away at the end.
     */
    fun onKindChange(kind: NewDeckKind) {
        if (_uiState.value.kind == kind) return
        if (kind == NewDeckKind.COLLECTION) {
            pictures.cancel()
            discardCover()
        }
        _uiState.update {
            it.copy(
                kind = kind,
                coverPath = null,
                coverCredit = null,
                check = DeckNames.check(it.name, takenNames(kind)),
            )
        }
        if (kind == NewDeckKind.DECK) pictures.suggest(_uiState.value.name, pause = false)
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name, check = DeckNames.check(name, takenNames(it.kind))) }
        val state = _uiState.value
        if (state.kind == NewDeckKind.DECK && state.coverPath == null) pictures.suggest(name)
    }

    fun onSourceChange(source: PictureSource) = pictures.setSource(source)

    fun onSearchPictures(text: String) = pictures.searchFor(text)

    fun onRetry() = pictures.again()

    fun onPick(hit: PictureHit) = choose(hit.credit) { pictures.fetch(hit) }

    fun onPickOwn(uri: Uri) = choose(credit = null) { flashcards.importImage(uri) }

    /** Back to the suggestions, and the copy made for the last choice deleted. */
    fun onRemoveCover() {
        discardCover()
        _uiState.update { it.copy(coverPath = null, coverCredit = null) }
        pictures.suggest(_uiState.value.name, pause = false)
    }

    private fun choose(credit: String?, bringIn: suspend () -> String) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            runCatchingCancellable { bringIn() }
                .onSuccess { path ->
                    discardCover()
                    _uiState.update { it.copy(coverPath = path, coverCredit = credit, isBusy = false) }
                }
                .onFailure { error ->
                    Log.w(TAG, "Could not bring in a picture for a new deck", error)
                    _uiState.update { it.copy(isBusy = false) }
                    _message.value = UiText.of(R.string.flashcards_picture_fetch_failed)
                }
        }
    }

    fun onCreate() {
        val state = _uiState.value
        if (state.check !is DeckNames.Check.Ok || state.isBusy) return
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val result = when (state.kind) {
                NewDeckKind.DECK ->
                    flashcards.createDeck(state.name, state.coverPath, state.coverCredit)

                NewDeckKind.COLLECTION -> flashcards.createCollection(state.name)
            }
            if (result is DeckNames.Check.Ok) {
                // The picture belongs to the deck now; nothing to clean up.
                _uiState.update { it.copy(coverPath = null, isBusy = false) }
                _created.value = true
            } else {
                _uiState.update { it.copy(check = result, isBusy = false) }
            }
        }
    }

    /**
     * The sheet was closed without making the deck. Said straight away rather
     * than left to [onCleared], because this lives as long as the tab does and
     * a picture nobody kept should not wait that long to be deleted.
     */
    fun onCancel() {
        if (_created.value) return
        pictures.cancel()
        discardCover()
        _uiState.update { it.copy(coverPath = null, coverCredit = null) }
    }

    fun onMessageShown() {
        _message.value = null
    }

    private fun discardCover() {
        _uiState.value.coverPath?.let { flashcards.deleteImageNow(it) }
    }

    private suspend fun translate(text: String): String? {
        val translator = english ?: WordTranslator(targetLanguage = "en").also { english = it }
        if (!translator.isModelReady) {
            runCatchingCancellable { translator.ensureModel(requireWifi = true) }.onFailure { return null }
        }
        return runCatchingCancellable { translator.translate(text) }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Closed without making the deck: the picture copied in for it goes too. */
    override fun onCleared() {
        discardCover()
        english?.close()
    }

    companion object {
        private const val TAG = "NewDeck"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as LlegeixApp
                NewDeckViewModel(app.flashcardRepository, app.pictureSearch)
            }
        }
    }
}
