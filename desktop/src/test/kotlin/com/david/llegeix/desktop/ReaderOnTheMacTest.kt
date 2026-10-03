package com.david.llegeix.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.david.llegeix.data.settings.TranslationTarget
import com.david.llegeix.ui.platform.DesktopBack
import com.david.llegeix.ui.reader.ReaderScreen
import com.david.llegeix.ui.theme.LlegeixTheme
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The reader on the Mac, worked with a mouse and keyboard: the page fits the
 * window, a double click looks a word up, a drag looks up what it crosses,
 * and the arrow keys turn the page. Screenshots of each step go to
 * desktop/build/screenshots.
 *
 * The test PDF's first page is A4 in 24 pt Helvetica: "finestra" sits at
 * 105–184 pt across, its line's baseline 760 pt up the 842 pt page;
 * "parlava amb" is on the line below.
 */
@OptIn(ExperimentalTestApi::class)
class ReaderOnTheMacTest {

    @Test
    fun readWithMouseAndKeyboard() = MacTestSession.run {
        val folder = Files.createTempDirectory("llegeix-reader").toFile()
        val pdf = File(folder, "prova.pdf")
        javaClass.getResourceAsStream("/prova.pdf")!!.use { input -> pdf.outputStream().use { input.copyTo(it) } }
        // The run's other tests share these settings, and the dictionary's
        // leaves the translation on Romanian.
        DesktopApp.settingsRepository.setTranslationTarget(TranslationTarget.ENGLISH)
        try {
            runComposeUiTest {
                // A window on screen is resumed; the test's stand-in has to be told.
                val lifecycle = object : LifecycleOwner {
                    val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
                    override val lifecycle: Lifecycle get() = registry
                }
                setContent {
                    CompositionLocalProvider(LocalLifecycleOwner provides lifecycle) {
                        LlegeixTheme { ReaderScreen(pdf.toURI().toString(), "prova", onBack = {}) }
                    }
                }
                waitUntil(timeoutMillis = 15_000) { onAllNodesWithContentDescription("Pàgina 1").fetchSemanticsNodes().isNotEmpty() }
                waitForIdle()
                screenshot("reader-1-page")
                val page = onAllNodesWithContentDescription("Pàgina 1")[0]

                // A double click on "finestra" looks it up.
                page.performMouseInput { doubleClick(page.at(145f, 772f)) }
                settleUntil { onAllNodes(hasText("window", substring = true, ignoreCase = true)).fetchSemanticsNodes().isNotEmpty() }
                waitForIdle()
                screenshot("reader-2-word")
                // Escape closes the sheet, as the back gesture does on the phone.
                DesktopBack.dispatch()
                settleUntil { onAllNodesWithText("Entesos").fetchSemanticsNodes().isEmpty() }

                // A drag from inside "parlava" to inside "amb" looks up both.
                page.performMouseInput {
                    moveTo(page.at(120f, 740f))
                    press()
                    moveTo(page.at(160f, 740f))
                    moveTo(page.at(205f, 740f))
                    release()
                }
                settleUntil { onAllNodesWithText("parlava amb").fetchSemanticsNodes().isNotEmpty() }
                waitForIdle()
                screenshot("reader-3-phrase")
                // Escape closes the sheet, as the back gesture does on the phone.
                DesktopBack.dispatch()
                settleUntil { onAllNodesWithText("Entesos").fetchSemanticsNodes().isEmpty() }

                // The right arrow turns the page.
                onRoot().performKeyInput { pressKey(Key.DirectionRight) }
                settleUntil { onAllNodesWithText("Pàgina 2 de 2").fetchSemanticsNodes().isNotEmpty() }
                waitForIdle()
                screenshot("reader-4-next")
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    /**
     * Waits for [condition] in real time while moving the test's clock along
     * with it: the reader's sheet waits a moment on that clock before it
     * rises, and the translator answers in real time.
     */
    private fun ComposeUiTest.settleUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 30_000
        while (true) {
            waitForIdle()
            if (condition()) return
            if (System.currentTimeMillis() > deadline) {
                screenshot("reader-timeout")
                error("Still waiting after 30 s")
            }
            mainClock.advanceTimeBy(100)
            Thread.sleep(50)
        }
    }

    /** A point given in PDF points (x across, y up from the foot) on the drawn page. */
    private fun SemanticsNodeInteraction.at(x: Float, y: Float): Offset {
        val size = fetchSemanticsNode().size
        return Offset(size.width * x / 595f, size.height * (1f - y / 842f))
    }
}
