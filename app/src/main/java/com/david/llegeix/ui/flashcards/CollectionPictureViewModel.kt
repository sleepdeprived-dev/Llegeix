package com.david.llegeix.ui.flashcards

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.david.llegeix.LlegeixApp
import com.david.llegeix.data.flashcards.FlashcardRepository
import com.david.llegeix.data.flashcards.PictureHit
import com.david.llegeix.data.flashcards.PictureSearch
import com.david.llegeix.data.flashcards.PictureSource
import com.david.llegeix.resources.*
import com.david.llegeix.translate.WordTranslator
import com.david.llegeix.ui.common.UiText
import com.david.llegeix.util.runCatchingCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Choosing a collection's own picture.
 *
 * Deliberately the same as [DeckPictureViewModel] in every way that shows: the
 * same search, asked with the shelf's name, the same grid of suggestions, the
 * same route in from the reader's own photos, and the same rule that the
 * picture is put on the moment it is picked and the sheet closes. A shelf and a
 * deck are two kinds of thing to the database and one kind of thing to the
 * person giving one a picture.
 *
 * Removing the picture is not "no picture" here but "back to borrowing one":
 * a shelf with nothing chosen wears the picture of the first deck on it.
 */
class CollectionPictureViewModel(
    private val flashcards: FlashcardRepository,
    pictureSearch: PictureSearch,
    private val collectionId: Long,
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
            flashcards.observeCollections().collect { collections ->
                collections.firstOrNull { it.id == collectionId }?.let { shelf ->
                    if (!pictures.isFor(shelf.name)) pictures.suggest(shelf.name, pause = false)
                }
            }
        }
    }

    fun onSourceChange(source: PictureSource) = pictures.setSource(source)

    fun onSearchPictures(text: String) = pictures.searchFor(text)

    fun onRetry() = pictures.again()

    fun onPick(hit: PictureHit) = setCover {
        flashcards.setCollectionCover(collectionId, pictures.fetch(hit), hit.credit)
    }

    fun onPickOwn(uri: Uri) = setCover {
        flashcards.setCollectionCover(collectionId, flashcards.importImage(uri), credit = null)
    }

    /** Back to the first deck's picture, or the initial. */
    fun onRemove() = setCover {
        flashcards.setCollectionCover(collectionId, path = null, credit = null)
    }

    private fun setCover(job: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            runCatchingCancellable { job() }
                .onSuccess { _done.value = true }
                .onFailure { error ->
                    Log.w(TAG, "Could not set the collection's picture", error)
                    _message.value = UiText.of(Res.string.flashcards_picture_fetch_failed)
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
        private const val TAG = "CollectionPicture"

        fun factory(collectionId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as LlegeixApp
                CollectionPictureViewModel(
                    app.flashcardRepository,
                    app.pictureSearch,
                    collectionId,
                )
            }
        }
    }
}
