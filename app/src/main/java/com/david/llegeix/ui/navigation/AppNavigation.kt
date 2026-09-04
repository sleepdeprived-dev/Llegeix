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
import androidx.annotation.DrawableRes
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import com.david.llegeix.R
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.david.llegeix.ui.bookmarks.BookmarkedCollectionScreen
import com.david.llegeix.ui.bookmarks.BookmarksScreen
import com.david.llegeix.ui.dictionary.DictionaryScreen
import com.david.llegeix.ui.folders.FolderDetailScreen
import com.david.llegeix.ui.folders.FoldersScreen
import com.david.llegeix.ui.library.LibraryScreen
import com.david.llegeix.ui.practice.PracticeScreen
import com.david.llegeix.ui.reader.ReaderScreen
import com.david.llegeix.ui.recent.RecentScreen
import com.david.llegeix.ui.settings.SettingsScreen

private object Routes {
    const val LIBRARY = "library"
    const val DICTIONARY = "dictionary"
    const val RECENT = "recent"
    const val BOOKMARKS = "bookmarks"
    const val FOLDERS = "folders"
    const val SETTINGS = "settings"
    const val PRACTICE = "practice"

    /** The derived "Bookmarked" collection, which is not a real folder row. */
    const val BOOKMARKED_COLLECTION = "collection/bookmarked"

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
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
)

/**
 * The tabs, in the order a reader meets them.
 *
 * The dictionary sits second, next to the library rather than out at the end,
 * because it is the other reason to open this app at all: the library is the
 * books, and the dictionary is the language.
 *
 * There were five. Three of them — Recent, Bookmarks, Folders — were three
 * re-cuts of the same documents, so the bar spent most of its width offering
 * ways to list things the library was already listing. Recent has become the
 * shelf of part-read books at the top of the library, which is where somebody
 * wanting to carry on reading was going to look anyway, and its full history is
 * one press of that shelf's own button away.
 */
private val topLevelDestinations = listOf(
    TopLevelDestination(Routes.LIBRARY, R.string.nav_library, R.drawable.ic_library),
    TopLevelDestination(Routes.DICTIONARY, R.string.nav_dictionary, R.drawable.ic_dictionary),
    TopLevelDestination(Routes.FOLDERS, R.string.nav_collections, R.drawable.ic_collection),
    TopLevelDestination(Routes.BOOKMARKS, R.string.nav_saved, R.drawable.ic_bookmark),
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
            // rarer "what was that thing I opened last week".
            composable(Routes.RECENT) {
                RecentScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDocument = { uriString, title ->
                        navController.navigate(Routes.reader(uriString, title))
                    },
                )
            }

            composable(Routes.BOOKMARKS) {
                BookmarksScreen(
                    onOpenDocument = { uriString, title, page ->
                        navController.navigate(Routes.reader(uriString, title, page))
                    },
                    onPractise = { navController.navigate(Routes.PRACTICE) },
                )
            }

            composable(Routes.FOLDERS) {
                FoldersScreen(
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
