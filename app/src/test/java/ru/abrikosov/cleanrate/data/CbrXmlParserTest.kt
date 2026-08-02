package ru.abrikosov.cleanrate.data

import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
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
        )

        assertEquals(LocalDate.of(2026, 8, 2), snapshot.officialDate)
        assertDecimal("1", snapshot.rates["USD"])
        assertDecimal("80", snapshot.rates["RUB"])
        assertDecimal("0.8", snapshot.rates["EUR"])
        assertDecimal("160", snapshot.rates["JPY"])
    }

    private fun extraCurrencies(): String = (1..21).joinToString("") { index ->
        val code = "X${index.toString().padStart(2, '0')}"
        "<Valute><CharCode>$code</CharCode><Nominal>1</Nominal><Value>${index},0</Value></Valute>"
    }

    private fun assertDecimal(expected: String, actual: BigDecimal?) {
        assertEquals(0, BigDecimal(expected).compareTo(requireNotNull(actual)))
    }
}
