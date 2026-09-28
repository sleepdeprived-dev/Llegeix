package com.david.llegeix.platform

actual fun logWarning(tag: String, message: String, error: Throwable?) {
    System.err.println("W/$tag: $message" + (error?.let { ": $it" } ?: ""))
}
