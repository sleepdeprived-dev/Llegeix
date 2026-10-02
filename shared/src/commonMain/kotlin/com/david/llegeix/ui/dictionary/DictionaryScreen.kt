package com.david.llegeix.ui.dictionary

import com.david.llegeix.ui.platform.onThisDevice
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.llegeix.data.settings.TranslationTarget
import com.david.llegeix.resources.*
import com.david.llegeix.ui.common.DictionaryCard
import com.david.llegeix.ui.common.EmptyState
import com.david.llegeix.ui.common.IpaLine
import com.david.llegeix.ui.common.PronounceButton
import com.david.llegeix.ui.common.ScreenTitle
import com.david.llegeix.ui.common.SearchField
import com.david.llegeix.ui.common.RecentSearches
import com.david.llegeix.ui.common.Space
import com.david.llegeix.ui.common.TranslationTargetFlags
import com.david.llegeix.ui.common.VerbDetails
import com.david.llegeix.ui.common.resolved
import com.david.llegeix.ui.platform.PlatformBackHandler
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The dictionary as a place, not as something that happens to a word you
 * pressed.
 *
 * The screen is only ever doing one of two things, and it never does both at
 * once: asking which word, or answering about one. Typing puts it back to
 * asking, so a definition is never sitting underneath a half-typed different
 * word. That is the same rule the rest of the app follows — one question per
 * screen — applied to the one place in Llegeix where the reader arrives with a
 * word already in mind rather than finding one on a page.
 *
 * What it answers with is deliberately identical to the reader's lookup sheet:
 * the Catalan, its pronunciation, the translation, then the Viccionari's
 * definition and the thesaurus. A word checked here and the same word pressed
 * while reading should not look like two different words.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreen(
    modifier: Modifier = Modifier,
    viewModel: DictionaryViewModel = viewModel(factory = DictionaryViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    // An open word is a mode, so back leaves the word before it leaves the tab.
    PlatformBackHandler(enabled = state.entry != null) { viewModel.onCloseEntry() }

    Scaffold(
        // The app shell's Scaffold has already inset this screen for the status
        // bar and the navigation bar; counting them a second time put a dead
        // band above the bottom bar and made every top bar 24dp taller than it
        // asks to be.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                expandedHeight = Space.topBar,
                title = {
                    ScreenTitle(
                        icon = Res.drawable.ic_dictionary,
                        title = stringResource(Res.string.dictionary_title),
                    )
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            SearchField(
                query = state.query,
                placeholder = stringResource(Res.string.dictionary_search),
                onQueryChange = viewModel::onQueryChange,
                onSubmit = {
                    keyboard?.hide()
                    viewModel.onSubmit()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.screen)
                    .padding(top = Space.lg, bottom = Space.md),
            )

            val entry = state.entry
            when {
                entry != null -> WordEntry(
                    entry = entry,
                    target = state.translationTarget,
                    onToggleSaved = viewModel::onToggleSaved,
                    onToggleTarget = viewModel::onToggleTranslationTarget,
                    onRetryOnAnyNetwork = viewModel::onRetryOnAnyNetwork,
                    onOpenWord = { word ->
                        keyboard?.hide()
                        viewModel.onPickSuggestion(word)
                    },
                )

                state.query.isBlank() -> Column {
                    RecentSearches(
                        history = recentSearches,
                        onPick = { past: String ->
                            keyboard?.hide()
                            viewModel.onPickSuggestion(past)
                        },
                        onClear = viewModel::onForgetSearches,
                        onRemove = viewModel::onForgetSearch,
                        modifier = Modifier.padding(bottom = Space.sm),
                    )
                    EmptyState(
                        title = stringResource(Res.string.dictionary_empty_title),
                        body = stringResource(Res.string.dictionary_empty_body),
                        icon = painterResource(Res.drawable.ic_dictionary),
                    )
                }

                else -> Suggestions(
                    typed = state.query.trim(),
                    suggestions = state.suggestions,
                    onPick = { word ->
                        keyboard?.hide()
                        viewModel.onPickSuggestion(word)
                    },
                )
            }
        }
    }
}

/**
 * What the reader might mean, with what they actually typed always at the top.
 *
 * The typed word leads even when the references have never heard of it. A
 * plural, a conjugated verb or a name is still worth pronouncing and
 * translating, and a list that offers no way to look up the word you just typed
 * reads as the app refusing rather than as the dictionary being short.
 */
@Composable
private fun Suggestions(
    typed: String,
    suggestions: List<String>,
    onPick: (String) -> Unit,
) {
    val rows = remember(typed, suggestions) {
        if (suggestions.any { it.equals(typed, ignoreCase = true) }) {
            suggestions
        } else {
            listOf(typed) + suggestions
        }
    }
    LazyColumn(contentPadding = PaddingValues(bottom = Space.lg)) {
        items(rows, key = { it }) { word ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(word) }
                    .padding(horizontal = Space.screen, vertical = Space.md),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = word,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = Space.lg),
                )
            }
        }
    }
}

