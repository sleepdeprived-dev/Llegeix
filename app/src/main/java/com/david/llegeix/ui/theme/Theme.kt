package com.david.llegeix.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.david.llegeix.data.settings.AccentColor
import com.david.llegeix.data.settings.DEFAULT_CUSTOM_ACCENT
import com.david.llegeix.data.settings.ThemeMode

@Composable
fun LlegeixTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: AccentColor = AccentColor.SYSTEM,
    customAccent: Int = DEFAULT_CUSTOM_ACCENT,
    content: @Composable () -> Unit,
) {
    val darkTheme = themeMode.isDark()

    // minSdk is 31, so dynamic colour is always available; no SDK_INT guard needed.
    val context = LocalContext.current
    val schemes = if (accent == AccentColor.CUSTOM) {
        remember(customAccent) { schemesFromSeed(Color(customAccent)) }
    } else {
        accent.schemes()
    }
    val colorScheme = when {
        schemes != null && darkTheme -> schemes.dark
        schemes != null -> schemes.light
        darkTheme -> dynamicDarkColorScheme(context)
        else -> dynamicLightColorScheme(context)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}

/** Whether this mode renders dark right now, resolving SYSTEM against the device. */
@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
