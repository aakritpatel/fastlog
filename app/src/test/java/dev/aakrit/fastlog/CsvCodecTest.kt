package dev.aakrit.fastlog

import dev.aakrit.fastlog.data.CsvCodec
import dev.aakrit.fastlog.model.FastRecord
import org.junit.Assert.*
import org.junit.Test

class CsvCodecTest {

    private fun record(
        id: String,
        startMs: Long = 1_700_000_000_000,
        hours: Double = 16.0,
        goal: Double = 16.0,
        protocol: String = "16:8",
    ) = FastRecord(
        id = id,
        startEpochMs = startMs,
        endEpochMs = startMs + (hours * 3_600_000).toLong(),
        goalHours = goal,
        protocolId = protocol,
    )

    @Test
    fun `export then parse returns the same records`() {
        val original = listOf(record("a"), record("b", hours = 24.0, goal = 24.0, protocol = "24h"))
        val parsed = CsvCodec.parse(CsvCodec.export(original)).records

        assertEquals(2, parsed.size)
        original.forEach { expected ->
            val actual = parsed.first { it.id == expected.id }
            assertEquals(expected.startEpochMs, actual.startEpochMs)
            assertEquals(expected.endEpochMs, actual.endEpochMs)
            assertEquals(expected.goalHours, actual.goalHours, 0.001)
            assertEquals(expected.protocolId, actual.protocolId)
        }
    }

    @Test
    fun `export writes a header row`() {
        assertTrue(CsvCodec.export(listOf(record("a"))).startsWith(CsvCodec.HEADER))
    }

    @Test
    fun `empty history exports header only`() {
        assertEquals(1, CsvCodec.export(emptyList()).trim().lines().size)
    }

    @Test
    fun `parsing empty or blank input yields nothing and does not throw`() {
        assertTrue(CsvCodec.parse("").records.isEmpty())
        assertTrue(CsvCodec.parse("   \n  \n").records.isEmpty())
    }

    @Test
    fun `malformed rows are skipped but good rows still import`() {
        val csv = buildString {
            appendLine(CsvCodec.HEADER)
            appendLine("x1,16:8,16.00,2023-11-14T22:13:20Z,2023-11-15T14:13:20Z,16.00,yes")
            appendLine("broken,row")
            appendLine("x2,16:8,notanumber,2023-11-14T22:13:20Z,2023-11-15T14:13:20Z,16.00,yes")
            appendLine("x3,24h,24.00,2023-11-10T00:00:00Z,2023-11-11T00:00:00Z,24.00,yes")
        }
        val result = CsvCodec.parse(csv)
        assertEquals(2, result.records.size)
        assertEquals(2, result.skipped)
    }

    @Test
    fun `a row whose end precedes its start is rejected`() {
        val csv = CsvCodec.HEADER + "\n" +
            "bad,16:8,16.00,2023-11-15T14:13:20Z,2023-11-14T22:13:20Z,16.00,no\n"
        val result = CsvCodec.parse(csv)
        assertTrue(result.records.isEmpty())
        assertEquals(1, result.skipped)
    }

    @Test
    fun `csv without a header is still parsed`() {
        val csv = "x1,16:8,16.00,2023-11-14T22:13:20Z,2023-11-15T14:13:20Z,16.00,yes\n"
        assertEquals(1, CsvCodec.parse(csv).records.size)
    }

    @Test
    fun `importing the same file twice adds nothing the second time`() {
        val existing = listOf(record("a"), record("b"))
        val incoming = listOf(record("a"), record("b"))

        val first = CsvCodec.merge(existing, incoming)
        assertEquals(0, first.added)
        assertEquals(2, first.duplicates)
        assertEquals(2, first.merged.size)
    }

    @Test
    fun `merge adds only genuinely new records and never drops existing ones`() {
        val existing = listOf(record("a"), record("b"))
        val incoming = listOf(record("b"), record("c"), record("d"))

        val result = CsvCodec.merge(existing, incoming)
        assertEquals(2, result.added)
        assertEquals(1, result.duplicates)
        assertEquals(4, result.merged.size)
        assertTrue(result.merged.map { it.id }.containsAll(listOf("a", "b", "c", "d")))
    }

    @Test
    fun `an existing record is never overwritten by an incoming one with the same id`() {
        val existing = listOf(record("a", hours = 20.0))
        val incoming = listOf(record("a", hours = 2.0))

        val result = CsvCodec.merge(existing, incoming)
        assertEquals(20.0, result.merged.single().durationHours, 0.01)
    }

    @Test
    fun `merged output is sorted newest first`() {
        val old = record("old", startMs = 1_600_000_000_000)
        val new = record("new", startMs = 1_800_000_000_000)
        val result = CsvCodec.merge(listOf(old), listOf(new))
        assertEquals("new", result.merged.first().id)
    }

    @Test
    fun `commas inside a field survive a round trip`() {
        val weird = record("a").copy(protocolId = "odd,name")
        val parsed = CsvCodec.parse(CsvCodec.export(listOf(weird))).records.single()
        assertEquals("odd,name", parsed.protocolId)
    }
}