/**
 * One word, answered.
 *
 * The order is the reader's sheet's order and for the same reason: the Catalan,
 * its pronunciation, the translation, then everything that is supporting detail.
 * Someone who only wants to know what a word means should never have to look
 * for it.
 */
@Composable
private fun WordEntry(
    entry: DictionaryEntry,
    target: TranslationTarget,
    onToggleSaved: () -> Unit,
    onToggleTarget: () -> Unit,
    onRetryOnAnyNetwork: () -> Unit,
    /** Look up another word, which the verb card's infinitive row asks for. */
    onOpenWord: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.screen)
            .padding(bottom = Space.xxl),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(
                    Res.string.lookup_direction,
                    stringResource(target.directionRes),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            TranslationTargetFlags(target = target, onPick = onToggleTarget)
            IconButton(onClick = onToggleSaved) {
                Icon(
                    imageVector = if (entry.isSaved) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = stringResource(
                        if (entry.isSaved) {
                            Res.string.lookup_unsave_word
                        } else {
                            Res.string.lookup_save_word
                        },
                    ),
                    tint = if (entry.isSaved) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        // Beside the word, exactly as in the reader's sheet: a word checked
        // here and the same word pressed while reading should not look like two
        // different words, and that goes for what can be done to it too.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = Space.xs),
        ) {
            Text(
                text = entry.word,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f, fill = false),
            )
            PronounceButton(text = entry.word)
        }
        if (entry.ipa.isNotBlank()) {
            IpaLine(
                ipa = entry.ipa,
                isApproximate = entry.isIpaApproximate,
                modifier = Modifier.padding(top = Space.xs),
            )
        }

        // A floor under the height, so the panel is already the size it will end
        // up at before the translation lands and does not jump under the finger.
        Box(
            modifier = Modifier
                .padding(top = Space.xl)
                .heightIn(min = AnswerMinHeight),
        ) {
            when (entry.status) {
                EntryStatus.TRANSLATING -> Text(
                    text = stringResource(Res.string.lookup_translating),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                EntryStatus.DOWNLOADING_MODEL -> Column {
                    Text(
                        text = stringResource(Res.string.lookup_downloading_title),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(Res.string.lookup_downloading_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Space.xs),
                    )
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Space.md),
                    )
                }

                EntryStatus.READY -> Text(
                    text = entry.translation.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                EntryStatus.FAILED -> Text(
                    text = entry.error?.resolved() ?: stringResource(Res.string.lookup_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        // Between the translation and the references, because it answers a
        // question that sits between theirs: the translation says what these
        // letters mean, the references say what the word means, and this says
        // what the word *is* — which is the one of the three a learner is least
        // able to work out for themselves.
        entry.verb?.let { verb ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Space.lg)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(Space.lg),
            ) {
                Text(
                    text = stringResource(Res.string.dictionary_verb_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = Space.xs),
                )
                VerbDetails(
                    verb = verb,
                    infinitiveMeaning = entry.verbInfinitiveMeaning,
                    definition = entry.verbDefinition,
                    onOpenInfinitive = onOpenWord,
                )
            }
        }

        DictionaryCard(
            entry = entry.reference,
            selected = entry.word,
            modifier = Modifier.padding(top = Space.lg),
        )

        if (entry.canRetryOnAnyNetwork) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.md),
                modifier = Modifier.padding(top = Space.xl),
            ) {
                OutlinedButton(onClick = onRetryOnAnyNetwork) {
                    Text(stringResource(onThisDevice(Res.string.lookup_use_mobile_data, Res.string.lookup_use_mobile_data_mac)))
                }
            }
        }
    }
}

/** Enough for two lines of the style the translation is set in. */
private val AnswerMinHeight = 72.dp
