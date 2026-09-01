package com.david.catalanpdfreader.data.model

import android.net.Uri
import androidx.core.net.toUri
import com.david.catalanpdfreader.util.pdfTitle

/**
 * Where a document was discovered. The same physical file can be found by both
 * paths, so [PdfDocument.dedupeKey] is what actually collapses duplicates.
 */
enum class PdfOrigin {
    /** Found under a folder tree the user granted via the SAF picker. */
    GRANTED_FOLDER,

    /** Found by the whole-device MediaStore sweep (needs All Files Access). */
    DEVICE_SCAN,

    /**
     * Chosen file-by-file from the document picker. This is the only path that
     * reaches a cloud provider such as Proton Drive, whose files have no local
     * path to walk and are fetched on demand.
     */
    PICKED_FILE,
}

/**
 * A PDF that exists somewhere on the device.
 *
 * This is the app's own domain type, deliberately independent of how it was
 * discovered, so the library UI never has to care which source produced it.
 *
 * The handle is held as a [String] rather than a [Uri] on purpose: it keeps the
 * model plain Kotlin so the merge and sort logic is unit-testable without an
 * emulator, and it is the form feature 3 will persist, since Room cannot store
 * a [Uri] without a type converter anyway.
 */
data class PdfDocument(
    /**
     * Stable, openable handle to the file. For [PdfOrigin.GRANTED_FOLDER] this
     * is a SAF document URI whose read permission survives reboot; for
     * [PdfOrigin.DEVICE_SCAN] it is a MediaStore content URI, readable only
     * while All Files Access is held.
     */
    val uriString: String,
    val displayName: String,
    val sizeBytes: Long,
    /** Epoch millis. 0 when the provider did not report one. */
    val lastModified: Long,
    val origin: PdfOrigin,
    /** Human-readable location, e.g. "Documents/Catalan". Null when unknown. */
    val parentLabel: String?,
) {
    val uri: Uri get() = uriString.toUri()

    /** Title without the ".pdf" suffix, for display. */
    val title: String get() = pdfTitle(displayName)

    /**
     * Identity used to merge results from the two sources. Name plus byte size
     * is a heuristic, but a much better one than the URI: the two providers
     * hand back completely different URIs for the same underlying file, so
     * comparing URIs would show every device-scanned file twice.
     */
    val dedupeKey: String
        get() = "${displayName.lowercase()}:$sizeBytes"
}
