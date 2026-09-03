package com.david.llegeix.ui.reader

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The regression this exists for: a one-finger swipe used to be eaten by the
 * zoom handler, so swiping over the page did nothing and the page could only be
 * turned from the margins. Zoom must respond to two fingers and to nothing else.
 */
@RunWith(AndroidJUnit4::class)
class PinchToZoomTest {

    @get:Rule
    val rule = createComposeRule()

    private val minZoom = 1f
    private val maxZoom = 4f

    @Test
    fun aTwoFingerPinchChangesTheZoom() {
        var zoom = 1f
        rule.setContent {
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag("page")
                    .pinchToZoom(
                        key = Unit,
                        currentZoom = { zoom },
                        minZoom = minZoom,
                        maxZoom = maxZoom,
                        onZoomChanged = { zoom = it },
                    ),
            )
        }

        rule.onNodeWithTag("page").performTouchInput {
            pinch(
                start0 = Offset(centerX - 40f, centerY),
                end0 = Offset(centerX - 200f, centerY),
                start1 = Offset(centerX + 40f, centerY),
                end1 = Offset(centerX + 200f, centerY),
            )
        }
        rule.waitForIdle()

        assertTrue("expected a pinch to zoom in, zoom was $zoom", zoom > 1.05f)
    }

    @Test
    fun aOneFingerSwipeDoesNotZoom() {
        var zoom = 1f
        rule.setContent {
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag("page")
                    .pinchToZoom(
                        key = Unit,
                        currentZoom = { zoom },
                        minZoom = minZoom,
                        maxZoom = maxZoom,
                        onZoomChanged = { zoom = it },
                    ),
            )
        }

        rule.onNodeWithTag("page").performTouchInput { swipeLeft() }
        rule.waitForIdle()

        assertEquals("a one-finger swipe must not zoom", 1f, zoom, 0.001f)
    }

    /**
     * The swipe has to reach whatever is underneath, not merely fail to zoom —
     * that is what turning the page depends on.
     */
    @Test
    fun aOneFingerSwipeReachesTheHandlerUnderneath() {
        var zoom = 1f
        var draggedBy = 0f
        rule.setContent {
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag("page")
                    .pointerInput(Unit) {
                        detectDragGestures { _, drag -> draggedBy += drag.x }
                    }
                    .pinchToZoom(
                        key = Unit,
                        currentZoom = { zoom },
                        minZoom = minZoom,
                        maxZoom = maxZoom,
                        onZoomChanged = { zoom = it },
                    ),
            )
        }

        rule.onNodeWithTag("page").performTouchInput { swipeLeft() }
        rule.waitForIdle()

        assertEquals(1f, zoom, 0.001f)
        assertTrue("the drag never reached the layer below", draggedBy < -10f)
    }

    @Test
    fun zoomIsClampedToTheAllowedRange() {
        var zoom = 3.9f
        rule.setContent {
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag("page")
                    .pinchToZoom(
                        key = Unit,
                        currentZoom = { zoom },
                        minZoom = minZoom,
                        maxZoom = maxZoom,
                        onZoomChanged = { zoom = it },
                    ),
            )
        }

        rule.onNodeWithTag("page").performTouchInput {
            pinch(
                start0 = Offset(centerX - 20f, centerY),
                end0 = Offset(centerX - 300f, centerY),
                start1 = Offset(centerX + 20f, centerY),
                end1 = Offset(centerX + 300f, centerY),
            )
        }
        rule.waitForIdle()

        assertTrue("zoom escaped its ceiling: $zoom", zoom <= maxZoom + 0.001f)
    }

    // ---- Double tap -------------------------------------------------------

    @Test
    fun aDoubleTapMagnifiesAnUnmagnifiedPage() {
        var zoom = 1f
        rule.setContent {
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag("page")
                    .doubleTapToZoom(
                        key = Unit,
                        currentZoom = { zoom },
                        minZoom = minZoom,
                        magnified = 2f,
                        onZoomChanged = { zoom = it },
                    ),
            )
        }

        rule.onNodeWithTag("page").performTouchInput { doubleClick() }
        rule.waitForIdle()

        assertEquals(2f, zoom, 0.001f)
    }

    /**
     * The regression this exists for.
     *
     * The keys deliberately leave the zoom out, so the detector is not restarted
     * every time a pinch moves it — which means a handler that *captured* the
     * zoom went stale on the first pinch. Written that way, a double tap after
     * zooming still believed the page was at 1x and magnified it again, and
     * there was no way to leave a magnified page at all.
     *
     * So the zoom is changed here from outside the gesture, exactly as a pinch
     * or the zoom button does, and then the page is double tapped.
     */
    @Test
    fun aDoubleTapAfterZoomingElsewhereReturnsThePage() {
        var zoom by mutableFloatStateOf(1f)
        rule.setContent {
            // The same holder the reader uses, so the test exercises the wiring
            // and not merely the modifier in isolation.
            val live by rememberUpdatedState(zoom)
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag("page")
                    .doubleTapToZoom(
                        key = Unit,
                        currentZoom = { live },
                        minZoom = minZoom,
                        magnified = 2f,
                        onZoomChanged = { zoom = it },
                    ),
            )
        }

        rule.runOnIdle { zoom = 1.5f }
        rule.onNodeWithTag("page").performTouchInput { doubleClick() }
        rule.waitForIdle()

        assertEquals("a double tap on a magnified page must put it back", 1f, zoom, 0.001f)
    }

    @Test
    fun aSingleTapLeavesTheZoomAlone() {
        var zoom = 1f
        rule.setContent {
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag("page")
                    .doubleTapToZoom(
                        key = Unit,
                        currentZoom = { zoom },
                        minZoom = minZoom,
                        magnified = 2f,
                        onZoomChanged = { zoom = it },
                    ),
            )
        }

        rule.onNodeWithTag("page").performTouchInput { click() }
        rule.waitForIdle()

        assertEquals(1f, zoom, 0.001f)
    }
}
