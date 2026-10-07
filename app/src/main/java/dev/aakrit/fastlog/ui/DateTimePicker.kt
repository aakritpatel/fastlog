package dev.aakrit.fastlog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.aakrit.fastlog.data.TimeEditing
import java.time.Instant
import java.time.ZoneId

/**
 * Two-step date then time picker, returning an epoch-millis instant.
 *
 * Material's DatePicker works in UTC midnight, so converting to and from a
 * local calendar day has to go through UTC explicitly. Doing it via the system
 * zone would shift the date by a day for anyone east or west of UTC.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(
    title: String,
    initialEpochMs: Long,
    /** Instants outside this range are rejected, with [rangeHint] explaining why. */
    minEpochMs: Long? = null,
    maxEpochMs: Long? = null,
    rangeHint: String = "",
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    var step by remember { mutableStateOf(Step.Date) }
    var error by remember { mutableStateOf<String?>(null) }

    val initial = remember(initialEpochMs) {
        Instant.ofEpochMilli(initialEpochMs).atZone(zone)
    }

    // Seed the date picker with UTC midnight of the chosen LOCAL day.
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = TimeEditing.toPickerDate(initialEpochMs, zone),
    )
    val timePickerState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = false,
    )

    fun resolve(): Long? {
        val dateMillis = datePickerState.selectedDateMillis ?: return null
        return TimeEditing.combine(dateMillis, timePickerState.hour, timePickerState.minute, zone)
    }

    when (step) {
        Step.Date -> DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = { step = Step.Time },
                    enabled = datePickerState.selectedDateMillis != null,
                ) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        ) {
            DatePicker(state = datePickerState, title = {
                Text(title, Modifier.padding(start = 24.dp, top = 16.dp))
            })
        }

        Step.Time -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state = timePickerState)
                    error?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val value = resolve()
                    when {
                        value == null -> error = "Pick a date first."
                        minEpochMs != null && value < minEpochMs ->
                            error = rangeHint.ifBlank { "That is too early." }
                        maxEpochMs != null && value > maxEpochMs ->
                            error = rangeHint.ifBlank { "That is in the future." }
                        else -> onConfirm(value)
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { error = null; step = Step.Date }) { Text("Back") }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            },
        )
    }
}

private enum class Step { Date, Time }

@Composable
private fun Row(
    horizontalArrangement: Arrangement.Horizontal,
    content: @Composable () -> Unit,
) = androidx.compose.foundation.layout.Row(
    horizontalArrangement = horizontalArrangement,
    verticalAlignment = Alignment.CenterVertically,
    content = { content() },
)
