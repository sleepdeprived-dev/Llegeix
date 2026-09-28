package com.david.llegeix.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.david.llegeix.resources.*
import com.david.llegeix.ui.bookmarks.BookmarkedCollectionScreen
import com.david.llegeix.ui.bookmarks.ReadLaterCollectionScreen
import com.david.llegeix.ui.dictionary.DictionaryScreen
import com.david.llegeix.ui.flashcards.CardEditorScreen
import com.david.llegeix.ui.flashcards.DeckScreen
import com.david.llegeix.ui.flashcards.FlashcardsScreen
import com.david.llegeix.ui.flashcards.StudyScreen
import com.david.llegeix.ui.flashcards.WeakWordsScreen
import com.david.llegeix.ui.bookmarks.LearnedWordsScreen
import com.david.llegeix.data.flashcards.StudyDirection
import com.david.llegeix.data.flashcards.StudyScope
import com.david.llegeix.ui.folders.FolderDetailScreen
import com.david.llegeix.ui.library.LibraryScreen
import com.david.llegeix.ui.practice.PracticeScreen
import com.david.llegeix.ui.reader.ReaderScreen
import com.david.llegeix.ui.recent.RecentScreen
import com.david.llegeix.ui.saved.SavedScreen
import com.david.llegeix.ui.settings.SettingsScreen
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private object Routes {
    const val LIBRARY = "library"
    const val DICTIONARY = "dictionary"
    const val RECENT = "recent"
    const val SAVED = "saved"
    const val SETTINGS = "settings"
    const val PRACTICE = "practice"
    const val FLASHCARDS = "flashcards"
    const val FLASHCARD_DECK = "flashcards/deck/{deckId}"

    /**
     * A card to write. `cardId` is -1 for a new card, since NavType.LongType
     * cannot express null — the same convention as the reader's page.
     */
    const val CARD_EDITOR = "flashcards/card?deckId={deckId}&cardId={cardId}"

    /**
     * A study session over a deck, a collection or everything.
     *
     * Two ids rather than one, because a route can only carry numbers and the
     * session is over one of three things: `deckId` and `collectionId` are each
     * -1 when they are not the answer, and [StudyScope] is what the two ends
     * actually deal in.
     *
     * It used to carry an `extra` flag as well, for a round that went through
     * cards that were not due without recording the answers. Every session goes
     * through every card now, so there is nothing for the flag to mean on the
     * way in; extra practice survives only as *Repeat these* at the end of a
     * round, which is a state the session screen puts itself into rather than
     * somewhere it is sent.
     */
    const val FLASHCARD_STUDY =
        "flashcards/study?deckId={deckId}&collectionId={collectionId}" +
            "&direction={direction}&weak={weak}"

    fun flashcardDeck(deckId: Long): String = "flashcards/deck/$deckId"

    /** The words learned, day by day. */
    const val LEARNED = "saved/learned"

    /** The weak words: every card last answered "not yet". */
    const val FLASHCARD_WEAK = "flashcards/weak"

    fun flashcardStudy(
        scope: StudyScope,
        direction: StudyDirection,
    ): String = "flashcards/study?deckId=${scope.deckArgument}" +
        "&collectionId=${scope.collectionArgument}" +
        "&direction=${direction.name}&weak=${scope == StudyScope.Weak}"

    fun cardEditor(deckId: Long, cardId: Long? = null): String =
        "flashcards/card?deckId=$deckId&cardId=${cardId ?: -1}"

    /**
     * The three derived collections, which are not real folder rows.
     *
     * Each is a live view of something the app already records — the bookmark
     * flag, the read-later flag, the reading history — rather than a folder,
     * because a document belongs to at most one folder and materialising any of
     * these as one would pull its PDFs out of the collection the reader filed
     * them in.
     */
    const val BOOKMARKED_COLLECTION = "collection/bookmarked"
    const val READ_LATER_COLLECTION = "collection/read-later"

    /**
     * Query parameters rather than path segments: a document URI contains
     * slashes and colons, and the navigation matcher decodes path segments in a
     * way that breaks on them. Query arguments survive [Uri.encode] intact.
     */
    const val READER = "reader?uri={uri}&title={title}&page={page}&find={find}"
    const val FOLDER_DETAIL = "folder/{folderId}?name={name}&adding={adding}"

    fun reader(
        uriString: String,
        title: String,
        page: Int? = null,
        find: String = "",
    ): String =
        "reader?uri=${Uri.encode(uriString)}&title=${Uri.encode(title)}" +
            "&page=${page ?: -1}&find=${Uri.encode(find)}"

    fun folderDetail(folderId: Long, name: String, adding: Boolean = false): String =
        "folder/$folderId?name=${Uri.encode(name)}&adding=$adding"
}

