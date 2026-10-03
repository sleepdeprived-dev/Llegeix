package com.david.llegeix.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File

/** `open`, which mounts a disk image in the Finder and shows a page in the browser. */
@Composable
actual fun rememberUpdateActions(updater: AppUpdater): UpdateActions = remember {
    object : UpdateActions {
        override fun install(file: File) = open(file.path)
        override fun requestInstallPermission() = true
        override fun openReleasePage(update: AvailableUpdate) =
            open(update.pageUrl.takeIf { ReleaseFeed.isTrustedUrl(it) } ?: RELEASES_PAGE)

        private fun open(target: String): Boolean =
            runCatching { ProcessBuilder("open", target).start().waitFor() == 0 }.getOrDefault(false)
    }
}

private const val RELEASES_PAGE = "https://github.com/sleepdeprived-dev/Llegeix/releases"
