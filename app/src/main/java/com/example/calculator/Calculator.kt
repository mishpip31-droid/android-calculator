package com.example.calculator

/**
 * Состояние ввода калькулятора. Не зависит от Android, поэтому легко тестируется.
 * Минус в выражении всегда хранится как «−» (U+2212).
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
            number.contains('.') || number.contains('E') -> return
            last == ')' || last == '%' -> expression += "×0."
            number.isEmpty() -> expression += "0."
            else -> expression += '.'
        }
    }

    fun inputOperator(op: Char) {
        require(op in OPERATORS)
        resetFlags()
        if (last == '.' || last == 'E') expression = expression.dropLast(1)
        val l = last
        when {
            l == null || l == '(' -> if (op == '−') expression += op
            l in OPERATORS -> {
                if (op == '−' && (l == '×' || l == '÷')) {
                    expression += op // 5 × −2
                } else {
                    // Заменяем последний оператор (и, возможно, унарный минус перед ним).
                    while (expression.lastOrNull() in OPERATORS) expression = expression.dropLast(1)
                    if (expression.isEmpty() || expression.last() == '(') {
                        if (op == '−') expression += op
                    } else {
                        expression += op
                    }
                }
            }
            else -> expression += op
        }
    }

    fun inputPercent() {
        resetFlags()
        val l = last ?: return
        if (l.isDigit() || l == ')') expression += '%'
    }

    fun inputOpenParen() {
        startFresh()
        if (last == '.' || last == 'E') expression = expression.dropLast(1)
        val l = last
        if (l != null && (l.isDigit() || l == ')' || l == '%')) expression += '×'
        expression += '('
    }

    fun inputCloseParen() {
        resetFlags()
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
        resetFlags()
        expression = expression.dropLast(1)
    }

    fun clear() {
        expression = ""
        resetFlags()
    }

    /**
     * Выполняет «=». Возвращает запись для истории, если было что вычислять
     * (просто число без операций в историю не попадает).
     */
    fun evaluate(): HistoryEntry? {
        if (expression.isEmpty()) return null
        val expr = completed()
        return try {
            val result = displayResult(expr)
            expression = result
            justEvaluated = true
            error = null
            if (expr != result && hasOperation(expr)) HistoryEntry(expr, result) else null
        } catch (e: CalculationException) {
            error = e.message
            null
        } catch (e: ArithmeticException) {
            error = "Ошибка"
            null
        }
    }

    /** Предварительный результат, который показывается под выражением, или null. */
    fun preview(): String? {
        if (justEvaluated) return null
        val expr = completed()
        if (!hasOperation(expr)) return null
        return try {
            displayResult(expr)
        } catch (e: Exception) {
            null
        }
    }

    /** Текущее значение (результат или вычисленное выражение) или null, если вычислить нельзя. */
    fun currentValue(): String? {
        if (error != null) return null
        val expr = completed()
        if (expr.isEmpty()) return null
        return try {
            displayResult(expr)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Вставляет число (например, результат из истории).
     * Если пользователь как раз набирает число, оно заменяется вставленным.
     */
    fun insertNumber(value: String) = insert(value, compound = value.startsWith('−'))

    /** Вставляет целое выражение из истории; в середине ввода оно берётся в скобки. */
    fun insertExpression(expr: String) =
        insert(expr, compound = expr.any { it in OPERATORS || it == '%' })

    private fun insert(value: String, compound: Boolean) {
        if (value.isEmpty()) return
        error = null
        if (justEvaluated) expression = ""
        justEvaluated = false
        val number = currentNumber()
        expression = expression.dropLast(number.length)
        if (last == '.' || last == 'E') expression = expression.dropLast(1)
        val l = last
        if (l == ')' || l == '%') expression += '×'
        val inContext = expression.isNotEmpty() && expression.last() != '('
        expression += if (compound && inContext) "($value)" else value
    }

    /** Восстановление состояния (например, после поворота экрана). */
    fun restore(expression: String, justEvaluated: Boolean) {
        this.expression = expression
        this.justEvaluated = justEvaluated
        error = null
    }

    private fun resetFlags() {
        error = null
        justEvaluated = false
    }

    private fun startFresh() {
        error = null
        if (justEvaluated) {
            expression = ""
            justEvaluated = false
        }
    }

    private fun displayResult(expr: String): String =
        Evaluator.format(Evaluator.evaluate(expr)).replace('-', '−')

    private fun hasOperation(expr: String) =
        expr.any { it in OPERATORS || it == '%' || it == '(' }

    /** Выражение с отброшенными висящими операторами и закрытыми скобками. */
    private fun completed(): String {
        var e = expression
        while (e.isNotEmpty() && (e.last() in OPERATORS || e.last() in "(.E")) {
            e = e.dropLast(1)
        }
        var open = 0
        for (c in e) when (c) {
            '(' -> open++
            ')' -> open--
        }
        return e + ")".repeat(open.coerceAtLeast(0))
    }

    /** Число в конце выражения, включая экспоненту: «12.5», «1E−20». */
    private fun currentNumber(): String =
        NUMBER_AT_END.find(expression)?.value ?: ""

    private fun openParens(): Int = expression.count { it == '(' } - expression.count { it == ')' }

    companion object {
        val OPERATORS = setOf('+', '−', '×', '÷')
        private val NUMBER_AT_END = Regex("[0-9.]+(E−?[0-9]*)?$")
    }
}
