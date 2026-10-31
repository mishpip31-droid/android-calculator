package com.example.calculator

import java.util.Calendar
import java.util.GregorianCalendar

/** Простая дата без времени (java.time недоступен на Android 7). */
data class Day(val year: Int, val month: Int, val day: Int) : Comparable<Day> {
    override fun compareTo(other: Day) = compareValuesBy(this, other, { it.year }, { it.month }, { it.day })

    fun plusDays(n: Int): Day {
        val c = GregorianCalendar(year, month - 1, day)
        c.add(Calendar.DAY_OF_MONTH, n)
        return Day(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    companion object {
        fun today(): Day {
            val c = Calendar.getInstance()
            return Day(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        }
    }
}

/**
 * Праздник с оформлением.
 * @param emoji что падает на экране
 * @param when_ когда тема включается автоматически (для данного года)
 */
class Holiday(
    val id: String,
    val title: String,
    val dates: String,
    val emoji: List<String>,
    private val when_: (year: Int) -> List<ClosedRange<Day>>,
    private val greetingFor: (Day) -> String,
) {
    fun isOn(day: Day): Boolean =
        listOf(day.year - 1, day.year, day.year + 1).any { y -> when_(y).any { day in it } }

    fun greeting(day: Day) = greetingFor(day)
}

object Holidays {
    private fun fixed(m1: Int, d1: Int, m2: Int, d2: Int): (Int) -> List<ClosedRange<Day>> = { y ->
        // Диапазон через Новый год: начало в этом году, конец — в следующем.
        val end = if (m2 < m1) Day(y + 1, m2, d2) else Day(y, m2, d2)
        listOf(Day(y, m1, d1)..end)
    }

    /** Католическая (григорианская) Пасха — алгоритм Гаусса/«анонимный». */
    fun catholicEaster(y: Int): Day {
        val a = y % 19
        val b = y / 100
        val c = y % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return Day(y, month, day)
    }

    /** Православная Пасха: юлианская дата по Мееусу + 13 дней (верно для 1900–2099). */
    fun orthodoxEaster(y: Int): Day {
        val a = y % 4
        val b = y % 7
        val c = y % 19
        val d = (19 * c + 15) % 30
        val e = (2 * a + 4 * b - d + 34) % 7
        val month = (d + e + 114) / 31
        val day = (d + e + 114) % 31 + 1
        return Day(y, month, day).plusDays(13)
    }

    /** Порядок важен: при пересечении дат выбирается праздник выше в списке. */
    val all: List<Holiday> = listOf(
        Holiday("christmas", "Рождество Христово", "6–8 января", listOf("⭐", "✨", "🌟"),
            fixed(1, 6, 1, 8)) { "✨ Со светлым Рождеством Христовым!" },
        Holiday("new_year", "Новый год", "20 декабря – 5 января", listOf("❄️", "❄", "✨", "🎄"),
            fixed(12, 20, 1, 5)) { d ->
            when {
                d.month == 12 && d.day in 24..26 -> "🎄 С Рождеством и наступающим Новым годом!"
                d.month == 12 -> "🎄 С наступающим Новым годом!"
                else -> "🎄 С Новым годом!"
            }
        },
        Holiday("easter", "Пасха", "по церковному календарю", listOf("🥚", "🐣", "🌿", "🌸"),
            { y ->
                listOf(catholicEaster(y), orthodoxEaster(y)).map { it.plusDays(-1)..it.plusDays(1) }
            }) { "🥚 Христос Воскрес! Со светлой Пасхой!" },
        Holiday("valentine", "День святого Валентина", "12–14 февраля", listOf("❤️", "💕", "💗"),
            fixed(2, 12, 2, 14)) { "❤️ С Днём святого Валентина!" },
        Holiday("defender", "День защитника Отечества", "21–23 февраля", listOf("⭐", "🎖️"),
            fixed(2, 21, 2, 23)) { "🎖️ С Днём защитника Отечества!" },
        Holiday("womens_day", "8 Марта", "6–8 марта", listOf("🌷", "🌸", "💐"),
            fixed(3, 6, 3, 8)) { "🌷 С 8 Марта!" },
        // Масленица длится неделю — короткие праздники с фиксированной датой важнее.
        Holiday("maslenitsa", "Масленица", "неделя перед Великим постом", listOf("🥞", "☀️", "🌼"),
            { y -> val e = orthodoxEaster(y); listOf(e.plusDays(-55)..e.plusDays(-49)) }) { "🥞 Весёлой Масленицы!" },
        Holiday("labour_day", "Праздник труда", "30 апреля – 1 мая", listOf("🎈", "🌷", "☀️"),
            fixed(4, 30, 5, 1)) { "🎈 С Праздником труда — с 1 Мая!" },
        Holiday("victory_day", "День Победы", "7–9 мая", listOf("🎆", "✨", "🌟"),
            fixed(5, 7, 5, 9)) { "🎆 С Днём Победы!" },
        Holiday("independence", "День Независимости", "1–3 июля", listOf("🎆", "🎉", "✨"),
            fixed(7, 1, 7, 3)) { "🎆 С Днём Независимости Республики Беларусь!" },
        Holiday("kupalle", "Купалье", "6–7 июля", listOf("🌿", "🌼", "🔥", "✨"),
            fixed(7, 6, 7, 7)) { "🌿 Шчаслівага Купалля!" },
        Holiday("knowledge", "День знаний", "30 августа – 1 сентября", listOf("📚", "✏️", "🍁", "🎒"),
            fixed(8, 30, 9, 1)) { "📚 С Днём знаний!" },
        Holiday("mothers_day", "День матери", "13–14 октября", listOf("💐", "🌷", "💜"),
            fixed(10, 13, 10, 14)) { "💐 С Днём матери!" },
        Holiday("halloween", "Хэллоуин", "29–31 октября", listOf("🎃", "🦇", "👻"),
            fixed(10, 29, 10, 31)) { "🎃 Весёлого Хэллоуина!" },
    )

    fun byId(id: String): Holiday? = all.firstOrNull { it.id == id }

    fun forDate(day: Day): Holiday? = all.firstOrNull { it.isOn(day) }
}
