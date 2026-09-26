package com.david.llegeix.ui.flashcards

import android.net.Uri
import com.david.llegeix.data.flashcards.FlashcardRepository
import com.david.llegeix.data.flashcards.PictureHit
import com.david.llegeix.data.flashcards.PictureSafety
import com.david.llegeix.data.flashcards.PictureSearch
import com.david.llegeix.data.flashcards.PictureSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** How the row of suggested pictures stands. */
enum class PictureStatus {
    /** No word yet, so nothing to look for. */
    WAITING,
    SEARCHING,
    FOUND,
    NONE_FOUND,

    /** No network, or the service did not answer. */
    OFFLINE,

    /** The word is on the safety list, so photos are not searched for it at all. */
    BLOCKED,
}

data class PictureSuggestions(
    val status: PictureStatus = PictureStatus.WAITING,
    val hits: List<PictureHit> = emptyList(),
    /** The word these are for, so a stale row is never offered for a new word. */
    val word: String = "",
    val source: PictureSource = PictureSource.PICTOGRAMS,
    /** The suggestion being fetched after a tap, so its tile can say so. */
    val fetching: String? = null,
    /**
     * What was searched for, as the reader knows it — the word, or what they
     * typed — so the search field can show it and they can change it. Never
     * the English photos are looked up with behind the scenes.
     */
    val searched: String = "",
)

/**
 * Suggested pictures for a word: searching, and bringing the chosen one in.
 *
 * Shared by the card form and the deck's picture, so the two search the same
 * way and are held to the same safety rules.
 *
 * ### Which word photos are searched with
 *
 * Photos are tagged in English, and so are most of the pictograms outside
 * ARASAAC's Catalan labels. Translating a lone Catalan word
 * into English is where a translator is weakest — *pa* comes back as "pa", and
 * the photos are of Pennsylvania — so the English is taken, in order, from:
 * the translator, if it actually translated; or the translator again from the
 * Romanian meaning, which the reader has checked and which is a whole word
 * rather than two letters. The English is only ever the search's: it is
 * never shown, and never kept on a card.
 *
 * @param englishFor Catalan to English on the phone, or null if it cannot.
 * @param englishFromRomanian Romanian to English on the phone, the fallback.
 */
