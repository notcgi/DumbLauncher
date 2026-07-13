package com.dumblauncher.app.data

import java.util.Locale

/**
 * RU/EN search matching via QWERTY↔ЙЦУКЕН keyboard layout swap and phonetic transliteration.
 */
object SearchTransliteration {

    private val EN_TO_RU_LAYOUT = mapOf(
        'q' to 'й', 'w' to 'ц', 'e' to 'у', 'r' to 'к', 't' to 'е',
        'y' to 'н', 'u' to 'г', 'i' to 'ш', 'o' to 'щ', 'p' to 'з',
        '[' to 'х', ']' to 'ъ',
        'a' to 'ф', 's' to 'ы', 'd' to 'в', 'f' to 'а', 'g' to 'п',
        'h' to 'р', 'j' to 'о', 'k' to 'л', 'l' to 'д', ';' to 'ж',
        '\'' to 'э',
        'z' to 'я', 'x' to 'ч', 'c' to 'с', 'v' to 'м', 'b' to 'и',
        'n' to 'т', 'm' to 'ь', ',' to 'б', '.' to 'ю', '`' to 'ё',
    )

    private val RU_TO_EN_LAYOUT = EN_TO_RU_LAYOUT.entries.associate { (en, ru) -> ru to en }

    /** Longest-first phonetic latin → cyrillic digraphs/trigraphs, then singles. */
    private val EN_TO_RU_TRANSLIT: List<Pair<String, String>> = listOf(
        "shch" to "щ",
        "sch" to "щ",
        "yo" to "ё",
        "yu" to "ю",
        "ya" to "я",
        "ye" to "е",
        "zh" to "ж",
        "kh" to "х",
        "ts" to "ц",
        "ch" to "ч",
        "sh" to "ш",
        "eh" to "э",
        "a" to "а",
        "b" to "б",
        "v" to "в",
        "w" to "в",
        "g" to "г",
        "d" to "д",
        "e" to "е",
        "z" to "з",
        "i" to "и",
        "j" to "й",
        "y" to "й",
        "k" to "к",
        "l" to "л",
        "m" to "м",
        "n" to "н",
        "o" to "о",
        "p" to "п",
        "r" to "р",
        "s" to "с",
        "t" to "т",
        "u" to "у",
        "f" to "ф",
        "h" to "х",
        "c" to "к",
        "x" to "кс",
        "'" to "ь",
        "`" to "ъ",
    )

    private val RU_TO_EN_TRANSLIT = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
        'е' to "e", 'ё' to "yo", 'ж' to "zh", 'з' to "z", 'и' to "i",
        'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
        'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
        'у' to "u", 'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch",
        'ш' to "sh", 'щ' to "shch", 'ъ' to "", 'ы' to "y", 'ь' to "",
        'э' to "e", 'ю' to "yu", 'я' to "ya",
    )

    fun normalize(raw: String): String {
        return raw.lowercase(Locale.ROOT)
            .map { if (it == 'ё') 'е' else it }
            .filter { it.isLetterOrDigit() }
            .joinToString("")
    }

    fun layoutSwapEnToRu(input: String): String =
        buildString(input.length) {
            for (ch in input) append(EN_TO_RU_LAYOUT[ch] ?: ch)
        }

    fun layoutSwapRuToEn(input: String): String =
        buildString(input.length) {
            for (ch in input) append(RU_TO_EN_LAYOUT[ch] ?: ch)
        }

    fun translitEnToRu(input: String): String {
        val out = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            var matched = false
            for ((from, to) in EN_TO_RU_TRANSLIT) {
                if (input.startsWith(from, i)) {
                    out.append(to)
                    i += from.length
                    matched = true
                    break
                }
            }
            if (!matched) {
                out.append(input[i])
                i++
            }
        }
        return out.toString()
    }

    fun translitRuToEn(input: String): String {
        return buildString(input.length * 2) {
            for (ch in input) {
                append(RU_TO_EN_TRANSLIT[ch] ?: ch)
            }
        }
    }

    /** All searchable forms of a label or query (normalized, distinct). */
    fun variants(raw: String): List<String> {
        val base = normalize(raw)
        if (base.isEmpty()) return emptyList()
        return linkedSetOf(
            base,
            normalize(layoutSwapEnToRu(base)),
            normalize(layoutSwapRuToEn(base)),
            normalize(translitEnToRu(base)),
            normalize(translitRuToEn(base)),
        ).filter { it.isNotEmpty() }
    }

    fun matches(label: String, query: String): Boolean {
        val queryVariants = variants(query)
        if (queryVariants.isEmpty()) return true
        val labelVariants = variants(label)
        return queryVariants.any { q ->
            labelVariants.any { labelKey -> labelKey.contains(q) }
        }
    }
}
