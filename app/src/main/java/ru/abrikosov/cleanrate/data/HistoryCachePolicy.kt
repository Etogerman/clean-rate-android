package ru.abrikosov.cleanrate.data

internal object HistoryCachePolicy {
    fun keysToEvict(savedAtByKey: Map<String, Long>, maximumEntries: Int): Set<String> {
        require(maximumEntries >= 0)
        return savedAtByKey.entries
            .sortedWith(compareBy<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .take((savedAtByKey.size - maximumEntries).coerceAtLeast(0))
            .mapTo(linkedSetOf(), Map.Entry<String, Long>::key)
    }
}
