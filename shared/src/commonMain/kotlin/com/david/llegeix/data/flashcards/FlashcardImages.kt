package com.david.llegeix.data.flashcards

import androidx.compose.ui.graphics.ImageBitmap
import com.david.llegeix.platform.ContentRef
import java.io.File

/**
 * The pictures on cards: copied in shrunk and the right way up, stored under
 * the app's files as JPEG (or PNG where they have see-through parts), and read
 * back at the size a screen asks for. The phone does the decoding with
 * Android's own BitmapFactory; the Mac with Skia.
 */
expect class FlashcardImages {

    /**
     * Copy the picture at [uri] in, shrunk and turned the right way up. Returns
     * the stored path; throws if the file cannot be read as a picture.
     */
    suspend fun import(uri: ContentRef): String

    /** The picture at [path], at about [maxEdge] across, or null if missing or unreadable. */
    suspend fun load(path: String, maxEdge: Int): ImageBitmap?

    suspend fun delete(paths: Collection<String>)

    /** Delete at once, on the calling thread. */
    fun deleteNow(path: String)

    /** Delete every picture no card points at, leaving any still being written. */
    suspend fun sweep(referenced: Set<String>, now: Long = System.currentTimeMillis())

    /** Every picture, and the folder that holds them. */
    suspend fun deleteAll()

    /** Where a stored picture is on disk, for copying it into a backup. */
    fun fileOf(path: String): File
}

/** Where card pictures are kept, under the app's files directory. */
const val IMAGE_DIRECTORY = "flashcards"

/** High enough that a photo of a word's meaning shows no blocks. */
internal const val JPEG_QUALITY = 85
