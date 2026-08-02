package ru.abrikosov.cleanrate.data

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import java.io.ByteArrayInputStream
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.abrikosov.cleanrate.BuildConfig

class CbrRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun loadSnapshot(): CbrRateSnapshot {
        val cached = preferences.getString(KEY_XML_BASE64, null)
        if (cached != null) {
            runCatching {
                val bytes = Base64.decode(cached, Base64.DEFAULT)
                CbrXmlParser.parse(ByteArrayInputStream(bytes), loadedFromSeed = false)
                    .copy(lastCheckedEpochSeconds = lastSuccessEpochSeconds())
            }.getOrNull()?.let { return it }
        }

        return context.assets.open(SEED_FILE).use {
            CbrXmlParser.parse(it, loadedFromSeed = true)
        }
    }

    suspend fun refreshRates(): Result<CbrRateSnapshot> = withContext(Dispatchers.IO) {
        preferences.edit { putLong(KEY_LAST_ATTEMPT, System.currentTimeMillis()) }
        try {
            val bytes = HttpsClient.getBytes(
                address = RATES_URL,
                accept = "application/xml,text/xml",
                userAgent = "CleanRate/${BuildConfig.VERSION_NAME} Android",
                maximumBytes = MAX_RESPONSE_BYTES,
                serviceName = "Банк России",
            )
            val checkedAtMillis = System.currentTimeMillis()
            val snapshot = CbrXmlParser.parse(
                input = ByteArrayInputStream(bytes),
                loadedFromSeed = false,
                requireFresh = true,
            ).copy(lastCheckedEpochSeconds = checkedAtMillis / 1_000L)
            preferences.edit {
                putString(KEY_XML_BASE64, Base64.encodeToString(bytes, Base64.NO_WRAP))
                putLong(KEY_LAST_SUCCESS, checkedAtMillis)
            }
            Result.success(snapshot)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            Log.w(TAG, "CBR request failed: ${error.javaClass.simpleName}: ${error.message}")
            Result.failure(error)
        }
    }

    fun shouldRefresh(snapshot: CbrRateSnapshot): Boolean {
        val now = System.currentTimeMillis()
        val lastAttempt = preferences.getLong(KEY_LAST_ATTEMPT, 0L)
        val lastSuccess = preferences.getLong(KEY_LAST_SUCCESS, 0L)
        return now - lastAttempt > RETRY_INTERVAL_MILLIS &&
            (
                snapshot.loadedFromSeed ||
                    snapshot.isStale ||
                    lastSuccess == 0L ||
                    now - lastSuccess > REFRESH_INTERVAL_MILLIS
                )
    }

    private fun lastSuccessEpochSeconds(): Long? = preferences
        .getLong(KEY_LAST_SUCCESS, 0L)
        .takeIf { it > 0L }
        ?.div(1_000L)

    companion object {
        const val OFFICIAL_URL = "https://www.cbr.ru/currency_base/daily/"
        private const val RATES_URL = "https://www.cbr.ru/scripts/XML_daily.asp"
        private const val SEED_FILE = "seed_cbr.xml"
        private const val PREFERENCES_NAME = "clean_rate_cbr_state"
        private const val TAG = "CleanRateCbr"
        private const val KEY_XML_BASE64 = "xml_base64"
        private const val KEY_LAST_ATTEMPT = "last_attempt"
        private const val KEY_LAST_SUCCESS = "last_success"
        private const val RETRY_INTERVAL_MILLIS = 15 * 60 * 1_000L
        private const val REFRESH_INTERVAL_MILLIS = 20 * 60 * 60 * 1_000L
        private const val MAX_RESPONSE_BYTES = 128 * 1_024
    }
}
