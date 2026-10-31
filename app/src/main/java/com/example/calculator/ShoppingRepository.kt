package com.example.calculator

import android.content.Context
import java.math.BigDecimal
import org.json.JSONArray
import org.json.JSONObject

/** Хранит списки покупок на телефоне (SharedPreferences, JSON). Один экземпляр на приложение. */
object ShoppingRepository {
    private const val PREFS = "shopping"
    private const val KEY = "book"
    private var book: ShoppingBook? = null

    fun book(context: Context): ShoppingBook {
        book?.let { return it }
        val loaded = ShoppingBook()
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        if (raw != null) {
            try {
                val o = JSONObject(raw)
                val lists = o.getJSONArray("lists")
                for (i in 0 until lists.length()) loaded.put(readList(lists.getJSONObject(i)))
                val names = o.optJSONArray("names") ?: JSONArray()
                loaded.restoreSuggestions(
                    (0 until names.length()).map { names.getJSONObject(it).let { n -> n.getString("n") to n.getInt("c") } },
                )
            } catch (e: Exception) {
                // Повреждённые данные — начинаем с пустых списков.
            }
        }
        book = loaded
        return loaded
    }

    fun save(context: Context) {
        val b = book ?: return
        val lists = JSONArray()
        for (list in b.lists) lists.put(writeList(list))
        val names = JSONArray()
        for ((n, c) in b.savedSuggestions()) names.put(JSONObject().put("n", n).put("c", c))
        val o = JSONObject().put("lists", lists).put("names", names)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, o.toString()).apply()
    }

    private fun readList(o: JSONObject): ShoppingList {
        val items = o.getJSONArray("items")
        return ShoppingList(
            id = o.getLong("id"),
            name = o.getString("name"),
            items = (0 until items.length()).map { i ->
                val it = items.getJSONObject(i)
                ShoppingItem(
                    id = it.getLong("id"),
                    name = it.getString("name"),
                    quantity = it.optString("q").takeIf { s -> s.isNotEmpty() }?.let(::BigDecimal),
                    unit = it.optString("u"),
                    price = it.optString("p").takeIf { s -> s.isNotEmpty() }?.let(::BigDecimal),
                    done = it.optBoolean("d"),
                )
            },
            updatedAt = o.optLong("t"),
        )
    }

    private fun writeList(list: ShoppingList): JSONObject {
        val items = JSONArray()
        for (item in list.items) {
            val o = JSONObject().put("id", item.id).put("name", item.name)
            item.quantity?.let { o.put("q", it.toPlainString()) }
            if (item.unit.isNotEmpty()) o.put("u", item.unit)
            item.price?.let { o.put("p", it.toPlainString()) }
            if (item.done) o.put("d", true)
            items.put(o)
        }
        return JSONObject().put("id", list.id).put("name", list.name).put("items", items).put("t", list.updatedAt)
    }
}
