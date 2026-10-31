package com.example.calculator

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickSumTest {
    private fun p(s: String) = QuickSum.parse(s)?.let { it.first to QuickSum.format(it.second) }

    @Test fun plainNumbers() {
        assertEquals("" to "1.90", p("1.9"))
        assertEquals("" to "1234.50", p("1 234,5"))
        assertEquals("" to "−5.00", p("-5"))
        assertEquals("" to "−5.00", p("−5"))
        assertEquals("" to "7.05", p("2.35*3"))
    }

    @Test fun labelBeforeNumber() {
        assertEquals("хлеб" to "1.90", p("хлеб 1.9"))
        assertEquals("молоко" to "4.70", p("молоко 2×2.35"))
        assertEquals("молоко" to "4.70", p("молоко 2 x 2,35"))
        assertEquals("хлеб 2 шт" to "3.80", p("хлеб 2 шт 3.8"))
        assertEquals("скидка" to "−5.00", p("скидка: -5"))
        assertEquals("Сыр" to "12.50", p("Сыр — 12.5"))
    }

    @Test fun labelAfterNumber() {
        assertEquals("молоко" to "5.40", p("5.40 молоко"))
        assertEquals("такси до дома" to "12.00", p("12 такси до дома"))
    }

    @Test fun rejectsNoNumber() {
        assertNull(p(""))
        assertNull(p("просто текст"))
        assertNull(p("1.2.3"))
    }

    @Test fun trailingOperatorIgnored() {
        assertEquals("" to "15.00", p("15+"))
    }

    private fun line(id: Long, v: String, label: String = "", ex: Boolean = false) =
        SumLine(id, label, BigDecimal(v), ex)

    @Test fun totalSkipsExcluded() {
        val lines = listOf(line(1, "1.9"), line(2, "4.70"), line(3, "-5"), line(4, "100", ex = true))
        assertEquals("1.60", QuickSum.format(QuickSum.total(lines)))
        assertEquals("0.00", QuickSum.format(QuickSum.total(emptyList())))
    }

    @Test fun historyExpression() {
        val lines = listOf(line(1, "1.9"), line(2, "4.7"), line(3, "-5"), line(4, "9", ex = true))
        assertEquals("1.90 + 4.70 − 5.00", QuickSum.historyExpression(lines))
        val many = (1L..12L).map { line(it, "1") }
        assertEquals(true, QuickSum.historyExpression(many, maxTerms = 3).endsWith("+ … (12)"))
    }

    @Test fun shareText() {
        val lines = listOf(line(1, "1.9", "хлеб"), line(2, "4.7"), line(3, "3", ex = true))
        assertEquals(
            "Σ Быстрая сумма\n1. хлеб — 1.90\n2. 4.70\n3. 3.00 (не учтено)\nИтого: 6.60",
            QuickSum.shareText(lines),
        )
    }

    @Test fun formatKeepsPrecision() {
        assertEquals("0.125", QuickSum.format(BigDecimal("0.125")))
        assertEquals("3.00", QuickSum.format(BigDecimal("3")))
    }
}
