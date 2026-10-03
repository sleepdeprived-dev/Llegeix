package com.david.llegeix.ui.settings

import com.david.llegeix.ui.platform.onThisDevice
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.llegeix.data.settings.AccentColor
import com.david.llegeix.data.settings.ThemeMode
import com.david.llegeix.resources.*
import com.david.llegeix.ui.common.resolved
import com.david.llegeix.ui.common.Space
import kotlin.math.roundToInt
import java.io.File
import com.david.llegeix.util.formatSize
import com.david.llegeix.update.AvailableUpdate
import com.david.llegeix.update.rememberUpdateActions
import com.david.llegeix.ui.theme.systemColorScheme
import com.david.llegeix.ui.theme.hslColor
import com.david.llegeix.ui.theme.isDark
import com.david.llegeix.ui.theme.swatchOrNull
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

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
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    var showColorPicker by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showErase by remember { mutableStateOf(false) }
    var askForInstallPermission by remember { mutableStateOf(false) }
    val isErasing by viewModel.isErasing.collectAsState()
    val updateActions = rememberUpdateActions(viewModel.updater)

    Scaffold(
        // The app shell's Scaffold has already inset this screen for the
        // status bar and the navigation bar; counting them a second time
        // put a dead band above the bottom bar and made every top bar
        // 24dp taller than it asks to be.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                expandedHeight = Space.topBar,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                        )
                    }
                },
                title = { Text(stringResource(Res.string.settings_title)) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen)
                .padding(bottom = Space.xl),
        ) {
            SectionHeader(stringResource(Res.string.settings_appearance))

            SettingsCard {
                FieldLabel(stringResource(Res.string.settings_theme))
                ThemePicker(
                    current = settings.themeMode,
                    onChoose = viewModel::onThemeModeChange,
                )

                Spacer(modifier = Modifier.height(Space.xl))
                FieldLabel(stringResource(Res.string.settings_accent))
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
                        text = stringResource(onThisDevice(Res.string.settings_accent_system_summary, Res.string.settings_accent_system_summary_mac)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Space.xs),
                    )
                }
            }

            SectionHeader(stringResource(Res.string.settings_privacy))

            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPrivacy = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.settings_privacy_open),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(Res.string.settings_privacy_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Space.xs),
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionHeader(stringResource(Res.string.settings_data))

            SettingsCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showErase = true },
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.settings_erase),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = stringResource(Res.string.settings_erase_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Space.xs),
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionHeader(stringResource(Res.string.settings_updates))

            SettingsCard {
                UpdateRows(
                    state = updateState,
                    installedVersion = viewModel.installedVersion,
                    onCheck = viewModel::onCheckForUpdates,
                    onDownload = viewModel::onDownloadUpdate,
                    onInstall = { file ->
                        if (viewModel.canInstallUpdates()) {
                            if (!updateActions.install(file)) viewModel.onCouldNotOpen()
                        } else {
                            askForInstallPermission = true
                        }
                    },
                    onOpenPage = { update ->
                        if (!updateActions.openReleasePage(update)) viewModel.onCouldNotOpen()
                    },
                    onDismiss = viewModel::onDismissUpdate,
                )
            }

            SectionHeader(stringResource(Res.string.settings_about))

            SettingsCard {
                AboutRows(viewModel.installedVersion.ifBlank { null })
            }
        }
    }

    if (showPrivacy) {
        PrivacyDialog(onDismiss = { showPrivacy = false })
    }

    if (askForInstallPermission) {
        InstallPermissionDialog(
            onOpenSettings = {
                askForInstallPermission = false
                if (!updateActions.requestInstallPermission()) viewModel.onCouldNotOpen()
            },
            onDismiss = { askForInstallPermission = false },
        )
    }

    if (showErase) {
        EraseDialog(
            isErasing = isErasing,
            onConfirm = { viewModel.onEraseEverything { showErase = false } },
            onDismiss = { showErase = false },
        )
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

/**
 * The privacy policy, in full, in the app.
 *
 * Kept as text rather than a link to a web page: a policy you have to be online
 * to read is a poor fit for an app whose whole point is working offline, and a
 * link is one more place for the truth to drift from the code.
 */
/**
 * The confirmation for erasing everything.
 *
 * It lists what goes, including the two things Android's own "clear storage"
 * leaves behind — the folder permissions and the downloaded translation models
 * — because those are the reason the reader is looking at this dialog rather
 * than at the system one. The confirm button is coloured as the destructive
 * action it is, and there is no undo, so the wording says that too.
 */
@Composable
private fun EraseDialog(
    isErasing: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isErasing) onDismiss() },
        title = { Text(stringResource(Res.string.erase_title)) },
        text = {
            Text(
                text = stringResource(onThisDevice(Res.string.erase_body, Res.string.erase_body_mac)),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isErasing) {
                Text(
                    text = stringResource(
                        if (isErasing) Res.string.erase_running else Res.string.erase_confirm,
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isErasing) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}

@Composable
private fun PrivacyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.privacy_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(onThisDevice(Res.string.privacy_body, Res.string.privacy_body_mac)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(Res.string.privacy_updated),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.lg),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_done)) }
        },
    )
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

