package com.david.llegeix.pdf

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.io.File
import java.io.IOException
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_DOUBLE
import java.lang.foreign.ValueLayout.JAVA_FLOAT
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.foreign.ValueLayout.JAVA_LONG
import java.lang.invoke.MethodHandle
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Open [file] for rendering. Throws [IOException] when the document cannot be
 * read — a missing file, or a PDF PDFium rejects as malformed or
 * password-protected.
 */
suspend fun PdfiumPageRenderer.Companion.open(file: File): PdfiumPageRenderer =
    withContext(Dispatchers.IO) {
        PdfiumPageRenderer(DesktopPdfiumDocument.load(file))
    }

/**
 * PDFium on the Mac: the library from tools/macos/fetch-pdfium.sh, called
 * through the JDK's own foreign function interface, so nothing but the library
 * itself has to be shipped.
 *
 * PDFium is not thread-safe at all, not even across documents, so every call
 * into it holds [lock]. pdfiumandroid does the same on the phone.
 */
internal object PdfiumLibrary {

    val lock = ReentrantLock()

    private val linker = Linker.nativeLinker()

    private val symbols: SymbolLookup by lazy {
        SymbolLookup.libraryLookup(libraryFile().toPath(), Arena.global())
    }

    /**
     * Where the library is: named by `llegeix.pdfium` while developing, and
     * among the app's own resources once packaged.
     */
    private fun libraryFile(): File {
        System.getProperty("llegeix.pdfium")?.let { return File(it) }
        System.getProperty("compose.application.resources.dir")?.let { return File(it, "libpdfium.dylib") }
        throw IOException("PDFium was not found: run tools/macos/fetch-pdfium.sh")
    }

    private fun function(name: String, result: MemoryLayout?, vararg arguments: MemoryLayout): MethodHandle {
        val descriptor = if (result == null) {
            FunctionDescriptor.ofVoid(*arguments)
        } else {
            FunctionDescriptor.of(result, *arguments)
        }
        val address = symbols.find(name).orElseThrow { IOException("PDFium has no $name") }
        return linker.downcallHandle(address, descriptor)
    }

    /** Started once, on first use; never shut down, as the phone's never is. */
    private val started: Unit by lazy { function("FPDF_InitLibrary", null).invokeWithArguments(); Unit }

    /** Runs [block] inside the library, holding the lock. */
    fun <T> call(block: PdfiumLibrary.() -> T): T = lock.withLock {
        started
        block()
    }

    // fpdfview.h
    val loadDocument by lazy { function("FPDF_LoadDocument", ADDRESS, ADDRESS, ADDRESS) }
    val lastError by lazy { function("FPDF_GetLastError", JAVA_LONG) }
    val closeDocument by lazy { function("FPDF_CloseDocument", null, ADDRESS) }
    val pageCount by lazy { function("FPDF_GetPageCount", JAVA_INT, ADDRESS) }
    val loadPage by lazy { function("FPDF_LoadPage", ADDRESS, ADDRESS, JAVA_INT) }
    val closePage by lazy { function("FPDF_ClosePage", null, ADDRESS) }
    val pageWidth by lazy { function("FPDF_GetPageWidthF", JAVA_FLOAT, ADDRESS) }
    val pageHeight by lazy { function("FPDF_GetPageHeightF", JAVA_FLOAT, ADDRESS) }
    val createBitmap by lazy { function("FPDFBitmap_CreateEx", ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT) }
    val fillRect by lazy { function("FPDFBitmap_FillRect", JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG) }
    val bitmapBuffer by lazy { function("FPDFBitmap_GetBuffer", ADDRESS, ADDRESS) }
    val bitmapStride by lazy { function("FPDFBitmap_GetStride", JAVA_INT, ADDRESS) }
    val destroyBitmap by lazy { function("FPDFBitmap_Destroy", null, ADDRESS) }
    val renderPage by lazy {
        function("FPDF_RenderPageBitmap", null, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT)
    }

