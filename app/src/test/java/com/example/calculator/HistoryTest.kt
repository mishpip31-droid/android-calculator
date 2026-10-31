package com.example.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryTest {
    @Test fun addsAndSkipsConsecutiveDuplicates() {
        val h = History()
        assertTrue(h.add(HistoryEntry("2+2", "4", 1)))
        assertFalse(h.add(HistoryEntry("2+2", "4", 2)))
        assertTrue(h.add(HistoryEntry("3+3", "6", 3)))
        assertTrue(h.add(HistoryEntry("2+2", "4", 4)))
        assertEquals(3, h.size)
    }

    @Test fun keepsOnlyNewestWhenFull() {
        val h = History(maxSize = 3)
        for (i in 1..5) h.add(HistoryEntry("$i+0", "$i", i.toLong()))
        assertEquals(listOf("3", "4", "5"), h.entries.map { it.result })
    }

    @Test fun removeAndClear() {
        val h = History()
        h.add(HistoryEntry("1+1", "2"))
        h.add(HistoryEntry("2+2", "4"))
        h.removeAt(0)
        assertEquals("4", h.entries.single().result)
        h.clear()
        assertTrue(h.isEmpty())
    }

    @Test fun evaluateProducesEntry() {
        val c = Calculator()
        "12+30".forEach { if (it.isDigit()) c.inputDigit(it) else c.inputOperator(it) }
        val entry = c.evaluate()!!
        assertEquals("12+30", entry.expression)
        assertEquals("42", entry.result)
    }

    @Test fun plainNumberIsNotRecorded() {
        val c = Calculator()
        c.inputDigit('7')
        assertNull(c.evaluate())
    }

    @Test fun autoClosedParensAreRecorded() {
        val c = Calculator()
        c.inputDigit('2'); c.inputOperator('×'); c.inputOpenParen()
        c.inputDigit('3'); c.inputOperator('+'); c.inputDigit('4')
        assertEquals("2×(3+4)", c.evaluate()!!.expression)
    }

    @Test fun insertNumberStartsNewAfterResult() {
        val c = Calculator()
        c.inputDigit('1'); c.inputOperator('+'); c.inputDigit('1'); c.evaluate()
        c.insertNumber("42")
        assertEquals("42", c.expression)
        assertFalse(c.justEvaluated)
    }

    @Test fun insertNumberAfterOperatorAppends() {
        val c = Calculator()
        c.inputDigit('5'); c.inputOperator('+')
        c.insertNumber("42")
        assertEquals("5+42", c.expression)
        assertEquals("47", c.preview())
    }

    @Test fun insertNumberReplacesNumberBeingTyped() {
        val c = Calculator()
        c.inputDigit('5'); c.inputOperator('+'); c.inputDigit('9'); c.inputDigit('9')
        c.insertNumber("42")
        assertEquals("5+42", c.expression)
    }

    @Test fun insertNegativeNumberIsWrapped() {
        val c = Calculator()
        c.inputDigit('3'); c.inputOperator('−')
        c.insertNumber("−5")
        assertEquals("3−(−5)", c.expression)
        assertEquals("8", c.preview())
    }

    @Test fun insertAfterParenMultiplies() {
        val c = Calculator()
        c.inputOpenParen(); c.inputDigit('2'); c.inputCloseParen()
        c.insertNumber("3")
        assertEquals("(2)×3", c.expression)
    }

    @Test fun insertExpressionWrappedInContext() {
        val c = Calculator()
        c.inputDigit('2'); c.inputOperator('×')
        c.insertExpression("3+4")
        assertEquals("2×(3+4)", c.expression)
        assertEquals("14", c.preview())
    }

    @Test fun insertExpressionIntoEmptyIsRaw() {
        val c = Calculator()
        c.insertExpression("3+4")
        assertEquals("3+4", c.expression)
    }

    @Test fun scientificResultsCanBeReused() {
        val c = Calculator()
        c.insertNumber("1E−20")
        c.inputOperator('×'); c.inputDigit('2')
        assertEquals("2E-20", c.preview()!!.replace('−', '-'))
        c.inputDot() // точка после экспоненты игнорируется не ломая выражение
        assertEquals("1E−20×2.", c.expression)
    }

    @Test fun negativeResultUsesUnicodeMinus() {
        val c = Calculator()
        c.inputDigit('2'); c.inputOperator('−'); c.inputDigit('5')
        assertEquals("−3", c.evaluate()!!.result)
        c.inputOperator('+'); c.inputDigit('1')
        assertEquals("−2", c.preview())
    }
}

class HistoryNoteTest {
    @Test fun setAndClearNote() {
        val h = History()
        h.add(HistoryEntry("4500×3", "13500", 1))
        val updated = h.setNote(0, "  аренда ")
        assertEquals("аренда", updated.note)
        assertEquals(0, h.indexOf(updated))
        assertEquals("", h.setNote(0, "").note)
    }

    @Test fun indexOfUsesIdentity() {
        val h = History()
        val a = HistoryEntry("1+1", "2", 5)
        h.add(a)
        assertEquals(-1, h.indexOf(a.copy()))
        assertEquals(0, h.indexOf(a))
    }

    @Test fun currentValue() {
        val c = Calculator()
        assertNull(c.currentValue())
        c.inputDigit('2'); c.inputOperator('×'); c.inputDigit('3'); c.inputOperator('+')
        assertEquals("6", c.currentValue())
    }
}
