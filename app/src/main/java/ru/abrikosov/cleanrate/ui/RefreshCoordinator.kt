package ru.abrikosov.cleanrate.ui

internal data class RefreshRequest(
    val showSuccessMessage: Boolean,
    val force: Boolean,
) {
    fun merge(other: RefreshRequest): RefreshRequest = RefreshRequest(
        showSuccessMessage = showSuccessMessage || other.showSuccessMessage,
        force = force || other.force,
    )
}

/**
 * Сохраняет повторный запрос, поступивший во время загрузки. После завершения
 * текущей операции новый план строится уже по актуальному выбранному источнику.
 */
internal class RefreshCoordinator {
    private var isRunning = false
    private var pendingRequest: RefreshRequest? = null

    fun submit(request: RefreshRequest): RefreshRequest? {
        if (isRunning) {
            pendingRequest = pendingRequest?.merge(request) ?: request
            return null
        }
        isRunning = true
        return request
    }

    fun complete(): RefreshRequest? {
        check(isRunning) { "Нет выполняющегося обновления" }
        val next = pendingRequest
        pendingRequest = null
        isRunning = next != null
        return next
    }
}