    // fpdf_text.h
    val loadText by lazy { function("FPDFText_LoadPage", ADDRESS, ADDRESS) }
    val closeText by lazy { function("FPDFText_ClosePage", null, ADDRESS) }
    val countChars by lazy { function("FPDFText_CountChars", JAVA_INT, ADDRESS) }
    val unicode by lazy { function("FPDFText_GetUnicode", JAVA_INT, ADDRESS, JAVA_INT) }
    val charIndexAtPos by lazy {
        function("FPDFText_GetCharIndexAtPos", JAVA_INT, ADDRESS, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE)
    }
    val getText by lazy { function("FPDFText_GetText", JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS) }
    val charBox by lazy { function("FPDFText_GetCharBox", JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS) }
    val countRects by lazy { function("FPDFText_CountRects", JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT) }
    val getRect by lazy { function("FPDFText_GetRect", JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS) }
    val findStart by lazy { function("FPDFText_FindStart", ADDRESS, ADDRESS, ADDRESS, JAVA_LONG, JAVA_INT) }
    val findNext by lazy { function("FPDFText_FindNext", JAVA_INT, ADDRESS) }
    val resultIndex by lazy { function("FPDFText_GetSchResultIndex", JAVA_INT, ADDRESS) }
    val resultCount by lazy { function("FPDFText_GetSchCount", JAVA_INT, ADDRESS) }
    val findClose by lazy { function("FPDFText_FindClose", null, ADDRESS) }

    // fpdf_doc.h
    val firstChild by lazy { function("FPDFBookmark_GetFirstChild", ADDRESS, ADDRESS, ADDRESS) }
    val nextSibling by lazy { function("FPDFBookmark_GetNextSibling", ADDRESS, ADDRESS, ADDRESS) }
    val bookmarkTitle by lazy { function("FPDFBookmark_GetTitle", JAVA_LONG, ADDRESS, ADDRESS, JAVA_LONG) }
    val bookmarkDest by lazy { function("FPDFBookmark_GetDest", ADDRESS, ADDRESS, ADDRESS) }
    val bookmarkAction by lazy { function("FPDFBookmark_GetAction", ADDRESS, ADDRESS) }
    val actionDest by lazy { function("FPDFAction_GetDest", ADDRESS, ADDRESS, ADDRESS) }
    val destPageIndex by lazy { function("FPDFDest_GetDestPageIndex", JAVA_INT, ADDRESS, ADDRESS) }

    const val BITMAP_BGRA = 4
    const val RENDER_ANNOTATIONS = 0x01
    const val ERROR_PASSWORD = 4L
}

/** A pointer PDFium handed back, which is null when it had nothing to give. */
private val MemorySegment.isNull: Boolean get() = address() == 0L

private fun MethodHandle.int(vararg arguments: Any): Int = invokeWithArguments(*arguments) as Int

private fun MethodHandle.pointer(vararg arguments: Any): MemorySegment =
    invokeWithArguments(*arguments) as MemorySegment

/** UTF-16, terminated, as PDFium takes its wide strings. */
private fun Arena.wideString(text: String): MemorySegment {
    val units = text.toCharArray()
    val segment = allocate(ValueLayout.JAVA_CHAR, (units.size + 1).toLong())
    units.forEachIndexed { index, unit -> segment.setAtIndex(ValueLayout.JAVA_CHAR, index.toLong(), unit) }
    return segment
}

private fun MemorySegment.wideString(units: Int): String =
    String(CharArray(units) { getAtIndex(ValueLayout.JAVA_CHAR, it.toLong()) })

internal class DesktopPdfiumDocument private constructor(private val handle: MemorySegment) : PdfiumDocument {

    override val pageCount: Int = PdfiumLibrary.call { this.pageCount.int(handle) }

    override fun openPage(index: Int): PdfiumPage? = PdfiumLibrary.call {
        loadPage.pointer(handle, index).takeUnless { it.isNull }?.let(::DesktopPdfiumPage)
    }

    override fun tableOfContents(): List<PdfiumBookmark> = PdfiumLibrary.call {
        bookmarksUnder(MemorySegment.NULL, HashSet())
    }

