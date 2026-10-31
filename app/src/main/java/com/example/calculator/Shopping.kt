package com.example.calculator

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Товар в списке покупок.
 * @param quantity количество (null — не указано, считается как 1)
 * @param unit единица: «кг», «шт», «л»… (может быть пустой)
 * @param price цена за единицу (null — без цены)
 */
data class ShoppingItem(
    val id: Long,
    val name: String,
    val quantity: BigDecimal? = null,
    val unit: String = "",
    val price: BigDecimal? = null,
    val done: Boolean = false,
) {
    /** Стоимость: количество × цена, или null, если цены нет. */
    val cost: BigDecimal? get() = price?.multiply(quantity ?: BigDecimal.ONE)

    /** «2 кг × 3.50 = 7.00», «3 шт», «4.70» или пусто. */
    fun details(): String {
        val qty = quantity?.let { Shopping.formatQuantity(it) + if (unit.isNotEmpty()) " $unit" else "" }
        val p = price ?: return qty.orEmpty()
        if (qty == null) return Shopping.formatMoney(p)
        return "$qty × ${Shopping.formatMoney(p)} = ${Shopping.formatMoney(cost!!)}"
    }
}

data class ShoppingList(
    val id: Long,
    val name: String,
    val items: List<ShoppingItem> = emptyList(),
    val updatedAt: Long = 0,
) {
    val active: List<ShoppingItem> get() = items.filter { !it.done }
    val bought: List<ShoppingItem> get() = items.filter { it.done }
    val doneCount: Int get() = items.count { it.done }
    val hasPrices: Boolean get() = items.any { it.price != null }

    /** Итого по всем товарам с ценой. */
    val total: BigDecimal get() = items.mapNotNull { it.cost }.fold(BigDecimal.ZERO, BigDecimal::add)

    /** Уже потрачено — по купленным. */
    val spent: BigDecimal get() = bought.mapNotNull { it.cost }.fold(BigDecimal.ZERO, BigDecimal::add)

    /** Осталось купить на сумму. */
    val left: BigDecimal get() = total.subtract(spent)
}

/**
 * Все списки покупок и подсказки названий. Не зависит от Android.
 */
class ShoppingBook(private val now: () -> Long = System::currentTimeMillis) {
    private val data = ArrayList<ShoppingList>()

    /** Названия товаров, которые уже вводились: ключ — в нижнем регистре. */
    private val names = LinkedHashMap<String, Pair<String, Int>>()
    private var lastId = 0L

    /** Списки — сначала недавно изменённые. */
    val lists: List<ShoppingList> get() = data.sortedByDescending { it.updatedAt }

    fun get(id: Long): ShoppingList? = data.firstOrNull { it.id == id }

    fun create(name: String): ShoppingList {
        val list = ShoppingList(newId(), name.trim().ifEmpty { "Список" }, updatedAt = now())
        data += list
        return list
    }

    fun rename(id: Long, name: String) = update(id) { it.copy(name = name.trim().ifEmpty { it.name }) }

    fun delete(id: Long): ShoppingList? {
        val list = get(id) ?: return null
        data.remove(list)
        return list
    }

    /** Возвращает список целиком (для «Отменить»). */
    fun put(list: ShoppingList) {
        data.removeAll { it.id == list.id }
        data += list
        lastId = maxOf(lastId, list.id, list.items.maxOfOrNull { it.id } ?: 0)
    }

    /** Добавляет товар из строки ввода: «Молоко», «Молоко 2 × 2.35», «Яблоки 1.5 кг». */
    fun addItem(listId: Long, input: String): ShoppingItem? {
        val parsed = ItemInputParser.parse(input) ?: return null
        val item = parsed.copy(id = newId())
        update(listId) { it.copy(items = it.items + item) }
        remember(item.name)
        return item
    }

    fun toggle(listId: Long, itemId: Long) = updateItems(listId) { items ->
        items.map { if (it.id == itemId) it.copy(done = !it.done) else it }
    }

    fun updateItem(listId: Long, item: ShoppingItem) {
        updateItems(listId) { items -> items.map { if (it.id == item.id) item else it } }
        remember(item.name)
    }

