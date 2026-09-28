package com.david.llegeix.platform

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

/** The Mac's picture handling, on the Skia that Compose already draws with. */
internal object SkiaImages {

    /** [image] drawn at [width] × [height], smoothly. */
    fun scaled(image: Image, width: Int, height: Int): Image {
        if (width == image.width && height == image.height) return image
        val surface = Surface.makeRaster(ImageInfo.makeN32Premul(width.coerceAtLeast(1), height.coerceAtLeast(1)))
        surface.canvas.drawImageRect(
            image,
            Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
            Rect.makeWH(width.toFloat(), height.toFloat()),
            FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR),
            null,
            true,
        )
        return surface.makeImageSnapshot()
    }

    /** Whether any pixel is less than fully opaque; an "alpha" PNG often has none. */
    fun hasClearPixels(image: Image): Boolean {
        val bitmap = Bitmap().apply {
            allocPixels(ImageInfo.makeN32(image.width, image.height, ColorAlphaType.UNPREMUL))
        }
        if (!image.readPixels(bitmap)) return false
        val bytes = bitmap.readPixels() ?: return false
        // N32 is four bytes a pixel, alpha last on the Mac (BGRA).
        for (i in 3 until bytes.size step 4) if (bytes[i] != 0xFF.toByte()) return true
        return false
    }
}
