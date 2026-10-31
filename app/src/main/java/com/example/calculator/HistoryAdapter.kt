package com.example.calculator

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Показывает записи истории; какие именно и в каком порядке — решает [entries]. */
class HistoryAdapter(
    private val layout: Int,
    private val entries: () -> List<HistoryEntry>,
) : BaseAdapter() {
    override fun getCount() = entries().size

    override fun getItem(position: Int): HistoryEntry = entries()[position]

    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(parent.context).inflate(layout, parent, false)
        val entry = getItem(position)
        view.findViewById<TextView>(R.id.historyExpression).text = entry.expression
        view.findViewById<TextView>(R.id.historyResult).text = "= ${entry.result}"
        view.findViewById<TextView>(R.id.historyNote).apply {
            text = entry.note
            visibility = if (entry.note.isEmpty()) View.GONE else View.VISIBLE
        }
        view.findViewById<TextView?>(R.id.historyTime)?.text = formatTime(entry.timestamp)
        return view
    }

    private fun formatTime(timestamp: Long): String {
        if (timestamp <= 0) return ""
        val then = Calendar.getInstance().apply { timeInMillis = timestamp }
        val now = Calendar.getInstance()
        val sameYear = then.get(Calendar.YEAR) == now.get(Calendar.YEAR)
        val dayDiff = if (sameYear) now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR) else -1
        val time = SimpleDateFormat("HH:mm", RU).format(Date(timestamp))
        return when {
            dayDiff == 0 -> "Сегодня, $time"
            dayDiff == 1 -> "Вчера, $time"
            sameYear -> SimpleDateFormat("d MMM, HH:mm", RU).format(Date(timestamp))
            else -> SimpleDateFormat("d MMM yyyy", RU).format(Date(timestamp))
        }
    }

    private companion object {
        val RU: Locale = Locale.forLanguageTag("ru")
    }
}
