package com.david.llegeix.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.david.llegeix.ui.common.EmptyState
import com.david.llegeix.ui.common.Pill
import com.david.llegeix.ui.common.PillGroup
import com.david.llegeix.ui.common.Space
import com.david.llegeix.ui.theme.Shapes
import com.david.llegeix.ui.theme.schemesFromSeed

/**
 * Llegeix for the Mac: for now, the shell.
 *
 * A window in the app's own colours, shapes and type, with the four sections the
 * phone has. Everything in it comes from the shared module; the sections fill in
 * as their screens move there.
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Llegeix",
        state = rememberWindowState(size = DpSize(1000.dp, 720.dp)),
    ) {
        LlegeixDesktopTheme {
            Shell()
        }
    }
}

/** The four sections, in the phone's order. */
private enum class Section(val label: String) {
    LIBRARY("Biblioteca"),
    DICTIONARY("Diccionari"),
    SAVED("Desat"),
    FLASHCARDS("Targetes"),
}

@Composable
private fun Shell() {
    var section by rememberSaveable { mutableStateOf(Section.LIBRARY) }
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(top = Space.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PillGroup {
                Section.entries.forEach { entry ->
                    Pill(
                        selected = entry == section,
                        onClick = { section = entry },
                        label = entry.label,
                    ) {
                        Text(entry.label, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            EmptyState(
                title = section.label,
                body = "Aquesta secció encara no és al Mac.",
            )
        }
    }
}

/**
 * Blue, the Mac's own default accent, through the same generator the phone uses
 * for its fixed accents, in Figtree. Reading the system accent and the reader's
 * saved choice come with settings.
 */
@Composable
private fun LlegeixDesktopTheme(content: @Composable () -> Unit) {
    val schemes = remember { schemesFromSeed(Color(0xFF1976D2)) }
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) schemes.dark else schemes.light,
        shapes = Shapes,
        typography = remember { figtreeTypography() },
        content = content,
    )
}

@OptIn(ExperimentalTextApi::class)
private fun figtree(weight: FontWeight) = Font(
    resource = "figtree.ttf",
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

private fun figtreeTypography(): Typography {
    val family = FontFamily(
        figtree(FontWeight.Normal),
        figtree(FontWeight.Medium),
        figtree(FontWeight.SemiBold),
        figtree(FontWeight.Bold),
    )
    fun TextStyle.inFigtree() = copy(fontFamily = family)
    return with(Typography()) {
        copy(
            displayLarge = displayLarge.inFigtree(), displayMedium = displayMedium.inFigtree(),
            displaySmall = displaySmall.inFigtree(), headlineLarge = headlineLarge.inFigtree(),
            headlineMedium = headlineMedium.inFigtree(), headlineSmall = headlineSmall.inFigtree(),
            titleLarge = titleLarge.inFigtree(), titleMedium = titleMedium.inFigtree(),
            titleSmall = titleSmall.inFigtree(), bodyLarge = bodyLarge.inFigtree(),
            bodyMedium = bodyMedium.inFigtree(), bodySmall = bodySmall.inFigtree(),
            labelLarge = labelLarge.inFigtree(), labelMedium = labelMedium.inFigtree(),
            labelSmall = labelSmall.inFigtree(),
        )
    }
}