class PictureSuggester(
    private val scope: CoroutineScope,
    private val search: PictureSearch,
    private val flashcards: FlashcardRepository,
    private val englishFor: suspend (String) -> String?,
    private val englishFromRomanian: suspend (String) -> String?,
) {
    private val _state = MutableStateFlow(PictureSuggestions())
    val state: StateFlow<PictureSuggestions> = _state.asStateFlow()

    private val _source = MutableStateFlow(PictureSource.PICTOGRAMS)
    val source: StateFlow<PictureSource> = _source.asStateFlow()

    private var job: Job? = null
    private var last: Request? = null

    /**
     * @param typed what the reader typed into the search field, which is
     *   searched in place of the word, for every kind of picture.
     */
    private data class Request(
        val word: String,
        val romanian: String?,
        val typed: String? = null,
    )

    /**
     * Look for pictures of [word], after a pause if [pause].
     *
     * @param romanian the Romanian meaning, for working out the English photos need.
     */
    fun suggest(word: String, romanian: String? = null, pause: Boolean = true) {
        val request = Request(
            word.trim(),
            romanian?.trim()?.takeIf { it.isNotEmpty() },
        )
        // A search the reader typed stands until the word itself changes.
        val kept = last?.typed?.takeIf { last?.word == request.word }
        run(request.copy(typed = kept), pause)
    }

    /**
     * Search for exactly [text], typed into the grid's own search field: for
     * when the word's own pictures are not the ones wanted — *taronja*, but a
     * picture of orange juice.
     */
    fun searchFor(text: String) {
        val base = last ?: Request(word = text.trim(), romanian = null)
        run(base.copy(typed = text.trim().takeIf { it.isNotEmpty() }), pause = false)
    }

    private fun run(request: Request, pause: Boolean) {
        job?.cancel()
        val trimmed = request.word
        val source = _source.value
        last = request
        if (trimmed.isEmpty() && request.typed == null) {
            _state.value = PictureSuggestions(source = source)
            return
        }
        job = scope.launch {
            if (pause) delay(PAUSE_MS)
            val typed = request.typed
            val catalan = typed ?: trimmed
            _state.value = PictureSuggestions(
                PictureStatus.SEARCHING,
                word = trimmed,
                source = source,
                searched = catalan,
            )
            // What was typed is translated like the word it replaces; the
            // Romanian meaning belongs to the word, so it only helps the word.
            val query = if (typed != null) Request(word = typed, romanian = null) else request
            val english = when (source) {
                // Pictograms are found in Catalan; the English only widens the
                // search, so it is not waited on for long.
                PictureSource.PICTOGRAMS, PictureSource.EMOJI -> withTimeoutOrNull(PICTOGRAM_ENGLISH_WAIT_MS) {
                    englishQuery(query)
                }
                // With no English at all, the Catalan itself is searched
                // rather than nothing: Wikipedia is asked in Catalan anyway,
                // and a great many words are spelt the same in both.
                PictureSource.PHOTOS -> englishQuery(query) ?: catalan
            }
            if (source == PictureSource.PHOTOS &&
                (PictureSafety.isBlockedQuery(english.orEmpty()) || PictureSafety.isBlockedQuery(catalan))
            ) {
                _state.value = PictureSuggestions(PictureStatus.BLOCKED, word = trimmed, source = source)
                return@launch
            }
            _state.value = when (val outcome = search.search(source, catalan, english)) {
                PictureSearch.Outcome.Offline ->
                    PictureSuggestions(PictureStatus.OFFLINE, word = trimmed, source = source, searched = catalan)

                is PictureSearch.Outcome.Found -> PictureSuggestions(
                    status = if (outcome.hits.isEmpty()) PictureStatus.NONE_FOUND else PictureStatus.FOUND,
                    hits = outcome.hits,
                    word = trimmed,
                    source = source,
                    searched = catalan,
                )
            }
        }
    }

    /**
     * The English to search with.
     *
     * A translation that comes back as the word itself used to be taken as a
     * failure — *pa* handed back as "pa" is the translator not knowing it —
     * and with no Romanian to fall back on, photos were not searched at all.
     * But *animals*, *hotel* and *taxi* come back as themselves because they
     * *are* the English. So a word given back unchanged is only doubted when
     * it is short enough to be a fragment, and otherwise kept.
     */
    private suspend fun englishQuery(request: Request): String? {
        val translated = englishFor(request.word)
        if (translated != null &&
            (!translated.equals(request.word, ignoreCase = true) || request.word.length > SAME_WORD_MIN)
        ) {
            return translated
        }
        return request.romanian?.let { englishFromRomanian(it) } ?: translated
    }

    fun setSource(source: PictureSource) {
        if (source == _source.value) return
        _source.value = source
        again()
    }

    /** Search once more for the last word: after "no connection", or a change of source. */
    fun again() {
        last?.let { run(it, pause = false) }
    }

    /** Whether the row on show is for [word] as it is now. */
    fun isFor(word: String): Boolean = _state.value.word == word.trim() && _state.value.source == _source.value

    /**
     * Download [hit] and bring it in like a photo from the phone. Returns its
     * stored path; throws if it could not be had.
     */
    suspend fun fetch(hit: PictureHit): String {
        _state.update { it.copy(fetching = hit.id) }
        try {
            val file = search.download(hit)
            try {
                return flashcards.importImage(Uri.fromFile(file))
            } finally {
                file.delete()
            }
        } finally {
            _state.update { it.copy(fetching = null) }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    private companion object {
        /** It goes to a server, so it waits until the word has plainly stopped changing. */
        const val PAUSE_MS = 700L

        /** A translator that has its model answers in well under this. */
        const val PICTOGRAM_ENGLISH_WAIT_MS = 2_500L

        /** Longer than this, a word the translator hands back unchanged is taken as English already. */
        const val SAME_WORD_MIN = 3
    }
}
