package com.david.llegeix.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import com.david.llegeix.R
import com.david.llegeix.data.db.dao.DocumentTag
import com.david.llegeix.data.db.entity.TagEntity

/**
 * The tags on a document, as they appear in a list.
 *
 * Deliberately set apart from whatever action sits at the end of the row. A
 * coloured chip immediately beside a dismiss button reads as part of it, and the
 * two mean completely different things — one is a label, the other destroys
 * something. The gap is the whole design.
 *
 * At most [maxVisible] are drawn and the rest become a count, because the row
 * belongs to the document's title and a tag list is allowed to be long.
 */
@Composable
fun TagStrip(
    tags: List<DocumentTag>,
    modifier: Modifier = Modifier,
    maxVisible: Int = 1,
    maxWidth: Dp = 132.dp,
) {
    if (tags.isEmpty()) return

    Row(
        // Capped, because the title owns this row. Left to size itself the
        // strip takes the width it wants and the document's name wraps to two
        // lines to get out of its way, which is backwards.
        modifier = modifier
            .widthIn(max = maxWidth)
            .padding(end = Space.md),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tags.take(maxVisible).forEach { tag ->
            TagChip(name = tag.name, color = Color(tag.colorArgb))
        }
        if (tags.size > maxVisible) {
            Text(
                text = stringResource(R.string.tags_overflow, tags.size - maxVisible),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TagChip(name: String, color: Color) {
    val surface = MaterialTheme.colorScheme.surface
    // Composited here rather than left as an alpha, so the contrast the label
    // was chosen against is the contrast that actually gets drawn.
    val chipBackground = tagChipBackground(color, surface)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(chipBackground)
            .padding(horizontal = Space.sm, vertical = 3.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = tagLabelColor(color, surface),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 84.dp),
        )
    }
}

/**
 * Darkens or lightens a tag colour until its label can actually be read on
 * [background], keeping the hue the reader chose.
 *
 * Measured rather than guessed. A fixed 35% darkening was the first attempt and
 * it left a yellow tag's label at 3.06:1 — below the 4.5:1 that small text
 * needs — while a purple one was comfortably past it. The same nudge cannot
 * work for every hue, because hues do not start at the same brightness.
 */
private fun Color.readableOn(background: Color): Color {
    val goDarker = background.relativeLuminance() > 0.5f
    var candidate = this
    repeat(MAX_CONTRAST_STEPS) {
        if (contrastAgainst(candidate, background) >= MIN_LABEL_CONTRAST) return candidate
        candidate = if (goDarker) candidate.darken(0.12f) else candidate.lighten(0.12f)
    }
    return candidate
}

private const val MIN_LABEL_CONTRAST = 4.5f
private const val MAX_CONTRAST_STEPS = 14

/** The chip's ground: a wash of its own colour over whatever is behind it. */
internal fun tagChipBackground(tag: Color, surface: Color): Color =
    Color(
        red = tag.red * TAG_WASH + surface.red * (1f - TAG_WASH),
        green = tag.green * TAG_WASH + surface.green * (1f - TAG_WASH),
        blue = tag.blue * TAG_WASH + surface.blue * (1f - TAG_WASH),
    )

private const val TAG_WASH = 0.18f

/** Exposed so the palette can be checked against both themes in a test. */
internal fun tagLabelColor(tag: Color, surface: Color): Color =
    tag.readableOn(tagChipBackground(tag, surface))

internal fun contrastAgainst(a: Color, b: Color): Float {
    val la = a.relativeLuminance()
    val lb = b.relativeLuminance()
    return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
}

private fun Color.relativeLuminance(): Float {
    fun channel(value: Float): Float =
        if (value <= 0.03928f) value / 12.92f else ((value + 0.055f) / 1.055f).pow(2.4f)
    return 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
}

private fun Color.darken(amount: Float): Color =
    Color(red * (1 - amount), green * (1 - amount), blue * (1 - amount), alpha)

private fun Color.lighten(amount: Float): Color = Color(
    red + (1f - red) * amount,
    green + (1f - green) * amount,
    blue + (1f - blue) * amount,
    alpha,
)

/** The palette offered when making a tag; the same one folders use. */
private val TagPalette: List<Int> = HighlightColors.palette

/**
 * Pick which tags are on a document, and make new ones without leaving.
 *
 * Creating and assigning are the same screen on purpose: the first tag a reader
 * ever makes is made because they want it on the document in front of them, and
 * sending them elsewhere to create it first would lose the thread.
 */
@Composable
fun TagPickerDialog(
    documentTitle: String,
    allTags: List<TagEntity>,
    selectedIds: Set<Long>,
    onToggle: (TagEntity) -> Unit,
    onCreate: (String, Int) -> Unit,
    onRecolour: (TagEntity, Int) -> Unit,
    onDelete: (TagEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    var colorIndex by remember { mutableIntStateOf(0) }
    // Which existing tag has its palette open, if any.
    var recolouring by remember { mutableStateOf<TagEntity?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tags_title)) },
        text = {
            Column {
                Text(
                    text = documentTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (allTags.isEmpty()) {
                    Text(
                        text = stringResource(R.string.tags_none_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = Space.lg),
                    )
                } else {
                    Column(modifier = Modifier.padding(top = Space.md)) {
                        allTags.forEach { tag ->
                            TagRow(
                                tag = tag,
                                selected = tag.id in selectedIds,
                                paletteOpen = recolouring?.id == tag.id,
                                onToggle = { onToggle(tag) },
                                onOpenPalette = {
                                    recolouring = if (recolouring?.id == tag.id) null else tag
                                },
                                onPickColour = { colour ->
                                    onRecolour(tag, colour)
                                    recolouring = null
                                },
                                onDelete = { onDelete(tag) },
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.padding(top = Space.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(TagPalette[colorIndex]))
                            .clickable { colorIndex = (colorIndex + 1) % TagPalette.size },
                    )
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(R.string.tags_new_name)) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = Space.md),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(newName, TagPalette[colorIndex])
                    newName = ""
                },
                enabled = newName.isNotBlank(),
            ) {
                Text(stringResource(R.string.tags_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        },
    )
}

/**
 * One tag in the picker.
 *
 * The dot does two jobs, deliberately separated from the row: tapping the row
 * puts the tag on the document or takes it off, and tapping the *dot* opens the
 * palette to recolour it. Choosing a colour you regret should not mean deleting
 * the tag and making it again.
 */
@Composable
private fun TagRow(
    tag: TagEntity,
    selected: Boolean,
    paletteOpen: Boolean,
    onToggle: () -> Unit,
    onOpenPalette: () -> Unit,
    onPickColour: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    val changeColourLabel = stringResource(R.string.tags_change_colour, tag.name)
    val pickColourLabel = stringResource(R.string.tags_pick_colour)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onOpenPalette)
                    .semantics {
                        contentDescription = changeColourLabel
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(tag.colorArgb))
                        .border(
                            width = if (paletteOpen) 2.dp else 0.dp,
                            color = MaterialTheme.colorScheme.onSurface,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.Black.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            Text(
                text = tag.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Space.md),
            )
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.tags_delete, tag.name),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        if (paletteOpen) {
            // The fixed palette only. A tag is a glance-level marker in a list;
            // the hex mixer belongs to the app's accent, where one colour is
            // worth fussing over.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Space.xl, bottom = Space.sm),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                TagPalette.forEach { argb ->
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .border(
                                width = if (argb == tag.colorArgb) 2.dp else 1.dp,
                                color = if (argb == tag.colorArgb) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = CircleShape,
                            )
                            .clickable { onPickColour(argb) }
                            .semantics {
                                contentDescription = pickColourLabel
                            },
                    )
                }
            }
        }
    }
}
