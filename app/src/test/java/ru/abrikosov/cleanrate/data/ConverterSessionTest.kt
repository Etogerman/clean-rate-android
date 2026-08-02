package ru.abrikosov.cleanrate.data

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConverterSessionTest {
    @Test
    fun `valid converter session restores currency expression and amount`() {
        assertEquals(
            ConverterSession("USD", "12345,67", BigDecimal("12345.67"), true),
            parseConverterSession("USD", "12345,67", "12345.67", justEvaluated = true),
        )
    }

    @Test
    fun `invalid converter session is ignored`() {
        assertNull(parseConverterSession("usd", "100", "100"))
        assertNull(parseConverterSession("USD", "", "100"))
        assertNull(parseConverterSession("USD", "100", "not-a-number"))
    }
}
