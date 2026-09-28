package com.david.llegeix.ui.bookmarks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.david.llegeix.LlegeixApp
import com.david.llegeix.data.db.entity.LearnedWordEntity
import com.david.llegeix.data.flashcards.FlashcardRepository
import com.david.llegeix.data.flashcards.PictureSearch
import com.david.llegeix.resources.*
import com.david.llegeix.translate.WordTranslator
import com.david.llegeix.ui.common.AppBottomSheet
import com.david.llegeix.ui.common.EmptyState
import com.david.llegeix.ui.common.Space
import com.david.llegeix.ui.flashcards.Flag
import com.david.llegeix.util.runCatchingCancellable
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One day of learned words, the newest day first. */
data class LearnedDay(val date: LocalDate, val words: List<LearnedWordEntity>)

/** The draft in the add sheet. */
data class LearnedDraft(
    val catalan: String = "",
    val romanian: String = "",
    /** The Romanian was filled in by the app rather than typed, so it may be replaced. */
    val suggested: Boolean = false,
)

/**
 * The words learned by day, grouped by the day they were added on.
 *
 * Days with nothing added are simply not there: the list is a diary of the
 * days something was learned, not a calendar.
 */
class LearnedWordsViewModel(
    private val flashcards: FlashcardRepository,
    private val pictureSearch: PictureSearch,
) : ViewModel() {

    val days: StateFlow<List<LearnedDay>?> = flashcards.observeLearned()
        .map { words -> group(words) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _draft = MutableStateFlow(LearnedDraft())
    val draft: StateFlow<LearnedDraft> = _draft.asStateFlow()

    private var suggestJob: Job? = null
    private var translator: WordTranslator? = null

    fun onCatalanChange(text: String) {
        _draft.update { it.copy(catalan = text) }
        suggestRomanian(text)
    }

    fun onRomanianChange(text: String) {
        suggestJob?.cancel()
        _draft.update { it.copy(romanian = text, suggested = false) }
    }

    /**
     * The Romanian, offered after a pause in the typing: ARASAAC's dictionary
     * first — people's words for this word — and the on-device translator only
     * for words it has not labelled. Never over something the reader typed.
     */
    private fun suggestRomanian(word: String) {
        suggestJob?.cancel()
        val draft = _draft.value
        if (draft.romanian.isNotBlank() && !draft.suggested) return
        val trimmed = word.trim()
        if (trimmed.isEmpty()) {
            _draft.update { if (it.suggested) it.copy(romanian = "", suggested = false) else it }
            return
        }
        suggestJob = viewModelScope.launch {
            delay(SUGGEST_PAUSE_MS)
            val meaning = pictureSearch.meanings(trimmed)?.romanian ?: translate(trimmed)
            if (meaning != null && _draft.value.catalan.trim() == trimmed) {
                _draft.update {
                    if (it.romanian.isBlank() || it.suggested) it.copy(romanian = meaning, suggested = true) else it
                }
            }
        }
    }

    private suspend fun translate(word: String): String? {
        val model = translator ?: WordTranslator(targetLanguage = "ro").also { translator = it }
        if (!model.isModelReady) {
            runCatchingCancellable { model.ensureModel(requireWifi = true) }.onFailure { return null }
        }
        return runCatchingCancellable { model.translate(word) }.getOrNull()
            ?.trim()?.takeIf { it.isNotEmpty() && !it.equals(word, ignoreCase = true) }
    }

    /** Save the draft as learned today; true when it was saved. */
    fun onSave(): Boolean {
        val draft = _draft.value
        if (draft.catalan.isBlank() || draft.romanian.isBlank()) return false
        viewModelScope.launch { flashcards.addLearned(draft.catalan, draft.romanian) }
        suggestJob?.cancel()
        _draft.value = LearnedDraft()
        return true
    }

    fun onDiscard() {
        suggestJob?.cancel()
        _draft.value = LearnedDraft()
    }

    fun onRemove(word: LearnedWordEntity) = viewModelScope.launch { flashcards.removeLearned(word.id) }

    override fun onCleared() {
        translator?.close()
    }

    companion object {
        private const val SUGGEST_PAUSE_MS = 600L

        fun group(words: List<LearnedWordEntity>, zone: ZoneId = ZoneId.systemDefault()): List<LearnedDay> =
            words.groupBy { Instant.ofEpochMilli(it.learnedAt).atZone(zone).toLocalDate() }
                .map { (date, list) -> LearnedDay(date, list.sortedByDescending { it.learnedAt }) }
                .sortedByDescending { it.date }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LlegeixApp
                LearnedWordsViewModel(app.flashcardRepository, app.pictureSearch)
            }
        }
    }
}

