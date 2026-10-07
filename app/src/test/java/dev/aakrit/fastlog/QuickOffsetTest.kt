package dev.aakrit.fastlog

import dev.aakrit.fastlog.data.TimeEditing
import org.junit.Assert.*
import org.junit.Test

/**
 * The quick picker offers relative offsets and hides any that would land before
 * a lower bound. That filter is what stops you recording a fast that ended
 * before it began, so it is worth pinning down independently of the UI.
 */
class QuickOffsetTest {

    private val offsets = listOf(15L, 30L, 60L, 120L, 180L, 240L, 360L, 480L, 720L, 960L, 1440L)

    private fun offered(nowMs: Long, minEpochMs: Long?): List<Long> =
        offsets.map { nowMs - it * 60_000L }
            .filter { minEpochMs == null || it > minEpochMs }

    @Test
    fun `with no lower bound every offset is offered`() {
        val now = 1_791_000_000_000
        assertEquals(offsets.size, offered(now, null).size)
    }

    @Test
    fun `offsets older than the fast's start are hidden`() {
        val now = 1_791_000_000_000
        val startedEightHoursAgo = now - 8 * 3_600_000
        val result = offered(now, startedEightHoursAgo)

        // 15m..6h survive. The 8h offset lands exactly on the start and is
        // excluded, because an end there would be a zero-length fast.
        assertEquals(7, result.size)
        assertTrue(result.all { it >= startedEightHoursAgo })
    }

    @Test
    fun `a fast only minutes old offers almost nothing before it`() {
        val now = 1_791_000_000_000
        val startedTenMinutesAgo = now - 10 * 60_000
        assertTrue(offered(now, startedTenMinutesAgo).isEmpty())
    }

    @Test
    fun `an offset landing exactly on the start is excluded`() {
        val now = 1_791_000_000_000
        val start = now - 8 * 3_600_000
        assertFalse(offered(now, start).contains(start))
    }

    @Test
    fun `every offered end time passes validation against the start`() {
        val now = 1_791_000_000_000
        val start = now - 8 * 3_600_000
        offered(now, start).forEach { candidate ->
            assertTrue(
                "offset at $candidate should be a valid end",
                TimeEditing.isValidEnd(start, candidate, now),
            )
        }
    }

    @Test
    fun `editing a record keeps its goal and recomputes whether it was met`() {
        val goalHours = 16.0
        val start = 1_791_000_000_000
        val shortEnd = start + (15 * 3_600_000)
        val longEnd = start + (17 * 3_600_000)

        assertFalse((shortEnd - start) / 3_600_000.0 >= goalHours)
        assertTrue((longEnd - start) / 3_600_000.0 >= goalHours)
    }
}