    fun removeItem(listId: Long, itemId: Long) = updateItems(listId) { items -> items.filter { it.id != itemId } }

    fun clearBought(listId: Long) = updateItems(listId) { items -> items.filter { !it.done } }

    fun uncheckAll(listId: Long) = updateItems(listId) { items -> items.map { it.copy(done = false) } }

    /** Подсказки при вводе: частые сверху. */
    fun suggestions(): List<String> =
        names.values.sortedByDescending { it.second }.map { it.first }

    fun restoreSuggestions(saved: List<Pair<String, Int>>) {
        names.clear()
        for ((name, count) in saved) names[name.lowercase()] = name to count
    }

    fun savedSuggestions(): List<Pair<String, Int>> = names.values.toList()

    private fun remember(name: String) {
        val key = name.trim().lowercase()
        if (key.isEmpty()) return
        val count = names[key]?.second ?: 0
        names[key] = name.trim() to count + 1
        if (names.size > MAX_NAMES) {
            names.entries.minByOrNull { it.value.second }?.key?.let(names::remove)
        }
    }

    private fun updateItems(listId: Long, change: (List<ShoppingItem>) -> List<ShoppingItem>) =
        update(listId) { it.copy(items = change(it.items)) }

    private fun update(id: Long, change: (ShoppingList) -> ShoppingList) {
        val i = data.indexOfFirst { it.id == id }
        if (i >= 0) data[i] = change(data[i]).copy(updatedAt = now())
    }

    private fun newId(): Long {
        lastId = maxOf(lastId + 1, now())
        return lastId
    }

    private companion object {
        const val MAX_NAMES = 300
    }
}

/** Разбор строки ввода товара. */
object ItemInputParser {
    private const val NUM = """(\d+(?:[.,]\d+)?)"""
    private val UNITS = "шт|кг|г|гр|л|мл|уп|пач|бут|м"

    // «Молоко 2 × 2.35», «Молоко 2x2,35», «Яблоки 1.5 кг × 3.2»
    private val QTY_PRICE = Regex("""^(.+?)\s+$NUM\s*($UNITS)?\.?\s*[x×х*]\s*$NUM\s*$""", RegexOption.IGNORE_CASE)

    // «Яблоки 1.5 кг», «Хлеб 2 шт»
    private val QTY_UNIT = Regex("""^(.+?)\s+$NUM\s*($UNITS)\.?\s*$""", RegexOption.IGNORE_CASE)

    fun parse(input: String): ShoppingItem? {
        val text = input.trim().replace(Regex("\\s+"), " ")
        if (text.isEmpty()) return null
        QTY_PRICE.find(text)?.let { m ->
            return ShoppingItem(
                id = 0,
                name = m.groupValues[1].trim(),
                quantity = number(m.groupValues[2]),
                unit = m.groupValues[3].lowercase(),
                price = number(m.groupValues[4]),
            )
        }
        QTY_UNIT.find(text)?.let { m ->
            return ShoppingItem(
                id = 0,
                name = m.groupValues[1].trim(),
                quantity = number(m.groupValues[2]),
                unit = m.groupValues[3].lowercase(),
            )
        }
        return ShoppingItem(id = 0, name = text)
    }

    private fun number(s: String) = BigDecimal(s.replace(',', '.'))
}

object Shopping {
    fun formatMoney(v: BigDecimal): String = v.setScale(2, RoundingMode.HALF_UP).toPlainString()

    fun formatQuantity(v: BigDecimal): String = v.stripTrailingZeros().toPlainString()

    /** Текст для «Поделиться»: ☐ / ☑ и итог. */
    fun shareText(list: ShoppingList): String = buildString {
        append("🛒 ").append(list.name).append('\n')
        for (item in list.active + list.bought) {
            append(if (item.done) "☑ " else "☐ ").append(item.name)
            val d = item.details()
            if (d.isNotEmpty()) append(" — ").append(d)
            append('\n')
        }
        if (list.hasPrices) append("Итого: ").append(formatMoney(list.total))
    }.trimEnd()
}
