package ru.abrikosov.cleanrate.data

import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale
import org.json.JSONObject

internal object MarketRateParser {
    fun parse(
        rawJson: String,
        loadedFromSeed: Boolean,
        lastCheckedEpochSeconds: Long?,
        requireFresh: Boolean = false,
        today: LocalDate = LocalDate.now(),
    ): RateSnapshot? = runCatching {
        val root = JSONObject(rawJson)
        val rateDate = LocalDate.parse(root.getString("date"))
        val ratesJson = root.getJSONObject(BASE_CODE.lowercase(Locale.ROOT))
        val rates = buildMap {
            ratesJson.keys().forEach { rawCode ->
                val code = rawCode.uppercase(Locale.ROOT)
                if (code in SUPPORTED_CODES) {
                    val value = ratesJson.get(rawCode).toString().toBigDecimalOrNull()
                    if (value != null && value > BigDecimal.ZERO && value <= MAX_REASONABLE_RATE) {
                        put(code, value)
                    }
                }
            }
            put(BASE_CODE, BigDecimal.ONE)
        }
        check(rates.size > 100) { "Источник вернул слишком мало поддерживаемых валют" }
        check(REQUIRED_CODES.all(rates::containsKey)) { "В ответе нет основных валют" }
        val isStale = !RateFreshnessPolicy.isMarketFresh(rateDate, today)
        check(!requireFresh || !isStale) { "Источник вернул неактуальную дату курса" }
        RateSnapshot(
            rates = rates,
            rateDate = rateDate,
            loadedFromSeed = loadedFromSeed,
            lastCheckedEpochSeconds = lastCheckedEpochSeconds,
            isStale = isStale,
        )
    }.getOrNull()

    private const val BASE_CODE = "USD"
    private val REQUIRED_CODES = setOf("USD", "EUR", "RUB", "MGA")
    private val MAX_REASONABLE_RATE = BigDecimal("1000000000000000")

    // Список сохраняет привычный набор фиатных валют и отсекает криптоактивы,
    // металлы и устаревшие коды, которые также присутствуют в открытом источнике.
    private val SUPPORTED_CODES = setOf(
        "AED", "AFN", "ALL", "AMD", "ANG", "AOA", "ARS", "AUD", "AWG", "AZN", "BAM", "BBD",
        "BDT", "BGN", "BHD", "BIF", "BMD", "BND", "BOB", "BRL", "BSD", "BTN", "BWP", "BYN",
        "BZD", "CAD", "CDF", "CHF", "CLF", "CLP", "CNH", "CNY", "COP", "CRC", "CUP", "CVE",
        "CZK", "DJF", "DKK", "DOP", "DZD", "EGP", "ERN", "ETB", "EUR", "FJD", "FKP", "FOK",
        "GBP", "GEL", "GGP", "GHS", "GIP", "GMD", "GNF", "GTQ", "GYD", "HKD", "HNL", "HRK",
        "HTG", "HUF", "IDR", "ILS", "IMP", "INR", "IQD", "IRR", "ISK", "JEP", "JMD", "JOD",
        "JPY", "KES", "KGS", "KHR", "KID", "KMF", "KRW", "KWD", "KYD", "KZT", "LAK", "LBP",
        "LKR", "LRD", "LSL", "LYD", "MAD", "MDL", "MGA", "MKD", "MMK", "MNT", "MOP", "MRU",
        "MUR", "MVR", "MWK", "MXN", "MYR", "MZN", "NAD", "NGN", "NIO", "NOK", "NPR", "NZD",
        "OMR", "PAB", "PEN", "PGK", "PHP", "PKR", "PLN", "PYG", "QAR", "RON", "RSD", "RUB",
        "RWF", "SAR", "SBD", "SCR", "SDG", "SEK", "SGD", "SHP", "SLE", "SLL", "SOS", "SRD",
        "SSP", "STN", "SYP", "SZL", "THB", "TJS", "TMT", "TND", "TOP", "TRY", "TTD", "TVD",
        "TWD", "TZS", "UAH", "UGX", "USD", "UYU", "UZS", "VES", "VND", "VUV", "WST", "XAF",
        "XCD", "XCG", "XDR", "XOF", "XPF", "YER", "ZAR", "ZMW", "ZWG", "ZWL",
    )
}
