package ru.abrikosov.cleanrate.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.abrikosov.cleanrate.data.ChartPeriod
import ru.abrikosov.cleanrate.data.HistoricalRatePoint
import ru.abrikosov.cleanrate.data.HistoricalRatesRepository

data class HistoryUiState(
    val baseCode: String = "RUB",
    val quoteCode: String = "USD",
    val period: ChartPeriod = ChartPeriod.MONTH,
    val amountText: String = "1",
    val points: List<HistoricalRatePoint> = emptyList(),
    val isLoading: Boolean = false,
    val loadedFromCache: Boolean = false,
    val isStale: Boolean = false,
    val missingPointCount: Int = 0,
    val hasError: Boolean = false,
    val initialized: Boolean = false,
) {
    val amount: BigDecimal?
        get() = amountText.replace(',', '.').toBigDecimalOrNull()?.takeIf { it >= BigDecimal.ZERO }

    val latestRate: BigDecimal?
        get() = points.lastOrNull()?.rate

    val convertedAmount: BigDecimal?
        get() = convertedAmountAt(latestRate)

    fun convertedAmountAt(rate: BigDecimal?): BigDecimal? =
        amount?.let { value -> rate?.multiply(value) }

    val minimumRate: BigDecimal?
        get() = points.minByOrNull { it.rate }?.rate

    val maximumRate: BigDecimal?
        get() = points.maxByOrNull { it.rate }?.rate
}

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = HistoricalRatesRepository(application)
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    fun initialize(defaultBase: String, defaultQuote: String, amount: BigDecimal) {
        if (_uiState.value.initialized) return
        val selection = repository.loadSelection(defaultBase, defaultQuote)
        _uiState.value = HistoryUiState(
            baseCode = selection.baseCode,
            quoteCode = selection.quoteCode,
            period = selection.period,
            amountText = editableNumber(defaultChartAmount(amount)),
            initialized = true,
        )
        load(clearExisting = true)
    }

    fun selectBase(code: String) {
        val current = _uiState.value
        if (code == current.baseCode) return
        val quote = if (code == current.quoteCode) current.baseCode else current.quoteCode
        updatePair(baseCode = code, quoteCode = quote)
    }

    fun selectQuote(code: String) {
        val current = _uiState.value
        if (code == current.quoteCode) return
        val base = if (code == current.baseCode) current.quoteCode else current.baseCode
        updatePair(baseCode = base, quoteCode = code)
    }

    fun swapCurrencies() {
        val current = _uiState.value
        updatePair(baseCode = current.quoteCode, quoteCode = current.baseCode)
    }

    fun selectPeriod(period: ChartPeriod) {
        if (_uiState.value.period == period) return
        _uiState.update {
            it.copy(
                period = period,
                points = emptyList(),
                hasError = false,
                loadedFromCache = false,
                isStale = false,
                missingPointCount = 0,
            )
        }
        saveSelection()
        load(clearExisting = true)
    }

    fun setAmount(rawValue: String) {
        val filtered = buildString {
            var hasSeparator = false
            rawValue.forEach { character ->
                when {
                    character.isDigit() -> append(character)
                    character in charArrayOf(',', '.') && !hasSeparator -> {
                        if (isEmpty()) append('0')
                        append(character)
                        hasSeparator = true
                    }
                }
            }
        }.take(MAX_AMOUNT_LENGTH)
        _uiState.update { it.copy(amountText = filtered) }
    }

    fun refresh() {
        if (!_uiState.value.initialized || _uiState.value.isLoading) return
        load(clearExisting = false, forceRefresh = true)
    }

    private fun updatePair(baseCode: String, quoteCode: String) {
        _uiState.update {
            it.copy(
                baseCode = baseCode,
                quoteCode = quoteCode,
                points = emptyList(),
                hasError = false,
                loadedFromCache = false,
                isStale = false,
                missingPointCount = 0,
            )
        }
        saveSelection()
        load(clearExisting = true)
    }

    private fun load(clearExisting: Boolean, forceRefresh: Boolean = false) {
        val request = _uiState.value
        if (!request.initialized) return
        loadJob?.cancel()
        _uiState.update {
            it.copy(
                isLoading = true,
                points = if (clearExisting) emptyList() else it.points,
                hasError = false,
            )
        }
        loadJob = viewModelScope.launch {
            val result = repository.loadHistory(
                baseCode = request.baseCode,
                quoteCode = request.quoteCode,
                period = request.period,
                forceRefresh = forceRefresh,
            )
            _uiState.update { current ->
                if (
                    current.baseCode != request.baseCode ||
                    current.quoteCode != request.quoteCode ||
                    current.period != request.period
                ) {
                    current
                } else {
                    result.fold(
                        onSuccess = { history ->
                            current.copy(
                                points = history.points,
                                isLoading = false,
                                loadedFromCache = history.loadedFromCache,
                                isStale = history.isStale,
                                missingPointCount = history.missingPointCount,
                                hasError = false,
                            )
                        },
                        onFailure = {
                            current.copy(isLoading = false, hasError = true)
                        },
                    )
                }
            }
        }
    }

    private fun saveSelection() {
        val state = _uiState.value
        repository.saveSelection(state.baseCode, state.quoteCode, state.period)
    }

    private fun editableNumber(value: BigDecimal): String = value
        .setScale(8, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()

    companion object {
        private const val MAX_AMOUNT_LENGTH = 24
    }
}

internal fun defaultChartAmount(amount: BigDecimal): BigDecimal =
    amount.takeIf { it > BigDecimal.ZERO } ?: BigDecimal.ONE
