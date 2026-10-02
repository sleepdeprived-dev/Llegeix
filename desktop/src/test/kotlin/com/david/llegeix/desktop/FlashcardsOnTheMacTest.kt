package com.david.llegeix.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.rememberNavController
import com.david.llegeix.ui.theme.LlegeixTheme
import org.junit.Test

/**
 * Targetes on the Mac, driven as a reader would: make a deck, add a card to
 * it. Scratch data and scratch preferences (see [MacTestSession]); screenshots
 * of each step go to desktop/build/screenshots.
 */
@OptIn(ExperimentalTestApi::class)
class FlashcardsOnTheMacTest {

    @Test
    fun makeADeckAndAddACard() = MacTestSession.run { flow() }

    private fun flow() {
            runComposeUiTest {
                // A window on screen is resumed; the test's stand-in has to be told.
                val lifecycle = object : LifecycleOwner {
                    val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
                    override val lifecycle: Lifecycle get() = registry
                }
                setContent {
                    CompositionLocalProvider(LocalLifecycleOwner provides lifecycle) {
                        LlegeixTheme { FlashcardsHost(rememberNavController()) }
                    }
                }
                waitForIdle()
                fun shot(name: String) = screenshot("flashcards-$name")
                shot("1-empty")

                onNodeWithContentDescription("Baralla nova").performClick()
                waitForIdle()
                onAllNodes(hasSetTextAction())[0].performTextInput("Menjar")
                waitForIdle()
                shot("2-new-deck")
                onNodeWithText("Crea").performScrollTo().performClick()
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Menjar").fetchSemanticsNodes().isNotEmpty() }
                waitForIdle()
                shot("3-deck-made")

                onAllNodesWithText("Menjar")[0].performClick()
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodesWithContentDescription("Afegeix una targeta").fetchSemanticsNodes().isNotEmpty()
                }
                waitForIdle()
                shot("4-deck")
                onNodeWithContentDescription("Afegeix una targeta").performClick()
                waitForIdle()
                onAllNodes(hasSetTextAction())[0].performTextInput("poma")
                // The meaning arrives by itself: ARASAAC's Romanian label, or
                // the translator through English when that cannot be reached.
                waitUntil(timeoutMillis = 30_000) {
                    onAllNodes(hasSetTextAction() and hasText("măr")).fetchSemanticsNodes().isNotEmpty()
                }
                waitForIdle()
                shot("5-editor")
                onNodeWithText("Desa").performClick()
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodesWithText("Targeta nova").fetchSemanticsNodes().isEmpty()
                }
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("măr").fetchSemanticsNodes().isNotEmpty() }
                waitForIdle()
                shot("6-deck-with-card")
            }
    }
}
