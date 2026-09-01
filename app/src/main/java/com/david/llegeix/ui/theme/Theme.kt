package com.david.llegeix.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.david.llegeix.data.settings.AccentColor
import com.david.llegeix.data.settings.ThemeMode

@Composable
fun LlegeixTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: AccentColor = AccentColor.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = themeMode.isDark()

    // minSdk is 31, so dynamic colour is always available; no SDK_INT guard needed.
    val context = LocalContext.current
    val schemes = accent.schemes()
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
