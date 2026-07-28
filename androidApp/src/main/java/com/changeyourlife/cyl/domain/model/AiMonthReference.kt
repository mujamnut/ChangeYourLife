package com.changeyourlife.cyl.domain.model

import java.time.Year
import java.util.Locale

data class AiMonthReference(
    val year: Int,
    val month: Int,
) {
    fun matches(other: AiMonthReference): Boolean {
        return year == other.year && month == other.month
    }
}

fun String.toAiMonthReferenceOrNull(
    defaultYear: Int = Year.now().value,
): AiMonthReference? {
    val value = trim().lowercase(Locale.ROOT)
    if (value.isBlank()) return null

    AiYearMonthRegex.find(value)?.let { match ->
        val year = match.groupValues[1].toIntOrNull() ?: return@let
        val month = match.groupValues[2].toIntOrNull() ?: return@let
        return AiMonthReference(year = year, month = month)
    }
    AiNamedMonthNumberRegex.find(value)?.let { match ->
        val month = match.groupValues[1].toIntOrNull() ?: return@let
        val year = match.groupValues[2].toIntOrNull() ?: defaultYear
        return AiMonthReference(year = year, month = month)
    }

    val normalizedWords = value
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .split(Regex("\\s+"))
    val month = normalizedWords
        .firstNotNullOfOrNull { word -> AiMonthNames[word] }
        ?: return null
    val year = normalizedWords
        .firstNotNullOfOrNull { word ->
            word.toIntOrNull()?.takeIf { number -> number in 1900..2999 }
        }
        ?: defaultYear
    return AiMonthReference(year = year, month = month)
}

private val AiYearMonthRegex by lazy {
    Regex("""(?<!\d)(\d{4})[-/.](0?[1-9]|1[0-2])(?:[-/.]\d{1,2})?(?!\d)""")
}

private val AiNamedMonthNumberRegex by lazy {
    Regex(
        """\b(?:bulan|month|bln)\s*(?:ke[-\s]*)?(0?[1-9]|1[0-2])(?:\s*(?:tahun|year)?\s*(\d{4}))?\b""",
        RegexOption.IGNORE_CASE,
    )
}

private val AiMonthNames = mapOf(
    "january" to 1,
    "januari" to 1,
    "jan" to 1,
    "february" to 2,
    "februari" to 2,
    "feb" to 2,
    "march" to 3,
    "maret" to 3,
    "mac" to 3,
    "mar" to 3,
    "april" to 4,
    "apr" to 4,
    "may" to 5,
    "mei" to 5,
    "june" to 6,
    "juni" to 6,
    "jun" to 6,
    "july" to 7,
    "juli" to 7,
    "julai" to 7,
    "jul" to 7,
    "august" to 8,
    "agustus" to 8,
    "ogos" to 8,
    "agu" to 8,
    "aug" to 8,
    "september" to 9,
    "sep" to 9,
    "october" to 10,
    "oktober" to 10,
    "okt" to 10,
    "oct" to 10,
    "november" to 11,
    "nov" to 11,
    "december" to 12,
    "disember" to 12,
    "desember" to 12,
    "dis" to 12,
    "dec" to 12,
)
