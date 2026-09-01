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
}
