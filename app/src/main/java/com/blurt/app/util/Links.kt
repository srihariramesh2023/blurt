package com.blurt.app.util

import android.net.Uri

/** Prepends https:// when no scheme is present, so "google.com" becomes a valid URL. */
fun String.normalizedHttpUrl(): String {
    val trimmed = trim()
    if (trimmed.isEmpty()) return trimmed
    return if (Uri.parse(trimmed).scheme.isNullOrEmpty()) "https://$trimmed" else trimmed
}

/** True when the string is an absolute http/https URL with a host and no whitespace. */
fun String.isHttpUrl(): Boolean = runCatching {
    val uri = Uri.parse(this)
    uri.isAbsolute &&
        (uri.scheme == "http" || uri.scheme == "https") &&
        !uri.host.isNullOrEmpty() &&
        uri.host!!.none { it.isWhitespace() }
}.getOrDefault(false)

/** Human-friendly domain of a URL, e.g. "https://www.voiceos.com" -> "voiceos.com". */
fun String.urlDomain(): String {
    val host = runCatching { Uri.parse(this).host }.getOrNull()
    return host?.removePrefix("www.") ?: this
}
