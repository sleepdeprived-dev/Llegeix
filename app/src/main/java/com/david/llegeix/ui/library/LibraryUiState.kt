package com.david.llegeix.ui.library

import androidx.annotation.StringRes
import com.david.llegeix.R
import com.david.llegeix.data.model.LibrarySort
import com.david.llegeix.data.model.PdfDocument
import com.david.llegeix.data.source.GrantedFolder
import com.david.llegeix.data.source.LibraryFolder
import com.david.llegeix.ui.common.UiText

/**
 * How the library is arranged on screen.
 *
 * [FOLDERS] is the default and the reason the other two are still here. A
 * shelf of every PDF on the phone at once is a list nobody has a place to
 * start in; walking the folders they are already filed in means each screen
 * asks one question — what is in here — and the reader's own arrangement does
 * the sorting. But "show me everything, most recent first" is a real question
 * too, and read-later is a shelf rather than a place, so both keep a chip.
 */
enum class LibraryView(@param:StringRes val labelRes: Int) {
    FOLDERS(R.string.library_view_by_folder),
    ALL(R.string.library_filter_all),
    READ_LATER(R.string.library_filter_read_later),
}

data class LibraryUiState(
    val isScanning: Boolean = false,
    /** Already filtered by [query] and [view], and ordered by [sort]. */
    val documents: List<PdfDocument> = emptyList(),
    /** The folders at [path], shown above the documents while browsing. */
    val folders: List<LibraryFolder> = emptyList(),
    /** Which folder is open, or null for the top of the library. */
    val path: String? = null,
    /** Total found before filtering, so the UI can say "3 of 41". */
    val totalFound: Int = 0,
    val grantedFolders: List<GrantedFolder> = emptyList(),
    val deviceScanEnabled: Boolean = false,
    val query: String = "",
    val sort: LibrarySort = LibrarySort.RECENT,
    val view: LibraryView = LibraryView.FOLDERS,
    /** URIs of documents flagged to read later, for the row toggles. */
    val readLaterUris: Set<String> = emptySet(),
    /** URIs of documents bookmarked as a whole. */
    val bookmarkedUris: Set<String> = emptySet(),
    /** False until the first scan finishes, to tell "empty" from "not looked yet". */
    val hasScanned: Boolean = false,
    val errorMessage: UiText? = null,
) {
    /** True when at least one discovery path is set up. */
    val hasAnySource: Boolean
        get() = grantedFolders.isNotEmpty() || deviceScanEnabled

    /**
     * Walking folders rather than showing a flat list.
     *
     * A search suspends it: someone typing a name wants the answer from the
     * whole library, not from whichever folder they happened to be standing in
     * when the thought arrived.
     */
    val isBrowsing: Boolean
        get() = view == LibraryView.FOLDERS && query.isBlank()

    /** Sources exist, the scan ran, and it genuinely found nothing. */
    val isEmptyAfterScan: Boolean
        get() = hasAnySource && hasScanned && totalFound == 0 && !isScanning

    /** Documents exist but the search or the view excluded all of them. */
    val isFilteredToNothing: Boolean
        get() = totalFound > 0 && documents.isEmpty() && folders.isEmpty()

    fun isReadLater(document: PdfDocument): Boolean = document.uriString in readLaterUris

    fun isBookmarked(document: PdfDocument): Boolean = document.uriString in bookmarkedUris
}
