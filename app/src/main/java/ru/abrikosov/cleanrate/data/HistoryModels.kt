package ru.abrikosov.cleanrate.data

import java.math.BigDecimal
import java.time.LocalDate

enum class ChartPeriod(
    val daysBack: Long,
    val sampleEveryDays: Long,
) {
    WEEK(daysBack = 7, sampleEveryDays = 1),
    MONTH(daysBack = 30, sampleEveryDays = 1),
    THREE_MONTHS(daysBack = 90, sampleEveryDays = 3),
    SIX_MONTHS(daysBack = 180, sampleEveryDays = 6),
    YEAR(daysBack = 365, sampleEveryDays = 13),
}

data class HistoricalRatePoint(
    val date: LocalDate,
    val rate: BigDecimal,
)

data class HistoricalRates(
    val baseCode: String,
    val quoteCode: String,
    val period: ChartPeriod,
    val points: List<HistoricalRatePoint>,
    val savedAtEpochMillis: Long,
    val requestedPointCount: Int = points.size,
    val loadedFromCache: Boolean = false,
    val isStale: Boolean = false,
) {
    val missingPointCount: Int
        get() = (requestedPointCount - points.size).coerceAtLeast(0)
}

data class HistorySelection(
    val baseCode: String,
    val quoteCode: String,
    val period: ChartPeriod,
    val amountText: String? = null,
)

internal const val MAX_HISTORY_AMOUNT_LENGTH = 24

internal fun filterHistoryAmountText(rawValue: String): String = buildString {
    var hasSeparator = false
    rawValue.forEach { character ->
        when {
            character.isDigit() -> append(character)
            character in charArrayOf(',', '.') && !hasSeparator -> {
                if (isEmpty()) append('0')
                append(character)
                hasSeparator = true
            }
        }
    }
}.take(MAX_HISTORY_AMOUNT_LENGTH)

internal fun validatedHistoryAmountText(value: String?): String? = value?.takeIf {
    filterHistoryAmountText(it) == it
}

object HistorySampling {
    fun dates(endDate: LocalDate, period: ChartPeriod): List<LocalDate> {
        val startDate = endDate.minusDays(period.daysBack)
        val result = mutableListOf<LocalDate>()
        var date = startDate
        while (date < endDate) {
            result += date
            date = date.plusDays(period.sampleEveryDays)
        }
        if (result.lastOrNull() != endDate) result += endDate
        return result
    }
}
