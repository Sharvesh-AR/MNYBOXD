// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.spendly.core.money

/**
 * Money is always handled as a [Long] number of paise (1 rupee = 100 paise).
 * Floating point is never used for stored or calculated amounts.
 */
object Money {
    /** Largest single amount accepted: ₹99,99,99,999.99 (just under ₹100 crore). */
    const val MAX_PAISE: Long = 99_999_999_999L
    const val MAX_INTEGER_DIGITS = 9
    private const val MINUS = "−"

    /**
     * Parses a user-typed rupee string such as "120", "120.5", "₹1,20,000.75" into paise.
     * Returns null for empty, negative, malformed, over-precise (> 2 decimals) or out-of-range input.
     */
    fun parseRupees(input: String): Long? {
        val cleaned = input.trim().removePrefix("₹").replace(",", "").replace(" ", "")
        if (cleaned.isEmpty()) return null
        if (!Regex("""^\d+(\.\d{0,2})?$""").matches(cleaned)) return null
        val parts = cleaned.split('.')
        val intPart = parts[0].trimStart('0').ifEmpty { "0" }
        if (intPart.length > MAX_INTEGER_DIGITS) return null
        val fracPart = parts.getOrNull(1).orEmpty().padEnd(2, '0')
        val paise = intPart.toLong() * 100 + fracPart.toLong()
        return paise.takeIf { it <= MAX_PAISE }
    }

    /** True when [paise] is a valid positive transaction amount. */
    fun isValidAmount(paise: Long?): Boolean = paise != null && paise in 1..MAX_PAISE

    /**
     * Formats paise in Indian notation: 1234567 paise → "₹12,345.67", 100000000 → "₹10,00,000".
     * Paise are shown only when non-zero unless [alwaysShowPaise] is set.
     */
    fun format(paise: Long, symbol: Boolean = true, alwaysShowPaise: Boolean = false, signed: Boolean = false): String {
        val negative = paise < 0
        // Long.MIN_VALUE cannot be negated; it is far outside any valid balance, clamp defensively.
        val abs = if (paise == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(paise)
        val rupees = abs / 100
        val fraction = abs % 100
        val sb = StringBuilder()
        if (negative) sb.append(MINUS) else if (signed && paise > 0) sb.append('+')
        if (symbol) sb.append('₹')
        sb.append(groupIndian(rupees.toString()))
        if (fraction != 0L || alwaysShowPaise) sb.append('.').append(fraction.toString().padStart(2, '0'))
        return sb.toString()
    }

    /** Plain machine-readable form for exports: "-1234.50". Always two decimals, ASCII minus. */
    fun toPlainString(paise: Long): String {
        val negative = paise < 0
        val abs = kotlin.math.abs(paise)
        return (if (negative) "-" else "") + (abs / 100) + "." + (abs % 100).toString().padStart(2, '0')
    }

    /** Short label for chart axes: ₹950, ₹1.2K, ₹12K, ₹1.2L, ₹3.4Cr. */
    fun formatCompact(paise: Long): String {
        val negative = paise < 0
        val rupees = kotlin.math.abs(paise) / 100
        val body = when {
            rupees >= 1_00_00_000 -> scaled(rupees, 1_00_00_000) + "Cr"
            rupees >= 1_00_000 -> scaled(rupees, 1_00_000) + "L"
            rupees >= 1_000 -> scaled(rupees, 1_000) + "K"
            else -> rupees.toString()
        }
        return (if (negative) MINUS else "") + "₹" + body
    }

    /** One decimal place using integer arithmetic only (truncated). */
    private fun scaled(value: Long, unit: Long): String {
        val tenths = value * 10 / unit
        val whole = tenths / 10
        val dec = tenths % 10
        return if (whole >= 100 || dec == 0L) whole.toString() else "$whole.$dec"
    }

    internal fun groupIndian(digits: String): String {
        if (digits.length <= 3) return digits
        val last3 = digits.takeLast(3)
        val rest = digits.dropLast(3)
        val groups = ArrayList<String>()
        var i = rest.length
        while (i > 0) {
            val start = maxOf(0, i - 2)
            groups.add(0, rest.substring(start, i))
            i = start
        }
        return groups.joinToString(",") + "," + last3
    }
}

/**
 * Immutable keypad buffer for the Quick Add amount display.
 * Enforces at most [Money.MAX_INTEGER_DIGITS] integer digits and 2 decimals at entry time.
 */
data class AmountInput(val text: String = "") {
    val paise: Long? get() = if (text.isEmpty()) null else Money.parseRupees(text)
    val isValid: Boolean get() = Money.isValidAmount(paise)

    fun appendDigit(d: Char): AmountInput {
        require(d in '0'..'9')
        val dot = text.indexOf('.')
        if (dot >= 0) {
            if (text.length - dot - 1 >= 2) return this
            return copy(text = text + d)
        }
        if (text == "0") return copy(text = d.toString())
        if (text.length >= Money.MAX_INTEGER_DIGITS) return this
        return copy(text = text + d)
    }

    fun appendDecimal(): AmountInput = when {
        text.contains('.') -> this
        text.isEmpty() -> copy(text = "0.")
        else -> copy(text = "$text.")
    }

    fun backspace(): AmountInput = copy(text = text.dropLast(1))

    fun clear(): AmountInput = AmountInput()

    /** Text for display with Indian grouping applied to the integer part. */
    fun display(): String {
        if (text.isEmpty()) return "0"
        val dot = text.indexOf('.')
        val intPart = if (dot >= 0) text.substring(0, dot) else text
        val grouped = Money.groupIndian(intPart.ifEmpty { "0" })
        return if (dot >= 0) grouped + text.substring(dot) else grouped
    }

    companion object {
        fun fromPaise(paise: Long?): AmountInput {
            if (paise == null || paise <= 0) return AmountInput()
            val r = paise / 100
            val p = paise % 100
            return AmountInput(
                when {
                    p == 0L -> r.toString()
                    p % 10 == 0L -> "$r.${p / 10}"
                    else -> "$r.${p.toString().padStart(2, '0')}"
                },
            )
        }
    }
}
