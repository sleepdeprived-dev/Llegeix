package com.david.llegeix.desktop

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.savedstate.read
import com.david.llegeix.ui.bookmarks.BookmarkedCollectionScreen
import com.david.llegeix.ui.bookmarks.LearnedWordsScreen
import com.david.llegeix.ui.bookmarks.ReadLaterCollectionScreen
import com.david.llegeix.ui.folders.FolderDetailScreen
import com.david.llegeix.ui.practice.PracticeScreen
import com.david.llegeix.ui.saved.SavedScreen
import java.net.URLEncoder

/**
 * The Desat section on the Mac: the phone's saved words and collections, wired
 * as the phone's AppNavigation wires them, route for route.
 */
@Composable
fun SavedHost(navController: NavHostController) {
    NavHost(navController, startDestination = SAVED) {
        composable(SAVED) {
            SavedScreen(
                onOpenDocument = { uriString, title, page -> navController.openReader(uriString, title, page) },
                onOpenFolder = { folderId, name -> navController.navigate(folderDetail(folderId, name)) },
                onFillNewCollection = { folderId, name -> navController.navigate(folderDetail(folderId, name, adding = true)) },
                onOpenBookmarked = { navController.navigate(BOOKMARKED) },
                onOpenReadLater = { navController.navigate(READ_LATER) },
                onOpenRecent = { navController.openRecent() },
                onPractise = { navController.navigate(PRACTICE) },
                onOpenLearned = { navController.navigate(LEARNED) },
            )
        }
        composable(LEARNED) { LearnedWordsScreen(onBack = { navController.popBackStack() }) }
        composable(PRACTICE) { PracticeScreen(onBack = { navController.popBackStack() }) }
        composable(BOOKMARKED) {
            BookmarkedCollectionScreen(
                onBack = { navController.popBackStack() },
                onOpenDocument = { uriString, title -> navController.openReader(uriString, title) },
            )
        }
        composable(READ_LATER) {
            ReadLaterCollectionScreen(
                onBack = { navController.popBackStack() },
                onOpenDocument = { uriString, title -> navController.openReader(uriString, title) },
            )
        }
        composable(
            FOLDER_DETAIL,
            arguments = listOf(
                navArgument("folderId") { type = NavType.LongType },
                navArgument("name") { type = NavType.StringType; defaultValue = "" },
                navArgument("adding") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            FolderDetailScreen(
                folderId = entry.arguments?.read { getLongOrNull("folderId") } ?: 0L,
                folderName = entry.arguments?.read { getStringOrNull("name") }.orEmpty(),
                startAdding = entry.arguments?.read { getBooleanOrNull("adding") } == true,
                onBack = { navController.popBackStack() },
                onOpenDocument = { uriString, title -> navController.openReader(uriString, title) },
            )
        }
        readerAndRecent(navController)
    }
}

private const val SAVED = "saved"
private const val LEARNED = "saved/learned"
private const val PRACTICE = "saved/practice"
private const val BOOKMARKED = "collection/bookmarked"
private const val READ_LATER = "collection/read-later"
private const val FOLDER_DETAIL = "folder/{folderId}?name={name}&adding={adding}"

private fun folderDetail(folderId: Long, name: String, adding: Boolean = false) =
    "folder/$folderId?name=${URLEncoder.encode(name, Charsets.UTF_8).replace("+", "%20")}&adding=$adding"
