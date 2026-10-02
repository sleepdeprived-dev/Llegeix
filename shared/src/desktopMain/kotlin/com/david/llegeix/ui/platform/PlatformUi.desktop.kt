package com.david.llegeix.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.david.llegeix.data.source.DesktopPdfLibrary
import com.david.llegeix.platform.ContentRef
import com.david.llegeix.platform.Services
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

/** The Mac's open panel, turned to choosing a folder. */
@Composable
actual fun rememberFolderPicker(onPicked: (ContentRef) -> Unit): () -> Unit {
    val current by rememberUpdatedState(onPicked)
    return remember {
        {
            // Read by the panel as it opens; put back so other panels choose files.
            System.setProperty(FOLDERS_PROPERTY, "true")
            try {
                choose(FileDialog.LOAD, title = null, suggested = null)
            } finally {
                System.setProperty(FOLDERS_PROPERTY, "false")
            }?.let { current(ContentRef(it)) }
        }
    }
}

/** The Mac's open panel, for PDFs, several at once. */
@Composable
actual fun rememberPdfFilesPicker(onPicked: (List<ContentRef>) -> Unit): () -> Unit {
    val current by rememberUpdatedState(onPicked)
    return remember {
        {
            val dialog = FileDialog(null as Frame?, "", FileDialog.LOAD).apply {
                isMultipleMode = true
                setFilenameFilter { _, name -> name.endsWith(".pdf", ignoreCase = true) }
                isVisible = true
            }
            current(dialog.files.map(::ContentRef))
        }
    }
}

/**
 * Spotlight answers without asking macOS for anything, so the reader choosing
 * the sweep is the permission. It is kept beside the library's other choices.
 */
@Composable
actual fun rememberDeviceScanRequest(onReturn: () -> Unit): () -> Boolean {
    val current by rememberUpdatedState(onReturn)
    return remember {
        {
            (Services.app.pdfLibrary as? DesktopPdfLibrary)?.allowDeviceScan()
            current()
            true
        }
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

private const val FOLDERS_PROPERTY = "apple.awt.fileDialogForDirectories"

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
