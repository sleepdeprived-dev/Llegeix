package com.david.llegeix.desktop

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.savedstate.read
import com.david.llegeix.resources.Res
import com.david.llegeix.resources.action_back
import com.david.llegeix.resources.desktop_section_pending
import com.david.llegeix.resources.recent_title
import com.david.llegeix.resources.settings_title
import com.david.llegeix.ui.common.EmptyState
import com.david.llegeix.ui.common.Space
import com.david.llegeix.ui.library.LibraryScreen
import com.david.llegeix.ui.reader.ReaderScreen
import org.jetbrains.compose.resources.stringResource
import java.net.URLEncoder

/**
 * The Biblioteca section on the Mac: the phone's library and reader, wired as
 * the phone's AppNavigation wires them. Configuració and the reading history
 * have not reached the Mac yet, so each of those is a page that says so, with
 * the way back.
 */
@Composable
fun LibraryHost(navController: NavHostController) {
    NavHost(navController, startDestination = LIBRARY) {
        composable(LIBRARY) {
            LibraryScreen(
                onOpenDocument = { document -> navController.navigate(reader(document.uriString, document.displayName)) },
                onOpenReading = { uriString, title -> navController.navigate(reader(uriString, title)) },
                onFindInDocument = { uriString, title, query ->
                    navController.navigate(reader(uriString, title, find = query.ifBlank { " " }))
                },
                onOpenSettings = { navController.navigate(SETTINGS) },
                onOpenHistory = { navController.navigate(HISTORY) },
            )
        }
        composable(
            READER,
            arguments = listOf(
                navArgument("uri") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
                navArgument("find") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            ReaderScreen(
                uriString = entry.arguments?.read { getStringOrNull("uri") }.orEmpty(),
                title = entry.arguments?.read { getStringOrNull("title") }.orEmpty(),
                findQuery = entry.arguments?.read { getStringOrNull("find") }?.takeIf { it.isNotEmpty() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(SETTINGS) {
            Pending(
                title = stringResource(Res.string.settings_title),
                body = stringResource(Res.string.desktop_section_pending),
                onBack = { navController.popBackStack() },
            )
        }
        composable(HISTORY) {
            Pending(
                title = stringResource(Res.string.recent_title),
                body = stringResource(Res.string.desktop_section_pending),
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/** A screen the Mac does not have yet, under its own name. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Pending(title: String, body: String, onBack: () -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                expandedHeight = Space.topBar,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        },
    ) { padding ->
        EmptyState(title = title, body = body, modifier = Modifier.fillMaxSize().padding(padding))
    }
}

private const val LIBRARY = "library"
private const val READER = "library/reader?uri={uri}&title={title}&find={find}"
private const val SETTINGS = "library/settings"
private const val HISTORY = "library/history"

private fun reader(uriString: String, title: String, find: String = "") =
    "library/reader?uri=${encoded(uriString)}&title=${encoded(title)}&find=${encoded(find)}"

private fun encoded(value: String) = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
