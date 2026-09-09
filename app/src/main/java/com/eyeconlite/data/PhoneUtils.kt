package com.eyeconlite.data

data class NormalizedNumber(
    val e164Digits: String,        // digits only, e.g. 8801712345678
    val displayPlus: String,       // +8801712345678
    val prettyInternational: String, // +880 1712-345678
    val nationalFormat: String,    // 01712-345678 (or best effort)
    val countryCode: String,       // 880
    val nationalNumber: String,    // 1712345678
    val countryName: String,       // Bangladesh
    val countryFlag: String,       // emoji flag
    val operator: String,          // Grameenphone / Robi ...
    val numberType: String,        // Mobile / Landline? / Unknown
    val digitCount: Int
)

object PhoneUtils {

    private val countries = listOf(
        "880" to Pair("Bangladesh", "\uD83C\uDDE7\uD83C\uDDE9"),
        "91" to Pair("India", "\uD83C\uDDEE\uD83C\uDDF3"),
        "971" to Pair("UAE", "\uD83C\uDDE6\uD83C\uDDEA"),
        "966" to Pair("Saudi Arabia", "\uD83C\uDDF8\uD83C\uDDE6"),
        "974" to Pair("Qatar", "\uD83C\uDDF6\uD83C\uDDE6"),
        "965" to Pair("Kuwait", "\uD83C\uDDF0\uD83C\uDDFC"),
        "968" to Pair("Oman", "\uD83C\uDDF4\uD83C\uDDF2"),
        "973" to Pair("Bahrain", "\uD83C\uDDE7\uD83C\uDDED"),
        "880" to Pair("Bangladesh", "\uD83C\uDDE7\uD83C\uDDE9"),
        "65" to Pair("Singapore", "\uD83C\uDDF8\uD83C\uDDEC"),
        "60" to Pair("Malaysia", "\uD83C\uDDF2\uD83C\uDDFE"),
        "61" to Pair("Australia", "\uD83C\uDDE6\uD83C\uDDFA"),
        "44" to Pair("United Kingdom", "\uD83C\uDDEC\uD83C\uDDE7"),
        "1" to Pair("USA / Canada", "\uD83C\uDDFA\uD83C\uDDF8"),
        "49" to Pair("Germany", "\uD83C\uDDE9\uD83C\uDDEA"),
        "33" to Pair("France", "\uD83C\uDDEB\uD83C\uDDF7"),
        "39" to Pair("Italy", "\uD83C\uDDEE\uD83C\uDDF9"),
        "81" to Pair("Japan", "\uD83C\uDDEF\uD83C\uDDF5"),
        "82" to Pair("South Korea", "\uD83C\uDDF0\uD83C\uDDF7"),
        "86" to Pair("China", "\uD83C\uDDE8\uD83C\uDDF3"),
        "92" to Pair("Pakistan", "\uD83C\uDDF5\uD83C\uDDF0"),
        "93" to Pair("Afghanistan", "\uD83C\uDDE6\uD83C\uDDEB"),
        "94" to Pair("Sri Lanka", "\uD83C\uDDF1\uD83C\uDDF0"),
        "95" to Pair("Myanmar", "\uD83C\uDDF2\uD83C\uDDF2"),
        "977" to Pair("Nepal", "\uD83C\uDDF3\uD83C\uDDF5"),
        "975" to Pair("Bhutan", "\uD83C\uDDE7\uD83C\uDDF9"),
        "960" to Pair("Maldives", "\uD83C\uDDF2\uD83C\uDDFB")
    ).sortedByDescending { it.first.length }

