package com.david.llegeix.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.david.llegeix.ui.common.Space
import com.david.llegeix.ui.theme.isDark
import com.david.llegeix.ui.theme.swatchOrNull

/**
 * Three choices, one card each.
 *
 * Theme and language were lists of radio rows separated by rules, which made
 * five settings look like fifteen things to read. Both are now single
 * segmented controls: the options and the current answer are one glance rather
 * than a scan down a column, and the space that saves goes into the gaps
 * between the groups.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showColorPicker by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                title = { Text(stringResource(R.string.settings_title)) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen)
                .padding(bottom = Space.huge),
        ) {
            SectionHeader(stringResource(R.string.settings_appearance))

            SettingsCard {
                FieldLabel(stringResource(R.string.settings_theme))
                ThemePicker(
                    current = settings.themeMode,
                    onChoose = viewModel::onThemeModeChange,
                )

                Spacer(modifier = Modifier.height(Space.xl))
                FieldLabel(stringResource(R.string.settings_accent))
                AccentPicker(
                    current = settings.accent,
                    themeMode = settings.themeMode,
                    customAccent = settings.customAccent,
                    onChoose = viewModel::onAccentChange,
                    onOpenPicker = { showColorPicker = true },
                )
                Text(
                    text = stringResource(settings.accent.labelRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.md),
                )
                if (settings.accent == AccentColor.SYSTEM) {
                    Text(
                        text = stringResource(R.string.settings_accent_system_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Space.xs),
                    )
                }
                TextButton(
                    onClick = { showColorPicker = true },
                    modifier = Modifier.padding(top = Space.xs),
                ) {
                    Text(stringResource(R.string.settings_accent_custom_open))
                }
            }

            SectionHeader(stringResource(R.string.settings_language))

            SettingsCard {
                LanguagePicker(
                    current = settings.language,
                    onChoose = viewModel::onLanguageChange,
                )
                Text(
                    text = stringResource(R.string.settings_language_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.lg),
                )
            }

            SectionHeader(stringResource(R.string.settings_about))

            SettingsCard {
                AboutRows()
            }
        }
    }

    ColorPickerHost(
        show = showColorPicker,
        initial = settings.customAccent,
        onDismiss = { showColorPicker = false },
        onConfirm = { chosen ->
            viewModel.onCustomAccentChange(chosen)
            showColorPicker = false
        },
    )
}

@Composable
private fun ColorPickerHost(
    show: Boolean,
    initial: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    if (show) {
        ColorPickerDialog(initial = initial, onDismiss = onDismiss, onConfirm = onConfirm)
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
            start = Space.xs,
            top = Space.xxl,
            bottom = Space.md,
        ),
    )
}

/** A group of related controls, held together by a surface rather than rules. */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(Space.xl),
        content = content,
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = Space.md),
    )
}

@Composable
private fun ThemePicker(current: ThemeMode, onChoose: (ThemeMode) -> Unit) {
    // Short labels: three of them share one row, and "Follow the system"
    // written out would force the control to wrap.
    val labels = mapOf(
        ThemeMode.SYSTEM to R.string.settings_theme_system_short,
        ThemeMode.LIGHT to R.string.settings_theme_light_short,
        ThemeMode.DARK to R.string.settings_theme_dark_short,
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        ThemeMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == current,
                onClick = { onChoose(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
            ) {
                Text(stringResource(labels.getValue(mode)))
            }
        }
    }
}

@Composable
private fun LanguagePicker(current: AppLanguage, onChoose: (AppLanguage) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        AppLanguage.entries.forEachIndexed { index, language ->
            SegmentedButton(
                selected = language == current,
                onClick = { onChoose(language) },
                shape = SegmentedButtonDefaults.itemShape(index, AppLanguage.entries.size),
            ) {
                Text(stringResource(language.labelRes))
            }
        }
    }
}

@Composable
private fun AccentPicker(
    current: AccentColor,
    themeMode: ThemeMode,
    customAccent: Int,
    onChoose: (AccentColor) -> Unit,
    onOpenPicker: () -> Unit,
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
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        AccentColor.entries.forEach { accent ->
            val label = stringResource(accent.labelRes)
            val selected = accent == current
            AccentSwatch(
                color = when (accent) {
                    AccentColor.CUSTOM -> Color(customAccent)
                    else -> accent.swatchOrNull() ?: dynamicPrimary
                },
                selected = selected,
                contentDescription = if (selected) {
                    stringResource(R.string.settings_accent_selected, label)
                } else {
                    label
                },
                // Tapping the custom dot opens the mixer rather than silently
                // applying whatever was mixed last.
                onClick = {
                    if (accent == AccentColor.CUSTOM) onOpenPicker() else onChoose(accent)
                },
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
            .size(40.dp)
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
    val version = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
    }

    // The name is a proper noun and never translated, so it comes straight from
    // the resource rather than from anything locale-dependent.
    Text(
        text = stringResource(R.string.app_name),
        style = MaterialTheme.typography.titleMedium,
    )
    if (version != null) {
        Text(
            text = stringResource(R.string.settings_version, version),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.xs),
        )
    }
}
