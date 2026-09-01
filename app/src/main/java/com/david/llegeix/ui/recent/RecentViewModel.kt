package com.david.llegeix.ui.recent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.david.llegeix.LlegeixApp
import com.david.llegeix.data.settings.LibraryLayout
import com.david.llegeix.data.settings.SettingsRepository
import com.david.llegeix.data.db.dao.DocumentTag
import com.david.llegeix.data.db.dao.RecentDocument
import com.david.llegeix.data.source.LibraryDataRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecentViewModel(
    private val libraryData: LibraryDataRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    /** Rows or covers. Shared with the library so the app looks like one app. */
    val layout: StateFlow<LibraryLayout> = settings.settings
        .map { it.libraryLayout }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settings.current.libraryLayout)

    fun onToggleLayout() = settings.setLibraryLayout(layout.value.toggled())

    /** Tags keyed by document, so recent rows match the library's. */
    val tagsByDocument: StateFlow<Map<String, List<DocumentTag>>> =
        libraryData.observeTagsByDocument()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

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
                    as LlegeixApp
                RecentViewModel(app.libraryDataRepository, app.settingsRepository)
            }
        }
    }
}
