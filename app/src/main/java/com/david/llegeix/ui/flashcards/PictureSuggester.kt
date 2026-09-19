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

/** How the row of suggested pictures stands. */
enum class PictureStatus {
    /** No word yet, so nothing to look for. */
    WAITING,
    SEARCHING,
    FOUND,
    NONE_FOUND,

    /** No network, or the service did not answer. */
    OFFLINE,

    /** Photos are searched in English, and no English word could be had for this one. */
    NO_ENGLISH,

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
)

/**
 * Suggested pictures for a word: searching, and bringing the chosen one in.
 *
 * Shared by the card form and the deck's picture, so the two search the same
 * way and are held to the same safety rules.
 *
 * ### Which word photos are searched with
 *
 * Openverse's photos are tagged in English. Translating a lone Catalan word
 * into English is where a translator is weakest — *pa* comes back as "pa", and
 * the photos are of Pennsylvania — so the English is taken, in order, from:
 * the English the reader has on the card; the translator, if it actually
 * translated; or the translator again from the Romanian meaning, which the
 * reader has checked and which is a whole word rather than two letters.
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

    private data class Request(val word: String, val english: String?, val romanian: String?)

    /**
     * Look for pictures of [word], after a pause if [pause].
     *
     * @param english the English meaning, if the reader has one on the card.
     * @param romanian the Romanian meaning, for working one out if not.
     */
    fun suggest(word: String, english: String? = null, romanian: String? = null, pause: Boolean = true) {
        job?.cancel()
        val trimmed = word.trim()
        val source = _source.value
        last = Request(trimmed, english?.trim()?.takeIf { it.isNotEmpty() }, romanian?.trim()?.takeIf { it.isNotEmpty() })
        if (trimmed.isEmpty()) {
            _state.value = PictureSuggestions(source = source)
            return
        }
        val request = last!!
        job = scope.launch {
            if (pause) delay(PAUSE_MS)
            _state.value = PictureSuggestions(PictureStatus.SEARCHING, word = trimmed, source = source)
            val query = when (source) {
                PictureSource.PICTOGRAMS -> trimmed
                PictureSource.PHOTOS -> photoQuery(request) ?: run {
                    _state.value = PictureSuggestions(PictureStatus.NO_ENGLISH, word = trimmed, source = source)
                    return@launch
                }
            }
            if (source == PictureSource.PHOTOS &&
                (PictureSafety.isBlockedQuery(query) || PictureSafety.isBlockedQuery(trimmed))
            ) {
                _state.value = PictureSuggestions(PictureStatus.BLOCKED, word = trimmed, source = source)
                return@launch
            }
            _state.value = when (val outcome = search.search(source, query)) {
                PictureSearch.Outcome.Offline ->
                    PictureSuggestions(PictureStatus.OFFLINE, word = trimmed, source = source)

                is PictureSearch.Outcome.Found -> PictureSuggestions(
                    status = if (outcome.hits.isEmpty()) PictureStatus.NONE_FOUND else PictureStatus.FOUND,
                    hits = outcome.hits,
                    word = trimmed,
                    source = source,
                )
            }
        }
    }

    private suspend fun photoQuery(request: Request): String? {
        request.english?.let { return it }
        englishFor(request.word)
            ?.takeIf { !it.equals(request.word, ignoreCase = true) }
            ?.let { return it }
        return request.romanian?.let { englishFromRomanian(it) }
    }

    fun setSource(source: PictureSource) {
        if (source == _source.value) return
        _source.value = source
        again()
    }

    /** Search once more for the last word: after "no connection", or a change of source. */
    fun again() {
        last?.let { suggest(it.word, it.english, it.romanian, pause = false) }
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
    }
}
