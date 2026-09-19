package com.david.llegeix.ui.flashcards

import androidx.activity.compose.BackHandler
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
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen, vertical = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            // The two sides of the card, each marked with its flag: the same
            // mark the practice switch uses for the same two languages, so the
            // card and the way it is asked read as one thing.
            FormSection {
                OutlinedTextField(
                    value = state.catalan,
                    onValueChange = viewModel::onCatalanChange,
                    label = { Text(stringResource(R.string.flashcards_field_catalan)) },
                    textStyle = MaterialTheme.typography.titleLarge,
                    singleLine = true,
                    leadingIcon = { LanguageFlag(R.drawable.ic_flag_ca) },
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
                    label = { Text(stringResource(R.string.flashcards_field_ipa)) },
                    singleLine = true,
                    prefix = { Text("[") },
                    suffix = { Text("]") },
                    supportingText = when {
                        state.ipa.text.isBlank() -> null
                        state.ipa.isSuggestion && state.ipa.isApproximate -> {
                            { Text(stringResource(R.string.flashcards_ipa_approximate)) }
                        }

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

            FormSection {
                OutlinedTextField(
                    value = state.romanian.text,
                    onValueChange = viewModel::onRomanianChange,
                    label = { Text(stringResource(R.string.flashcards_field_romanian)) },
                    textStyle = MaterialTheme.typography.titleLarge,
                    singleLine = true,
                    leadingIcon = { LanguageFlag(R.drawable.ic_flag_ro) },
                    supportingText = meaningNote(state)?.let { note -> { Text(note) } },
                    keyboardOptions = WordKeyboard.copy(imeAction = ImeAction.Done),
                    shape = FieldShape,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            FormSection {
                PictureSection(
                    state = state,
                    onPickSuggestion = viewModel::onPickSuggestion,
                    onSourceChange = viewModel::onPictureSourceChange,
                    onRetry = viewModel::onRetryPictures,
                    onPickFromGallery = openPicker,
                    onRemove = viewModel::onRemoveImage,
                )
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

/** What to say under the Romanian field, if anything. */
@Composable
private fun meaningNote(state: CardEditorUiState): String? = when {
    state.meaningSuggestion == MeaningSuggestion.DOWNLOADING ->
        stringResource(R.string.flashcards_meaning_downloading)

    state.romanian.isSuggestion && state.romanian.text.isNotBlank() ->
        stringResource(R.string.flashcards_meaning_suggested)

    state.meaningSuggestion == MeaningSuggestion.NEEDS_MODEL && state.romanian.text.isBlank() ->
        stringResource(R.string.flashcards_meaning_needs_model)

    else -> null
}

/** A group of fields on its own quiet surface, so the form reads as three parts, not six boxes. */
@Composable
private fun FormSection(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(Space.lg),
        content = content,
    )
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
 * The card's picture: the one chosen, or a row of suggestions to choose from.
 *
 * Suggestions are offered, never placed. A picture of the wrong sense of a
 * word — *banc* the bench for *banc* the bank — is worse than none on a card
 * meant to be learned from, so nothing reaches the card without a tap. Once
 * one is chosen the row makes way for it, and says who made it.
 */
@Composable
private fun PictureSection(
    state: CardEditorUiState,
    onPickSuggestion: (PictureHit) -> Unit,
    onSourceChange: (PictureSource) -> Unit,
    onRetry: () -> Unit,
    onPickFromGallery: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.flashcards_picture),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        if (state.imagePath == null) {
            SourceSwitch(selected = state.pictureSource, onSelect = onSourceChange)
        }
    }

    val imagePath = state.imagePath
    when {
        state.isImporting -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.md)
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        imagePath != null -> {
            // Fit rather than crop: this is where the reader checks the whole
            // picture came in, so none of it is cut away here.
            CardImage(
                path = imagePath,
                maxEdge = ImageSizing.MAX_EDGE,
                contentDescription = stringResource(R.string.flashcards_picture),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .padding(top = Space.md)
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(16.dp)),
            )
            state.imageCredit?.let { credit ->
                Text(
                    text = credit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.xs),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                modifier = Modifier.padding(top = Space.sm),
            ) {
                OutlinedButton(onClick = onRemove) {
                    Text(stringResource(R.string.flashcards_picture_other))
                }
            }
        }

        else -> {
            Suggestions(
                state = state,
                onPick = onPickSuggestion,
                onRetry = onRetry,
            )
            Text(
                text = stringResource(
                    when (state.pictureSource) {
                        PictureSource.PICTOGRAMS -> R.string.flashcards_pictures_credit_pictograms
                        PictureSource.PHOTOS -> R.string.flashcards_pictures_credit_photos
                    },
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.sm),
            )
            OutlinedButton(
                onClick = onPickFromGallery,
                modifier = Modifier.padding(top = Space.md),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(Space.sm))
                Text(stringResource(R.string.flashcards_picture_from_gallery))
            }
        }
    }
}

/** Pictograms or photos: two small chips, the one in use lit. */
@Composable
private fun SourceSwitch(selected: PictureSource, onSelect: (PictureSource) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        PictureSource.entries.forEach { source ->
            FilterChip(
                selected = source == selected,
                onClick = { onSelect(source) },
                label = {
                    Text(
                        stringResource(
                            when (source) {
                                PictureSource.PICTOGRAMS -> R.string.flashcards_pictures_pictograms
                                PictureSource.PHOTOS -> R.string.flashcards_pictures_photos
                            },
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun Suggestions(
    state: CardEditorUiState,
    onPick: (PictureHit) -> Unit,
    onRetry: () -> Unit,
) {
    val pictures = state.pictures
    val showingCurrent = pictures.source == state.pictureSource
    when {
        pictures.status == PictureStatus.SEARCHING -> LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            userScrollEnabled = false,
            modifier = Modifier.padding(top = Space.md),
        ) {
            // Tiles standing in for the answer, so the row does not jump
            // from a line of text to a row of pictures when it arrives.
            items(4) { Box(Modifier.tile().background(MaterialTheme.colorScheme.surfaceContainerHighest)) }
        }

        pictures.status == PictureStatus.FOUND && showingCurrent -> LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            modifier = Modifier.padding(top = Space.md),
        ) {
            items(pictures.hits, key = { it.source.name + it.id }) { hit ->
                SuggestionTile(
                    hit = hit,
                    isFetching = state.fetchingPicture == hit.id,
                    enabled = state.fetchingPicture == null,
                    onClick = { onPick(hit) },
                )
            }
        }

        else -> {
            val note = when (pictures.status) {
                PictureStatus.WAITING -> stringResource(R.string.flashcards_pictures_waiting)
                PictureStatus.NONE_FOUND -> stringResource(
                    when (state.pictureSource) {
                        PictureSource.PICTOGRAMS -> R.string.flashcards_pictures_none_pictograms
                        PictureSource.PHOTOS -> R.string.flashcards_pictures_none_photos
                    },
                    pictures.word,
                )
                PictureStatus.OFFLINE -> stringResource(R.string.flashcards_pictures_offline)
                PictureStatus.NEEDS_ENGLISH -> stringResource(R.string.flashcards_pictures_needs_english)
                else -> null
            }
            note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.md),
                )
            }
            if (pictures.status == PictureStatus.OFFLINE) {
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.flashcards_pictures_retry))
                }
            }
        }
    }
}

@Composable
private fun SuggestionTile(
    hit: PictureHit,
    isFetching: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val search = remember(context) { (context.applicationContext as LlegeixApp).pictureSearch }
    val thumbnail by produceState<ImageBitmap?>(initialValue = null, hit.thumbnailUrl) {
        value = search.thumbnail(hit.thumbnailUrl)?.asImageBitmap()
    }
    // Pictograms are drawn on white, and read best on it in either theme;
    // a photo fills its tile.
    val isPictogram = hit.source == PictureSource.PICTOGRAMS
    Box(
        modifier = Modifier.tile()
            .background(
                if (isPictogram) Color.White else MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        thumbnail?.let {
            Image(
                bitmap = it,
                contentDescription = stringResource(R.string.flashcards_picture_use),
                contentScale = if (isPictogram) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isPictogram) Space.sm else 0.dp),
            )
        }
        if (isFetching) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
            }
        }
    }
}

/** A suggestion's square, the same size whether it holds a picture or is waiting for one. */
private fun Modifier.tile(): Modifier = size(TileSize).clip(RoundedCornerShape(14.dp))

private val TileSize = 96.dp

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
