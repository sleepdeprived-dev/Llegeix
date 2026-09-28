package com.david.llegeix.ui.flashcards

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.david.llegeix.LlegeixApp
import com.david.llegeix.data.db.entity.FlashcardEntity
import com.david.llegeix.data.flashcards.FlashcardRepository
import com.david.llegeix.data.flashcards.PictureHit
import com.david.llegeix.data.flashcards.PictureSearch
import com.david.llegeix.data.flashcards.PictureSource
import com.david.llegeix.data.flashcards.Suggested
import com.david.llegeix.data.flashcards.matchLeadingCase
import com.david.llegeix.data.flashcards.tidyIpa
import com.david.llegeix.lang.CatalanIpa
import com.david.llegeix.resources.*
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

/** Where a meaning's suggestion has got to, for the line under its field. */
enum class MeaningSuggestion {
    IDLE,

    /** The language model is being fetched, once, over Wi-Fi. */
    DOWNLOADING,

    /** The model is not on the phone and could not be fetched just now. */
    NEEDS_MODEL,
}

/** Where a suggested meaning came from, so the field can say how far to trust it. */
enum class MeaningSource {
    /** ARASAAC's labels, written by people for this very word. */
    DICTIONARY,

    /** The on-device translator, handed one word with no sentence around it. */
    TRANSLATOR,
}

