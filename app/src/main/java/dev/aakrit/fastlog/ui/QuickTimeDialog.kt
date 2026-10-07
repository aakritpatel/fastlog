package dev.aakrit.fastlog.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Relative time chooser: "I started two hours ago".
 *
 * Backdating is nearly always recent — you ate, then opened the app a few hours
 * later — so the common case is one tap here rather than a date grid. The exact
 * picker stays available for anything older.
 */
@Composable
fun QuickTimeDialog(
    title: String,
    nowMs: Long,
    /**
     * Offsets at or before this instant are hidden. Strictly after, not at: an
     * end landing exactly on the fast's own start would record a zero-length fast.
     */
    minEpochMs: Long? = null,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
    onPickExact: () -> Unit,
) {
    val offsets = remember2(nowMs, minEpochMs) {
        OFFSET_MINUTES
            .map { minutes -> minutes to nowMs - minutes * 60_000L }
            .filter { (_, instant) -> minEpochMs == null || instant > minEpochMs }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OptionRow(
                    label = "Just now",
                    detail = formatClock(nowMs),
                    onClick = { onPick(nowMs) },
                )
                offsets.forEach { (minutes, instant) ->
                    OptionRow(
                        label = labelFor(minutes),
                        detail = "${formatClock(instant)} · ${formatDay(instant)}",
                        onClick = { onPick(instant) },
                    )
                }
                OptionRow(
                    label = "Pick exact date & time",
                    detail = "For anything older",
                    onClick = onPickExact,
                    emphasised = true,
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun OptionRow(
    label: String,
    detail: String,
    onClick: () -> Unit,
    emphasised: Boolean = false,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (emphasised) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (emphasised) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (emphasised) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val OFFSET_MINUTES = listOf(15L, 30L, 60L, 120L, 180L, 240L, 360L, 480L, 720L, 960L, 1440L)

private fun labelFor(minutes: Long): String = when {
    minutes < 60 -> "$minutes minutes ago"
    minutes == 60L -> "1 hour ago"
    minutes % 60 == 0L && minutes < 1440 -> "${minutes / 60} hours ago"
    minutes == 1440L -> "Yesterday, this time"
    else -> "${minutes / 60} hours ago"
}

@Composable
private fun <A, B, R> remember2(a: A, b: B, block: () -> R): R =
    androidx.compose.runtime.remember(a, b) { block() }
