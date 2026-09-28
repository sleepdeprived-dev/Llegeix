package com.david.llegeix.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.io.File
import java.nio.file.Files
import java.util.Locale
import java.util.prefs.Preferences
import javax.imageio.ImageIO

/**
 * Targetes on the Mac, driven as a reader would: make a deck, add a card to
 * it. Scratch data and scratch preferences; screenshots of each step go to
 * desktop/build/screenshots.
 */
@OptIn(ExperimentalTestApi::class)
class FlashcardsOnTheMacTest {

    @Test
    fun makeADeckAndAddACard() {
        val data = Files.createTempDirectory("llegeix-mac").toFile()
        val prefs = "com/david/llegeix/test-${System.nanoTime()}"
        System.setProperty("llegeix.data", data.path)
        System.setProperty("llegeix.prefs", prefs)
        Locale.setDefault(Locale.forLanguageTag("ca"))
        installTestModels(data)
        DesktopApp.start()
        // The test's own thread stands in for the Mac's main one, which is
        // where a real window's clicks arrive and navigation insists on.
        // Unconfined rather than a test dispatcher, so the editor's typing
        // pause before it suggests a meaning passes in real time.
        Dispatchers.setMain(Dispatchers.Unconfined)
        try {
            flow()
        } finally {
            Dispatchers.resetMain()
            Preferences.userRoot().node(prefs).removeNode()
            data.deleteRecursively()
        }
    }

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
                fun shot(name: String) {
                    val dir = File(System.getProperty("llegeix.screenshots")).apply { mkdirs() }
                    // The topmost layer: the screen, or a sheet open over it.
                    ImageIO.write(onAllNodes(isRoot()).onLast().captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
                }
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

    /** The models tools/macos fetched, laid out as the app installs them. */
    private fun installTestModels(data: File) {
        val models = System.getProperty("llegeix.testModels")?.let(::File)?.takeIf { it.isDirectory } ?: return
        val translation = File(data, "translation").apply { mkdirs() }
        for (pair in listOf("caen", "enca", "enro", "roen")) {
            val installed = File(translation, pair).apply { mkdirs() }
            File(models, pair).listFiles()!!.filter { !it.name.endsWith(".yml") }.forEach {
                Files.createSymbolicLink(File(installed, it.name).toPath(), it.toPath())
            }
            File(installed, "config.yml").writeText(
                File(models, "$pair/config.yml").readText().replace(File(models, pair).path, installed.path),
            )
        }
    }

}
