package dev.aakrit.fastlog.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.aakrit.fastlog.data.ChartData
import dev.aakrit.fastlog.model.Protocol
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class Range(val label: String, val days: Int)

private val RANGES = listOf(
    Range("7 days", 7),
    Range("30 days", 30),
    Range("90 days", 90),
)

@Composable
fun ChartScreen(
    state: UiState,
    modifier: Modifier = Modifier,
) {
    var rangeIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedBar by remember { mutableStateOf<Int?>(null) }

    val range = RANGES[rangeIndex]
    val series = remember(state.history, range.days) {
        ChartData.build(state.history, range.days)
    }

    // A range change invalidates any bar selection from the previous range.
    LaunchedEffect(rangeIndex) { selectedBar = null }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RANGES.forEachIndexed { index, r ->
                FilterChip(
                    selected = index == rangeIndex,
                    onClick = { rangeIndex = index },
                    label = { Text(r.label) },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (state.history.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().height(220.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Finish a fast and it will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Text(
                "Hours fasted, by day completed",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                // A 36h or 48h fast lands wholly on the day it ends, so a single
                // bar can legitimately read above 24h. Say so rather than letting
                // the reader think the chart is broken.
                "A fast counts on the day it ends, so a long fast can exceed 24h.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            FastingBarChart(
                series = series,
                selectedIndex = selectedBar,
                onSelectBar = { selectedBar = it },
            )
            Spacer(Modifier.height(10.dp))

            val selected = selectedBar?.let { series.buckets.getOrNull(it) }
            if (selected != null) {
                SelectionCard(
                    dateLabel = selected.date.format(
                        DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())
                    ),
                    hours = selected.hours,
                    fastCount = selected.fastCount,
                )
            } else {
                Text(
                    "Tap a bar for that day's detail. The dashed line is your average.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))
            SummaryCard(
                average = series.averageHours,
                daysFasted = series.daysFasted,
                totalDays = range.days,
                longest = series.maxHours,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SelectionCard(dateLabel: String, hours: Double, fastCount: Int) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                dateLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                if (hours <= 0.0) "No fast" else Protocol.formatHours(hours),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            if (fastCount > 1) {
                Text(
                    "$fastCount fasts combined",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(average: Double, daysFasted: Int, totalDays: Int, longest: Double) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "AVERAGE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        Protocol.formatHours(average),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "on days you fasted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "DAYS FASTED",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "$daysFasted / $totalDays",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "best ${Protocol.formatHours(longest)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
