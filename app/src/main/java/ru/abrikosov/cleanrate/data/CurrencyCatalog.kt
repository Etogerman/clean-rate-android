package ru.abrikosov.cleanrate.data

import java.util.Currency
import java.util.Locale

object CurrencyCatalog {
    private val russianSpecialNames = mapOf(
        "CLF" to "Расчётная единица Чили",
        "CNH" to "Китайский юань (офшорный)",
        "FOK" to "Фарерская крона",
        "KID" to "Доллар Кирибати",
        "SLL" to "Леоне Сьерра-Леоне (старый)",
        "TVD" to "Доллар Тувалу",
        "XAF" to "Франк КФА BEAC",
        "XCG" to "Карибский гульден",
        "XDR" to "Специальные права заимствования",
        "XOF" to "Франк КФА BCEAO",
        "XPF" to "Франк КФП",
        "ZWG" to "Зимбабвийское золото",
    )

    private val englishSpecialNames = mapOf(
        "CLF" to "Chilean unit of account (UF)",
        "CNH" to "Chinese yuan (offshore)",
        "FOK" to "Faroese króna",
        "KID" to "Kiribati dollar",
        "SLL" to "Sierra Leonean leone (old)",
        "TVD" to "Tuvaluan dollar",
        "XAF" to "Central African CFA franc",
        "XCG" to "Caribbean guilder",
        "XDR" to "Special Drawing Rights",
        "XOF" to "West African CFA franc",
        "XPF" to "CFP franc",
        "ZWG" to "Zimbabwe Gold",
    )

    private val frenchSpecialNames = mapOf(
        "CLF" to "Unité de compte chilienne (UF)",
        "CNH" to "Yuan chinois (offshore)",
        "FOK" to "Couronne féroïenne",
        "KID" to "Dollar kiribatien",
        "SLL" to "Leone sierra-léonais (ancien)",
        "TVD" to "Dollar tuvaluan",
        "XAF" to "Franc CFA d’Afrique centrale",
        "XCG" to "Florin caribéen",
        "XDR" to "Droits de tirage spéciaux",
        "XOF" to "Franc CFA d’Afrique de l’Ouest",
        "XPF" to "Franc CFP",
        "ZWG" to "Or du Zimbabwe",
    )

    private val regions = mapOf(
        "AED" to "AE", "AFN" to "AF", "ALL" to "AL", "AMD" to "AM",
        "ARS" to "AR", "AUD" to "AU", "AZN" to "AZ", "BDT" to "BD",
        "BGN" to "BG", "BHD" to "BH", "BRL" to "BR", "BYN" to "BY",
        "CAD" to "CA", "CHF" to "CH", "CLP" to "CL", "CNY" to "CN",
        "COP" to "CO", "CZK" to "CZ", "DKK" to "DK", "DZD" to "DZ",
        "EGP" to "EG", "EUR" to "EU", "GBP" to "GB", "GEL" to "GE",
        "GHS" to "GH", "HKD" to "HK", "HUF" to "HU", "IDR" to "ID",
        "ILS" to "IL", "INR" to "IN", "IQD" to "IQ", "IRR" to "IR",
        "ISK" to "IS", "JOD" to "JO", "JPY" to "JP", "KES" to "KE",
        "KGS" to "KG", "KRW" to "KR", "KWD" to "KW", "KZT" to "KZ",
        "LBP" to "LB", "LKR" to "LK", "MAD" to "MA", "MDL" to "MD",
        "MGA" to "MG", "MXN" to "MX", "MYR" to "MY", "NGN" to "NG",
        "NOK" to "NO", "NPR" to "NP", "NZD" to "NZ", "OMR" to "OM",
        "PEN" to "PE", "PHP" to "PH", "PKR" to "PK", "PLN" to "PL",
        "QAR" to "QA", "RON" to "RO", "RSD" to "RS", "RUB" to "RU",
        "SAR" to "SA", "SEK" to "SE", "SGD" to "SG", "THB" to "TH",
        "TJS" to "TJ", "TMT" to "TM", "TND" to "TN", "TRY" to "TR",
        "TWD" to "TW", "UAH" to "UA", "USD" to "US", "UYU" to "UY",
        "UZS" to "UZ", "VES" to "VE", "VND" to "VN", "ZAR" to "ZA",
    )

    fun metadata(code: String, language: UiLanguage = UiLanguage.RUSSIAN): CurrencyMetadata {
        val locale = language.locale
        val javaCurrency = runCatching { Currency.getInstance(code) }.getOrNull()
        val specialNames = when (language) {
            UiLanguage.RUSSIAN -> russianSpecialNames
            UiLanguage.ENGLISH -> englishSpecialNames
            UiLanguage.FRENCH -> frenchSpecialNames
        }
        val name = specialNames[code]
            ?: javaCurrency?.getDisplayName(locale)?.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(locale) else it.toString()
            }
            ?: when (language) {
                UiLanguage.RUSSIAN -> "Валюта $code"
                UiLanguage.ENGLISH -> "$code currency"
                UiLanguage.FRENCH -> "Devise $code"
            }

        return CurrencyMetadata(
            code = code,
            name = name,
            symbol = javaCurrency?.getSymbol(locale) ?: code,
            flag = regions[code]?.toFlagEmoji() ?: "◉",
        )
    }

    private fun String.toFlagEmoji(): String = uppercase(Locale.ROOT)
        .map { char -> Character.toChars(0x1F1E6 + (char.code - 'A'.code)).concatToString() }
        .joinToString("")
}
