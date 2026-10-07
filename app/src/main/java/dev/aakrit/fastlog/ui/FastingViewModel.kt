package dev.aakrit.fastlog.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.aakrit.fastlog.data.FastingRepository
import dev.aakrit.fastlog.data.Stats
import dev.aakrit.fastlog.data.StatsCalculator
import dev.aakrit.fastlog.model.ActiveFast
import dev.aakrit.fastlog.model.FastRecord
import dev.aakrit.fastlog.model.Protocol
import dev.aakrit.fastlog.notify.FastNotifier
import dev.aakrit.fastlog.notify.GoalAlarmScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

data class UiState(
    val active: ActiveFast? = null,
    val history: List<FastRecord> = emptyList(),
    val stats: Stats = Stats(0, 0, 0.0, 0.0, 0),
    val nowMs: Long = System.currentTimeMillis(),
    val selectedProtocol: Protocol = Protocol.byId("16:8")!!,
)

class FastingViewModel(app: Application) : AndroidViewModel(app) {

    private val context = app.applicationContext
    private val repo = FastingRepository.get(app)

    /** Ticks once a second so the running timer stays live. */
    private val now = MutableStateFlow(System.currentTimeMillis())

    private val selected = MutableStateFlow(Protocol.byId("16:8")!!)
    val selectedProtocol: StateFlow<Protocol> = selected.asStateFlow()

    /** The fast that just finished, driving the completion summary. */
    val justCompleted: StateFlow<FastRecord?> = repo.justCompleted

    fun acknowledgeCompletion() = repo.acknowledgeCompletion()

    val uiState: StateFlow<UiState> =
        combine(repo.state, now, selected) { state, nowMs, protocol ->
            UiState(
                active = state.active,
                history = state.history,
                stats = StatsCalculator.from(state.history),
                nowMs = nowMs,
                selectedProtocol = protocol,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    init {
        viewModelScope.launch {
            while (true) {
                now.value = System.currentTimeMillis()
                delay(1_000)
            }
        }
        // Resume showing whichever protocol the running fast uses.
        repo.state.value.active?.let { active ->
            selected.value = Protocol.resolve(active.protocolId, active.goalHours)
            // Safety net: if the boot broadcast never arrived (some OEMs drop it,
            // or the permission was granted after the fast began), opening the
            // app puts the indicator back.
            FastNotifier.showActiveFast(context, active)
            GoalAlarmScheduler.schedule(context, active)
        }
    }

    fun selectProtocol(protocol: Protocol) {
        selected.value = protocol
    }

    /**
     * Switches the plan of a fast already running. The elapsed time is kept; only
     * the target changes, so choosing a goal shorter than the time already served
     * drops the fast straight into its over-goal state.
     */
    fun changePlan(protocol: Protocol) {
        selected.value = protocol
        viewModelScope.launch {
            repo.updateGoal(protocol.fastHours, protocol.id)
            repo.state.value.active?.let { active ->
                FastNotifier.showActiveFast(context, active)
                // The old goal alarm is no longer correct for the new target.
                GoalAlarmScheduler.cancel(context)
                GoalAlarmScheduler.schedule(context, active)
            }
        }
    }

    fun startFast(startEpochMs: Long = System.currentTimeMillis()) {
        val protocol = selected.value
        viewModelScope.launch {
            repo.startFast(protocol.fastHours, protocol.id, startEpochMs)
            repo.state.value.active?.let { active ->
                FastNotifier.showActiveFast(context, active)
                GoalAlarmScheduler.schedule(context, active)
            }
        }
    }

    fun endFast(endEpochMs: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            repo.endFast(endEpochMs)
            FastNotifier.cancel(context)
            GoalAlarmScheduler.cancel(context)
        }
    }

    fun cancelFast() {
        viewModelScope.launch {
            repo.cancelFast()
            FastNotifier.cancel(context)
            GoalAlarmScheduler.cancel(context)
        }
    }

    fun updateStartTime(startEpochMs: Long) {
        viewModelScope.launch {
            repo.updateStartTime(startEpochMs)
            repo.state.value.active?.let { active ->
                FastNotifier.showActiveFast(context, active)
                GoalAlarmScheduler.schedule(context, active)
            }
        }
    }

    /** Re-posts the indicator, e.g. after notification permission is granted. */
    fun refreshNotification() {
        val active = repo.state.value.active ?: return
        FastNotifier.showActiveFast(context, active)
        GoalAlarmScheduler.schedule(context, active)
    }

    fun exportCsv(): String = repo.exportCsv()

    fun previewImport(csv: String): FastingRepository.ImportSummary = repo.previewImport(csv)

    fun importCsv(csv: String, onDone: (FastingRepository.ImportSummary) -> Unit) {
        viewModelScope.launch { onDone(repo.importCsv(csv)) }
    }

    fun updateRecord(id: String, startEpochMs: Long, endEpochMs: Long) {
        viewModelScope.launch { repo.updateRecord(id, startEpochMs, endEpochMs) }
    }

    fun deleteRecord(id: String) {
        viewModelScope.launch { repo.deleteRecord(id) }
    }
}
