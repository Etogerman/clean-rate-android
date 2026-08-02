package ru.abrikosov.cleanrate.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import java.time.LocalDate
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
            val fetched = withHistoryRequestTimeout(HISTORY_REQUEST_TIMEOUT_MILLIS) {
                fetchHistory(normalizedBase, normalizedQuote, period)
            }
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
        preferences.edit {
            putString(KEY_SELECTED_BASE, baseCode)
            putString(KEY_SELECTED_QUOTE, quoteCode)
            putString(KEY_SELECTED_PERIOD, period.name)
        }
    }

    private suspend fun fetchHistory(
        baseCode: String,
        quoteCode: String,
        period: ChartPeriod,
    ): HistoricalRates = coroutineScope {
        val latestPoint = downloadPoint(
            dateToken = "latest",
            baseCode = baseCode,
            quoteCode = quoteCode,
            expectedDate = null,
        )

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
            requestedPointCount = dates.size,
        )
    }

    private suspend fun fetchPointOrNull(
        date: LocalDate,
        baseCode: String,
        quoteCode: String,
    ): HistoricalRatePoint? = try {
        downloadPoint(
            dateToken = date.toString(),
            baseCode = baseCode,
            quoteCode = quoteCode,
            expectedDate = date,
        )
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        null
    }

    private suspend fun downloadPoint(
        dateToken: String,
        baseCode: String,
        quoteCode: String,
        expectedDate: LocalDate?,
    ): HistoricalRatePoint {
        val base = baseCode.lowercase()
        val urls = listOf(
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@$dateToken/v1/currencies/$base.min.json",
            "https://$dateToken.currency-api.pages.dev/v1/currencies/$base.min.json",
        )
        return firstValidSource(
            sources = urls,
            unavailableMessage = "Источник истории недоступен",
        ) { url ->
            val body = request(url)
            checkNotNull(
                HistoryRateParser.parsePoint(
                    rawJson = body,
                    baseCode = baseCode,
                    quoteCode = quoteCode,
                    expectedDate = expectedDate,
                ),
            ) {
                "Источник истории вернул некорректный курс"
            }
        }
    }

    private suspend fun request(address: String): String {
        return HttpsClient.getText(
            address = address,
            accept = "application/json",
            userAgent = "CleanRate/${BuildConfig.VERSION_NAME} Android",
            maximumBytes = MAX_RESPONSE_BYTES,
            serviceName = "Источник истории",
        )
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
                requestedPointCount = root.optInt("requestedPointCount", points.size)
                    .coerceAtLeast(points.size),
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
            .put("requestedPointCount", history.requestedPointCount)
            .put("points", points)
        val key = cacheKey(history.baseCode, history.quoteCode, history.period)
        val savedAtByKey = preferences.all.keys
            .asSequence()
            .filter { it.startsWith(CACHE_KEY_PREFIX) }
            .associateWith { cachedKey ->
                preferences.getString(cachedKey, null)
                    ?.let { raw -> runCatching { JSONObject(raw).optLong("savedAt", 0L) }.getOrDefault(0L) }
                    ?: 0L
            }
            .toMutableMap()
            .apply { this[key] = history.savedAtEpochMillis }
        preferences.edit {
            putString(key, root.toString())
            HistoryCachePolicy.keysToEvict(savedAtByKey, MAX_CACHE_ENTRIES).forEach { cachedKey ->
                remove(cachedKey)
            }
        }
    }

    private fun isFresh(history: HistoricalRates): Boolean =
        System.currentTimeMillis() - history.savedAtEpochMillis <= CACHE_FRESH_MILLIS

    private fun cacheKey(baseCode: String, quoteCode: String, period: ChartPeriod): String =
        "${CACHE_KEY_PREFIX}${baseCode}_${quoteCode}_${period.name}"

    companion object {
        const val ATTRIBUTION_URL = "https://github.com/fawazahmed0/exchange-api"
        private const val PREFERENCES_NAME = "clean_rate_history"
        private const val TAG = "CleanRateHistory"
        private const val KEY_SELECTED_BASE = "selected_base"
        private const val KEY_SELECTED_QUOTE = "selected_quote"
        private const val KEY_SELECTED_PERIOD = "selected_period"
        private const val CACHE_KEY_PREFIX = "history_"
        private const val MAX_PARALLEL_REQUESTS = 6
        private const val MAX_CACHE_ENTRIES = 24
        private const val MAX_RESPONSE_BYTES = 64 * 1_024
        private const val HISTORY_REQUEST_TIMEOUT_MILLIS = 60_000L
        private const val CACHE_FRESH_MILLIS = 20 * 60 * 60 * 1_000L
        private val CURRENCY_CODE = Regex("[A-Z]{3}")
    }
}

internal class HistoryRequestTimeoutException : IllegalStateException(
    "Превышено время загрузки истории курса",
)

internal suspend fun <T : Any> withHistoryRequestTimeout(
    timeoutMillis: Long,
    block: suspend CoroutineScope.() -> T,
): T = withTimeoutOrNull(timeoutMillis, block) ?: throw HistoryRequestTimeoutException()
