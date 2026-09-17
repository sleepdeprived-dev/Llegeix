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
import com.david.llegeix.data.settings.ContinueShelf
import com.david.llegeix.data.settings.AppSettings
import com.david.llegeix.data.settings.SettingsRepository
import com.david.llegeix.data.settings.ThemeMode
import com.david.llegeix.R
import com.david.llegeix.ui.common.UiText
import com.david.llegeix.update.AvailableUpdate
import com.david.llegeix.update.UpdateCheck
import com.david.llegeix.update.UpdateRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * How far the reader has got with a new version of the app.
 *
 * One value rather than a handful of booleans, because these states are
 * genuinely exclusive and the card draws exactly one of them: a screen that can
 * be both "checking" and "ready to install" is a screen with a bug in it.
 */
sealed interface UpdateUiState {

    /** Nothing has been asked yet, which is where every session starts. */
    data object Idle : UpdateUiState

    data object Checking : UpdateUiState

    data object UpToDate : UpdateUiState

    data class Available(val update: AvailableUpdate) : UpdateUiState

    data class Downloading(val update: AvailableUpdate, val fraction: Float) : UpdateUiState

    /**
     * Fetched and waiting to be handed over.
     *
     * Held as a state of its own rather than going straight into the installer,
     * so that a download finishing while the reader is elsewhere in Configuració
     * does not throw a system dialog over whatever they were doing.
     */
    data class Ready(val update: AvailableUpdate, val file: File) : UpdateUiState

    data class Trouble(val message: UiText) : UpdateUiState
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val dataEraser: DataEraser,
    private val updates: UpdateRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings

    fun onThemeModeChange(mode: ThemeMode) = settingsRepository.setThemeMode(mode)

    fun onAccentChange(accent: AccentColor) = settingsRepository.setAccent(accent)

    fun onLanguageChange(language: AppLanguage) = settingsRepository.setLanguage(language)

    /**
     * Shown, folded away, or hidden, for the Continue reading shelf.
     *
     * The shelf carries its own controls, on the library, where the decision is
     * actually taken — this is here because *hidden* has to be undoable from
     * somewhere, and a shelf that is no longer on the screen cannot offer the
     * button that brings it back.
     */
    fun onContinueShelfChange(shelf: ContinueShelf) =
        settingsRepository.setContinueShelf(shelf)

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
        // In a finally, because the dialog disables its own dismiss button
        // while this flag is set: anything thrown on the way through would
        // otherwise leave the reader looking at a dialog with no way out.
        try {
            dataEraser.eraseEverything()
        } finally {
            _isErasing.value = false
            onDone()
        }
    }

    // ---- Updates ----------------------------------------------------------

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    /** The version running now, for the card to name without asking anything. */
    val installedVersion: String get() = updates.installedVersion

    private var updateJob: Job? = null

    /**
     * Ask the releases repository what the newest version is, on the button.
     *
     * The library asks the same question once a day of its own accord, to
     * decide whether to mark the gear. This is the version that reports what it
     * found in full and offers to fetch it, and it answers straight away rather
     * than waiting for the quiet check's daily allowance.
     */
    fun onCheckForUpdates() {
        if (_updateState.value is UpdateUiState.Checking) return
        updateJob?.cancel()
        _updateState.value = UpdateUiState.Checking
        updateJob = viewModelScope.launch {
            _updateState.value = when (val answer = updates.check()) {
                is UpdateCheck.UpToDate -> {
                    // The dot on the library's gear comes off here as well as
                    // going on: pressing the button is the reader asking the
                    // same question the quiet check asks, and an answer of "you
                    // are on the newest one" should not leave a mark saying
                    // otherwise.
                    updates.rememberWaiting(null)
                    UpdateUiState.UpToDate
                }
                is UpdateCheck.Available -> {
                    updates.rememberWaiting(answer.update.version)
                    UpdateUiState.Available(answer.update)
                }
                is UpdateCheck.Trouble -> UpdateUiState.Trouble(troubleText(answer.reason))
            }
        }
    }

    fun onDownloadUpdate(update: AvailableUpdate) {
        updateJob?.cancel()
        _updateState.value = UpdateUiState.Downloading(update, 0f)
        updateJob = viewModelScope.launch {
            val file = updates.download(update) { fraction ->
                // Only while this is still the download being watched: a reader
                // who pressed check again mid-fetch is not owed a progress bar
                // for the attempt they abandoned.
                _updateState.update { current ->
                    if (current is UpdateUiState.Downloading && current.update == update) {
                        current.copy(fraction = fraction)
                    } else {
                        current
                    }
                }
            }
            _updateState.value = when {
                file == null ->
                    UpdateUiState.Trouble(UiText.of(R.string.settings_update_download_failed))
                // Checked before the reader is shown a dialog about it, not
                // after. See UpdateRepository.isOurBuild.
                !updates.isOurBuild(file) ->
                    UpdateUiState.Trouble(UiText.of(R.string.settings_update_not_ours))
                else -> UpdateUiState.Ready(update, file)
            }
        }
    }

    /** Put the card back to resting, without forgetting what version is running. */
    fun onDismissUpdate() {
        updateJob?.cancel()
        _updateState.value = UpdateUiState.Idle
    }

    /**
     * Whether Android will let the app hand an APK to the installer.
     *
     * Asked at the moment of installing rather than remembered, because the
     * reader can grant or revoke it in system Settings while this screen is
     * open and the answer would go stale in a field.
     */
    fun canInstallUpdates(): Boolean = updates.canInstall()

    /**
     * The intents the screen fires.
     *
     * Built here because the repository knows the file provider and the package
     * name, and started there because starting an activity wants the screen's
     * own context, not the application's.
     */
    fun installIntent(file: File) = updates.installIntent(file)

    fun installPermissionIntent() = updates.installPermissionIntent()

    fun releasePageIntent(update: AvailableUpdate) = updates.releasePageIntent(update)

    /**
     * Say so when an intent would not start.
     *
     * Three of these are fired from the card — the installer, the browser, the
     * permission screen — and every one of them is a `startActivity` that can
     * throw on a device with nothing registered for it. Uncaught, in a click
     * handler, that is not a failed update: it is the app closing. A device
     * with no package installer at all is close to hypothetical, which is
     * exactly why it would never be found any other way.
     */
    fun onCouldNotOpen() {
        _updateState.value = UpdateUiState.Trouble(UiText.of(R.string.settings_update_cannot_open))
    }

    private fun troubleText(reason: UpdateCheck.Reason): UiText = UiText.of(
        when (reason) {
            UpdateCheck.Reason.OFFLINE -> R.string.settings_update_offline
            UpdateCheck.Reason.RATE_LIMITED -> R.string.settings_update_rate_limited
            UpdateCheck.Reason.NO_BUILD -> R.string.settings_update_no_build
            UpdateCheck.Reason.UNREADABLE -> R.string.settings_update_unreadable
        },
    )

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as LlegeixApp
                SettingsViewModel(app.settingsRepository, app.dataEraser, app.updateRepository)
            }
        }
    }
}
