package ru.abrikosov.cleanrate.data

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RateFreshnessPolicyTest {
    private val today = LocalDate.of(2026, 8, 10)

    @Test
    fun `мировой курс учитывает выходные но отклоняет старые данные`() {
        assertTrue(RateFreshnessPolicy.isMarketFresh(today.minusDays(4), today))
        assertFalse(RateFreshnessPolicy.isMarketFresh(today.minusDays(5), today))
    }

    @Test
    fun `неправдоподобные будущие даты отклоняются`() {
        assertTrue(RateFreshnessPolicy.isMarketFresh(today.plusDays(1), today))
        assertFalse(RateFreshnessPolicy.isMarketFresh(today.plusDays(2), today))
        assertTrue(RateFreshnessPolicy.isCbrFresh(today.plusDays(3), today))
        assertFalse(RateFreshnessPolicy.isCbrFresh(today.plusDays(4), today))
    }

    @Test
    fun `официальный курс получает запас на длинные праздники`() {
        assertTrue(RateFreshnessPolicy.isCbrFresh(today.minusDays(10), today))
        assertFalse(RateFreshnessPolicy.isCbrFresh(today.minusDays(11), today))
    }
}
