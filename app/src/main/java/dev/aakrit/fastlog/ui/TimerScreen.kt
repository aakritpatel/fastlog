package dev.aakrit.fastlog.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.aakrit.fastlog.model.FastPhase
import dev.aakrit.fastlog.model.Protocol

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    state: UiState,
    onSelectProtocol: (Protocol) -> Unit,
    onStart: () -> Unit,
    onStartAt: (Long) -> Unit,
    onEnd: (Long) -> Unit,
    onEditStart: (Long) -> Unit,
    onChangePlan: (Protocol) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCustom by remember { mutableStateOf(false) }
    var showEndConfirm by remember { mutableStateOf(false) }
    // Each time edit opens the quick relative chooser first; "exact" escalates
    // to the full date picker.
    var quickPick by remember { mutableStateOf<TimeTarget?>(null) }
    var exactPick by remember { mutableStateOf<TimeTarget?>(null) }
    var showPlanPicker by remember { mutableStateOf(false) }

    val active = state.active
    val goalHours = active?.goalHours ?: state.selectedProtocol.fastHours
    val elapsedMs = if (active != null) (state.nowMs - active.startEpochMs).coerceAtLeast(0) else 0L
    val elapsedHours = elapsedMs / 3_600_000.0
    val goalMs = (goalHours * 3_600_000.0).toLong()
    val progress = if (goalMs > 0) (elapsedMs.toFloat() / goalMs.toFloat()) else 0f
    val overGoal = active != null && elapsedMs >= goalMs
    val phase = FastPhase.forHours(elapsedHours)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))

        TimerRing(
            progress = progress,
            centerLabel = if (active != null) formatElapsed(elapsedMs) else "0:00:00",
            topLabel = if (active != null) {
                if (overGoal) "GOAL REACHED" else "ELAPSED"
            } else "READY TO START",
            bottomLabel = if (active != null) {
                if (overGoal) {
                    val over = elapsedMs - goalMs
                    // Right on the boundary "0m past" reads like a glitch.
                    if (over < 60_000) "just reached your ${Protocol.formatHours(goalHours)} goal"
                    else "${formatDuration(over)} past your ${Protocol.formatHours(goalHours)} goal"
                }
                else "${formatDuration(goalMs - elapsedMs)} to go"
            } else "${Protocol.formatHours(goalHours)} goal",
            overGoal = overGoal,
        )

        Spacer(Modifier.height(20.dp))

        if (active != null) {
            if (overGoal) {
                OverGoalBanner(
                    overBy = elapsedMs - goalMs,
                    goalHours = goalHours,
                )
                Spacer(Modifier.height(10.dp))
            }

            PlanCard(
                protocol = Protocol.resolve(active.protocolId, active.goalHours),
                onChange = { showPlanPicker = true },
            )
            Spacer(Modifier.height(10.dp))

            PhaseCard(phase = phase, elapsedHours = elapsedHours)
            Spacer(Modifier.height(12.dp))
            Card(
                onClick = { quickPick = TimeTarget.EditStart },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "STARTED",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${formatClock(active.startEpochMs)} · ${formatDay(active.startEpochMs)}",
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
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { showEndConfirm = true },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(27.dp),
            ) {
                Text("End fast", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onCancel) {
                Text("Discard without saving", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Text(
                "Choose a protocol",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start),
            )
            Spacer(Modifier.height(10.dp))
            ProtocolRow(
                selected = state.selectedProtocol,
                onSelect = onSelectProtocol,
                onCustom = { showCustom = true },
            )
            Spacer(Modifier.height(16.dp))
            PhasePreview(goalHours = goalHours)
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(27.dp),
            ) {
                Text("Start ${state.selectedProtocol.label} fast", fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = { quickPick = TimeTarget.BackdatedStart }) {
                Text("I started earlier", color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(28.dp))
    }

    if (showCustom) {
        CustomLengthDialog(
            initialHours = state.selectedProtocol.fastHours,
            onDismiss = { showCustom = false },
            onConfirm = { hours ->
                onSelectProtocol(Protocol.custom(hours))
                showCustom = false
            },
        )
    }

    if (showEndConfirm) {
        AlertDialog(
            onDismissRequest = { showEndConfirm = false },
            title = { Text("End this fast?") },
            text = {
                Text(
                    "You're at ${formatDuration(elapsedMs)} of a " +
                        "${Protocol.formatHours(goalHours)} goal. It will be saved to your history."
                )
            },
            confirmButton = {
                TextButton(onClick = { showEndConfirm = false; onEnd(System.currentTimeMillis()) }) {
                    Text("End now")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(onClick = { showEndConfirm = false }) { Text("Keep fasting") }
                    TextButton(onClick = { showEndConfirm = false; quickPick = TimeTarget.End }) {
                        Text("Ended earlier")
                    }
                }
            },
        )
    }

    if (showPlanPicker && active != null) {
        ChangePlanDialog(
            current = Protocol.resolve(active.protocolId, active.goalHours),
            elapsedHours = elapsedHours,
            onDismiss = { showPlanPicker = false },
            onSelect = { showPlanPicker = false; onChangePlan(it) },
        )
    }

    quickPick?.let { target ->
        QuickTimeDialog(
            title = target.title,
            nowMs = state.nowMs,
            // An end time can never precede the start of its own fast.
            minEpochMs = if (target == TimeTarget.End) active?.startEpochMs else null,
            onDismiss = { quickPick = null },
            onPick = { chosen ->
                quickPick = null
                applyTime(target, chosen, onStartAt, onEnd, onEditStart)
            },
            onPickExact = { quickPick = null; exactPick = target },
        )
    }

    exactPick?.let { target ->
        DateTimePickerDialog(
            title = target.title,
            initialEpochMs = if (target == TimeTarget.EditStart) {
                active?.startEpochMs ?: state.nowMs
            } else {
                state.nowMs
            },
            minEpochMs = if (target == TimeTarget.End) active?.startEpochMs else null,
            maxEpochMs = state.nowMs,
            rangeHint = if (target == TimeTarget.End) {
                "Pick a time between when the fast started and now."
            } else {
                "A fast cannot start in the future."
            },
            onDismiss = { exactPick = null },
            onConfirm = { chosen ->
                exactPick = null
                applyTime(target, chosen, onStartAt, onEnd, onEditStart)
            },
        )
    }
}

internal enum class TimeTarget(val title: String) {
    BackdatedStart("When did you start fasting?"),
    EditStart("When did this fast start?"),
    End("When did you break the fast?"),
}

private fun applyTime(
    target: TimeTarget,
    chosen: Long,
    onStartAt: (Long) -> Unit,
    onEnd: (Long) -> Unit,
    onEditStart: (Long) -> Unit,
) {
    when (target) {
        TimeTarget.BackdatedStart -> onStartAt(chosen)
        TimeTarget.EditStart -> onEditStart(chosen)
        TimeTarget.End -> onEnd(chosen)
    }
}

@Composable
private fun ProtocolRow(
    selected: Protocol,
    onSelect: (Protocol) -> Unit,
    onCustom: () -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(Protocol.ALL) { protocol ->
            val isSelected = protocol.id == selected.id
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(protocol) },
                label = { Text(protocol.label) },
            )
        }
        item {
            FilterChip(
                selected = selected.id == Protocol.CUSTOM_ID,
                onClick = onCustom,
                label = {
                    Text(
                        if (selected.id == Protocol.CUSTOM_ID) selected.label else "Custom"
                    )
                },
            )
        }
    }
}

