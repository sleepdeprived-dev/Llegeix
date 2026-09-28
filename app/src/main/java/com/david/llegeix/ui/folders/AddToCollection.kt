package com.david.llegeix.ui.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.david.llegeix.LlegeixApp
import com.david.llegeix.data.model.PdfDocument
import com.david.llegeix.data.source.LibraryDataRepository
import com.david.llegeix.data.source.PdfRepository
import com.david.llegeix.resources.*
import com.david.llegeix.ui.common.AppBottomSheet
import com.david.llegeix.ui.common.CoverAspectRatio
import com.david.llegeix.ui.common.EmptyState
import com.david.llegeix.ui.common.PdfCover
import com.david.llegeix.ui.common.SearchField
import com.david.llegeix.ui.common.Space
import com.david.llegeix.util.runCatchingCancellable
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One PDF as the "add to collection" list draws it.
 *
 * [inThisCollection] and [otherCollection] are separate because they are
 * separate facts, and the second is the one that stops a mistake: a document
 * belongs to at most one collection, so ticking one that is already filed
 * elsewhere moves it. Saying where it is now, on the row, before it is ticked,
 * is the difference between a decision and a surprise.
 */
data class AddCandidate(
    val document: PdfDocument,
    /** What the reader calls it, which is what the row shows and the search matches. */
    val title: String,
    val inThisCollection: Boolean,
    val otherCollection: String?,
)

/**
 * Everything the reader could put in a collection, and what is in it already.
 *
 * It scans storage rather than listing the documents the database happens to
 * know about. A PDF only gets a database row once something has been remembered
 * about it, so a list built from the database would offer the reader exactly
 * the documents they had already organised — and none of the ones they were
 * looking for.
 */
class AddToCollectionViewModel(
    private val repository: PdfRepository,
    private val libraryData: LibraryDataRepository,
    private val collectionId: Long,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _isScanning = MutableStateFlow(true)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val everything = MutableStateFlow<List<PdfDocument>>(emptyList())

    /**
     * Which documents were already in the collection when the sheet opened.
     *
     * The order of the list is taken from this and not from what is in the
     * collection *now*, which is the difference between a list you can work
     * down and one that rearranges itself under your finger. Ticking a book
     * used to make it true that it was in the collection, which made it sort to
     * the top, which took it out from under the finger that had just tapped it
     * — so a mistap could not be undone because the row was no longer there.
     * Now a tick only ticks. The order settles when this ViewModel is built
     * again, which is the next visit to the collection — never while somebody
     * is aiming at a row.
     */
    private var orderedBy: Set<String>? = null

    /**
     * The list, filtered by the query and annotated with where each document
     * currently sits.
     *
     * Sorted with the ones that were already in this collection first, so
     * opening the sheet on a collection you have been building shows you what
     * you built rather than making you hunt for it among two hundred others.
     */
    val candidates: StateFlow<List<AddCandidate>> = combine(
        everything,
        _query,
        libraryData.observeFolderAssignments(),
        libraryData.observeFolders(),
        libraryData.observeDocumentNames(),
    ) { documents, query, assignments, collections, names ->
        val namesById = collections.associate { it.id to it.name }
        val needle = query.trim()
        // Taken on the first answer from the database and kept from then on:
        // see [orderedBy].
        val order = orderedBy ?: assignments
            .filterValues { it == collectionId }
            .keys
            .toSet()
            .also { orderedBy = it }
        documents
            .map { document ->
                val filedIn = assignments[document.uriString]
                AddCandidate(
                    document = document,
                    // Resolved once, here, so the row draws it and the search
                    // matches it without either having to know how a name is
                    // looked up.
                    title = names.titleFor(document.uriString, document.displayName),
                    inThisCollection = filedIn == collectionId,
                    otherCollection = filedIn
                        ?.takeIf { it != collectionId }
                        ?.let { namesById[it] },
                )
            }
            .filter { candidate ->
                needle.isEmpty() ||
                    candidate.title.contains(needle, ignoreCase = true) ||
                    candidate.document.displayName.contains(needle, ignoreCase = true) ||
                    candidate.document.parentLabel?.contains(needle, ignoreCase = true) == true
            }
            .sortedWith(
                compareByDescending<AddCandidate> { it.document.uriString in order }
                    .thenBy { it.title.lowercase() },
            )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** How many are in the collection right now, for the sheet's subtitle. */
    val chosenCount: StateFlow<Int> = libraryData.observeDocumentsInFolder(collectionId)
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        viewModelScope.launch {
            runCatchingCancellable { repository.loadLibrary() }
                .onSuccess { everything.value = it.documents }
            _isScanning.value = false
        }
    }

    fun onQueryChange(query: String) {
        _query.update { query }
    }

    /**
     * Put a document in this collection, or take it out.
     *
     * The row is ensured first: a PDF found by scanning storage has no database
     * row until something is remembered about it, and being filed is exactly
     * that something.
     */
    fun onToggle(candidate: AddCandidate) = viewModelScope.launch {
        libraryData.ensureDocument(
            candidate.document.uriString,
            candidate.document.displayName,
        )
        libraryData.moveToFolder(
            candidate.document.uriString,
            if (candidate.inThisCollection) null else collectionId,
        )
    }

    companion object {
        fun factory(collectionId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as LlegeixApp
                AddToCollectionViewModel(
                    app.pdfRepository,
                    app.libraryDataRepository,
                    collectionId,
                )
            }
        }
    }
}

