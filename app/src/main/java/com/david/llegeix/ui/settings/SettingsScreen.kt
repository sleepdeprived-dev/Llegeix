package com.david.llegeix.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.llegeix.R
import com.david.llegeix.data.settings.AccentColor
import com.david.llegeix.data.settings.AppLanguage
import com.david.llegeix.data.settings.ThemeMode
import com.david.llegeix.ui.theme.isDark
import com.david.llegeix.ui.theme.swatchOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(stringResource(R.string.settings_appearance))

            SubHeader(stringResource(R.string.settings_theme))
            ThemeMode.entries.forEach { mode ->
                ChoiceRow(
                    label = stringResource(mode.labelRes),
                    selected = mode == settings.themeMode,
                    onClick = { viewModel.onThemeModeChange(mode) },
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            SubHeader(
                title = stringResource(R.string.settings_accent),
                summary = stringResource(R.string.settings_accent_system_summary)
                    .takeIf { settings.accent == AccentColor.SYSTEM },
            )
            AccentPicker(
                current = settings.accent,
                themeMode = settings.themeMode,
                onChoose = viewModel::onAccentChange,
            )
            Text(
                text = stringResource(settings.accent.labelRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 12.dp),
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            SectionHeader(stringResource(R.string.settings_language))
            Text(
                text = stringResource(R.string.settings_language_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
            )
            AppLanguage.entries.forEach { language ->
                ChoiceRow(
                    label = stringResource(language.labelRes),
                    selected = language == settings.language,
                    onClick = { viewModel.onLanguageChange(language) },
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            SectionHeader(stringResource(R.string.settings_about))
            AboutRows()
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun SubHeader(title: String, summary: String? = null) {
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        if (summary != null) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Null onClick: the whole row is already clickable, and giving the
        // button its own handler would announce two targets to a screen reader.
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun AccentPicker(
    current: AccentColor,
    themeMode: ThemeMode,
    onChoose: (AccentColor) -> Unit,
) {
    // SYSTEM has no colour of its own, so its dot previews what the platform
    // would actually produce. Asked for directly rather than read off
    // MaterialTheme, which would otherwise show whichever fixed accent is
    // currently applied and make every dot look the same as the selection.
    val context = LocalContext.current
    val dark = themeMode.isDark()
    val dynamicPrimary = if (dark) {
        dynamicDarkColorScheme(context).primary
    } else {
        dynamicLightColorScheme(context).primary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AccentColor.entries.forEach { accent ->
            val label = stringResource(accent.labelRes)
            val selected = accent == current
            AccentSwatch(
                color = accent.swatchOrNull() ?: dynamicPrimary,
                selected = selected,
                contentDescription = if (selected) {
                    stringResource(R.string.settings_accent_selected, label)
                } else {
                    label
                },
                onClick = { onChoose(accent) },
            )
        }
    }
}

@Composable
private fun AccentSwatch(
    color: Color,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun AboutRows() {
    val context = LocalContext.current
    // The name is a proper noun and never translated, so it comes straight from
    // the resource rather than from anything locale-dependent.
    val version = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
    }

    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (version != null) {
            Text(
                text = stringResource(R.string.settings_version, version),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
