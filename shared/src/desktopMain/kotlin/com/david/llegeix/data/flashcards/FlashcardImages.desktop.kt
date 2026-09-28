package com.david.llegeix.data.flashcards

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.david.llegeix.platform.AppFiles
import com.david.llegeix.platform.ContentRef
import com.david.llegeix.platform.SkiaImages
import com.david.llegeix.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Card pictures on the Mac: the phone's rules (shrunk to ImageSizing, turned
 * upright, JPEG unless it has see-through parts, written aside then renamed
 * into place), with Skia doing the decoding and encoding.
 */
actual class FlashcardImages(private val files: AppFiles) {

    private val directory: File get() = File(files.filesDir, IMAGE_DIRECTORY)

    private val cache = object : LruCache<String, ImageBitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    actual suspend fun import(uri: ContentRef): String = withContext(Dispatchers.IO) {
        val bytes = files.openInput(uri)?.use { it.readBytes() } ?: throw IOException("cannot open $uri")
        // Skia reads the camera's orientation as it decodes, so what comes
        // back is already upright: a portrait is taller than it is wide.
        val decoded = runCatching { Image.makeFromEncoded(bytes) }.getOrNull()
            ?: throw IOException("not a picture")
        if (decoded.width <= 0 || decoded.height <= 0) throw IOException("not a picture")

        val target = ImageSizing.fit(decoded.width, decoded.height)
        val shaped = SkiaImages.scaled(decoded, target.width, target.height)

        val transparent = SkiaImages.hasClearPixels(shaped)
        directory.mkdirs()
        val name = "${UUID.randomUUID()}." + if (transparent) "png" else "jpg"
        val encoded = if (transparent) {
            shaped.encodeToData(EncodedImageFormat.PNG, 100)
        } else {
            shaped.encodeToData(EncodedImageFormat.JPEG, JPEG_QUALITY)
        } ?: throw IOException("cannot encode picture")
        val partial = File(directory, "$name.part")
        try {
            partial.writeBytes(encoded.bytes)
            if (!partial.renameTo(File(directory, name))) throw IOException("cannot store picture")
        } finally {
            partial.delete()
        }
        "$IMAGE_DIRECTORY/$name"
    }

    actual suspend fun load(path: String, maxEdge: Int): ImageBitmap? {
        val key = "$path@$maxEdge"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val file = fileOf(path)
            if (!file.isFile) return@withContext null
            val image = runCatching { Image.makeFromEncoded(file.readBytes()) }.getOrNull()
                ?: return@withContext null
            val sample = ImageSizing.sampleSize(image.width, image.height, maxEdge)
            SkiaImages.scaled(image, image.width / sample, image.height / sample)
                .toComposeImageBitmap()
                .also { cache.put(key, it) }
        }
    }

    actual suspend fun delete(paths: Collection<String>): Unit = withContext(Dispatchers.IO) {
        for (path in paths) deleteNow(path)
    }

    actual fun deleteNow(path: String) {
        runCatching { fileOf(path).delete() }
    }

    actual suspend fun sweep(referenced: Set<String>, now: Long): Unit = withContext(Dispatchers.IO) {
        val found = directory.listFiles() ?: return@withContext
        for (file in found) {
            val path = "$IMAGE_DIRECTORY/${file.name}"
            if (path !in referenced && now - file.lastModified() > SWEEP_GRACE_MS) {
                runCatching { file.delete() }
            }
        }
    }

    actual suspend fun deleteAll(): Unit = withContext(Dispatchers.IO) {
        cache.evictAll()
        runCatching { directory.deleteRecursively() }
    }

    actual fun fileOf(path: String): File = File(files.filesDir, path)

    private companion object {
        const val CACHE_BYTES = 24 * 1024 * 1024
        val SWEEP_GRACE_MS = TimeUnit.HOURS.toMillis(1)
    }
}
