package com.david.llegeix.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.david.llegeix.LlegeixApp
import com.david.llegeix.data.settings.AccentColor
import com.david.llegeix.data.settings.AppLanguage
import com.david.llegeix.data.settings.AppSettings
import com.david.llegeix.data.settings.SettingsRepository
import com.david.llegeix.data.settings.ThemeMode
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings

    fun onThemeModeChange(mode: ThemeMode) = settingsRepository.setThemeMode(mode)

    fun onAccentChange(accent: AccentColor) = settingsRepository.setAccent(accent)

    fun onLanguageChange(language: AppLanguage) = settingsRepository.setLanguage(language)

    fun onApiKeyChange(key: String) = settingsRepository.setAnthropicApiKey(key)

    /** Applies the mixed colour and selects it in one step. */
    fun onCustomAccentChange(argb: Int) = settingsRepository.setCustomAccent(argb)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as LlegeixApp
                SettingsViewModel(app.settingsRepository)
            }
        }
    }
}
