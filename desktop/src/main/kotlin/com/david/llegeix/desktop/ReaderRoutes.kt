package com.david.llegeix.desktop

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.savedstate.read
import com.david.llegeix.ui.reader.ReaderScreen
import com.david.llegeix.ui.recent.RecentScreen
import java.net.URLEncoder

/*
 * The destinations more than one section opens, as the phone's AppNavigation
 * has them: a document in the reader, and the reading history. Each section's
 * host adds them to its own graph, so going back from a book returns to the
 * section it was opened from.
 */

private const val READER = "reader?uri={uri}&title={title}&page={page}&find={find}"
private const val RECENT = "recent"

/** Open a document, on [page] if given, with find already holding [find] if not empty. */
internal fun NavHostController.openReader(uriString: String, title: String, page: Int? = null, find: String = "") =
    navigate("reader?uri=${encoded(uriString)}&title=${encoded(title)}&page=${page ?: -1}&find=${encoded(find)}")

internal fun NavHostController.openRecent() = navigate(RECENT)

internal fun NavGraphBuilder.readerAndRecent(navController: NavHostController) {
    composable(
        READER,
        arguments = listOf(
            navArgument("uri") { type = NavType.StringType },
            navArgument("title") { type = NavType.StringType },
            // -1 stands for "no target page", since an Int argument cannot be null.
            navArgument("page") { type = NavType.IntType; defaultValue = -1 },
            navArgument("find") { type = NavType.StringType; defaultValue = "" },
        ),
    ) { entry ->
        ReaderScreen(
            uriString = entry.arguments?.read { getStringOrNull("uri") }.orEmpty(),
            title = entry.arguments?.read { getStringOrNull("title") }.orEmpty(),
            targetPage = entry.arguments?.read { getIntOrNull("page") }?.takeIf { it >= 0 },
            findQuery = entry.arguments?.read { getStringOrNull("find") }?.takeIf { it.isNotEmpty() },
            onBack = { navController.popBackStack() },
        )
    }
    composable(RECENT) {
        RecentScreen(
            onOpenDocument = { uriString, title -> navController.openReader(uriString, title) },
            onBack = { navController.popBackStack() },
        )
    }
}

private fun encoded(value: String) = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
