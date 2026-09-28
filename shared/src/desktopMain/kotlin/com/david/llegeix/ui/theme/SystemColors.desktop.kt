package com.david.llegeix.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import java.util.concurrent.TimeUnit

/**
 * The Mac's accent colour, as the same generator builds the phone's fixed
 * accents from, with the same readable light neutrals.
 */
@Composable
internal actual fun systemColorScheme(dark: Boolean): ColorScheme {
    val schemes = remember { schemesFromSeed(macAccent()) }
    return if (dark) schemes.dark else schemes.light.withReadableLightNeutrals()
}

/**
 * System Settings keeps the choice as AppleAccentColor, a number, absent for
 * the default (blue, or "multicolour", which also shows as blue). The colours
 * are the ones macOS draws its own controls in.
 */
private fun macAccent(): Color {
    val value = runCatching {
        val process = ProcessBuilder("defaults", "read", "-g", "AppleAccentColor")
            .redirectErrorStream(true)
            .start()
        val out = process.inputStream.bufferedReader().readText().trim()
        if (process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0) out.toIntOrNull() else null
    }.getOrNull()
    return when (value) {
        -1 -> Color(0xFF8C8C8C) // graphite
        0 -> Color(0xFFFF5257) // red
        1 -> Color(0xFFF7821B) // orange
        2 -> Color(0xFFFFC600) // yellow
        3 -> Color(0xFF62BA46) // green
        5 -> Color(0xFFA550A7) // purple
        6 -> Color(0xFFF74F9E) // pink
        else -> Color(0xFF007AFF) // blue
    }
}
