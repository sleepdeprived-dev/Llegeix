package com.david.llegeix.ui.flashcards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.david.llegeix.data.db.dao.WeakCard
import com.david.llegeix.data.flashcards.FlashcardRepository
import com.david.llegeix.platform.Services
import com.david.llegeix.data.flashcards.PictureResults
import com.david.llegeix.data.flashcards.StudyDirection
import com.david.llegeix.data.flashcards.StudyScope
import com.david.llegeix.resources.*
import com.david.llegeix.ui.common.EmptyState
import com.david.llegeix.ui.common.Space
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The weak words, and taking one off them by hand. */
class WeakWordsViewModel(private val flashcards: FlashcardRepository) : ViewModel() {

    /** Null until the database has answered. */
    val weak: StateFlow<List<WeakCard>?> = flashcards.observeWeak()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _direction = MutableStateFlow(flashcards.prefs.direction)
    val direction: StateFlow<StudyDirection> = _direction.asStateFlow()

    fun onChooseDirection(direction: StudyDirection) {
        _direction.value = direction
        flashcards.prefs.direction = direction
    }

    fun onForget(card: WeakCard) = viewModelScope.launch { flashcards.forgetWeak(card.card.id) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = Services.app
                WeakWordsViewModel(app.flashcardRepository)
            }
        }
    }
}

/**
 * The weak words: every card last answered "Encara no", from whatever deck it
 * is in, gathered in one place like a folder of their own.
 *
 * Each row says which deck its word is from, since the list is a mix of all of
 * them. Reviewing plays exactly these words; answering one right there takes
 * it off the list, and the ✕ takes one off by hand.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeakWordsScreen(
    onBack: () -> Unit,
    onStudy: (scope: StudyScope, direction: StudyDirection) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WeakWordsViewModel = viewModel(factory = WeakWordsViewModel.Factory),
) {
    val weak by viewModel.weak.collectAsStateWithLifecycle()
    val direction by viewModel.direction.collectAsStateWithLifecycle()
    var asking by remember { mutableStateOf(false) }
    val title = stringResource(Res.string.flashcards_weak_title)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize(),
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
                title = { Text(title) },
            )
        },
        floatingActionButton = {
            if (!weak.isNullOrEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { asking = true },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    text = { Text(stringResource(Res.string.flashcards_weak_practise)) },
                )
            }
        },
    ) { padding ->
        val list = weak
        when {
            list == null -> Box(Modifier.padding(padding))
            list.isEmpty() -> EmptyState(
                title = stringResource(Res.string.flashcards_weak_empty_title),
                body = stringResource(Res.string.flashcards_weak_empty_body),
                icon = painterResource(Res.drawable.ic_weak),
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(top = Space.sm, bottom = 96.dp),
            ) {
                item(key = "intro") {
                    Text(
                        text = stringResource(Res.string.flashcards_weak_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Space.screen, vertical = Space.sm),
                    )
                }
                items(list, key = { it.card.id }) { item ->
                    WeakRow(item, onForget = { viewModel.onForget(item) }, modifier = Modifier.animateItem())
                }
            }
        }
    }

    if (asking) {
        PlayPairSheet(
            request = PlayRequest(StudyScope.Weak, title),
            lastDirection = direction,
            onDismiss = { asking = false },
            onChoose = { way ->
                viewModel.onChooseDirection(way)
                onStudy(StudyScope.Weak, way)
            },
        )
    }
}

@Composable
private fun WeakRow(item: WeakCard, onForget: () -> Unit, modifier: Modifier = Modifier) {
    val card = item.card
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screen, vertical = 5.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(scheme.surfaceContainerLow)
            .padding(Space.md),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(scheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            val path = card.imagePath
            if (path != null) {
                CardImage(
                    path = path,
                    maxEdge = 160,
                    contentDescription = null,
                    kind = PictureResults.kindOf(card.imageCredit),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(card.catalan.take(1).uppercase(), style = MaterialTheme.typography.titleLarge)
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Space.lg),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = card.catalan,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = card.romanian,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Where it is from, since the list is a mix of every deck.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(scheme.secondaryContainer)
                    .padding(horizontal = Space.sm, vertical = 2.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_cards),
                    contentDescription = null,
                    tint = scheme.onSecondaryContainer,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text = item.deckName,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        IconButton(onClick = onForget) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(Res.string.flashcards_weak_forget, card.catalan),
                tint = scheme.onSurfaceVariant,
            )
        }
    }
}
