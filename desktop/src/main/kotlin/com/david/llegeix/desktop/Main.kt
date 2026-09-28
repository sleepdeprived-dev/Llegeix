package com.david.llegeix.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.david.llegeix.resources.Res
import com.david.llegeix.resources.app_name
import com.david.llegeix.resources.desktop_section_pending
import com.david.llegeix.resources.nav_dictionary
import com.david.llegeix.resources.nav_flashcards
import com.david.llegeix.resources.nav_library
import com.david.llegeix.resources.nav_saved
import com.david.llegeix.ui.common.EmptyState
import com.david.llegeix.ui.common.Pill
import com.david.llegeix.ui.common.PillGroup
import com.david.llegeix.ui.common.Space
import com.david.llegeix.ui.theme.LlegeixTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import java.util.Locale

/**
 * Llegeix for the Mac: for now, the shell.
 *
 * A window in the phone's own theme, following the saved settings (the Mac's
 * own accent until another is chosen), with the four sections the phone has. Everything in it comes from the shared module; the sections fill in
 * as their screens move there.
 */
fun main() {
    // Catalan whatever the Mac is set to, as on the phone: plurals follow the
    // locale's rules ("1000000 de fonts"), and so do dates and numbers.
    Locale.setDefault(Locale.forLanguageTag("ca"))
    application { App() }
}

@Composable
private fun ApplicationScope.App() {
    Window(
        onCloseRequest = ::exitApplication,
        title = stringResource(Res.string.app_name),
        state = rememberWindowState(size = DpSize(1000.dp, 720.dp)),
    ) {
        val settings by DesktopApp.settings.settings.collectAsState()
        LlegeixTheme(
            themeMode = settings.themeMode,
            accent = settings.accent,
            customAccent = settings.customAccent,
        ) {
            Shell()
        }
    }
}

/** The four sections, in the phone's order. */
private enum class Section(val label: StringResource) {
    LIBRARY(Res.string.nav_library),
    DICTIONARY(Res.string.nav_dictionary),
    SAVED(Res.string.nav_saved),
    FLASHCARDS(Res.string.nav_flashcards),
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
                    val label = stringResource(entry.label)
                    Pill(
                        selected = entry == section,
                        onClick = { section = entry },
                        label = label,
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            EmptyState(
                title = stringResource(section.label),
                body = stringResource(Res.string.desktop_section_pending),
            )
        }
    }
}
