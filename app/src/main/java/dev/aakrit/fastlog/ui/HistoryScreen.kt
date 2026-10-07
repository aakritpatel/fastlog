package dev.aakrit.fastlog.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.aakrit.fastlog.data.Stats
import dev.aakrit.fastlog.model.FastRecord
import dev.aakrit.fastlog.model.Protocol

@Composable
fun HistoryScreen(
    state: UiState,
    onDelete: (String) -> Unit,
    onEdit: (String, Long, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<FastRecord?>(null) }
    var pendingEdit by remember { mutableStateOf<FastRecord?>(null) }

    if (state.history.isEmpty()) {
        Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "No fasts logged yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Finish a fast on the Timer tab and it will show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { StatsPanel(state.stats) }
        item {
            Text(
                "All fasts",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        items(state.history, key = { it.id }) { record ->
            HistoryRow(
                record = record,
                onDelete = { pendingDelete = record },
                onEdit = { pendingEdit = record },
            )
        }
    }

    pendingEdit?.let { record ->
        EditFastDialog(
            record = record,
            onDismiss = { pendingEdit = null },
            onSave = { start, end -> onEdit(record.id, start, end); pendingEdit = null },
        )
    }

    pendingDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this fast?") },
            text = {
                Text(
                    "${formatDuration(record.durationMs)} on ${formatDay(record.endEpochMs)} " +
                        "will be removed. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = { onDelete(record.id); pendingDelete = null }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun StatsPanel(stats: Stats) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                StatCell("Streak", "${stats.currentStreakDays}", "days", Modifier.weight(1f))
                StatCell("Total", "${stats.totalFasts}", "fasts", Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                StatCell(
                    "Longest",
                    Protocol.formatHours(stats.longestHours),
                    "best fast",
                    Modifier.weight(1f),
                )
                StatCell(
                    "Average",
                    Protocol.formatHours(stats.averageHours),
                    "per fast",
                    Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "${stats.goalsReached} of ${stats.totalFasts} fasts hit their goal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, caption: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HistoryRow(record: FastRecord, onDelete: () -> Unit, onEdit: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatDuration(record.durationMs),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(8.dp))
                    if (record.goalReached) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) { Text("goal met") }
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    "${record.protocol.label} · ${formatDay(record.startEpochMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${formatClock(record.startEpochMs)} → ${formatClock(record.endEpochMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onEdit) {
                    Text("Edit", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = onDelete) {
                    Text(
                        "Delete",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Edits the times on a finished fast. Both ends are editable because you may
 * have been away from your phone at either one. The resulting duration updates
 * live, so you can see what you are about to save.
 */
@Composable
private fun EditFastDialog(
    record: FastRecord,
    onDismiss: () -> Unit,
    onSave: (Long, Long) -> Unit,
) {
    var start by remember { mutableLongStateOf(record.startEpochMs) }
    var end by remember { mutableLongStateOf(record.endEpochMs) }
    var picking by remember { mutableStateOf<Which?>(null) }

    val now = System.currentTimeMillis()
    val valid = end > start
    val durationMs = (end - start).coerceAtLeast(0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit this fast") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                EditRow("Started", start) { picking = Which.Start }
                EditRow("Ended", end) { picking = Which.End }

                HorizontalDivider()

                if (valid) {
                    Text(
                        "Duration ${formatDuration(durationMs)}" +
                            if (durationMs / 3_600_000.0 >= record.goalHours) " · goal met" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Text(
                        "The end must come after the start.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    "The ${Protocol.formatHours(record.goalHours)} goal stays as it was.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(start, end) }, enabled = valid) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    when (picking) {
        Which.Start -> DateTimePickerDialog(
            title = "When did it start?",
            initialEpochMs = start,
            maxEpochMs = now,
            rangeHint = "A fast cannot start in the future.",
            onDismiss = { picking = null },
            onConfirm = { start = it; picking = null },
        )

        Which.End -> DateTimePickerDialog(
            title = "When did it end?",
            initialEpochMs = end,
            maxEpochMs = now,
            rangeHint = "A fast cannot end in the future.",
            onDismiss = { picking = null },
            onConfirm = { end = it; picking = null },
        )

        null -> Unit
    }
}

private enum class Which { Start, End }

@Composable
private fun EditRow(label: String, epochMs: Long, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${formatClock(epochMs)} · ${formatDay(epochMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                "Change",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
