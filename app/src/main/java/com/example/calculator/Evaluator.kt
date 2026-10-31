package com.example.calculator

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

class CalculationException(message: String) : Exception(message)

/**
 * Вычисляет арифметическое выражение методом рекурсивного спуска.
 *
 * Поддерживается: + − × ÷ (также + - * /), скобки, унарный минус,
 * постфиксный процент (50% = 0.5), неявное умножение: 2(3) = 6,
 * и экспоненциальная запись: 1.5E20.
 */
object Evaluator {
    private val MC = MathContext(34, RoundingMode.HALF_EVEN)

    fun evaluate(expression: String): BigDecimal = Parser(normalize(expression)).parse()

    /** Форматирует результат для показа на экране. */
    fun format(value: BigDecimal): String {
        val rounded = value.round(MathContext(15, RoundingMode.HALF_EVEN))
        if (rounded.signum() == 0) return "0"
        val stripped = rounded.stripTrailingZeros()
        val abs = stripped.abs()
        return if (abs >= BigDecimal("1e15") || abs < BigDecimal("1e-9")) {
            toScientific(stripped)
        } else {
            stripped.toPlainString()
        }
    }

    private fun toScientific(v: BigDecimal): String {
        val exponent = v.precision() - v.scale() - 1
        val mantissa = v.movePointLeft(exponent).round(MathContext(10)).stripTrailingZeros()
        return "${mantissa.toPlainString()}E$exponent"
    }

    private fun normalize(s: String) = s
        .replace('×', '*')
        .replace('÷', '/')
        .replace('−', '-')
        .replace(',', '.')
        .filterNot { it.isWhitespace() }

    private class Parser(private val s: String) {
        private var pos = 0

        fun parse(): BigDecimal {
            if (s.isEmpty()) throw CalculationException("Пустое выражение")
            val result = expression()
            if (pos != s.length) throw CalculationException("Неожиданный символ '${s[pos]}'")
            return result
        }

        private fun peek(): Char? = s.getOrNull(pos)

        // expression := term (('+' | '-') term)*
        private fun expression(): BigDecimal {
            var value = term()
            while (true) {
                value = when (peek()) {
                    '+' -> { pos++; value.add(term(), MC) }
                    '-' -> { pos++; value.subtract(term(), MC) }
                    else -> return value
                }
            }
        }

        // term := unary (('*' | '/') unary | implicit-multiplication)*
        private fun term(): BigDecimal {
            var value = unary()
            while (true) {
                val c = peek()
                value = when {
                    c == '*' -> { pos++; value.multiply(unary(), MC) }
                    c == '/' -> {
                        pos++
                        val divisor = unary()
                        if (divisor.signum() == 0) throw CalculationException("Деление на ноль")
                        value.divide(divisor, MC)
                    }
                    c == '(' || (c != null && (c.isDigit() || c == '.')) -> value.multiply(postfix(), MC)
                    else -> return value
                }
            }
        }

        // unary := ('+' | '-') unary | postfix
        private fun unary(): BigDecimal = when (peek()) {
            '-' -> { pos++; unary().negate() }
            '+' -> { pos++; unary() }
            else -> postfix()
        }

        // postfix := primary '%'*
        private fun postfix(): BigDecimal {
            var value = primary()
            while (peek() == '%') {
                pos++
                value = value.divide(BigDecimal(100), MC)
            }
            return value
        }

        // primary := number | '(' expression ')'
        private fun primary(): BigDecimal {
            val c = peek() ?: throw CalculationException("Неполное выражение")
            if (c == '(') {
                pos++
                val value = expression()
                if (peek() != ')') throw CalculationException("Не закрыта скобка")
                pos++
                return value
            }
            if (c.isDigit() || c == '.') {
                val start = pos
                while (peek()?.let { it.isDigit() || it == '.' } == true) pos++
                val mantissa = s.substring(start, pos)
                if (mantissa == "." || mantissa.count { it == '.' } > 1) {
                    throw CalculationException("Неверное число '$mantissa'")
                }
                // Экспонента, например 1.5E-20 (так показываются очень большие/малые результаты).
                if (peek() == 'E' || peek() == 'e') {
                    pos++
                    if (peek() == '-' || peek() == '+') pos++
                    val digitsStart = pos
                    while (peek()?.isDigit() == true) pos++
                    if (pos == digitsStart) throw CalculationException("Неверное число")
                }
                return BigDecimal(s.substring(start, pos))
            }
            throw CalculationException("Неожиданный символ '$c'")
        }
    }
}
