package com.david.llegeix.ui.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * A piece of text a ViewModel wants shown, resolved to a string by the UI.
 *
 * ViewModels cannot format their own messages here: the app carries its own
 * language preference, and the only `Context` configured for it is the one the
 * composition runs against. A ViewModel holding the application context would
 * resolve every snackbar in the device's language instead of the app's, so it
 * names the resource and lets the composable read it.
 *
 * [Raw] exists for text that is already a string with no resource behind it —
 * an exception message from the platform, most often.
 */
sealed interface UiText {

    data class Res(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    data class Raw(val value: String) : UiText

    fun resolve(context: Context): String = when (this) {
        is Raw -> value
        is Res -> if (args.isEmpty()) {
            context.getString(id)
        } else {
            context.getString(id, *args.toTypedArray())
        }
    }

    companion object {
        fun of(@StringRes id: Int, vararg args: Any): UiText = Res(id, args.toList())

        /** Prefers a platform message when there is one, falling back to [id]. */
        fun ofMessageOr(message: String?, @StringRes id: Int): UiText =
            message?.takeIf { it.isNotBlank() }?.let(::Raw) ?: Res(id)
    }
}

@Composable
fun UiText.resolved(): String = resolve(LocalContext.current)
