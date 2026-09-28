package com.david.llegeix.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// minSdk is 31, so dynamic colour is always available; no SDK_INT guard needed.
@Composable
internal actual fun systemColorScheme(dark: Boolean): ColorScheme {
    val context = LocalContext.current
    return if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
}
