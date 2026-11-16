package com.nmur.reelnotes.data

import android.content.Context

val DEFAULT_CATEGORIES = listOf(
    "AI & Engineering",
    "Programming",
    "Free Online Games",
    "Android & Emulators",
    "Tools & Websites",
    "Tech News",
    "Learning & Courses",
    "Productivity",
    "Other",
)

object Providers {
    const val GEMINI = "gemini"
    const val CLAUDE = "claude"
}

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString("server_url", "") ?: ""
        set(v) = prefs.edit().putString("server_url", v.trim().trimEnd('/')).apply()

    var token: String
        get() = prefs.getString("token", "") ?: ""
        set(v) = prefs.edit().putString("token", v.trim()).apply()

    var provider: String
        get() = prefs.getString("provider", Providers.GEMINI) ?: Providers.GEMINI
        set(v) = prefs.edit().putString("provider", v).apply()

    var categories: List<String>
        get() = (prefs.getString("categories", null) ?: DEFAULT_CATEGORIES.joinToString("\n"))
            .lines().map { it.trim() }.filter { it.isNotEmpty() }
        set(v) = prefs.edit().putString("categories", v.joinToString("\n")).apply()

    val isConfigured: Boolean get() = serverUrl.isNotBlank()
}