/**
 * Filling a collection, as one list of tick boxes.
 *
 * Before this, the only way a PDF got into a collection was one at a time, from
 * the library, through a row's overflow menu and then a dialog — four taps a
 * book, and the collection you were filling was never on screen while you did
 * it. Making a collection and then putting things in it is one job, so this is
 * one screen: everything you have, what is already in, and a tick.
 *
 * Ticks apply immediately rather than on a Save. There is nothing to lose by
 * being wrong — untick and it is out again — and a sheet that hoards a dozen
 * decisions until a button is pressed is a sheet that can drop them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToCollectionSheet(
    collectionId: Long,
    collectionName: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddToCollectionViewModel = viewModel(
        key = "add-to-$collectionId",
        factory = AddToCollectionViewModel.factory(collectionId),
    ),
) {
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val chosenCount by viewModel.chosenCount.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    AppBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // The list is long and the field has to stay put above it, so
                // the sheet takes a fixed share of the screen rather than
                // growing and shrinking as the search narrows it.
                .fillMaxHeight(0.9f)
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(Res.string.collections_add_title, collectionName),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = Space.screen),
            )
            Text(
                text = if (chosenCount == 0) {
                    stringResource(Res.string.collections_add_none_yet)
                } else {
                    pluralStringResource(
                        Res.plurals.collections_add_chosen,
                        chosenCount,
                        chosenCount,
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = Space.screen)
                    .padding(top = Space.xs),
            )

            SearchField(
                query = query,
                placeholder = stringResource(Res.string.collections_add_search),
                onQueryChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.screen)
                    .padding(top = Space.lg, bottom = Space.sm),
            )

            when {
                isScanning && candidates.isEmpty() -> Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                candidates.isEmpty() -> EmptyState(
                    title = stringResource(Res.string.library_no_matches_title),
                    body = stringResource(Res.string.collections_add_no_matches),
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = Space.xxl),
                ) {
                    items(candidates, key = { it.document.uriString }) { candidate ->
                        CandidateRow(
                            candidate = candidate,
                            onToggle = { viewModel.onToggle(candidate) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * One document, offered to a collection.
 *
 * The whole row is the tick. The question it asks is "does this belong in
 * here", and answering it should not mean hitting an 18dp box at the far edge
 * of the screen — especially not thirty times in a row, which is what filling a
 * collection actually looks like.
 */
@Composable
private fun CandidateRow(
    candidate: AddCandidate,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = Space.screen, vertical = Space.sm),
    ) {
        PdfCover(
            uriString = candidate.document.uriString,
            width = AddCoverWidth,
            cornerRadius = 6.dp,
            modifier = Modifier
                .width(AddCoverWidth)
                .aspectRatio(CoverAspectRatio),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = candidate.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // Said before the tick is pressed rather than after: a document
            // lives in one collection at a time, so this row is offering to
            // move it out of somewhere.
            val detail = candidate.otherCollection?.let {
                stringResource(Res.string.collections_add_in_other, it)
            } ?: candidate.document.parentLabel?.takeIf { it.isNotBlank() }
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (candidate.otherCollection != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Checkbox(checked = candidate.inThisCollection, onCheckedChange = null)
    }
}

/**
 * A small cover, because this list is a decision about identity rather than
 * about reading: enough of the cover to recognise the book by, no more.
 */
private val AddCoverWidth = 34.dp
