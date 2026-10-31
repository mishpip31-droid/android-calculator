package com.example.calculator

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast

class MainActivity : ThemedActivity() {
    private val calculator = Calculator()
    private val history = History()
    private lateinit var historyStore: HistoryStore
    private lateinit var listAdapter: HistoryAdapter
    private lateinit var tileAdapter: HistoryAdapter

    private lateinit var expressionView: TextView
    private lateinit var resultView: TextView
    private lateinit var scroll: HorizontalScrollView
    private lateinit var historyList: ListView
    private lateinit var historyGrid: GridView
    private lateinit var historyEmpty: TextView
    private lateinit var searchBar: View
    private lateinit var searchInput: EditText
    private lateinit var notesOnlyToggle: TextView
    private lateinit var converterButton: View
    private lateinit var keypad: View
    private lateinit var toggleHistory: ImageButton
    private lateinit var clearHistory: ImageButton

    /** Режим «вся история»: клавиатура скрыта, лента занимает весь экран. */
    private var historyExpanded = false

    /** Плитки с учётом поиска и фильтра «только с заметками». */
    private var tileEntries: List<HistoryEntry> = emptyList()
    private var notesOnly = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        expressionView = findViewById(R.id.expression)
        resultView = findViewById(R.id.result)
        scroll = findViewById(R.id.expressionScroll)
        historyList = findViewById(R.id.historyList)
        historyGrid = findViewById(R.id.historyGrid)
        historyEmpty = findViewById(R.id.historyEmpty)
        searchBar = findViewById(R.id.searchBar)
        searchInput = findViewById(R.id.searchInput)
        notesOnlyToggle = findViewById(R.id.btnNotesOnly)
        converterButton = findViewById(R.id.btnConverter)
        keypad = findViewById(R.id.keypad)
        toggleHistory = findViewById(R.id.btnToggleHistory)
        clearHistory = findViewById(R.id.btnClearHistory)

        historyStore = HistoryStore(this)
        history.replaceAll(historyStore.load())
        // Лента под выражением: новые снизу. Плитки на весь экран: новые сверху.
        listAdapter = HistoryAdapter(R.layout.item_history) { history.entries }
        tileAdapter = HistoryAdapter(R.layout.item_history_tile) { tileEntries }
        historyList.adapter = listAdapter
        historyGrid.adapter = tileAdapter

        savedInstanceState?.let {
            calculator.restore(
                it.getString(KEY_EXPRESSION, ""),
                it.getBoolean(KEY_EVALUATED, false),
            )
            historyExpanded = it.getBoolean(KEY_HISTORY_EXPANDED, false)
            notesOnly = it.getBoolean(KEY_NOTES_ONLY, false)
        }

        setupKeypad()
        setupHistory()

