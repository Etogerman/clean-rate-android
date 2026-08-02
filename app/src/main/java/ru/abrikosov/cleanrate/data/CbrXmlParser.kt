package ru.abrikosov.cleanrate.data

import java.io.InputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

object CbrXmlParser {
    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun parse(input: InputStream, loadedFromSeed: Boolean): CbrRateSnapshot {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        val document = factory.newDocumentBuilder().parse(input)
        val root = document.documentElement
        val officialDate = LocalDate.parse(root.getAttribute("Date"), dateFormatter)
        val rublesPerUnit = buildMap {
            val nodes = root.getElementsByTagName("Valute")
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as? Element ?: continue
                val code = element.text("CharCode")
                val nominal = element.text("Nominal").toBigDecimalOrNull()
                val value = element.text("Value").replace(',', '.').toBigDecimalOrNull()
                if (code.length == 3 && nominal != null && nominal > BigDecimal.ZERO && value != null) {
                    put(code, value.divide(nominal, 18, RoundingMode.HALF_UP))
                }
            }
        }

        val usdRub = checkNotNull(rublesPerUnit["USD"]) { "В ответе Банка России нет курса USD" }
        val normalizedRates = buildMap {
            put("USD", BigDecimal.ONE)
            put("RUB", usdRub)
            rublesPerUnit.forEach { (code, rublesForOneUnit) ->
                if (rublesForOneUnit > BigDecimal.ZERO) {
                    put(code, usdRub.divide(rublesForOneUnit, 18, RoundingMode.HALF_UP))
                }
            }
        }
        check(normalizedRates.size > 20) { "В ответе Банка России слишком мало валют" }
        return CbrRateSnapshot(
            rates = normalizedRates,
            officialDate = officialDate,
            loadedFromSeed = loadedFromSeed,
        )
    }

    private fun Element.text(tagName: String): String =
        getElementsByTagName(tagName).item(0)?.textContent?.trim().orEmpty()
}
