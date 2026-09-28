package com.david.llegeix.platform

import java.io.File

/**
 * Where the Mac app keeps its own data: Application Support, as Mac apps do.
 * `llegeix.data` moves it, so tests never touch the real one.
 */
val desktopDataDirectory: File
    get() = System.getProperty("llegeix.data")?.let(::File)
        ?: File(System.getProperty("user.home"), "Library/Application Support/Llegeix")

/**
 * Where the Mac app's preferences live in java.util.prefs, which macOS keeps as
 * ~/Library/Preferences/com.david.llegeix.plist. `llegeix.prefs` moves it, so
 * tests never touch the real settings.
 */
val desktopPrefsRoot: java.util.prefs.Preferences
    get() = java.util.prefs.Preferences.userRoot().node(System.getProperty("llegeix.prefs") ?: "com/david/llegeix")
