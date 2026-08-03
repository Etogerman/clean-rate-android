package ru.abrikosov.cleanrate.data

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.SAXException

object CbrXmlParser {
    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun parse(
        input: InputStream,
        loadedFromSeed: Boolean,
        requireFresh: Boolean = false,
        today: LocalDate = LocalDate.now(),
    ): CbrRateSnapshot {
        val bytes = input.readBytesLimited(MAX_XML_BYTES, "Ответ Банка России слишком большой")
        check(bytes.none { it == 0.toByte() }) { "Неподдерживаемая кодировка XML Банка России" }
        val lexicalXml = String(bytes, StandardCharsets.ISO_8859_1).uppercase(Locale.ROOT)
        check("<!DOCTYPE" !in lexicalXml && "<!ENTITY" !in lexicalXml) {
            "XML Банка России содержит запрещённое объявление"
        }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isValidating = false
            isExpandEntityReferences = false
            // Набор поддерживаемых JAXP-флагов различается между JVM и Android.
            // Это дополнительный слой: объявления уже запрещены побайтовой проверкой,
            // а EntityResolver ниже блокирует любое обращение к внешнему ресурсу.
            setFeatureWhenSupported(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeatureWhenSupported("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeatureWhenSupported("http://xml.org/sax/features/external-general-entities", false)
            setFeatureWhenSupported("http://xml.org/sax/features/external-parameter-entities", false)
            setFeatureWhenSupported("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }
        val builder = factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> throw SAXException("Внешние XML-сущности запрещены") }
        }
        val document = builder.parse(ByteArrayInputStream(bytes))
        val root = document.documentElement
        check(root.tagName == "ValCurs") { "Неожиданный корневой элемент XML" }
        val officialDate = LocalDate.parse(root.getAttribute("Date"), dateFormatter)
        val isStale = !RateFreshnessPolicy.isCbrFresh(officialDate, today)
        check(!requireFresh || !isStale) { "Банк России вернул неактуальную дату курса" }
        val rublesPerUnit = buildMap {
            val nodes = root.getElementsByTagName("Valute")
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as? Element ?: continue
                val code = element.text("CharCode")
                val nominal = element.text("Nominal").toBigDecimalOrNull()
                val value = element.text("Value").replace(',', '.').toBigDecimalOrNull()
                if (
                    code.matches(CURRENCY_CODE) &&
                    nominal != null && nominal > BigDecimal.ZERO &&
                    value != null && value > BigDecimal.ZERO
                ) {
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
            isStale = isStale,
        )
    }

    private fun Element.text(tagName: String): String =
        getElementsByTagName(tagName).item(0)?.textContent?.trim().orEmpty()

    private fun DocumentBuilderFactory.setFeatureWhenSupported(feature: String, value: Boolean) {
        try {
            setFeature(feature, value)
        } catch (_: ParserConfigurationException) {
            // Android может не реализовать JAXP-флаг; независимые обязательные барьеры описаны выше.
        }
    }

    private val CURRENCY_CODE = Regex("[A-Z]{3}")
    private const val MAX_XML_BYTES = 128 * 1_024
}
