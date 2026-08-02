package ru.abrikosov.cleanrate.ui

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.abrikosov.cleanrate.data.CbrRateSnapshot
import ru.abrikosov.cleanrate.data.RateSnapshot
import ru.abrikosov.cleanrate.data.RateSource
import ru.abrikosov.cleanrate.data.UiLanguage

class RateStatusPolicyTest {
    @Test
    fun `режим цб учитывает устаревший мировой резерв для отсутствующей валюты`() {
        val health = RateStatusPolicy.evaluate(
            state(
                favorites = listOf("USD", "MGA"),
                marketStale = true,
                marketFromSeed = true,
            ),
        )

        assertTrue(health.isStale)
        assertTrue(health.loadedFromSeed)
        assertEquals(100L, health.lastCheckedEpochSeconds)
    }

    @Test
    fun `режим цб не зависит от мирового снимка когда все валюты официальные`() {
        val health = RateStatusPolicy.evaluate(
            state(
                favorites = listOf("USD", "RUB"),
                marketStale = true,
                marketFromSeed = true,
            ),
        )

        assertFalse(health.isStale)
        assertFalse(health.loadedFromSeed)
        assertEquals(200L, health.lastCheckedEpochSeconds)
    }

    @Test
    fun `смешанный статус не выдаёт частичное время за проверку всех источников`() {
        val health = RateStatusPolicy.evaluate(
            state(
                favorites = listOf("USD", "MGA"),
                marketStale = false,
                marketFromSeed = true,
                marketLastCheckedEpochSeconds = null,
            ),
        )

        assertFalse(health.isStale)
        assertTrue(health.loadedFromSeed)
        assertNull(health.lastCheckedEpochSeconds)
    }

    private fun state(
        favorites: List<String>,
        marketStale: Boolean,
        marketFromSeed: Boolean,
        marketLastCheckedEpochSeconds: Long? = 100L,
    ): ConverterUiState = ConverterUiState(
        marketSnapshot = RateSnapshot(
            rates = mapOf(
                "USD" to BigDecimal.ONE,
                "RUB" to BigDecimal("80"),
                "MGA" to BigDecimal("4500"),
            ),
            rateDate = LocalDate.of(2026, 8, 2),
            loadedFromSeed = marketFromSeed,
            lastCheckedEpochSeconds = marketLastCheckedEpochSeconds,
            isStale = marketStale,
        ),
        cbrSnapshot = CbrRateSnapshot(
            rates = mapOf(
                "USD" to BigDecimal.ONE,
                "RUB" to BigDecimal("80"),
            ),
            officialDate = LocalDate.of(2026, 8, 2),
            loadedFromSeed = false,
            lastCheckedEpochSeconds = 200L,
            isStale = false,
        ),
        rateSource = RateSource.CBR,
        uiLanguage = UiLanguage.RUSSIAN,
        favorites = favorites,
        manualRates = emptyMap(),
        keySoundEnabled = true,
        keyVibrationEnabled = true,
    )
}
