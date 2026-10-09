package com.kudo.android.ui

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

sealed interface LinuxDoDestination {
    data class Topic(val id: Int, val number: Int? = null) : LinuxDoDestination
    data class User(val username: String) : LinuxDoDestination
    data object Home : LinuxDoDestination
}

object LinuxDoLink {
    fun parse(value: String): LinuxDoDestination? {
        val url = value.trim().toHttpUrlOrNull() ?: return null
        if (url.host != "linux.do" || url.username.isNotEmpty() || url.password.isNotEmpty() ||
            url.port != if (url.isHttps) 443 else 80) return null
        val path = url.pathSegments.dropLastWhile { it.isEmpty() }
        return when (path.firstOrNull()) {
            "t" -> {
                val offset = if (path.getOrNull(1)?.toIntOrNull() != null) 1 else 2
                val id = path.getOrNull(offset)?.toIntOrNull()?.takeIf { it > 0 }
                val floor = path.getOrNull(offset + 1)
                val number = floor?.toIntOrNull()?.takeIf { it > 0 }
                if (id == null || path.size > offset + 2 || (floor != null && number == null))
                    LinuxDoDestination.Home else LinuxDoDestination.Topic(id, number)
            }
            "u" -> path.getOrNull(1)?.takeIf {
                it.matches(Regex("[\\p{L}\\p{N}_-][\\p{L}\\p{N}_.-]*"))
            }?.let(LinuxDoDestination::User) ?: LinuxDoDestination.Home
            else -> LinuxDoDestination.Home
        }
    }
}
