package ru.abrikosov.cleanrate.data

import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketRateParserTest {
    @Test
    fun `открытый резервный снимок разбирается и отсекает криптоактивы`() {
        val rawJson = seedFile().readText()
        val snapshot = requireNotNull(
            MarketRateParser.parse(
                rawJson = rawJson,
                loadedFromSeed = true,
                lastCheckedEpochSeconds = null,
            ),
        )

        assertEquals(LocalDate.parse("2026-08-01"), snapshot.rateDate)
        assertEquals(0, BigDecimal.ONE.compareTo(snapshot.rates["USD"]))
        assertTrue("RUB" in snapshot.rates)
        assertTrue("MGA" in snapshot.rates)
        assertFalse("BTC" in snapshot.rates)
        assertTrue(snapshot.loadedFromSeed)
    }

    @Test
    fun `неполный ответ источника отклоняется`() {
        assertNull(
            MarketRateParser.parse(
                rawJson = """{"date":"2026-08-01","usd":{"usd":1,"eur":0.9}}""",
                loadedFromSeed = false,
                lastCheckedEpochSeconds = 1L,
            ),
        )
    }

    private fun seedFile(): File {
        val candidates = listOf(
            File("src/main/assets/seed_rates.json"),
            File("app/src/main/assets/seed_rates.json"),
        )
        return candidates.firstOrNull(File::isFile)
            ?: error("Не найден резервный снимок курсов")
    }
}
