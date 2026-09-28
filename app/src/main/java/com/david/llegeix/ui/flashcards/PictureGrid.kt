package com.david.llegeix.ui.flashcards

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.david.llegeix.resources.*
import com.david.llegeix.ui.common.SearchField
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.david.llegeix.LlegeixApp
import com.david.llegeix.R
import com.david.llegeix.data.flashcards.PictureHit
import com.david.llegeix.data.flashcards.PictureSource
import com.david.llegeix.ui.common.Pill
import com.david.llegeix.ui.common.PillGroup
import com.david.llegeix.ui.common.Space
import org.jetbrains.compose.resources.stringResource

/**
 * Choosing a picture: a switch between pictograms, photos and emoji, a search
 * with a way to add a photo of the reader's own beside it, and a grid of what
 * was found.
 *
 * A grid of three across rather than a strip, so nine choices are seen at once
 * without flicking sideways past most of them. The reader's own photo is a
 * round button at the end of the search row, where "look for another, or use
 * mine" is one line — it used to be the grid's first square, labelled in small
 * type, where it read as one more result.
 */
@Composable
fun PictureGrid(
    suggestions: PictureSuggestions,
    source: PictureSource,
    onSourceChange: (PictureSource) -> Unit,
    onPick: (PictureHit) -> Unit,
    onPickOwn: () -> Unit,
    onRetry: () -> Unit,
    /** Search for exactly this instead of the word, from the field over the grid. */
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SourceSwitch(selected = source, onSelect = onSourceChange)

        // What was searched, and room to search for something else: the
        // word's own pictures are not always the ones wanted, and a grid with
        // no way to ask again is a grid you can only accept or leave. It
        // shows the word as the reader wrote it, never the English photos are
        // looked up with. Submitted with the keyboard's search key; each kind
        // of picture is searched for the same text.
        var text by remember(suggestions.searched) { mutableStateOf(suggestions.searched) }
        val focus = LocalFocusManager.current
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.md),
        ) {
            SearchField(
                query = text,
                placeholder = stringResource(Res.string.flashcards_pictures_search),
                onQueryChange = { text = it },
                onSubmit = {
                    focus.clearFocus()
                    onSearch(text)
                },
                modifier = Modifier.weight(1f),
            )
            FilledTonalIconButton(
                onClick = onPickOwn,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add_photo),
                    contentDescription = stringResource(Res.string.flashcards_picture_from_gallery),
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        val showing = suggestions.source == source
        val hits = if (showing && suggestions.status == PictureStatus.FOUND) suggestions.hits else emptyList()

        // What was found, then grey squares while a search is on its way —
        // so the grid has its shape before the answer arrives, and nothing
        // jumps when it does.
        val tiles = buildList<@Composable (Modifier) -> Unit> {
            // Every picture the search came back with, from every source: a
            // grid that stopped at a round number was throwing away pictures
            // the services had already found.
            hits.forEach { hit ->
                add { m ->
                    SuggestionTile(
                        hit = hit,
                        isFetching = suggestions.fetching == hit.id,
                        enabled = suggestions.fetching == null,
                        onClick = { onPick(hit) },
                        modifier = m,
                    )
                }
            }
            repeat(if (showing && suggestions.status == PictureStatus.SEARCHING) PLACEHOLDERS else 0) {
                add { m -> Box(m.background(MaterialTheme.colorScheme.surfaceContainerHighest)) }
            }
        }
        if (tiles.isNotEmpty()) Column(
            verticalArrangement = Arrangement.spacedBy(Space.sm),
            modifier = Modifier.padding(top = Space.md),
        ) {
            tiles.chunked(COLUMNS).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    row.forEach { tile -> tile(Modifier.weight(1f).aspectRatio(1f).clip(TileShape)) }
                    repeat(COLUMNS - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }

        StatusNote(suggestions = suggestions, source = source, onRetry = onRetry)

        Text(
            text = stringResource(
                when (source) {
                    PictureSource.PICTOGRAMS -> Res.string.flashcards_pictures_credit_pictograms
                    PictureSource.PHOTOS -> Res.string.flashcards_pictures_credit_photos
                    PictureSource.EMOJI -> Res.string.flashcards_pictures_credit_emoji
                },
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.sm),
        )
    }
}

