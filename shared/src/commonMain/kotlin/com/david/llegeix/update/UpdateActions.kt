package com.david.llegeix.update

import androidx.compose.runtime.Composable
import java.io.File

/**
 * What only the platform can do with an update. Each answers whether it could,
 * so the screen can say so when it could not.
 */
interface UpdateActions {
    /** Hand [file] over: Android's installer on the phone, the Finder on the Mac. */
    fun install(file: File): Boolean

    /** Ask for leave to install, where the platform wants it asked. */
    fun requestInstallPermission(): Boolean

    /** Show the release in the browser. */
    fun openReleasePage(update: AvailableUpdate): Boolean
}

@Composable
expect fun rememberUpdateActions(updater: AppUpdater): UpdateActions
