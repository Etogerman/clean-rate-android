package ru.abrikosov.cleanrate.domain

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorEngineTest {
    @Test
    fun `учитывает приоритет операций`() {
        assertDecimal("14", CalculatorEngine.evaluate("2+3×4"))
    }

    @Test
    fun `понимает запятую и деление`() {
        assertDecimal("3.75", CalculatorEngine.evaluate("7,5÷2"))
    }

    @Test
    fun `вычисляет незаконченное выражение до последнего числа`() {
        assertDecimal("15", CalculatorEngine.evaluate("10+5+"))
    }

    @Test
    fun `не допускает деление на ноль`() {
        assertNull(CalculatorEngine.evaluate("10÷0"))
    }

    private fun assertDecimal(expected: String, actual: BigDecimal?) {
        assertEquals(0, BigDecimal(expected).compareTo(requireNotNull(actual)))
    }
}