    /**
     * Normalize any user input: "+880 17XX-XXXXXX", "0088017...", "017XXXXXXXX",
     * "(017) 123-45678", "88017..." all become e164 digits "88017XXXXXXXX".
     */
    fun normalize(rawInput: String): NormalizedNumber {
        val trimmed = rawInput.trim()
        require(trimmed.isNotEmpty()) { "Number likhun" }

        // keep leading + / 00 info, drop everything else except digits
        var digits = trimmed.filter { it.isDigit() }
        val startsPlus = trimmed.startsWith("+")
        val starts00 = trimmed.startsWith("00")

        if (starts00 && digits.length > 2) {
            // 0088017... -> 88017...
            digits = digits.removePrefix("00")
        } else if (startsPlus) {
            // +880... digits already fine
        } else {
            // local formats
            if (digits.startsWith("880") && digits.length in 12..14) {
                // already BD international without +
            } else if (digits.startsWith("01") && digits.length == 11) {
                // BD local mobile 017XXXXXXXX -> 88017XXXXXXXX
                digits = "880" + digits.substring(1)
            } else if (digits.startsWith("0") && digits.length in 8..12) {
                // generic trunk-zero drop: 017... already handled; fallback drop one 0
                // keep as-is for non-BD; only drop for lookup variants below
            }
        }

        require(digits.length in 7..15) {
            "Valid number din (7-15 digit). Pelam ${digits.length} digit."
        }

        // country detection (longest prefix match)
        var cc = ""
        var cname = "Unknown"
        var flag = "\uD83C\uDF0D"
        for ((code, meta) in countries) {
            if (digits.startsWith(code)) {
                // avoid matching "1" for e.g. "170..." unless NANP length
                if (code == "1" && !(digits.length == 11 && digits.startsWith("1"))) continue
                cc = code
                cname = meta.first
                flag = meta.second
                break
            }
        }
        if (cc.isEmpty()) {
            // fallback: first 1-3 digits as code guess
            cc = when {
                digits.length >= 13 -> digits.take(3)
                digits.length >= 11 -> digits.take(2)
                else -> digits.take(1)
            }
        }
        val national = if (digits.startsWith(cc)) digits.removePrefix(cc) else digits

        val operator = detectOperator(cc, national)
        val type = detectType(cc, national)
        val pretty = "+$cc ${groupDigits(national)}"
        val nationalFmt = nationalDisplay(cc, national)

        return NormalizedNumber(
            e164Digits = digits,
            displayPlus = "+$digits",
            prettyInternational = pretty,
            nationalFormat = nationalFmt,
            countryCode = cc,
            nationalNumber = national,
            countryName = cname,
            countryFlag = flag,
            operator = operator,
            numberType = type,
            digitCount = digits.length
        )
    }

    /** Variants to try against API (some servers like no-trunk, some like full). */
    fun lookupVariants(n: NormalizedNumber): List<String> {
        val list = mutableListOf(n.e164Digits)
        // with trunk zero (BD): 017XXXXXXXX
        if (n.countryCode == "880" && n.nationalNumber.length == 10) {
            list.add("0" + n.nationalNumber)
        }
        return list.distinct()
    }

    private fun detectOperator(cc: String, national: String): String {
        if (cc == "880") {
            val p3 = national.take(3)
            return when (p3) {
                "013", "017" -> "Grameenphone"
                "014", "019" -> "Banglalink"
                "015" -> "Teletalk"
                "016", "018" -> "Robi / Airtel"
                else -> if (national.startsWith("01")) "Bangladesh Mobile" else "Unknown"
            }
        }
        if (cc == "91" && national.length == 10) return "India Mobile"
        if (cc == "1" && national.length == 10) return "NANP Carrier"
        return "—"
    }

    private fun detectType(cc: String, national: String): String {
        if (cc == "880") {
            return if (national.startsWith("01") && national.length == 10) "Mobile"
            else "Unknown"
        }
        return when {
            national.length >= 10 -> "Mobile / Unknown"
            national.length <= 8 -> "Landline?"
            else -> "Unknown"
        }
    }

    private fun groupDigits(national: String): String {
        // 1712345678 -> 1712-345678 ; generic 3-3-rest
        return when {
            national.length == 10 -> national.take(4) + "-" + national.drop(4)
            national.length > 6 -> national.take(3) + " " + national.drop(3).chunked(2).joinToString(" ")
            else -> national
        }
    }

    private fun nationalDisplay(cc: String, national: String): String {
        if (cc == "880" && national.length == 10) {
            return "0" + national.take(4) + "-" + national.drop(4)
        }
        return national
    }
}
