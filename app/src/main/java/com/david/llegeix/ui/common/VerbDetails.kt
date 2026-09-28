package com.david.llegeix.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.david.llegeix.lang.Conjugation
import com.david.llegeix.lang.Mood
import com.david.llegeix.lang.NonFiniteForm
import com.david.llegeix.lang.Person
import com.david.llegeix.lang.Tense
import com.david.llegeix.lang.VerbForm
import com.david.llegeix.lang.VerbReading
import com.david.llegeix.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * What a verb form is, spelled out.
 *
 * A learner who meets *cantéssim* and looks it up was being answered with a
 * translation and, if the inflected-forms file happened to have it, a
 * definition quietly filed under a different word. The three facts they needed
 * were all missing: that it is a verb, which verb, and which part of it. This
 * is those three facts, in that order, plus what the verb means — because "it
 * is the imperfect subjunctive of *cantar*" is only half an answer to somebody
 * who does not yet know what *cantar* is.
 *
 * Bare content with no background of its own, because it has to sit inside two
 * quite different containers: the dictionary's own card, and the reader's
 * lookup sheet, where every supporting section is a [DetailCard] and one panel
 * drawn to its own taste would stand out as a mistake. Both get the same words
 * in the same order, which is the point — a word tapped on a page and the same
 * word typed into the dictionary must not look like two different words.
 *
 * @param onOpenInfinitive makes the infinitive a row you can press, which looks
 *   it up properly. Null where there is nowhere to go, as in the reader, where
 *   the dictionary is another tab and another document away — and a row that
 *   looks pressable and is not is worse than one that never offered.
 */
@Composable
fun VerbDetails(
    verb: VerbForm,
    /** The infinitive translated, when the form the reader met was not it. */
    infinitiveMeaning: String?,
    /** The Viccionari's own definition of the verb, in Catalan. */
    definition: String?,
    modifier: Modifier = Modifier,
    onOpenInfinitive: ((String) -> Unit)? = null,
) {
    Column(modifier = modifier) {
        // What the written form is. The leading reading is the one a reader is
        // most likely to have met; the rest follow on one line, because
        // "canta" really is both the present and an order and hiding either
        // would be the card being tidier than the language.
        val readings = verb.readings.map { readingName(it) }
        if (readings.isNotEmpty()) {
            Text(
                text = readings.first(),
                style = MaterialTheme.typography.titleMedium,
            )
            if (readings.size > 1) {
                Text(
                    text = stringResource(
                        Res.string.dictionary_verb_also,
                        readings.drop(1).joinToString(", "),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        } else {
            // Placed, but not placed exactly. Saying which verb it belongs to
            // is worth a card on its own, and inventing a tense to fill the
            // line would be worse than leaving it out.
            Text(
                text = stringResource(Res.string.dictionary_verb_unplaced),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = stringResource(conjugationName(verb.conjugation)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.sm),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = Space.md)
                .clip(RoundedCornerShape(14.dp))
                .then(
                    if (onOpenInfinitive == null) {
                        Modifier
                    } else {
                        Modifier.clickable { onOpenInfinitive(verb.infinitive) }
                    },
                )
                .padding(vertical = Space.sm),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.dictionary_verb_infinitive),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = verb.infinitive,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 1.dp),
                )
                infinitiveMeaning?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (onOpenInfinitive != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(
                        Res.string.dictionary_verb_look_up,
                        verb.infinitive,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        definition?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = Space.sm),
            )
        }
    }
}

/** "Imperfect indicative · 2nd person singular", or "Gerund". */
@Composable
private fun readingName(reading: VerbReading): String = when (reading) {
    is VerbReading.NonFinite -> stringResource(
        when (reading.form) {
            NonFiniteForm.INFINITIVE -> Res.string.verb_form_infinitive
            NonFiniteForm.GERUND -> Res.string.verb_form_gerund
            NonFiniteForm.PARTICIPLE -> Res.string.verb_form_participle
        },
    )

    is VerbReading.Finite -> stringResource(
        Res.string.dictionary_verb_reading,
        stringResource(tenseName(reading.mood, reading.tense)),
        stringResource(personName(reading.person)),
    )
}

/**
 * A mood and a tense as the one name a grammar would print for them.
 *
 * One string per combination rather than a mood and a tense glued together,
 * because the two do not concatenate in either language this app speaks:
 * *present de subjuntiu* is not *present* followed by *subjuntiu*, and the
 * imperative has no tense at all to put beside it.
 */
private fun tenseName(mood: Mood, tense: Tense?): StringResource = when (mood) {
    Mood.IMPERATIVE -> Res.string.verb_mood_imperative
    Mood.INDICATIVE -> when (tense) {
        Tense.PRESENT -> Res.string.verb_tense_present_indicative
        Tense.IMPERFECT -> Res.string.verb_tense_imperfect_indicative
        Tense.PAST -> Res.string.verb_tense_past
        Tense.FUTURE -> Res.string.verb_tense_future
        Tense.CONDITIONAL -> Res.string.verb_tense_conditional
        null -> Res.string.verb_mood_indicative
    }

    Mood.SUBJUNCTIVE -> when (tense) {
        Tense.IMPERFECT -> Res.string.verb_tense_imperfect_subjunctive
        else -> Res.string.verb_tense_present_subjunctive
    }
}

private fun personName(person: Person): StringResource = when (person) {
    Person.S1 -> Res.string.verb_person_1s
    Person.S2 -> Res.string.verb_person_2s
    Person.S3 -> Res.string.verb_person_3s
    Person.P1 -> Res.string.verb_person_1p
    Person.P2 -> Res.string.verb_person_2p
    Person.P3 -> Res.string.verb_person_3p
}

private fun conjugationName(conjugation: Conjugation): StringResource = when (conjugation) {
    Conjugation.FIRST -> Res.string.verb_conjugation_first
    Conjugation.SECOND -> Res.string.verb_conjugation_second
    Conjugation.THIRD -> Res.string.verb_conjugation_third
}
