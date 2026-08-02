package ru.abrikosov.cleanrate.data

import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CbrXmlParserTest {
    @Test
    fun `учитывает номинал и нормализует курсы относительно USD`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ValCurs Date="02.08.2026" name="Foreign Currency Market">
                <Valute><CharCode>USD</CharCode><Nominal>1</Nominal><Value>80,0000</Value></Valute>
                <Valute><CharCode>EUR</CharCode><Nominal>1</Nominal><Value>100,0000</Value></Valute>
                <Valute><CharCode>JPY</CharCode><Nominal>100</Nominal><Value>50,0000</Value></Valute>
                ${extraCurrencies()}
            </ValCurs>
        """.trimIndent()

        val snapshot = CbrXmlParser.parse(
            ByteArrayInputStream(xml.toByteArray()),
            loadedFromSeed = false,
            today = LocalDate.of(2026, 8, 2),
        )

        assertEquals(LocalDate.of(2026, 8, 2), snapshot.officialDate)
        assertDecimal("1", snapshot.rates["USD"])
        assertDecimal("80", snapshot.rates["RUB"])
        assertDecimal("0.8", snapshot.rates["EUR"])
        assertDecimal("160", snapshot.rates["JPY"])
    }

    @Test
    fun `запрещает doctype и внешние сущности`() {
        val xml = """
            <?xml version="1.0"?>
            <!DOCTYPE ValCurs [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
            <ValCurs Date="02.08.2026"><Value>&secret;</Value></ValCurs>
        """.trimIndent()

        assertThrows(IllegalStateException::class.java) {
            CbrXmlParser.parse(
                ByteArrayInputStream(xml.toByteArray()),
                loadedFromSeed = false,
                today = LocalDate.of(2026, 8, 2),
            )
        }
    }

    @Test
    fun `ограничивает размер xml до разбора`() {
        val oversized = ByteArray(128 * 1_024 + 1) { 'x'.code.toByte() }

        assertThrows(IllegalStateException::class.java) {
            CbrXmlParser.parse(
                ByteArrayInputStream(oversized),
                loadedFromSeed = false,
                today = LocalDate.of(2026, 8, 2),
            )
        }
    }

    @Test
    fun `отклоняет utf16 чтобы нельзя было скрыть doctype нулевыми байтами`() {
        val disguisedDoctype = """
            <?xml version="1.0" encoding="UTF-16"?>
            <!DOCTYPE ValCurs [<!ENTITY secret SYSTEM "https://example.com/secret">]>
            <ValCurs Date="02.08.2026" />
        """.trimIndent().toByteArray(Charsets.UTF_16)

        assertThrows(IllegalStateException::class.java) {
            CbrXmlParser.parse(
                ByteArrayInputStream(disguisedDoctype),
                loadedFromSeed = false,
                today = LocalDate.of(2026, 8, 2),
            )
        }
    }

    private fun extraCurrencies(): String = (0 until 21).joinToString("") { index ->
        val code = "X${('A'.code + index / 26).toChar()}${('A'.code + index % 26).toChar()}"
        "<Valute><CharCode>$code</CharCode><Nominal>1</Nominal><Value>${index},0</Value></Valute>"
    }

    private fun assertDecimal(expected: String, actual: BigDecimal?) {
        assertEquals(0, BigDecimal(expected).compareTo(requireNotNull(actual)))
    }
}
