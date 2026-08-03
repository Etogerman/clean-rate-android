package ru.abrikosov.cleanrate.data

import java.time.LocalDate
import java.util.concurrent.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class SourceResilienceTest {
    @Test
    fun `резервный источник проверяется после некорректного основного ответа`() = runBlocking {
        val attempted = mutableListOf<String>()

        val result = firstValidSource(
            sources = listOf("primary", "fallback"),
            unavailableMessage = "нет источников",
        ) { source ->
            attempted += source
            check(source == "fallback") { "некорректные данные" }
            "проверенный результат"
        }

        assertEquals(listOf("primary", "fallback"), attempted)
        assertEquals("проверенный результат", result)
    }

    @Test
    fun `резервный latest используется после устаревшего основного ответа`() = runBlocking {
        val today = LocalDate.of(2026, 8, 2)
        val attempted = mutableListOf<String>()

        val point = firstValidSource(
            sources = listOf("primary", "fallback"),
            unavailableMessage = "нет источников",
        ) { source ->
            attempted += source
            val date = if (source == "primary") "2026-07-20" else "2026-08-02"
            checkNotNull(
                HistoryRateParser.parsePoint(
                    rawJson = """{"date":"$date","rub":{"mga":53.9}}""",
                    baseCode = "RUB",
                    quoteCode = "MGA",
                    requireFresh = true,
                    today = today,
                ),
            )
        }

        assertEquals(listOf("primary", "fallback"), attempted)
        assertEquals(today, point.date)
    }

    @Test
    fun `отмена не запускает следующий источник`() {
        val cancellation = CancellationException("остановлено")
        var fallbackAttempted = false

        val thrown = assertThrows(CancellationException::class.java) {
            runBlocking {
                firstValidSource(
                    sources = listOf("primary", "fallback"),
                    unavailableMessage = "нет источников",
                ) { source ->
                    if (source == "primary") throw cancellation
                    fallbackAttempted = true
                    "результат"
                }
            }
        }

        assertSame(cancellation, thrown)
        assertFalse(fallbackAttempted)
    }

    @Test
    fun `внутренний тайм-аут становится обычной ошибкой загрузки`() {
        assertThrows(HistoryRequestTimeoutException::class.java) {
            runBlocking {
                withHistoryRequestTimeout(timeoutMillis = 10L) {
                    delay(5_000L)
                    "слишком поздно"
                }
            }
        }
    }
}
