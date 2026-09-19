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
import com.david.llegeix.data.db.entity.FlashcardEntity
import com.david.llegeix.data.flashcards.FlashcardRepository
import com.david.llegeix.data.flashcards.PictureHit
import com.david.llegeix.data.flashcards.PictureSearch
import com.david.llegeix.data.flashcards.PictureSource
import com.david.llegeix.data.flashcards.Suggested
import com.david.llegeix.data.flashcards.matchLeadingCase
import com.david.llegeix.data.flashcards.tidyIpa
import com.david.llegeix.data.settings.TranslationTarget
import com.david.llegeix.lang.CatalanIpa
import com.david.llegeix.translate.WordTranslator
import com.david.llegeix.ui.common.UiText
import com.david.llegeix.util.runCatchingCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the Romanian suggestion has got to, for the line under its field. */
enum class MeaningSuggestion {
    IDLE,

    /** The Catalan–Romanian model is being fetched, once, over Wi-Fi. */
    DOWNLOADING,

    /** The model is not on the phone and could not be fetched just now. */
    NEEDS_MODEL,
}

/** How the row of suggested pictures stands. */
enum class PictureStatus {
    /** No word yet, so nothing to look for. */
    WAITING,
    SEARCHING,
    FOUND,
    NONE_FOUND,

    /** No network, or the service did not answer. */
    OFFLINE,

    /** Photos are searched in English, and the English model is not on the phone. */
    NEEDS_ENGLISH,
}

data class PictureSuggestions(
    val status: PictureStatus = PictureStatus.WAITING,
    val hits: List<PictureHit> = emptyList(),
    /** The word these are for, so a stale row is never offered for a new word. */
    val word: String = "",
    val source: PictureSource = PictureSource.PICTOGRAMS,
)

data class CardEditorUiState(
    val isLoading: Boolean = true,
    val deckName: String = "",
    /** False when editing a card that already exists. */
    val isNew: Boolean = true,
    val catalan: String = "",
    val romanian: Suggested = Suggested(),
    val ipa: Suggested = Suggested(),
    val imagePath: String? = null,
    /** Who made the picture, when it came from a search; null for the reader's own. */
    val imageCredit: String? = null,
    val isImporting: Boolean = false,
    /** Which source the row of suggestions is showing. Pictograms first. */
    val pictureSource: PictureSource = PictureSource.PICTOGRAMS,
    val pictures: PictureSuggestions = PictureSuggestions(),
    /** The suggestion being fetched after a tap, so its tile can say so. */
    val fetchingPicture: String? = null,
    /** A save is being written; a second press must not make a second card. */
    val isSaving: Boolean = false,
    val meaningSuggestion: MeaningSuggestion = MeaningSuggestion.IDLE,
    /** Anything differs from what was loaded, so leaving should ask first. */
    val isDirty: Boolean = false,
) {
    val canSave: Boolean
        get() = !isLoading && !isImporting && !isSaving &&
            catalan.isNotBlank() && romanian.text.isNotBlank()
}

/**
 * Making a card, or changing one.
 *
 * ### What the app fills in
 *
 * The pronunciation is written the moment the Catalan is: it is generated from
 * the spelling and costs nothing. The Romanian waits for a pause in the typing
 * and then asks the on-device translator. Both go into their fields as
 * [Suggested] values, marked as the app's, and both stop moving the moment the
 * reader types in the field. The Romanian is a suggestion and nothing more —
 * a single word handed to a translator with no sentence around it is exactly
 * where a translator is weakest.
 *
 * ### Pictures and their files
 *
 * A picked photo is copied in straight away, so the form can show it. That
 * means a file exists before the card that will point at it, and the form has
 * to keep track of which files are the card's and which are only its own:
 *
 *  - [storedImage] is the picture the saved card points at;
 *  - the state's `imagePath` is the one on screen.
 *
 * Whenever the one on screen is not the stored one, it belongs to the form, and
 * the form deletes it if it is replaced, removed or abandoned. The stored one
 * is deleted only once a save has replaced it: until then, backing out of the
 * form has to leave the card exactly as it was.
 */
