package com.david.llegeix.ui.platform

import androidx.compose.runtime.Composable
import com.david.llegeix.platform.ContentRef

/*
 * The few things a screen asks of the system it runs on. On the phone each is
 * the Activity Result or back-press call the screens always made; on the Mac,
 * the open and save panels, and the Escape key.
 */

/** A launcher for choosing one picture; [onPicked] hears nothing if it is cancelled. */
@Composable
expect fun rememberPicturePicker(onPicked: (ContentRef) -> Unit): () -> Unit

/** A launcher for choosing where to save a new file, offered [suggestedName]. */
@Composable
expect fun rememberDocumentCreator(mimeType: String, onCreated: (ContentRef) -> Unit): (suggestedName: String) -> Unit

/** A launcher for choosing a file to open, of any of [mimeTypes]. */
@Composable
expect fun rememberDocumentOpener(onOpened: (ContentRef) -> Unit): (mimeTypes: Array<String>) -> Unit

/** A launcher for choosing a folder of PDFs to keep watched. */
@Composable
expect fun rememberFolderPicker(onPicked: (ContentRef) -> Unit): () -> Unit

/** A launcher for choosing PDFs one by one, several at a time. */
@Composable
expect fun rememberPdfFilesPicker(onPicked: (List<ContentRef>) -> Unit): () -> Unit

/**
 * A launcher for leave to sweep the whole device for PDFs: All Files Access in
 * Android's Settings on the phone; on the Mac, where Spotlight needs no
 * permission, the reader's say-so. [onReturn] runs once they are back. The
 * launcher answers whether it could ask at all.
 */
@Composable
expect fun rememberDeviceScanRequest(onReturn: () -> Unit): () -> Boolean

/**
 * Whether pages are worked with a mouse and keyboard — the Mac — rather than
 * a finger. The reader selects by dragging, looks a word up on a double click,
 * pans with the scroll wheel and turns pages from the keyboard when it is.
 */
expect val usesMouse: Boolean

/** Going back: the system back gesture on the phone, Escape on the Mac. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