private data class TopLevelDestination(
    val route: String,
    val labelRes: StringResource,
    val iconRes: DrawableResource,
)

/**
 * The tabs, in the order a reader meets them.
 *
 * The dictionary sits second, next to the library rather than out at the end,
 * because it is the other reason to open this app at all: the library is the
 * books, and the dictionary is the language.
 *
 * There were five, then four, and there are three. Recent, Bookmarks and
 * Folders were three re-cuts of the same documents, so the bar spent most of
 * its width offering ways to list what the library was already listing; Recent
 * became the shelf of part-read books at the top of the library, and Bookmarks
 * and Folders became the one Saved tab, because they were two names for setting
 * something aside and between them listed the starred PDFs twice.
 *
 * Exams was the fourth and is gone in v4.1, feature and all. Nothing was
 * promoted into the space it left, and that was the point: the bar is not a
 * shelf with four slots to keep filled, it is the list of things this app is
 * for. Practice and the reading history are reached from the Saved tab, where
 * somebody looking for what they have put by is already standing.
 *
 * Flashcards is the fourth now, and it passes that test rather than filling
 * the slot. It is not another cut of the documents, nor of what was set aside
 * while reading: it is vocabulary written by hand, a place where things are
 * made, and one of the main reasons to open the app at all. It goes last
 * because the first three are still the order a reader meets them in. Four is
 * also the ceiling. Anything that wants a tab after this has to take one over
 * by merging, not add a fifth.
 */
