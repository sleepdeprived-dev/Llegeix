package com.david.llegeix.update

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File

/** The intents the settings screen has always started, from its own context. */
@Composable
actual fun rememberUpdateActions(updater: AppUpdater): UpdateActions {
    val context = LocalContext.current
    val phone = updater as UpdateRepository
    return remember(context, phone) {
        object : UpdateActions {
            override fun install(file: File) = start { phone.installIntent(file) }
            override fun requestInstallPermission() = start { phone.installPermissionIntent() }
            override fun openReleasePage(update: AvailableUpdate) = start { phone.releasePageIntent(update) }

            /** False when nothing on the phone would take the intent. */
            private inline fun start(intent: () -> Intent): Boolean =
                runCatching { context.startActivity(intent()) }.isSuccess
        }
    }
}
