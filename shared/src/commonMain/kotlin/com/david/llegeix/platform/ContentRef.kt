package com.david.llegeix.platform

import java.io.File

/**
 * A file the reader handed the app: a picture to put on a card, a place to save
 * a backup, a backup to restore. Android's content Uri on the phone, which is
 * how the system's pickers answer; a plain File on the Mac.
 */
expect class ContentRef

/** A file of the app's own, as a [ContentRef]. */
expect fun contentRefOf(file: File): ContentRef
