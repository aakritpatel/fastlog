package dev.aakrit.fastlog

import dev.aakrit.fastlog.data.ChartData
import dev.aakrit.fastlog.model.FastRecord
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ChartDataTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val today: LocalDate = LocalDate.of(2026, 3, 15)

    /** A fast of [hours] ending at midday on the day [daysAgo] before [today]. */
    private fun endingDaysAgo(id: String, daysAgo: Long, hours: Double, goal: Double = 16.0): FastRecord {
        val endMs = today.minusDays(daysAgo)
            .atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
        return FastRecord(
            id = id,
            startEpochMs = endMs - (hours * 3_600_000).toLong(),
            endEpochMs = endMs,
            goalHours = goal,
            protocolId = "16:8",
        )
    }

    @Test
    fun `series always spans the full requested window`() {
        val series = ChartData.build(emptyList(), days = 30, today = today, zone = zone)
        assertEquals(30, series.buckets.size)
        assertEquals(today, series.buckets.last().date)
        assertEquals(today.minusDays(29), series.buckets.first().date)
    }

    @Test
    fun `days with no fast appear as empty buckets so gaps stay visible`() {
        val series = ChartData.build(
            listOf(endingDaysAgo("a", 0, 16.0)),
            days = 7, today = today, zone = zone,
        )
        assertEquals(7, series.buckets.size)
        assertEquals(1, series.buckets.count { it.hours > 0 })
        assertEquals(6, series.buckets.count { it.hours == 0.0 })
    }

    @Test
    fun `two fasts ending on the same day are summed`() {
        val series = ChartData.build(
            listOf(endingDaysAgo("a", 1, 10.0), endingDaysAgo("b", 1, 6.0)),
            days = 7, today = today, zone = zone,
        )
        val bucket = series.buckets.first { it.date == today.minusDays(1) }
        assertEquals(16.0, bucket.hours, 0.01)
        assertEquals(2, bucket.fastCount)
    }

    @Test
    fun `fasts outside the window are excluded`() {
        val series = ChartData.build(
            listOf(endingDaysAgo("old", 40, 16.0), endingDaysAgo("recent", 2, 18.0)),
            days = 7, today = today, zone = zone,
        )
        assertEquals(18.0, series.maxHours, 0.01)
        assertEquals(1, series.daysFasted)
    }

    @Test
    fun `average counts only days actually fasted`() {
        // 2 fasts in a 30 day window: the average must be 16h, not 32/30.
        val series = ChartData.build(
            listOf(endingDaysAgo("a", 0, 16.0), endingDaysAgo("b", 5, 16.0)),
            days = 30, today = today, zone = zone,
        )
        assertEquals(16.0, series.averageHours, 0.01)
        assertEquals(2, series.daysFasted)
    }

    @Test
    fun `empty history produces a zeroed but well formed series`() {
        val series = ChartData.build(emptyList(), days = 7, today = today, zone = zone)
        assertEquals(0.0, series.maxHours, 0.001)
        assertEquals(0.0, series.averageHours, 0.001)
        assertEquals(0, series.daysFasted)
        assertTrue(series.buckets.all { it.hours == 0.0 })
    }

    @Test
    fun `a fast is attributed to the day it ended not the day it started`() {
        // Starts the previous day, ends today: belongs to today.
        val series = ChartData.build(
            listOf(endingDaysAgo("overnight", 0, 20.0)),
            days = 7, today = today, zone = zone,
        )
        assertEquals(20.0, series.buckets.last().hours, 0.01)
        assertEquals(0.0, series.buckets[series.buckets.size - 2].hours, 0.01)
    }

    @Test
    fun `a fast ending today is included at the window edge`() {
        val series = ChartData.build(
            listOf(endingDaysAgo("edge", 6, 16.0)),
            days = 7, today = today, zone = zone,
        )
        assertEquals(16.0, series.buckets.first().hours, 0.01)
    }
}
