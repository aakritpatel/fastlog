package dev.aakrit.fastlog

import dev.aakrit.fastlog.data.TimeEditing
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TimeEditingTest {

    private val kolkata: ZoneId = ZoneId.of("Asia/Kolkata")   // UTC+5:30, no DST
    private val newYork: ZoneId = ZoneId.of("America/New_York") // has DST
    private val utc: ZoneId = ZoneId.of("UTC")

    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int, zone: ZoneId): Long =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `round trips a local instant through the picker without shifting the day`() {
        // 00:30 local in Kolkata is still the previous day in UTC: the classic trap.
        val original = ms(2026, 10, 4, 0, 30, kolkata)
        val pickerDate = TimeEditing.toPickerDate(original, kolkata)
        val rebuilt = TimeEditing.combine(pickerDate, 0, 30, kolkata)
        assertEquals(original, rebuilt)
    }

    @Test
    fun `late evening local time also round trips`() {
        // 23:45 local in New York is already the next day in UTC.
        val original = ms(2026, 3, 14, 23, 45, newYork)
        val pickerDate = TimeEditing.toPickerDate(original, newYork)
        assertEquals(original, TimeEditing.combine(pickerDate, 23, 45, newYork))
    }

    @Test
    fun `combine honours the zone rather than assuming UTC`() {
        val pickerDate = TimeEditing.toPickerDate(ms(2026, 6, 1, 12, 0, utc), utc)
        val inKolkata = TimeEditing.combine(pickerDate, 9, 0, kolkata)
        val inUtc = TimeEditing.combine(pickerDate, 9, 0, utc)
        // 09:00 IST is 5h30m earlier in absolute terms than 09:00 UTC.
        assertEquals(-5 * 3600_000L - 30 * 60_000L, inKolkata - inUtc)
    }

    @Test
    fun `a date survives a round trip across a DST boundary`() {
        // US DST began 8 March 2026; a date in summer must not drift.
        val original = ms(2026, 7, 4, 8, 15, newYork)
        val pickerDate = TimeEditing.toPickerDate(original, newYork)
        assertEquals(original, TimeEditing.combine(pickerDate, 8, 15, newYork))
    }

    @Test
    fun `a start in the future is rejected`() {
        val now = ms(2026, 10, 4, 12, 0, kolkata)
        assertTrue(TimeEditing.isValidStart(now - 3_600_000, now))
        assertTrue(TimeEditing.isValidStart(now, now))
        assertFalse(TimeEditing.isValidStart(now + 60_000, now))
    }

    @Test
    fun `an end before its start is rejected`() {
        val now = ms(2026, 10, 4, 12, 0, kolkata)
        val start = now - 10 * 3_600_000
        assertTrue(TimeEditing.isValidEnd(start, start + 3_600_000, now))
        assertFalse(TimeEditing.isValidEnd(start, start - 1, now))
        assertFalse(TimeEditing.isValidEnd(start, start, now))
    }

    @Test
    fun `an end in the future is rejected`() {
        val now = ms(2026, 10, 4, 12, 0, kolkata)
        val start = now - 10 * 3_600_000
        assertFalse(TimeEditing.isValidEnd(start, now + 60_000, now))
        assertTrue(TimeEditing.isValidEnd(start, now, now))
    }

    @Test
    fun `a full record edit must satisfy both ends`() {
        val now = ms(2026, 10, 4, 12, 0, kolkata)
        val start = now - 16 * 3_600_000
        assertTrue(TimeEditing.isValidRecordEdit(start, now, now))
        assertFalse(TimeEditing.isValidRecordEdit(now + 1, now + 2, now))  // starts ahead of now
        assertFalse(TimeEditing.isValidRecordEdit(start, start - 1, now))  // ends before start
    }
}
