package ru.abrikosov.cleanrate.data

import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

enum class UiLanguage(
    val languageTag: String,
    val shortLabel: String,
) {
    RUSSIAN("ru-RU", "RU"),
    ENGLISH("en-US", "EN"),
    FRENCH("fr-FR", "FR"),
    ;

    val locale: Locale
        get() = Locale.forLanguageTag(languageTag)
}

enum class RateSource {
    MARKET,
    CBR,
    CUSTOM,
}

enum class RateOrigin {
    MARKET,
    CBR,
    CUSTOM,
}

data class RateSnapshot(
    val rates: Map<String, BigDecimal>,
    val rateDate: LocalDate,
    val loadedFromSeed: Boolean = false,
    val lastCheckedEpochSeconds: Long? = null,
    val isStale: Boolean = false,
)

data class CbrRateSnapshot(
    val rates: Map<String, BigDecimal>,
    val officialDate: LocalDate,
    val loadedFromSeed: Boolean = false,
    val lastCheckedEpochSeconds: Long? = null,
    val isStale: Boolean = false,
)

data class CurrencyMetadata(
    val code: String,
    val name: String,
    val symbol: String,
    val flag: String,
)
