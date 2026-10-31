package com.example.calculator

/**
 * Состояние ввода калькулятора. Не зависит от Android, поэтому легко тестируется.
 */
class Calculator {
    var expression: String = ""
        private set

    /** true, если на экране результат последнего «=»; следующая цифра начнёт новое выражение. */
    var justEvaluated: Boolean = false
        private set

    var error: String? = null
        private set

    private val last: Char? get() = expression.lastOrNull()

    fun inputDigit(d: Char) {
        require(d.isDigit())
        startFresh()
        if (last == ')' || last == '%') expression += '×'
        // Не допускаем ведущих нулей: «0» + «5» → «5».
        if (currentNumber() == "0") expression = expression.dropLast(1)
        expression += d
    }

    fun inputDot() {
        startFresh()
        val number = currentNumber()
        when {
            number.contains('.') -> return
            last == ')' || last == '%' -> expression += "×0."
            number.isEmpty() -> expression += "0."
            else -> expression += '.'
        }
    }

    fun inputOperator(op: Char) {
        require(op in OPERATORS)
        error = null
        justEvaluated = false
        val l = last
        when {
            l == null || l == '(' -> if (op == '−') expression += op
            l in OPERATORS -> {
                if (op == '−' && (l == '×' || l == '÷')) {
                    expression += op // 5 × −2
                } else {
                    // Заменяем последний оператор (и, возможно, унарный минус перед ним).
                    expression = expression.dropLast(1)
                    while (expression.lastOrNull() in OPERATORS) expression = expression.dropLast(1)
                    if (expression.isEmpty() || expression.last() == '(') {
                        if (op == '−') expression += op
                    } else {
                        expression += op
                    }
                }
            }
            l == '.' -> expression = expression.dropLast(1) + op
            else -> expression += op
        }
    }

    fun inputPercent() {
        error = null
        justEvaluated = false
        val l = last ?: return
        if (l.isDigit() || l == ')') expression += '%'
    }

    fun inputOpenParen() {
        startFresh()
        val l = last
        if (l != null && (l.isDigit() || l == ')' || l == '%' || l == '.')) {
            if (l == '.') expression = expression.dropLast(1)
            expression += '×'
        }
        expression += '('
    }

    fun inputCloseParen() {
        error = null
        justEvaluated = false
        val l = last ?: return
        if (openParens() > 0 && (l.isDigit() || l == ')' || l == '%')) expression += ')'
    }

    /** Умная скобка: открывает или закрывает в зависимости от контекста. */
    fun inputParen() {
        val l = last
        val canClose = openParens() > 0 && l != null && (l.isDigit() || l == ')' || l == '%')
        if (canClose && !justEvaluated) inputCloseParen() else inputOpenParen()
    }

    fun backspace() {
        error = null
        justEvaluated = false
        expression = expression.dropLast(1)
    }

    fun clear() {
        expression = ""
        error = null
        justEvaluated = false
    }

    /** Выполняет «=». */
    fun evaluate() {
        if (expression.isEmpty()) return
        try {
            val value = Evaluator.evaluate(completed())
            expression = Evaluator.format(value)
            justEvaluated = true
            error = null
        } catch (e: CalculationException) {
            error = e.message
        } catch (e: ArithmeticException) {
            error = "Ошибка"
        }
    }

    /** Предварительный результат, который показывается под выражением, или null. */
    fun preview(): String? {
        if (justEvaluated) return null
        val expr = completed()
        // Показываем только если есть хоть одна операция, иначе это просто число.
        if (expr.none { it in OPERATORS || it == '%' } && !expr.contains('(')) return null
        return try {
            Evaluator.format(Evaluator.evaluate(expr))
        } catch (e: Exception) {
            null
        }
    }

    /** Восстановление состояния (например, после поворота экрана). */
    fun restore(expression: String, justEvaluated: Boolean) {
        this.expression = expression
        this.justEvaluated = justEvaluated
        error = null
    }

    private fun startFresh() {
        error = null
        if (justEvaluated) {
            expression = ""
            justEvaluated = false
        }
    }

    /** Выражение с отброшенными висящими операторами и закрытыми скобками. */
    private fun completed(): String {
        var e = expression
        while (e.isNotEmpty() && (e.last() in OPERATORS || e.last() == '(' || e.last() == '.')) {
            e = e.dropLast(1)
        }
        var open = 0
        for (c in e) when (c) {
            '(' -> open++
            ')' -> open--
        }
        return e + ")".repeat(open.coerceAtLeast(0))
    }

    private fun currentNumber(): String =
        expression.takeLastWhile { it.isDigit() || it == '.' }

    private fun openParens(): Int = expression.count { it == '(' } - expression.count { it == ')' }

    companion object {
        val OPERATORS = setOf('+', '−', '×', '÷')
    }
}
