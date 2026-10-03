package com.david.llegeix.ui.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.david.llegeix.data.db.entity.WordBookmarkEntity
import com.david.llegeix.data.settings.SearchHistoryRepository
import com.david.llegeix.data.settings.SearchScope
import com.david.llegeix.data.settings.SettingsRepository
import com.david.llegeix.data.settings.TranslationTarget
import com.david.llegeix.data.source.LibraryDataRepository
import com.david.llegeix.lang.CatalanIpa
import com.david.llegeix.lang.CatalanWordBank
import com.david.llegeix.lang.VerbForm
import com.david.llegeix.lang.verbEntry
import com.david.llegeix.platform.Services
import com.david.llegeix.resources.*
import com.david.llegeix.translate.WordTranslator
import com.david.llegeix.ui.common.DictionaryState
import com.david.llegeix.ui.common.DictionaryStatus
import com.david.llegeix.ui.common.UiText
import com.david.llegeix.util.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How far the word being looked up has got. */
enum class EntryStatus { TRANSLATING, DOWNLOADING_MODEL, READY, FAILED }

/**
 * One word, looked up on purpose rather than met on a page.
 *
 * Deliberately the same set of facts the reader's sheet shows, minus the two
 * that only a page can supply — the line it came from and where on the page it
 * was. Everything else is the same answer to the same question, so a word
 * checked here and the same word pressed while reading do not look like two
 * different words.
 */
data class DictionaryEntry(
    val word: String,
    val ipa: String,
    /** True when the stressed vowel's aperture had to be guessed. */
    val isIpaApproximate: Boolean,
    val status: EntryStatus,
    val translation: String? = null,
    val error: UiText? = null,
    /** True once a Wi-Fi-only download has failed, so retrying is worth offering. */
    val canRetryOnAnyNetwork: Boolean = false,
    /** Definitions, synonyms and antonyms. Opened straight away on this screen. */
    val reference: DictionaryState = DictionaryState(DictionaryStatus.LOADING),
    val isSaved: Boolean = false,
    /**
     * What the word turns out to be, when it turns out to be a verb.
     *
     * Null for everything else, and null for a verb form the app cannot place
     * — see [com.david.llegeix.lang.CatalanVerbs], which would rather say
     * nothing than guess a tense.
     */
    val verb: VerbForm? = null,
    /** The infinitive translated, which is the meaning somebody wanted. */
    val verbInfinitiveMeaning: String? = null,
    /** The Viccionari's own first definition of the infinitive, in Catalan. */
    val verbDefinition: String? = null,
)

data class DictionaryUiState(
    val query: String = "",
    /** Headwords the query could be the start of, offered while typing. */
    val suggestions: List<String> = emptyList(),
    /** The word currently being shown, or null while the reader is still asking. */
    val entry: DictionaryEntry? = null,
    val translationTarget: TranslationTarget = TranslationTarget.Default,
)

/**
 * The dictionary as a place you go, rather than something that happens to a
 * word you pressed.
 *
 * The reader's lookup answers "what is this word doing here". This answers "what
 * about this word" — a word half-remembered from a conversation, one you meant
 * to check later, one you cannot find in the book in front of you. It is the
 * same machinery underneath: the same bundled Viccionari and thesaurus, the same
 * rule-based pronunciation, the same on-device translator, and the same saved
 * words list at the end of it.
 */