data class CardEditorUiState(
    val isLoading: Boolean = true,
    val deckName: String = "",
    /** False when editing a card that already exists. */
    val isNew: Boolean = true,
    val catalan: String = "",
    /**
     * The Catalan was filled in by the app, from a meaning the reader typed
     * first, rather than typed by them. It follows that meaning until the
     * reader writes in the Catalan field themselves, like any other suggestion.
     */
    val catalanIsSuggestion: Boolean = false,
    val romanian: Suggested = Suggested(),
    val ipa: Suggested = Suggested(),
    val imagePath: String? = null,
    /** Who made the picture, when it came from a search; null for the reader's own. */
    val imageCredit: String? = null,
    val isImporting: Boolean = false,
    /** A save is being written; a second press must not make a second card. */
    val isSaving: Boolean = false,
    val romanianSuggestion: MeaningSuggestion = MeaningSuggestion.IDLE,
    val romanianSource: MeaningSource = MeaningSource.TRANSLATOR,
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
 * the spelling and costs nothing. The Romanian meaning waits for a pause in the
 * typing and then asks the on-device translator. Both go into their fields
 * as [Suggested] values, marked as the app's, and stop moving the moment the
 * reader types in the field. A single word handed to a translator with no
 * sentence around it is exactly where a translator is weakest, so a suggestion
 * is never more than that.
 *
 * ### Pictures and their files
 *
 * A chosen picture is copied in straight away, so the form can show it. That
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

    /** Bumped after "save and new", so the screen can refocus the word. */
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

    // Each translator is bound to its language pair, and each is only made
    // when first needed. The two into English are the photo search's alone:
    // photos are tagged in English, and the English is never shown or kept.
    private val toRomanian = Translator("ro")
    private val toEnglish = Translator("en")
    private val romanianToEnglish = Translator("en", source = "ro")
    private val romanianToCatalan = Translator("ca", source = "ro")

    private var romanianJob: Job? = null
    private var fromMeaningJob: Job? = null

    val pictures = PictureSuggester(
        scope = viewModelScope,
        search = pictureSearch,
        flashcards = flashcards,
        englishFor = { toEnglish.translate(it) },
        englishFromRomanian = { romanianToEnglish.translate(it) },
    )

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
            baseline = Baseline(
                catalan = card?.catalan.orEmpty(),
                romanian = card?.romanian.orEmpty(),
                ipa = card?.ipa.orEmpty(),
                imagePath = card?.imagePath,
            )
            // One opened without a picture is offered some straight away;
            // one that has a picture is not asked about until it is removed.
            if (card != null && card.imagePath == null) suggestPictures(pause = false)
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
        // Typed, so the Catalan is the reader's from here on, and the meanings
        // follow it rather than the other way round.
        fromMeaningJob?.cancel()
        val pronunciation = CatalanIpa.transcribe(text)
        val movedOn = text.trim() != loaded?.catalan?.trim().orEmpty()
        updateForm {
            it.copy(
                catalan = text,
                catalanIsSuggestion = false,
                ipa = it.ipa.offer(pronunciation.ipa, pronunciation.isApproximate),
                // The word the last meanings were suggested for has gone.
                romanian = when {
                    text.isBlank() -> it.romanian.offer("")
                    movedOn -> releaseStale(it.romanian, loaded?.romanian)
                    else -> it.romanian
                },
            )
        }
        suggestRomanian(text.trim())
        suggestPictures()
    }

    /**
     * A meaning of the word that used to be here is not a meaning of the one
     * that is here now.
     *
     * Opening a saved card marks every field as the reader's, which is right
     * for what it protects — nobody's hand-written meaning should be quietly
     * overwritten — and was wrong about one case that turned out to be the
     * common one. Change *pa* to *llet* on a card you wrote last week and the
     * Romanian went on saying *pâine*: the form only ever filled the meanings
     * in on a card's first draft, because after a save they were all "theirs".
     *
     * So a field that still holds exactly what was saved in it is handed back
     * the moment the Catalan moves off the word it was saved for. One the
     * reader has touched this time round does not match what was saved, so it
     * stays theirs; and what replaces a released field arrives marked as the
     * app's suggestion, to be typed over like any other.
     *
     * The same rule the pronunciation has always followed — see
     * [savedPronunciation] — applied to the meanings.
     */
    private fun releaseStale(field: Suggested, saved: String?): Suggested =
        if (!field.isSuggestion && field.text == saved.orEmpty() && field.text.isNotBlank()) {
            field.copy(isSuggestion = true)
        } else {
            field
        }

    /*
     * Clearing a field leaves it empty rather than refilling it on the spot:
     * selecting everything and deleting it is how somebody starts typing their
     * own, and a suggestion that sprang back mid-gesture would fight them. An
     * empty field takes the next suggestion when the Catalan next changes.
     */

    fun onRomanianChange(text: String) {
        updateForm { it.copy(romanian = it.romanian.typed(text)) }
        suggestFromMeaning(text.trim())
    }

    fun onIpaChange(text: String) {
        updateForm { it.copy(ipa = it.ipa.typed(text)) }
    }

    /**
     * Photos are searched with an English worked out from the Romanian when the
     * Catalan alone does not give one, so a meaning the reader has just
     * corrected is the moment to search again — once they stop.
     */
    fun onMeaningEditingDone() {
        if (pictures.source.value == PictureSource.PHOTOS) suggestPictures(pause = false)
    }

    /**
     * The card written the other way round: a Romanian meaning typed first
     * fills in the Catalan — and with it the pronunciation and the pictures —
     * the same way typing the Catalan fills in the meaning.
     *
     * Only into fields that are still waiting for a suggestion: an empty
     * Catalan, or one the app filled in itself; never one the reader typed.
     * ARASAAC's people are asked first, and the translator only for words
     * they have not labelled.
     */
    private fun suggestFromMeaning(word: String) {
        fromMeaningJob?.cancel()
        val state = _uiState.value
        // And the same the other way round: a Catalan still exactly as it was
        // saved is a Catalan for the meaning that was saved with it, so
        // changing that meaning releases it. See [releaseStale].
        val untouchedSince = state.catalan == loaded?.catalan && word != loaded?.romanian?.trim().orEmpty()
        val catalanOpen = state.catalan.isBlank() || state.catalanIsSuggestion || untouchedSince
        if (!catalanOpen) return
        if (word.isEmpty()) {
            // The meaning it came from has gone, so the suggested Catalan goes too.
            if (state.catalanIsSuggestion) applyCatalanSuggestion("")
            return
        }
        fromMeaningJob = viewModelScope.launch {
            delay(SUGGEST_DELAY_MS)
            val labelled = pictureSearch.labels(word, "ro")
            val catalan = labelled?.catalan
                ?: romanianToCatalan.translate(word)?.takeIf { !it.equals(word, ignoreCase = true) }
            if (catalan != null && _uiState.value.romanian.text.trim() == word) {
                applyCatalanSuggestion(matchLeadingCase(word, catalan))
            }
        }
    }

    /**
     * Put a suggested Catalan in its field, with the pronunciation that goes
     * with it and pictures of it, without asking for meanings back: they are
     * what it was worked out from.
     */
    private fun applyCatalanSuggestion(catalan: String) {
        val pronunciation = CatalanIpa.transcribe(catalan)
        updateForm {
            it.copy(
                catalan = catalan,
                catalanIsSuggestion = catalan.isNotEmpty(),
                ipa = it.ipa.offer(pronunciation.ipa, pronunciation.isApproximate),
            )
        }
        suggestPictures()
    }

    /**
     * Ask the translator for the Romanian meaning, after a pause in the typing.
     *
     * The pause is so that *p*, *pa* and *pan* are not three trips to the
     * model. Each new keystroke cancels the last request, and an answer that
     * arrives for a word the reader has since changed is thrown away.
     */
    private fun suggestRomanian(word: String) {
        romanianJob?.cancel()
        if (word.isEmpty() || !_uiState.value.romanian.acceptsSuggestions) return
        romanianJob = viewModelScope.launch {
            delay(SUGGEST_DELAY_MS)
            // People's words first; the translator only for what they have not labelled.
            val labelled = pictureSearch.meanings(word)?.romanian
            val meaning = labelled ?: toRomanian.suggest(word) { status ->
                _uiState.update { it.copy(romanianSuggestion = status) }
            } ?: return@launch
            val source = if (labelled != null) MeaningSource.DICTIONARY else MeaningSource.TRANSLATOR
            updateForm {
                if (it.catalan.trim() == word) {
                    it.copy(romanian = it.romanian.offer(matchLeadingCase(word, meaning)), romanianSource = source)
                } else {
                    it
                }
            }
        }
    }

    // ---- The picture -------------------------------------------------------

    private fun suggestPictures(pause: Boolean = true) {
        val state = _uiState.value
        // Only while the card has no picture: the row is only on screen then,
        // and a search nobody will see is a word sent to a server for nothing.
        if (state.imagePath != null) {
            pictures.cancel()
            return
        }
        pictures.suggest(
            word = state.catalan,
            romanian = state.romanian.text,
            pause = pause,
        )
    }

    fun onPictureSourceChange(source: PictureSource) = pictures.setSource(source)

    fun onSearchPictures(text: String) = pictures.searchFor(text)

    fun onRetryPictures() = pictures.again()

    /** Put a suggested picture on the card; the credit comes with it. */
    fun onPickSuggestion(hit: PictureHit) {
        if (pictures.state.value.fetching != null) return
        viewModelScope.launch {
            runCatchingCancellable { pictures.fetch(hit) }
                .onSuccess { path ->
                    discardUnsavedImage()
                    updateForm { it.copy(imagePath = path, imageCredit = hit.credit) }
                }
                .onFailure { error ->
                    Log.w(TAG, "Could not fetch picture ${hit.fullUrl}", error)
                    _message.value = UiText.of(Res.string.flashcards_picture_fetch_failed)
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
                _message.value = UiText.of(Res.string.flashcards_image_failed)
            }
        }
    }

    fun onRemoveImage() {
        discardUnsavedImage()
        updateForm { it.copy(imagePath = null, imageCredit = null) }
        // The grid comes back; offer pictures for the word as it is now.
        if (!pictures.isFor(_uiState.value.catalan)) suggestPictures(pause = false)
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
            english = null,
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
                romanianJob?.cancel()
                pictures.suggest("")
                loaded = null
                storedImage = null
                baseline = Baseline()
                _uiState.update { CardEditorUiState(isLoading = false, deckName = it.deckName) }
                _message.value = UiText.of(Res.string.flashcards_card_saved, card.catalan)
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
        toRomanian.close()
        toEnglish.close()
        romanianToEnglish.close()
        romanianToCatalan.close()
    }

    /**
     * One on-device translator, made on first use.
     *
     * [suggest] is the careful form for a field: it says when the model is
     * downloading or missing, fixes the capital, and returns null for a word
     * handed back unchanged — which is the translator not knowing it, not a
     * word spelled the same in both languages.
     */
    private class Translator(private val target: String, private val source: String = "ca") {
        private var client: WordTranslator? = null

        private fun client(): WordTranslator =
            client ?: WordTranslator(targetLanguage = target, sourceLanguage = source).also { client = it }

        suspend fun suggest(word: String, onStatus: (MeaningSuggestion) -> Unit): String? {
            val translator = client()
            if (!translator.isModelReady) {
                val ready = runCatchingCancellable {
                    translator.ensureModel(requireWifi = true) { onStatus(MeaningSuggestion.DOWNLOADING) }
                }
                if (ready.isFailure) {
                    onStatus(MeaningSuggestion.NEEDS_MODEL)
                    return null
                }
                onStatus(MeaningSuggestion.IDLE)
            }
            val meaning = runCatchingCancellable { translator.translate(word) }
                .getOrNull()?.trim().orEmpty()
                .let { matchLeadingCase(word, it) }
            return meaning.takeIf { it.isNotEmpty() && !it.equals(word, ignoreCase = true) }
        }

        /** Plain translation, or null if the model is missing or it fails. */
        suspend fun translate(text: String): String? {
            val translator = client()
            if (!translator.isModelReady) {
                runCatchingCancellable { translator.ensureModel(requireWifi = true) }
                    .onFailure { return null }
            }
            return runCatchingCancellable { translator.translate(text) }.getOrNull()
                ?.trim()?.takeIf { it.isNotEmpty() }
        }

        fun close() {
            client?.close()
        }
    }

    companion object {
        private const val TAG = "CardEditor"

        /** Long enough to be a pause, short enough to feel like an answer. */
        private const val SUGGEST_DELAY_MS = 500L

        fun factory(deckId: Long, cardId: Long?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as LlegeixApp
                CardEditorViewModel(app.flashcardRepository, app.pictureSearch, deckId, cardId)
            }
        }
    }
}
