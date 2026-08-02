package ru.abrikosov.cleanrate.data

import android.content.Context
import android.util.Log
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ru.abrikosov.cleanrate.BuildConfig

class HistoricalRatesRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    suspend fun loadHistory(
        baseCode: String,
        quoteCode: String,
        period: ChartPeriod,
        forceRefresh: Boolean = false,
    ): Result<HistoricalRates> = withContext(Dispatchers.IO) {
        val normalizedBase = baseCode.uppercase()
        val normalizedQuote = quoteCode.uppercase()
        require(normalizedBase.matches(CURRENCY_CODE) && normalizedQuote.matches(CURRENCY_CODE))
        require(normalizedBase != normalizedQuote)

        val cached = loadCached(normalizedBase, normalizedQuote, period)
        if (!forceRefresh && cached != null && isFresh(cached)) {
            return@withContext Result.success(cached.copy(loadedFromCache = true))
        }

        try {
            val fetched = fetchHistory(normalizedBase, normalizedQuote, period)
            saveCached(fetched)
            Result.success(fetched)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            Log.w(TAG, "History request failed: ${error.javaClass.simpleName}: ${error.message}")
            if (cached != null) {
                Result.success(cached.copy(loadedFromCache = true, isStale = true))
            } else {
                Result.failure(error)
            }
        }
    }

    fun loadSelection(defaultBase: String, defaultQuote: String): HistorySelection {
        val base = preferences.getString(KEY_SELECTED_BASE, null)
            ?.takeIf { it.matches(CURRENCY_CODE) }
            ?: defaultBase
        var quote = preferences.getString(KEY_SELECTED_QUOTE, null)
            ?.takeIf { it.matches(CURRENCY_CODE) }
            ?: defaultQuote
        if (base == quote) quote = if (base == "USD") "EUR" else "USD"
        val period = runCatching {
            ChartPeriod.valueOf(preferences.getString(KEY_SELECTED_PERIOD, null) ?: ChartPeriod.MONTH.name)
        }.getOrDefault(ChartPeriod.MONTH)
        return HistorySelection(baseCode = base, quoteCode = quote, period = period)
    }

    fun saveSelection(baseCode: String, quoteCode: String, period: ChartPeriod) {
        preferences.edit()
            .putString(KEY_SELECTED_BASE, baseCode)
            .putString(KEY_SELECTED_QUOTE, quoteCode)
            .putString(KEY_SELECTED_PERIOD, period.name)
            .apply()
    }

    private suspend fun fetchHistory(
        baseCode: String,
        quoteCode: String,
        period: ChartPeriod,
    ): HistoricalRates = coroutineScope {
        val latestJson = downloadJson(dateToken = "latest", baseCode = baseCode)
        val latestPoint = checkNotNull(
            HistoryRateParser.parsePoint(latestJson, baseCode, quoteCode),
        ) { "Источник истории вернул некорректный последний курс" }

        val dates = HistorySampling.dates(latestPoint.date, period)
        val semaphore = Semaphore(MAX_PARALLEL_REQUESTS)
        val olderPoints = dates
            .filterNot { it == latestPoint.date }
            .map { date ->
                async {
                    semaphore.withPermit {
                        fetchPointOrNull(date, baseCode, quoteCode)
                    }
                }
            }
            .awaitAll()
            .filterNotNull()

        val points = (olderPoints + latestPoint)
            .distinctBy { it.date }
            .sortedBy { it.date }
        val minimumPoints = if (period == ChartPeriod.WEEK) 4 else 12
        check(points.size >= minimumPoints) { "Недостаточно исторических точек" }

        HistoricalRates(
            baseCode = baseCode,
            quoteCode = quoteCode,
            period = period,
            points = points,
            savedAtEpochMillis = System.currentTimeMillis(),
        )
    }

    private fun fetchPointOrNull(
        date: LocalDate,
        baseCode: String,
        quoteCode: String,
    ): HistoricalRatePoint? = try {
        val body = downloadJson(dateToken = date.toString(), baseCode = baseCode)
        HistoryRateParser.parsePoint(body, baseCode, quoteCode, expectedDate = date)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        null
    }

    private fun downloadJson(dateToken: String, baseCode: String): String {
        val base = baseCode.lowercase()
        val urls = listOf(
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@$dateToken/v1/currencies/$base.min.json",
            "https://$dateToken.currency-api.pages.dev/v1/currencies/$base.min.json",
        )
        var lastError: Throwable? = null
        urls.forEach { url ->
            try {
                return request(url)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("Источник истории недоступен")
    }

    private fun request(address: String): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "CleanRate/${BuildConfig.VERSION_NAME} Android")
        }
        try {
            check(connection.responseCode in 200..299) {
                "Источник истории ответил кодом ${connection.responseCode}"
            }
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8_192)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    check(total <= MAX_RESPONSE_BYTES) { "Ответ источника истории слишком большой" }
                    output.write(buffer, 0, read)
                }
            }
            return output.toString(StandardCharsets.UTF_8.name())
        } finally {
            connection.disconnect()
        }
    }

    private fun loadCached(
        baseCode: String,
        quoteCode: String,
        period: ChartPeriod,
    ): HistoricalRates? {
        val raw = preferences.getString(cacheKey(baseCode, quoteCode, period), null) ?: return null
        return runCatching {
            val root = JSONObject(raw)
            check(root.getString("base") == baseCode)
            check(root.getString("quote") == quoteCode)
            check(root.getString("period") == period.name)
            val pointsJson = root.getJSONArray("points")
            val points = buildList {
                for (index in 0 until pointsJson.length()) {
                    val item = pointsJson.getJSONObject(index)
                    val date = LocalDate.parse(item.getString("date"))
                    val rate = item.getString("rate").toBigDecimalOrNull()
                    if (rate != null && rate.signum() > 0) add(HistoricalRatePoint(date, rate))
                }
            }.distinctBy { it.date }.sortedBy { it.date }
            check(points.size >= 2)
            HistoricalRates(
                baseCode = baseCode,
                quoteCode = quoteCode,
                period = period,
                points = points,
                savedAtEpochMillis = root.getLong("savedAt"),
                loadedFromCache = true,
            )
        }.getOrNull()
    }

    private fun saveCached(history: HistoricalRates) {
        val points = JSONArray()
        history.points.forEach { point ->
            points.put(
                JSONObject()
                    .put("date", point.date.toString())
                    .put("rate", point.rate.toPlainString()),
            )
        }
        val root = JSONObject()
            .put("base", history.baseCode)
            .put("quote", history.quoteCode)
            .put("period", history.period.name)
            .put("savedAt", history.savedAtEpochMillis)
            .put("points", points)
        preferences.edit()
            .putString(cacheKey(history.baseCode, history.quoteCode, history.period), root.toString())
            .apply()
    }

    private fun isFresh(history: HistoricalRates): Boolean =
        System.currentTimeMillis() - history.savedAtEpochMillis <= CACHE_FRESH_MILLIS

    private fun cacheKey(baseCode: String, quoteCode: String, period: ChartPeriod): String =
        "history_${baseCode}_${quoteCode}_${period.name}"

    companion object {
        const val ATTRIBUTION_URL = "https://github.com/fawazahmed0/exchange-api"
        private const val PREFERENCES_NAME = "clean_rate_history"
        private const val TAG = "CleanRateHistory"
        private const val KEY_SELECTED_BASE = "selected_base"
        private const val KEY_SELECTED_QUOTE = "selected_quote"
        private const val KEY_SELECTED_PERIOD = "selected_period"
        private const val MAX_PARALLEL_REQUESTS = 6
        private const val MAX_RESPONSE_BYTES = 64 * 1_024
        private const val CACHE_FRESH_MILLIS = 20 * 60 * 60 * 1_000L
        private val CURRENCY_CODE = Regex("[A-Z]{3}")
    }
}
