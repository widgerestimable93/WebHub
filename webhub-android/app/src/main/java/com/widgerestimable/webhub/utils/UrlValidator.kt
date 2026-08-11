package com.widgerestimable.webhub.utils

import java.net.URI

object UrlValidator {
    /** N'accepte que des URL HTTPS bien formées. */
    fun isValidHttps(value: String): Boolean {
        if (value.isBlank()) return false
        return try {
            val uri = URI(value.trim())
            uri.scheme == "https" && !uri.host.isNullOrBlank()
        } catch (e: Exception) {
            false
        }
    }
}

