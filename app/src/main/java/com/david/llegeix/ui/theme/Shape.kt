package com.david.llegeix.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * The app's corners, one scale for everything Material draws.
 *
 * Material's own defaults start at 4dp, which is what menus and text fields
 * were drawn with: square-cornered boxes next to cards and buttons that are
 * generously round, as if two apps had been put on one screen. These are rounder
 * throughout and step up evenly, so a menu, a field, a card, a sheet and a
 * dialog read as one family — the same softness the flashcards are drawn with.
 */
val Shapes = Shapes(
    // Menus, tooltips, text fields.
    extraSmall = RoundedCornerShape(12.dp),
    // Chips, snackbars.
    small = RoundedCornerShape(14.dp),
    // Cards.
    medium = RoundedCornerShape(18.dp),
    // Sheets' top corners, navigation drawers.
    large = RoundedCornerShape(24.dp),
    // Dialogs.
    extraLarge = RoundedCornerShape(30.dp),
)
