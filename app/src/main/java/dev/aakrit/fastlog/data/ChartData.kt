package dev.aakrit.fastlog.data

import dev.aakrit.fastlog.model.FastRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One bar on the chart: a calendar day and the hours fasted on it. */
data class DayBucket(
    val date: LocalDate,
    val hours: Double,
    val fastCount: Int,
)

data class ChartSeries(
    val buckets: List<DayBucket>,
    val maxHours: Double,
    val averageHours: Double,
    val daysFasted: Int,
)

object ChartData {

    /**
     * Buckets history into one entry per calendar day across the whole range,
     * including days with no fast so gaps in the habit stay visible.
     *
     * A fast is attributed to the day it ENDED, matching how the streak counts.
     * Two fasts ending on the same day are summed.
     */
    fun build(
        history: List<FastRecord>,
        days: Int,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): ChartSeries {
        val start = today.minusDays((days - 1).toLong())

        val totals = mutableMapOf<LocalDate, Double>()
        val counts = mutableMapOf<LocalDate, Int>()

        history.forEach { record ->
            val day = Instant.ofEpochMilli(record.endEpochMs).atZone(zone).toLocalDate()
            if (!day.isBefore(start) && !day.isAfter(today)) {
                totals[day] = (totals[day] ?: 0.0) + record.durationHours
                counts[day] = (counts[day] ?: 0) + 1
            }
        }

        val buckets = (0 until days).map { offset ->
            val date = start.plusDays(offset.toLong())
            DayBucket(
                date = date,
                hours = totals[date] ?: 0.0,
                fastCount = counts[date] ?: 0,
            )
        }

        val fastedDays = buckets.filter { it.hours > 0.0 }
        return ChartSeries(
            buckets = buckets,
            maxHours = buckets.maxOfOrNull { it.hours } ?: 0.0,
            // Average over days actually fasted, not over the whole window:
            // dividing by empty days would make every range look worse the longer it is.
            averageHours = if (fastedDays.isEmpty()) 0.0
            else fastedDays.sumOf { it.hours } / fastedDays.size,
            daysFasted = fastedDays.size,
        )
    }
}
