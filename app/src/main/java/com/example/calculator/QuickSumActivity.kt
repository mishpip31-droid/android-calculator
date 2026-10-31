package com.example.calculator

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.math.BigDecimal
import org.json.JSONArray
import org.json.JSONObject

/** Передаётся в MainActivity вместе с числом: запись для истории. */
const val EXTRA_HISTORY_EXPRESSION = "history_expression"
const val EXTRA_HISTORY_RESULT = "history_result"

/**
 * «Быстрая сумма» — лента сложения: числа вводятся подряд, итог сразу сверху.
 * Нажатие — не учитывать строку, свайп — удалить (с «Отменить»), удержание — изменить.
 */
class QuickSumActivity : ThemedActivity() {
    private val lines = ArrayList<SumLine>()

    private lateinit var total: TextView
    private lateinit var info: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var empty: TextView
    private lateinit var input: EditText
    private lateinit var keyboardMode: TextView
    private lateinit var undoBar: View
    private lateinit var undoText: TextView

    private var numericKeyboard = true
    private var undoSnapshot: List<SumLine>? = null
    private val handler = Handler(Looper.getMainLooper())
    private val hideUndo = Runnable {
        undoBar.visibility = View.GONE
        undoSnapshot = null
    }
    private val adapter = LinesAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quick_sum)

        total = findViewById(R.id.sumTotal)
        info = findViewById(R.id.sumInfo)
        recycler = findViewById(R.id.sumLines)
        empty = findViewById(R.id.sumEmpty)
        input = findViewById(R.id.sumInput)
        keyboardMode = findViewById(R.id.btnKeyboardMode)
        undoBar = findViewById(R.id.undoBar)
        undoText = findViewById(R.id.undoText)

        recycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        recycler.adapter = adapter
        ItemTouchHelper(SwipeToDelete()).attachToRecyclerView(recycler)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnShare).setOnClickListener { share() }
        findViewById<View>(R.id.btnClearAll).setOnClickListener { clearAll() }
        findViewById<View>(R.id.btnSumToCalc).setOnClickListener { toCalculator() }
        findViewById<View>(R.id.btnAddLine).setOnClickListener { addFromInput() }
        findViewById<View>(R.id.btnUndo).setOnClickListener { undo() }
        keyboardMode.setOnClickListener { setNumericKeyboard(!numericKeyboard, showKeyboard = true) }

        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_NULL) {
                addFromInput()
                true // клавиатура остаётся — можно вводить следующее число
            } else {
                false
            }
        }

        load()
        numericKeyboard = prefs().getBoolean(KEY_NUMERIC, true)
        setNumericKeyboard(numericKeyboard, showKeyboard = false)
        render()
        input.requestFocus()
    }

    override fun onPause() {
        super.onPause()
        save()
    }

    // ---------- Действия ----------

    private fun addFromInput() {
        val text = input.text.toString()
        if (text.isBlank()) return
        val parsed = QuickSum.parse(text)
        if (parsed == null) {
            Toast.makeText(this, R.string.quick_sum_bad_input, Toast.LENGTH_SHORT).show()
            return
        }
        lines += SumLine(nextId(), parsed.first, parsed.second)
        input.setText("")
        input.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        changed()
        recycler.post { recycler.scrollToPosition(lines.size - 1) }
    }

    private fun toggle(position: Int) {
        val line = lines.getOrNull(position) ?: return
        lines[position] = line.copy(excluded = !line.excluded)
        changed()
    }

    private fun delete(position: Int) {
        val line = lines.getOrNull(position) ?: return
        val before = ArrayList(lines)
        lines.removeAt(position)
        changed()
        val name = line.label.ifEmpty { QuickSum.format(line.amount) }
        offerUndo(before, getString(R.string.quick_sum_deleted, name))
    }

    private fun clearAll() {
        if (lines.isEmpty()) return
        val before = ArrayList(lines)
        lines.clear()
        changed()
        offerUndo(before, getString(R.string.quick_sum_cleared))
    }

    private fun offerUndo(snapshot: List<SumLine>, message: String) {
        undoSnapshot = snapshot
        undoText.text = message
        undoBar.visibility = View.VISIBLE
        handler.removeCallbacks(hideUndo)
        handler.postDelayed(hideUndo, 5000)
    }

    private fun undo() {
        val snapshot = undoSnapshot ?: return
        lines.clear()
        lines.addAll(snapshot)
        handler.removeCallbacks(hideUndo)
        hideUndo.run()
        changed()
    }

    private fun edit(position: Int) {
        val line = lines.getOrNull(position) ?: return
        val pad = (20 * resources.displayMetrics.density).toInt()
        val label = EditText(this).apply {
            setText(line.label)
            hint = getString(R.string.quick_sum_label_hint)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val amount = EditText(this).apply {
            setText(QuickSum.format(line.amount).replace('−', '-'))
            hint = getString(R.string.quick_sum_amount_hint)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or
                InputType.TYPE_NUMBER_FLAG_SIGNED
            keyListener = DigitsKeyListener.getInstance(DIGITS)
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(label)
            addView(amount)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.quick_sum_edit)
            .setView(box)
            .setPositiveButton(R.string.save) { _, _ ->
                val value = QuickSum.parse(amount.text.toString())?.second
                if (value == null) {
                    Toast.makeText(this, R.string.quick_sum_bad_input, Toast.LENGTH_SHORT).show()
                } else {
                    lines[position] = line.copy(label = label.text.toString().trim(), amount = value)
                    changed()
                }
            }
            .setNeutralButton(R.string.delete) { _, _ -> delete(position) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun share() {
        if (lines.isEmpty()) return
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, QuickSum.shareText(lines))
        startActivity(Intent.createChooser(send, getString(R.string.quick_sum_title)))
    }

    /** Итог — в калькулятор, а сама лента — в историю как одно вычисление. */
    private fun toCalculator() {
        val sum = QuickSum.total(lines)
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_INSERT_NUMBER, Evaluator.format(sum))
        if (lines.count { !it.excluded } > 1) {
            intent.putExtra(EXTRA_HISTORY_EXPRESSION, QuickSum.historyExpression(lines))
                .putExtra(EXTRA_HISTORY_RESULT, Evaluator.format(sum).replace('-', '−'))
        }
        save()
        startActivity(intent)
    }

    private fun setNumericKeyboard(numeric: Boolean, showKeyboard: Boolean) {
        numericKeyboard = numeric
        if (numeric) {
            input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or
                InputType.TYPE_NUMBER_FLAG_SIGNED
            input.keyListener = DigitsKeyListener.getInstance(DIGITS)
        } else {
            input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        input.setSelection(input.text.length)
        // На кнопке — режим, на который переключит нажатие.
        keyboardMode.text = if (numeric) "абв" else "123"
        prefs().edit().putBoolean(KEY_NUMERIC, numeric).apply()
        if (showKeyboard) {
            input.requestFocus()
            val imm = getSystemService(InputMethodManager::class.java)
            imm.restartInput(input)
            imm.showSoftInput(input, 0)
        }
    }

    // ---------- Отрисовка и хранение ----------

    private fun changed() {
        save()
        render()
    }

    private fun render() {
        total.text = QuickSum.format(QuickSum.total(lines))
        val excluded = lines.count { it.excluded }
        info.text = if (excluded == 0) getString(R.string.quick_sum_info, lines.size)
        else getString(R.string.quick_sum_info_excluded, lines.size - excluded, excluded)
        empty.visibility = if (lines.isEmpty()) View.VISIBLE else View.GONE
        @Suppress("NotifyDataSetChanged")
        adapter.notifyDataSetChanged()
    }

    private fun prefs() = getSharedPreferences("quick_sum", MODE_PRIVATE)

    private fun load() {
        lines.clear()
        val raw = prefs().getString(KEY_LINES, null) ?: return
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                lines += SumLine(o.getLong("id"), o.optString("l"), BigDecimal(o.getString("a")), o.optBoolean("x"))
            }
        } catch (e: Exception) {
            lines.clear()
        }
    }

    private fun save() {
        val arr = JSONArray()
        for (l in lines) {
            val o = JSONObject().put("id", l.id).put("a", l.amount.toPlainString())
            if (l.label.isNotEmpty()) o.put("l", l.label)
            if (l.excluded) o.put("x", true)
            arr.put(o)
        }
        prefs().edit().putString(KEY_LINES, arr.toString()).apply()
    }

    private fun nextId() = (lines.maxOfOrNull { it.id } ?: 0) + 1

    private inner class LinesAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = lines.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_quick_sum_line, parent, false)
            return object : RecyclerView.ViewHolder(view) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val line = lines[position]
            val v = holder.itemView
            v.findViewById<TextView>(R.id.lineIndex).text = "${position + 1}."
            v.findViewById<TextView>(R.id.lineLabel).text = line.label
            val amount = v.findViewById<TextView>(R.id.lineAmount)
            amount.text = QuickSum.format(line.amount)
            amount.setTextColor(
                themeColor(if (line.amount.signum() < 0) R.attr.c_danger else R.attr.c_text_primary),
            )
            amount.paintFlags = if (line.excluded) amount.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            else amount.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            v.alpha = if (line.excluded) 0.45f else 1f
            v.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                toggle(holder.bindingAdapterPosition)
            }
            v.setOnLongClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                edit(holder.bindingAdapterPosition)
                true
            }
        }
    }

    private inner class SwipeToDelete : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
        private val bgPaint = Paint().apply { color = themeColor(R.attr.c_danger) }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 16 * resources.displayMetrics.density
            isFakeBoldText = true
        }

        override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            delete(viewHolder.bindingAdapterPosition)
        }

        override fun onChildDraw(
            c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
            dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean,
        ) {
            val v = viewHolder.itemView
            if (dX != 0f) {
                val rect = if (dX > 0) RectF(v.left.toFloat(), v.top.toFloat(), v.left + dX, v.bottom.toFloat())
                else RectF(v.right + dX, v.top.toFloat(), v.right.toFloat(), v.bottom.toFloat())
                c.drawRect(rect, bgPaint)
                val label = getString(R.string.swipe_delete)
                val w = textPaint.measureText(label)
                val margin = 24 * resources.displayMetrics.density
                val y = v.top + v.height / 2f - (textPaint.descent() + textPaint.ascent()) / 2
                c.drawText(label, if (dX > 0) v.left + margin else v.right - margin - w, y, textPaint)
            }
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
    }

    private companion object {
        const val KEY_LINES = "lines"
        const val KEY_NUMERIC = "numeric_keyboard"
        const val DIGITS = "0123456789.,-+*/×−() "
    }
}
