package ru.abrikosov.cleanrate.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.abrikosov.cleanrate.data.CbrRateSnapshot
import ru.abrikosov.cleanrate.data.CbrRepository
import ru.abrikosov.cleanrate.data.CurrencyRepository
import ru.abrikosov.cleanrate.data.RateOrigin
import ru.abrikosov.cleanrate.data.RateSnapshot
import ru.abrikosov.cleanrate.data.RateSource
import ru.abrikosov.cleanrate.data.UiLanguage
import ru.abrikosov.cleanrate.domain.CalculatorEngine
import ru.abrikosov.cleanrate.domain.ConversionEngine
import ru.abrikosov.cleanrate.domain.RateResolver

data class ConverterUiState(
    val marketSnapshot: RateSnapshot,
    val cbrSnapshot: CbrRateSnapshot,
    val rateSource: RateSource,
    val uiLanguage: UiLanguage,
    val favorites: List<String>,
    val manualRates: Map<String, BigDecimal>,
    val keySoundEnabled: Boolean,
    val keyVibrationEnabled: Boolean,
    val isChartVisible: Boolean = false,
    val activeCode: String = "RUB",
    val expression: String = "1000",
    val amount: BigDecimal = BigDecimal("1000"),
    val justEvaluated: Boolean = false,
    val isRefreshing: Boolean = false,
    val message: UiMessage? = null,
) {
    val effectiveRates: Map<String, BigDecimal>
        get() = RateResolver.resolve(
            source = rateSource,
            marketRates = marketSnapshot.rates,
            cbrRates = cbrSnapshot.rates,
            customRates = manualRates,
        )

    val allCodes: List<String>
        get() = marketSnapshot.rates.keys.sorted()

    fun originFor(code: String): RateOrigin = RateResolver.origin(
        code = code,
        source = rateSource,
        cbrRates = cbrSnapshot.rates,
        customRates = manualRates,
    )
}

class ConverterViewModel(application: Application) : AndroidViewModel(application) {
    private val marketRepository = CurrencyRepository(application)
    private val cbrRepository = CbrRepository(application)
    private val initialMarketSnapshot = marketRepository.loadSnapshot()
    private val initialCbrSnapshot = cbrRepository.loadSnapshot()
    private val initialFavorites = marketRepository.loadFavorites()
        .filter { it in initialMarketSnapshot.rates }
        .ifEmpty { CurrencyRepository.DEFAULT_FAVORITES.filter { it in initialMarketSnapshot.rates } }
    private val restoredSession = marketRepository.loadConverterSession()?.takeIf {
        it.activeCode in initialFavorites && it.activeCode in initialMarketSnapshot.rates
    }

    private val _uiState = MutableStateFlow(
        ConverterUiState(
            marketSnapshot = initialMarketSnapshot,
            cbrSnapshot = initialCbrSnapshot,
            rateSource = marketRepository.loadRateSource(),
            uiLanguage = marketRepository.loadUiLanguage(),
            favorites = initialFavorites,
            manualRates = marketRepository.loadManualRates(),
            keySoundEnabled = marketRepository.loadKeySoundEnabled(),
            keyVibrationEnabled = marketRepository.loadKeyVibrationEnabled(),
            isChartVisible = marketRepository.loadChartVisible(),
            activeCode = restoredSession?.activeCode ?: initialFavorites.first(),
            expression = restoredSession?.expression ?: "1000",
            amount = restoredSession?.amount ?: BigDecimal("1000"),
            justEvaluated = restoredSession?.justEvaluated ?: false,
        ),
    )
    val uiState: StateFlow<ConverterUiState> = _uiState.asStateFlow()

    init {
        refreshRates(showSuccessMessage = false, force = false)
    }

