package com.david.llegeix.ui.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration

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

/**
 * Runs [content] with the reader's own idea of how long a press has to last.
 *
 * Everything else about touch is left as the platform set it; only the hold
 * that starts a selection is shortened. Android's half-second is tuned for
 * screens where holding something is the rare, deliberate alternative to
 * tapping it, and is right for those. Here holding a word *is* the interaction
 * — it is how the app is used, several times a page — and half a second of a
 * finger on a word with nothing happening does not read as "keep holding", it
 * reads as the press not having registered. Shortened, and answered with a tick
 * of haptics the instant it takes, the same gesture stops feeling like a wait.
 *
 * Not so short that it fires while the page is being turned: a swipe crosses
 * the touch slop long before this, and crossing it hands the gesture to the
 * pager and cancels the hold.
 */
@Composable
fun SelectionTiming(content: @Composable () -> Unit) {
    val platform = LocalViewConfiguration.current
    val tuned = remember(platform) { HoldToSelect(platform) }
    CompositionLocalProvider(LocalViewConfiguration provides tuned, content = content)
}

/** The platform's touch configuration with one number changed. */
private class HoldToSelect(platform: ViewConfiguration) : ViewConfiguration by platform {
    override val longPressTimeoutMillis: Long = HOLD_TO_SELECT_MS
}

/**
 * How long a word has to be held before it is selected.
 *
 * Arrived at on a device. At 250 ms a finger resting on the page while reading
 * starts picking words up; at the platform's 500 ms the gesture feels ignored.
 */
private const val HOLD_TO_SELECT_MS = 320L
