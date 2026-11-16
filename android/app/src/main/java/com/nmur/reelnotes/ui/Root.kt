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
import com.nmur.reelnotes.MainActivity

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

object Screens {
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val ASK = "ask"
    const val REVIEW = MainActivity.SCREEN_REVIEW
    const val GRAPH = "graph"
}

/** Small navigator: a top-level screen, plus an optional open note on top of it. */
@Composable
fun ReelNotesRoot(openNoteId: Long, openScreen: String?, onOpenHandled: () -> Unit) {
    var screen by rememberSaveable { mutableStateOf(Screens.LIBRARY) }
    var noteId by rememberSaveable { mutableLongStateOf(-1L) }

    LaunchedEffect(openNoteId, openScreen) {
        if (openScreen != null) { screen = openScreen; noteId = -1L }
        if (openNoteId > 0) noteId = openNoteId
        if (openScreen != null || openNoteId > 0) onOpenHandled()
    }

    ReelNotesTheme {
        if (noteId > 0) {
            BackHandler { noteId = -1L }
            DetailScreen(noteId = noteId, onBack = { noteId = -1L }, onOpenNote = { noteId = it })
            return@ReelNotesTheme
        }
        if (screen != Screens.LIBRARY) BackHandler { screen = Screens.LIBRARY }
        val back = { screen = Screens.LIBRARY }
        when (screen) {
            Screens.SETTINGS -> SettingsScreen(onBack = back)
            Screens.ASK -> AskScreen(onBack = back, onOpenNote = { noteId = it })
            Screens.REVIEW -> ReviewScreen(onBack = back, onOpenNote = { noteId = it })
            Screens.GRAPH -> GraphScreen(onBack = back, onOpenNote = { noteId = it })
            else -> LibraryScreen(onOpen = { noteId = it }, onNavigate = { screen = it })
        }
    }
}
