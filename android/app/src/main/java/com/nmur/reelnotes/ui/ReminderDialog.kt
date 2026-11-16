package com.nmur.reelnotes.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Calendar

/** Quick reminder presets plus a custom date/time picker. [onSet] gets 0 to clear. */
@Composable
fun ReminderDialog(current: Long, onDismiss: () -> Unit, onSet: (Long) -> Unit) {
    val ctx = LocalContext.current

    fun at(daysFromNow: Int, hour: Int, weekday: Int? = null): Long = Calendar.getInstance().apply {
        if (weekday != null) {
            while (get(Calendar.DAY_OF_WEEK) != weekday) add(Calendar.DAY_OF_YEAR, 1)
        } else {
            add(Calendar.DAY_OF_YEAR, daysFromNow)
        }
        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, if (weekday != null) 7 else 1)
    }.timeInMillis

    val presets = listOf(
        "In 1 hour" to System.currentTimeMillis() + 60 * 60 * 1000L,
        "This evening (8 PM)" to at(0, 20),
        "Tomorrow morning (9 AM)" to at(1, 9),
        "This weekend (Sat 10 AM)" to at(0, 10, Calendar.SATURDAY),
        "Next week (Mon 9 AM)" to at(0, 9, Calendar.MONDAY).let { if (it - System.currentTimeMillis() < 86_400_000L) it + 7 * 86_400_000L else it },
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remind me") },
        text = {
            Column {
                presets.forEach { (label, time) ->
                    Text(label, Modifier.fillMaxWidth().clickable { onSet(time) }.padding(vertical = 12.dp))
                }
                Text("Pick date & time…", Modifier.fillMaxWidth().clickable {
                    val now = Calendar.getInstance()
                    DatePickerDialog(ctx, { _, y, m, d ->
                        TimePickerDialog(ctx, { _, h, min ->
                            val c = Calendar.getInstance().apply { set(y, m, d, h, min, 0) }
                            onSet(c.timeInMillis)
                        }, 9, 0, true).show()
                    }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show()
                }.padding(vertical = 12.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        dismissButton = if (current > 0) ({ TextButton(onClick = { onSet(0) }) { Text("Remove reminder") } }) else null,
    )
}
