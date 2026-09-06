package com.david.llegeix.ui.saved

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.llegeix.R
import com.david.llegeix.ui.bookmarks.BookmarksViewModel
import com.david.llegeix.ui.bookmarks.PagesPane
import com.david.llegeix.ui.bookmarks.WordsPane
import com.david.llegeix.ui.common.Space
import com.david.llegeix.ui.folders.CollectionsPane
import com.david.llegeix.ui.folders.FoldersViewModel
import kotlinx.coroutines.launch

/**
 * Everything the reader has set aside, under one tab.
 *
 * This is two tabs joined. Collections and Saved were next to each other in the
 * bar and were answering the same question — *where did I put that* — in two
 * places, and between them they were drawing the starred PDFs twice: once as
 * the automatic "Bookmarked" collection and once as a whole tab of its own,
 * from the same query on the same ViewModel. Joining them freed the slot Exams
 * needed and let one of those two lists go, which is the better half of the
 * trade: the bar is no longer a menu of near-synonyms.
 *
 * Three panes, in the order things are set aside in: the shelves you built, the
 * pages you marked, the words you kept. The tab row carries counts because the
 * count is usually the reason to go to a tab at all.
 */
private enum class SavedTab(@param:StringRes val labelRes: Int) {
    COLLECTIONS(R.string.nav_collections),
    PAGES(R.string.bookmarks_tab_pages),
    WORDS(R.string.bookmarks_tab_words),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(
    onOpenDocument: (uriString: String, title: String, page: Int?) -> Unit,
    onOpenFolder: (folderId: Long, name: String) -> Unit,
    onFillNewCollection: (folderId: Long, name: String) -> Unit,
    onOpenBookmarked: () -> Unit,
    onPractise: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Both ViewModels are read here as well as inside their panes so the tab
    // row can carry counts. They are the same instances the panes get: this
    // composable and its panes share one ViewModelStoreOwner, so `viewModel()`
    // hands back what is already there rather than building a second copy.
    val foldersViewModel: FoldersViewModel = viewModel(factory = FoldersViewModel.Factory)
    val bookmarksViewModel: BookmarksViewModel = viewModel(factory = BookmarksViewModel.Factory)
    val folders by foldersViewModel.folders.collectAsStateWithLifecycle()
    val pages by bookmarksViewModel.pageBookmarks.collectAsStateWithLifecycle()
    val savedWords by bookmarksViewModel.savedWords.collectAsStateWithLifecycle()

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                expandedHeight = Space.topBar,
                title = { Text(stringResource(R.string.nav_saved)) },
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
                ExtendedFloatingActionButton(
                    onClick = { showCreateDialog = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.collections_new)) },
                )
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                SavedTab.entries.forEachIndexed { index, tab ->
                    val count = when (tab) {
                        SavedTab.COLLECTIONS -> folders.size
                        SavedTab.PAGES -> pages.size
                        SavedTab.WORDS -> savedWords.size
                    }
                    Tab(
                        selected = selectedTab == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = {
                            val name = stringResource(tab.labelRes)
                            Text(
                                text = if (count > 0) {
                                    stringResource(R.string.bookmarks_tab_with_count, name, count)
                                } else {
                                    name
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
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
                        viewModel = bookmarksViewModel,
                    )
                }
            }
        }
    }
}
