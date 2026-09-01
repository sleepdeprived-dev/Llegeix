package com.david.llegeix.util

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.Settings
import androidx.core.net.toUri

/** True when the app currently holds All Files Access. */
fun hasAllFilesAccess(): Boolean = Environment.isExternalStorageManager()

/**
 * Ways to reach the All Files Access toggle, best first.
 *
 * This grant cannot be requested with a runtime permission dialog — the user has
 * to flip it in system Settings and come back — so callers must re-check
 * [hasAllFilesAccess] on resume rather than trusting an activity result.
 *
 * A list rather than a single resolved intent because package visibility on API
 * 30+ makes `resolveActivity` unreliable for probing: it can return null for an
 * activity that in fact launches fine. Callers should try each in turn and let
 * `ActivityNotFoundException` be the real signal.
 */
fun allFilesAccessIntents(context: Context): List<Intent> = listOf(
    // Per-app screen, lands directly on our toggle.
    Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        "package:${context.packageName}".toUri(),
    ),
    // A few OEM builds omit the per-app screen; this is the full app list.
    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
)
