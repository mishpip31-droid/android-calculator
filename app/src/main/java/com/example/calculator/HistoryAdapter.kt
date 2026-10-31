package com.example.calculator

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

/** Показывает записи истории: выражение мелко, результат крупно. */
class HistoryAdapter(private val history: History) : BaseAdapter() {
    override fun getCount() = history.size
    override fun getItem(position: Int): HistoryEntry = history.entries[position]
    override fun getItemId(position: Int) = getItem(position).timestamp
    override fun hasStableIds() = false

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        val entry = getItem(position)
        view.findViewById<TextView>(R.id.historyExpression).text = entry.expression
        view.findViewById<TextView>(R.id.historyResult).text = "= ${entry.result}"
        return view
    }
}