    /**
     * The bookmarks under [parent]. A malformed outline can point back at
     * itself, so every bookmark is visited once at most.
     */
    private fun PdfiumLibrary.bookmarksUnder(parent: MemorySegment, seen: HashSet<Long>): List<PdfiumBookmark> {
        val found = ArrayList<PdfiumBookmark>()
        var bookmark = firstChild.pointer(handle, parent)
        while (!bookmark.isNull && seen.add(bookmark.address())) {
            found += PdfiumBookmark(
                title = titleOf(bookmark),
                pageIndex = pageOf(bookmark),
                children = bookmarksUnder(bookmark, seen),
            )
            bookmark = nextSibling.pointer(handle, bookmark)
        }
        return found
    }

    private fun PdfiumLibrary.titleOf(bookmark: MemorySegment): String? {
        val bytes = bookmarkTitle.invokeWithArguments(bookmark, MemorySegment.NULL, 0L) as Long
        if (bytes <= 2L) return null
        return Arena.ofConfined().use { arena ->
            val buffer = arena.allocate(bytes)
            bookmarkTitle.invokeWithArguments(bookmark, buffer, bytes)
            buffer.wideString((bytes / 2 - 1).toInt())
        }
    }

    /** Where a bookmark goes, by its destination or by the action that carries one. */
    private fun PdfiumLibrary.pageOf(bookmark: MemorySegment): Int {
        var destination = bookmarkDest.pointer(handle, bookmark)
        if (destination.isNull) {
            val action = bookmarkAction.pointer(bookmark)
            if (!action.isNull) destination = actionDest.pointer(handle, action)
        }
        return if (destination.isNull) 0 else destPageIndex.int(handle, destination)
    }

    override fun close() = PdfiumLibrary.call { closeDocument.invokeWithArguments(handle); Unit }

    companion object {
        fun load(file: File): DesktopPdfiumDocument = PdfiumLibrary.call {
            val handle = Arena.ofConfined().use { arena ->
                loadDocument.pointer(arena.allocateFrom(file.path), MemorySegment.NULL)
            }
            if (handle.isNull) {
                val error = lastError.invokeWithArguments() as Long
                throw IOException(
                    if (error == PdfiumLibrary.ERROR_PASSWORD) {
                        "${file.name} is protected by a password"
                    } else {
                        "PDFium could not open ${file.name} (error $error)"
                    },
                )
            }
            DesktopPdfiumDocument(handle)
        }
    }
}

private class DesktopPdfiumPage(private val handle: MemorySegment) : PdfiumPage {

    override val widthPt: Double = PdfiumLibrary.call { (pageWidth.invokeWithArguments(handle) as Float).toDouble() }
    override val heightPt: Double = PdfiumLibrary.call { (pageHeight.invokeWithArguments(handle) as Float).toDouble() }

    override fun render(
        width: Int,
        height: Int,
        startX: Int,
        startY: Int,
        drawWidth: Int,
        drawHeight: Int,
    ): ImageBitmap = PdfiumLibrary.call {
        val bitmap = createBitmap.pointer(width, height, PdfiumLibrary.BITMAP_BGRA, MemorySegment.NULL, 0)
        if (bitmap.isNull) throw IOException("No memory for a $width × $height page")
        try {
            // PDFium composites only the page's own marks, so anything it does
            // not paint would stay transparent and read as black.
            fillRect.invokeWithArguments(bitmap, 0, 0, width, height, 0xFFFFFFFFL)
            renderPage.invokeWithArguments(
                bitmap, handle, startX, startY, drawWidth, drawHeight, 0, PdfiumLibrary.RENDER_ANNOTATIONS,
            )
            val stride = bitmapStride.int(bitmap)
            val pixels = bitmapBuffer.pointer(bitmap).reinterpret(stride.toLong() * height)
            val rowBytes = width * 4
            val bytes = ByteArray(rowBytes * height)
            for (row in 0 until height) {
                MemorySegment.copy(pixels, ValueLayout.JAVA_BYTE, row.toLong() * stride, bytes, row * rowBytes, rowBytes)
            }
            // BGRA is the order PDFium writes and the order Skia keeps.
            val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
            Bitmap().apply {
                installPixels(info, bytes, rowBytes)
                setImmutable()
            }.asComposeImageBitmap()
        } finally {
            destroyBitmap.invokeWithArguments(bitmap)
        }
    }

