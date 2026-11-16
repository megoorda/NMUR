package com.nmur.reelnotes.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
fun ReelNotesTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Very small navigator: library → note detail / settings. */
@Composable
fun ReelNotesRoot(openNoteId: Long, onOpenHandled: () -> Unit) {
    var noteId by rememberSaveable { mutableLongStateOf(-1L) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(openNoteId) {
        if (openNoteId > 0) {
            noteId = openNoteId
            showSettings = false
            onOpenHandled()
        }
    }

    ReelNotesTheme {
        when {
            showSettings -> {
                BackHandler { showSettings = false }
                SettingsScreen(onBack = { showSettings = false })
            }
            noteId > 0 -> {
                BackHandler { noteId = -1L }
                DetailScreen(noteId = noteId, onBack = { noteId = -1L })
            }
            else -> LibraryScreen(
                onOpen = { noteId = it },
                onSettings = { showSettings = true },
            )
        }
    }
}
