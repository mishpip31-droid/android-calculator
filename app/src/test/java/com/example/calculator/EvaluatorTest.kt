package com.example.calculator

import org.junit.Assert.assertEquals
import org.junit.Test

class EvaluatorTest {
    private fun calc(expr: String) = Evaluator.format(Evaluator.evaluate(expr))

    @Test fun basicOperations() {
        assertEquals("5", calc("2+3"))
        assertEquals("-1", calc("2−3"))
        assertEquals("6", calc("2×3"))
        assertEquals("2.5", calc("5÷2"))
    }

    @Test fun precedence() {
        assertEquals("14", calc("2+3×4"))
        assertEquals("20", calc("(2+3)×4"))
        assertEquals("1", calc("8÷4÷2"))
        assertEquals("3", calc("10−4−3"))
    }

    @Test fun decimalsAreExact() {
        assertEquals("0.3", calc("0.1+0.2"))
        assertEquals("0.333333333333333", calc("1÷3"))
        assertEquals("0.666666666666667", calc("2÷3"))
    }

    @Test fun unaryMinusAndPercent() {
        assertEquals("-10", calc("5×−2"))
        assertEquals("2", calc("−−2"))
        assertEquals("0.5", calc("50%"))
        assertEquals("25", calc("50%×50"))
    }

    @Test fun implicitMultiplication() {
        assertEquals("6", calc("2(3)"))
        assertEquals("20", calc("(2+3)(4)"))
    }

    @Test fun formatting() {
        assertEquals("0", calc("0.5−0.5"))
        assertEquals("1E20", calc("10000000000×10000000000"))
        assertEquals("1000000", calc("1000×1000"))
    }

    @Test(expected = CalculationException::class)
    fun divisionByZero() { Evaluator.evaluate("1÷0") }

    @Test(expected = CalculationException::class)
    fun unclosedParen() { Evaluator.evaluate("(1+2") }

    @Test(expected = CalculationException::class)
    fun badNumber() { Evaluator.evaluate("1.2.3") }
}
