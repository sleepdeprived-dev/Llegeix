package com.david.llegeix.ui.recent

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.llegeix.data.settings.LibraryLayout
import com.david.llegeix.data.db.dao.RecentDocument
import com.david.llegeix.resources.*
import com.david.llegeix.ui.common.CoverAspectRatio
import com.david.llegeix.ui.common.EmptyState
import com.david.llegeix.ui.common.MenuIcon
import com.david.llegeix.ui.common.PdfCover
import com.david.llegeix.ui.common.TagStrip
import com.david.llegeix.ui.library.GridCoverWidth
import com.david.llegeix.ui.library.ListCoverWidth
import com.david.llegeix.ui.common.AppSnackbarHost
import com.david.llegeix.ui.common.Space
import com.david.llegeix.util.formatModified
import com.david.llegeix.util.pdfTitle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RecentScreen(
    onOpenDocument: (uriString: String, title: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecentViewModel = viewModel(factory = RecentViewModel.Factory),
) {
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    val tagsByDocument by viewModel.tagsByDocument.collectAsStateWithLifecycle()
    val names by viewModel.names.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var forgetting by remember { mutableStateOf<RecentDocument?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val removedLabel = stringResource(Res.string.recent_removed)
    val undoLabel = stringResource(Res.string.action_undo)

    /**
     * Forget an entry, having asked, and offer it straight back anyway.
     *
     * The undo alone was the whole safety net for a while, on the grounds that
     * removing something from a reading history destroys nothing. That is true
     * and it was still the wrong shape: the gesture is a long press on a list
     * whose rows are otherwise tapped, so it fires by accident, and an undo
     * only helps somebody who noticed the message before it went. Ask first,
     * then keep the undo for the press that was meant and regretted.
     */
    fun forget(recent: RecentDocument) {
        viewModel.remove(recent.uriString)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = removedLabel,
                actionLabel = undoLabel,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.restore(recent)
            }
        }
    }

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
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                        )
                    }
                },
                title = { Text(stringResource(Res.string.recent_title)) },
                actions = {
                    // Clearing the history is destructive and rarely wanted, so
                    // it sits behind the menu rather than one stray tap away.
                    if (recents.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(
                                        Res.string.action_more_options,
                                    ),
                                )
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                            ) {
                                DropdownMenuItem(
                                    leadingIcon = {
                                        MenuIcon(
                                            painterResource(
                                                if (layout == LibraryLayout.GRID) {
                                                    Res.drawable.ic_list
                                                } else {
                                                    Res.drawable.ic_grid
                                                },
                                            ),
                                        )
                                    },
                                    text = {
                                        Text(
                                            stringResource(
                                                if (layout == LibraryLayout.GRID) {
                                                    Res.string.library_view_list
                                                } else {
                                                    Res.string.library_view_grid
                                                },
                                            ),
                                        )
                                    },
                                    onClick = {
                                        viewModel.onToggleLayout()
                                        menuOpen = false
                                    },
                                )
                                DropdownMenuItem(
                                    leadingIcon = { MenuIcon(Icons.Default.Delete) },
                                    text = {
                                        Text(stringResource(Res.string.recent_clear_history))
                                    },
                                    onClick = {
                                        viewModel.clearAll()
                                        menuOpen = false
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (recents.isEmpty()) {
            EmptyState(
                title = stringResource(Res.string.recent_empty_title),
                body = stringResource(Res.string.recent_empty_body),
                icon = painterResource(Res.drawable.ic_recent),
                modifier = Modifier.padding(innerPadding),
            )
        } else if (layout == LibraryLayout.GRID) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                modifier = Modifier.padding(innerPadding),
                contentPadding = PaddingValues(
                    start = Space.md,
                    end = Space.md,
                    bottom = Space.lg,
                ),
            ) {
                items(recents, key = { it.uriString }) { recent ->
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .combinedClickable(
                                onClick = {
                                    onOpenDocument(
                                        recent.uriString,
                                        names.titleFor(recent.uriString, recent.displayName),
                                    )
                                },
                                onLongClick = { forgetting = recent },
                            )
                            .padding(Space.sm),
                    ) {
                        PdfCover(
                            uriString = recent.uriString,
                            width = GridCoverWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(CoverAspectRatio),
                            cornerRadius = 12.dp,
                        )
                        Text(
                            text = names.titleFor(recent.uriString, recent.displayName),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = Space.sm),
                        )
                        Text(
                            text = stringResource(
                                Res.string.recent_page,
                                recent.lastPageIndex + 1,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding),
                contentPadding = PaddingValues(bottom = Space.lg),
            ) {
                items(recents, key = { it.uriString }) { recent ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Hold to remove. A row of X buttons would put a
                            // destructive control next to every entry in a list
                            // whose whole purpose is being tapped quickly.
                            .combinedClickable(
                                onClick = {
                                    onOpenDocument(
                                        recent.uriString,
                                        names.titleFor(recent.uriString, recent.displayName),
                                    )
                                },
                                onLongClick = { forgetting = recent },
                            )
                            .padding(
                                start = Space.screen,
                                end = Space.screen,
                                top = Space.row,
                                bottom = Space.row,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PdfCover(
                            uriString = recent.uriString,
                            width = ListCoverWidth,
                            modifier = Modifier
                                .width(ListCoverWidth)
                                .aspectRatio(CoverAspectRatio),
                        )
                        Column(modifier = Modifier.padding(start = Space.lg)) {
                            Text(
                                text = names.titleFor(recent.uriString, recent.displayName),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(
                                    Res.string.recent_subtitle,
                                    stringResource(
                                        Res.string.recent_page,
                                        recent.lastPageIndex + 1,
                                    ),
                                    formatModified(recent.viewedAt),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        TagStrip(
                            tags = tagsByDocument[recent.uriString].orEmpty(),
                            modifier = Modifier.padding(start = Space.sm),
                        )
                    }
                }
            }
        }
    }

    forgetting?.let { recent ->
        AlertDialog(
            onDismissRequest = { forgetting = null },
            title = { Text(stringResource(Res.string.recent_forget_title)) },
            text = {
                Text(
                    stringResource(
                        Res.string.recent_forget_body,
                        names.titleFor(recent.uriString, recent.displayName),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        forgetting = null
                        forget(recent)
                    },
                ) { Text(stringResource(Res.string.recent_forget_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { forgetting = null }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }
}
