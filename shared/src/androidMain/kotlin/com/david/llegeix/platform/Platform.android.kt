package com.david.llegeix.platform

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.david.llegeix.data.flashcards.ImageSizing
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** The Uri a system picker answered with. */
actual class ContentRef(val uri: Uri) {
    override fun toString(): String = uri.toString()
}

actual fun contentRefOf(file: File): ContentRef = ContentRef(Uri.fromFile(file))

/** The app's folders and the content resolver, from its [Context]. */
class AndroidAppFiles(context: Context) : AppFiles {
    private val appContext = context.applicationContext
    override val filesDir: File get() = appContext.filesDir
    override val cacheDir: File get() = appContext.cacheDir
    override fun openInput(ref: ContentRef): InputStream? = appContext.contentResolver.openInputStream(ref.uri)
    override fun openOutput(ref: ContentRef): OutputStream? = appContext.contentResolver.openOutputStream(ref.uri, "wt")
}

/** Sized on the way in, as the phone always has: the bounds first, then a sampled decode. */
actual fun decodeImage(bytes: ByteArray, maxEdge: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = ImageSizing.sampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}
