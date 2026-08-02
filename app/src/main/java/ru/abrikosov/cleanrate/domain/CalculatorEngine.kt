package ru.abrikosov.cleanrate.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Небольшой калькулятор без eval и преобразования через Double. */
object CalculatorEngine {
    private val mathContext = MathContext(24, RoundingMode.HALF_UP)

    fun evaluate(rawExpression: String): BigDecimal? {
        val normalized = rawExpression
            .replace(" ", "")
            .replace(',', '.')
            .replace('−', '-')
            .replace('×', '*')
            .replace('÷', '/')
            .trimTrailingOperators()

        if (normalized.isBlank()) return BigDecimal.ZERO

        return runCatching {
            val parser = Parser(normalized)
            parser.parse().stripTrailingZeros().let {
                if (it.compareTo(BigDecimal.ZERO) == 0) BigDecimal.ZERO else it
            }
        }.getOrNull()
    }

    private fun String.trimTrailingOperators(): String {
        var result = this
        while (result.isNotEmpty() && result.last() in charArrayOf('+', '-', '*', '/')) {
            result = result.dropLast(1)
        }
        return result
    }

    private class Parser(private val input: String) {
        private var position = 0

        fun parse(): BigDecimal {
            val result = parseExpression()
            check(position == input.length) { "Лишний символ в выражении" }
            return result
        }

        private fun parseExpression(): BigDecimal {
            var value = parseTerm()
            while (position < input.length) {
                value = when (input[position]) {
                    '+' -> {
                        position++
                        value.add(parseTerm(), mathContext)
                    }
                    '-' -> {
                        position++
                        value.subtract(parseTerm(), mathContext)
                    }
                    else -> return value
                }
            }
            return value
        }

        private fun parseTerm(): BigDecimal {
            var value = parseFactor()
            while (position < input.length) {
                value = when (input[position]) {
                    '*' -> {
                        position++
                        value.multiply(parseFactor(), mathContext)
                    }
                    '/' -> {
                        position++
                        val divisor = parseFactor()
                        check(divisor.compareTo(BigDecimal.ZERO) != 0) { "Деление на ноль" }
                        value.divide(divisor, 18, RoundingMode.HALF_UP)
                    }
                    else -> return value
                }
            }
            return value
        }

        private fun parseFactor(): BigDecimal {
            var sign = BigDecimal.ONE
            while (position < input.length && input[position] in charArrayOf('+', '-')) {
                if (input[position] == '-') sign = sign.negate()
                position++
            }

            val start = position
            var hasDecimalSeparator = false
            while (position < input.length) {
                val char = input[position]
                if (char.isDigit()) {
                    position++
                } else if (char == '.' && !hasDecimalSeparator) {
                    hasDecimalSeparator = true
                    position++
                } else {
                    break
                }
            }

            check(position > start) { "Ожидалось число" }
            var number = input.substring(start, position)
            if (number.startsWith('.')) number = "0$number"
            if (number.endsWith('.')) number += "0"
            return BigDecimal(number, mathContext).multiply(sign)
        }
    }
}
