package com.david.llegeix.update

import com.david.llegeix.data.settings.javaPrefsStore
import com.david.llegeix.platform.desktopDataDirectory
import com.david.llegeix.platform.desktopPrefsRoot
import java.io.File
import java.io.RandomAccessFile

/** The Mac's updater, its memory beside the app's other preferences. */
fun desktopUpdater(): DesktopUpdater = DesktopUpdater()

/**
 * The Mac's updater: the same release the phone reads, and in it the disk
 * image, `Llegeix-<version>-macos.dmg`. A newer one is downloaded into the
 * app's own folder and opened in the Finder, where the reader drags Llegeix
 * into Applications as with any Mac app; nothing is installed by the app.
 */
class DesktopUpdater internal constructor() : AppUpdater(javaPrefsStore(desktopPrefsRoot.node(PREFS_NAME))) {

    /**
     * The version of the app bundle running, which jpackage's launcher passes
     * on; while developing, the one the build names.
     */
    override val installedVersion: String
        get() = System.getProperty("jpackage.app-version")
            ?: System.getProperty("llegeix.version")
            ?: ""

    override fun buildFor(release: PublishedRelease): ChosenBuild? = ReleaseFeed.macBuildFor(release)

    override fun downloadFolder(): File = desktopDataDirectory.resolve(UPDATE_FOLDER)

    override fun fileNameFor(update: AvailableUpdate): String = "Llegeix-${update.version}-macos.dmg"

    /**
     * Whether [file] is a disk image at all: every one ends in a 512-byte
     * trailer that starts "koly". What is inside is macOS's to check — the
     * Finder will not open a damaged image, and Gatekeeper looks at the app's
     * signature the first time it is opened.
     */
    override fun isOurBuild(file: File): Boolean = runCatching {
        RandomAccessFile(file, "r").use { image ->
            if (image.length() < TRAILER_BYTES) return false
            image.seek(image.length() - TRAILER_BYTES)
            val magic = ByteArray(4).also { image.readFully(it) }
            magic.contentEquals("koly".toByteArray())
        }
    }.getOrDefault(false)

    /** The Mac asks nothing of the app before an image is opened. */
    override fun canInstall(): Boolean = true

    private companion object {
        const val PREFS_NAME = "llegeix.updates"
        const val UPDATE_FOLDER = "updates"
        const val TRAILER_BYTES = 512L
    }
}
