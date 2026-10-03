package com.david.llegeix.platform

import java.io.File

/**
 * A helper program the Mac app carries — the translator, the page reader — as
 * a file that can actually be run, or null when there is none.
 *
 * [developmentProperty] names a development build (tools/macos), used while
 * running from Gradle. Otherwise it is the copy in the app's resources. When
 * that copy cannot be run — a disk image built before its executable bit was
 * restored, or a bundle opened read-only from the image itself — it is copied
 * into the app's own folder and made executable there, and that is what runs.
 */
internal fun bundledProgram(name: String, developmentProperty: String): File? {
    System.getProperty(developmentProperty)?.let(::File)?.takeIf { it.canExecute() }?.let { return it }
    val bundled = System.getProperty("compose.application.resources.dir")
        ?.let { File(it, name) }
        ?.takeIf { it.isFile }
        ?: return null
    if (bundled.canExecute()) return bundled
    return runCatching {
        val copy = desktopDataDirectory.resolve("programs/$name")
        // Copied again whenever the app brings a different one.
        if (!copy.isFile || copy.length() != bundled.length() || copy.lastModified() < bundled.lastModified()) {
            copy.parentFile.mkdirs()
            bundled.copyTo(copy, overwrite = true)
        }
        copy.setExecutable(true, true)
        copy.takeIf { it.canExecute() }
    }.getOrElse { error ->
        logWarning("BundledPrograms", "$name could not be made runnable", error)
        null
    }
}
