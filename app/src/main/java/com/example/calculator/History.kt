package com.example.calculator

/** Одна запись истории: выражение и его результат. */
data class HistoryEntry(
    val expression: String,
    val result: String,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * Список вычислений, от старых к новым. Не зависит от Android.
 */
class History(private val maxSize: Int = 200) {
    private val items = ArrayList<HistoryEntry>()

    val entries: List<HistoryEntry> get() = items
    val size: Int get() = items.size
    fun isEmpty() = items.isEmpty()

    /** Добавляет запись. Повтор того же вычисления подряд не дублируется. */
    fun add(entry: HistoryEntry): Boolean {
        val last = items.lastOrNull()
        if (last != null && last.expression == entry.expression && last.result == entry.result) {
            return false
        }
        items += entry
        while (items.size > maxSize) items.removeAt(0)
        return true
    }

    fun removeAt(index: Int): HistoryEntry = items.removeAt(index)

    fun clear() = items.clear()

    fun replaceAll(entries: List<HistoryEntry>) {
        items.clear()
        items.addAll(entries.takeLast(maxSize))
    }
}
