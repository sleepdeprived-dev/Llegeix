package com.david.llegeix.desktop

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.david.llegeix.ui.library.LibraryScreen
import com.david.llegeix.ui.settings.SettingsScreen

/**
 * The Biblioteca section on the Mac: the phone's library, reader, reading
 * history and Configuració, wired as the phone's AppNavigation wires them.
 */
@Composable
fun LibraryHost(navController: NavHostController) {
    NavHost(navController, startDestination = LIBRARY) {
        composable(LIBRARY) {
            LibraryScreen(
                onOpenDocument = { document -> navController.openReader(document.uriString, document.displayName) },
                onOpenReading = { uriString, title -> navController.openReader(uriString, title) },
                onFindInDocument = { uriString, title, query ->
                    navController.openReader(uriString, title, find = query.ifBlank { " " })
                },
                onOpenSettings = { navController.navigate(SETTINGS) },
                onOpenHistory = { navController.openRecent() },
            )
        }
        composable(SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }) }
        readerAndRecent(navController)
    }
}

private const val LIBRARY = "library"
private const val SETTINGS = "library/settings"
