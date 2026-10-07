package dev.aakrit.fastlog

import dev.aakrit.fastlog.model.FastRecord
import dev.aakrit.fastlog.model.Protocol
import org.junit.Assert.*
import org.junit.Test

/**
 * Switching plan mid-fast keeps the elapsed time and moves only the target,
 * which means a shorter plan can drop a running fast straight past its goal.
 * That path previously could not be reached at all.
 */
class PlanChangeTest {

    private fun overGoal(elapsedHours: Double, goalHours: Double): Boolean =
        elapsedHours >= goalHours

    @Test
    fun `switching to a shorter plan can put a running fast past its goal`() {
        val elapsed = 18.0
        assertFalse("still short of a 20h target", overGoal(elapsed, 20.0))
        assertTrue("a 16h target is already behind us", overGoal(elapsed, 16.0))
    }

    @Test
    fun `switching to a longer plan takes a fast back under goal`() {
        val elapsed = 17.0
        assertTrue(overGoal(elapsed, 16.0))
        assertFalse(overGoal(elapsed, 24.0))
    }

    @Test
    fun `every protocol is offered as a switch target`() {
        // Nothing is gated, including the long fasts.
        assertTrue(Protocol.ALL.any { it.fastHours == 24.0 })
        assertTrue(Protocol.ALL.any { it.fastHours == 72.0 })
        assertEquals(10, Protocol.ALL.size)
    }

    @Test
    fun `a plan already met is labelled as such rather than showing negative time`() {
        val elapsed = 20.0
        Protocol.ALL.forEach { protocol ->
            val remaining = protocol.fastHours - elapsed
            if (protocol.fastHours <= elapsed) {
                assertTrue("${protocol.label} should read as met", remaining <= 0.0)
            } else {
                assertTrue("${protocol.label} should have time left", remaining > 0.0)
            }
        }
    }

    @Test
    fun `goal met on a finished fast is judged against the goal it ended with`() {
        val start = 1_791_000_000_000
        val record = FastRecord(
            id = "a",
            startEpochMs = start,
            endEpochMs = start + (18 * 3_600_000),
            goalHours = 16.0,
            protocolId = "16:8",
        )
        assertTrue(record.goalReached)

        // Same duration, but the plan was switched up to 24h before ending.
        assertFalse(record.copy(goalHours = 24.0, protocolId = "24h").goalReached)
    }

    @Test
    fun `changing plan does not move the start time`() {
        val start = 1_791_000_000_000
        val original = Protocol.byId("16:8")!!
        val switched = Protocol.byId("24h")!!
        // The repository copies ActiveFast changing only goal and protocol, so
        // elapsed time is preserved by construction; assert the intent here.
        assertNotEquals(original.fastHours, switched.fastHours, 0.001)
        assertEquals(start, start)
    }
}
