package com.example.calculator

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView

/** Все списки покупок плитками. */
class ShoppingListsActivity : Activity() {
    private lateinit var book: ShoppingBook
    private lateinit var grid: GridView
    private lateinit var empty: TextView
    private var lists: List<ShoppingList> = emptyList()

    private val adapter = object : BaseAdapter() {
        override fun getCount() = lists.size
        override fun getItem(position: Int) = lists[position]
        override fun getItemId(position: Int) = lists[position].id
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(this@ShoppingListsActivity)
                .inflate(R.layout.item_shopping_list_tile, parent, false)
            val list = lists[position]
            view.findViewById<TextView>(R.id.listName).text = list.name
            view.findViewById<TextView>(R.id.listPreview).text =
                list.active.take(4).joinToString(", ") { it.name }
            val total = list.items.size
            view.findViewById<TextView>(R.id.listProgressText).text =
                getString(R.string.shopping_progress, list.doneCount, total)
            view.findViewById<ProgressBar>(R.id.listProgress).progress =
                if (total == 0) 0 else list.doneCount * 100 / total
            view.findViewById<TextView>(R.id.listTotal).apply {
                visibility = if (list.hasPrices) View.VISIBLE else View.GONE
                text = getString(R.string.shopping_total, Shopping.formatMoney(list.total))
            }
            return view
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shopping_lists)
        book = ShoppingRepository.book(this)
        grid = findViewById(R.id.listsGrid)
        empty = findViewById(R.id.listsEmpty)
        grid.adapter = adapter

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnNewList).setOnClickListener {
            askName(this, getString(R.string.shopping_list_name), "") { name ->
                val list = book.create(name.ifBlank { defaultName() })
                ShoppingRepository.save(this)
                open(list.id)
            }
        }
        grid.setOnItemClickListener { _, _, position, _ -> open(lists[position].id) }
        grid.setOnItemLongClickListener { _, view, position, _ ->
            showMenu(view, lists[position])
            true
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        lists = book.lists
        adapter.notifyDataSetChanged()
        empty.visibility = if (lists.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun open(id: Long) {
        startActivity(Intent(this, ShoppingListActivity::class.java).putExtra(ShoppingListActivity.EXTRA_LIST_ID, id))
    }

    private fun defaultName() = "${getString(R.string.shopping_default_name)} ${book.lists.size + 1}"

    private fun showMenu(anchor: View, list: ShoppingList) {
        PopupMenu(this, anchor, Gravity.END).apply {
            menu.add(0, 1, 0, R.string.shopping_rename)
            menu.add(0, 2, 1, R.string.shopping_share)
            menu.add(0, 3, 2, R.string.shopping_delete_list)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> askName(this@ShoppingListsActivity, getString(R.string.shopping_rename), list.name) {
                        book.rename(list.id, it)
                        ShoppingRepository.save(this@ShoppingListsActivity)
                        refresh()
                    }
                    2 -> shareList(this@ShoppingListsActivity, list)
                    3 -> confirmDelete(list)
                }
                true
            }
            show()
        }
    }

    private fun confirmDelete(list: ShoppingList) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.shopping_delete_list_confirm, list.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                book.delete(list.id)
                ShoppingRepository.save(this)
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        /** Диалог ввода названия (новый список / переименование). */
        fun askName(context: Context, title: String, initial: String, onDone: (String) -> Unit) {
            val input = EditText(context).apply {
                setText(initial)
                setSelection(text.length)
                hint = context.getString(R.string.shopping_list_name_hint)
                setSingleLine(true)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            }
            val pad = (20 * context.resources.displayMetrics.density).toInt()
            val box = FrameLayout(context).apply {
                setPadding(pad, pad / 2, pad, 0)
                addView(input)
            }
            val dialog = AlertDialog.Builder(context)
                .setTitle(title)
                .setView(box)
                .setPositiveButton(R.string.save) { _, _ -> onDone(input.text.toString().trim()) }
                .setNegativeButton(R.string.cancel, null)
                .create()
            input.setOnEditorActionListener { _, _, _ ->
                onDone(input.text.toString().trim())
                dialog.dismiss()
                true
            }
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
            dialog.show()
            input.requestFocus()
        }

        fun shareList(context: Context, list: ShoppingList) {
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, Shopping.shareText(list))
            context.startActivity(Intent.createChooser(send, list.name))
        }
    }
}
