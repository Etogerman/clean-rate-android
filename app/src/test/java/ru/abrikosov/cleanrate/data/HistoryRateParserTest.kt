package ru.abrikosov.cleanrate.data

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryRateParserTest {
    @Test
    fun `parses a historical point for a rare currency`() {
        val point = HistoryRateParser.parsePoint(
            rawJson = """{"date":"2026-07-31","rub":{"mga":53.991397,"usd":0.0125}}""",
            baseCode = "RUB",
            quoteCode = "MGA",
            expectedDate = LocalDate.of(2026, 7, 31),
        )

        assertEquals(LocalDate.of(2026, 7, 31), requireNotNull(point).date)
        assertEquals(0, BigDecimal("53.991397").compareTo(point.rate))
    }

    @Test
    fun `rejects a response for another date`() {
        assertNull(
            HistoryRateParser.parsePoint(
                rawJson = """{"date":"2026-07-30","rub":{"mga":53.9}}""",
                baseCode = "RUB",
                quoteCode = "MGA",
                expectedDate = LocalDate.of(2026, 7, 31),
            ),
        )
    }

    @Test
    fun `rejects missing and non-positive rates`() {
        assertNull(
            HistoryRateParser.parsePoint(
                rawJson = """{"date":"2026-07-31","rub":{"mga":0}}""",
                baseCode = "RUB",
                quoteCode = "MGA",
            ),
        )
        assertNull(
            HistoryRateParser.parsePoint(
                rawJson = """{"date":"2026-07-31","rub":{"usd":0.01}}""",
                baseCode = "RUB",
                quoteCode = "MGA",
            ),
        )
    }

    @Test
    fun `sampling keeps periods bounded to about thirty points`() {
        val end = LocalDate.of(2026, 8, 1)

        assertEquals(8, HistorySampling.dates(end, ChartPeriod.WEEK).size)
        assertEquals(31, HistorySampling.dates(end, ChartPeriod.MONTH).size)
        assertEquals(31, HistorySampling.dates(end, ChartPeriod.THREE_MONTHS).size)
        assertEquals(31, HistorySampling.dates(end, ChartPeriod.SIX_MONTHS).size)
        assertEquals(30, HistorySampling.dates(end, ChartPeriod.YEAR).size)
        ChartPeriod.entries.forEach { period ->
            val dates = HistorySampling.dates(end, period)
            assertEquals(end.minusDays(period.daysBack), dates.first())
            assertEquals(end, dates.last())
        }
    }
}