    override fun openTextPage(): PdfiumTextPage = PdfiumLibrary.call {
        val text = loadText.pointer(handle)
        if (text.isNull) throw IOException("This page's text could not be read")
        DesktopPdfiumTextPage(text)
    }

    override fun close() = PdfiumLibrary.call { closePage.invokeWithArguments(handle); Unit }
}

private class DesktopPdfiumTextPage(private val handle: MemorySegment) : PdfiumTextPage {

    override val charCount: Int = PdfiumLibrary.call { countChars.int(handle) }

    override fun charIndexAt(x: Double, y: Double, xTolerance: Double, yTolerance: Double): Int =
        PdfiumLibrary.call { charIndexAtPos.int(handle, x, y, xTolerance, yTolerance) }

    override fun charAt(index: Int): Char = PdfiumLibrary.call { unicode.int(handle, index).toChar() }

    override fun text(start: Int, count: Int): String? {
        if (count <= 0) return ""
        return PdfiumLibrary.call {
            Arena.ofConfined().use { arena ->
                val buffer = arena.allocate(ValueLayout.JAVA_CHAR, count + 1L)
                // The count written includes the terminator.
                val written = getText.int(handle, start, count, buffer)
                if (written <= 0) null else buffer.wideString(written - 1)
            }
        }
    }

    override fun charBox(index: Int): Rect? = PdfiumLibrary.call {
        Arena.ofConfined().use { arena ->
            val edges = arena.allocate(JAVA_DOUBLE, 4)
            val found = charBox.int(
                handle, index,
                edges.asSlice(0, 8), edges.asSlice(8, 8), edges.asSlice(16, 8), edges.asSlice(24, 8),
            )
            if (found == 0) return@use null
            // left, right, bottom, top: the order PDFium fills them in.
            val left = edges.getAtIndex(JAVA_DOUBLE, 0).toFloat()
            val right = edges.getAtIndex(JAVA_DOUBLE, 1).toFloat()
            val bottom = edges.getAtIndex(JAVA_DOUBLE, 2).toFloat()
            val top = edges.getAtIndex(JAVA_DOUBLE, 3).toFloat()
            Rect(left, top, right, bottom)
        }
    }

    override fun countRects(start: Int, count: Int): Int = PdfiumLibrary.call { countRects.int(handle, start, count) }

    override fun rect(index: Int): Rect? = PdfiumLibrary.call {
        Arena.ofConfined().use { arena ->
            val edges = arena.allocate(JAVA_DOUBLE, 4)
            val found = getRect.int(
                handle, index,
                edges.asSlice(0, 8), edges.asSlice(8, 8), edges.asSlice(16, 8), edges.asSlice(24, 8),
            )
            if (found == 0) return@use null
            // left, top, right, bottom.
            Rect(
                edges.getAtIndex(JAVA_DOUBLE, 0).toFloat(),
                edges.getAtIndex(JAVA_DOUBLE, 1).toFloat(),
                edges.getAtIndex(JAVA_DOUBLE, 2).toFloat(),
                edges.getAtIndex(JAVA_DOUBLE, 3).toFloat(),
            )
        }
    }

    override fun find(term: String, limit: Int): List<Pair<Int, Int>> = PdfiumLibrary.call {
        Arena.ofConfined().use { arena ->
            // No flags: case-insensitive, substring.
            val search = findStart.pointer(handle, arena.wideString(term), 0L, 0)
            if (search.isNull) return@use emptyList()
            try {
                buildList {
                    while (size < limit && findNext.int(search) != 0) {
                        val index = resultIndex.int(search)
                        val count = resultCount.int(search)
                        if (index >= 0 && count > 0) add(index to count)
                    }
                }
            } finally {
                findClose.invokeWithArguments(search)
            }
        }
    }

    override fun close() = PdfiumLibrary.call { closeText.invokeWithArguments(handle); Unit }
}
