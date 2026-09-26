package com.david.llegeix.ui.flashcards

import androidx.activity.compose.BackHandler
import com.david.llegeix.data.flashcards.Suggested
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilledTonalButton
import com.david.llegeix.data.flashcards.PictureResults
import com.david.llegeix.data.flashcards.PictureSource
import com.david.llegeix.data.flashcards.PictureHit
import com.david.llegeix.LlegeixApp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.produceState
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.annotation.DrawableRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.llegeix.R
import com.david.llegeix.data.flashcards.ImageSizing
import com.david.llegeix.ui.common.AppSnackbarHost
import com.david.llegeix.ui.common.PronounceButton
import com.david.llegeix.ui.common.Space
import com.david.llegeix.ui.common.resolved

/**
 * One card, being written.
 *
 * The fields are in the order the card is thought about: the Catalan word, how
 * it sounds, what it means, and a picture if one helps. The app fills in the
 * two it can — the pronunciation from the spelling, a meaning from the
 * translator on the phone — and says under each field that it did, so a guess
 * is never mistaken for something the reader wrote.
 *
 * The buttons sit at the foot of the screen and ride up with the keyboard,
 * because the moment somebody finishes typing is the moment they want them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardEditorScreen(
    deckId: Long,
    cardId: Long?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CardEditorViewModel = viewModel(
        key = "card-$deckId-$cardId",
        factory = CardEditorViewModel.factory(deckId, cardId),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    val cleared by viewModel.cleared.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val catalanFocus = remember { FocusRequester() }
    var confirmingDiscard by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    val pickPicture = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(viewModel::onImagePicked) }
    val openPicker = {
        pickPicture.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    LaunchedEffect(finished) { if (finished) onBack() }

    // A new card starts with the keyboard up on the word, and so does the empty
    // form that follows "save and add another".
    LaunchedEffect(state.isLoading, cleared) {
        if (!state.isLoading && state.isNew) runCatching { catalanFocus.requestFocus() }
    }

    val messageText = message?.resolved()
    LaunchedEffect(messageText) {
        val text = messageText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.onMessageShown()
    }

    val leave = { if (state.isDirty) confirmingDiscard = true else onBack() }
    BackHandler(enabled = state.isDirty) { confirmingDiscard = true }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize(),
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                expandedHeight = Space.topBar,
                navigationIcon = {
                    IconButton(onClick = leave) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = stringResource(
                                if (state.isNew) {
                                    R.string.flashcards_new_card
                                } else {
                                    R.string.flashcards_edit_card_title
                                },
                            ),
                            maxLines = 1,
                        )
                        if (state.deckName.isNotEmpty()) {
                            Text(
                                text = state.deckName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { confirmingDelete = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    // The shell has already made room for the navigation bar;
                    // without this the keyboard's height would count it again.
                    .consumeWindowInsets(WindowInsets.navigationBars)
                    .imePadding(),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Space.md),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.screen, vertical = Space.md),
                ) {
                    if (state.isNew) {
                        // The wider of the two, because its label is: "Save"
                        // needs a fraction of what "Save and add another" does,
                        // and equal halves cut the longer one short.
                        OutlinedButton(
                            onClick = viewModel::onSaveAndAddAnother,
                            enabled = state.canSave,
                            modifier = Modifier.weight(1.6f),
                        ) {
                            Text(
                                stringResource(R.string.flashcards_save_and_add),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Button(
                        onClick = viewModel::onSave,
                        enabled = state.canSave,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.flashcards_save)) }
                }
            }
        },
    ) { innerPadding ->
        if (state.isLoading) {
            Box(Modifier.padding(innerPadding))
            return@Scaffold
        }
        val pictures by viewModel.pictures.state.collectAsStateWithLifecycle()
        val pictureSource by viewModel.pictures.source.collectAsStateWithLifecycle()
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen, vertical = Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            // The word, and how it sounds.
            FormSection {
                OutlinedTextField(
                    value = state.catalan,
                    onValueChange = viewModel::onCatalanChange,
                    label = { Text(stringResource(R.string.flashcards_field_catalan)) },
                    textStyle = MaterialTheme.typography.headlineSmall,
                    singleLine = true,
                    leadingIcon = { LanguageFlag(R.drawable.ic_flag_ca) },
                    supportingText = if (state.catalanIsSuggestion && state.catalan.isNotBlank()) {
                        { Text(stringResource(R.string.flashcards_catalan_suggested)) }
                    } else {
                        null
                    },
                    // Hearing it is the check the transcription cannot give.
                    // The button only speaks on a press, like everywhere else.
                    trailingIcon = if (state.catalan.isNotBlank()) {
                        { PronounceButton(text = state.catalan) }
                    } else {
                        null
                    },
                    keyboardOptions = WordKeyboard,
                    shape = FieldShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(catalanFocus),
                )
                OutlinedTextField(
                    value = state.ipa.text,
                    onValueChange = viewModel::onIpaChange,
                    textStyle = LocalTextStyle.current.copy(fontFamily = com.david.llegeix.ui.theme.IpaFont),
                    label = { Text(stringResource(R.string.flashcards_field_ipa)) },
                    singleLine = true,
                    prefix = { Text("[") },
                    suffix = { Text("]") },
                    // No "approx." here any more.
                    //
                    // It said that the spelling does not settle whether a
                    // stressed e or o is open or closed, which is true, and on
                    // a card being written it was a paragraph of phonology in
                    // answer to a question nobody had asked. The line that
                    // remains already says the transcription was worked out by
                    // the app and can be changed, which is the whole of what
                    // somebody writing a card needs to know about it. The
                    // dictionary still marks it, where a reader has gone
                    // looking for the pronunciation itself.
                    supportingText = when {
                        state.ipa.text.isBlank() -> null
                        state.ipa.isSuggestion -> {
                            { Text(stringResource(R.string.flashcards_ipa_generated)) }
                        }

                        else -> null
                    },
                    keyboardOptions = WordKeyboard,
                    shape = FieldShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Space.sm),
                )
            }

            // What it means: Romanian, which every card has, and English, which
            // it may. One section, because they are one fact in two languages.
            FormSection(title = stringResource(R.string.flashcards_section_meaning)) {
                OutlinedTextField(
                    value = state.romanian.text,
                    onValueChange = viewModel::onRomanianChange,
                    label = { Text(stringResource(R.string.flashcards_field_romanian)) },
                    textStyle = MaterialTheme.typography.titleLarge,
                    singleLine = true,
                    leadingIcon = { LanguageFlag(R.drawable.ic_flag_ro) },
                    supportingText = meaningNote(state.romanian, state.romanianSuggestion, state.romanianSource)?.let { { Text(it) } },
                    keyboardOptions = WordKeyboard,
                    shape = FieldShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { if (!it.isFocused) viewModel.onMeaningEditingDone() },
                )
                OutlinedTextField(
                    value = state.english.text,
                    onValueChange = viewModel::onEnglishChange,
                    label = { Text(stringResource(R.string.flashcards_field_english)) },
                    singleLine = true,
                    leadingIcon = { LanguageFlag(R.drawable.ic_flag_uk) },
                    supportingText = meaningNote(state.english, state.englishSuggestion, state.englishSource)?.let { { Text(it) } },
                    keyboardOptions = WordKeyboard.copy(imeAction = ImeAction.Done),
                    shape = FieldShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Space.sm)
                        .onFocusChanged { if (!it.isFocused) viewModel.onMeaningEditingDone() },
                )
            }

            FormSection(title = stringResource(R.string.flashcards_picture)) {
                val imagePath = state.imagePath
                when {
                    state.isImporting -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }

                    imagePath != null -> ChosenPicture(
                        path = imagePath,
                        credit = state.imageCredit,
                        onChange = viewModel::onRemoveImage,
                    )

                    else -> PictureGrid(
                        suggestions = pictures,
                        source = pictureSource,
                        onSourceChange = viewModel::onPictureSourceChange,
                        onPick = viewModel::onPickSuggestion,
                        onPickOwn = openPicker,
                        onRetry = viewModel::onRetryPictures,
                        onSearch = viewModel::onSearchPictures,
                    )
                }
            }
        }
    }

    if (confirmingDiscard) {
        AlertDialog(
            onDismissRequest = { confirmingDiscard = false },
            title = { Text(stringResource(R.string.flashcards_discard_title)) },
            text = { Text(stringResource(R.string.flashcards_discard_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDiscard = false
                        onBack()
                    },
                ) { Text(stringResource(R.string.flashcards_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDiscard = false }) {
                    Text(stringResource(R.string.flashcards_keep_editing))
                }
            },
        )
    }

    if (confirmingDelete) {
        DeleteCardDialog(
            word = state.catalan,
            hasPicture = state.imagePath != null,
            onDismiss = { confirmingDelete = false },
            onConfirm = {
                confirmingDelete = false
                viewModel.onDelete()
            },
        )
    }
}

/** What to say under a meaning's field, if anything. */
@Composable
private fun meaningNote(field: Suggested, status: MeaningSuggestion, source: MeaningSource): String? = when {
    field.isSuggestion && field.text.isNotBlank() && source == MeaningSource.DICTIONARY ->
        stringResource(R.string.flashcards_meaning_from_dictionary)
    status == MeaningSuggestion.DOWNLOADING -> stringResource(R.string.flashcards_meaning_downloading)
    field.isSuggestion && field.text.isNotBlank() -> stringResource(R.string.flashcards_meaning_suggested)
    status == MeaningSuggestion.NEEDS_MODEL && field.text.isBlank() ->
        stringResource(R.string.flashcards_meaning_needs_model)
    else -> null
}

