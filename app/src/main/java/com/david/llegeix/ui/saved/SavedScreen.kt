package com.david.llegeix.ui.saved

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.llegeix.resources.*
import com.david.llegeix.ui.bookmarks.BookmarksViewModel
import com.david.llegeix.ui.bookmarks.PagesPane
import com.david.llegeix.ui.bookmarks.WordsPane
import com.david.llegeix.ui.common.AppSnackbarHost
import com.david.llegeix.ui.common.Pill
import com.david.llegeix.ui.common.PillGroup
import com.david.llegeix.ui.common.ScreenTitle
import com.david.llegeix.ui.common.Space
import com.david.llegeix.ui.folders.CollectionsPane
import com.david.llegeix.ui.folders.FoldersViewModel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.launch

/**
 * Everything the reader has set aside, under one tab.
 *
 * This is two tabs joined. Collections and Saved were next to each other in the
 * bar and were answering the same question — *where did I put that* — in two
 * places, and between them they were drawing the starred PDFs twice: once as
 * the automatic "Bookmarked" collection and once as a whole tab of its own,
 * from the same query on the same ViewModel. Joining them let one of those two
 * lists go, and the bar stopped being a menu of near-synonyms.
 *
 * Three panes, in the order things are set aside in: the shelves you built, the
 * pages you marked, the words you kept. The tab row carries counts because the
 * count is usually the reason to go to a tab at all.
 */
private enum class SavedTab(val labelRes: StringResource) {
    COLLECTIONS(Res.string.nav_collections),
    PAGES(Res.string.bookmarks_tab_pages),
    WORDS(Res.string.bookmarks_tab_words),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(
    onOpenDocument: (uriString: String, title: String, page: Int?) -> Unit,
    onOpenFolder: (folderId: Long, name: String) -> Unit,
    onFillNewCollection: (folderId: Long, name: String) -> Unit,
    onOpenBookmarked: () -> Unit,
    onOpenReadLater: () -> Unit,
    onOpenRecent: () -> Unit,
    onPractise: () -> Unit,
    onOpenLearned: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Built here rather than inside the panes so that the three of them share
    // one instance each: this composable and its panes share a
    // ViewModelStoreOwner, so `viewModel()` hands back what is already there
    // rather than building a second copy per pane.
    val foldersViewModel: FoldersViewModel = viewModel(factory = FoldersViewModel.Factory)
    val bookmarksViewModel: BookmarksViewModel = viewModel(factory = BookmarksViewModel.Factory)

    // The pager owns the position; the tab row follows it, so a swipe and a tap
    // cannot disagree about which tab is showing.
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { SavedTab.entries.size })
    val scope = rememberCoroutineScope()
    val selectedTab = pagerState.currentPage
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        // The app shell's Scaffold has already inset this screen for the
        // status bar and the navigation bar; counting them a second time
        // put a dead band above the bottom bar and made every top bar
        // 24dp taller than it asks to be.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize(),
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                expandedHeight = Space.topBar,
                title = {
                    ScreenTitle(
                        icon = Res.drawable.ic_bookmark,
                        title = stringResource(Res.string.nav_saved),
                    )
                },
            )
        },
        floatingActionButton = {
            // Only over the collections. A button that makes a new collection
            // has nothing to do with a list of marked pages, and one that
            // stayed put while the pane behind it changed would read as
            // applying to whatever is showing.
            AnimatedVisibility(
                visible = selectedTab == SavedTab.COLLECTIONS.ordinal,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                FloatingActionButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.collections_new))
                }
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            // Pills on a groove rather than Material's underlined tab row, and
            // the name and nothing else on each one.
            //
            // They carried a count: first written into the label as "Pages
            // (2)", then as a small badge beside it. Both were wrong for the
            // same reason, which only showed up in Catalan — *Col·leccions* is
            // twelve letters, a third of the width available to it, and a
            // number sharing that pill left "Col·lecc…". A tab whose name
            // cannot be read is not a tab. The count was the lesser fact: how
            // many pages are saved is answered by the pane itself the moment
            // it opens, and by its empty state when there are none.
            PillGroup(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.screen)
                    .padding(top = Space.sm, bottom = Space.xs),
            ) {
                SavedTab.entries.forEachIndexed { index, tab ->
                    val name = stringResource(tab.labelRes)
                    Pill(
                        selected = selectedTab == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        label = name,
                        // Each pill takes an equal share, so the three of them
                        // fill the width between them.
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = Space.xs, vertical = Space.sm),
                    ) {
                        // A step down from labelLarge, which is what buys the
                        // longest of the three names the room to be read: at
                        // 14sp "Col·leccions" is most of a third of a narrow
                        // phone and arrives as "Col·lecc…". There is no
                        // ellipsis here on purpose — nothing should be able to
                        // cut one of these three names again without the pill
                        // visibly overflowing in a preview.
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                // Panes start at the top. The default is CenterVertically,
                // which quietly pushed the words pane's search field a third of
                // the way down the screen because its content is shorter than
                // the page.
                verticalAlignment = Alignment.Top,
            ) { page ->
                when (SavedTab.entries[page]) {
                    SavedTab.COLLECTIONS -> CollectionsPane(
                        onOpenFolder = onOpenFolder,
                        onOpenBookmarked = onOpenBookmarked,
                        onOpenReadLater = onOpenReadLater,
                        onOpenRecent = onOpenRecent,
                        onFillNewCollection = onFillNewCollection,
                        snackbarHostState = snackbarHostState,
                        showCreateDialog = showCreateDialog,
                        onCreateDialogDismissed = { showCreateDialog = false },
                        viewModel = foldersViewModel,
                    )

                    SavedTab.PAGES -> PagesPane(
                        onOpenDocument = onOpenDocument,
                        viewModel = bookmarksViewModel,
                    )

                    SavedTab.WORDS -> WordsPane(
                        onOpenDocument = onOpenDocument,
                        onPractise = onPractise,
                        onOpenLearned = onOpenLearned,
                        viewModel = bookmarksViewModel,
                    )
                }
            }
        }
    }
}
