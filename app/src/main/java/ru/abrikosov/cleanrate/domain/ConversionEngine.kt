package ru.abrikosov.cleanrate.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Курсы хранятся в форме «1 USD = N единиц валюты».
 * Расчёт всегда проходит через общую базу, поэтому работает для любой пары.
 */
object ConversionEngine {
    fun convert(
        amount: BigDecimal,
        fromCode: String,
        toCode: String,
        rates: Map<String, BigDecimal>,
    ): BigDecimal? {
        if (fromCode == toCode) return amount
        val fromRate = rates[fromCode] ?: return null
        val toRate = rates[toCode] ?: return null
        if (fromRate.compareTo(BigDecimal.ZERO) <= 0 || toRate.compareTo(BigDecimal.ZERO) <= 0) {
            return null
        }

        return amount
            .divide(fromRate, 18, RoundingMode.HALF_UP)
            .multiply(toRate)
    }
}
