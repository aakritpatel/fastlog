package dev.aakrit.fastlog.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aakrit.fastlog.data.ChartData
import dev.aakrit.fastlog.model.FastPhase
import dev.aakrit.fastlog.model.FastRecord
import dev.aakrit.fastlog.model.Protocol

/**
 * Shown once, immediately after a fast ends, from wherever it was ended.
 *
 * Its job is to confirm exactly what went into history, so finishing a fast is
 * never silent: before this existed, ending a fast simply reset the timer and
 * left you guessing whether it had been recorded.
 */
@Composable
fun CompletionScreen(
    record: FastRecord,
    historyCount: Int,
    currentStreakDays: Int,
    onDone: () -> Unit,
    onSeeHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        CompletionContent(record, historyCount, currentStreakDays, onDone, onSeeHistory)
    }
}

/**
 * Painted on its own Surface because this screen renders outside the Scaffold.
 * Without it the activity theme's window background shows through, which is dark
 * even in light mode and leaves the heading nearly unreadable.
 */
@Composable
private fun CompletionContent(
    record: FastRecord,
    historyCount: Int,
    currentStreakDays: Int,
    onDone: () -> Unit,
    onSeeHistory: () -> Unit,
) {
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 700),
        label = "completionRing",
    )
    val phase = FastPhase.forHours(record.durationHours)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))

        Text(
            if (record.goalReached) "Fast complete" else "Fast logged",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (record.goalReached) {
                "You hit your ${Protocol.formatHours(record.goalHours)} goal."
            } else {
                "Saved to your history."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(20.dp))

        TimerRing(
            progress = progress,
            centerLabel = formatDuration(record.durationMs),
            topLabel = "YOU FASTED FOR",
            bottomLabel = if (record.goalReached) {
                val over = record.durationMs - (record.goalHours * 3_600_000.0).toLong()
                if (over > 60_000) "${formatDuration(over)} past goal" else "right on target"
            } else {
                "${formatDuration((record.goalHours * 3_600_000.0).toLong() - record.durationMs)} short of goal"
            },
            overGoal = record.goalReached,
        )

        Spacer(Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(Modifier.padding(16.dp)) {
                SummaryLine("Plan", record.protocol.label)
                SummaryLine("Goal", Protocol.formatHours(record.goalHours))
                SummaryLine("Started", "${formatClock(record.startEpochMs)} · ${formatDay(record.startEpochMs)}")
                SummaryLine("Ended", "${formatClock(record.endEpochMs)} · ${formatDay(record.endEpochMs)}")
                SummaryLine("Reached stage", phase.title)
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                SummaryLine("Fasts logged", "$historyCount")
                SummaryLine(
                    "Current streak",
                    if (currentStreakDays == 1) "1 day" else "$currentStreakDays days",
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(26.dp),
        ) {
            Text("Done", fontWeight = FontWeight.SemiBold)
        }
        TextButton(onClick = onSeeHistory) {
            Text("See full history", color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
        )
    }
}
