package ru.abrikosov.cleanrate.data

import java.util.concurrent.CancellationException

internal suspend fun <T> firstValidSource(
    sources: Iterable<String>,
    unavailableMessage: String,
    loadAndValidate: suspend (String) -> T,
): T {
    var lastError: Exception? = null
    for (source in sources) {
        try {
            return loadAndValidate(source)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            lastError = error
        }
    }
    throw lastError ?: IllegalStateException(unavailableMessage)
}
