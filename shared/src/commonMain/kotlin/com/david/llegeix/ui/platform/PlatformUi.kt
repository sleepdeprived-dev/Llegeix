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

/** Going back: the system back gesture on the phone, Escape on the Mac. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
