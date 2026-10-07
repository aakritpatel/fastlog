package dev.aakrit.fastlog.data

import dev.aakrit.fastlog.model.FastRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class Stats(
    val totalFasts: Int,
    val goalsReached: Int,
    val longestHours: Double,
    val averageHours: Double,
    val currentStreakDays: Int,
)

object StatsCalculator {

    fun from(history: List<FastRecord>, zone: ZoneId = ZoneId.systemDefault()): Stats {
        if (history.isEmpty()) {
            return Stats(0, 0, 0.0, 0.0, 0)
        }
        return Stats(
            totalFasts = history.size,
            goalsReached = history.count { it.goalReached },
            longestHours = history.maxOf { it.durationHours },
            averageHours = history.sumOf { it.durationHours } / history.size,
            currentStreakDays = currentStreak(history, zone),
        )
    }

    /**
     * Counts back from today over the distinct days on which a fast ended.
     * A streak survives if you fasted today or yesterday; it breaks on the
     * first missing day before that.
     */
    private fun currentStreak(history: List<FastRecord>, zone: ZoneId): Int {
        val days = history
            .map { Instant.ofEpochMilli(it.endEpochMs).atZone(zone).toLocalDate() }
            .toSortedSet(reverseOrder())

        if (days.isEmpty()) return 0

        val today = LocalDate.now(zone)
        var cursor = when (days.first()) {
            today -> today
            today.minusDays(1) -> today.minusDays(1)
            else -> return 0 // most recent fast is older than yesterday
        }

        var streak = 0
        while (days.contains(cursor)) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }
}
