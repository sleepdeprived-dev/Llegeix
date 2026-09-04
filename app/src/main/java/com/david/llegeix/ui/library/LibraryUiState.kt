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

/**
 * One place the library's documents come from, as the strip draws it.
 *
 * Counted twice on purpose. "48 PDFs" is what a source holds; "31 of 48" is
 * what it is currently contributing, and the gap between the two is the only
 * on-screen answer to "why is that book not in my library" — a question that
 * otherwise ends with the reader concluding the app cannot see the file at all.
 */
data class LibrarySource(
    /** Top-level display path, which doubles as the identity of the source. */
    val label: String,
    val kind: Kind,
    val visibleCount: Int,
    val totalCount: Int,
) {
    enum class Kind {
        /** A folder the reader granted through the system picker. */
        GRANTED,

        /** The whole-device sweep, which is a permission rather than a place. */
        DEVICE,
    }

    /** Nothing from here is reaching the library, though it holds something. */
    val isSilenced: Boolean get() = totalCount > 0 && visibleCount == 0

    /** Some of it is in and some is out, which is worth saying without opening. */
    val isPartial: Boolean get() = visibleCount in 1 until totalCount
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
    /** Where the documents come from, for the strip at the top of the library. */
    val sources: List<LibrarySource> = emptyList(),
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

    /**
     * Whether the strip of sources is worth drawing at all.
     *
     * It is a permanent fixture of the library's root and nowhere else, so it
     * has to earn its height: with nothing set up the welcome state is already
     * saying more than a row of tiles could, and inside a folder the question
     * on screen is what is in this folder rather than where any of it came
     * from.
     */
    val showsSources: Boolean
        get() = sources.isNotEmpty() && path == null && query.isBlank()

    fun isReadLater(document: PdfDocument): Boolean = document.uriString in readLaterUris

    fun isBookmarked(document: PdfDocument): Boolean = document.uriString in bookmarkedUris
}
