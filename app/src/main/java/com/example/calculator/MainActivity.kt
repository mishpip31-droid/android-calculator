package com.example.calculator

import android.app.Activity
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private val calculator = Calculator()
    private lateinit var expressionView: TextView
    private lateinit var resultView: TextView
    private lateinit var scroll: HorizontalScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        expressionView = findViewById(R.id.expression)
        resultView = findViewById(R.id.result)
        scroll = findViewById(R.id.expressionScroll)

        savedInstanceState?.let {
            calculator.restore(
                it.getString(KEY_EXPRESSION, ""),
                it.getBoolean(KEY_EVALUATED, false),
            )
        }

        val digits = mapOf(
            R.id.btn0 to '0', R.id.btn1 to '1', R.id.btn2 to '2', R.id.btn3 to '3',
            R.id.btn4 to '4', R.id.btn5 to '5', R.id.btn6 to '6', R.id.btn7 to '7',
            R.id.btn8 to '8', R.id.btn9 to '9',
        )
        digits.forEach { (id, d) -> bind(id) { calculator.inputDigit(d) } }

        bind(R.id.btnPlus) { calculator.inputOperator('+') }
        bind(R.id.btnMinus) { calculator.inputOperator('−') }
        bind(R.id.btnMultiply) { calculator.inputOperator('×') }
        bind(R.id.btnDivide) { calculator.inputOperator('÷') }
        bind(R.id.btnPercent) { calculator.inputPercent() }
        bind(R.id.btnDot) { calculator.inputDot() }
        bind(R.id.btnParen) { calculator.inputParen() }
        bind(R.id.btnClear) { calculator.clear() }
        bind(R.id.btnBackspace) { calculator.backspace() }
        bind(R.id.btnEquals) { calculator.evaluate() }

        findViewById<View>(R.id.btnBackspace).setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            calculator.clear()
            render()
            true
        }

        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_EXPRESSION, calculator.expression)
        outState.putBoolean(KEY_EVALUATED, calculator.justEvaluated)
    }

    private fun bind(id: Int, action: () -> Unit) {
        findViewById<View>(id).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            action()
            render()
        }
    }

    private fun render() {
        expressionView.text = calculator.expression.ifEmpty { "0" }
        val error = calculator.error
        if (error != null) {
            resultView.text = error
            resultView.setTextColor(getColor(R.color.error))
        } else {
            resultView.text = calculator.preview()?.let { "= $it" } ?: ""
            resultView.setTextColor(getColor(R.color.text_secondary))
        }
        scroll.post { scroll.fullScroll(View.FOCUS_RIGHT) }
    }

    private companion object {
        const val KEY_EXPRESSION = "expression"
        const val KEY_EVALUATED = "evaluated"
    }
}
