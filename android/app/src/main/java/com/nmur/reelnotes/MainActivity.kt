package com.nmur.reelnotes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import com.nmur.reelnotes.ui.ReelNotesRoot

class MainActivity : ComponentActivity() {
    private val openNoteId = mutableLongStateOf(-1L)
    private val openScreen = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        handle(intent)
        setContent {
            ReelNotesRoot(
                openNoteId = openNoteId.longValue,
                openScreen = openScreen.value,
                onOpenHandled = { openNoteId.longValue = -1L; openScreen.value = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_NOTE_ID, -1L) ?: -1L
        if (id > 0) openNoteId.longValue = id
        intent?.getStringExtra(EXTRA_SCREEN)?.let { openScreen.value = it }
    }

    companion object {
        const val EXTRA_NOTE_ID = "note_id"
        const val EXTRA_SCREEN = "screen"
        const val SCREEN_REVIEW = "review"
    }
}
