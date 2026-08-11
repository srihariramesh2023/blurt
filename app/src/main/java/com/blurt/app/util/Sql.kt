package com.blurt.app.util

/**
 * Escapes SQL LIKE wildcards so user input matches literally.
 * Companion to the `ESCAPE '\'` clause in CaptureDao.search.
 */
fun String.escapeLikePattern(): String =
    replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
