package com.example.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HolidaysTest {
    @Test fun catholicEaster() {
        assertEquals(Day(2024, 3, 31), Holidays.catholicEaster(2024))
        assertEquals(Day(2025, 4, 20), Holidays.catholicEaster(2025))
        assertEquals(Day(2026, 4, 5), Holidays.catholicEaster(2026))
        assertEquals(Day(2027, 3, 28), Holidays.catholicEaster(2027))
    }

    @Test fun orthodoxEaster() {
        assertEquals(Day(2024, 5, 5), Holidays.orthodoxEaster(2024))
        assertEquals(Day(2025, 4, 20), Holidays.orthodoxEaster(2025))
        assertEquals(Day(2026, 4, 12), Holidays.orthodoxEaster(2026))
        assertEquals(Day(2027, 5, 2), Holidays.orthodoxEaster(2027))
    }

    private fun id(y: Int, m: Int, d: Int) = Holidays.forDate(Day(y, m, d))?.id

    @Test fun fixedHolidays() {
        assertEquals("new_year", id(2026, 12, 31))
        assertEquals("new_year", id(2027, 1, 2))
        assertEquals("christmas", id(2027, 1, 7))
        assertEquals("valentine", id(2027, 2, 14))
        assertEquals("defender", id(2027, 2, 23))
        assertEquals("womens_day", id(2027, 3, 8))
        assertEquals("labour_day", id(2026, 5, 1))
        assertEquals("victory_day", id(2026, 5, 9))
        assertEquals("independence", id(2026, 7, 3))
        assertEquals("kupalle", id(2026, 7, 7))
        assertEquals("knowledge", id(2026, 9, 1))
        assertEquals("mothers_day", id(2026, 10, 14))
        assertEquals("halloween", id(2026, 10, 31))
        assertNull(id(2026, 11, 15))
        assertNull(id(2026, 6, 15))
    }

    @Test fun movableHolidays() {
        assertEquals("easter", id(2026, 4, 5))   // католическая
        assertEquals("easter", id(2026, 4, 12))  // православная
        // Масленица 2026: 16–22 февраля (Пасха 12 апреля − 55…49 дней)
        assertEquals("maslenitsa", id(2026, 2, 16))
        assertEquals("maslenitsa", id(2026, 2, 20))
        // 2027: Масленица 8–14 марта, но 8 Марта важнее
        assertEquals("womens_day", id(2027, 3, 8))
        assertEquals("maslenitsa", id(2027, 3, 9))
        // В 2024 православная Пасха 5 мая — пересекается с Днём Победы? Нет: 4–6 мая.
        assertEquals("easter", id(2024, 5, 5))
        assertEquals("victory_day", id(2024, 5, 9))
    }

    @Test fun greetings() {
        val ny = Holidays.byId("new_year")!!
        assertEquals("🎄 С наступающим Новым годом!", ny.greeting(Day(2026, 12, 30)))
        assertEquals("🎄 С Рождеством и наступающим Новым годом!", ny.greeting(Day(2026, 12, 25)))
        assertEquals("🎄 С Новым годом!", ny.greeting(Day(2027, 1, 1)))
    }

    @Test fun dayArithmetic() {
        assertEquals(Day(2027, 1, 1), Day(2026, 12, 31).plusDays(1))
        assertEquals(Day(2024, 2, 29), Day(2024, 3, 1).plusDays(-1))
    }
}
