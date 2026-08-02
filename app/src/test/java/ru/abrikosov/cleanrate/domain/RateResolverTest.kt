package ru.abrikosov.cleanrate.domain

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.abrikosov.cleanrate.data.RateOrigin
import ru.abrikosov.cleanrate.data.RateSource

class RateResolverTest {
    private val market = mapOf(
        "USD" to BigDecimal.ONE,
        "RUB" to BigDecimal("79"),
        "EUR" to BigDecimal("0.87"),
        "MGA" to BigDecimal("4300"),
    )
    private val cbr = mapOf(
        "USD" to BigDecimal.ONE,
        "RUB" to BigDecimal("80"),
        "EUR" to BigDecimal("0.8"),
    )
    private val custom = mapOf("RUB" to BigDecimal("83"))

    @Test
    fun `режим ЦБ заменяет опубликованные валюты и сохраняет мировой резерв`() {
        val resolved = RateResolver.resolve(RateSource.CBR, market, cbr, custom)
        assertEquals(BigDecimal("80"), resolved["RUB"])
        assertEquals(BigDecimal("4300"), resolved["MGA"])
        assertEquals(RateOrigin.CBR, RateResolver.origin("RUB", RateSource.CBR, cbr, custom))
        assertEquals(RateOrigin.MARKET, RateResolver.origin("MGA", RateSource.CBR, cbr, custom))
    }

    @Test
    fun `режим банка применяет только введённые пользователем курсы`() {
        val resolved = RateResolver.resolve(RateSource.CUSTOM, market, cbr, custom)
        assertEquals(BigDecimal("83"), resolved["RUB"])
        assertEquals(BigDecimal("0.87"), resolved["EUR"])
        assertEquals(RateOrigin.CUSTOM, RateResolver.origin("RUB", RateSource.CUSTOM, cbr, custom))
        assertEquals(RateOrigin.MARKET, RateResolver.origin("EUR", RateSource.CUSTOM, cbr, custom))
    }
}
