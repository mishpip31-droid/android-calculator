package com.example.calculator

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingTest {
    private var clock = 1000L
    private fun book() = ShoppingBook { clock++ }
    private fun bd(s: String) = BigDecimal(s)

    @Test fun parsesPlainName() {
        val item = ItemInputParser.parse("  Молоко   3,2% ")!!
        assertEquals("Молоко 3,2%", item.name)
        assertNull(item.quantity)
        assertNull(item.price)
        assertNull(ItemInputParser.parse("   "))
    }

    @Test fun parsesQuantityAndPrice() {
        val a = ItemInputParser.parse("Молоко 2 × 2.35")!!
        assertEquals("Молоко", a.name)
        assertEquals(bd("2"), a.quantity)
        assertEquals(bd("2.35"), a.price)
        assertEquals(bd("4.70"), a.cost)

        val b = ItemInputParser.parse("Сыр 1x12,5")!!
        assertEquals("Сыр", b.name)
        assertEquals(bd("12.5"), b.price)

        val c = ItemInputParser.parse("Яблоки 1.5 кг × 3.2")!!
        assertEquals("Яблоки", c.name)
        assertEquals("кг", c.unit)
        assertEquals("1.5 кг × 3.20 = 4.80", c.details())

        val d = ItemInputParser.parse("Хлеб 2 х 1.9")!! // кириллическая «х»
        assertEquals(bd("1.9"), d.price)
    }

    @Test fun parsesQuantityWithUnit() {
        val item = ItemInputParser.parse("Картошка 3 кг")!!
        assertEquals("Картошка", item.name)
        assertEquals(bd("3"), item.quantity)
        assertEquals("кг", item.unit)
        assertEquals("3 кг", item.details())
        // Число без единицы — часть названия (например, «Батарейки АА 4»)
        assertEquals("Вода 5", ItemInputParser.parse("Вода 5")!!.name)
    }

    @Test fun addToggleAndOrder() {
        val b = book()
        val list = b.create("Продукты")
        val milk = b.addItem(list.id, "Молоко")!!
        val bread = b.addItem(list.id, "Хлеб")!!
        b.toggle(list.id, milk.id)
        val l = b.get(list.id)!!
        assertEquals(listOf("Хлеб"), l.active.map { it.name })
        assertEquals(listOf("Молоко"), l.bought.map { it.name })
        assertEquals(1, l.doneCount)
        b.toggle(list.id, milk.id)
        assertEquals(0, b.get(list.id)!!.doneCount)
        assertTrue(bread.id != milk.id)
    }

    @Test fun totals() {
        val b = book()
        val list = b.create("Магазин")
        val a = b.addItem(list.id, "Молоко 2 × 2.35")!!
        b.addItem(list.id, "Сыр 1 × 10")
        b.addItem(list.id, "Соль")
        b.toggle(list.id, a.id)
        val l = b.get(list.id)!!
        assertTrue(l.hasPrices)
        assertEquals("14.70", Shopping.formatMoney(l.total))
        assertEquals("4.70", Shopping.formatMoney(l.spent))
        assertEquals("10.00", Shopping.formatMoney(l.left))
    }

    @Test fun clearBoughtUncheckAllAndUndo() {
        val b = book()
        val list = b.create("Неделя")
        val a = b.addItem(list.id, "A")!!
        b.addItem(list.id, "B")
        b.toggle(list.id, a.id)
        val snapshot = b.get(list.id)!!
        b.clearBought(list.id)
        assertEquals(listOf("B"), b.get(list.id)!!.items.map { it.name })
        b.put(snapshot) // «Отменить»
        assertEquals(2, b.get(list.id)!!.items.size)
        b.uncheckAll(list.id)
        assertEquals(0, b.get(list.id)!!.doneCount)
    }

    @Test fun listsSortedByRecentChange() {
        val b = book()
        val first = b.create("Первый")
        val second = b.create("Второй")
        assertEquals(listOf("Второй", "Первый"), b.lists.map { it.name })
        b.addItem(first.id, "Чай")
        assertEquals(listOf("Первый", "Второй"), b.lists.map { it.name })
        b.rename(second.id, "  Стройка ")
        assertEquals("Стройка", b.get(second.id)!!.name)
        b.delete(first.id)
        assertEquals(listOf("Стройка"), b.lists.map { it.name })
    }

    @Test fun suggestionsByFrequency() {
        val b = book()
        val list = b.create("x")
        b.addItem(list.id, "Хлеб")
        b.addItem(list.id, "Молоко 2 × 2")
        b.addItem(list.id, "молоко")
        assertEquals(listOf("молоко", "Хлеб"), b.suggestions())
    }

    @Test fun shareText() {
        val b = book()
        val list = b.create("Продукты")
        val milk = b.addItem(list.id, "Молоко 2 × 2.35")!!
        b.addItem(list.id, "Хлеб")
        b.toggle(list.id, milk.id)
        assertEquals(
            "🛒 Продукты\n☐ Хлеб\n☑ Молоко — 2 × 2.35 = 4.70\nИтого: 4.70",
            Shopping.shareText(b.get(list.id)!!),
        )
        assertFalse(Shopping.shareText(b.create("Пусто")).contains("Итого"))
    }
}
