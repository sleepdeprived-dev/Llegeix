package com.david.llegeix.ui.reader

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

/**
 * The reader's keys on the Mac, the ones every Mac reader of PDFs answers to:
 * the arrows, Page Up and Page Down, the space bar (with shift, back) and Home
 * and End to move through the pages; ⌘+, ⌘− and ⌘0 to zoom; ⌘F to find.
 *
 * On the key event rather than its preview, so a field that has the focus —
 * the find bar — takes its own keys first and only what it leaves comes here.
 */
internal fun Modifier.readerKeys(
    onTurn: (step: Int) -> Unit,
    onFirst: () -> Unit,
    onLast: () -> Unit,
    onZoom: (factor: Float?) -> Unit,
    onFind: () -> Unit,
): Modifier = onKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
    val command = event.isMetaPressed || event.isCtrlPressed
    when {
        command && (event.key == Key.Equals || event.key == Key.Plus || event.key == Key.NumPadAdd) -> onZoom(ZOOM_STEP)
        command && (event.key == Key.Minus || event.key == Key.NumPadSubtract) -> onZoom(1f / ZOOM_STEP)
        command && (event.key == Key.Zero || event.key == Key.NumPad0) -> onZoom(null)
        command && event.key == Key.F -> onFind()
        command -> return@onKeyEvent false
        event.key == Key.DirectionRight || event.key == Key.DirectionDown || event.key == Key.PageDown -> onTurn(1)
        event.key == Key.DirectionLeft || event.key == Key.DirectionUp || event.key == Key.PageUp -> onTurn(-1)
        event.key == Key.Spacebar -> onTurn(if (event.isShiftPressed) -1 else 1)
        event.key == Key.MoveHome -> onFirst()
        event.key == Key.MoveEnd -> onLast()
        else -> return@onKeyEvent false
    }
    true
}

/** One press of ⌘+ or ⌘−: a quarter larger or smaller. */
internal const val ZOOM_STEP = 1.25f
