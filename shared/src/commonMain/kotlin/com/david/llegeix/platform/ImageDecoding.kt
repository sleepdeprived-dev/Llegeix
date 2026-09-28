package com.david.llegeix.platform

import androidx.compose.ui.graphics.ImageBitmap

/**
 * A picture from its encoded bytes, shrunk as it is decoded to about [maxEdge]
 * across (see ImageSizing.sampleSize), or null if the bytes are not a picture.
 */
expect fun decodeImage(bytes: ByteArray, maxEdge: Int): ImageBitmap?

/** What a decoded picture costs to keep in memory, for sizing caches. */
internal fun ImageBitmap.byteCount(): Int = width * height * 4