class CardEditorViewModel(
    private val flashcards: FlashcardRepository,
    private val pictureSearch: PictureSearch,
    private val deckId: Long,
    private val cardId: Long?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CardEditorUiState(isNew = cardId == null))
    val uiState: StateFlow<CardEditorUiState> = _uiState.asStateFlow()

    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    /** Set once the form is done with, so the screen can leave. */
    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    /** Bumped after "save and add another", so the screen can refocus the word. */
    private val _cleared = MutableStateFlow(0)
    val cleared: StateFlow<Int> = _cleared.asStateFlow()

    private var loaded: FlashcardEntity? = null
    private var storedImage: String? = null

    /** The values the form opened with, which [CardEditorUiState.isDirty] compares against. */
    private var baseline = Baseline()

    private data class Baseline(
        val catalan: String = "",
        val romanian: String = "",
        val ipa: String = "",
        val imagePath: String? = null,
    )

    private val translator = WordTranslator(TranslationTarget.ROMANIAN.code)
    private var suggestJob: Job? = null

    /** Catalan to English, only for asking Openverse, whose photos are tagged in English. */
    private var english: WordTranslator? = null
    private var pictureJob: Job? = null

    init {
        viewModelScope.launch {
            val deck = flashcards.observeDeck(deckId).first()
            val card = cardId?.let { flashcards.card(it) }
            loaded = card
            storedImage = card?.imagePath
            _uiState.update {
                if (card == null) {
                    it.copy(isLoading = false, deckName = deck?.name.orEmpty())
                } else {
                    it.copy(
                        isLoading = false,
                        deckName = deck?.name.orEmpty(),
                        catalan = card.catalan,
                        romanian = Suggested.owned(card.romanian),
                        ipa = savedPronunciation(card),
                        imagePath = card.imagePath,
                        imageCredit = card.imageCredit,
                    )
                }
            }
            // A card opened without a picture is offered some straight away;
            // one that has a picture is not asked about until it is removed.
            if (card != null && card.imagePath == null) suggestPictures(card.catalan, pause = false)
            baseline = Baseline(
                catalan = card?.catalan.orEmpty(),
                romanian = card?.romanian.orEmpty(),
                ipa = card?.ipa.orEmpty(),
                imagePath = card?.imagePath,
            )
        }
    }

    /**
     * A saved pronunciation that is still exactly what the transcriber makes of
     * the word is still the transcriber's, and follows the word if the spelling
     * is corrected. One the reader changed is theirs.
     */
    private fun savedPronunciation(card: FlashcardEntity): Suggested {
        val ipa = card.ipa.orEmpty()
        val generated = CatalanIpa.transcribe(card.catalan)
        return if (ipa.isNotEmpty() && ipa == generated.ipa) {
            Suggested(ipa, isSuggestion = true, isApproximate = card.ipaApproximate)
        } else {
            Suggested.owned(ipa)
        }
    }

    // ---- Typing ------------------------------------------------------------

    fun onCatalanChange(text: String) {
        val pronunciation = CatalanIpa.transcribe(text)
        updateForm {
            it.copy(
                catalan = text,
                ipa = it.ipa.offer(pronunciation.ipa, pronunciation.isApproximate),
                // The word the last meaning was suggested for has gone.
                romanian = if (text.isBlank()) it.romanian.offer("") else it.romanian,
            )
        }
        suggestMeaning(text.trim())
        suggestPictures(text)
    }

    /*
     * Clearing either field leaves it empty rather than refilling it on the
     * spot: selecting everything and deleting it is how somebody starts typing
     * their own, and a suggestion that sprang back mid-gesture would fight them.
     * An empty field takes the next suggestion when the Catalan next changes.
     */

    fun onRomanianChange(text: String) {
        updateForm { it.copy(romanian = it.romanian.typed(text)) }
    }

    fun onIpaChange(text: String) {
        updateForm { it.copy(ipa = it.ipa.typed(text)) }
    }

    /**
     * Ask the translator for a Romanian meaning, after a pause in the typing.
     *
     * The pause is so that *p*, *pa* and *pan* are not three trips to the
     * model. Each new keystroke cancels the last request, and an answer that
     * arrives for a word the reader has since changed is thrown away.
     */
    private fun suggestMeaning(word: String) {
        suggestJob?.cancel()
        if (word.isEmpty() || !_uiState.value.romanian.acceptsSuggestions) return
        suggestJob = viewModelScope.launch {
            delay(SUGGEST_DELAY_MS)
            if (!translator.isModelReady) {
                val ready = runCatchingCancellable {
                    translator.ensureModel(requireWifi = true) {
                        _uiState.update { it.copy(meaningSuggestion = MeaningSuggestion.DOWNLOADING) }
                    }
                }
                if (ready.isFailure) {
                    _uiState.update { it.copy(meaningSuggestion = MeaningSuggestion.NEEDS_MODEL) }
                    return@launch
                }
                _uiState.update { it.copy(meaningSuggestion = MeaningSuggestion.IDLE) }
            }
            val meaning = runCatchingCancellable { translator.translate(word) }
                .getOrNull()?.trim().orEmpty()
                .let { matchLeadingCase(word, it) }
            // A word handed back unchanged is the translator not knowing it,
            // not a Romanian word that happens to be spelled the same.
            if (meaning.isEmpty() || meaning.equals(word, ignoreCase = true)) return@launch
            updateForm {
                if (it.catalan.trim() == word) it.copy(romanian = it.romanian.offer(meaning)) else it
            }
        }
    }

    // ---- The picture -------------------------------------------------------

    /**
     * Look for pictures of [word], after a pause in the typing.
     *
     * Only while the card has no picture: the row is only on screen then, and
     * a search whose answer nobody will see is a word sent to a server for
     * nothing. Removing the picture asks again.
     */
    private fun suggestPictures(word: String, pause: Boolean = true) {
        pictureJob?.cancel()
        val trimmed = word.trim()
        val source = _uiState.value.pictureSource
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(pictures = PictureSuggestions(source = source)) }
            return
        }
        if (_uiState.value.imagePath != null) return
        pictureJob = viewModelScope.launch {
            if (pause) delay(PICTURE_DELAY_MS)
            _uiState.update {
                it.copy(
                    pictures = PictureSuggestions(PictureStatus.SEARCHING, word = trimmed, source = source),
                )
            }
            val query = when (source) {
                PictureSource.PICTOGRAMS -> trimmed
                PictureSource.PHOTOS -> englishFor(trimmed) ?: run {
                    _uiState.update {
                        it.copy(pictures = PictureSuggestions(PictureStatus.NEEDS_ENGLISH, word = trimmed, source = source))
                    }
                    return@launch
                }
            }
            val found = when (val outcome = pictureSearch.search(source, query)) {
                PictureSearch.Outcome.Offline ->
                    PictureSuggestions(PictureStatus.OFFLINE, word = trimmed, source = source)

                is PictureSearch.Outcome.Found -> PictureSuggestions(
                    status = if (outcome.hits.isEmpty()) PictureStatus.NONE_FOUND else PictureStatus.FOUND,
                    hits = outcome.hits,
                    word = trimmed,
                    source = source,
                )
            }
            // Thrown away if the word moved on while the answer was coming.
            _uiState.update { if (it.catalan.trim() == trimmed) it.copy(pictures = found) else it }
        }
    }

    /** The word in English, for Openverse; null when the model is not on the phone. */
    private suspend fun englishFor(word: String): String? {
        val english = english ?: WordTranslator(TranslationTarget.ENGLISH.code).also { english = it }
        if (!english.isModelReady) {
            runCatchingCancellable { english.ensureModel(requireWifi = true) }
                .onFailure { return null }
        }
        return runCatchingCancellable { english.translate(word) }.getOrNull()
            ?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun onPictureSourceChange(source: PictureSource) {
        if (source == _uiState.value.pictureSource) return
        _uiState.update { it.copy(pictureSource = source) }
        suggestPictures(_uiState.value.catalan, pause = false)
    }

    /** Try the search again, after "no connection". */
    fun onRetryPictures() = suggestPictures(_uiState.value.catalan, pause = false)

    /**
     * Put a suggested picture on the card.
     *
     * Downloaded, then brought in through exactly the same door as a photo from
     * the phone — shrunk, turned upright, saved as a JPEG this app wrote — and
     * the download deleted. The credit comes with it.
     */
    fun onPickSuggestion(hit: PictureHit) {
        if (_uiState.value.fetchingPicture != null) return
        _uiState.update { it.copy(fetchingPicture = hit.id) }
        viewModelScope.launch {
            val stored = runCatchingCancellable {
                val file = pictureSearch.download(hit)
                try {
                    flashcards.importImage(Uri.fromFile(file))
                } finally {
                    file.delete()
                }
            }
            stored.onSuccess { path ->
                discardUnsavedImage()
                updateForm { it.copy(imagePath = path, imageCredit = hit.credit, fetchingPicture = null) }
            }.onFailure { error ->
                Log.w(TAG, "Could not fetch picture ${hit.fullUrl}", error)
                _uiState.update { it.copy(fetchingPicture = null) }
                _message.value = UiText.of(R.string.flashcards_picture_fetch_failed)
            }
        }
    }

    fun onImagePicked(uri: Uri) {
        _uiState.update { it.copy(isImporting = true) }
        viewModelScope.launch {
            val imported = runCatchingCancellable { flashcards.importImage(uri) }
            imported.onSuccess { path ->
                discardUnsavedImage()
                // The reader's own photo: nobody else to credit.
                updateForm { it.copy(imagePath = path, imageCredit = null, isImporting = false) }
            }.onFailure { error ->
                Log.w(TAG, "Could not import picture $uri", error)
                _uiState.update { it.copy(isImporting = false) }
                _message.value = UiText.of(R.string.flashcards_image_failed)
            }
        }
    }

    fun onRemoveImage() {
        discardUnsavedImage()
        updateForm { it.copy(imagePath = null, imageCredit = null) }
        // The row comes back; offer pictures for the word as it is now.
        val state = _uiState.value
        if (state.pictures.word != state.catalan.trim() || state.pictures.source != state.pictureSource) {
            suggestPictures(state.catalan, pause = false)
        }
    }

    /** Delete the picture on screen if it is the form's own rather than the card's. */
    private fun discardUnsavedImage() {
        val current = _uiState.value.imagePath ?: return
        if (current != storedImage) viewModelScope.launch { flashcards.deleteImage(current) }
    }

    // ---- Saving ------------------------------------------------------------

    fun onSave() = save(addAnother = false)

    /** Save, then an empty form for the next card, for filling a deck in one go. */
    fun onSaveAndAddAnother() = save(addAnother = true)

    private fun save(addAnother: Boolean) {
        val state = _uiState.value
        if (!state.canSave) return
        val ipa = tidyIpa(state.ipa.text)
        val card = (loaded ?: FlashcardEntity(deckId = deckId, catalan = "", romanian = "")).copy(
            catalan = state.catalan.trim(),
            romanian = state.romanian.text.trim(),
            ipa = ipa.ifEmpty { null },
            ipaApproximate = ipa.isNotEmpty() && state.ipa.isSuggestion && state.ipa.isApproximate,
            imagePath = state.imagePath,
            imageCredit = state.imageCredit.takeIf { state.imagePath != null },
        )
        // Counted as the card's from here, before the write rather than after
        // it: leaving the form mid-save must not delete a picture the saved row
        // is about to point at. If the write never lands, the file is a stray
        // and the sweep collects it.
        val replaced = storedImage
        storedImage = card.imagePath
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            flashcards.saveCard(card)
            // The old picture goes only now that nothing points at it.
            if (replaced != null && replaced != card.imagePath) flashcards.deleteImage(replaced)

            if (addAnother) {
                suggestJob?.cancel()
                pictureJob?.cancel()
                loaded = null
                storedImage = null
                baseline = Baseline()
                _uiState.update {
                    CardEditorUiState(
                        isLoading = false,
                        deckName = it.deckName,
                        // The source is a preference about pictures, not about
                        // the card just saved, so the next card keeps it.
                        pictureSource = it.pictureSource,
                        pictures = PictureSuggestions(source = it.pictureSource),
                    )
                }
                _message.value = UiText.of(R.string.flashcards_card_saved, card.catalan)
                _cleared.update { it + 1 }
            } else {
                _finished.value = true
            }
        }
    }

    fun onDelete() {
        val card = loaded ?: return
        viewModelScope.launch {
            discardUnsavedImage()
            flashcards.deleteCard(card)
            storedImage = null
            _finished.value = true
        }
    }

    fun onMessageShown() {
        _message.value = null
    }

    /** Any change to the words or picture goes through here, to keep [CardEditorUiState.isDirty] true. */
    private fun updateForm(change: (CardEditorUiState) -> CardEditorUiState) {
        _uiState.update { old ->
            val new = change(old)
            new.copy(
                isDirty = new.catalan != baseline.catalan ||
                    new.romanian.text != baseline.romanian ||
                    new.ipa.text != baseline.ipa ||
                    new.imagePath != baseline.imagePath,
            )
        }
    }

    /**
     * The form is gone. A picture copied in for a card that was never saved is
     * deleted now, on this thread: the coroutines that would have done it have
     * already been cancelled.
     */
    override fun onCleared() {
        val current = _uiState.value.imagePath
        if (current != null && current != storedImage) flashcards.deleteImageNow(current)
        translator.close()
        english?.close()
    }

    companion object {
        private const val TAG = "CardEditor"

        /** Long enough to be a pause, short enough to feel like an answer. */
        private const val SUGGEST_DELAY_MS = 500L

        /**
         * A little longer than the meaning's pause: this one goes to a server,
         * so it waits until the word has plainly stopped changing.
         */
        private const val PICTURE_DELAY_MS = 700L

        fun factory(deckId: Long, cardId: Long?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as LlegeixApp
                CardEditorViewModel(app.flashcardRepository, app.pictureSearch, deckId, cardId)
            }
        }
    }
}
