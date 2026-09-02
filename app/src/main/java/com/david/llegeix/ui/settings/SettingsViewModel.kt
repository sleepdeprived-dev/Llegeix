package com.david.llegeix.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.david.llegeix.LlegeixApp
import com.david.llegeix.data.DataEraser
import com.david.llegeix.data.settings.AccentColor
import com.david.llegeix.data.settings.AppLanguage
import com.david.llegeix.data.settings.AppSettings
import com.david.llegeix.data.settings.SettingsRepository
import com.david.llegeix.data.settings.ThemeMode
import com.david.llegeix.data.settings.TranslationTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val dataEraser: DataEraser,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings

    fun onThemeModeChange(mode: ThemeMode) = settingsRepository.setThemeMode(mode)

    fun onAccentChange(accent: AccentColor) = settingsRepository.setAccent(accent)

    fun onLanguageChange(language: AppLanguage) = settingsRepository.setLanguage(language)

    /** Which language a tapped word is translated into. */
    fun onTranslationTargetChange(target: TranslationTarget) =
        settingsRepository.setTranslationTarget(target)

    /** Applies the mixed colour and selects it in one step. */
    fun onCustomAccentChange(argb: Int) = settingsRepository.setCustomAccent(argb)

    /** True while the wipe is running, so the dialog can say it is working. */
    private val _isErasing = MutableStateFlow(false)
    val isErasing: StateFlow<Boolean> = _isErasing.asStateFlow()

    /**
     * Erase everything and report when it is done.
     *
     * The caller is given a callback rather than a flow because the only thing
     * left to do afterwards is leave the screen: the library behind it has just
     * become empty.
     */
    fun onEraseEverything(onDone: () -> Unit) = viewModelScope.launch {
        _isErasing.value = true
        dataEraser.eraseEverything()
        _isErasing.value = false
        onDone()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as LlegeixApp
                SettingsViewModel(app.settingsRepository, app.dataEraser)
            }
        }
    }
}
