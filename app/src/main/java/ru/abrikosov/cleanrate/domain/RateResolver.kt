package ru.abrikosov.cleanrate.domain

import java.math.BigDecimal
import ru.abrikosov.cleanrate.data.RateOrigin
import ru.abrikosov.cleanrate.data.RateSource

object RateResolver {
    fun resolve(
        source: RateSource,
        marketRates: Map<String, BigDecimal>,
        cbrRates: Map<String, BigDecimal>,
        customRates: Map<String, BigDecimal>,
    ): Map<String, BigDecimal> = when (source) {
        RateSource.MARKET -> marketRates
        RateSource.CBR -> marketRates + cbrRates
        RateSource.CUSTOM -> marketRates + customRates
    }

    fun origin(
        code: String,
        source: RateSource,
        cbrRates: Map<String, BigDecimal>,
        customRates: Map<String, BigDecimal>,
    ): RateOrigin = when {
        source == RateSource.CBR && code in cbrRates -> RateOrigin.CBR
        source == RateSource.CUSTOM && code in customRates -> RateOrigin.CUSTOM
        else -> RateOrigin.MARKET
    }
}
