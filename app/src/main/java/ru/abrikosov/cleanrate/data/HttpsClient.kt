package ru.abrikosov.cleanrate.data

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

internal object HttpsClient {
    suspend fun getText(
        address: String,
        accept: String,
        userAgent: String,
        maximumBytes: Int,
        serviceName: String,
    ): String = String(
        getBytes(address, accept, userAgent, maximumBytes, serviceName),
        StandardCharsets.UTF_8,
    )

    suspend fun getBytes(
        address: String,
        accept: String,
        userAgent: String,
        maximumBytes: Int,
        serviceName: String,
    ): ByteArray = runInterruptible(Dispatchers.IO) {
        getBytesBlocking(address, accept, userAgent, maximumBytes, serviceName)
    }

    private fun getBytesBlocking(
        address: String,
        accept: String,
        userAgent: String,
        maximumBytes: Int,
        serviceName: String,
    ): ByteArray {
        var currentUrl = URL(address)
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            check(currentUrl.protocol.equals("https", ignoreCase = true)) {
                "$serviceName попытался использовать незащищённое соединение"
            }
            val connection = (currentUrl.openConnection() as HttpsURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MILLIS
                readTimeout = READ_TIMEOUT_MILLIS
                instanceFollowRedirects = false
                setRequestProperty("Accept", accept)
                setRequestProperty("User-Agent", userAgent)
            }
            try {
                val responseCode = connection.responseCode
                if (responseCode in REDIRECT_CODES) {
                    check(redirectCount < MAX_REDIRECTS) { "$serviceName вернул слишком много перенаправлений" }
                    val location = connection.getHeaderField("Location")
                        ?.takeIf(String::isNotBlank)
                        ?: error("$serviceName вернул перенаправление без адреса")
                    currentUrl = URL(currentUrl, location)
                    return@repeat
                }
                check(responseCode in 200..299) { "$serviceName ответил кодом $responseCode" }
                val declaredLength = connection.contentLengthLong
                check(declaredLength < 0L || declaredLength <= maximumBytes) {
                    "$serviceName вернул слишком большой ответ"
                }
                return connection.inputStream.use { input ->
                    input.readBytesLimited(maximumBytes, "$serviceName вернул слишком большой ответ")
                }
            } finally {
                connection.disconnect()
            }
        }
        error("$serviceName не вернул данные")
    }

    private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    private const val CONNECT_TIMEOUT_MILLIS = 10_000
    private const val READ_TIMEOUT_MILLIS = 15_000
    private const val MAX_REDIRECTS = 3
}

internal fun InputStream.readBytesLimited(maximumBytes: Int, errorMessage: String): ByteArray {
    require(maximumBytes > 0)
    val output = ByteArrayOutputStream(minOf(maximumBytes, DEFAULT_BUFFER_SIZE))
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        check(total <= maximumBytes) { errorMessage }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
