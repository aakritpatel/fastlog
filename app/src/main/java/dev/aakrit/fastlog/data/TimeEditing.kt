package dev.aakrit.fastlog.data

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Date/time arithmetic for the edit pickers, kept free of Android types so the
 * timezone handling can be tested directly.
 *
 * Material's DatePicker speaks UTC midnight while a fast happens in local time.
 * Mixing the two silently shifts the date by a day for anyone not on UTC, which
 * is why the conversion lives here behind named functions rather than inline.
 */
object TimeEditing {

    /** Local instant -> the UTC-midnight value Material's DatePicker expects. */
    fun toPickerDate(epochMs: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(epochMs)
            .atZone(zone)
            .toLocalDate()
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()

    /** A UTC-midnight date from the picker plus a wall-clock time -> local instant. */
    fun combine(pickerDateMs: Long, hour: Int, minute: Int, zone: ZoneId): Long =
        Instant.ofEpochMilli(pickerDateMs)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .atTime(LocalTime.of(hour, minute))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    /** A fast may not start in the future. */
    fun isValidStart(startMs: Long, nowMs: Long): Boolean = startMs <= nowMs

    /** An end must follow its start and may not be in the future. */
    fun isValidEnd(startMs: Long, endMs: Long, nowMs: Long): Boolean =
        endMs > startMs && endMs <= nowMs

    fun isValidRecordEdit(startMs: Long, endMs: Long, nowMs: Long): Boolean =
        isValidStart(startMs, nowMs) && isValidEnd(startMs, endMs, nowMs)
}
