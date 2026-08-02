package ru.abrikosov.cleanrate.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryCachePolicyTest {
    @Test
    fun `кэш удаляет самые старые записи и сохраняет заданный предел`() {
        val entries = linkedMapOf("new" to 30L, "old" to 10L, "middle" to 20L)

        assertEquals(
            setOf("old"),
            HistoryCachePolicy.keysToEvict(entries, maximumEntries = 2),
        )
    }
}
