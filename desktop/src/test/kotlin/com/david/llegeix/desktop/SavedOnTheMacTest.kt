package com.david.llegeix.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.rememberNavController
import com.david.llegeix.data.db.entity.WordBookmarkEntity
import com.david.llegeix.ui.theme.LlegeixTheme
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Desat on the Mac, driven as a reader would: a saved word in its list,
 * practised as a card, and a new collection made. Screenshots of each step go
 * to desktop/build/screenshots.
 */
@OptIn(ExperimentalTestApi::class)
class SavedOnTheMacTest {

    private companion object {
        const val KNEW_IT = "Ho sabia"
    }

    @Test
    fun wordsPracticeAndCollections() = MacTestSession.run {
        // A word of its own, so the other tests' saved words make no difference.
        runBlocking {
            val data = DesktopApp.libraryDataRepository
            if (data.findWordBookmark("poma", null, 0) == null) {
                data.toggleWordBookmark(
                    WordBookmarkEntity(
                        word = "poma", translation = "apple", ipa = "ˈpomə", context = null,
                        documentUri = null, displayName = null, pageIndex = 0, lineNumber = 0,
                    ),
                )
            }
        }
        runComposeUiTest {
            // A window on screen is resumed; the test's stand-in has to be told.
            val lifecycle = object : LifecycleOwner {
                val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
                override val lifecycle: Lifecycle get() = registry
            }
            setContent {
                CompositionLocalProvider(LocalLifecycleOwner provides lifecycle) {
                    LlegeixTheme { SavedHost(rememberNavController()) }
                }
            }
            settleUntil { onAllNodesWithText("Paraules").fetchSemanticsNodes().isNotEmpty() }
            screenshot("saved-1-collections")

            onAllNodesWithText("Paraules")[0].performClick()
            settleUntil { onAllNodesWithText("poma").fetchSemanticsNodes().isNotEmpty() }
            screenshot("saved-2-words")

            // Practice: the card asks, a click answers.
            onAllNodesWithText("Repassa")[0].performClick()
            settleUntil { onAllNodesWithText("Fes clic per veure la resposta").fetchSemanticsNodes().isNotEmpty() }
            screenshot("saved-3-practice")
            // Anywhere on the card but its middle, where the speaker button is.
            onAllNodesWithText("Fes clic per veure la resposta")[0].performMouseInput { click(Offset(width / 2f, 40f)) }
            // Turned over: the hint goes and the two answers come. Which card
            // comes first depends on what else the run has saved.
            settleUntil {
                onAllNodesWithText("Fes clic per veure la resposta").fetchSemanticsNodes().isEmpty() &&
                    onAllNodesWithText(KNEW_IT).fetchSemanticsNodes().isNotEmpty()
            }
            screenshot("saved-4-answer")
        }
        // A fresh window for the collections, as coming back to the section would be.
        runComposeUiTest {
            val lifecycle = object : LifecycleOwner {
                val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
                override val lifecycle: Lifecycle get() = registry
            }
            setContent {
                CompositionLocalProvider(LocalLifecycleOwner provides lifecycle) {
                    LlegeixTheme { SavedHost(rememberNavController()) }
                }
            }
            settleUntil { onAllNodesWithText("Col·leccions").fetchSemanticsNodes().isNotEmpty() }
            onAllNodesWithText("Col·leccions")[0].performClick()
            onNodeWithContentDescription("Col·lecció nova").performClick()
            settleUntil { onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
            onAllNodes(hasSetTextAction())[0].performTextInput("Llibres de prova")
            onAllNodesWithText("Crea")[0].performClick()
            // A new collection opens ready to be filled.
            settleUntil { onAllNodesWithText("Llibres de prova").fetchSemanticsNodes().isNotEmpty() }
            screenshot("saved-5-new-collection")
        }
    }
}
