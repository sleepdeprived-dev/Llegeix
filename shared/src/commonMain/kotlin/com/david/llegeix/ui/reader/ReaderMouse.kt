package com.david.llegeix.ui.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed

/**
 * The scroll wheel, or two fingers on a trackpad, over a page on the Mac.
 *
 * With ⌘ (or control) held it zooms, as Preview does. Otherwise it pans a
 * magnified page, which on the phone is a drag — a drag the Mac gives to
 * selecting text. Over a page at its normal size it does nothing here, and
 * the pages around it take it.
 */
internal suspend fun PointerInputScope.wheelOnPage(
    zoom: () -> Float,
    onZoom: (Float) -> Unit,
    offset: () -> Offset,
    onPan: (Offset) -> Unit,
) = awaitPointerEventScope {
    while (true) {
        val event = awaitPointerEvent()
        if (event.type != PointerEventType.Scroll) continue
        val delta = event.changes.fold(Offset.Zero) { sum, change -> sum + change.scrollDelta }
        val live = zoom()
        val modifiers = event.keyboardModifiers
        when {
            modifiers.isMetaPressed || modifiers.isCtrlPressed ->
                onZoom((live * (1f - delta.y * ZOOM_PER_NOTCH)).coerceIn(ReaderViewModel.MIN_ZOOM, ReaderViewModel.MAX_ZOOM))
            live > 1.01f -> {
                // The same bounds as the phone's drag: the page grows both ways
                // from the centre sideways, and only downward from its top.
                val maxX = size.width * (live - 1f) / 2f
                val minY = -size.height * (live - 1f)
                val now = offset()
                onPan(
                    Offset(
                        (now.x - delta.x * PAN_PER_NOTCH).coerceIn(-maxX, maxX),
                        (now.y - delta.y * PAN_PER_NOTCH).coerceIn(minY, 0f),
                    ),
                )
            }
            else -> continue
        }
        event.changes.forEach { it.consume() }
    }
}

/** How far one notch of the wheel moves a magnified page, in pixels. */
private const val PAN_PER_NOTCH = 48f

/** How much one notch with ⌘ held zooms. */
private const val ZOOM_PER_NOTCH = 0.1f