private val topLevelDestinations = listOf(
    TopLevelDestination(Routes.LIBRARY, Res.string.nav_library, Res.drawable.ic_library),
    TopLevelDestination(Routes.DICTIONARY, Res.string.nav_dictionary, Res.drawable.ic_dictionary),
    TopLevelDestination(Routes.SAVED, Res.string.nav_saved, Res.drawable.ic_bookmark),
    TopLevelDestination(Routes.FLASHCARDS, Res.string.nav_flashcards, Res.drawable.ic_flashcards),
)

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // The bar belongs to the top-level tabs only; the reader, a folder's
    // contents and Settings are pushed on top of them and get the whole screen.
    val showBottomBar = topLevelDestinations.any { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp,
                ) {
                    topLevelDestinations.forEach { destination ->
                        val selected = currentDestination?.hierarchy
                            ?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    // Switching tabs should not pile up a back
                                    // stack of previously visited tabs.
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    painter = painterResource(destination.iconRes),
                                    contentDescription = null,
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(destination.labelRes),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.LIBRARY,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onOpenDocument = { document ->
                        navController.navigate(Routes.reader(document.uriString, document.displayName))
                    },
                    onOpenReading = { uriString, title ->
                        navController.navigate(Routes.reader(uriString, title))
                    },
                    onFindInDocument = { uriString, title, query ->
                        navController.navigate(
                            Routes.reader(uriString, title, find = query.ifBlank { " " }),
                        )
                    },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenHistory = { navController.navigate(Routes.RECENT) },
                )
            }

            composable(Routes.DICTIONARY) {
                DictionaryScreen()
            }

            // Pushed rather than a tab: the shelf at the top of the library
            // answers "carry on with what I was reading", and this answers the
            // rarer "what was that thing I opened last week". It is reached
            // from the Saved tab, as one of the three collections the app keeps
            // for you, and from the shelf's own button.
            composable(Routes.RECENT) {
                RecentScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDocument = { uriString, title ->
                        navController.navigate(Routes.reader(uriString, title))
                    },
                )
            }

            composable(Routes.SAVED) {
                SavedScreen(
                    onOpenDocument = { uriString, title, page ->
                        navController.navigate(Routes.reader(uriString, title, page))
                    },
                    onOpenFolder = { folderId, name ->
                        navController.navigate(Routes.folderDetail(folderId, name))
                    },
                    onFillNewCollection = { folderId, name ->
                        navController.navigate(
                            Routes.folderDetail(folderId, name, adding = true),
                        )
                    },
                    onOpenBookmarked = {
                        navController.navigate(Routes.BOOKMARKED_COLLECTION)
                    },
                    onOpenReadLater = {
                        navController.navigate(Routes.READ_LATER_COLLECTION)
                    },
                    onOpenRecent = { navController.navigate(Routes.RECENT) },
                    onPractise = { navController.navigate(Routes.PRACTICE) },
                    onOpenLearned = { navController.navigate(Routes.LEARNED) },
                )
            }

            composable(Routes.LEARNED) {
                LearnedWordsScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.FLASHCARDS) {
                FlashcardsScreen(
                    onOpenDeck = { deckId -> navController.navigate(Routes.flashcardDeck(deckId)) },
                    onOpenWeak = { navController.navigate(Routes.FLASHCARD_WEAK) },
                    onStudy = { scope, direction ->
                        navController.navigate(Routes.flashcardStudy(scope, direction))
                    },
                )
            }

            composable(
                route = Routes.FLASHCARD_STUDY,
                arguments = listOf(
                    navArgument("deckId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("collectionId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("direction") {
                        type = NavType.StringType
                        defaultValue = StudyDirection.Default.name
                    },
                    navArgument("weak") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { entry ->
                StudyScreen(
                    scope = StudyScope.fromRoute(
                        deckId = entry.arguments?.getLong("deckId") ?: StudyScope.NONE,
                        collectionId = entry.arguments?.getLong("collectionId") ?: StudyScope.NONE,
                        weak = entry.arguments?.getBoolean("weak") ?: false,
                    ),
                    direction = StudyDirection.fromName(entry.arguments?.getString("direction")),
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.FLASHCARD_WEAK) {
                WeakWordsScreen(
                    onBack = { navController.popBackStack() },
                    onStudy = { scope, direction ->
                        navController.navigate(Routes.flashcardStudy(scope, direction))
                    },
                )
            }

            composable(
                route = Routes.FLASHCARD_DECK,
                arguments = listOf(navArgument("deckId") { type = NavType.LongType }),
            ) { entry ->
                val deckId = entry.arguments?.getLong("deckId") ?: 0L
                DeckScreen(
                    deckId = deckId,
                    onBack = { navController.popBackStack() },
                    onAddCard = { navController.navigate(Routes.cardEditor(deckId)) },
                    onEditCard = { cardId ->
                        navController.navigate(Routes.cardEditor(deckId, cardId))
                    },
                )
            }

            composable(
                route = Routes.CARD_EDITOR,
                arguments = listOf(
                    navArgument("deckId") { type = NavType.LongType },
                    navArgument("cardId") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) { entry ->
                CardEditorScreen(
                    deckId = entry.arguments?.getLong("deckId") ?: 0L,
                    cardId = entry.arguments?.getLong("cardId")?.takeIf { it >= 0 },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.PRACTICE) {
                PracticeScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.BOOKMARKED_COLLECTION) {
                BookmarkedCollectionScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDocument = { uriString, title ->
                        navController.navigate(Routes.reader(uriString, title))
                    },
                )
            }

            composable(Routes.READ_LATER_COLLECTION) {
                ReadLaterCollectionScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDocument = { uriString, title ->
                        navController.navigate(Routes.reader(uriString, title))
                    },
                )
            }

            composable(
                route = Routes.FOLDER_DETAIL,
                arguments = listOf(
                    navArgument("folderId") { type = NavType.LongType },
                    navArgument("name") { type = NavType.StringType; defaultValue = "" },
                    navArgument("adding") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { entry ->
                FolderDetailScreen(
                    folderId = entry.arguments?.getLong("folderId") ?: 0L,
                    folderName = entry.arguments?.getString("name").orEmpty(),
                    startAdding = entry.arguments?.getBoolean("adding") == true,
                    onBack = { navController.popBackStack() },
                    onOpenDocument = { uriString, title ->
                        navController.navigate(Routes.reader(uriString, title))
                    },
                )
            }

            composable(
                route = Routes.READER,
                arguments = listOf(
                    navArgument("uri") { type = NavType.StringType; defaultValue = "" },
                    navArgument("title") { type = NavType.StringType; defaultValue = "" },
                    // -1 stands for "no target page", since NavType.IntType
                    // cannot express null.
                    navArgument("page") { type = NavType.IntType; defaultValue = -1 },
                    // A single space stands for "open find with nothing in it",
                    // since an empty string cannot be told apart from the
                    // argument simply not being there.
                    navArgument("find") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                ReaderScreen(
                    uriString = entry.arguments?.getString("uri").orEmpty(),
                    title = entry.arguments?.getString("title").orEmpty(),
                    targetPage = entry.arguments?.getInt("page")?.takeIf { it >= 0 },
                    findQuery = entry.arguments?.getString("find")?.takeIf { it.isNotEmpty() },
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