/**
 * Four tiles rather than four segments in a row.
 *
 * A segmented control divides the width equally regardless of the words in it,
 * so "Sistema" — the longest label and the default — was wrapping to two lines
 * inside its segment and clipping. Shrinking the type would have fixed the
 * symptom and made the control harder to read. A tile has room for its label
 * and, more to the point, room to *show* what it does: each one previews its
 * own background and text colour, so the choice is visible rather than named.
 */
@Composable
private fun ThemePicker(current: ThemeMode, onChoose: (ThemeMode) -> Unit) {
    val labels = mapOf(
        ThemeMode.SYSTEM to Res.string.settings_theme_system_short,
        ThemeMode.LIGHT to Res.string.settings_theme_light_short,
        ThemeMode.DARK to Res.string.settings_theme_dark_short,
        ThemeMode.AMOLED to Res.string.settings_theme_amoled_short,
    )
    val systemIsDark = isSystemInDarkTheme()

    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        ThemeMode.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                row.forEach { mode ->
                    ThemeTile(
                        label = stringResource(labels.getValue(mode)),
                        preview = mode.previewColors(systemIsDark),
                        selected = mode == current,
                        onClick = { onChoose(mode) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** The page and ink a mode produces, for the tile to show. */
private fun ThemeMode.previewColors(systemIsDark: Boolean): Pair<Color, Color> = when {
    this == ThemeMode.LIGHT -> Color(0xFFFCFCFC) to Color(0xFF14151A)
    this == ThemeMode.DARK -> Color(0xFF1C1B20) to Color(0xFFE6E1E9)
    this == ThemeMode.AMOLED -> Color.Black to Color(0xFFEDEDED)
    systemIsDark -> Color(0xFF1C1B20) to Color(0xFFE6E1E9)
    else -> Color(0xFFFCFCFC) to Color(0xFF14151A)
}

@Composable
private fun ThemeTile(
    label: String,
    preview: Pair<Color, Color>,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (page, ink) = preview
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(Space.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // A scrap of page with two lines of text on it.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(page)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    Modifier
                        .width(38.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ink),
                )
                Box(
                    Modifier
                        .width(24.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ink.copy(alpha = 0.55f)),
                )
            }
        }
        Row(
            modifier = Modifier.padding(top = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(end = 2.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
    val dark = themeMode.isDark()
    val dynamicPrimary = systemColorScheme(dark).primary

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
                            stringResource(Res.string.settings_accent_selected, label)
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

/**
 * The update card: what is running, and the one button that goes and asks.
 *
 * Deliberately a card that does nothing until it is pressed. Llegeix is
 * sideloaded, so it has to be able to tell the reader that a new version
 * exists — but "has to be able to" is not "should keep going and looking", and
 * an app that phones home on every launch to see whether it is out of date is
 * an app doing something on the reader's connection that the reader did not ask
 * for. One button, one request, and the last line of the card says so.
 *
 * The states below are drawn one at a time and read top to bottom in the order
 * the job actually happens — ask, find, fetch, install — so the card never
 * shows two things to decide between.
 */
@Composable
private fun UpdateRows(
    state: UpdateUiState,
    installedVersion: String,
    onCheck: () -> Unit,
    onDownload: (AvailableUpdate) -> Unit,
    onInstall: (File) -> Unit,
    onOpenPage: (AvailableUpdate) -> Unit,
    onDismiss: () -> Unit,
) {

    Text(
        text = stringResource(Res.string.settings_version, installedVersion),
        style = MaterialTheme.typography.titleMedium,
    )

    when (state) {
        is UpdateUiState.Idle -> {
            Text(
                text = stringResource(Res.string.settings_update_manual),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.xs),
            )
            Button(
                onClick = onCheck,
                modifier = Modifier.padding(top = Space.md),
            ) {
                Text(stringResource(Res.string.settings_update_check))
            }
        }

        is UpdateUiState.Checking -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = Space.md),
        ) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(Res.string.settings_update_checking),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Space.md),
            )
        }

        is UpdateUiState.UpToDate -> {
            UpdateNote(stringResource(Res.string.settings_update_current))
            Button(
                onClick = onCheck,
                modifier = Modifier.padding(top = Space.md),
            ) {
                Text(stringResource(Res.string.settings_update_check))
            }
        }

        is UpdateUiState.Available -> {
            UpdateHeadline(state.update)
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = Space.md),
            ) {
                Button(onClick = { onDownload(state.update) }) {
                    Text(stringResource(onThisDevice(Res.string.settings_update_download, Res.string.settings_update_download_mac)))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.settings_update_dismiss))
                }
            }
            TextButton(
                onClick = { onOpenPage(state.update) },
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(stringResource(Res.string.settings_update_open_page))
            }
        }

        is UpdateUiState.Downloading -> {
            UpdateHeadline(state.update)
            // A determinate bar, because the size is known before the first
            // byte arrives: a 70 MB fetch behind a spinner is a spinner nobody
            // can tell from a hang.
            LinearProgressIndicator(
                progress = { state.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Space.md),
            )
            Text(
                text = stringResource(
                    Res.string.settings_update_downloading,
                    (state.fraction * 100).roundToInt(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.sm),
            )
        }

        is UpdateUiState.Ready -> {
            UpdateHeadline(state.update)
            UpdateNote(stringResource(onThisDevice(Res.string.settings_update_ready, Res.string.settings_update_ready_mac)))
            Button(
                onClick = { onInstall(state.file) },
                modifier = Modifier.padding(top = Space.md),
            ) {
                Text(stringResource(onThisDevice(Res.string.settings_update_install, Res.string.settings_update_install_mac)))
            }
        }

        is UpdateUiState.Trouble -> {
            Text(
                text = state.message.resolved(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Space.sm),
            )
            Button(
                onClick = onCheck,
                modifier = Modifier.padding(top = Space.md),
            ) {
                Text(stringResource(Res.string.settings_update_check))
            }
        }
    }
}

