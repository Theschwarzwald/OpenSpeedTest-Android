package com.example.openspeedtest

import android.net.Uri
import java.util.UUID

data class Server(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val allowSelfSigned: Boolean = false,
)

/** "192.168.1.10:3000" -> "http://192.168.1.10:3000/" */
fun normalizeUrl(input: String): String {
    var u = input.trim()
    if (!u.startsWith("http://", ignoreCase = true) && !u.startsWith("https://", ignoreCase = true)) {
        u = "http://$u"
    }
    return u.trimEnd('/') + "/"
}

fun isValidServerUrl(input: String): Boolean =
    input.isNotBlank() && !Uri.parse(normalizeUrl(input)).host.isNullOrBlank()
