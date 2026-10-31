package com.example.calculator

/** Одна запись истории: выражение, результат и необязательная заметка. */
data class HistoryEntry(
    val expression: String,
    val result: String,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = "",
) {
    val hasNote: Boolean get() = note.isNotEmpty()
}

/**
 * Список вычислений, от старых к новым. Не зависит от Android.
 *
 * Записи с заметками хранятся всегда. Записей без заметок хранится не больше
 * [maxUnnoted] — при переполнении удаляются самые старые из них.
 */
class History(private val maxUnnoted: Int = 200) {
    private val items = ArrayList<HistoryEntry>()

    val entries: List<HistoryEntry> get() = items
    val size: Int get() = items.size
    fun isEmpty() = items.isEmpty()
    val notedCount: Int get() = items.count { it.hasNote }

    /** Добавляет запись. Повтор того же вычисления подряд не дублируется. */
    fun add(entry: HistoryEntry): Boolean {
        val last = items.lastOrNull()
        if (last != null && last.expression == entry.expression && last.result == entry.result) {
            return false
        }
        items += entry
        trim()
        return true
    }

    fun removeAt(index: Int): HistoryEntry = items.removeAt(index)

    /** Индекс именно этого объекта записи (а не равной ему). */
    fun indexOf(entry: HistoryEntry): Int = items.indexOfFirst { it === entry }

    /** Задаёт заметку; пустая строка убирает её. Возвращает обновлённую запись. */
    fun setNote(index: Int, note: String): HistoryEntry {
        val updated = items[index].copy(note = note.trim())
        items[index] = updated
        return updated
    }

    /** Удаляет всё. */
    fun clear() = items.clear()

    /** Удаляет только записи без заметок. */
    fun clearUnnoted() {
        items.removeAll { !it.hasNote }
    }

    fun replaceAll(entries: List<HistoryEntry>) {
        items.clear()
        items.addAll(entries)
        trim()
    }

    /**
     * Поиск по заметке, выражению и результату, от новых к старым.
     * Все слова запроса должны найтись; регистр, «ё/е», «,/.» и виды минуса не важны.
     */
    fun search(query: String, notesOnly: Boolean = false): List<HistoryEntry> {
        val terms = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map(::normalize)
        return items.asReversed().filter { entry ->
            if (notesOnly && !entry.hasNote) return@filter false
            if (terms.isEmpty()) return@filter true
            val haystack = normalize("${entry.note} ${entry.expression} = ${entry.result}")
            terms.all { haystack.contains(it) }
        }
    }

    private fun trim() {
        var excess = items.count { !it.hasNote } - maxUnnoted
        if (excess <= 0) return
        val iterator = items.iterator()
        while (excess > 0 && iterator.hasNext()) {
            if (!iterator.next().hasNote) {
                iterator.remove()
                excess--
            }
        }
    }

    private companion object {
        fun normalize(s: String) = s.lowercase()
            .replace('ё', 'е')
            .replace(',', '.')
            .replace('-', '−')
            .replace('*', '×')
            .replace('/', '÷')
    }
}
