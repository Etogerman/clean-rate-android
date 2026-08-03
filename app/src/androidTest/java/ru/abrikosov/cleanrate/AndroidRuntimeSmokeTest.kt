package ru.abrikosov.cleanrate

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.abrikosov.cleanrate.data.CbrRepository
import ru.abrikosov.cleanrate.data.ChartPeriod
import ru.abrikosov.cleanrate.data.CurrencyRepository
import ru.abrikosov.cleanrate.data.HistoricalRatesRepository

@RunWith(AndroidJUnit4::class)
class AndroidRuntimeSmokeTest {
    @Test
    fun bundledCbrXmlParsesOnAndroidRuntime() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val snapshot = CbrRepository(context).loadSnapshot()

        assertFalse(snapshot.rates.isEmpty())
        assertEquals(0, BigDecimal.ONE.compareTo(snapshot.rates["USD"]))
    }

    @Test
    fun mainActivityStartsWithoutCrash() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
            }
        }
    }

    @Test
    fun lastScreenChoicePersistsInApplicationStorage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = CurrencyRepository(context)
        val originalValue = repository.loadChartVisible()

        try {
            repository.saveChartVisible(true)
            assertTrue(CurrencyRepository(context).loadChartVisible())
        } finally {
            repository.saveChartVisible(originalValue)
        }
    }

    @Test
    fun chartSelectionAndAmountPersistInApplicationStorage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = HistoricalRatesRepository(context)
        val original = repository.loadSelection(defaultBase = "USD", defaultQuote = "EUR")

        try {
            repository.saveSelection(
                baseCode = "RUB",
                quoteCode = "MGA",
                period = ChartPeriod.THREE_MONTHS,
                amountText = "1600000,5",
            )

            val restored = HistoricalRatesRepository(context).loadSelection(
                defaultBase = "USD",
                defaultQuote = "EUR",
            )
            assertEquals("RUB", restored.baseCode)
            assertEquals("MGA", restored.quoteCode)
            assertEquals(ChartPeriod.THREE_MONTHS, restored.period)
            assertEquals("1600000,5", restored.amountText)
        } finally {
            repository.saveSelection(
                baseCode = original.baseCode,
                quoteCode = original.quoteCode,
                period = original.period,
                amountText = original.amountText,
            )
        }
    }
}