/**
 * "Paraules que he après avui": the words the reader learned, with their
 * Romanian, set out day by day — a date, then that day's words — newest first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnedWordsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LearnedWordsViewModel = viewModel(factory = LearnedWordsViewModel.Factory),
) {
    val days by viewModel.days.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                expandedHeight = Space.topBar,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
                title = { Text(stringResource(Res.string.learned_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.learned_add))
            }
        },
    ) { padding ->
        val list = days
        when {
            list == null -> Box(Modifier.padding(padding))
            list.isEmpty() -> EmptyState(
                title = stringResource(Res.string.learned_empty_title),
                body = stringResource(Res.string.learned_empty_body),
                icon = painterResource(Res.drawable.ic_dictionary),
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(top = Space.sm, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(Space.lg),
            ) {
                items(list, key = { it.date.toEpochDay() }) { day ->
                    DayCard(day, onRemove = viewModel::onRemove, modifier = Modifier.animateItem())
                }
            }
        }
    }

    if (adding) AddLearnedSheet(viewModel = viewModel, onDismiss = { adding = false })
}

/** One day: its date as a heading, then its words, each with its Romanian. */
@Composable
private fun DayCard(day: LearnedDay, onRemove: (LearnedWordEntity) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screen)
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.surfaceContainerLow)
            .padding(vertical = Space.lg),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Space.lg),
        ) {
            Text(
                text = dayTitle(day.date),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = scheme.primary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = pluralStringResource(Res.plurals.learned_count, day.words.size, day.words.size),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        Column(modifier = Modifier.padding(top = Space.sm)) {
            day.words.forEach { word ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = Space.lg, end = Space.xs),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(scheme.primary),
                    )
                    Text(
                        text = word.catalan,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        modifier = Modifier.padding(start = Space.md),
                    )
                    Text(
                        text = "  —  " + word.romanian,
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onRemove(word) }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(Res.string.learned_remove, word.catalan),
                            tint = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

/** "Avui · 26 de setembre", "Ahir · 25 de setembre", "23 de setembre", with the year when it is not this one. */
@Composable
private fun dayTitle(date: LocalDate): String {
    val today = LocalDate.now()
    val catalan = Locale.forLanguageTag("ca")
    val pattern = if (date.year == today.year) "d MMMM" else "d MMMM yyyy"
    val formatted = date.format(DateTimeFormatter.ofPattern(pattern, catalan))
    return when (date) {
        today -> stringResource(Res.string.learned_today, formatted)
        today.minusDays(1) -> stringResource(Res.string.learned_yesterday, formatted)
        else -> formatted
    }
}

/** Adding a word: the Catalan, and its Romanian — which fills itself in when it can. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddLearnedSheet(viewModel: LearnedWordsViewModel, onDismiss: () -> Unit) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val close = {
        viewModel.onDiscard()
        onDismiss()
    }
    AppBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.screen)
                .padding(bottom = Space.xl)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Text(
                text = stringResource(Res.string.learned_add),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = Space.md),
            )
            OutlinedTextField(
                value = draft.catalan,
                onValueChange = viewModel::onCatalanChange,
                label = { Text(stringResource(Res.string.flashcards_field_catalan)) },
                leadingIcon = { Flag(Res.drawable.ic_flag_ca, 24.dp) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
            OutlinedTextField(
                value = draft.romanian,
                onValueChange = viewModel::onRomanianChange,
                label = { Text(stringResource(Res.string.flashcards_field_romanian)) },
                leadingIcon = { Flag(Res.drawable.ic_flag_ro, 24.dp) },
                singleLine = true,
                supportingText = if (draft.suggested) {
                    { Text(stringResource(Res.string.learned_suggested)) }
                } else {
                    null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Space.sm),
            )
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Space.md),
            ) {
                TextButton(onClick = close) { Text(stringResource(Res.string.action_cancel)) }
                Button(
                    onClick = { if (viewModel.onSave()) onDismiss() },
                    enabled = draft.catalan.isNotBlank() && draft.romanian.isNotBlank(),
                    modifier = Modifier.padding(start = Space.sm),
                ) { Text(stringResource(Res.string.learned_save)) }
            }
        }
    }
}
