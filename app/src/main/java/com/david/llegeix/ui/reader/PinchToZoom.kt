package com.david.llegeix.ui.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Zoom on a two-finger pinch, and leave every other gesture alone.
 *
 * The obvious implementation is `detectTransformGestures`, which is what this
 * replaced. That helper reports pan alongside zoom and consumes every drag it
 * sees — including one-finger drags — so with it attached to the page, swiping
 * to turn the page only worked in the margins where the page was not. Watching
 * the pointers directly means a single finger is never touched and passes
 * straight through to the pager underneath.
 *
 * @param currentZoom read on each event rather than captured, because the
 *   gesture outlives the composition that started it and a captured value goes
 *   stale as soon as the first pinch changes it.
 */
fun Modifier.pinchToZoom(
    key: Any?,
    currentZoom: () -> Float,
    minZoom: Float,
    maxZoom: Float,
    onZoomChanged: (Float) -> Unit,
): Modifier = pointerInput(key) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            if (event.changes.count { it.pressed } >= 2) {
                val change = event.calculateZoom()
                if (change != 1f) {
                    onZoomChanged((currentZoom() * change).coerceIn(minZoom, maxZoom))
                    event.changes.forEach { if (it.pressed) it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}
