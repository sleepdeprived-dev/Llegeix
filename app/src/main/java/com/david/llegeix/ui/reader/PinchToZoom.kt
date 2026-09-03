package com.david.llegeix.ui.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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

/**
 * Double tap to magnify the page, and double tap again to put it back.
 *
 * Its own modifier beside [pinchToZoom] for exactly the reason that one exists,
 * and it is worth saying twice because getting it wrong is silent. A gesture
 * handler outlives the composition that started it: the keys deliberately leave
 * the zoom out, so the detector is not torn down and restarted every time a
 * pinch moves it. That means the zoom has to be *read* on each event rather
 * than captured.
 *
 * Written inline in the page, this read the captured parameter, so after a pinch
 * — or after the zoom button — the handler still believed the page was at 1x and
 * double tapping magnified it again instead of returning it. There was then no
 * way at all to leave a magnified page except by pinching back out.
 *
 * @param currentZoom read on each event, never captured.
 * @param magnified where a double tap on an unmagnified page lands.
 */
fun Modifier.doubleTapToZoom(
    key: Any?,
    currentZoom: () -> Float,
    minZoom: Float,
    magnified: Float,
    onZoomChanged: (Float) -> Unit,
): Modifier = pointerInput(key) {
    detectTapGestures(
        onDoubleTap = {
            val zoomed = currentZoom() > minZoom + ZOOM_EPSILON
            onZoomChanged(if (zoomed) minZoom else magnified)
        },
    )
}

/**
 * How far past the minimum counts as magnified.
 *
 * Zoom is a float arrived at by multiplying pinch deltas together, so it lands
 * on 1.0000001 rather than on 1, and an exact comparison would leave a page that
 * looks unmagnified behaving as though it were.
 */
private const val ZOOM_EPSILON = 0.01f
