package com.david.llegeix.translate

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

/**
 * The Mac translates between Catalan and Romanian, both ways, through English.
 *
 * Runs against the development translator and the models tools/macos fetched,
 * set up in a scratch data folder with the layout the app installs into.
 */
class DesktopTranslatorTest {

    @get:Rule val folder = TemporaryFolder()

    @Before
    fun models() {
        val binary = System.getProperty("llegeix.bergamot")?.let(::File)
        val models = System.getProperty("llegeix.testModels")?.let(::File)
        assumeTrue("tools/macos/build-bergamot.sh has not been run", binary?.canExecute() == true)
        assumeTrue("tools/macos/fetch-translation-models.sh has not been run", models?.isDirectory == true)
        val data = folder.newFolder("data")
        val translation = File(data, "translation").apply { mkdirs() }
        for (pair in listOf("caen", "enca", "enro", "roen")) {
            val installed = File(translation, pair).apply { mkdirs() }
            File(models, pair).listFiles()!!.filter { !it.name.endsWith(".yml") }.forEach {
                Files.createSymbolicLink(File(installed, it.name).toPath(), it.toPath())
            }
            // The config the app itself would have written.
            File(installed, "config.yml").writeText(
                File(models, "$pair/config.yml").readText().replace(File(models, pair).path, installed.path),
            )
        }
        System.setProperty("llegeix.data", data.path)
    }

    @After
    fun reset() {
        System.clearProperty("llegeix.data")
    }

    @Test
    fun catalanToRomanian() = runBlocking {
        val translator = WordTranslator(targetLanguage = "ro", sourceLanguage = "ca")
        assertTrue(translator.isModelReady)
        assertEquals("fereastră", translator.translate("finestra").lowercase())
    }

    @Test
    fun romanianToCatalan() = runBlocking {
        val translator = WordTranslator(targetLanguage = "ca", sourceLanguage = "ro")
        assertEquals("finestra", translator.translate("fereastră").lowercase())
    }

    @Test
    fun catalanToEnglishIsDirect() = runBlocking {
        assertEquals(listOf("caen"), Bergamot.route("ca", "en"))
        assertEquals(listOf("roen", "enca"), Bergamot.route("ro", "ca"))
        assertEquals("book", WordTranslator(targetLanguage = "en").translate("llibre").lowercase())
    }
}
