package com.david.llegeix.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.david.llegeix.platform.ContentRef
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** The Mac's open panel, for one picture. */
@Composable
actual fun rememberPicturePicker(onPicked: (ContentRef) -> Unit): () -> Unit {
    val current by rememberUpdatedState(onPicked)
    return remember {
        {
            choose(FileDialog.LOAD, title = null, suggested = null) { name ->
                name.substringAfterLast('.', "").lowercase() in PICTURE_EXTENSIONS
            }?.let { current(ContentRef(it)) }
        }
    }
}

/** The Mac's save panel. */
@Composable
actual fun rememberDocumentCreator(mimeType: String, onCreated: (ContentRef) -> Unit): (String) -> Unit {
    val current by rememberUpdatedState(onCreated)
    return remember {
        { name -> choose(FileDialog.SAVE, title = null, suggested = name)?.let { current(ContentRef(it)) } }
    }
}

/** The Mac's open panel, for a backup. MIME types mean little to a Mac panel; any file may be chosen. */
@Composable
actual fun rememberDocumentOpener(onOpened: (ContentRef) -> Unit): (Array<String>) -> Unit {
    val current by rememberUpdatedState(onOpened)
    return remember {
        { _ -> choose(FileDialog.LOAD, title = null, suggested = null)?.let { current(ContentRef(it)) } }
    }
}

private fun choose(mode: Int, title: String?, suggested: String?, accept: ((String) -> Boolean)? = null): File? {
    val dialog = FileDialog(null as Frame?, title ?: "", mode).apply {
        if (suggested != null) file = suggested
        if (accept != null) setFilenameFilter { _, name -> accept(name) }
        isVisible = true
    }
    val name = dialog.file ?: return null
    return File(dialog.directory, name)
}

private val PICTURE_EXTENSIONS = setOf("jpg", "jpeg", "png", "heic", "heif", "webp", "gif", "bmp", "tif", "tiff")

/**
 * Back on the Mac: Escape, or the window's own back button, goes to the most
 * recently shown screen that wants it, as the back gesture does on the phone.
 */
object DesktopBack {
    private val handlers = mutableListOf<() -> Boolean>()

    internal fun add(handler: () -> Boolean) { handlers += handler }
    internal fun remove(handler: () -> Boolean) { handlers -= handler }

    /** Whether some screen took it. */
    fun dispatch(): Boolean = handlers.asReversed().any { it() }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val isEnabled by rememberUpdatedState(enabled)
    val action by rememberUpdatedState(onBack)
    DisposableEffect(Unit) {
        val handler = { if (isEnabled) { action(); true } else false }
        DesktopBack.add(handler)
        onDispose { DesktopBack.remove(handler) }
    }
}
