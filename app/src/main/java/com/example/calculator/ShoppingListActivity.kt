package com.example.calculator

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Передаётся в MainActivity: число для вставки в калькулятор (минус — ASCII «-»). */
const val EXTRA_INSERT_NUMBER = "insert_number"

/**
 * Один список покупок: добавление строкой ввода, нажатие — вычеркнуть,
 * свайп — удалить (с «Отменить»), удержание — изменить количество и цену.
 */
class ShoppingListActivity : ThemedActivity() {
    private lateinit var book: ShoppingBook
    private var listId = 0L

    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var empty: TextView
    private lateinit var input: AutoCompleteTextView
    private lateinit var totalsBar: View
    private lateinit var totalText: TextView
    private lateinit var spentText: TextView
    private lateinit var undoBar: View
    private lateinit var undoText: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var undoSnapshot: ShoppingList? = null
    private val hideUndo = Runnable {
        undoBar.visibility = View.GONE
        undoSnapshot = null
    }

    private var rows: List<Row> = emptyList()
    private val adapter = ItemsAdapter()

    private sealed class Row {
        data class Item(val item: ShoppingItem) : Row()
        data class Header(val count: Int) : Row()
    }

    private val list: ShoppingList? get() = book.get(listId)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shopping_list)
        book = ShoppingRepository.book(this)
        listId = intent.getLongExtra(EXTRA_LIST_ID, 0)
        if (list == null) {
            finish()
            return
        }

        title = findViewById(R.id.listTitle)
        subtitle = findViewById(R.id.listSubtitle)
        recycler = findViewById(R.id.itemsList)
        empty = findViewById(R.id.itemsEmpty)
        input = findViewById(R.id.itemInput)
        totalsBar = findViewById(R.id.totalsBar)
        totalText = findViewById(R.id.totalText)
        spentText = findViewById(R.id.spentText)
        undoBar = findViewById(R.id.undoBar)
        undoText = findViewById(R.id.undoText)

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        ItemTouchHelper(SwipeToDelete()).attachToRecyclerView(recycler)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.titleBox).setOnClickListener { rename() }
        findViewById<View>(R.id.btnShare).setOnClickListener { list?.let { ShoppingListsActivity.shareList(this, it) } }
        findViewById<View>(R.id.btnMore).setOnClickListener { showMenu(it) }
        findViewById<View>(R.id.btnAddItem).setOnClickListener { addFromInput() }
        findViewById<View>(R.id.btnUndo).setOnClickListener { undo() }
        findViewById<View>(R.id.btnTotalToCalc).setOnClickListener { totalToCalculator() }

        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_NULL) {
                addFromInput()
                true // клавиатура остаётся — можно вводить следующий товар
            } else {
                false
            }
        }
        updateSuggestions()
        render()
    }

    override fun onPause() {
        super.onPause()
        ShoppingRepository.save(this)
    }

    // ---------- Действия ----------

    private fun addFromInput() {
        val text = input.text.toString()
        if (text.isBlank()) return
        book.addItem(listId, text) ?: return
        input.setText("")
        changed()
        updateSuggestions()
        recycler.post { recycler.scrollToPosition(maxOf(0, list!!.active.size - 1)) }
    }

    private fun toggle(item: ShoppingItem, view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        book.toggle(listId, item.id)
        changed()
    }

    private fun delete(item: ShoppingItem) {
        val before = list ?: return
        book.removeItem(listId, item.id)
        changed()
        offerUndo(before, getString(R.string.shopping_deleted_item, item.name))
    }

    private fun clearBought() {
        val before = list ?: return
        if (before.doneCount == 0) return
        book.clearBought(listId)
        changed()
        offerUndo(before, getString(R.string.shopping_cleared_bought))
    }

    private fun uncheckAll() {
        val before = list ?: return
        if (before.doneCount == 0) return
        book.uncheckAll(listId)
        changed()
        offerUndo(before, getString(R.string.shopping_unchecked))
    }

    private fun offerUndo(snapshot: ShoppingList, message: String) {
        undoSnapshot = snapshot
        undoText.text = message
        undoBar.visibility = View.VISIBLE
        handler.removeCallbacks(hideUndo)
        handler.postDelayed(hideUndo, 5000)
    }

    private fun undo() {
        val snapshot = undoSnapshot ?: return
        book.put(snapshot)
        hideUndo.run()
        handler.removeCallbacks(hideUndo)
        changed()
    }

    private fun rename() {
        val current = list ?: return
        ShoppingListsActivity.askName(this, getString(R.string.shopping_rename), current.name) {
            book.rename(listId, it)
            changed()
        }
    }

    private fun showMenu(anchor: View) {
        PopupMenu(this, anchor, Gravity.END).apply {
            menu.add(0, 1, 0, R.string.shopping_uncheck_all)
            menu.add(0, 2, 1, R.string.shopping_clear_bought)
            menu.add(0, 3, 2, R.string.shopping_rename)
            menu.add(0, 4, 3, R.string.shopping_delete_list)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> uncheckAll()
                    2 -> clearBought()
                    3 -> rename()
                    4 -> confirmDeleteList()
                }
                true
            }
            show()
        }
    }

    private fun confirmDeleteList() {
        val current = list ?: return
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.shopping_delete_list_confirm, current.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                book.delete(listId)
                ShoppingRepository.save(this)
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Удержание на товаре: название, количество, единица, цена. */
    private fun editItem(item: ShoppingItem) {
        val pad = (20 * resources.displayMetrics.density).toInt()
        fun field(hintRes: Int, value: String, numeric: Boolean) = EditText(this).apply {
            hint = getString(hintRes)
            setText(value)
            setSingleLine(true)
            inputType = if (numeric) {
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            }
        }
        val name = field(R.string.shopping_field_name, item.name, false)
        val qty = field(R.string.shopping_field_qty, item.quantity?.let(Shopping::formatQuantity).orEmpty(), true)
        val unit = field(R.string.shopping_field_unit, item.unit, false).apply {
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val price = field(R.string.shopping_field_price, item.price?.let(Shopping::formatMoney).orEmpty(), true)
        qty.keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789.,")
        price.keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789.,")

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(qty, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(unit, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(name)
            addView(row)
            addView(price)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.shopping_edit_item)
            .setView(box)
            .setPositiveButton(R.string.save) { _, _ ->
                val newName = name.text.toString().trim().ifEmpty { item.name }
                book.updateItem(
                    listId,
                    item.copy(
                        name = newName,
                        quantity = Money.parse(qty.text.toString())?.takeIf { it.signum() > 0 },
                        unit = unit.text.toString().trim(),
                        price = Money.parse(price.text.toString())?.takeIf { it.signum() >= 0 },
                    ),
                )
                changed()
                updateSuggestions()
            }
            .setNeutralButton(R.string.delete) { _, _ -> delete(item) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** «В калькулятор»: итог списка вставляется в выражение на главном экране. */
    private fun totalToCalculator() {
        val current = list ?: return
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_INSERT_NUMBER, Shopping.formatMoney(current.total))
        ShoppingRepository.save(this)
        startActivity(intent)
    }

    // ---------- Отрисовка ----------

    private fun changed() {
        ShoppingRepository.save(this)
        render()
    }

    private fun updateSuggestions() {
        input.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, book.suggestions()))
    }

    private fun render() {
        val current = list ?: return
        title.text = current.name
        subtitle.text = if (current.items.isEmpty()) "" else
            getString(R.string.shopping_progress, current.doneCount, current.items.size)

        val newRows = ArrayList<Row>()
        current.active.forEach { newRows += Row.Item(it) }
        if (current.bought.isNotEmpty()) {
            newRows += Row.Header(current.bought.size)
            current.bought.forEach { newRows += Row.Item(it) }
        }
        rows = newRows
        @Suppress("NotifyDataSetChanged")
        adapter.notifyDataSetChanged()
        empty.visibility = if (current.items.isEmpty()) View.VISIBLE else View.GONE

        totalsBar.visibility = if (current.hasPrices) View.VISIBLE else View.GONE
        totalText.text = getString(R.string.shopping_total, Shopping.formatMoney(current.total))
        spentText.text = getString(
            R.string.shopping_spent_left,
            Shopping.formatMoney(current.spent),
            Shopping.formatMoney(current.left),
        )
    }

    private inner class ItemsAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = rows.size
        override fun getItemViewType(position: Int) = if (rows[position] is Row.Header) 1 else 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val layout = if (viewType == 1) R.layout.item_shopping_header else R.layout.item_shopping_item
            val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
            return object : RecyclerView.ViewHolder(view) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val v = holder.itemView
            when (val row = rows[position]) {
                is Row.Header -> {
                    v.findViewById<TextView>(R.id.headerText).text =
                        getString(R.string.shopping_bought_header, row.count)
                    v.findViewById<View>(R.id.headerAction).setOnClickListener { clearBought() }
                }
                is Row.Item -> {
                    val item = row.item
                    val name = v.findViewById<TextView>(R.id.itemName)
                    val details = v.findViewById<TextView>(R.id.itemDetails)
                    name.text = item.name
                    name.paintFlags = if (item.done) {
                        name.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    } else {
                        name.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    }
                    name.setTextColor(themeColor(if (item.done) R.attr.c_text_secondary else R.attr.c_text_primary))
                    val d = item.details()
                    details.text = d
                    details.visibility = if (d.isEmpty()) View.GONE else View.VISIBLE
                    v.findViewById<ImageView>(R.id.itemCheck).setImageResource(
                        if (item.done) R.drawable.ic_check_circle else R.drawable.ic_circle_outline,
                    )
                    v.setOnClickListener { toggle(item, it) }
                    v.setOnLongClickListener {
                        it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        editItem(item)
                        true
                    }
                }
            }
        }
    }

    /** Свайп влево или вправо — удалить товар (с красным фоном «Удалить»). */
    private inner class SwipeToDelete : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
        private val bgPaint = Paint().apply { color = themeColor(R.attr.c_danger) }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 16 * resources.displayMetrics.scaledDensity
            isFakeBoldText = true
        }

        override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
            val pos = viewHolder.bindingAdapterPosition
            return if (pos == RecyclerView.NO_POSITION || rows.getOrNull(pos) !is Row.Item) 0
            else super.getSwipeDirs(recyclerView, viewHolder)
        }

        override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val row = rows.getOrNull(viewHolder.bindingAdapterPosition) as? Row.Item ?: return render()
            delete(row.item)
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
                val x = if (dX > 0) v.left + margin else v.right - margin - w
                c.drawText(label, x, y, textPaint)
            }
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
    }

    companion object {
        const val EXTRA_LIST_ID = "list_id"
    }
}