@Composable
private fun PhaseCard(phase: FastPhase, elapsedHours: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    phase.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "stage ${FastPhase.indexForHours(elapsedHours) + 1}/${FastPhase.ALL.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                phase.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PhasePreview(goalHours: Double) {
    val reached = FastPhase.ALL.filter { it.startHour < goalHours }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Stages you'll pass through",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            reached.forEach { phase ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text(
                        "${phase.startHour.toInt()}h",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(44.dp),
                    )
                    Text(
                        phase.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomLengthDialog(
    initialHours: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var hours by remember { mutableStateOf(initialHours.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom fast length") },
        text = {
            Column {
                Text(
                    Protocol.formatHours(hours.toDouble()),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = hours,
                    onValueChange = { hours = Math.round(it * 2f) / 2f },
                    valueRange = 1f..168f,
                )
                Text(
                    "Anywhere from 1 hour to 7 days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(hours.toDouble()) }) { Text("Use it") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Names the plan a running fast is on, and lets you switch it. */
@Composable
private fun PlanCard(protocol: Protocol, onChange: () -> Unit) {
    Card(
        onClick = onChange,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "PLAN",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${protocol.label} · ${Protocol.formatHours(protocol.fastHours)} goal",
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

/**
 * Shown once a fast passes its goal. Reachable the ordinary way by fasting on,
 * and now also by switching to a plan shorter than the time already served.
 */
@Composable
private fun OverGoalBanner(overBy: Long, goalHours: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "Goal reached",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (overBy < 60_000) {
                    "You've just hit your ${Protocol.formatHours(goalHours)} goal. " +
                        "End whenever you're ready, or keep going."
                } else {
                    "You're ${formatDuration(overBy)} past your " +
                        "${Protocol.formatHours(goalHours)} goal. End whenever you're ready, " +
                        "or keep going."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

/**
 * Switches the plan mid-fast. Each option states what it would mean right now,
 * so picking a goal already behind you is a deliberate choice rather than a
 * surprise.
 */
@Composable
private fun ChangePlanDialog(
    current: Protocol,
    elapsedHours: Double,
    onDismiss: () -> Unit,
    onSelect: (Protocol) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change plan") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "You're ${Protocol.formatHours(elapsedHours)} in. Switching keeps that " +
                        "time and only moves the target.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Protocol.ALL.forEach { protocol ->
                    val isCurrent = protocol.id == current.id
                    val alreadyPast = protocol.fastHours <= elapsedHours
                    Card(
                        onClick = { onSelect(protocol) },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrent) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    protocol.label + if (isCurrent) " · current" else "",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    protocol.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                if (alreadyPast) "already met" else
                                    "${Protocol.formatHours(protocol.fastHours - elapsedHours)} to go",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (alreadyPast) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
