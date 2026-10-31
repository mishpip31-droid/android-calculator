package com.example.calculator

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val calculator = Calculator()
    private val history = History()
    private lateinit var historyStore: HistoryStore
    private lateinit var historyAdapter: HistoryAdapter

    private lateinit var expressionView: TextView
    private lateinit var resultView: TextView
    private lateinit var scroll: HorizontalScrollView
    private lateinit var historyList: ListView
    private lateinit var historyEmpty: TextView
    private lateinit var historyHint: TextView
    private lateinit var keypad: View
    private lateinit var toggleHistory: ImageButton
    private lateinit var clearHistory: ImageButton

    /** Режим «вся история»: клавиатура скрыта, лента занимает весь экран. */
    private var historyExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        expressionView = findViewById(R.id.expression)
        resultView = findViewById(R.id.result)
        scroll = findViewById(R.id.expressionScroll)
        historyList = findViewById(R.id.historyList)
        historyEmpty = findViewById(R.id.historyEmpty)
        historyHint = findViewById(R.id.historyHint)
        keypad = findViewById(R.id.keypad)
        toggleHistory = findViewById(R.id.btnToggleHistory)
        clearHistory = findViewById(R.id.btnClearHistory)

        historyStore = HistoryStore(this)
        history.replaceAll(historyStore.load())
        historyAdapter = HistoryAdapter(history)
        historyList.adapter = historyAdapter

        savedInstanceState?.let {
            calculator.restore(
                it.getString(KEY_EXPRESSION, ""),
                it.getBoolean(KEY_EVALUATED, false),
            )
            historyExpanded = it.getBoolean(KEY_HISTORY_EXPANDED, false)
        }

        setupKeypad()
        setupHistory()

        applyHistoryMode()
        render()
        historyList.setSelection(history.size - 1)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_EXPRESSION, calculator.expression)
        outState.putBoolean(KEY_EVALUATED, calculator.justEvaluated)
        outState.putBoolean(KEY_HISTORY_EXPANDED, historyExpanded)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (historyExpanded) setHistoryExpanded(false) else super.onBackPressed()
    }

    private fun setupKeypad() {
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
        bind(R.id.btnEquals) {
            calculator.evaluate()?.let { addToHistory(it) }
        }

        findViewById<View>(R.id.btnBackspace).setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            calculator.clear()
            render()
            true
        }
    }

    private fun setupHistory() {
        toggleHistory.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            setHistoryExpanded(!historyExpanded)
        }
        clearHistory.setOnClickListener { confirmClearHistory() }

        // Нажатие — вставить результат в текущее выражение.
        historyList.setOnItemClickListener { _, view, position, _ ->
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            calculator.insertNumber(history.entries[position].result)
            if (historyExpanded) setHistoryExpanded(false)
            render()
        }
        // Удержание — меню с остальными действиями.
        historyList.setOnItemLongClickListener { _, view, position, _ ->
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            showEntryMenu(view, position)
            true
        }
    }

    private fun showEntryMenu(anchor: View, position: Int) {
        val entry = history.entries.getOrNull(position) ?: return
        PopupMenu(this, anchor, Gravity.END).apply {
            menu.add(0, MENU_RESULT, 0, R.string.menu_insert_result)
            menu.add(0, MENU_EXPRESSION, 1, R.string.menu_insert_expression)
            menu.add(0, MENU_COPY, 2, R.string.menu_copy)
            menu.add(0, MENU_DELETE, 3, R.string.menu_delete)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_RESULT -> insertFromHistory { calculator.insertNumber(entry.result) }
                    MENU_EXPRESSION -> insertFromHistory { calculator.insertExpression(entry.expression) }
                    MENU_COPY -> copyToClipboard("${entry.expression} = ${entry.result}")
                    MENU_DELETE -> {
                        val index = history.entries.indexOf(entry)
                        if (index >= 0) {
                            history.removeAt(index)
                            onHistoryChanged()
                        }
                    }
                }
                true
            }
            show()
        }
    }

    private fun insertFromHistory(action: () -> Unit) {
        action()
        if (historyExpanded) setHistoryExpanded(false)
        render()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), text))
        // Начиная с Android 13 система сама показывает уведомление о копировании.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmClearHistory() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_history_title)
            .setMessage(R.string.clear_history_message)
            .setPositiveButton(R.string.clear) { _, _ ->
                history.clear()
                onHistoryChanged()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun addToHistory(entry: HistoryEntry) {
        if (history.add(entry)) {
            onHistoryChanged()
            historyList.post { historyList.setSelection(history.size - 1) }
        }
    }

    private fun onHistoryChanged() {
        historyStore.save(history.entries)
        historyAdapter.notifyDataSetChanged()
        applyHistoryMode()
    }

    private fun setHistoryExpanded(expanded: Boolean) {
        historyExpanded = expanded
        applyHistoryMode()
        if (expanded) historyList.post { historyList.setSelection(history.size - 1) }
    }

    private fun applyHistoryMode() {
        keypad.visibility = if (historyExpanded) View.GONE else View.VISIBLE
        val empty = history.isEmpty()
        // В свёрнутом виде пустую ленту просто не показываем, в развёрнутом — подсказка.
        historyEmpty.visibility = if (historyExpanded && empty) View.VISIBLE else View.GONE
        historyHint.visibility = if (historyExpanded && !empty) View.VISIBLE else View.INVISIBLE
        clearHistory.visibility = if (historyExpanded && !empty) View.VISIBLE else View.INVISIBLE
        toggleHistory.setImageResource(if (historyExpanded) R.drawable.ic_keypad else R.drawable.ic_history)
        toggleHistory.contentDescription = getString(
            if (historyExpanded) R.string.desc_keypad else R.string.desc_history,
        )
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
        const val KEY_HISTORY_EXPANDED = "history_expanded"

        const val MENU_RESULT = 1
        const val MENU_EXPRESSION = 2
        const val MENU_COPY = 3
        const val MENU_DELETE = 4
    }
}
