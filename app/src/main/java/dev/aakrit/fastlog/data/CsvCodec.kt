package dev.aakrit.fastlog.data

import dev.aakrit.fastlog.model.FastRecord
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Converts history to and from CSV. Pure string work, no Android types, so it
 * can be unit tested on the JVM.
 *
 * Import is keyed on the record id, which is what makes restoring a backup
 * repeatable: importing the same file twice cannot duplicate history.
 */
object CsvCodec {

    const val HEADER = "id,protocol,goal_hours,start_iso,end_iso,duration_hours,goal_met"

    private val iso: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT

    fun export(history: List<FastRecord>): String {
        val sb = StringBuilder()
        sb.appendLine(HEADER)
        history.sortedByDescending { it.endEpochMs }.forEach { r ->
            sb.appendLine(
                listOf(
                    r.id,
                    r.protocolId,
                    fmt(r.goalHours),
                    iso.format(Instant.ofEpochMilli(r.startEpochMs)),
                    iso.format(Instant.ofEpochMilli(r.endEpochMs)),
                    fmt(r.durationHours),
                    if (r.goalReached) "yes" else "no",
                ).joinToString(",") { escape(it) }
            )
        }
        return sb.toString()
    }

    /**
     * Parses a CSV back into records. Malformed rows are skipped rather than
     * failing the whole import, so one bad line cannot cost you a restore.
     */
    fun parse(csv: String): ParseResult {
        val lines = csv.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        if (lines.isEmpty()) return ParseResult(emptyList(), 0)

        // Tolerate a missing header: only skip the first line if it looks like one.
        val body = if (lines.first().startsWith("id,")) lines.drop(1) else lines

        val records = mutableListOf<FastRecord>()
        var skipped = 0

        body.forEach { line ->
            val cells = splitCsvLine(line)
            if (cells.size < 5) {
                skipped++
                return@forEach
            }
            val parsed = runCatching {
                FastRecord(
                    id = cells[0],
                    protocolId = cells[1],
                    goalHours = cells[2].toDouble(),
                    startEpochMs = Instant.parse(cells[3]).toEpochMilli(),
                    endEpochMs = Instant.parse(cells[4]).toEpochMilli(),
                )
            }.getOrNull()

            if (parsed == null || parsed.id.isBlank() || parsed.endEpochMs < parsed.startEpochMs) {
                skipped++
            } else {
                records.add(parsed)
            }
        }
        return ParseResult(records, skipped)
    }

    /**
     * Merges imported records into existing history, keyed on id.
     * Existing records always win, so an import can never overwrite local data.
     */
    fun merge(existing: List<FastRecord>, incoming: List<FastRecord>): MergeResult {
        val byId = existing.associateBy { it.id }.toMutableMap()
        var added = 0
        var duplicates = 0

        incoming.forEach { record ->
            if (byId.containsKey(record.id)) {
                duplicates++
            } else {
                byId[record.id] = record
                added++
            }
        }
        return MergeResult(
            merged = byId.values.sortedByDescending { it.endEpochMs },
            added = added,
            duplicates = duplicates,
        )
    }

    private fun fmt(value: Double): String = String.format(Locale.US, "%.2f", value)

    private fun escape(value: String): String =
        if (value.contains(',') || value.contains('"')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    /** Minimal RFC-4180 splitter: handles quoted cells and escaped quotes. */
    private fun splitCsvLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    cells.add(current.toString()); current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        cells.add(current.toString())
        return cells.map { it.trim() }
    }

    data class ParseResult(val records: List<FastRecord>, val skipped: Int)
    data class MergeResult(val merged: List<FastRecord>, val added: Int, val duplicates: Int)
}
