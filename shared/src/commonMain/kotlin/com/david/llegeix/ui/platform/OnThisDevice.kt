package com.david.llegeix.ui.platform

import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

/**
 * [phone] on the phone, [mac] on the Mac: for the few sentences that name the
 * device they are read on, or a permission only one of them asks for.
 */
expect fun onThisDevice(phone: StringResource, mac: StringResource): StringResource

/** The same for the pictures of the device: a phone on the phone, a laptop on the Mac. */
expect fun onThisDevice(phone: DrawableResource, mac: DrawableResource): DrawableResource
