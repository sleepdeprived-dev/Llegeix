package com.david.llegeix.ui.platform

import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

actual fun onThisDevice(phone: StringResource, mac: StringResource): StringResource = mac

actual fun onThisDevice(phone: DrawableResource, mac: DrawableResource): DrawableResource = mac
