package com.david.llegeix.ui.flashcards

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.david.llegeix.LlegeixApp
import com.david.llegeix.R
import com.david.llegeix.data.flashcards.PictureHit
import com.david.llegeix.data.flashcards.PictureSource
import com.david.llegeix.ui.common.Space

/**
 * Choosing a picture: a switch between pictograms and photos, and a grid of
 * what was found, with the reader's own photos as the first square.
 *
 * A grid of three across rather than a strip, so nine choices are seen at once
 * without flicking sideways past most of them; and the reader's own photos in
 * the grid rather than as a button under it, because "one of these, or one of
 * mine" is one choice, made in one place.
 */
@Composable
fun PictureGrid(
    suggestions: PictureSuggestions,
    source: PictureSource,
    onSourceChange: (PictureSource) -> Unit,
    onPick: (PictureHit) -> Unit,
    onPickOwn: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SourceSwitch(selected = source, onSelect = onSourceChange)

        val showing = suggestions.source == source
        val hits = if (showing && suggestions.status == PictureStatus.FOUND) suggestions.hits else emptyList()
        val placeholders = if (showing && suggestions.status == PictureStatus.SEARCHING) 5 else 0

        // The reader's own photo first, then what was found, then grey squares
        // while a search is on its way — so the grid has its shape before the
        // answer arrives, and nothing jumps when it does.
        val tiles = buildList<@Composable (Modifier) -> Unit> {
            add { m -> OwnPhotoTile(onPickOwn, m) }
            hits.take(8).forEach { hit ->
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
            repeat(placeholders) {
                add { m -> Box(m.background(MaterialTheme.colorScheme.surfaceContainerHighest)) }
            }
        }
        Column(
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
                    PictureSource.PICTOGRAMS -> R.string.flashcards_pictures_credit_pictograms
                    PictureSource.PHOTOS -> R.string.flashcards_pictures_credit_photos
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceSwitch(selected: PictureSource, onSelect: (PictureSource) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        PictureSource.entries.forEachIndexed { index, source ->
            SegmentedButton(
                selected = source == selected,
                onClick = { onSelect(source) },
                shape = SegmentedButtonDefaults.itemShape(index, PictureSource.entries.size),
                colors = brightSegmentColors(),
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

/** A line under the grid when there is something to say instead of pictures. */
@Composable
private fun StatusNote(suggestions: PictureSuggestions, source: PictureSource, onRetry: () -> Unit) {
    if (suggestions.source != source) return
    val note = when (suggestions.status) {
        PictureStatus.WAITING -> stringResource(R.string.flashcards_pictures_waiting)
        PictureStatus.NONE_FOUND -> stringResource(
            when (source) {
                PictureSource.PICTOGRAMS -> R.string.flashcards_pictures_none_pictograms
                PictureSource.PHOTOS -> R.string.flashcards_pictures_none_photos
            },
            suggestions.word,
        )
        PictureStatus.OFFLINE -> stringResource(R.string.flashcards_pictures_offline)
        PictureStatus.NO_ENGLISH -> stringResource(R.string.flashcards_pictures_no_english, suggestions.word)
        PictureStatus.BLOCKED -> stringResource(R.string.flashcards_pictures_blocked)
        PictureStatus.SEARCHING, PictureStatus.FOUND -> null
    } ?: return
    Text(
        text = note,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Space.md),
    )
    if (suggestions.status == PictureStatus.OFFLINE) {
        TextButton(onClick = onRetry) { Text(stringResource(R.string.flashcards_pictures_retry)) }
    }
}

@Composable
private fun OwnPhotoTile(onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, TileShape)
            .clickable(onClick = onClick)
            .padding(Space.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = stringResource(R.string.flashcards_picture_from_gallery),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Space.xs),
        )
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
    // Pictograms are drawn on white, and read best on it in either theme; a
    // photo fills its square.
    val isPictogram = hit.source == PictureSource.PICTOGRAMS
    Box(
        modifier = modifier
            .background(if (isPictogram) Color.White else MaterialTheme.colorScheme.surfaceContainerHighest)
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

/**
 * The accent for the chosen segment, with the label in the colour meant for
 * it — bright and plainly "on", in the dark theme as much as the light, where
 * the chosen segment used to be the darkest thing on the panel.
 */
@Composable
fun brightSegmentColors() = SegmentedButtonDefaults.colors(
    activeContainerColor = MaterialTheme.colorScheme.primary,
    activeContentColor = MaterialTheme.colorScheme.onPrimary,
    activeBorderColor = MaterialTheme.colorScheme.primary,
    inactiveContainerColor = Color.Transparent,
    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant,
)

private const val COLUMNS = 3

private val TileShape = RoundedCornerShape(14.dp)
