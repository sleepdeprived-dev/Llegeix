package com.david.llegeix.ui.exams

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.david.llegeix.LlegeixApp
import com.david.llegeix.R
import com.david.llegeix.data.db.dao.ExamWithProgress
import com.david.llegeix.data.exam.ExamRepository
import com.david.llegeix.data.exam.ImportFailure
import com.david.llegeix.data.exam.ImportResult
import com.david.llegeix.ui.common.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExamsViewModel(private val repository: ExamRepository) : ViewModel() {

    val exams: StateFlow<List<ExamWithProgress>> = repository.observeExams()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * True while a file is being copied in.
     *
     * Worth a state of its own rather than a spinner somewhere: importing is
     * the one thing in this app that can take several seconds on a large file,
     * and an exam list that simply does not change for four seconds after the
     * picker closes reads as the import having failed silently.
     */
    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    /**
     * Files the picker handed back, waiting on one question.
     *
     * Picking several files at once is ambiguous in a way picking one is not:
     * they might be three papers, or three parts of one paper. Guessing gets it
     * wrong half the time and the reader then has to undo it, so the question is
     * asked once, at the moment it can be answered, and only when more than one
     * file was picked.
     */
    private val _pendingChoice = MutableStateFlow<List<Uri>>(emptyList())
    val pendingChoice: StateFlow<List<Uri>> = _pendingChoice.asStateFlow()

    fun onPicked(uris: List<Uri>) {
        when {
            uris.isEmpty() -> Unit
            uris.size == 1 -> importAsOne(uris)
            else -> _pendingChoice.value = uris
        }
    }

    fun onGroupChosen(asOneExam: Boolean) {
        val uris = _pendingChoice.value
        _pendingChoice.value = emptyList()
        if (uris.isEmpty()) return
        if (asOneExam) importAsOne(uris) else importSeparately(uris)
    }

    fun onChoiceDismissed() {
        _pendingChoice.value = emptyList()
    }

    private fun importAsOne(uris: List<Uri>) = viewModelScope.launch {
        run(uris) { repository.importAsOneExam(it) }
    }

    private fun importSeparately(uris: List<Uri>) = viewModelScope.launch {
        run(uris) { repository.importAsSeparateExams(it) }
    }

    private suspend fun run(uris: List<Uri>, work: suspend (List<Uri>) -> ImportResult) {
        _isImporting.value = true
        val result = work(uris)
        _isImporting.value = false
        _message.value = describe(result)
    }

    /**
     * One sentence about what happened, which is not always "done".
     *
     * A batch can half-succeed — four PDFs and a Word document — and saying
     * only "imported" would leave the reader to notice the missing one for
     * themselves, on an exam paper, later.
     */
    private fun describe(result: ImportResult): UiText = when (result) {
        is ImportResult.Failed -> UiText.of(messageFor(result.reason))

        is ImportResult.Imported -> when {
            result.failedCount > 0 -> UiText.of(
                R.string.exams_imported_partly,
                result.fileCount,
                result.failedCount,
            )

            result.title != null -> UiText.of(R.string.exams_imported, result.title)

            else -> UiText.ofPlural(R.plurals.exams_imported_many, result.fileCount)
        }
    }

    private fun messageFor(failure: ImportFailure) = when (failure) {
        ImportFailure.NOT_A_PDF -> R.string.exams_import_not_a_pdf
        ImportFailure.OUT_OF_SPACE -> R.string.exams_import_no_space
        ImportFailure.UNREADABLE -> R.string.exams_import_unreadable
    }

    /**
     * The cover's file as a URI the thumbnail cache can open.
     *
     * Exam papers live in the app's own storage rather than behind a grant, so
     * this is a `file://` URI. The renderer opens it through the same
     * ContentResolver call it uses for everything else, which handles that
     * scheme, so nothing about the cover machinery has to know the difference.
     */
    fun coverUriFor(fileName: String): String =
        android.net.Uri.fromFile(repository.fileFor(fileName)).toString()

    fun onRename(examId: Long, title: String) = viewModelScope.launch {
        repository.renameExam(examId, title)
    }

    fun onDelete(examId: Long) = viewModelScope.launch {
        repository.deleteExam(examId)
    }

    fun onMessageShown() {
        _message.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as LlegeixApp
                ExamsViewModel(app.examRepository)
            }
        }
    }
}
