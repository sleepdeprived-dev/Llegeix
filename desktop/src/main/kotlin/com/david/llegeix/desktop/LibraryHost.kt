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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.david.llegeix.resources.Res
import com.david.llegeix.resources.action_back
import com.david.llegeix.resources.desktop_section_pending
import com.david.llegeix.resources.settings_title
import com.david.llegeix.ui.common.EmptyState
import com.david.llegeix.ui.common.Space
import com.david.llegeix.ui.library.LibraryScreen
import org.jetbrains.compose.resources.stringResource

/**
 * The Biblioteca section on the Mac: the phone's library, reader and reading
 * history, wired as the phone's AppNavigation wires them. Configuració has not
 * reached the Mac yet, so it is a page that says so, with the way back.
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
        composable(SETTINGS) {
            Pending(
                title = stringResource(Res.string.settings_title),
                body = stringResource(Res.string.desktop_section_pending),
                onBack = { navController.popBackStack() },
            )
        }
        readerAndRecent(navController)
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
private const val SETTINGS = "library/settings"
