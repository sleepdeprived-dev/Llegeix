package com.david.llegeix.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.david.llegeix.R
import com.david.llegeix.data.settings.TranslationTarget
import com.david.llegeix.lang.WordReference

/**
 * How far a lookup in the bundled references has got.
 *
 * [CLOSED] is a real state rather than a null: in the reader the panel stays
 * shut until it is asked for, because the files are sixteen megabytes together
 * and most lookups never want them. The dictionary screen opens it immediately,
 * since a definition is the whole reason that screen exists.
 */
enum class DictionaryStatus { CLOSED, LOADING, READY }

/** What the bundled references had to say, and whether they have answered yet. */
data class DictionaryState(
    val status: DictionaryStatus = DictionaryStatus.CLOSED,
    val entries: List<WordReference> = emptyList(),
)

/**
 * The pieces a word is described with, shared by the reader's lookup sheet and
 * the dictionary screen.
 *
 * They were written for the sheet and then wanted whole by the screen. Kept in
 * one place rather than copied, because "what a word looks like when the app
 * explains it" is one answer and should stay one answer: a change to the way a
 * definition is capped or a pronunciation is marked has to reach both, or the
 * same word starts looking like two different words depending on where it was
 * asked about.
 */

/**
 * The two-flag switch for the translation language.
 *
 * Flags rather than words because this sits in the corner of a panel whose
 * subject is a word: two more words there would compete with it. Both are
 * always visible, so switching is one tap and the current choice is not
 * something to work out.
 */
@Composable
fun TranslationTargetFlags(target: TranslationTarget, onPick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TranslationTarget.entries.forEach { entry ->
            val selected = entry == target
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        },
                    )
                    .clickable(enabled = !selected, onClick = onPick)
                    .padding(horizontal = Space.sm, vertical = 6.dp),
            ) {
                Image(
                    painter = painterResource(entry.flagRes),
                    contentDescription = stringResource(entry.switchRes),
                    modifier = Modifier
                        .size(width = 22.dp, height = 15.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .alpha(if (selected) 1f else 0.4f),
                )
            }
        }
    }
}

/**
 * The dictionary panel: what the word means, and what else it could be.
 *
 * The order is the order of usefulness. The definition comes first, in Catalan,
 * because someone reading Catalan to learn it is better served by a Catalan
 * definition than by a second translation. Synonyms and antonyms follow as
 * quieter, labelled blocks.
 *
 * Everything is capped. A word like *gran* has six senses and forty synonyms in
 * the sources, and printing all of them would turn a quick check into a wall of
 * text — which is exactly the thing this app exists not to do.
 */
@Composable
fun DictionaryCard(
    entry: DictionaryState,
    /** What the reader actually asked about, so the card can avoid repeating it. */
    selected: String,
    modifier: Modifier = Modifier,
) {
    DetailCard(title = stringResource(R.string.lookup_dictionary), modifier = modifier) {
        when {
            entry.status == DictionaryStatus.LOADING -> Text(
                text = stringResource(R.string.lookup_dictionary_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            entry.entries.isEmpty() -> Text(
                text = stringResource(R.string.lookup_dictionary_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> entry.entries.forEachIndexed { index, word ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        modifier = Modifier.padding(vertical = Space.lg),
                    )
                }
                // Naming the word again is only worth a line when it is not the
                // word already set in headline type at the top: a phrase's
                // several entries, or a plural answered by its lemma.
                if (entry.entries.size > 1 || word.headword != selected.lowercase()) {
                    Text(
                        text = word.headword,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = Space.sm),
                    )
                }

                word.definitions.take(MAX_PARTS_OF_SPEECH).forEachIndexed { part, definition ->
                    if (part > 0) Spacer(modifier = Modifier.height(Space.md))
                    partOfSpeechLabel(definition.partOfSpeech)?.let { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    definition.meanings.take(MAX_MEANINGS).forEach { meaning ->
                        Text(
                            text = meaning,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = Space.xs),
                        )
                    }
                }

                if (word.senses.isNotEmpty()) {
                    DictionarySection(
                        label = stringResource(R.string.lookup_synonyms),
                        // Two groups, not one. The groups are separate senses
                        // and the sources do not agree on their order, so
                        // showing only the first can print the synonyms for
                        // "heap" under the definition of "mountain".
                        lines = word.senses
                            .take(MAX_SENSE_GROUPS)
                            .map { it.words.take(MAX_SYNONYMS_SHOWN).joinToString(" · ") },
                        topPadding = if (word.definitions.isEmpty()) Space.xs else Space.lg,
                    )
                }
                if (word.antonyms.isNotEmpty()) {
                    DictionarySection(
                        label = stringResource(R.string.lookup_antonyms),
                        lines = listOf(
                            word.antonyms.take(MAX_SYNONYMS_SHOWN).joinToString(" · "),
                        ),
                        topPadding = Space.lg,
                    )
                }
            }
        }
    }
}

/** A labelled block of words inside the dictionary card. */
@Composable
private fun DictionarySection(label: String, lines: List<String>, topPadding: Dp) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = topPadding),
    )
    lines.forEach { line ->
        Text(
            text = line,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.xs),
        )
    }
}

/** The dictionary's part-of-speech code, in the reader's own language. */
@Composable
private fun partOfSpeechLabel(code: String): String? = when (code) {
    "nom" -> stringResource(R.string.pos_nom)
    "verb" -> stringResource(R.string.pos_verb)
    "adj" -> stringResource(R.string.pos_adj)
    "adv" -> stringResource(R.string.pos_adv)
    "interj" -> stringResource(R.string.pos_interj)
    "loc" -> stringResource(R.string.pos_loc)
    else -> null
}

/**
 * The pronunciation line.
 *
 * A trailing marker when the transcription had to guess: Catalan spelling does
 * not record whether a stressed e or o is close or open, so saying so is more
 * useful than quietly presenting a guess as fact.
 */
@Composable
fun IpaLine(ipa: String, isApproximate: Boolean, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "[$ipa]",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (isApproximate) {
            Text(
                text = stringResource(R.string.lookup_ipa_approximate),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Space.sm),
            )
        }
    }
}

/** A quiet block of supporting detail under the answer. */
@Composable
fun DetailCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(Space.lg),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = Space.sm),
        )
        content()
    }
}

/** Parts of speech shown before the card becomes a grammar lesson. */
private const val MAX_PARTS_OF_SPEECH = 2

/** Meanings shown per part of speech. */
private const val MAX_MEANINGS = 2

/** Synonym groups shown, each one being a different sense of the word. */
private const val MAX_SENSE_GROUPS = 2

/** Words shown per group, which is about one comfortable line and a half. */
private const val MAX_SYNONYMS_SHOWN = 6
