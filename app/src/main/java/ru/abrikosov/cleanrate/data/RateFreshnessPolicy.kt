package ru.abrikosov.cleanrate.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Проверяет именно дату данных, а не только время последнего сетевого запроса.
 * Небольшой запас учитывает выходные и публикацию официального курса заранее.
 */
internal object RateFreshnessPolicy {
    fun isMarketFresh(rateDate: LocalDate, today: LocalDate = LocalDate.now()): Boolean =
        isWithinWindow(
            rateDate = rateDate,
            today = today,
            maximumAgeDays = MARKET_MAXIMUM_AGE_DAYS,
            maximumFutureDays = MARKET_MAXIMUM_FUTURE_DAYS,
        )

    fun isCbrFresh(rateDate: LocalDate, today: LocalDate = LocalDate.now()): Boolean =
        isWithinWindow(
            rateDate = rateDate,
            today = today,
            maximumAgeDays = CBR_MAXIMUM_AGE_DAYS,
            maximumFutureDays = CBR_MAXIMUM_FUTURE_DAYS,
        )

    private fun isWithinWindow(
        rateDate: LocalDate,
        today: LocalDate,
        maximumAgeDays: Long,
        maximumFutureDays: Long,
    ): Boolean {
        val ageDays = ChronoUnit.DAYS.between(rateDate, today)
        return ageDays in -maximumFutureDays..maximumAgeDays
    }

    private const val MARKET_MAXIMUM_AGE_DAYS = 4L
    private const val MARKET_MAXIMUM_FUTURE_DAYS = 1L
    private const val CBR_MAXIMUM_AGE_DAYS = 10L
    private const val CBR_MAXIMUM_FUTURE_DAYS = 3L
}
