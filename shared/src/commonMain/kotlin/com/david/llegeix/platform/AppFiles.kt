package com.david.llegeix.platform

import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** The app's own folders, and a way into the files the reader picks. */
interface AppFiles {
    /** Kept until the app is removed: card pictures, and the like. */
    val filesDir: File

    /** The system may clear it: unpacked backups, downloads on their way in. */
    val cacheDir: File

    fun openInput(ref: ContentRef): InputStream?

    /** Replacing whatever was there. */
    fun openOutput(ref: ContentRef): OutputStream?
}
