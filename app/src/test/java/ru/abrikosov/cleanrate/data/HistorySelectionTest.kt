package ru.abrikosov.cleanrate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistorySelectionTest {
    @Test
    fun `сумма графика сохраняет редактируемое десятичное значение`() {
        assertEquals("1600000,50", filterHistoryAmountText("1 600 000,50"))
        assertEquals("0,", filterHistoryAmountText(","))
        assertEquals("1600000,50", validatedHistoryAmountText("1600000,50"))
    }

    @Test
    fun `повреждённая сохранённая сумма не восстанавливается`() {
        assertNull(validatedHistoryAmountText("1,2.3"))
        assertNull(validatedHistoryAmountText("1234567890123456789012345"))
    }
}
