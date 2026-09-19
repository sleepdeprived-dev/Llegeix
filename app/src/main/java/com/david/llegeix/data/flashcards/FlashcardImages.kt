package com.david.llegeix.data.flashcards

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.util.LruCache
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * The pictures on cards: copied in, shrunk, kept, shown and thrown away.
 *
 * ### Copied, not referred to
 *
 * The photo picker hands over a grant that lasts as long as the screen that
 * asked for it. A card that pointed at the photo in the gallery would lose its
 * picture the next day, or the moment the photo was deleted to free space, so
 * the picture is copied into the app's own storage the moment it is picked.
 *
 * ### Plain BitmapFactory, no image library
 *
 * Every picture here is a local file this class wrote itself, already no more
 * than [ImageSizing.MAX_EDGE] across, and a reader has dozens of them rather
 * than thousands. What an image library adds on top — network fetching, disk
 * caches, transformations, animated formats — is nothing this needs. Decoding
 * at the size it will be drawn, plus a small memory cache for the list, is the
 * whole job.
 *
 * Paths are stored relative to the files directory, as `flashcards/<name>.jpg`.
 *
 * The rotation is read with the platform's `android.media.ExifInterface`, not
 * the AndroidX copy lint suggests: the copy exists to fix the reader on Android
 * 6 and older, and this app starts at 12. Named in full rather than imported,
 * because lint's objection lands on the import line, where nothing can answer it.
 */
@SuppressLint("ExifInterface")
class FlashcardImages(context: Context) {

    private val appContext = context.applicationContext
    private val directory: File get() = File(appContext.filesDir, IMAGE_DIRECTORY)

    /**
     * Decoded pictures, sized by bytes.
     *
     * Enough for a screenful of list thumbnails and the card being edited;
     * anything more is re-read from disk, which for a file this small is quick.
     */
    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /**
     * Copy the picture at [uri] in, shrunk and turned the right way up.
     *
     * Returns the stored path. Throws if the file cannot be read as a picture,
     * which the form reports rather than saving a card with a hole in it.
     */
    suspend fun import(uri: Uri): String = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver

        // Reading only the size always returns null — the answer is written
        // into the options — so whether the file opened is checked on the
        // stream itself, and whether it is a picture on the size it reported.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val stream = resolver.openInputStream(uri) ?: throw IOException("cannot open $uri")
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("not a picture")

        val options = BitmapFactory.Options().apply {
            inSampleSize = ImageSizing.sampleSize(bounds.outWidth, bounds.outHeight)
        }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IOException("cannot decode $uri")

        // Phones store a photo the way the sensor saw it and write down which
        // way up it was meant to be. Without this, half of all portraits would
        // arrive on their side.
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use {
                android.media.ExifInterface(it).getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrNull() ?: android.media.ExifInterface.ORIENTATION_NORMAL

        val target = ImageSizing.fit(decoded.width, decoded.height)
        val matrix = Matrix().apply {
            postScale(
                target.width.toFloat() / decoded.width,
                target.height.toFloat() / decoded.height,
            )
            applyOrientation(orientation)
        }
        val shaped = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (shaped !== decoded) decoded.recycle()

        // JPEG has no transparency, and a transparent PNG saved as one turns its
        // clear parts black. White is what a picture with a clear background was
        // drawn to be seen against.
        val opaque = if (shaped.hasAlpha()) {
            createBitmap(shaped.width, shaped.height).also {
                Canvas(it).apply {
                    drawColor(Color.WHITE)
                    drawBitmap(shaped, 0f, 0f, null)
                }
                shaped.recycle()
            }
        } else {
            shaped
        }

        directory.mkdirs()
        val name = "${UUID.randomUUID()}.jpg"
        // Written aside and renamed into place, so a card can never be saved
        // pointing at half a file.
        val partial = File(directory, "$name.part")
        try {
            partial.outputStream().use { opaque.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            if (!partial.renameTo(File(directory, name))) throw IOException("cannot store picture")
        } finally {
            partial.delete()
            opaque.recycle()
        }
        "$IMAGE_DIRECTORY/$name"
    }

    /**
     * The picture at [path], decoded to about [maxEdge] across, or null if it
     * is missing or unreadable — a card still works without its picture.
     */
    suspend fun load(path: String, maxEdge: Int): Bitmap? {
        val key = "$path@$maxEdge"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val file = fileOf(path)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0) return@withContext null
            val options = BitmapFactory.Options().apply {
                inSampleSize = ImageSizing.sampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
            }
            BitmapFactory.decodeFile(file.path, options)?.also { cache.put(key, it) }
        }
    }

    suspend fun delete(paths: Collection<String>) = withContext(Dispatchers.IO) {
        for (path in paths) deleteNow(path)
    }

    /**
     * Delete at once, on the calling thread.
     *
     * For a form being thrown away, whose coroutines are already cancelled by
     * the time it hears about it. One small file is not worth a thread.
     */
    fun deleteNow(path: String) {
        runCatching { fileOf(path).delete() }
    }

    /**
     * Delete every picture no card points at.
     *
     * A safety net under the careful paths: a crash between copying a picture
     * in and saving the card, or between deleting a card and its file, leaves a
     * picture nothing can reach. Anything younger than [SWEEP_GRACE_MS] is left
     * alone, because it may belong to a card still being written.
     */
    suspend fun sweep(referenced: Set<String>, now: Long = System.currentTimeMillis()) =
        withContext(Dispatchers.IO) {
            val files = directory.listFiles() ?: return@withContext
            for (file in files) {
                val path = "$IMAGE_DIRECTORY/${file.name}"
                if (path !in referenced && now - file.lastModified() > SWEEP_GRACE_MS) {
                    runCatching { file.delete() }
                }
            }
        }

    /** Every picture, and the folder that holds them. */
    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        cache.evictAll()
        runCatching { directory.deleteRecursively() }
    }

    private fun fileOf(path: String): File = File(appContext.filesDir, path)

    private fun Matrix.applyOrientation(orientation: Int) {
        when (orientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            android.media.ExifInterface.ORIENTATION_TRANSPOSE -> {
                postRotate(90f)
                postScale(-1f, 1f)
            }

            android.media.ExifInterface.ORIENTATION_TRANSVERSE -> {
                postRotate(270f)
                postScale(-1f, 1f)
            }
        }
    }

    companion object {
        /** Where card pictures are kept, under the app's files directory. */
        const val IMAGE_DIRECTORY = "flashcards"

        /** High enough that a photo of a word's meaning shows no blocks. */
        private const val JPEG_QUALITY = 85

        private const val CACHE_BYTES = 24 * 1024 * 1024

        private val SWEEP_GRACE_MS = TimeUnit.HOURS.toMillis(1)
    }
}
