package ru.abrikosov.cleanrate.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import java.math.BigDecimal
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.abrikosov.cleanrate.BuildConfig

data class ConverterSession(
    val activeCode: String,
    val expression: String,
    val amount: BigDecimal,
    val justEvaluated: Boolean,
)

private data class ValidatedRateDownload(
    val rawJson: String,
    val snapshot: RateSnapshot,
    val checkedAtMillis: Long,
)

class CurrencyRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun loadSnapshot(): RateSnapshot {
        val cached = preferences.getString(KEY_RATE_JSON, null)
        if (cached != null) {
            MarketRateParser.parse(
                rawJson = cached,
                loadedFromSeed = false,
                lastCheckedEpochSeconds = preferences.getLong(KEY_LAST_SUCCESS, 0L)
                    .takeIf { it > 0L }
                    ?.div(1_000L),
            )?.let { return it }
        }

        val seed = context.assets.open(SEED_FILE).bufferedReader().use { it.readText() }
        return checkNotNull(
            MarketRateParser.parse(seed, loadedFromSeed = true, lastCheckedEpochSeconds = null),
        ) {
            "Встроенный снимок курсов повреждён"
        }
    }

    suspend fun refreshRates(): Result<RateSnapshot> = withContext(Dispatchers.IO) {
        preferences.edit { putLong(KEY_LAST_ATTEMPT, System.currentTimeMillis()) }
        try {
            val download = downloadRates()
            preferences.edit {
                putString(KEY_RATE_JSON, download.rawJson)
                putLong(KEY_LAST_SUCCESS, download.checkedAtMillis)
            }
            Result.success(download.snapshot)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            Log.w(TAG, "Rates request failed: ${error.javaClass.simpleName}: ${error.message}")
            Result.failure(error)
        }
    }

    fun shouldRefresh(snapshot: RateSnapshot): Boolean {
        val now = System.currentTimeMillis()
        val lastAttempt = preferences.getLong(KEY_LAST_ATTEMPT, 0L)
        val lastSuccess = preferences.getLong(KEY_LAST_SUCCESS, 0L)
        val enoughTimeSinceAttempt = now - lastAttempt > RETRY_INTERVAL_MILLIS
        return enoughTimeSinceAttempt &&
            (
                snapshot.loadedFromSeed ||
                    snapshot.isStale ||
                    lastSuccess == 0L ||
                    now - lastSuccess > REFRESH_INTERVAL_MILLIS
                )
    }

    fun loadFavorites(): List<String> {
        val stored = preferences.getString(KEY_FAVORITES, null)
        return stored
            ?.split(',')
            ?.filter { it.length == 3 }
            ?.distinct()
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_FAVORITES
    }

    fun saveFavorites(codes: List<String>) {
        preferences.edit { putString(KEY_FAVORITES, codes.joinToString(",")) }
    }

    fun loadManualRates(): Map<String, BigDecimal> {
        val raw = preferences.getString(KEY_MANUAL_RATES, null) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            buildMap {
                json.keys().forEach { code ->
                    val rate = json.optString(code).toBigDecimalOrNull()
                    if (rate != null && rate > BigDecimal.ZERO) put(code, rate)
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun saveManualRates(rates: Map<String, BigDecimal>) {
        val json = JSONObject()
        rates.forEach { (code, rate) -> json.put(code, rate.toPlainString()) }
        preferences.edit { putString(KEY_MANUAL_RATES, json.toString()) }
    }

    fun loadRateSource(): RateSource = runCatching {
        RateSource.valueOf(preferences.getString(KEY_RATE_SOURCE, null) ?: RateSource.MARKET.name)
    }.getOrDefault(RateSource.MARKET)

    fun saveRateSource(source: RateSource) {
        preferences.edit { putString(KEY_RATE_SOURCE, source.name) }
    }

    fun loadUiLanguage(): UiLanguage = runCatching {
        UiLanguage.valueOf(preferences.getString(KEY_UI_LANGUAGE, null) ?: UiLanguage.RUSSIAN.name)
    }.getOrDefault(UiLanguage.RUSSIAN)

    fun saveUiLanguage(language: UiLanguage) {
        preferences.edit { putString(KEY_UI_LANGUAGE, language.name) }
    }

    fun loadKeySoundEnabled(): Boolean = preferences.getBoolean(KEY_KEY_SOUND_ENABLED, true)

    fun saveKeySoundEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_KEY_SOUND_ENABLED, enabled) }
    }

    fun loadKeyVibrationEnabled(): Boolean = preferences.getBoolean(KEY_KEY_VIBRATION_ENABLED, true)

    fun saveKeyVibrationEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_KEY_VIBRATION_ENABLED, enabled) }
    }

    fun loadChartVisible(): Boolean = preferences.getBoolean(KEY_CHART_VISIBLE, false)

    fun saveChartVisible(visible: Boolean) {
        preferences.edit { putBoolean(KEY_CHART_VISIBLE, visible) }
    }

    fun loadConverterSession(): ConverterSession? = parseConverterSession(
        activeCode = preferences.getString(KEY_ACTIVE_CODE, null),
        expression = preferences.getString(KEY_LAST_EXPRESSION, null),
        amountText = preferences.getString(KEY_LAST_AMOUNT, null),
        justEvaluated = preferences.getBoolean(KEY_JUST_EVALUATED, false),
    )

    fun saveConverterSession(
        activeCode: String,
        expression: String,
        amount: BigDecimal,
        justEvaluated: Boolean,
    ) {
        preferences.edit {
            putString(KEY_ACTIVE_CODE, activeCode)
            putString(KEY_LAST_EXPRESSION, expression)
            putString(KEY_LAST_AMOUNT, amount.toPlainString())
            putBoolean(KEY_JUST_EVALUATED, justEvaluated)
        }
    }

    private suspend fun downloadRates(): ValidatedRateDownload = firstValidSource(
        sources = RATES_URLS,
        unavailableMessage = "Источник курсов недоступен",
    ) { address ->
        val body = request(address)
        val checkedAtMillis = System.currentTimeMillis()
        val snapshot = checkNotNull(
            MarketRateParser.parse(
                rawJson = body,
                loadedFromSeed = false,
                lastCheckedEpochSeconds = checkedAtMillis / 1_000L,
                requireFresh = true,
            ),
        ) {
            "Сервис вернул некорректные данные"
        }
        ValidatedRateDownload(
            rawJson = body,
            snapshot = snapshot,
            checkedAtMillis = checkedAtMillis,
        )
    }

    private suspend fun request(address: String): String {
        return HttpsClient.getText(
            address = address,
            accept = "application/json",
            userAgent = "CleanRate/${BuildConfig.VERSION_NAME} Android",
            maximumBytes = MAX_RESPONSE_BYTES,
            serviceName = "Сервис курсов",
        )
    }

    companion object {
        const val ATTRIBUTION_URL = "https://github.com/fawazahmed0/exchange-api"
        const val ATTRIBUTION_LABEL = "currency-api"
        private const val SEED_FILE = "seed_rates.json"
        private const val TAG = "CleanRateRates"
        private const val PREFERENCES_NAME = "clean_rate_state"
        private const val KEY_RATE_JSON = "rates_json"
        private const val KEY_LAST_ATTEMPT = "last_refresh_attempt"
        private const val KEY_LAST_SUCCESS = "last_refresh_success"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_MANUAL_RATES = "manual_rates"
        private const val KEY_RATE_SOURCE = "rate_source"
        private const val KEY_UI_LANGUAGE = "ui_language"
        private const val KEY_KEY_SOUND_ENABLED = "key_sound_enabled"
        private const val KEY_KEY_VIBRATION_ENABLED = "key_vibration_enabled"
        private const val KEY_CHART_VISIBLE = "chart_visible"
        private const val KEY_ACTIVE_CODE = "active_code"
        private const val KEY_LAST_EXPRESSION = "last_expression"
        private const val KEY_LAST_AMOUNT = "last_amount"
        private const val KEY_JUST_EVALUATED = "just_evaluated"
        private const val RETRY_INTERVAL_MILLIS = 15 * 60 * 1_000L
        private const val REFRESH_INTERVAL_MILLIS = 20 * 60 * 60 * 1_000L
        private const val MAX_RESPONSE_BYTES = 128 * 1_024
        private val RATES_URLS = listOf(
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.min.json",
            "https://latest.currency-api.pages.dev/v1/currencies/usd.min.json",
        )

        val DEFAULT_FAVORITES = listOf("RUB", "USD", "EUR", "CNY", "AED", "TRY")
    }
}

internal fun parseConverterSession(
    activeCode: String?,
    expression: String?,
    amountText: String?,
    justEvaluated: Boolean = false,
): ConverterSession? {
    val validCode = activeCode?.takeIf { code ->
        code.length == 3 && code.all { it in 'A'..'Z' }
    } ?: return null
    val validExpression = expression?.takeIf { it.isNotBlank() && it.length <= 36 } ?: return null
    val amount = amountText
        ?.takeIf { it.length <= 64 }
        ?.toBigDecimalOrNull()
        ?: return null
    return ConverterSession(validCode, validExpression, amount, justEvaluated)
}
