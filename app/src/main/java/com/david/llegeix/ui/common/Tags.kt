package com.david.llegeix.ui.common

import androidx.compose.foundation.background
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = Space.sm, vertical = 3.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            // The tag's own colour on a wash of itself: legible in either theme
            // without needing a second colour chosen for it.
            color = color.readableOn(MaterialTheme.colorScheme.surface),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 84.dp),
        )
    }
}

/**
 * Nudges a tag colour until it can be read against [background].
 *
 * A reader picking a pale yellow should still be able to read the label; this
 * darkens or lightens the text without moving the hue they chose.
 */
@Composable
private fun Color.readableOn(background: Color): Color {
    val isLight = background.luminanceApprox() > 0.5f
    return if (isLight) darken(0.35f) else lighten(0.35f)
}

private fun Color.luminanceApprox(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

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
    onDelete: (TagEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    var colorIndex by remember { mutableIntStateOf(0) }

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
                                onToggle = { onToggle(tag) },
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

@Composable
private fun TagRow(
    tag: TagEntity,
    selected: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color(tag.colorArgb)),
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
}
