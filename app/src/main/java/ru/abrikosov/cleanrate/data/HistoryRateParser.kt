package ru.abrikosov.cleanrate.data

import java.math.BigDecimal
import java.time.LocalDate
import org.json.JSONObject

object HistoryRateParser {
    fun parsePoint(
        rawJson: String,
        baseCode: String,
        quoteCode: String,
        expectedDate: LocalDate? = null,
        requireFresh: Boolean = false,
        today: LocalDate = LocalDate.now(),
    ): HistoricalRatePoint? = runCatching {
        val normalizedBase = baseCode.lowercase()
        val normalizedQuote = quoteCode.lowercase()
        check(normalizedBase.matches(CURRENCY_CODE))
        check(normalizedQuote.matches(CURRENCY_CODE))

        val root = JSONObject(rawJson)
        val date = LocalDate.parse(root.getString("date"))
        if (expectedDate != null) check(date == expectedDate)
        check(!requireFresh || RateFreshnessPolicy.isMarketFresh(date, today))

        val rates = root.getJSONObject(normalizedBase)
        val rate = rates.get(normalizedQuote).toString().toBigDecimalOrNull()
        check(rate != null && rate > BigDecimal.ZERO && rate <= MAX_REASONABLE_RATE)
        HistoricalRatePoint(date = date, rate = rate)
    }.getOrNull()

    private val CURRENCY_CODE = Regex("[a-z]{3}")
    private val MAX_REASONABLE_RATE = BigDecimal("1000000000000000")
}
