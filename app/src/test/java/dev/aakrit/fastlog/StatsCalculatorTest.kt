package dev.aakrit.fastlog

import dev.aakrit.fastlog.data.StatsCalculator
import dev.aakrit.fastlog.model.FastRecord
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class StatsCalculatorTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val day = 86_400_000L

    private fun endingDaysAgo(id: String, daysAgo: Long, hours: Double, goal: Double = 16.0): FastRecord {
        val end = System.currentTimeMillis() - daysAgo * day
        return FastRecord(
            id = id,
            startEpochMs = end - (hours * 3_600_000).toLong(),
            endEpochMs = end,
            goalHours = goal,
            protocolId = "16:8",
        )
    }

    @Test
    fun `empty history is all zeroes`() {
        val stats = StatsCalculator.from(emptyList(), zone)
        assertEquals(0, stats.totalFasts)
        assertEquals(0, stats.currentStreakDays)
        assertEquals(0.0, stats.longestHours, 0.001)
    }

    @Test
    fun `consecutive days build a streak`() {
        val stats = StatsCalculator.from(
            (0L..4L).map { endingDaysAgo("d$it", it, 16.0) }, zone,
        )
        assertEquals(5, stats.currentStreakDays)
    }

    @Test
    fun `a missing day breaks the streak`() {
        // today, yesterday, then a gap at 2 days ago, then 3 days ago.
        val stats = StatsCalculator.from(
            listOf(
                endingDaysAgo("a", 0, 16.0),
                endingDaysAgo("b", 1, 16.0),
                endingDaysAgo("d", 3, 16.0),
            ),
            zone,
        )
        assertEquals(2, stats.currentStreakDays)
    }

    @Test
    fun `a streak ending yesterday still counts`() {
        val stats = StatsCalculator.from(
            listOf(endingDaysAgo("a", 1, 16.0), endingDaysAgo("b", 2, 16.0)), zone,
        )
        assertEquals(2, stats.currentStreakDays)
    }

    @Test
    fun `a streak that stopped before yesterday is zero`() {
        val stats = StatsCalculator.from(
            listOf(endingDaysAgo("a", 4, 16.0), endingDaysAgo("b", 5, 16.0)), zone,
        )
        assertEquals(0, stats.currentStreakDays)
    }

    @Test
    fun `goal met is judged against each fast's own goal`() {
        val stats = StatsCalculator.from(
            listOf(
                endingDaysAgo("hit", 0, 16.5, goal = 16.0),
                endingDaysAgo("miss", 1, 15.0, goal = 16.0),
                endingDaysAgo("hitLong", 2, 25.0, goal = 24.0),
            ),
            zone,
        )
        assertEquals(3, stats.totalFasts)
        assertEquals(2, stats.goalsReached)
    }

    @Test
    fun `longest and average are computed over all fasts`() {
        val stats = StatsCalculator.from(
            listOf(
                endingDaysAgo("a", 0, 12.0),
                endingDaysAgo("b", 1, 24.0),
            ),
            zone,
        )
        assertEquals(24.0, stats.longestHours, 0.01)
        assertEquals(18.0, stats.averageHours, 0.01)
    }

    @Test
    fun `two fasts on the same day count once toward the streak`() {
        val stats = StatsCalculator.from(
            listOf(
                endingDaysAgo("a", 0, 8.0),
                endingDaysAgo("b", 0, 6.0),
                endingDaysAgo("c", 1, 16.0),
            ),
            zone,
        )
        assertEquals(2, stats.currentStreakDays)
        assertEquals(3, stats.totalFasts)
    }
}
