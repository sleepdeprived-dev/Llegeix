package com.david.llegeix.data.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import java.io.File

/**
 * The Mac's database, at [file].
 *
 * SQLite comes bundled rather than from the system, so the Mac runs the same
 * SQLite build whatever version of macOS it is on. There are no migrations:
 * a Mac library starts at the current version, with nothing older to carry
 * forward. A future schema change needs its migration here as well as on the
 * phone.
 */
fun LlegeixDatabase.Companion.openDesktop(file: File): LlegeixDatabase {
    file.parentFile?.mkdirs()
    return Room.databaseBuilder<LlegeixDatabase>(name = file.absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}
