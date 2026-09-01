package com.david.llegeix.ui.common

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.david.llegeix.R

/**
 * The palette offered for highlights, stored in the database as ARGB ints.
 *
 * Ints rather than Compose [Color] values because these are persisted: a
 * [Color] is a value class over a ULong whose encoding is an implementation
 * detail, and storing it would tie the database contents to a Compose version.
 */
object HighlightColors {

    val Yellow = 0xFFFFD54F.toInt()
    val Green = 0xFF81C784.toInt()
    val Blue = 0xFF64B5F6.toInt()
    val Pink = 0xFFF06292.toInt()
    val Orange = 0xFFFFB74D.toInt()
    val Purple = 0xFFBA68C8.toInt()

    val palette: List<Int> = listOf(Yellow, Green, Blue, Pink, Orange, Purple)

    /** Used when neither the bookmark nor its document specifies one. */
    val Default: Int = Yellow

    fun compose(argb: Int): Color = Color(argb)

    /** The colour's name, as a resource so it follows the app's language. */
    @StringRes
    fun nameOf(argb: Int): Int = when (argb) {
        Yellow -> R.string.colour_yellow
        Green -> R.string.colour_green
        Blue -> R.string.colour_blue
        Pink -> R.string.colour_pink
        Orange -> R.string.colour_orange
        Purple -> R.string.colour_purple
        else -> R.string.colour_custom
    }
}
