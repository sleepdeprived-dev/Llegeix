package com.david.llegeix.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.david.llegeix.data.flashcards.ImageSizing
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

/** A file the reader chose in a Mac open or save panel. */
actual class ContentRef(val file: File) {
    override fun toString(): String = file.path
}

actual fun contentRefOf(file: File): ContentRef = ContentRef(file)

/** The Mac app's folders, under Application Support and the user's caches. */
class DesktopAppFiles(root: File = desktopDataDirectory) : AppFiles {
    override val filesDir: File = File(root, "files").apply { mkdirs() }
    override val cacheDir: File =
        (System.getProperty("llegeix.data")?.let { File(it, "cache") }
            ?: File(System.getProperty("user.home"), "Library/Caches/Llegeix")).apply { mkdirs() }
    override fun openInput(ref: ContentRef): InputStream? = ref.file.takeIf { it.isFile }?.inputStream()
    override fun openOutput(ref: ContentRef): OutputStream? = ref.file.outputStream()
}

/** Decoded by Skia and drawn down to the size the phone's sampling would give. */
actual fun decodeImage(bytes: ByteArray, maxEdge: Int): ImageBitmap? {
    val image = runCatching { Image.makeFromEncoded(bytes) }.getOrNull() ?: return null
    if (image.width <= 0 || image.height <= 0) return null
    val sample = ImageSizing.sampleSize(image.width, image.height, maxEdge)
    return SkiaImages.scaled(image, image.width / sample, image.height / sample).toComposeImageBitmap()
}

actual fun imageFromPixels(pixels: IntArray, width: Int, height: Int): ImageBitmap {
    // ARGB in an int is B, G, R, A in memory on a little-endian Mac: Skia's BGRA.
    val bytes = ByteArray(width * height * 4)
    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(pixels, 0, width * height)
    val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
    return Bitmap().apply {
        installPixels(info, bytes, width * 4)
        setImmutable()
    }.asComposeImageBitmap()
}
