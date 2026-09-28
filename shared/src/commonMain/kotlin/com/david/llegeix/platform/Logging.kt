package com.david.llegeix.platform

/** A warning for whoever reads the logs: Android's Log.w on the phone, standard error on the Mac. */
expect fun logWarning(tag: String, message: String, error: Throwable? = null)
