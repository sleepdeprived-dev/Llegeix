package com.david.llegeix.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/**
 * The scheme for [com.david.llegeix.data.settings.AccentColor.SYSTEM]: the
 * colour the platform itself offers. On the phone that is Material You, drawn
 * from the wallpaper; on the Mac, the accent colour chosen in System Settings.
 */
@Composable
internal expect fun systemColorScheme(dark: Boolean): ColorScheme