class DictionaryViewModel(
    private val libraryData: LibraryDataRepository,
    private val settings: SettingsRepository,
    private val searchHistory: SearchHistoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        DictionaryUiState(translationTarget = settings.current.translationTarget),
    )
    val uiState: StateFlow<DictionaryUiState> = _uiState.asStateFlow()

    /**
     * The same pile of past searches the reader's find bar and the saved words
     * keep. A word chased across a page, a word kept in your own list and a word
     * looked up here are the same word to the person doing it.
     */
    val recentSearches: StateFlow<List<String>> = searchHistory.history(SearchScope.WORDS)

    fun onForgetSearches() = searchHistory.forget(SearchScope.WORDS)

    /** Drop one past search, from the cross on its row. */
    fun onForgetSearch(query: String) = searchHistory.forgetOne(SearchScope.WORDS, query)

    /**
     * Rebuilt when the target language changes: an ML Kit translator is bound to
     * its language pair at construction, so switching target means a new one.
     */
    private var translator = WordTranslator(settings.current.translationTarget.code)

    private var suggestJob: Job? = null
    private var lookupJob: Job? = null

    init {
        viewModelScope.launch {
            settings.settings.collect { current ->
                _uiState.update { it.copy(translationTarget = current.translationTarget) }
                if (translator.targetLanguage != current.translationTarget.code) {
                    translator.close()
                    translator = WordTranslator(current.translationTarget.code)
                    // A word on screen at the moment of the switch should answer
                    // in the new language rather than keep the old answer.
                    _uiState.value.entry?.let { open -> lookUp(open.word) }
                }
            }
        }
    }

    // ---- Asking ------------------------------------------------------------

    /**
     * Typing puts the screen back to asking.
     *
     * The entry is dropped rather than left underneath the suggestions: a
     * definition of the previous word sitting under a half-typed new one is a
     * screen showing two answers, one of which is wrong.
     */
    fun onQueryChange(query: String) {
        if (_uiState.value.entry != null) lookupJob?.cancel()
        _uiState.update { it.copy(query = query, entry = null) }
        suggestJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(suggestions = emptyList()) }
            return
        }
        suggestJob = viewModelScope.launch {
            // Long enough that a word is not looked up letter by letter, short
            // enough that the list is there by the time the finger stops.
            delay(SUGGEST_DEBOUNCE_MS)
            val found = withContext(Dispatchers.IO) {
                CatalanWordBank.get().suggest(query)
            }
            _uiState.update { if (it.query == query) it.copy(suggestions = found) else it }
        }
    }

    /** Look up whatever is in the field, for the keyboard's search key. */
    fun onSubmit() {
        val query = _uiState.value.query.trim()
        if (query.isNotEmpty()) lookUp(query)
    }

    /** Look up a word offered by the suggestion list. */
    fun onPickSuggestion(word: String) {
        _uiState.update { it.copy(query = word) }
        lookUp(word)
    }

    /** Put the screen back to asking, for the back button and the clear action. */
    fun onCloseEntry() {
        lookupJob?.cancel()
        _uiState.update { it.copy(entry = null) }
    }

    // ---- Answering ---------------------------------------------------------

    /**
     * Show [word], filling the answer in as each part of it arrives.
     *
     * The pronunciation is there immediately because it is generated from the
     * spelling and costs nothing. The references and the translation both take a
     * moment — the first to read sixteen megabytes of assets the first time, the
     * second to ask the on-device model — so they land in whichever order they
     * are ready, under a heading that is already on screen.
     */
    private fun lookUp(word: String) {
        lookupJob?.cancel()
        val pronunciation = CatalanIpa.transcribe(word)
        _uiState.update {
            it.copy(
                suggestions = emptyList(),
                entry = DictionaryEntry(
                    word = word,
                    ipa = pronunciation.ipa,
                    isIpaApproximate = pronunciation.isApproximate,
                    status = EntryStatus.TRANSLATING,
                ),
            )
        }

        lookupJob = viewModelScope.launch {
            val saved = libraryData.findWordBookmark(word, null, DICTIONARY_PAGE) != null
            updateEntry { it.copy(isSaved = saved) }

            // Asked before the references and the translation rather than
            // beside them. It reads the same three files the references do, so
            // on the first lookup of a run it is the sixteen-megabyte read
            // either way; every lookup after that it is two binary searches,
            // and having the answer in hand is what lets the infinitive be
            // translated straight afterwards without a second round of waiting.
            val found = withContext(Dispatchers.IO) {
                CatalanWordBank.get().verbEntry(word)
            }
            if (found != null) {
                updateEntry { it.copy(verb = found.form, verbDefinition = found.definition) }
            }

            launch { loadReference(word) }
            translate(word)

            // The meaning of the infinitive, which for a conjugated form is
            // the thing actually being looked up. Only once the model has
            // already answered for the written form: if it could not, a second
            // failed request would say nothing new.
            val form = found?.form
            if (form != null && !form.isInfinitive && translator.isModelReady) {
                runCatchingCancellable { translator.translate(form.infinitive) }
                    .getOrNull()
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { meaning -> updateEntry { it.copy(verbInfinitiveMeaning = meaning) } }
            }
        }
    }


    private suspend fun loadReference(word: String) {
        val found = withContext(Dispatchers.IO) {
            val bank = CatalanWordBank.get()
            // A phrase is looked up word by word, keeping only the words the
            // references actually know, so "de seguida" answers with what it can
            // rather than with nothing at all.
            val words = word.split(WORD_SPLIT).filter { it.isNotBlank() }
            val asked = if (words.size > 1) words.take(MAX_PHRASE_WORDS) else listOf(word)
            asked.filter { CatalanWordBank.isWorthLookingUp(it) }
                .mapNotNull { bank.lookup(it) }
                .filterNot { it.isEmpty }
        }
        updateEntry { it.copy(reference = DictionaryState(DictionaryStatus.READY, found)) }
        if (found.isNotEmpty()) searchHistory.record(SearchScope.WORDS, word)
    }

    private suspend fun translate(word: String, requireWifi: Boolean = true) {
        if (!translator.isModelReady) {
            val downloaded = runCatchingCancellable {
                translator.ensureModel(requireWifi) {
                    updateEntry { it.copy(status = EntryStatus.DOWNLOADING_MODEL) }
                }
            }
            if (downloaded.isFailure) {
                updateEntry {
                    it.copy(
                        status = EntryStatus.FAILED,
                        canRetryOnAnyNetwork = requireWifi,
                        error = UiText.of(
                            if (requireWifi) {
                                Res.string.lookup_model_wifi_failed
                            } else {
                                Res.string.lookup_model_failed
                            },
                        ),
                    )
                }
                return
            }
        }

        runCatchingCancellable { translator.translate(word) }
            .onSuccess { translation ->
                updateEntry { it.copy(status = EntryStatus.READY, translation = translation) }
                searchHistory.record(SearchScope.WORDS, word)
            }
            .onFailure { error ->
                updateEntry {
                    it.copy(
                        status = EntryStatus.FAILED,
                        error = UiText.of(Res.string.lookup_failed),
                    )
                }
            }
    }

    /** Retry a failed lookup without insisting on Wi-Fi. */
    fun onRetryOnAnyNetwork() {
        val word = _uiState.value.entry?.word ?: return
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            updateEntry {
                it.copy(status = EntryStatus.TRANSLATING, error = null, canRetryOnAnyNetwork = false)
            }
            translate(word, requireWifi = false)
        }
    }

    /**
     * Switch which language the screen translates into.
     *
     * The same setting Configuració writes and the reader's sheet toggles, so a
     * switch made here is the switch the app keeps.
     */
    fun onToggleTranslationTarget() {
        val current = _uiState.value.translationTarget
        val next = TranslationTarget.entries
            .getOrNull(current.ordinal + 1) ?: TranslationTarget.entries.first()
        settings.setTranslationTarget(next)
    }

    // ---- Keeping -----------------------------------------------------------

    /**
     * Save the word, or drop it if it is already saved.
     *
     * It lands in the same list as a word starred while reading, with no
     * document behind it: there was no page, and pretending otherwise would put
     * a "page 1, line 1" on an entry that was never on a page.
     */
    fun onToggleSaved() = viewModelScope.launch {
        val entry = _uiState.value.entry ?: return@launch
        val saved = libraryData.toggleWordBookmark(
            WordBookmarkEntity(
                word = entry.word,
                translation = entry.translation,
                ipa = entry.ipa,
                context = null,
                documentUri = null,
                displayName = null,
                pageIndex = DICTIONARY_PAGE,
                lineNumber = 0,
            ),
        )
        updateEntry { it.copy(isSaved = saved) }
    }

    /** Apply an edit to the entry on screen, if it is still the one being shown. */
    private fun updateEntry(edit: (DictionaryEntry) -> DictionaryEntry) {
        _uiState.update { state ->
            state.entry?.let { state.copy(entry = edit(it)) } ?: state
        }
    }

    override fun onCleared() {
        translator.close()
    }

    companion object {
        private const val SUGGEST_DEBOUNCE_MS = 180L

        /**
         * The page a dictionary word is filed under.
         *
         * Zero, and paired with a null document, which is what tells the saved
         * words list this one came from the dictionary rather than from a book.
         * The word bookmark table matches on word, document and page together,
         * so a null document and a fixed page make looking one word up twice a
         * toggle rather than a duplicate.
         */
        private const val DICTIONARY_PAGE = 0

        /** Words of a typed phrase looked up in the references. */
        private const val MAX_PHRASE_WORDS = 4

        /** Splits a typed phrase into words, on spaces and punctuation. */
        private val WORD_SPLIT = Regex("[^\\p{L}·'’]+")

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = Services.app
                DictionaryViewModel(
                    app.libraryDataRepository,
                    app.settingsRepository,
                    app.searchHistoryRepository,
                )
            }
        }
    }
}
