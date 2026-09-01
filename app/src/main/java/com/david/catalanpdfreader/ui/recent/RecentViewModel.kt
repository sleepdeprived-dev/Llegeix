package com.david.catalanpdfreader.ui.recent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.david.catalanpdfreader.CatalanPdfReaderApp
import com.david.catalanpdfreader.data.db.dao.RecentDocument
import com.david.catalanpdfreader.data.source.LibraryDataRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecentViewModel(
    private val libraryData: LibraryDataRepository,
) : ViewModel() {

    val recents: StateFlow<List<RecentDocument>> = libraryData.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun remove(uriString: String) = viewModelScope.launch {
        libraryData.removeFromRecent(uriString)
    }

    fun clearAll() = viewModelScope.launch { libraryData.clearRecent() }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as CatalanPdfReaderApp
                RecentViewModel(app.libraryDataRepository)
            }
        }
    }
}
