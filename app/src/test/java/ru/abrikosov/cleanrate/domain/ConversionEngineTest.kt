package ru.abrikosov.cleanrate.domain

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversionEngineTest {
    private val rates = mapOf(
        "USD" to BigDecimal.ONE,
        "RUB" to BigDecimal("80"),
        "EUR" to BigDecimal("0.8"),
    )

    @Test
    fun `переводит доллары в рубли`() {
        assertDecimal("8000", ConversionEngine.convert(BigDecimal("100"), "USD", "RUB", rates))
    }

    @Test
    fun `переводит рубли в евро через общую базу`() {
        assertDecimal("10", ConversionEngine.convert(BigDecimal("1000"), "RUB", "EUR", rates))
    }

    private fun assertDecimal(expected: String, actual: BigDecimal?) {
        assertEquals(0, BigDecimal(expected).compareTo(requireNotNull(actual)))
    }
}