    fun refreshRates(showSuccessMessage: Boolean = true, force: Boolean = true) {
        val state = _uiState.value
        if (state.isRefreshing) return

        val refreshMarket = force || marketRepository.shouldRefresh(state.marketSnapshot)
        val refreshCbr = state.rateSource == RateSource.CBR &&
            (force || cbrRepository.shouldRefresh(state.cbrSnapshot))
        if (!refreshMarket && !refreshCbr) return

        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val (marketResult, cbrResult) = coroutineScope {
                val marketDeferred = async {
                    if (refreshMarket) marketRepository.refreshRates() else null
                }
                val cbrDeferred = async {
                    if (refreshCbr) cbrRepository.refreshRates() else null
                }
                marketDeferred.await() to cbrDeferred.await()
            }
            val hasFailure = marketResult?.isFailure == true || cbrResult?.isFailure == true

            _uiState.update { current ->
                current.copy(
                    marketSnapshot = marketResult?.getOrNull() ?: current.marketSnapshot,
                    cbrSnapshot = cbrResult?.getOrNull() ?: current.cbrSnapshot,
                    isRefreshing = false,
                    message = when {
                        hasFailure -> UiMessage.SourcesUnavailable
                        showSuccessMessage -> UiMessage.RatesUpdated
                        else -> null
                    },
                )
            }
        }
    }

    fun selectRateSource(source: RateSource) {
        if (_uiState.value.rateSource == source) return
        marketRepository.saveRateSource(source)
        _uiState.update {
            it.copy(
                rateSource = source,
                message = if (source == RateSource.CUSTOM && it.manualRates.isEmpty()) {
                    UiMessage.AddBankRate
                } else {
                    null
                },
            )
        }
        refreshRates(showSuccessMessage = false, force = false)
    }

    fun pressKey(key: String) {
        val state = _uiState.value
        val compactExpression = state.expression.replace(" ", "")
        val operators = setOf("+", "−", "×", "÷")

        val newExpression = when {
            key == "C" -> "0"
            key == "⌫" -> compactExpression.dropLast(1).ifBlank { "0" }
            key == "=" -> editableNumber(state.amount)
            key == "±" -> editableNumber(state.amount.negate())
            key in operators -> {
                val base = if (state.justEvaluated) editableNumber(state.amount) else compactExpression
                when {
                    base.isBlank() -> "0$key"
                    base.last().toString() in operators -> base.dropLast(1) + key
                    base.last() == ',' -> base + "0$key"
                    else -> base + key
                }
            }
            key == "," -> appendDecimal(compactExpression, state.justEvaluated)
            key.all(Char::isDigit) -> appendDigits(compactExpression, key, state.justEvaluated)
            else -> compactExpression
        }.take(MAX_EXPRESSION_LENGTH)

        val evaluated = CalculatorEngine.evaluate(newExpression)
        _uiState.update {
            it.copy(
                expression = newExpression,
                amount = evaluated ?: it.amount,
                justEvaluated = key in setOf("=", "±"),
            )
        }
        saveConverterSession()
    }

    fun selectUiLanguage(language: UiLanguage) {
        if (_uiState.value.uiLanguage == language) return
        marketRepository.saveUiLanguage(language)
        _uiState.update { it.copy(uiLanguage = language, message = null) }
    }

    fun setKeySoundEnabled(enabled: Boolean) {
        marketRepository.saveKeySoundEnabled(enabled)
        _uiState.update { it.copy(keySoundEnabled = enabled) }
    }

    fun setKeyVibrationEnabled(enabled: Boolean) {
        marketRepository.saveKeyVibrationEnabled(enabled)
        _uiState.update { it.copy(keyVibrationEnabled = enabled) }
    }

    fun openChart() {
        setChartVisible(true)
    }

    fun closeChart() {
        setChartVisible(false)
    }

    fun selectCurrency(code: String) {
        val state = _uiState.value
        if (code == state.activeCode) return
        val converted = ConversionEngine.convert(
            amount = state.amount,
            fromCode = state.activeCode,
            toCode = code,
            rates = state.effectiveRates,
        ) ?: return

        _uiState.update {
            it.copy(
                activeCode = code,
                expression = editableNumber(converted),
                amount = converted,
                justEvaluated = true,
            )
        }
        saveConverterSession()
    }

    fun convertedAmount(code: String): BigDecimal? {
        val state = _uiState.value
        return ConversionEngine.convert(
            amount = state.amount,
            fromCode = state.activeCode,
            toCode = code,
            rates = state.effectiveRates,
        )
    }

    fun toggleFavorite(code: String) {
        val state = _uiState.value
        val newFavorites = if (code in state.favorites) {
            if (state.favorites.size == 1) {
                _uiState.update { it.copy(message = UiMessage.KeepOneCurrency) }
                return
            }
            state.favorites - code
        } else {
            state.favorites + code
        }

        val newActiveCode = if (state.activeCode in newFavorites) {
            state.activeCode
        } else {
            newFavorites.first()
        }
        val newAmount = if (newActiveCode == state.activeCode) {
            state.amount
        } else {
            ConversionEngine.convert(
                amount = state.amount,
                fromCode = state.activeCode,
                toCode = newActiveCode,
                rates = state.effectiveRates,
            ) ?: state.amount
        }

        marketRepository.saveFavorites(newFavorites)
        _uiState.update {
            it.copy(
                favorites = newFavorites,
                activeCode = newActiveCode,
                expression = if (newActiveCode == state.activeCode) it.expression else editableNumber(newAmount),
                amount = newAmount,
                justEvaluated = newActiveCode != state.activeCode,
            )
        }
        saveConverterSession()
    }

    fun moveFavorite(code: String, direction: Int) {
        val favorites = _uiState.value.favorites.toMutableList()
        val from = favorites.indexOf(code)
        val to = from + direction
        if (from !in favorites.indices || to !in favorites.indices) return
        val moved = favorites.removeAt(from)
        favorites.add(to, moved)
        marketRepository.saveFavorites(favorites)
        _uiState.update { it.copy(favorites = favorites) }
    }

    fun setManualRate(code: String, rate: BigDecimal?) {
        if (code == "USD") return
        val updated = _uiState.value.manualRates.toMutableMap()
        if (rate == null) updated.remove(code) else updated[code] = rate
        marketRepository.saveManualRates(updated)
        if (rate != null) marketRepository.saveRateSource(RateSource.CUSTOM)
        _uiState.update {
            it.copy(
                manualRates = updated,
                rateSource = if (rate != null) RateSource.CUSTOM else it.rateSource,
                message = if (rate == null) UiMessage.ManualRateDeleted(code) else UiMessage.ManualRateSaved(code),
            )
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun appendDigits(expression: String, digits: String, reset: Boolean): String {
        if (reset || expression == "0") return digits.trimStart('0').ifBlank { "0" }
        val lastNumber = expression.substringAfterLastOperator()
        if (lastNumber == "0" && !lastNumber.contains(',')) {
            return expression.dropLast(1) + digits.trimStart('0').ifBlank { "0" }
        }
        return expression + digits
    }

    private fun appendDecimal(expression: String, reset: Boolean): String {
        if (reset) return "0,"
        val lastNumber = expression.substringAfterLastOperator()
        return if (',' in lastNumber) expression else expression + if (lastNumber.isEmpty()) "0," else ","
    }

    private fun String.substringAfterLastOperator(): String {
        val index = indexOfLast { it in charArrayOf('+', '−', '×', '÷') }
        return substring(index + 1)
    }

    private fun editableNumber(value: BigDecimal): String = value
        .setScale(8, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
        .replace('.', ',')

    private fun saveConverterSession() {
        val state = _uiState.value
        marketRepository.saveConverterSession(
            activeCode = state.activeCode,
            expression = state.expression,
            amount = state.amount,
            justEvaluated = state.justEvaluated,
        )
    }

    private fun setChartVisible(visible: Boolean) {
        if (_uiState.value.isChartVisible == visible) return
        marketRepository.saveChartVisible(visible)
        _uiState.update { it.copy(isChartVisible = visible) }
    }

    companion object {
        private const val MAX_EXPRESSION_LENGTH = 36
    }
}
