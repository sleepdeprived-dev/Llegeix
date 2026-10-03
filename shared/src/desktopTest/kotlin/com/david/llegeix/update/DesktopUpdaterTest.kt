package com.david.llegeix.update

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.prefs.Preferences

/** What the Mac's updater decides for itself, without the network. */
class DesktopUpdaterTest {

    // Scratch preferences, never the real ones.
    private val prefs = "com/david/llegeix/test-updater-${System.nanoTime()}"
    private val updater = run {
        System.setProperty("llegeix.prefs", prefs)
        desktopUpdater()
    }

    @After
    fun tearDown() {
        Preferences.userRoot().node(prefs).removeNode()
        System.clearProperty("llegeix.prefs")
    }

    @Test
    fun knowsItsOwnVersion() {
        val packaged = System.getProperty("jpackage.app-version")
        System.setProperty("jpackage.app-version", "4.5.0")
        try {
            assertEquals("4.5.0", updater.installedVersion)
        } finally {
            if (packaged == null) System.clearProperty("jpackage.app-version") else System.setProperty("jpackage.app-version", packaged)
        }
    }

    @Test
    fun acceptsADiskImageAndNothingElse() {
        val folder = Files.createTempDirectory("llegeix-update").toFile()
        try {
            // A disk image ends in a 512-byte trailer that starts "koly".
            val image = File(folder, "Llegeix.dmg").apply {
                writeBytes(ByteArray(4096) + "koly".toByteArray() + ByteArray(508))
            }
            assertTrue(updater.isOurBuild(image))

            val page = File(folder, "Llegeix-4.5.0-macos.dmg").apply { writeText("<html>Not Found</html>") }
            assertFalse(updater.isOurBuild(page))
            assertFalse(updater.isOurBuild(File(folder, "missing.dmg")))
        } finally {
            folder.deleteRecursively()
        }
    }
}