/**
 * A group of fields on its own quiet surface, with a small heading when the
 * group needs one, so the form reads as three parts rather than six boxes.
 */
@Composable
private fun FormSection(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(Space.lg),
    ) {
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = Space.sm),
            )
        }
        content()
    }
}

@Composable
private fun LanguageFlag(@DrawableRes res: Int) {
    Image(
        painter = painterResource(res),
        contentDescription = null,
        modifier = Modifier
            .size(width = 24.dp, height = 16.dp)
            .clip(RoundedCornerShape(3.dp)),
    )
}

/**
 * The picture on the card, large, with who made it under it and a way to
 * choose another over its corner. Fit rather than crop: this is where the
 * reader checks the whole picture came in.
 */
@Composable
private fun ChosenPicture(path: String, credit: String?, onChange: () -> Unit) {
    Box {
        // The picture is the button, and nothing is drawn on it to say so.
        //
        // It had a filled *Choose another*, which became a small badge with a
        // refresh mark in it, which is still something sitting on the corner of
        // the picture explaining the picture. A picture on a form is the one
        // thing everybody already expects to be able to press. The words are
        // kept for whoever is listening rather than looking.
        FramedPicture(
            path = path,
            contentDescription = stringResource(R.string.flashcards_picture_other),
            kind = PictureResults.kindOf(credit),
            maxEdge = ImageSizing.MAX_EDGE,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(FieldShape)
                .clickable(
                    onClickLabel = stringResource(R.string.flashcards_picture_other),
                    onClick = onChange,
                ),
        )
    }
    credit?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.xs),
        )
    }
}

private val FieldShape = RoundedCornerShape(14.dp)

/**
 * For Catalan and Romanian words alike: no capital forced on the first letter,
 * since most words on a card are not names, and no autocorrect, which on a
 * keyboard set to one language "corrects" every word of the other.
 */
private val WordKeyboard = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    autoCorrectEnabled = false,
    imeAction = ImeAction.Next,
)
