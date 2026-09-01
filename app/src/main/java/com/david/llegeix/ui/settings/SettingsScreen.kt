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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import com.david.llegeix.ui.theme.hslColor
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
                expandedHeight = Space.topBar,
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
        ThemeMode.AMOLED to R.string.settings_theme_amoled_short,
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

    // Two rows of four rather than eight across. Eight dots on a narrow phone
    // leaves them touching, and a row of touching circles reads as a strip of
    // colour instead of a set of choices.
    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        AccentColor.entries.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { accent ->
                    val label = stringResource(accent.labelRes)
                    val selected = accent == current
                    AccentSwatch(
                        color = when (accent) {
                            AccentColor.CUSTOM -> Color(customAccent)
                            else -> accent.swatchOrNull() ?: dynamicPrimary
                        },
                        selected = selected,
                        // The custom dot is ringed so it reads as "and one of
                        // your own" rather than as a seventh fixed colour.
                        isCustom = accent == AccentColor.CUSTOM,
                        contentDescription = if (selected) {
                            stringResource(R.string.settings_accent_selected, label)
                        } else {
                            label
                        },
                        // Tapping the custom dot opens the mixer rather than
                        // silently applying whatever was mixed last.
                        onClick = {
                            if (accent == AccentColor.CUSTOM) {
                                onOpenPicker()
                            } else {
                                onChoose(accent)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AccentSwatch(
    color: Color,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    isCustom: Boolean = false,
) {
    val ring = MaterialTheme.colorScheme.onSurface

    Box(
        // The selection ring is drawn outside the dot with a gap, rather than
        // as a thicker border on it. A border eats into the colour and makes
        // the chosen swatch look smaller than the others; a halo leaves every
        // dot the same size and still reads unmistakably.
        modifier = Modifier
            .size(48.dp)
            .then(
                if (selected) {
                    Modifier.border(2.dp, ring, CircleShape)
                } else {
                    Modifier
                },
            )
            .padding(4.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .then(
                    if (isCustom) {
                        // A hue wheel behind the chosen colour: this is the one
                        // that opens a picker, and it should look like it.
                        Modifier.background(
                            Brush.sweepGradient(
                                (0..360 step 45).map { hslColor(it.toFloat(), 0.85f, 0.55f) },
                            ),
                        )
                    } else {
                        Modifier.background(color)
                    },
                )
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (isCustom) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = onColorOf(color),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Black or white, whichever can be seen on [color]. */
private fun onColorOf(color: Color): Color {
    val luminance = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
    return if (luminance > 0.55f) Color.Black else Color.White
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