        applyHistoryMode()
        render()
        historyList.setSelection(history.size - 1)
        if (savedInstanceState == null) handleInsert(intent)
    }


    /** Возврат из списка покупок: «В калькулятор» вставляет итог в выражение. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleInsert(intent)
    }

    private fun handleInsert(intent: Intent?) {
        val value = intent?.getStringExtra(EXTRA_INSERT_NUMBER) ?: return
        intent.removeExtra(EXTRA_INSERT_NUMBER)
        // «Быстрая сумма» передаёт и запись для истории.
        val expr = intent.getStringExtra(EXTRA_HISTORY_EXPRESSION)
        val result = intent.getStringExtra(EXTRA_HISTORY_RESULT)
        if (expr != null && result != null) {
            intent.removeExtra(EXTRA_HISTORY_EXPRESSION)
            intent.removeExtra(EXTRA_HISTORY_RESULT)
            addToHistory(HistoryEntry(expr, result))
        }
        if (historyExpanded) setHistoryExpanded(false)
        calculator.insertNumber(value.replace('-', '−'))
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_EXPRESSION, calculator.expression)
        outState.putBoolean(KEY_EVALUATED, calculator.justEvaluated)
        outState.putBoolean(KEY_HISTORY_EXPANDED, historyExpanded)
        outState.putBoolean(KEY_NOTES_ONLY, notesOnly)
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

        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) = refreshTiles()
        })
        searchInput.setOnEditorActionListener { _, _, _ ->
            hideKeyboard()
            true
        }
        notesOnlyToggle.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            notesOnly = !notesOnly
            refreshTiles()
        }
        // Прокрутка плиток прячет клавиатуру, чтобы она не закрывала результаты.
        historyGrid.setOnScrollListener(object : android.widget.AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: android.widget.AbsListView, state: Int) {
                if (state == android.widget.AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) hideKeyboard()
            }
            override fun onScroll(view: android.widget.AbsListView, first: Int, visible: Int, total: Int) {}
        })

        findViewById<View>(R.id.btnQuickSum).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            startActivity(Intent(this, QuickSumActivity::class.java))
        }

        findViewById<View>(R.id.btnShopping).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            startActivity(Intent(this, ShoppingListsActivity::class.java))
        }

        findViewById<View>(R.id.btnInfo).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            showMainMenu(it)
        }

        converterButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            openConverter()
        }

        for (view in listOf<AdapterView<*>>(historyList, historyGrid)) {
            // Нажатие — вставить результат в текущее выражение.
            view.setOnItemClickListener { parent, item, position, _ ->
                item.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                val entry = parent.getItemAtPosition(position) as HistoryEntry
                insertFromHistory { calculator.insertNumber(entry.result) }
            }
            // Удержание — меню с остальными действиями.
            view.setOnItemLongClickListener { parent, item, position, _ ->
                item.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                showEntryMenu(item, parent.getItemAtPosition(position) as HistoryEntry)
                true
            }
        }
    }

    private fun showEntryMenu(anchor: View, entry: HistoryEntry) {
        PopupMenu(this, anchor, Gravity.END).apply {
            menu.add(0, MENU_RESULT, 0, R.string.menu_insert_result)
            menu.add(0, MENU_EXPRESSION, 1, R.string.menu_insert_expression)
            menu.add(0, MENU_NOTE, 2, if (entry.note.isEmpty()) R.string.menu_note_add else R.string.menu_note_edit)
            menu.add(0, MENU_COPY, 3, R.string.menu_copy)
            menu.add(0, MENU_DELETE, 4, R.string.menu_delete)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_RESULT -> insertFromHistory { calculator.insertNumber(entry.result) }
                    MENU_EXPRESSION -> insertFromHistory { calculator.insertExpression(entry.expression) }
                    MENU_NOTE -> editNote(entry)
                    MENU_COPY -> copyToClipboard(
                        buildString {
                            if (entry.note.isNotEmpty()) append(entry.note).append(": ")
                            append(entry.expression).append(" = ").append(entry.result)
                        },
                    )
                    MENU_DELETE -> {
                        val index = history.indexOf(entry)
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

    private fun editNote(entry: HistoryEntry) {
        val input = EditText(this).apply {
            setText(entry.note)
            setSelection(text.length)
            hint = getString(R.string.note_hint)
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        fun save(note: String) {
            val index = history.indexOf(entry)
            if (index >= 0) {
                history.setNote(index, note)
                onHistoryChanged()
            }
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.note_title)
            .setMessage("${entry.expression} = ${entry.result}")
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ -> save(input.text.toString()) }
            .setNegativeButton(R.string.cancel, null)
            .apply {
                if (entry.note.isNotEmpty()) setNeutralButton(R.string.note_remove) { _, _ -> save("") }
            }
            .create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        input.setOnEditorActionListener { _, _, _ ->
            save(input.text.toString())
            dialog.dismiss()
            true
        }
        dialog.show()
        input.requestFocus()
    }

    private fun openConverter() {
        val intent = Intent(this, ConverterActivity::class.java)
        calculator.currentValue()?.let {
            intent.putExtra(ConverterActivity.EXTRA_VALUE, it.replace('−', '-'))
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_CONVERTER)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CONVERTER || resultCode != RESULT_OK) return
        val value = data?.getStringExtra(ConverterActivity.EXTRA_VALUE) ?: return
        calculator.insertNumber(value.replace('-', '−'))
        render()
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

    /**
     * «Очистить» удаляет только записи без заметок — заметки хранятся всегда.
     * Удалить вместе с заметками можно отдельной кнопкой с повторным подтверждением.
     */
    private fun confirmClearHistory() {
        val noted = history.notedCount
        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.clear_history_title)
            .setNegativeButton(R.string.cancel, null)
        if (noted == 0) {
            builder.setMessage(R.string.clear_history_message_all)
                .setPositiveButton(R.string.clear) { _, _ ->
                    history.clear()
                    onHistoryChanged()
                }
        } else {
            builder.setMessage(getString(R.string.clear_history_message, noted))
                .setPositiveButton(R.string.clear) { _, _ ->
                    history.clearUnnoted()
                    onHistoryChanged()
                }
                .setNeutralButton(R.string.clear_all) { _, _ -> confirmClearAll(noted) }
        }
        builder.show()
    }

    private fun confirmClearAll(noted: Int) {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_all_title)
            .setMessage(getString(R.string.clear_all_message, noted))
            .setPositiveButton(R.string.clear_all) { _, _ ->
                history.clear()
                onHistoryChanged()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun refreshTiles() {
        tileEntries = history.search(searchInput.text.toString(), notesOnly)
        tileAdapter.notifyDataSetChanged()
        notesOnlyToggle.isSelected = notesOnly
        updateEmptyState()
    }

    private fun updateEmptyState() {
        val showTiles = historyExpanded && tileEntries.isNotEmpty()
        historyGrid.visibility = if (showTiles) View.VISIBLE else View.GONE
        historyEmpty.visibility = if (historyExpanded && !showTiles) View.VISIBLE else View.GONE
        historyEmpty.setText(
            when {
                history.isEmpty() -> R.string.history_empty
                searchInput.text.isNotBlank() -> R.string.search_nothing
                notesOnly -> R.string.notes_empty
                else -> R.string.search_nothing
            },
        )
    }

    private fun hideKeyboard() {
        searchInput.clearFocus()
        getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            .hideSoftInputFromWindow(searchInput.windowToken, 0)
    }

    /** Меню ⋮: калькулятор поверх окон, оформление, инструкция. */
    private fun showMainMenu(anchor: View) {
        PopupMenu(this, anchor, Gravity.END).apply {
            menu.add(0, 1, 0, if (FloatingCalculatorService.running) R.string.menu_floating_off else R.string.menu_floating_on)
            menu.add(0, 2, 1, R.string.menu_appearance)
            menu.add(0, 3, 2, R.string.menu_about)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> toggleFloating()
                    2 -> ThemeManager.showPicker(this@MainActivity)
                    3 -> startActivity(Intent(this@MainActivity, AboutActivity::class.java))
                }
                true
            }
            show()
        }
    }

    private var floatingPermissionAsked = false

    private fun toggleFloating() {
        if (FloatingCalculatorService.running) {
            FloatingCalculatorService.stop(this)
            return
        }
        if (FloatingCalculatorService.start(this)) {
            Toast.makeText(this, R.string.floating_started, Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.floating_permission_title)
            .setMessage(R.string.floating_permission_message)
            .setPositiveButton(R.string.floating_permission_go) { _, _ ->
                floatingPermissionAsked = true
                startActivity(FloatingCalculatorService.permissionIntent(this))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        // Плавающее окно могло добавить записи в историю.
        history.replaceAll(historyStore.load())
        listAdapter.notifyDataSetChanged()
        applyHistoryMode()
        // Вернулись из настроек с разрешением — сразу открываем окно.
        if (floatingPermissionAsked) {
            floatingPermissionAsked = false
            if (FloatingCalculatorService.start(this)) {
                Toast.makeText(this, R.string.floating_started, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun addToHistory(entry: HistoryEntry) {
        history.replaceAll(historyStore.load())
        if (history.add(entry)) {
            onHistoryChanged()
            historyList.post { historyList.setSelection(history.size - 1) }
        }
    }

    private fun onHistoryChanged() {
        historyStore.save(history.entries)
        listAdapter.notifyDataSetChanged()
        applyHistoryMode()
    }

    private fun setHistoryExpanded(expanded: Boolean) {
        historyExpanded = expanded
        if (!expanded) {
            hideKeyboard()
            searchInput.setText("")
        }
        applyHistoryMode()
        if (expanded) historyGrid.setSelection(0) else historyList.post { historyList.setSelection(history.size - 1) }
    }

    private fun applyHistoryMode() {
        keypad.visibility = if (historyExpanded) View.GONE else View.VISIBLE
        val empty = history.isEmpty()
        historyList.visibility = if (historyExpanded) View.GONE else View.VISIBLE
        searchBar.visibility = if (historyExpanded && !empty) View.VISIBLE else View.INVISIBLE
        converterButton.visibility = if (historyExpanded) View.GONE else View.VISIBLE
        findViewById<View>(R.id.btnInfo).visibility = if (historyExpanded) View.GONE else View.VISIBLE
        findViewById<View>(R.id.btnShopping).visibility = if (historyExpanded) View.GONE else View.VISIBLE
        findViewById<View>(R.id.btnQuickSum).visibility = if (historyExpanded) View.GONE else View.VISIBLE
        refreshTiles()
        clearHistory.visibility = if (historyExpanded && !empty) View.VISIBLE else View.GONE
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
            resultView.setTextColor(themeColor(R.attr.c_error))
        } else {
            resultView.text = calculator.preview()?.let { "= $it" } ?: ""
            resultView.setTextColor(themeColor(R.attr.c_text_secondary))
        }
        scroll.post { scroll.fullScroll(View.FOCUS_RIGHT) }
    }

    private companion object {
        const val KEY_EXPRESSION = "expression"
        const val KEY_EVALUATED = "evaluated"
        const val KEY_HISTORY_EXPANDED = "history_expanded"
        const val KEY_NOTES_ONLY = "notes_only"

        const val MENU_RESULT = 1
        const val MENU_EXPRESSION = 2
        const val MENU_COPY = 3
        const val MENU_DELETE = 4
        const val MENU_NOTE = 5

        const val REQUEST_CONVERTER = 1
    }
}
