package ru.abrikosov.cleanrate.ui

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue
import ru.abrikosov.cleanrate.data.UiLanguage

object ValueFormatter {
    private val expressionNumber = Regex("""\d+(?:,\d*)?""")

    fun amount(value: BigDecimal?, language: UiLanguage = UiLanguage.RUSSIAN): String {
        if (value == null) return "—"
        val absolute = value.abs()
        val fractionDigits = when {
            absolute >= BigDecimal("1000") -> 2
            absolute >= BigDecimal.ONE -> 4
            absolute.compareTo(BigDecimal.ZERO) == 0 -> 0
            else -> 6
        }
        val symbols = DecimalFormatSymbols(language.locale).apply {
            groupingSeparator = if (language == UiLanguage.ENGLISH) ',' else ' '
            decimalSeparator = if (language == UiLanguage.ENGLISH) '.' else ','
        }
        val pattern = if (fractionDigits == 0) "#,##0" else "#,##0.${"#".repeat(fractionDigits)}"
        return DecimalFormat(pattern, symbols).apply {
            roundingMode = RoundingMode.HALF_UP
            isGroupingUsed = true
            maximumFractionDigits = fractionDigits
        }.format(value)
    }

    fun rate(value: BigDecimal?, language: UiLanguage = UiLanguage.RUSSIAN): String = value
        ?.setScale(8, RoundingMode.HALF_UP)
        ?.stripTrailingZeros()
        ?.toPlainString()
        ?.let { if (language == UiLanguage.ENGLISH) it else it.replace('.', ',') }
        ?: ""

    fun expression(value: String, language: UiLanguage): String {
        val groupingSeparator = if (language == UiLanguage.ENGLISH) ',' else '\u00A0'
        val decimalSeparator = if (language == UiLanguage.ENGLISH) '.' else ','

        return expressionNumber.replace(value) { match ->
            val rawNumber = match.value
            val integerPart = rawNumber.substringBefore(',')
            val fractionPart = rawNumber.substringAfter(',', missingDelimiterValue = "")
            val groupedInteger = integerPart
                .reversed()
                .chunked(3)
                .joinToString(groupingSeparator.toString())
                .reversed()

            if (',' in rawNumber) {
                "$groupedInteger$decimalSeparator$fractionPart"
            } else {
                groupedInteger
            }
        }
    }

    fun chartRate(value: BigDecimal?, language: UiLanguage = UiLanguage.RUSSIAN): String {
        return amount(value, language)
    }

    fun shortDate(date: LocalDate, language: UiLanguage = UiLanguage.RUSSIAN): String = runCatching {
        val pattern = if (language == UiLanguage.ENGLISH) "MMM d" else "dd.MM"
        DateTimeFormatter.ofPattern(pattern, language.locale).format(date)
    }.getOrDefault(date.toString())

    fun date(epochSeconds: Long, language: UiLanguage = UiLanguage.RUSSIAN): String = runCatching {
        DateTimeFormatter
            .ofPattern("d MMM, HH:mm", language.locale)
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochSecond(epochSeconds))
    }.getOrDefault(unknown(language))

    fun date(date: LocalDate, language: UiLanguage = UiLanguage.RUSSIAN): String = runCatching {
        val pattern = if (language == UiLanguage.ENGLISH) "MMMM d" else "d MMMM"
        DateTimeFormatter.ofPattern(pattern, language.locale).format(date)
    }.getOrDefault(date.toString())

    private fun unknown(language: UiLanguage): String = when (language) {
        UiLanguage.RUSSIAN -> "неизвестно"
        UiLanguage.ENGLISH -> "unknown"
        UiLanguage.FRENCH -> "inconnu"
    }

    fun colorIndex(code: String, size: Int): Int = code.hashCode().absoluteValue % size
}
