package com.david.llegeix.desktop

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.io.File
import java.nio.file.Files
import java.util.Locale
import java.util.prefs.Preferences
import javax.imageio.ImageIO

/**
 * One scratch Mac app for the whole test run.
 *
 * [DesktopApp] is an object, and opens its database and preferences the first
 * time they are asked for, so every test in the run shares them: they are set
 * up once, before the first test, and thrown away when the run ends. Nothing
 * touches the real Application Support or the real preferences.
 */
object MacTestSession {

    private val data: File = Files.createTempDirectory("llegeix-mac").toFile()
    private val prefs = "com/david/llegeix/test-${System.nanoTime()}"

    init {
        System.setProperty("llegeix.data", data.path)
        System.setProperty("llegeix.prefs", prefs)
        Locale.setDefault(Locale.forLanguageTag("ca"))
        installTestModels(data)
        DesktopApp.start()
        Runtime.getRuntime().addShutdownHook(
            Thread {
                runCatching { Preferences.userRoot().node(prefs).removeNode() }
                data.deleteRecursively()
            },
        )
    }

    /**
     * Runs [test] against the scratch app.
     *
     * The test's own thread stands in for the Mac's main one, which is where a
     * real window's clicks arrive and navigation insists on. Unconfined rather
     * than a test dispatcher, so the screens' typing pauses pass in real time.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun run(test: () -> Unit) {
        Dispatchers.setMain(Dispatchers.Unconfined)
        try {
            test()
        } finally {
            Dispatchers.resetMain()
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

/** The topmost layer — the screen, or a sheet open over it — to desktop/build/screenshots. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.screenshot(name: String) {
    val dir = File(System.getProperty("llegeix.screenshots")).apply { mkdirs() }
    ImageIO.write(onAllNodes(isRoot()).onLast().captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
}

/**
 * Waits for [condition] in real time while moving the test's clock along with
 * it: sheets and the reader wait a moment on that clock before they move, and
 * the translator and the database answer in real time. A screenshot of how
 * things stood goes with a timeout.
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.settleUntil(timeoutMillis: Long = 30_000, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (true) {
        waitForIdle()
        if (condition()) return
        if (System.currentTimeMillis() > deadline) {
            screenshot("timeout")
            error("Still waiting after ${timeoutMillis / 1000} s")
        }
        mainClock.advanceTimeBy(100)
        Thread.sleep(50)
    }
}
