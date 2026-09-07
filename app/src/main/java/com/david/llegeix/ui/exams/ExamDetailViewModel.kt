package com.david.llegeix.ui.exams

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.david.llegeix.LlegeixApp
import com.david.llegeix.R
import com.david.llegeix.data.db.entity.ExamAttemptEntity
import com.david.llegeix.data.db.entity.ExamAudioEntity
import com.david.llegeix.data.db.entity.ExamEntity
import com.david.llegeix.data.db.entity.ExamPartEntity
import com.david.llegeix.data.exam.ExamRepository
import com.david.llegeix.data.exam.ImportFailure
import com.david.llegeix.data.exam.ImportResult
import com.david.llegeix.ui.common.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One sitting, with enough about it for the list to be worth reading. */
data class AttemptSummary(
    val attempt: ExamAttemptEntity,
    /** Pages with anything written on them, out of the paper's length. */
    val markedPages: Int,
) {
    val isFinished: Boolean get() = attempt.finishedAt != null
}

/**
 * One exam paper and everything attached to it.
 *
 * The sittings each carry their own progress, which means one flow per sitting
 * combined into a list. That is more machinery than a single query would be, and
 * it is worth it: the alternative is a join that recounts every mark in every
 * attempt each time a single stroke is drawn, on a screen the reader may well
 * have open in the background while working.
 */
class ExamDetailViewModel(
    private val repository: ExamRepository,
    private val examId: Long,
) : ViewModel() {

    val exam: StateFlow<ExamEntity?> = repository.observeExam(examId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val audio: StateFlow<List<ExamAudioEntity>> = repository.observeAudio(examId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The documents this paper is made of, in the order the pages run. */
    val parts: StateFlow<List<ExamPartEntity>> = repository.observeParts(examId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val attempts: StateFlow<List<AttemptSummary>> = repository.observeAttempts(examId)
        .flatMapLatest { list ->
            if (list.isEmpty()) {
                kotlinx.coroutines.flow.flowOf(emptyList())
            } else {
                combine(
                    list.map { attempt ->
                        repository.observeMarkedPageCount(attempt.id)
                    },
                ) { counts ->
                    list.mapIndexed { index, attempt ->
                        AttemptSummary(attempt, counts[index])
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    /**
     * The sitting just started, so the screen can walk straight into it.
     *
     * Making an attempt and answering it is one job. Leaving the reader looking
     * at a new row in a list, with no hint that opening it is the next step, is
     * the same mistake collections used to make before they opened their picker.
     */
    private val _started = MutableStateFlow<Long?>(null)
    val started: StateFlow<Long?> = _started.asStateFlow()

    fun onStartAttempt() = viewModelScope.launch {
        _started.value = repository.startAttempt(examId)
    }

    fun onStartedHandled() {
        _started.value = null
    }

    fun onRenameAttempt(attemptId: Long, label: String) = viewModelScope.launch {
        repository.renameAttempt(attemptId, label)
    }

    fun onDeleteAttempt(attemptId: Long) = viewModelScope.launch {
        repository.deleteAttempt(attemptId)
    }

    fun onToggleFinished(attemptId: Long, finished: Boolean) = viewModelScope.launch {
        repository.setAttemptFinished(attemptId, finished)
    }

    /** Add more documents to the end of this paper. */
    fun onPartsPicked(uris: List<Uri>) = viewModelScope.launch {
        _busy.value = true
        val result = repository.addParts(examId, uris)
        _busy.value = false
        if (result is ImportResult.Failed) {
            _message.value = UiText.of(
                when (result.reason) {
                    ImportFailure.NOT_A_PDF -> R.string.exams_import_not_a_pdf
                    ImportFailure.OUT_OF_SPACE -> R.string.exams_import_no_space
                    ImportFailure.UNREADABLE -> R.string.exams_import_unreadable
                },
            )
        }
    }

    fun onRemovePart(partId: Long) = viewModelScope.launch {
        repository.removePart(partId)
    }

    fun onMovePart(partId: Long, delta: Int) = viewModelScope.launch {
        repository.movePart(partId, delta)
    }

    fun onAudioPicked(uri: Uri) = viewModelScope.launch {
        _busy.value = true
        val added = repository.addAudio(examId, uri)
        _busy.value = false
        if (!added) _message.value = UiText.of(R.string.exams_import_unreadable)
    }

    fun onRemoveAudio(audio: ExamAudioEntity) = viewModelScope.launch {
        repository.removeAudio(audio.id, audio.fileName)
    }

    fun onAnswerKeyPicked(uri: Uri) = viewModelScope.launch {
        _busy.value = true
        val attached = repository.setAnswerKey(examId, uri)
        _busy.value = false
        _message.value = UiText.of(
            if (attached) R.string.exams_key_attached else R.string.exams_import_not_a_pdf,
        )
    }

    fun onRemoveAnswerKey() = viewModelScope.launch {
        repository.removeAnswerKey(examId)
    }

    /**
     * Rename one document of the paper, or one recording, inside the app.
     *
     * The stored copy keeps its own filename, which is a UUID nobody ever sees,
     * and the marks written on a document are addressed by the part's id — so a
     * rename cannot move a single answer. The original file the reader imported
     * from is not touched either: it was copied, and the app never held a
     * writable handle to it.
     */
    fun onRenamePart(partId: Long, name: String) = viewModelScope.launch {
        repository.renamePart(partId, name)
    }

    fun onRenameAudio(audioId: Long, name: String) = viewModelScope.launch {
        repository.renameAudio(audioId, name)
    }

    fun onRenameExam(title: String) = viewModelScope.launch {
        repository.renameExam(examId, title)
    }

    fun onMessageShown() {
        _message.value = null
    }

    companion object {

        /**
         * A factory bound to one exam, the way the reader's is bound to one
         * document.
         *
         * Built fresh on each composition, which is harmless: `viewModel()`
         * consults the store first and only calls a factory when there is
         * nothing there, so this runs once per screen rather than once per
         * frame.
         */
        fun factory(examId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as LlegeixApp
                ExamDetailViewModel(app.examRepository, examId)
            }
        }
    }
}
