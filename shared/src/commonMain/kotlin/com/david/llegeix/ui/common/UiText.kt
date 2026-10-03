package com.david.llegeix.ui.common

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A piece of text a ViewModel wants shown, resolved to a string by the UI.
 *
 * ViewModels name the resource and let the composable read it, so a message
 * is worded where it is shown, in the composition's language, rather than
 * wherever the ViewModel happened to be when it failed.
 *
 * Always a resource, never an exception's message: those come from Android,
 * the JDK or a library, in English, and the app speaks Catalan. What actually
 * went wrong belongs in the log.
 */
sealed interface UiText {

    data class Res(val id: StringResource, val args: List<Any> = emptyList()) : UiText

    /**
     * A message whose wording depends on a count.
     *
     * Separate from [Res] because a plural cannot be resolved by naming a
     * string: which wording applies is a question about the number *and* the
     * language, and only the resources know the answer. Catalan and English
     * disagree about it, which is exactly why the app cannot pick one itself.
     */
    data class Plural(
        val id: PluralStringResource,
        val count: Int,
        val args: List<Any> = listOf(count),
    ) : UiText

    /**
     * Several messages read as one, a space between each.
     *
     * For a report made of counts — "12 cards added. 3 were already here." —
     * where each sentence needs its own plural: one plural resource cannot
     * agree with two numbers at once.
     */
    data class Joined(val parts: List<UiText>) : UiText

    companion object {
        fun of(id: StringResource, vararg args: Any): UiText = Res(id, args.toList())

        fun ofPlural(id: PluralStringResource, count: Int): UiText = Plural(id, count)
    }
}

@Composable
fun UiText.resolved(): String = when (this) {
    is UiText.Res -> stringResource(id, *args.toTypedArray())
    is UiText.Plural -> pluralStringResource(id, count, *args.toTypedArray())
    is UiText.Joined -> parts.map { it.resolved() }.joinToString(" ")
}