/**
 * Pictograms or photos, as one control across the width with the one in use
 * lit in the accent — not two loose chips that read as two separate filters.
 */
@Composable
private fun SourceSwitch(selected: PictureSource, onSelect: (PictureSource) -> Unit) {
    PillGroup(modifier = Modifier.fillMaxWidth()) {
        PictureSource.entries.forEach { source ->
            val name = stringResource(
                when (source) {
                    PictureSource.PICTOGRAMS -> Res.string.flashcards_pictures_pictograms
                    PictureSource.PHOTOS -> Res.string.flashcards_pictures_photos
                    PictureSource.EMOJI -> Res.string.flashcards_pictures_emoji
                },
            )
            Pill(
                selected = source == selected,
                onClick = { onSelect(source) },
                label = name,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = Space.xs, vertical = Space.sm),
            ) {
                // Icon over the name, so three of them fit across a phone
                // with every name whole.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(
                            when (source) {
                                PictureSource.PICTOGRAMS -> R.drawable.ic_pictogram
                                PictureSource.PHOTOS -> R.drawable.ic_photo
                                PictureSource.EMOJI -> R.drawable.ic_emoji
                            },
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/** A line under the grid when there is something to say instead of pictures. */
@Composable
private fun StatusNote(suggestions: PictureSuggestions, source: PictureSource, onRetry: () -> Unit) {
    if (suggestions.source != source) return
    val note = when (suggestions.status) {
        PictureStatus.WAITING -> stringResource(Res.string.flashcards_pictures_waiting)
        PictureStatus.NONE_FOUND -> stringResource(Res.string.flashcards_pictures_none)
        PictureStatus.OFFLINE -> stringResource(Res.string.flashcards_pictures_offline)
        PictureStatus.BLOCKED -> stringResource(Res.string.flashcards_pictures_blocked)
        PictureStatus.SEARCHING, PictureStatus.FOUND -> null
    } ?: return
    // Where the grid would be, centred, so an empty search reads as an
    // answer rather than as a line of small print under nothing.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.md)
            .clip(TileShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = Space.lg, vertical = Space.xl),
    ) {
        Text(
            text = note,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (suggestions.status == PictureStatus.OFFLINE) {
            TextButton(onClick = onRetry) { Text(stringResource(Res.string.flashcards_pictures_retry)) }
        }
    }
}

@Composable
private fun SuggestionTile(
    hit: PictureHit,
    isFetching: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val search = remember(context) { (context.applicationContext as LlegeixApp).pictureSearch }
    val thumbnail by produceState<ImageBitmap?>(initialValue = null, hit.thumbnailUrl) {
        value = search.thumbnail(hit.thumbnailUrl)?.asImageBitmap()
    }
    // A pictogram on white, the way it will look on the card once chosen
    // (see CardImage); a photo fills its square.
    val isPictogram = hit.source == PictureSource.PICTOGRAMS
    val isEmoji = hit.source == PictureSource.EMOJI
    Box(
        modifier = modifier
            .background(
                when {
                    isPictogram -> PictogramPaper
                    isEmoji -> MaterialTheme.colorScheme.surfaceContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                },
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        thumbnail?.let {
            Image(
                bitmap = it,
                contentDescription = stringResource(Res.string.flashcards_picture_use),
                contentScale = if (isPictogram || isEmoji) ContentScale.Fit else ContentScale.Crop,
                // Scaled smoothly, so a small source image is not left blocky.
                filterQuality = FilterQuality.High,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        when {
                            isPictogram -> Space.sm
                            isEmoji -> Space.md
                            else -> 0.dp
                        },
                    ),
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

private const val COLUMNS = 3

/** Grey squares while a search is on its way: two rows' worth. */
private const val PLACEHOLDERS = 6

private val TileShape = RoundedCornerShape(14.dp)
