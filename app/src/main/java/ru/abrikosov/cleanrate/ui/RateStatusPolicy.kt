package ru.abrikosov.cleanrate.ui

import ru.abrikosov.cleanrate.data.RateSource

internal data class RateStatusHealth(
    val loadedFromSeed: Boolean,
    val isStale: Boolean,
    val lastCheckedEpochSeconds: Long?,
)

internal object RateStatusPolicy {
    fun evaluate(state: ConverterUiState): RateStatusHealth {
        val market = RateStatusHealth(
            loadedFromSeed = state.marketSnapshot.loadedFromSeed,
            isStale = state.marketSnapshot.isStale,
            lastCheckedEpochSeconds = state.marketSnapshot.lastCheckedEpochSeconds,
        )
        val cbr = RateStatusHealth(
            loadedFromSeed = state.cbrSnapshot.loadedFromSeed,
            isStale = state.cbrSnapshot.isStale,
            lastCheckedEpochSeconds = state.cbrSnapshot.lastCheckedEpochSeconds,
        )
        return when (state.rateSource) {
            RateSource.MARKET, RateSource.CUSTOM -> market
            RateSource.CBR -> {
                val usesMarketFallback = state.favorites.any { code ->
                    code !in state.cbrSnapshot.rates
                }
                if (usesMarketFallback) merge(cbr, market) else cbr
            }
        }
    }

    private fun merge(vararg sources: RateStatusHealth): RateStatusHealth {
        // Сводное время относится ко всему набору используемых источников.
        // Если хотя бы один из них ещё не проверялся, время другого источника
        // нельзя показывать как время проверки всего набора.
        val checkedTimes = sources.map(RateStatusHealth::lastCheckedEpochSeconds)
        return RateStatusHealth(
            loadedFromSeed = sources.any(RateStatusHealth::loadedFromSeed),
            isStale = sources.any(RateStatusHealth::isStale),
            lastCheckedEpochSeconds = checkedTimes
                .takeIf { times -> times.all { it != null } }
                ?.filterNotNull()
                ?.minOrNull(),
        )
    }
}
