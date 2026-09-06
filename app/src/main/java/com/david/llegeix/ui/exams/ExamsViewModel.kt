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

    fun onPicked(uri: Uri) = viewModelScope.launch {
        _isImporting.value = true
        val result = repository.import(uri)
        _isImporting.value = false
        _message.value = when (result) {
            is ImportResult.Imported -> UiText.of(R.string.exams_imported, result.title)
            is ImportResult.Failed -> UiText.of(
                when (result.reason) {
                    ImportResult.Failure.NOT_A_PDF -> R.string.exams_import_not_a_pdf
                    ImportResult.Failure.OUT_OF_SPACE -> R.string.exams_import_no_space
                    ImportResult.Failure.UNREADABLE -> R.string.exams_import_unreadable
                },
            )
        }
    }

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
