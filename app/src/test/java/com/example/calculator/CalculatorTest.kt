package com.example.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorTest {
    private fun Calculator.type(keys: String): Calculator {
        for (k in keys) when (k) {
            in '0'..'9' -> inputDigit(k)
            '.' -> inputDot()
            '%' -> inputPercent()
            '(' -> inputOpenParen()
            ')' -> inputCloseParen()
            '<' -> backspace()
            '=' -> evaluate()
            in Calculator.OPERATORS -> inputOperator(k)
            else -> error("unknown key $k")
        }
        return this
    }

    @Test fun simpleEvaluation() {
        val c = Calculator().type("12+30=")
        assertEquals("42", c.expression)
    }

    @Test fun operatorReplacesPrevious() {
        assertEquals("5×", Calculator().type("5+×").expression)
        assertEquals("5×−", Calculator().type("5×−").expression)
        assertEquals("5+", Calculator().type("5×−+").expression)
    }

    @Test fun onlyMinusAtStart() {
        assertEquals("", Calculator().type("×").expression)
        assertEquals("−", Calculator().type("−").expression)
        assertEquals("", Calculator().type("−+").expression)
    }

    @Test fun dotHandling() {
        assertEquals("0.", Calculator().type(".").expression)
        assertEquals("1.5", Calculator().type("1.5.").expression)
        assertEquals("2+0.", Calculator().type("2+.").expression)
    }

    @Test fun noLeadingZeros() {
        assertEquals("5", Calculator().type("005").expression)
        assertEquals("0.05", Calculator().type("0.05").expression)
    }

    @Test fun digitAfterResultStartsNew() {
        assertEquals("7", Calculator().type("2+2=7").expression)
    }

    @Test fun operatorAfterResultContinues() {
        assertEquals("4+1", Calculator().type("2+2=+1").expression)
    }

    @Test fun parenthesesAutoClose() {
        val c = Calculator().type("2×(3+4")
        assertEquals("14", c.preview())
        c.evaluate()
        assertEquals("14", c.expression)
    }

    @Test fun closeParenOnlyWhenOpen() {
        assertEquals("5", Calculator().type("5)").expression)
    }

    @Test fun implicitMultiplyBeforeParen() {
        assertEquals("2×(", Calculator().type("2(").expression)
    }

    @Test fun smartParen() {
        val c = Calculator()
        c.inputParen(); c.inputDigit('3'); c.inputParen()
        assertEquals("(3)", c.expression)
    }

    @Test fun previewIgnoresTrailingOperator() {
        assertEquals("5", Calculator().type("2+3×").preview())
        assertNull(Calculator().type("23").preview())
    }

    @Test fun divisionByZeroShowsError() {
        val c = Calculator().type("1÷0=")
        assertNotNull(c.error)
        assertEquals("1÷0", c.expression)
        c.backspace()
        assertNull(c.error)
    }

    @Test fun backspaceAndClear() {
        val c = Calculator().type("123<")
        assertEquals("12", c.expression)
        c.clear()
        assertEquals("", c.expression)
    }
}