/** The version that is out, and what fetching it will cost. */
@Composable
private fun UpdateHeadline(update: AvailableUpdate) {
    Text(
        text = stringResource(Res.string.settings_update_found, update.version),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Space.sm),
    )
    Text(
        text = stringResource(
            Res.string.settings_update_size,
            formatSize(update.downloadBytes),
            update.abi,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Space.xs),
    )
}

@Composable
private fun UpdateNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Space.sm),
    )
}

/**
 * Asks for the one permission this needs, and says what it is for.
 *
 * Shown only when the installer would refuse, and only after the update has
 * already been downloaded — asking for the right to install before there is
 * anything to install is asking a question with no context around it.
 */
@Composable
private fun InstallPermissionDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_update_permission_title)) },
        text = { Text(stringResource(Res.string.settings_update_permission_body)) },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(Res.string.settings_update_permission_open))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}

@Composable
private fun AboutRows(version: String?) {
    // The name is a proper noun and never translated, so it comes straight from
    // the resource rather than from anything locale-dependent.
    Text(
        text = stringResource(Res.string.app_name),
        style = MaterialTheme.typography.titleMedium,
    )
    if (version != null) {
        Text(
            text = stringResource(Res.string.settings_version, version),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.xs),
        )
    }
    // The synonym dictionary is CC BY 4.0, which asks for the credit to travel
    // with it. It is also simply the right place to say whose work this is.
    Text(
        text = stringResource(Res.string.settings_credits_thesaurus),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Space.md),
    )
}
