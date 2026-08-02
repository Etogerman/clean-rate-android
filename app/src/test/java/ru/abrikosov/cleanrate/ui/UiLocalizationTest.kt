package ru.abrikosov.cleanrate.ui

import java.math.BigDecimal
import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import ru.abrikosov.cleanrate.data.CurrencyCatalog
import ru.abrikosov.cleanrate.data.RateSource
import ru.abrikosov.cleanrate.data.UiLanguage

class UiLocalizationTest {
    @Test
    fun `all languages have core interface labels`() {
        UiLanguage.entries.forEach { language ->
            val text = UiText(language)
            assertFalse(text.appName.isBlank())
            assertFalse(text.manageCurrencies.isBlank())
            assertFalse(text.sourceName(RateSource.CBR).isBlank())
            assertFalse(text.languagePickerTitle.isBlank())
            assertFalse(text.hideKeyboard.isBlank())
            assertFalse(text.showKeyboard.isBlank())
            assertFalse(text.chartTitle.isBlank())
            assertFalse(text.chartPeriod(ru.abrikosov.cleanrate.data.ChartPeriod.MONTH).isBlank())
            assertFalse(text.privacyPolicy.isBlank())
            assertFalse(text.privacySummary.isBlank())
            assertFalse(text.chartTouchHint.isBlank())
            assertFalse(text.showLatestValue.isBlank())
            assertFalse(text.settings.isBlank())
            assertFalse(text.keySound.isBlank())
            assertFalse(text.keyVibration.isBlank())
        }
    }

    @Test
    fun `numbers use the selected decimal separator`() {
        val value = BigDecimal("1234.5")

        assertEquals("1,234.5", ValueFormatter.amount(value, UiLanguage.ENGLISH))
        assertEquals("1 234,5", ValueFormatter.amount(value, UiLanguage.FRENCH))
        assertEquals("1 234,5", ValueFormatter.amount(value, UiLanguage.RUSSIAN))
    }

    @Test
    fun `calculator decimal key is localized without changing its internal format`() {
        assertEquals("12.5+3", ValueFormatter.expression("12,5+3", UiLanguage.ENGLISH))
        assertEquals("12,5+3", ValueFormatter.expression("12,5+3", UiLanguage.FRENCH))
    }

    @Test
    fun `calculator groups thousands in every number of the expression`() {
        assertEquals(
            "1 000 600,25+2 000",
            ValueFormatter.expression("1000600,25+2000", UiLanguage.RUSSIAN),
        )
        assertEquals(
            "1,000,600.25+2,000",
            ValueFormatter.expression("1000600,25+2000", UiLanguage.ENGLISH),
        )
    }

    @Test
    fun `chart values and dates follow the selected language`() {
        val date = java.time.LocalDate.of(2026, 7, 31)

        assertEquals("53.9914", ValueFormatter.chartRate(BigDecimal("53.991397"), UiLanguage.ENGLISH))
        assertEquals("53,9914", ValueFormatter.chartRate(BigDecimal("53.991397"), UiLanguage.RUSSIAN))
        assertEquals("11 954,36", ValueFormatter.chartRate(BigDecimal("11954.35966"), UiLanguage.RUSSIAN))
        assertEquals("31.07", ValueFormatter.shortDate(date, UiLanguage.FRENCH))
        assertEquals("July 31", ValueFormatter.date(date, UiLanguage.ENGLISH))
        assertEquals("31 juillet", ValueFormatter.date(date, UiLanguage.FRENCH))
    }

    @Test
    fun `chart amount field groups thousands while typing`() {
        val russian = AmountGroupingVisualTransformation(UiLanguage.RUSSIAN)
            .filter(AnnotatedString("1000600,5"))
            .text
            .text
        val english = AmountGroupingVisualTransformation(UiLanguage.ENGLISH)
            .filter(AnnotatedString("1000600.5"))
            .text
            .text

        assertEquals("1 000 600,5", russian)
        assertEquals("1,000,600.5", english)
    }

    @Test
    fun `chart touch position selects the nearest historical point`() {
        assertEquals(-1, ChartPointSelection.nearestIndex(100f, 0, 100f, 500f))
        assertEquals(0, ChartPointSelection.nearestIndex(20f, 5, 100f, 500f))
        assertEquals(1, ChartPointSelection.nearestIndex(200f, 5, 100f, 500f))
        assertEquals(2, ChartPointSelection.nearestIndex(300f, 5, 100f, 500f))
        assertEquals(4, ChartPointSelection.nearestIndex(700f, 5, 100f, 500f))
    }

    @Test
    fun `selected historical rate converts the entered amount`() {
        val state = HistoryUiState(amountText = "1000")

        assertEquals(
            0,
            BigDecimal("53792.53").compareTo(state.convertedAmountAt(BigDecimal("53.79253"))),
        )
    }

    @Test
    fun `chart defaults zero or negative converter amount to one`() {
        assertEquals(BigDecimal.ONE, defaultChartAmount(BigDecimal.ZERO))
        assertEquals(BigDecimal.ONE, defaultChartAmount(BigDecimal("-5")))
        assertEquals(BigDecimal("12.5"), defaultChartAmount(BigDecimal("12.5")))
        assertEquals("1", HistoryUiState().amountText)
    }

    @Test
    fun `selected chart point description is localized`() {
        assertEquals(
            "Выбрано 17 июля: 1 RUB равен 54,2974 MGA",
            UiText(UiLanguage.RUSSIAN).selectedChartPoint(
                baseCode = "RUB",
                quoteCode = "MGA",
                date = "17 июля",
                rate = "54,2974",
            ),
        )
    }

    @Test
    fun `special currency names are translated`() {
        assertEquals("Специальные права заимствования", CurrencyCatalog.metadata("XDR", UiLanguage.RUSSIAN).name)
        assertEquals("Special Drawing Rights", CurrencyCatalog.metadata("XDR", UiLanguage.ENGLISH).name)
        assertEquals("Droits de tirage spéciaux", CurrencyCatalog.metadata("XDR", UiLanguage.FRENCH).name)
    }
}
