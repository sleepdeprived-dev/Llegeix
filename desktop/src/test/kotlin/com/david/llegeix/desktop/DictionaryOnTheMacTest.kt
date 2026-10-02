package com.david.llegeix.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.david.llegeix.ui.dictionary.DictionaryScreen
import com.david.llegeix.ui.theme.LlegeixTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Diccionari on the Mac, driven as a reader would: look a word up, read it in
 * both languages, keep it, find it again among the recent searches, and have
 * a conjugated verb placed.
 * Screenshots of each step go to desktop/build/screenshots.
 */
@OptIn(ExperimentalTestApi::class)
class DictionaryOnTheMacTest {

    @Test
    fun lookUpAndKeepAWord() = MacTestSession.run {
        runComposeUiTest {
            // A window on screen is resumed; the test's stand-in has to be told.
            val lifecycle = object : LifecycleOwner {
                val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
                override val lifecycle: Lifecycle get() = registry
            }
            setContent {
                CompositionLocalProvider(LocalLifecycleOwner provides lifecycle) {
                    LlegeixTheme { DictionaryScreen() }
                }
            }
            waitForIdle()
            screenshot("dictionary-1-empty")

            val field = onAllNodes(hasSetTextAction())[0]
            field.performTextInput("finest")
            // The word bank offers headwords as the reader types.
            waitUntil(timeoutMillis = 15_000) {
                onAllNodes(hasText("finestra") and !hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
            }
            waitForIdle()
            screenshot("dictionary-2-suggestions")

            onAllNodes(hasText("finestra") and !hasSetTextAction())[0].performClick()
            // The pronunciation is there at once, the definition from the
            // bundled Viccionari, the meaning from the translator.
            waitUntil(timeoutMillis = 30_000) {
                onAllNodes(hasText("window", substring = true, ignoreCase = true)).fetchSemanticsNodes().isNotEmpty()
            }
            waitUntil(timeoutMillis = 10_000) {
                onAllNodesWithText("[", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            waitForIdle()
            screenshot("dictionary-3-english")

            // The flag beside the word switches the language, and the word on
            // screen answers again in the new one.
            onNodeWithContentDescription("Tradueix al romanès").performClick()
            waitUntil(timeoutMillis = 30_000) {
                onAllNodes(hasText("fereastr", substring = true, ignoreCase = true)).fetchSemanticsNodes().isNotEmpty()
            }
            waitForIdle()
            screenshot("dictionary-4-romanian")

            onNodeWithContentDescription("Desa aquesta paraula").performClick()
            waitUntil(timeoutMillis = 10_000) {
                onAllNodesWithContentDescription("Treu de les paraules desades").fetchSemanticsNodes().isNotEmpty()
            }
            val saved = runBlocking { DesktopApp.libraryDataRepository.observeWordBookmarks().first() }
            assertTrue(saved.any { it.word == "finestra" && it.translation.orEmpty().startsWith("fereastr", ignoreCase = true) })

            // Back to asking: the word is offered again among the recent searches.
            field.performTextClearance()
            waitUntil(timeoutMillis = 10_000) {
                onAllNodes(hasText("finestra") and !hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
            }
            waitForIdle()
            screenshot("dictionary-5-recent")

            // A conjugated verb is placed, and named by its infinitive.
            field.performTextInput("parlava")
            onAllNodes(hasText("parlava") and !hasSetTextAction())[0].performClick()
            waitUntil(timeoutMillis = 15_000) {
                onAllNodesWithText("Forma verbal").fetchSemanticsNodes().isNotEmpty() &&
                    onAllNodes(hasText("parlar", substring = true) and !hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
            }
            waitForIdle()
            screenshot("dictionary-6-verb")
        }
    }
}
