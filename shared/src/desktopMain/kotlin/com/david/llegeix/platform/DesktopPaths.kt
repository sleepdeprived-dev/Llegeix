package com.david.llegeix.platform

import java.io.File

/**
 * Where the Mac app keeps its own data: Application Support, as Mac apps do.
 * `llegeix.data` moves it, so tests never touch the real one.
 */
val desktopDataDirectory: File
    get() = System.getProperty("llegeix.data")?.let(::File)
        ?: File(System.getProperty("user.home"), "Library/Application Support/Llegeix")
