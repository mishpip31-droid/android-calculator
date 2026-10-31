package com.example.calculator

import java.math.BigDecimal
import java.math.RoundingMode

/** Строка ленты «Быстрой суммы». excluded — временно не учитывается в итоге. */
data class SumLine(
    val id: Long,
    val label: String,
    val amount: BigDecimal,
    val excluded: Boolean = false,
)

/** Лента сложения: разбор ввода, итог, текст для «Поделиться». Не зависит от Android. */
object QuickSum {
    private const val EXPR = """[-−+]?\s*[\d(][\d\s.,+\-−×xх*/÷()%]*"""

    // «хлеб 1.9», «молоко 2×2.35», «−5», «1 234,50»
    private val TAIL = Regex("""^(.*?)\s*($EXPR)$""")

    // «5.40 молоко»
    private val HEAD = Regex("""^($EXPR?)\s+(\D.*)$""")

    /** Возвращает (подпись, сумма) или null, если числа нет либо выражение ошибочно. */
    fun parse(input: String): Pair<String, BigDecimal>? {
        val text = input.trim()
        if (text.isEmpty()) return null
        TAIL.find(text)?.let { m ->
            evaluate(m.groupValues[2])?.let { return cleanLabel(m.groupValues[1]) to it }
        }
        HEAD.find(text)?.let { m ->
            evaluate(m.groupValues[1])?.let { return cleanLabel(m.groupValues[2]) to it }
        }
        return null
    }

    private fun evaluate(expr: String): BigDecimal? {
        val e = expr.replace(" ", "").replace('x', '*').replace('х', '*').trimEnd('+', '-', '−', '*', '/', '×', '÷')
        if (e.isEmpty() || e.none { it.isDigit() }) return null
        return try {
            Evaluator.evaluate(e)
        } catch (ex: Exception) {
            null
        }
    }

    private fun cleanLabel(s: String) = s.trim().trimEnd(':', '—', '–', '-', '=').trim()

    fun total(lines: List<SumLine>): BigDecimal =
        lines.filter { !it.excluded }.fold(BigDecimal.ZERO) { acc, l -> acc.add(l.amount) }

    /** Деньги — с двумя знаками («1.90»), дробные с большей точностью — как есть. */
    fun format(v: BigDecimal): String {
        val s = if (v.stripTrailingZeros().scale() <= 2) {
            v.setScale(2, RoundingMode.HALF_UP).toPlainString()
        } else {
            Evaluator.format(v)
        }
        return s.replace('-', '−')
    }

    /** Выражение для истории: «1.90 + 4.70 − 5.00» (длинные ленты сокращаются). */
    fun historyExpression(lines: List<SumLine>, maxTerms: Int = 10): String {
        val used = lines.filter { !it.excluded }
        val terms = used.take(maxTerms).mapIndexed { i, l ->
            val v = format(l.amount.abs())
            when {
                i == 0 -> format(l.amount)
                l.amount.signum() < 0 -> "− $v"
                else -> "+ $v"
            }
        }
        val more = if (used.size > maxTerms) " + … (${used.size})" else ""
        return terms.joinToString(" ") + more
    }

    fun shareText(lines: List<SumLine>): String = buildString {
        append("Σ Быстрая сумма\n")
        lines.forEachIndexed { i, l ->
            append(i + 1).append(". ")
            if (l.label.isNotEmpty()) append(l.label).append(" — ")
            append(format(l.amount))
            if (l.excluded) append(" (не учтено)")
            append('\n')
        }
        append("Итого: ").append(format(total(lines)))
    }
}
