package dev.aakrit.fastlog.data

import android.content.Context
import dev.aakrit.fastlog.model.ActiveFast
import dev.aakrit.fastlog.model.FastRecord
import dev.aakrit.fastlog.model.FastingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Stores all fasting data in a single JSON file inside the app's private
 * internal storage. Nothing leaves the device: the app holds no INTERNET
 * permission, so it could not upload this even if it tried.
 */
class FastingRepository private constructor(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)
    private val backupFile = File(context.filesDir, BACKUP_NAME)
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val _state = MutableStateFlow(readFromDisk())
    val state: StateFlow<FastingState> = _state.asStateFlow()

    /**
     * The fast that just finished, for the completion summary. Deliberately not
     * persisted: it is a one-off hand-off to the UI, not a stored fact, so a
     * restart should not resurface a summary the user already dismissed.
     */
    private val _justCompleted = MutableStateFlow<FastRecord?>(null)
    val justCompleted: StateFlow<FastRecord?> = _justCompleted.asStateFlow()

    fun acknowledgeCompletion() {
        _justCompleted.value = null
    }

    private fun readFromDisk(): FastingState =
        runCatching {
            if (file.exists()) json.decodeFromString<FastingState>(file.readText())
            else FastingState()
        }.getOrElse { FastingState() }

    private suspend fun persist(next: FastingState) {
        _state.value = next
        withContext(Dispatchers.IO) {
            runCatching {
                // Write to a temp file first so a crash mid-write cannot corrupt history.
                val tmp = File(file.parentFile, "$FILE_NAME.tmp")
                tmp.writeText(json.encodeToString(next))
                tmp.renameTo(file)
            }
            // Always keep a restorable CSV on disk, even if Export is never tapped.
            runCatching { backupFile.writeText(CsvCodec.export(next.history)) }
        }
    }

    /** The CSV text for the current history, for sharing out via the file picker. */
    fun exportCsv(): String = CsvCodec.export(_state.value.history)

    /**
     * Merges a CSV backup into history. Existing records win, so importing the
     * same file twice is harmless.
     */
    suspend fun importCsv(csv: String): ImportSummary {
        val parsed = CsvCodec.parse(csv)
        val merged = CsvCodec.merge(_state.value.history, parsed.records)
        if (merged.added > 0) {
            persist(_state.value.copy(history = merged.merged))
        }
        return ImportSummary(
            added = merged.added,
            duplicates = merged.duplicates,
            skipped = parsed.skipped,
        )
    }

    /** Parses without writing, so the UI can show what an import would do. */
    fun previewImport(csv: String): ImportSummary {
        val parsed = CsvCodec.parse(csv)
        val merged = CsvCodec.merge(_state.value.history, parsed.records)
        return ImportSummary(
            added = merged.added,
            duplicates = merged.duplicates,
            skipped = parsed.skipped,
        )
    }

    data class ImportSummary(val added: Int, val duplicates: Int, val skipped: Int)

    suspend fun startFast(goalHours: Double, protocolId: String, startEpochMs: Long) {
        persist(
            _state.value.copy(
                active = ActiveFast(
                    startEpochMs = startEpochMs,
                    goalHours = goalHours,
                    protocolId = protocolId,
                )
            )
        )
    }

    /** Ends the running fast and files it into history. */
    suspend fun endFast(endEpochMs: Long) {
        val active = _state.value.active ?: return
        // Guard against an end time before the start time.
        val safeEnd = maxOf(endEpochMs, active.startEpochMs)
        val record = FastRecord(
            id = UUID.randomUUID().toString(),
            startEpochMs = active.startEpochMs,
            endEpochMs = safeEnd,
            goalHours = active.goalHours,
            protocolId = active.protocolId,
        )
        persist(
            _state.value.copy(
                active = null,
                history = (_state.value.history + record).sortedByDescending { it.endEpochMs },
            )
        )
        _justCompleted.value = record
    }

    /** Throws away the running fast without recording it. */
    suspend fun cancelFast() {
        persist(_state.value.copy(active = null))
    }

    /**
     * Switches the plan of a fast already under way. The start time is left
     * alone, so the elapsed time carries over; only the target moves. Picking a
     * goal shorter than the time already elapsed is allowed, and simply puts the
     * fast straight into its over-goal state.
     */
    suspend fun updateGoal(goalHours: Double, protocolId: String) {
        val active = _state.value.active ?: return
        persist(
            _state.value.copy(
                active = active.copy(goalHours = goalHours, protocolId = protocolId),
            )
        )
    }

    /** Adjusts when the running fast began, e.g. "I actually started at 8pm". */
    suspend fun updateStartTime(startEpochMs: Long) {
        val active = _state.value.active ?: return
        persist(_state.value.copy(active = active.copy(startEpochMs = startEpochMs)))
    }

    /**
     * Rewrites the times on a finished fast, for when you could not log it as it
     * happened. The goal is left alone: you are correcting when it happened, not
     * what you were aiming for.
     */
    suspend fun updateRecord(id: String, startEpochMs: Long, endEpochMs: Long) {
        if (endEpochMs < startEpochMs) return
        val updated = _state.value.history.map { record ->
            if (record.id == id) {
                record.copy(startEpochMs = startEpochMs, endEpochMs = endEpochMs)
            } else {
                record
            }
        }
        persist(_state.value.copy(history = updated.sortedByDescending { it.endEpochMs }))
    }

    suspend fun deleteRecord(id: String) {
        persist(_state.value.copy(history = _state.value.history.filterNot { it.id == id }))
    }

    companion object {
        private const val FILE_NAME = "fastlog.json"
        private const val BACKUP_NAME = "fastlog-backup.csv"

        @Volatile
        private var instance: FastingRepository? = null

        /**
         * One repository per process.
         *
         * Previously every caller built its own, each with its own in-memory
         * state read from disk at construction. A write from a notification
         * action therefore never reached the flow the UI was collecting, and the
         * screen only caught up when the app was restarted and a fresh instance
         * re-read the file. Sharing one instance is what makes an end triggered
         * from the shade visible in the app immediately.
         */
        fun get(context: Context): FastingRepository =
            instance ?: synchronized(this) {
                instance ?: FastingRepository(context.applicationContext).also { instance = it }
            }
    }
}
